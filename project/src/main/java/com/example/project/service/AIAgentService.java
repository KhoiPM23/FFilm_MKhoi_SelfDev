package com.example.project.service;

import com.example.project.dto.MovieSearchFilters; 
import com.example.project.model.*;
import com.example.project.repository.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

// --- KHU VỰC IMPORT QUAN TRỌNG ĐỂ FIX LỖI ---
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpMethod;
// --------------------------------------------

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class AIAgentService {

    //---- CẤU HÌNH ----
    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    private static final String GEMINI_API_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";

    private final RestTemplate restTemplate;
    private final SubscriptionPlanRepository planRepository;
    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final PersonRepository personRepository;
    private final MovieService movieService;
    private final Cache conversationCache;

    private Map<String, Object> websiteContext;

    @Autowired
    public AIAgentService(
            @Value("${gemini.api.key:}") String geminiApiKey,
            RestTemplate restTemplate,
            SubscriptionPlanRepository planRepository,
            MovieRepository movieRepository,
            GenreRepository genreRepository,
            PersonRepository personRepository,
            MovieService movieService,
            CacheManager cacheManager
    ) {
        this.geminiApiKey = geminiApiKey;
        this.restTemplate = restTemplate;
        this.planRepository = planRepository;
        this.movieRepository = movieRepository;
        this.genreRepository = genreRepository;
        this.personRepository = personRepository;
        this.movieService = movieService;
        this.conversationCache = cacheManager.getCache("conversationCache");
        
        loadWebsiteContext();
    }

    //---- 1. LOCAL SAFETY ----
    private static final Set<String> BLACKLISTED_KEYWORDS = Set.of(
        "sex", "tình dục", "xxx", "porn", "khỏa thân", "khiêu dâm", "làm tình", "ấu dâm", "vú", "bướm", "cu", "chịch", "đụ", "show hàng"
    );

    //---- 2. COUNTRY MAPPING ----
    private static final Map<String, List<String>> COUNTRY_MAPPING = Map.ofEntries(
        Map.entry("South Korea", List.of("hàn", "han", "korea", "hàn quốc", "han quoc", "남한", "korean")),
        Map.entry("Viet Nam", List.of("việt", "viet", "vietnam", "việt nam", "vn", "vietnamese")),
        Map.entry("United States", List.of("mỹ", "my", "mỹ", "usa", "us", "america", "american", "hollywood")),
        Map.entry("Japan", List.of("nhật", "nhat", "nhật bản", "japan", "japanese", "日本")),
        Map.entry("China", List.of("trung", "trung quốc", "china", "chinese", "中国", "trung hoa")),
        Map.entry("Thailand", List.of("thái", "thai", "thái lan", "thailand")),
        Map.entry("India", List.of("ấn", "ấn độ", "india", "indian", "bollywood")),
        Map.entry("United Kingdom", List.of("anh", "anh quốc", "uk", "britain", "british", "england")),
        Map.entry("France", List.of("pháp", "phap", "france", "french")),
        Map.entry("Germany", List.of("đức", "duc", "germany", "german"))
    );

    //---- 3. GENRE MAPPING (Vietnamese → English) ----
    private static final Map<String, List<String>> GENRE_MAPPING = Map.ofEntries(
        Map.entry("Hành động", List.of("hành động", "hanh dong", "action", "đánh nhau", "võ thuật", "vo thuat")),
        Map.entry("Hài", List.of("hài", "hai", "comedy", "hài hước", "hai huoc", "vui", "funny", "cười")),
        Map.entry("Chính kịch", List.of("chính kịch", "chinh kich", "drama", "tâm lý", "tam ly")),
        Map.entry("Lãng mạn", List.of("lãng mạn", "lang man", "romance", "tình cảm", "tinh cam", "yêu", "love")),
        Map.entry("Kinh dị", List.of("kinh dị", "kinh di", "horror", "ma", "ghost", "sợ hãi", "scary")),
        Map.entry("Khoa học viễn tưởng", List.of("khoa học", "sci-fi", "viễn tưởng", "vien tuong", "công nghệ")),
        Map.entry("Gây cấn", List.of("gây cấn", "gay can", "thriller", "kịch tính", "kich tinh", "căng thẳng")),
        Map.entry("Phiêu lưu", List.of("phiêu lưu", "phieu luu", "adventure", "mạo hiểm", "mao hiem")),
        Map.entry("Hoạt hình", List.of("hoạt hình", "hoat hinh", "animation", "anime", "cartoon", "animated")),
        Map.entry("Gia đình", List.of("gia đình", "gia dinh", "family", "trẻ em", "tre em", "kids")),
        Map.entry("Hình sự", List.of("hình sự", "hinh su", "crime", "tội phạm", "toi pham", "gangster")),
        Map.entry("Bí ẩn", List.of("bí ẩn", "bi an", "mystery", "trinh thám", "detective")),
        Map.entry("Tài liệu", List.of("tài liệu", "tai lieu", "documentary", "document")),
        Map.entry("Chiến tranh", List.of("chiến tranh", "chien tranh", "war", "quân sự", "quan su")),
        Map.entry("Lịch sử", List.of("lịch sử", "lich su", "history", "historical"))
    );

    //---- 4. MOOD MAPPING (Tâm trạng → Thể loại) ----
    private static final Map<String, List<String>> MOOD_MAPPING = Map.ofEntries(
        // Cảm xúc tiêu cực
        Map.entry("SAD", List.of("buồn", "buon", "sad", "depressed", "tâm trạng", "stress", "mệt mỏi", "met moi", "chán", "chan", "cô đơn", "co don", "thất vọng", "that vong")),
        Map.entry("ANGRY", List.of("tức", "tuc", "giận", "gian", "angry", "mad", "bực", "buc", "phẫn nộ")),
        Map.entry("SCARED", List.of("sợ", "so", "scared", "afraid", "lo lắng", "lo lang", "anxiety", "hồi hộp")),
        
        // Cảm xúc tích cực
        Map.entry("HAPPY", List.of("vui", "happy", "hạnh phúc", "hanh phuc", "sảng khoái", "khỏe", "khoai")),
        Map.entry("EXCITED", List.of("hứng", "hung", "excited", "năng lượng", "nang luong", "nhiệt huyết", "nhiet huyet")),
        Map.entry("RELAXED", List.of("thư giãn", "thu gian", "relax", "nhẹ nhàng", "nhe nhang", "bình yên", "binh yen", "chill")),
        
        // Nhu cầu
        Map.entry("NEED_MOTIVATION", List.of("động lực", "dong luc", "motivation", "inspire", "cảm hứng", "cam hung", "khuyến khích", "khuyen khich")),
        Map.entry("NEED_LAUGH", List.of("cười", "cuoi", "laugh", "giải trí", "giai tri", "entertainment", "fun")),
        Map.entry("NEED_THINK", List.of("suy ngẫm", "suy ngam", "think", "triết lý", "triet ly", "philosophy", "ý nghĩa", "y nghia", "deep")),
        Map.entry("NEED_ADRENALINE", List.of("kích thích", "kich thich", "adrenaline", "gay cấn", "gay can", "hồi hộp", "hoi hop", "intense"))
    );

    // Mood → Genre Mapping
    private static final Map<String, List<String>> MOOD_TO_GENRES = Map.of(
        "SAD", List.of("Chính kịch", "Lãng mạn"),
        "ANGRY", List.of("Hành động", "Hình sự", "Gây cấn"),
        "SCARED", List.of("Kinh dị", "Gây cấn"),
        "HAPPY", List.of("Hài", "Lãng mạn", "Hoạt hình"),
        "EXCITED", List.of("Hành động", "Phiêu lưu", "Khoa học viễn tưởng"),
        "RELAXED", List.of("Hài", "Gia đình", "Hoạt hình", "Tài liệu"),
        "NEED_MOTIVATION", List.of("Chính kịch", "Phiêu lưu", "Gia đình"),
        "NEED_LAUGH", List.of("Hài", "Hoạt hình"),
        "NEED_THINK", List.of("Chính kịch", "Bí ẩn", "Khoa học viễn tưởng", "Tài liệu"),
        "NEED_ADRENALINE", List.of("Hành động", "Gây cấn", "Kinh dị")
    );

    private boolean isUnsafe(String message) {
        String lowerCaseMessage = message.toLowerCase();
        for (String keyword : BLACKLISTED_KEYWORDS) {
            if (lowerCaseMessage.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * PROMPT "FLATTENED BRAIN" (PHASE 8)
     */
    // THAY THẾ FLAT_PROMPT với version mới (thêm ví dụ)
    private static final String FLAT_PROMPT =
        "Bạn là trợ lý phân tích câu hỏi về phim. Trả về JSON thuần túy.\n\n" +
        
        "# QUY TẮC:\n" +
        "1. Output CHÍNH XÁC 1 JSON object\n" +
        "2. KHÔNG thêm ```json hoặc text ngoài\n" +
        "3. Ưu tiên LOOKUP nếu chỉ có tên riêng KHÔNG kèm 'phim'\n" +
        "4. Ưu tiên FILTER nếu có 'phim' + tên người\n" +
        "5. BẮT BUỘC phải có f_actor hoặc f_director nếu câu chứa 'phim của'\n\n" +
        
        "# CÁC TRƯỜNG:\n" +
        "- intent: FILTER|LOOKUP|TRENDING|QA|CHITCHAT|UNKNOWN\n" +
        "- f_country: Vietnam|South Korea|China|Japan|United States\n" +
        "- f_genres: [Hành động|Hài|Chính kịch|Lãng mạn|Kinh dị...]\n" +
        "- f_year_from, f_year_to: năm\n" +
        "- f_director, f_actor: tên người (BẮT BUỘC nếu có 'phim của')\n" +
        "- q_subject: tên phim/người (LOOKUP)\n" +
        "- q_type: actor|director|cast\n\n" +
        
        "# VÍ DỤ (20 CASES - QUAN TRỌNG):\n" +
        "Q: 'phim hàn quốc' → {\"intent\":\"FILTER\",\"f_country\":\"South Korea\"}\n" +
        "Q: 'phim việt nam' → {\"intent\":\"FILTER\",\"f_country\":\"Vietnam\"}\n" +
        "Q: 'phim mỹ hành động' → {\"intent\":\"FILTER\",\"f_country\":\"United States\",\"f_genres\":[\"Hành động\"]}\n" +
        "Q: 'phim hài hàn quốc' → {\"intent\":\"FILTER\",\"f_country\":\"South Korea\",\"f_genres\":[\"Hài\"]}\n" +
        "Q: 'phim kinh dị nhật' → {\"intent\":\"FILTER\",\"f_country\":\"Japan\",\"f_genres\":[\"Kinh dị\"]}\n" +
        "Q: 'phim tình cảm việt nam sau 2018' → {\"intent\":\"FILTER\",\"f_country\":\"Vietnam\",\"f_genres\":[\"Lãng mạn\"],\"f_year_from\":2018}\n" +
        "Q: 'phim hành động mỹ trước 2020' → {\"intent\":\"FILTER\",\"f_country\":\"United States\",\"f_genres\":[\"Hành động\"],\"f_year_to\":2019}\n" +
        "Q: 'tôi đang buồn' → {\"intent\":\"FILTER\",\"f_genres\":[\"Chính kịch\",\"Lãng mạn\"]}\n" +
        "Q: 'tôi cần động lực' → {\"intent\":\"FILTER\",\"f_genres\":[\"Chính kịch\",\"Phiêu lưu\"]}\n" +
        "Q: 'phim hài nhẹ nhàng' → {\"intent\":\"FILTER\",\"f_genres\":[\"Hài\",\"Gia đình\"]}\n" +
        "Q: 'Trấn Thành' → {\"intent\":\"LOOKUP\",\"q_subject\":\"Trấn Thành\",\"q_type\":\"actor\"}\n" +
        "Q: 'Tom Hanks' → {\"intent\":\"LOOKUP\",\"q_subject\":\"Tom Hanks\",\"q_type\":\"actor\"}\n" +
        "Q: 'Tuấn Trần' → {\"intent\":\"LOOKUP\",\"q_subject\":\"Tuấn Trần\",\"q_type\":\"actor\"}\n" +
        "Q: 'phim của Trấn Thành' → {\"intent\":\"FILTER\",\"f_actor\":\"Trấn Thành\"}\n" +
        "Q: 'phim của đạo diễn Trấn Thành' → {\"intent\":\"FILTER\",\"f_director\":\"Trấn Thành\"}\n" +
        "Q: 'Trấn Thành đóng phim gì' → {\"intent\":\"FILTER\",\"f_actor\":\"Trấn Thành\"}\n" +
        "Q: 'đạo diễn phim Bố Già' → {\"intent\":\"LOOKUP\",\"q_subject\":\"Bố Già\",\"q_type\":\"director\"}\n" +
        "Q: 'diễn viên phim Bố Già' → {\"intent\":\"LOOKUP\",\"q_subject\":\"Bố Già\",\"q_type\":\"cast\"}\n" +
        "Q: 'diễn viên phim Mai của Trấn Thành' → {\"intent\":\"LOOKUP\",\"q_subject\":\"Mai\",\"q_context\":\"Trấn Thành\",\"q_type\":\"actor\"}\n" +
        "Q: 'phim gì hot nhất' → {\"intent\":\"TRENDING\"}\n\n" +
        
        "Câu hỏi: \"%s\"\nJSON:";

    //---- LOGIC XỬ LÝ CHÍNH ----

    public Map<String, Object> processMessage(String message, String conversationId) throws Exception {
        if (isUnsafe(message)) return createResponse("Xin lỗi, nội dung này vi phạm chính sách an toàn của FFilm.");
        if (!isConfigured()) throw new Exception("Gemini API key chưa cấu hình");

        String aiResponseText = "Xin lỗi, tôi chưa thể xử lý yêu cầu này.";
        ConversationContext context = conversationCache.get(conversationId, ConversationContext.class);
        if (context == null) context = new ConversationContext();

        // Strip quotes & normalize
        message = message.replace("\"", "").replace("'", "").trim();
        String cleanMsg = message.toLowerCase();

        // 1. LOCAL SHORTCUTS
        if (cleanMsg.contains("liệt kê") && cleanMsg.contains("thể loại")) {
            return createResponse(formatGenresResponse(genreRepository.findAll(), "tất cả thể loại"));
        }
        if (cleanMsg.contains("gói cước") || cleanMsg.contains("bao nhiêu tiền")) {
            return createResponse("Hiện tại, thông tin về các gói cước của FFilm đang trong quá trình cập nhật. Bạn vui lòng theo dõi trang chủ để biết thêm chi tiết nhé!");
        }
        if (cleanMsg.contains("bạn là ai") || cleanMsg.equals("hi") || cleanMsg.equals("xin chào")) {
            return createResponse("Chào bạn! Tôi là trợ lý ảo chuyên về phim ảnh của FFilm. Tôi có thể giúp bạn tìm phim, tra cứu thông tin diễn viên và nhiều hơn nữa.");
        }

        // 2. CONTEXT CHECK - IMPROVED
        boolean isFollowUp = context.getLastQuestionAsked() != null && 
            (cleanMsg.equals("có") || cleanMsg.equals("co") ||
            cleanMsg.equals("ok") || cleanMsg.equals("oke") ||
            cleanMsg.equals("ừ") || cleanMsg.equals("u") ||
            cleanMsg.equals("xem thêm") || cleanMsg.equals("xem them") ||
            cleanMsg.equals("còn nữa không") || cleanMsg.equals("con nua khong") ||
            cleanMsg.equals("có nữa không") || cleanMsg.equals("co nua khong") ||
            cleanMsg.equals("tiếp") || cleanMsg.equals("tiep"));

        if (isFollowUp) {
            aiResponseText = handleFollowUp(context, cleanMsg);
            conversationCache.put(conversationId, context);
            return createResponse(aiResponseText);
        }

        // 3. AI PROCESSING (Phase 8)
        try {
            String prompt = String.format(FLAT_PROMPT, message);
            JSONObject request = buildGeminiRequest_Simple(prompt);
            JSONObject response = callGeminiAPI(request);
            String jsonText = extractTextResponse(response);
            System.out.println("🤖 AI Raw Response: " + jsonText);
            
            JSONObject brain = parseJsonSafely(jsonText);
            
            if (brain == null) {
                aiResponseText = runKeywordFallback(message, context);
            } else {
                String intent = brain.optString("intent", "UNKNOWN");
                System.out.println("🔵 Intent: " + intent + " | Brain: " + brain.toString());

                switch (intent) {
                    case "FILTER":
                    case "SEMANTIC": // Gộp chung logic Filter
                        MovieSearchFilters filters = parseFlatFilters(brain);
                        if (filters.hasFilters()) {
                            context = new ConversationContext(); // Reset
                            List<Movie> movies = movieService.findMoviesByFilters(filters);
                            
                            if (!movies.isEmpty()) {
                                context.setLastSubjectType("Filter");
                                context.setLastSubjectId(filters); 
                                context.setLastQuestionAsked("ask_more_filter");
                                
                                aiResponseText = formatMoviesResponse(movies, "yêu cầu của bạn", context);
                                
                                if (filters.getDirector() != null) updateContext(context, "Person", filters.getDirector(), "ask_director_movies");
                                else if (filters.getActor() != null) updateContext(context, "Person", filters.getActor(), "ask_person_movies");
                            } else {
                                aiResponseText = runKeywordFallback(message, context);
                            }
                        } else {
                            aiResponseText = runKeywordFallback(message, context);
                        }
                        break;

                    case "LOOKUP":
                        String subject = brain.optString("q_subject");
                        String contextName = brain.optString("q_context");
                        String qType = brain.optString("q_type");
                        
                        Movie targetMovie = movieService.findMovieByTitleAndContext(subject, contextName);
                        
                        if (targetMovie == null) {
                            // Nếu không tìm thấy phim, thử tìm người
                            List<Person> persons = personRepository.findByFullNameContainingIgnoreCase(subject);
                            if (!persons.isEmpty()) {
                                context = new ConversationContext();
                                aiResponseText = formatPersonsResponse(persons, subject, context);
                            } else {
                                aiResponseText = runKeywordFallback(subject, context);
                            }
                        } else {
                            context = new ConversationContext();
                            
                            if ("director".equals(qType)) {
                                String d = targetMovie.getDirector();
                                if (d != null && !d.isEmpty()) {
                                    aiResponseText = formatMovieDetail(targetMovie, context) + 
                                                    "\n\n👉 Đạo diễn: **" + d + "**\n" +
                                                    "Bạn muốn xem thêm phim của đạo diễn này không?";
                                    updateContext(context, "Person", d, "ask_director_movies");
                                } else {
                                    aiResponseText = formatMovieDetail(targetMovie, context) + 
                                                    "\n\n⚠️ Thông tin đạo diễn đang được cập nhật.";
                                }
                            } else if ("actor".equals(qType) || "cast".equals(qType)) {
                                if (!targetMovie.getPersons().isEmpty()) {
                                    String cast = targetMovie.getPersons().stream()
                                        .limit(5)
                                        .map(Person::getFullName)
                                        .collect(Collectors.joining(", "));
                                    aiResponseText = formatMovieDetail(targetMovie, context) + 
                                                    "\n\n🎭 **Diễn viên chính**: " + cast;
                                    
                                    Person firstActor = targetMovie.getPersons().iterator().next();
                                    aiResponseText += "\n\nBạn muốn xem phim khác của " + firstActor.getFullName() + " không?";
                                    updateContext(context, "Person", firstActor.getPersonID(), "ask_person_movies");
                                } else {
                                    aiResponseText = formatMovieDetail(targetMovie, context) + 
                                                    "\n\n⚠️ Thông tin diễn viên đang được cập nhật.";
                                }
                            } else {
                                aiResponseText = formatMovieDetail(targetMovie, context);
                            }
                        }
                        break;
                    case "TRENDING":
                        context = new ConversationContext();
                        context.setLastSubjectType("Trending");
                        context.setLastQuestionAsked("ask_more_trending");
                        aiResponseText = formatMoviesResponse(movieService.getHotMoviesForAI(5), "phim hot nhất hiện tại", context);
                        break;

                    case "QA":
                    case "CHITCHAT":
                        aiResponseText = brain.optString("reply", "Xin chào! Tôi có thể giúp gì cho bạn?");
                        context = new ConversationContext();
                        break;

                    default:
                        aiResponseText = runKeywordFallback(message, context);
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            aiResponseText = runKeywordFallback(message, context);
        }

        conversationCache.put(conversationId, context);
        return createResponse(aiResponseText);
    }
    
    //---- HELPERS ----

    private String handleFollowUp(ConversationContext context, String message) {
        String q = context.getLastQuestionAsked();
        Object id = context.getLastSubjectId();
        
        // Xem thêm Filter
        if ("ask_more_filter".equals(q) && id instanceof MovieSearchFilters) {
             MovieSearchFilters f = (MovieSearchFilters) id;
             List<Movie> m = movieService.findMoviesByFilters(f);
             return formatMoviesResponse(m, "các kết quả tiếp theo", context);
        }
        // Xem thêm Trending
        if ("ask_more_trending".equals(q)) {
             List<Movie> m = movieService.getHotMoviesForAI(20); 
             return formatMoviesResponse(m, "các phim hot khác", context);
        }
        // Gợi ý Đạo diễn
        if ("ask_director_movies".equals(q) && id instanceof String) {
            MovieSearchFilters f = new MovieSearchFilters(); f.setDirector((String) id);
            List<Movie> m = movieService.findMoviesByFilters(f);
            context.setLastSubjectType("Filter"); context.setLastSubjectId(f); context.setLastQuestionAsked("ask_more_filter");
            return formatMoviesResponse(m, "phim của đạo diễn " + id, context);
        }
        // Gợi ý Diễn viên
        if ("ask_person_movies".equals(q)) {
            MovieSearchFilters f = new MovieSearchFilters();
            String name = "";
            if (id instanceof Integer) {
                Person p = personRepository.findById((Integer) id).orElse(null);
                if (p != null) { f.setActor(p.getFullName()); name = p.getFullName(); }
            } else if (id instanceof String) {
                f.setActor((String) id); name = (String) id;
            }
            
            if (f.getActor() != null) {
                List<Movie> m = movieService.findMoviesByFilters(f);
                context.setLastSubjectType("Filter"); context.setLastSubjectId(f); context.setLastQuestionAsked("ask_more_filter");
                return formatMoviesResponse(m, "diễn viên " + name, context);
            }
        }
        
        return runKeywordFallback(message, context);
    }

    private String formatMoviesResponse(List<Movie> movies, String reason, ConversationContext ctx) {
        List<Integer> shownIds = ctx.getShownMovieIds() != null ? ctx.getShownMovieIds() : new ArrayList<>();
        List<Movie> newMovies = movies.stream()
            .filter(m -> !shownIds.contains(m.getMovieID()))
            .limit(5)
            .collect(Collectors.toList());

        if (newMovies.isEmpty()) return "Đã hết phim để hiển thị cho yêu cầu này rồi ạ.";

        StringBuilder sb = new StringBuilder("FFilm tìm thấy " + newMovies.size() + " phim (" + reason + "):\n");
        for (Movie m : newMovies) {
            sb.append("• ").append(m.getTitle()).append(" (Rating: ").append(m.getRating()).append(")\n");
            ctx.addShownMovieId(m.getMovieID());
        }
        
        if (!newMovies.isEmpty()) {
             sb.append("\n(Gõ 'xem thêm' để xem các kết quả khác...)");
             if (ctx.getLastQuestionAsked() == null) ctx.setLastQuestionAsked("ask_more_filter");
        }
        
        if ("ask_director_movies".equals(ctx.getLastQuestionAsked())) {
             sb.append("\nBạn có muốn xem thêm phim của đạo diễn này không?");
        }

        return sb.toString();
    }

    private String formatMovieDetail(Movie movie, ConversationContext ctx) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("🎬 **").append(movie.getTitle()).append("**\n");
        sb.append("⭐ Rating: ").append(movie.getRating()).append("/10\n");
        
        if (movie.getReleaseDate() != null) {
            sb.append("📅 Năm: ").append(new java.text.SimpleDateFormat("yyyy").format(movie.getReleaseDate())).append("\n");
        }
        
        if (movie.getCountry() != null && !movie.getCountry().isEmpty()) {
            sb.append("🌍 Quốc gia: ").append(movie.getCountry()).append("\n");
        }
        
        if (movie.getDirector() != null && !movie.getDirector().isEmpty()) {
            sb.append("🎥 Đạo diễn: ").append(movie.getDirector()).append("\n");
        }
        
        if (!movie.getPersons().isEmpty()) {
            String cast = movie.getPersons().stream()
                .limit(3)
                .map(Person::getFullName)
                .collect(Collectors.joining(", "));
            sb.append("🎭 Diễn viên: ").append(cast);
            if (movie.getPersons().size() > 3) {
                sb.append(" và ").append(movie.getPersons().size() - 3).append(" người khác");
            }
            sb.append("\n");
        }
        
        if (!movie.getGenres().isEmpty()) {
            String genres = movie.getGenres().stream()
                .map(Genre::getName)
                .collect(Collectors.joining(", "));
            sb.append("🎭 Thể loại: ").append(genres).append("\n");
        }
        
        if (movie.getDescription() != null && !movie.getDescription().isEmpty()) {
            String desc = movie.getDescription();
            if (desc.length() > 150) {
                desc = desc.substring(0, 147) + "...";
            }
            sb.append("\n📝 Mô tả: ").append(desc).append("\n");
        }
        
        // Gợi ý similar movies
        if (movie.getCountry() != null && !movie.getGenres().isEmpty()) {
            Genre firstGenre = movie.getGenres().iterator().next();
            sb.append("\n💡 Xem thêm: 'phim ").append(firstGenre.getName().toLowerCase())
            .append(" ").append(movie.getCountry().toLowerCase()).append("'");
        }
        
        return sb.toString();
    }

    private String formatPersonsResponse(List<Person> persons, String reason, ConversationContext ctx) {
        // FIX: Distinct Persons by ID
        List<Person> distinctPersons = persons.stream()
            .filter(distinctByKey(Person::getPersonID))
            .collect(Collectors.toList());

        List<Person> newPersons = distinctPersons.stream()
            .filter(p -> !ctx.getShownPersonIds().contains(p.getPersonID()))
            .limit(5)
            .collect(Collectors.toList());
            
        if (newPersons.isEmpty()) return "Không tìm thấy thông tin.";

        StringBuilder sb = new StringBuilder("Tìm thấy " + newPersons.size() + " người (" + reason + "):\n");
        for (Person p : newPersons) {
            sb.append("• ").append(p.getFullName()).append("\n");
            ctx.addShownPersonId(p.getPersonID());
        }
        
        if (!newPersons.isEmpty()) {
            sb.append("\nBạn có muốn xem các phim của diễn viên đầu tiên (")
            .append(newPersons.get(0).getFullName()).append(") không?");
            updateContext(ctx, "Person", newPersons.get(0).getPersonID(), "ask_person_movies");
        }
        return sb.toString();
    }
    
    private String runKeywordFallback(String msg, ConversationContext ctx) {
        String lower = msg.toLowerCase();
        
        // PRIORITY 1: Tìm phim theo tên (exact/fuzzy)
        List<Movie> moviesByTitle = movieService.searchMoviesByTitle(msg);
        if (!moviesByTitle.isEmpty()) {
            MovieSearchFilters f = new MovieSearchFilters(); 
            f.setKeyword(msg);
            ctx = new ConversationContext();
            ctx.setLastSubjectType("Filter"); 
            ctx.setLastSubjectId(f); 
            ctx.setLastQuestionAsked("ask_more_filter");
            return formatMoviesResponse(moviesByTitle, msg, ctx);
        }
        
        // PRIORITY 2: Tìm người (actor/director)
        List<Person> persons = personRepository.findByFullNameContainingIgnoreCase(msg);
        if (!persons.isEmpty()) {
            ctx = new ConversationContext();
            return formatPersonsResponse(persons, msg, ctx);
        }
        
        // PRIORITY 3: Detect Mood (cao hơn Genre vì specific hơn)
        List<String> moodGenres = detectMood(lower);
        if (!moodGenres.isEmpty()) {
            MovieSearchFilters f = new MovieSearchFilters();
            f.setGenres(moodGenres);
            return executeFilter(f, ctx, "phim phù hợp với tâm trạng của bạn");
        }
        
        // PRIORITY 4: Detect Genre
        List<String> genres = detectGenres(lower);
        if (!genres.isEmpty()) {
            MovieSearchFilters f = new MovieSearchFilters();
            f.setGenres(genres);
            return executeFilter(f, ctx, "phim thể loại " + String.join(", ", genres));
        }
        
        // PRIORITY 5: Detect Country
        String country = detectCountry(lower);
        if (country != null) {
            MovieSearchFilters f = new MovieSearchFilters();
            f.setCountry(normalizeCountryForDB(country));
            return executeFilter(f, ctx, "phim " + country);
        }
        
        // PRIORITY 6: Detect Trending
        if (lower.contains("hot") || lower.contains("xu hướng") || lower.contains("phổ biến") || lower.contains("nổi bật")) {
            ctx = new ConversationContext();
            ctx.setLastSubjectType("Trending");
            ctx.setLastQuestionAsked("ask_more_trending");
            return formatMoviesResponse(movieService.getHotMoviesForAI(5), "phim hot nhất", ctx);
        }
        
        // FINAL FALLBACK
        ctx.setShownMovieIds(new ArrayList<>());
        ctx.setShownPersonIds(new ArrayList<>());
        ctx.setLastQuestionAsked(null);
        
        return "Rất tiếc, FFilm không tìm thấy kết quả nào cho '" + msg + "'.\n\n" +
            "💡 Gợi ý:\n" +
            "• Tìm theo thể loại: 'phim hài', 'phim kinh dị', 'phim hành động'\n" +
            "• Tìm theo quốc gia: 'phim hàn quốc', 'phim việt nam', 'phim mỹ'\n" +
            "• Tìm theo tâm trạng: 'tôi đang buồn', 'tôi cần động lực', 'muốn cười'\n" +
            "• Tìm theo tên: 'Thanh Gươm Diệt Quỷ', 'Trấn Thành'";
    }

    // Helper method - THÊM MỚI sau runKeywordFallback()
    private String executeFilter(MovieSearchFilters f, ConversationContext ctx, String reason) {
        ctx = new ConversationContext();
        List<Movie> movies = movieService.findMoviesByFilters(f);
        
        if (movies.isEmpty()) {
            return "Rất tiếc, hiện tại FFilm chưa có " + reason + " trong kho.\n\n" +
                "💡 Thử tìm kiếm khác:\n" +
                "• Thay đổi thể loại hoặc quốc gia\n" +
                "• Xem phim hot: 'phim gì hot nhất'";
        }
        
        ctx.setLastSubjectType("Filter");
        ctx.setLastSubjectId(f);
        ctx.setLastQuestionAsked("ask_more_filter");
        
        // Gợi ý similar movies nếu có country + genre
        String suggestion = "";
        if (f.getCountry() != null && f.getGenres() != null && !f.getGenres().isEmpty()) {
            suggestion = "\n\n💡 Có thể bạn cũng thích: 'phim " + f.getGenres().get(0).toLowerCase() + " " + f.getCountry().toLowerCase() + "'";
        }
        
        return formatMoviesResponse(movies, reason, ctx) + suggestion;
    }
    
    //---- UTILS ----
    
    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    private JSONObject parseJsonSafely(String text) {
        try {
            // Strip markdown và whitespace
            text = text.replaceAll("```json|```", "").trim();
            
            int start = text.indexOf("{");
            int end = text.lastIndexOf("}");
            
            if (start >= 0 && end > start) {
                String jsonStr = text.substring(start, end + 1);
                JSONObject json = new JSONObject(jsonStr);
                
                // Validate có intent
                if (json.has("intent")) {
                    System.out.println("✅ Parsed JSON: " + json.toString());
                    return json;
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ JSON parse error: " + e.getMessage());
        }
        
        System.err.println("❌ Failed to parse: " + text.substring(0, Math.min(text.length(), 100)));
        return null;
    }
    
    // Trong parseFlatFilters(), THAY THẾ
    private MovieSearchFilters parseFlatFilters(JSONObject j) {
        MovieSearchFilters f = new MovieSearchFilters();
        if (j == null) return f;
        try {
            // Normalize country từ AI
            if (j.has("f_country")) {
                String aiCountry = j.getString("f_country");
                f.setCountry(normalizeCountryForDB(aiCountry));
            }
            
            if (j.has("f_genres")) {
                List<String> g = new ArrayList<>();
                JSONArray a = j.optJSONArray("f_genres");
                if (a!=null) for(int i=0; i<a.length(); i++) g.add(a.getString(i));
                f.setGenres(g);
            }
            
            if (j.has("f_year_from")) f.setYearFrom(j.optInt("f_year_from"));
            if (j.has("f_year_to")) f.setYearTo(j.optInt("f_year_to")); // THÊM
            if (j.has("f_director")) f.setDirector(j.optString("f_director"));
            if (j.has("f_actor")) f.setActor(j.optString("f_actor"));
            if (j.has("keyword")) f.setKeyword(j.optString("keyword"));

            // Debug log
            System.out.println("🔍 Filters parsed: country=" + f.getCountry() + 
                ", genres=" + f.getGenres() + ", year=" + f.getYearFrom() + 
                ", actor=" + f.getActor());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return f;
    }

    private void updateContext(ConversationContext ctx, String type, Object id, String question) {
        ctx.setLastSubjectType(type); ctx.setLastSubjectId(id); ctx.setLastQuestionAsked(question);
    }

    //---- DETECTION HELPERS ----

    private String detectCountry(String text) {
        String lower = text.toLowerCase();
        
        for (Map.Entry<String, List<String>> entry : COUNTRY_MAPPING.entrySet()) {
            for (String alias : entry.getValue()) {
                // Word boundary check để tránh "Tom Hanks" match "han"
                if (alias.length() <= 3) {
                    // Short aliases cần word boundary
                    if (lower.matches(".*\\b" + alias + "\\b.*")) {
                        return entry.getKey();
                    }
                } else {
                    // Long aliases dùng contains
                    if (lower.contains(alias)) {
                        return entry.getKey();
                    }
                }
            }
        }
        return null;
    }

    private List<String> detectGenres(String text) {
        String lower = text.toLowerCase();
        List<String> detected = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : GENRE_MAPPING.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    detected.add(entry.getKey());
                    break;
                }
            }
        }
        return detected;
    }

    private List<String> detectMood(String text) {
        String lower = text.toLowerCase();
        for (Map.Entry<String, List<String>> entry : MOOD_MAPPING.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    return MOOD_TO_GENRES.getOrDefault(entry.getKey(), List.of());
                }
            }
        }
        return List.of();
    }

    private String normalizeCountryForDB(String userCountry) {
        // Map user input → DB value (xử lý variants)
        switch (userCountry) {
            case "South Korea": return "Korea"; // TMDB có thể lưu "Korea" hoặc "South Korea"
            case "Viet Nam": return "Vietnam"; // Chuẩn hóa
            case "United States": return "United States of America";
            default: return userCountry;
        }
    }

    private JSONObject buildGeminiRequest_Simple(String prompt) {
        JSONObject body = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        JSONObject part = new JSONObject();
        part.put("text", prompt);
        parts.put(part);
        content.put("parts", parts);
        contents.put(content);
        body.put("contents", contents);
        JSONObject config = new JSONObject();
        config.put("temperature", 0.1); config.put("maxOutputTokens", 2048);
        body.put("generationConfig", config);
        JSONArray safety = new JSONArray();
        safety.put(new JSONObject().put("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT").put("threshold", "BLOCK_LOW_AND_ABOVE"));
        body.put("safetySettings", safety);
        return body;
    }

    private JSONObject callGeminiAPI(JSONObject body) throws Exception {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);
            ResponseEntity<String> resp = restTemplate.exchange(GEMINI_API_URL + geminiApiKey, HttpMethod.POST, entity, String.class);
            return new JSONObject(resp.getBody());
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) throw new Exception("Hệ thống đang bận, vui lòng thử lại sau giây lát.");
            throw e;
        }
    }

    private String extractTextResponse(JSONObject json) {
        try { return json.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text"); } catch (Exception e) { return ""; }
    }
    
    private String formatGenresResponse(List<Genre> genres, String reason) {
        StringBuilder sb = new StringBuilder("Danh sách " + reason + ":\n");
        genres.forEach(g -> sb.append("• ").append(g.getName()).append("\n"));
        return sb.toString();
    }

    public boolean isConfigured() { return geminiApiKey != null && !geminiApiKey.isEmpty(); }
    private void loadWebsiteContext() {} 
    private Map<String, Object> createResponse(String msg) { return Map.of("success", true, "message", msg, "type", "website", "timestamp", System.currentTimeMillis()); }
}