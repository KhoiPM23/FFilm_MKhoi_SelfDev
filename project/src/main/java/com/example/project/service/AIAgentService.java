package com.example.project.service;

// IMPORT MỚI (PHASE 1)
import com.example.project.dto.MovieSearchFilters; 
import com.example.project.model.Genre;
import com.example.project.model.Movie;
import com.example.project.model.Person;
import com.example.project.model.SubscriptionPlan;
import com.example.project.repository.GenreRepository;
import com.example.project.repository.MovieRepository;
import com.example.project.repository.PersonRepository;
import com.example.project.repository.SubscriptionPlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AIAgentService {

    //---- 1. CẤU HÌNH & REPOSITORY INJECTION ----
    
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

    private Map<String, Object> websiteContext;

    @Autowired
    public AIAgentService(
            @Value("${gemini.api.key:}") String geminiApiKey,
            RestTemplate restTemplate,
            SubscriptionPlanRepository planRepository,
            MovieRepository movieRepository,
            GenreRepository genreRepository,
            PersonRepository personRepository,
            MovieService movieService
    ) {
        this.geminiApiKey = geminiApiKey;
        this.restTemplate = restTemplate;
        this.planRepository = planRepository;
        this.movieRepository = movieRepository;
        this.genreRepository = genreRepository;
        this.personRepository = personRepository;
        this.movieService = movieService;
        
        loadWebsiteContext();
    }

    //---- 2. CORE PROCESSING LOGIC (PHASE 2) ----

    // VĐ 5 (Fix): Blacklist tự định nghĩa
    private static final Set<String> BLACKLISTED_KEYWORDS = Set.of(
        "sex", "tình dục", "xxx", "porn", "khỏa thân", "khiêu dâm"
    );

    // VĐ 5 (Fix): Hàm kiểm tra an toàn
    private boolean isUnsafe(String message) {
        String lowerCaseMessage = message.toLowerCase();
        for (String keyword : BLACKLISTED_KEYWORDS) {
            if (lowerCaseMessage.contains(keyword)) {
                System.err.println("❌ SAFETY BLOCK (Local): " + keyword);
                return true;
            }
        }
        return false;
    }

    /**
     * PROMPT MỚI (PHASE 1.5)
     * VĐ 2: Thêm yêu cầu dịch tên quốc gia
     */
    private static final String FILTER_EXTRACTION_PROMPT =
        "Phân tích câu hỏi của người dùng và trích xuất filters tìm kiếm phim.\n" +
        "QUAN TRỌNG: Chỉ trả lời bằng JSON thuần (không có markdown \\`\\`\\`json).\n" +
        "QUAN TRỌNG (VĐ 2): Dịch tên quốc gia tiếng Việt sang tên quốc gia tiếng Anh (Hàn Quốc -> South Korea, Nhật Bản -> Japan, Việt Nam -> Vietnam, Trung Quốc -> China, Mỹ -> USA).\n\n" +
        "{\n" +
        "  \"intent\": \"ADVANCED_SEARCH\",\n" +
        "  \"filters\": {\n" +
        "    \"keyword\": \"từ khóa chính (nếu có)\",\n" +
        "    \"genres\": [\"thể loại 1\", \"thể loại 2\"],\n" +
        "    \"country\": \"tên quốc gia (tiếng Anh)\",\n" + // <-- Sửa (VĐ 2)
        "    \"yearFrom\": năm,\n" +
        "    \"yearTo\": năm,\n" +
        "    \"minRating\": số (0.0-10.0),\n" +
        "    \"minDuration\": phút,\n" +
        "    \"maxDuration\": phút,\n" +
        "    \"director\": \"tên đạo diễn\",\n" +
        "    \"actor\": \"tên diễn viên\"\n" +
        "  }\n" +
        "}\n\n" +
        "Ví dụ:\n" +
        "- \"Phim Hàn Quốc tình cảm sau 2020\" -> {\"intent\":\"ADVANCED_SEARCH\",\"filters\":{\"genres\":[\"Lãng mạn\", \"Tình cảm\"],\"country\":\"South Korea\",\"yearFrom\":2020}}\n" + // <-- Sửa (VĐ 2)
        "- \"Phim của Nolan dưới 150 phút\" -> {\"intent\":\"ADVANCED_SEARCH\",\"filters\":{\"director\":\"Nolan\",\"maxDuration\":150}}\n" +
        "- \"Anime rating trên 8.0\" -> {\"intent\":\"ADVANCED_SEARCH\",\"filters\":{\"genres\":[\"Hoạt hình\"],\"minRating\":8.0}}\n" +
        "- \"Phim gì hay\" -> {\"intent\":\"Q_A\",\"filters\":{}}\n" +
        "- \"Gói cước FFilm\" -> {\"intent\":\"Q_A\",\"filters\":{}}\n\n" +
        "Câu hỏi: \"%s\"\n";

    
    /**
     * PROMPT MỚI (PHASE 2): Phân loại Intent
     */
    private static final String INTENT_ROUTER_PROMPT =
        "Phân tích câu hỏi của người dùng và TRẢ VỀ 1 trong 4 intent sau:\n" +
        "1. INTENT_QA: Nếu hỏi về thông tin FFilm (gói cước, chính sách, là gì...).\n" +
        "2. INTENT_LIST_ALL: Nếu yêu cầu liệt kê TẤT CẢ (thể loại, diễn viên...).\n" +
        "3. INTENT_SEMANTIC_SEARCH: Nếu tìm phim theo cảm xúc, tâm trạng, mô tả (buồn, vui, truyền động lực, xem giải trí...).\n" +
        "4. INTENT_FILTER_SEARCH: Nếu tìm phim theo tiêu chí cụ thể (quốc gia, diễn viên, đạo diễn, năm, thể loại, từ khóa tên phim).\n\n" +
        "Ví dụ:\n" +
        "- \"gói cước bao nhiêu?\" -> INTENT_QA\n" +
        "- \"liệt kê hết tất cả thể loại\" -> INTENT_LIST_ALL\n" +
        "- \"tôi đang buồn\" -> INTENT_SEMANTIC_SEARCH\n" +
        "- \"phim hàn quốc\" -> INTENT_FILTER_SEARCH\n" +
        "- \"phim của tom hanks\" -> INTENT_FILTER_SEARCH\n" +
        "- \"Thanh Gươm Diệt Quỷ\" -> INTENT_FILTER_SEARCH\n\n" +
        "Câu hỏi: \"%s\"\n" +
        "TRẢ VỀ INTENT (chỉ 1 từ): ";
    
    /**
     * PROMPT MỚI (PHASE 3): Map tâm trạng sang thể loại
     */
    private static final String SEMANTIC_MAP_PROMPT =
        "Map câu mô tả tâm trạng của người dùng sang các THỂ LOẠI phim phù hợp nhất trong danh sách sau: " +
        "[Hành động, Phiêu lưu, Hoạt hình, Hài, Hình sự, Tài liệu, Chính kịch, Gia đình, Giả tưởng, Lịch sử, Kinh dị, Nhạc, Bí ẩn, Lãng mạn, Khoa học viễn tưởng, Gây cấn, Chiến tranh].\n" +
        "TRẢ VỀ JSON: {\"genres\": [\"Thể loại 1\", \"Thể loại 2\"]}\n\n" +
        "Ví dụ:\n" +
        "- \"tôi đang buồn\" -> {\"genres\": [\"Chính kịch\", \"Lãng mạn\"]}\n" +
        "- \"truyền động lực\" -> {\"genres\": [\"Chính kịch\", \"Tài liệu\"]}\n" +
        "- \"giải trí nhẹ nhàng\" -> {\"genres\": [\"Hài\", \"Hoạt hình\", \"Gia đình\"]}\n" +
        "- \"có thêm kiến thức\" -> {\"genres\": [\"Tài liệu\", \"Lịch sử\"]}\n\n" +
        "Câu hỏi: \"%s\"\n" +
        "JSON: ";

    
    /**
     * HÀM NÀY BỊ THIẾU Ở LƯỢT TRƯỚC (FIX LỖI BIÊN DỊCH)
     * Trích xuất filter (Gọi Gemini)
     */
    private MovieSearchFilters extractFilters(String userMessage) {
        try {
            String prompt = String.format(FILTER_EXTRACTION_PROMPT, userMessage);
            // VĐ 5: Gọi hàm build request ĐÃ CÓ safetySettings
            JSONObject requestBody = buildGeminiRequest_Simple(prompt); 
            JSONObject response = callGeminiAPI(requestBody);
            String jsonText = extractTextResponse(response);
            
            // Parse JSON
            jsonText = jsonText.replace("```json", "").replace("```", "").trim();
            JSONObject json = new JSONObject(jsonText);
            
            if (!"ADVANCED_SEARCH".equals(json.optString("intent"))) {
                return null; // Không phải advanced search
            }
            
            JSONObject filtersJson = json.optJSONObject("filters");
            if (filtersJson == null) return null;

            MovieSearchFilters filters = new MovieSearchFilters();
            
            // Map JSON -> DTO
            if (filtersJson.has("keyword")) filters.setKeyword(filtersJson.optString("keyword"));
            if (filtersJson.has("country")) filters.setCountry(filtersJson.optString("country"));
            if (filtersJson.has("yearFrom")) filters.setYearFrom(filtersJson.optInt("yearFrom"));
            if (filtersJson.has("yearTo")) filters.setYearTo(filtersJson.optInt("yearTo"));
            if (filtersJson.has("minRating")) filters.setMinRating((float) filtersJson.optDouble("minRating"));
            if (filtersJson.has("minDuration")) filters.setMinDuration(filtersJson.optInt("minDuration"));
            if (filtersJson.has("maxDuration")) filters.setMaxDuration(filtersJson.optInt("maxDuration"));
            if (filtersJson.has("director")) filters.setDirector(filtersJson.optString("director"));
            if (filtersJson.has("actor")) filters.setActor(filtersJson.optString("actor"));

            // Genres (array)
            if (filtersJson.has("genres")) {
                JSONArray genresArray = filtersJson.optJSONArray("genres");
                if (genresArray != null) {
                    List<String> genres = new ArrayList<>();
                    for (int i = 0; i < genresArray.length(); i++) {
                        genres.add(genresArray.optString(i));
                    }
                    filters.setGenres(genres);
                }
            }
            
            // Chỉ trả về nếu có ít nhất 1 filter
            return filters.hasFilters() ? filters : null;
            
        } catch (Exception e) {
            System.err.println("Lỗi extract filters: " + e.getMessage());
            return null; // Lỗi parse JSON hoặc gọi API -> coi như Q&A
        }
    }


    /**
     * HÀM PROCESS MESSAGE MỚI (PHASE 2)
     */
    public Map<String, Object> processMessage(String message, String conversationId) throws Exception {
        System.out.println("========================================");
        System.out.println("🔵 SERVICE: processMessage() (PHASE 2)");
        System.out.println("========================================");
        
        // VĐ 5 (Fix): Chạy Safety Check đầu tiên
        if (isUnsafe(message)) {
            return Map.of(
                "success", true,
                "message", "Xin lỗi, nội dung này vi phạm chính sách an toàn của FFilm.",
                "type", "website",
                "timestamp", System.currentTimeMillis()
            );
        }

        if (!isConfigured()) {
            throw new Exception("Gemini API key chưa cấu hình");
        }

        String aiResponseText = "Xin lỗi, tôi chưa thể xử lý yêu cầu này."; // SỬA LỖI 1 (Fix: Gán giá trị mặc định)

        // BƯỚC 1: Phân loại Intent (Gọi Gemini lần 1)
        String intentPrompt = String.format(INTENT_ROUTER_PROMPT, message);
        JSONObject intentRequest = buildGeminiRequest_Simple(intentPrompt); // Đã có safety
        String intentResult = "INTENT_FILTER_SEARCH"; // Mặc định
        try {
            JSONObject intentResponse = callGeminiAPI(intentRequest);
            intentResult = extractTextResponse(intentResponse).trim().toUpperCase();
        } catch (Exception e) {
            // VĐ 5: Nếu prompt bị chặn (ví dụ: "phim tình dục"), Gemini sẽ báo lỗi.
            if (e.getMessage() != null && e.getMessage().contains("PROMPT_SAFETY_VIOLATION")) {
                intentResult = "INTENT_SAFETY_BLOCK";
            } else {
                System.err.println("Lỗi Router Intent, dùng Filter mặc định. Lỗi: " + e.getMessage());
            }
        }
        
        System.out.println("🔵 Intent Recognized: " + intentResult);

        // BƯỚC 2: Xử lý theo Intent
        try {
            switch (intentResult) {
                
                // PHASE 3 (Free)
                case "INTENT_SEMANTIC_SEARCH":
                    System.out.println("🔵 Handling: SEMANTIC_SEARCH");
                    String semanticPrompt = String.format(SEMANTIC_MAP_PROMPT, message);
                    JSONObject semanticRequest = buildGeminiRequest_Simple(semanticPrompt);
                    JSONObject semanticResponse = callGeminiAPI(semanticRequest);
                    String semanticJsonText = extractTextResponse(semanticResponse);
                    
                    JSONObject semanticJson = new JSONObject(semanticJsonText);
                    JSONArray genresArray = semanticJson.optJSONArray("genres");
                    
                    MovieSearchFilters semanticFilters = new MovieSearchFilters();
                    if (genresArray != null) {
                        List<String> genres = new ArrayList<>();
                        for (int i = 0; i < genresArray.length(); i++) {
                            genres.add(genresArray.optString(i));
                        }
                        semanticFilters.setGenres(genres);
                    }
                    
                    if (semanticFilters.hasFilters()) {
                        List<Movie> movies = movieService.findMoviesByFilters(semanticFilters);
                        aiResponseText = formatMoviesResponse(movies, "phim phù hợp với tâm trạng của bạn");
                    } else {
                        aiResponseText = "Rất tiếc, tôi chưa tìm được thể loại nào phù hợp với tâm trạng của bạn.";
                    }
                    break;

                // PHASE 2
                case "INTENT_LIST_ALL":
                    System.out.println("🔵 Handling: LIST_ALL");
                    List<Genre> allGenres = genreRepository.findAll();
                    aiResponseText = formatGenresResponse(allGenres, "tất cả thể loại"); // Dùng hàm format cũ
                    break;

                // PHASE 1 (Nâng cấp)
                case "INTENT_FILTER_SEARCH":
                    System.out.println("🔵 Handling: FILTER_SEARCH");
                    MovieSearchFilters filters = extractFilters(message); // LỖI BIÊN DỊCH CỦA BẠN (Dòng 223) LÀ VÌ HÀM NÀY BỊ THIẾU
                    
                    if (filters != null && filters.hasFilters()) {
                        System.out.println("🔵 Filters extracted: " + filters.toString());
                        List<Movie> movies = movieService.findMoviesByFilters(filters);
                        
                        if (!movies.isEmpty()) {
                            aiResponseText = formatMoviesResponse(movies, "yêu cầu của bạn");
                        } else {
                            // VĐ 4 (Fix): Nếu filter 0 kết quả, thử Fallback (Tìm diễn viên/phim)
                            System.out.println("⚠️ FILTER_SEARCH 0 kết quả. Thử Fallback VĐ 4...");
                            aiResponseText = runKeywordFallback(filters, message);
                        }
                    } else {
                        // Nếu không trích xuất được filter (ví dụ: "tom hanks")
                        System.out.println("⚠️ Không trích xuất được filter. Thử Fallback (Keyword)...");
                        aiResponseText = runKeywordFallback(null, message);
                    }
                    break;
                
                // VĐ 5 (Fix): Bắt intent an toàn
                case "INTENT_SAFETY_BLOCK":
                    aiResponseText = "Xin lỗi, nội dung này vi phạm chính sách an toàn của FFilm.";
                    break;

                // PHASE 1 (Q&A)
                case "INTENT_QA":
                default:
                    System.out.println("🔵 Handling: Q_A (Default)");
                    String systemPrompt = buildSystemPrompt();
                    String finalPrompt = buildFinalPrompt_QA(systemPrompt, message);
                    JSONObject requestBody = buildGeminiRequest_Simple(finalPrompt);
                    JSONObject geminiResponse = callGeminiAPI(requestBody);
                    aiResponseText = extractTextResponse(geminiResponse);
                    break;
            }
        } catch (Exception e) {
             System.err.println("❌ Lỗi RAG/Gemini: " + e.getMessage());
             if (e.getMessage() != null && e.getMessage().contains("MultipleBagFetchException")) {
                aiResponseText = "Xin lỗi, tôi gặp lỗi khi cố gắng tìm kiếm trên nhiều tiêu chí (thể loại VÀ diễn viên) cùng lúc. Bạn vui lòng thử tìm riêng lẻ (ví dụ: chỉ tìm theo diễn viên, hoặc chỉ tìm theo thể loại) nhé.";
             } else {
                aiResponseText = "Xin lỗi, tôi gặp lỗi khi xử lý yêu cầu: " + e.getMessage();
             }
        }
        
        System.out.println("🟢 Response generated.");

        return Map.of(
            "success", true,
            "message", aiResponseText,
            "type", "website",
            "timestamp", System.currentTimeMillis()
        );
    }
    
    /**
     * HÀM MỚI (VĐ 4 - Fix): Logic Fallback thông minh
     * Ưu tiên tìm Diễn viên nếu filter có diễn viên, ngược lại tìm Phim.
     */
    private String runKeywordFallback(MovieSearchFilters filters, String originalMessage) {
        // Ưu tiên 1: Nếu filter trích xuất được Diễn viên (nhưng tìm 0 phim)
        if (filters != null && filters.getActor() != null && !filters.getActor().isEmpty()) {
            System.out.println("🔵 Fallback VĐ 4: Tìm kiếm Diễn viên (Person)");
            List<Person> persons = personRepository.findByFullNameContainingIgnoreCase(filters.getActor());
            return formatPersonsResponse(persons, filters.getActor());
        }
        
        // Ưu tiên 2: Tìm kiếm Phim theo từ khóa gốc
        System.out.println("🔵 Fallback VĐ 4: Tìm kiếm Phim (Movie)");
        String cleanedMessage = cleanSearchQuery(originalMessage, "phim", "tìm", "gợi ý");
        List<Movie> movies = movieService.searchMoviesByTitle(cleanedMessage);
        return formatMoviesResponse(movies, cleanedMessage); 
    }

    //---- 3. SYSTEM & INTENT HELPERS (Giữ nguyên) ----

    // (Hàm loadWebsiteContext giữ nguyên)
    private void loadWebsiteContext() {
        try {
            ClassPathResource resource = new ClassPathResource("static/data/ai-context.json");
            if (resource.exists()) {
                InputStream is = resource.getInputStream();
                ObjectMapper mapper = new ObjectMapper();
                websiteContext = mapper.readValue(is, Map.class);
                System.out.println("✅ Loaded AI context from JSON");
            } else {
                System.err.println("⚠️ ai-context.json not found");
                websiteContext = getDefaultContext();
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error loading context: " + e.getMessage());
            websiteContext = getDefaultContext();
        }
    }
    
    // (Hàm buildSystemPrompt giữ nguyên - đã bỏ VĐ 3)
    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là trợ lý AI của FFilm, một nền tảng xem phim trực tuyến. ");
        sb.append("QUAN TRỌNG: LUÔN LUÔN trả lời bằng Tiếng Việt.\n\n");
        
        try {
            long movieCount = movieRepository.count();
            long genreCount = genreRepository.count();
            long personCount = personRepository.count();
            
            sb.append("Thông tin hệ thống FFilm (Dữ liệu động):\n");
            sb.append("- Tổng số phim hiện tại: ").append(movieCount).append(" bộ phim.\n");
            sb.append("- Tổng số thể loại: ").append(genreCount).append(" thể loại.\n");
            sb.append("- Tổng số diễn viên/đạo diễn: ").append(personCount).append(" người.\n");
            
            List<SubscriptionPlan> plans = planRepository.findAll();
            if (plans.isEmpty()) {
                 sb.append("- Thông tin gói cước: (Chưa cập nhật)\n");
            } else {
                sb.append("- Các gói đăng ký:\n");
                 plans.forEach(p -> {
                    if (p.isStatus()) { 
                        sb.append("  • ").append(p.getPlanName()).append(": ")
                          .append(String.format("%,.0f", p.getPrice())).append("đ/tháng. (Mô tả: ")
                          .append(p.getDescription()).append(")\n");
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi lấy dữ liệu động cho AI Agent: " + e.getMessage());
        }

        if (websiteContext.containsKey("about")) {
            sb.append("\nVề FFilm: ").append(websiteContext.get("about")).append("\n");
        }
        if (websiteContext.containsKey("policies")) {
            sb.append("\nMột số chính sách quan trọng:\n");
            Map<String, String> policies = (Map<String, String>) websiteContext.get("policies");
            policies.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        }
        if (websiteContext.containsKey("contact")) {
             Map<String, String> contact = (Map<String, String>) websiteContext.get("contact");
             sb.append("\nLiên hệ: Email (").append(contact.get("email")).append(") hoặc Hotline (").append(contact.get("hotline")).append(").\n");
        }
        
        return sb.toString();
    }

    // (Hàm buildFinalPrompt_QA giữ nguyên)
    private String buildFinalPrompt_QA(String systemPrompt, String userMessage) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(systemPrompt);
        prompt.append("\n\n---BẮT ĐẦU YÊU CẦU---\n");
        prompt.append("Câu hỏi của người dùng: \"").append(userMessage).append("\"\n");
        prompt.append("\nHãy trả lời câu hỏi của người dùng dựa trên Kiến thức hệ thống của bạn.");
        prompt.append("\nTrả lời NGẮN GỌN, thân thiện, và LUÔN LUÔN bằng Tiếng Việt.");
        return prompt.toString();
    }

    // (Hàm cleanSearchQuery giữ nguyên)
    private String cleanSearchQuery(String message, String... keywordsToRemove) {
        String cleaned = message.toLowerCase();
        for (String kw : keywordsToRemove) {
            cleaned = cleaned.replace(kw, "");
        }
        cleaned = cleaned.replace("tôi muốn", "").replace("tìm giúp tôi", "").replace("bạn biết gì về", "");
        return cleaned.trim();
    }


    //---- 4. LOCAL RAG FORMATTERS (Giữ nguyên) ----

    // (Hàm formatMoviesResponse giữ nguyên)
    private String formatMoviesResponse(List<Movie> movies, String keyword) {
        if (movies == null || movies.isEmpty()) {
            return "Rất tiếc, FFilm hiện chưa tìm thấy phim nào khớp với " + keyword + ".";
        }
        StringBuilder sb = new StringBuilder("Chào bạn, FFilm tìm thấy " + movies.size() + " phim khớp (dưới đây là 5 phim hàng đầu):\n");
        movies.stream().limit(5).forEach(m -> {
            sb.append("• ").append(m.getTitle())
              .append(" (Rating: ").append(String.format("%.1f", m.getRating())).append(")\n");
        });
        sb.append("\nBạn có thể tìm kiếm tên phim cụ thể để FFilm hỗ trợ tốt hơn nhé!");
        return sb.toString();
    }

    // (Hàm formatPersonsResponse giữ nguyên)
    private String formatPersonsResponse(List<Person> persons, String keyword) {
        if (persons == null || persons.isEmpty()) {
            return "Chào bạn,\nRất tiếc, FFilm hiện chưa tìm thấy thông tin về diễn viên/đạo diễn '" + keyword + "' trong cơ sở dữ liệu của chúng tôi. Bạn có muốn thử tìm kiếm tên khác không?";
        }
        StringBuilder sb = new StringBuilder("Chào bạn, FFilm tìm thấy " + persons.size() + " người khớp (dưới đây là 5 người hàng đầu):\n");
        persons.stream().limit(5).forEach(p -> {
            sb.append("• ").append(p.getFullName())
              .append(" (Nghề nghiệp: ").append(p.getKnownForDepartment()).append(")\n");
        });
        return sb.toString();
    }
    
    // (Hàm formatGenresResponse giữ nguyên)
    private String formatGenresResponse(List<Genre> genres, String keyword) {
        if (genres == null || genres.isEmpty()) {
            return "Rất tiếc, FFilm không tìm thấy thể loại '" + keyword + "'.";
        }
        StringBuilder sb = new StringBuilder("Chào bạn, FFilm tìm thấy " + genres.size() + " thể loại khớp:\n");
        // Sửa lại logic để hiển thị tất cả nếu là "tất cả thể loại"
        if ("tất cả thể loại".equals(keyword)) {
             genres.forEach(g -> {
                sb.append("• ").append(g.getName()).append("\n");
            });
        } else {
            genres.stream().limit(5).forEach(g -> {
                sb.append("• ").append(g.getName()).append("\n");
            });
        }
        return sb.toString();
    }


    //---- 5. GEMINI API UTILS (Đã có VĐ 5 Fix) ----
    
    // (Hàm buildGeminiRequest_Simple giữ nguyên - ĐÃ CÓ VĐ 5)
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
        config.put("temperature", 0.7);
        config.put("maxOutputTokens", 1024);
        body.put("generationConfig", config);

        // THAY ĐỔI (VĐ 5): Thêm bộ lọc an toàn
        JSONArray safetySettings = new JSONArray();
        safetySettings.put(new JSONObject().put("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT").put("threshold", "BLOCK_LOW_AND_ABOVE"));
        safetySettings.put(new JSONObject().put("category", "HARM_CATEGORY_HATE_SPEECH").put("threshold", "BLOCK_LOW_AND_ABOVE"));
        safetySettings.put(new JSONObject().put("category", "HARM_CATEGORY_HARASSMENT").put("threshold", "BLOCK_LOW_AND_ABOVE"));
        safetySettings.put(new JSONObject().put("category", "HARM_CATEGORY_DANGEROUS_CONTENT").put("threshold", "BLOCK_LOW_AND_ABOVE"));
        body.put("safetySettings", safetySettings);

        return body;
    }

    // (Hàm callGeminiAPI giữ nguyên - ĐÃ CÓ VĐ 5)
    private JSONObject callGeminiAPI(JSONObject body) throws Exception {
        try {
            String apiUrl = GEMINI_API_URL + geminiApiKey;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

            System.out.println("🌐 Calling Gemini (v1beta)...");
            ResponseEntity<String> resp = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);
            
            String responseBody = resp.getBody();
            System.out.println("📡 Status: " + resp.getStatusCode());
            
            if (responseBody == null || responseBody.isEmpty()) throw new Exception("Gemini trả về body rỗng");

            JSONObject json = new JSONObject(responseBody);
            
            if (json.has("error")) {
                JSONObject error = json.optJSONObject("error");
                String errMsg = error != null ? error.optString("message", "Unknown") : "Unknown";
                System.err.println("❌ Gemini error: " + errMsg);
                throw new Exception("Gemini error: " + errMsg);
            }
            
            // THAY ĐỔI (VĐ 5): Kiểm tra promptFeedback (nếu input bị chặn)
            if (json.has("promptFeedback")) {
                JSONObject feedback = json.optJSONObject("promptFeedback");
                String reason = (feedback != null) ? feedback.optString("blockReason", "") : "";
                if (!reason.isEmpty()) {
                    System.err.println("❌ Gemini Prompt Blocked: " + reason);
                    // Nếu prompt bị chặn, ném lỗi để trả về thông báo an toàn
                    throw new Exception("PROMPT_SAFETY_VIOLATION");
                }
            }

            return json;

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            System.err.println("❌ HTTP Error: " + e.getStatusCode());
            throw new Exception("Gemini API lỗi: " + e.getResponseBodyAsString());
            
        } catch (Exception e) {
            // Nếu lỗi là do Safety, truyền nó lên
            if ("PROMPT_SAFETY_VIOLATION".equals(e.getMessage())) {
                throw e; 
            }
            System.err.println("❌ Error: " + e.getMessage());
            throw new Exception(e.getMessage() != null ? e.getMessage() : "Lỗi gọi Gemini API");
        }
    }
    
    // (Hàm extractTextResponse giữ nguyên - ĐÃ CÓ VĐ 5)
    private String extractTextResponse(JSONObject jsonResponse) throws Exception {
        try {
            if (jsonResponse.has("error")) return "Xin lỗi, có lỗi xảy ra: " + jsonResponse.getJSONObject("error").getString("message");
            
            JSONArray candidates = jsonResponse.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                JSONObject feedback = jsonResponse.optJSONObject("promptFeedback");
                String reason = (feedback != null) ? feedback.optString("blockReason", "") : "";
                if ("SAFETY".equals(reason)) {
                    System.err.println("❌ Gemini Prompt Blocked (No Candidates)");
                    return "Xin lỗi, nội dung này vi phạm chính sách an toàn của FFilm.";
                }
                throw new Exception("Không tìm thấy 'candidates'.");
            }

            JSONObject candidate = candidates.getJSONObject(0);
            
            String finishReason = candidate.optString("finishReason", "");
            if ("SAFETY".equals(finishReason)) {
                System.err.println("❌ Gemini Response Blocked: SAFETY");
                return "Xin lỗi, nội dung này vi phạm chính sách an toàn của FFilm.";
            }
            if ("MAX_TOKENS".equals(finishReason)) {
                 return "Câu trả lời quá dài, tôi không thể hiển thị hết.";
            }

            JSONObject content = candidate.optJSONObject("content");
            if (content == null) throw new Exception("Không tìm thấy 'content'.");

            JSONArray parts = content.optJSONArray("parts");
            if (parts == null || parts.length() == 0) return "Xin lỗi, tôi gặp lỗi khi xử lý phản hồi từ AI (parts not found).";

            String text = parts.getJSONObject(0).optString("text", "");
            if (text.isEmpty()) {
                return "Xin lỗi, AI trả về phản hồi rỗng.";
            }
            
            return text.trim();
        } catch (Exception e) {
            System.err.println("Lỗi parse AI Response: " + e.getMessage());
            return "Xin lỗi, tôi gặp lỗi khi xử lý phản hồi từ AI (Exception).";
        }
    }

    // (Hàm getDefaultContext giữ nguyên)
    private Map<String, Object> getDefaultContext() {
        return Map.of(
            "about", "FFilm là nền tảng xem phim trực tuyến hàng đầu Việt Nam.",
            "features", Arrays.asList("Thư viện 15,000+ phim", "Chất lượng 4K", "Không quảng cáo"),
            "plans", Collections.emptyList(),
            "policies", Map.of("Hoàn tiền", "14 ngày đầu", "Bảo mật", "SSL 256-bit")
        );
    }

    // (Hàm isConfigured giữ nguyên)
    public boolean isConfigured() {
        return geminiApiKey != null && !geminiApiKey.trim().isEmpty();
    }
}