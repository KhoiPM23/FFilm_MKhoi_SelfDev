package com.example.project.service;

import com.example.project.dto.MovieFavorite;
import com.example.project.dto.MovieSearchFilters;
import com.example.project.model.*;
import com.example.project.repository.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * FFilm AI Movie Intelligence Assistant Service.
 * Chuyên biệt hóa trải nghiệm tra cứu, so sánh, gợi ý và giải đáp điện ảnh
 * dựa trên nguồn dữ liệu có thẩm quyền (grounded truth) của nền tảng FFilm.
 */
@Service
public class AIAgentService {

    private static final Logger log = LoggerFactory.getLogger(AIAgentService.class);

    private final GeminiClient geminiClient;
    private final SubscriptionPlanRepository planRepository;
    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final PersonRepository personRepository;
    private final MoviePersonRepository moviePersonRepository;
    private final UserRepository userRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final FavoriteRepository favoriteRepository;
    private final MovieService movieService;
    private final Cache conversationCache;
    private final AISearchService aiSearchService;
    private final AIChatHistoryRepository chatHistoryRepository;
    private final MovieEntityResolver movieEntityResolver;
    private final MovieRecommendationEngine movieRecommendationEngine;

    // Bộ nhớ in-memory phòng vệ khi Spring Cache không có sẵn hoặc bị vô hiệu hóa
    private final Map<String, ConversationContext> inMemoryContextStore = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private TmdbClient tmdbClient;

    @Autowired
    public AIAgentService(
            GeminiClient geminiClient,
            SubscriptionPlanRepository planRepository,
            MovieRepository movieRepository,
            GenreRepository genreRepository,
            PersonRepository personRepository,
            MoviePersonRepository moviePersonRepository,
            UserRepository userRepository,
            WatchHistoryRepository watchHistoryRepository,
            FavoriteRepository favoriteRepository,
            MovieService movieService,
            CacheManager cacheManager,
            AISearchService aiSearchService,
            AIChatHistoryRepository chatHistoryRepository,
            MovieEntityResolver movieEntityResolver,
            MovieRecommendationEngine movieRecommendationEngine) {
        this.geminiClient = geminiClient;
        this.planRepository = planRepository;
        this.movieRepository = movieRepository;
        this.genreRepository = genreRepository;
        this.personRepository = personRepository;
        this.moviePersonRepository = moviePersonRepository;
        this.userRepository = userRepository;
        this.watchHistoryRepository = watchHistoryRepository;
        this.favoriteRepository = favoriteRepository;
        this.aiSearchService = aiSearchService;
        this.movieService = movieService;
        this.conversationCache = (cacheManager != null) ? cacheManager.getCache("conversationCache") : null;
        this.chatHistoryRepository = chatHistoryRepository;
        this.movieEntityResolver = movieEntityResolver;
        this.movieRecommendationEngine = movieRecommendationEngine;
    }

    // ---- 1. SAFETY & SECURITY POLICY ----
    private static final Set<String> BLACKLISTED_KEYWORDS = Set.of(
            "sex", "tình dục", "xxx", "porn", "khỏa thân", "khiêu dâm", "làm tình", "ấu dâm",
            "chịch", "địt", "đụ", "show hàng");

    private static final Set<String> PROMPT_INJECTION_KEYWORDS = Set.of(
            "ignore previous", "ignore all instructions", "system prompt", "bỏ qua hướng dẫn",
            "tiết lộ prompt", "show me the api key", "api key", "gemini key", "reveal secret",
            "jailbreak", "đóng vai lập trình viên", "act as dan");

    // ---- 2. COUNTRY & GENRE MAPPINGS ----
    private static final Map<String, List<String>> COUNTRY_MAPPING = Map.ofEntries(
            Map.entry("South Korea", List.of("hàn", "han", "korea", "hàn quốc", "han quoc", "남한", "korean")),
            Map.entry("Viet Nam", List.of("việt", "viet", "vietnam", "việt nam", "vn", "vietnamese")),
            Map.entry("United States", List.of("mỹ", "my", "usa", "us", "america", "american", "hollywood")),
            Map.entry("Japan", List.of("nhật", "nhat", "nhật bản", "japan", "japanese", "日本")),
            Map.entry("China", List.of("trung", "trung quốc", "china", "chinese", "中国", "trung hoa")),
            Map.entry("Thailand", List.of("thái", "thai", "thái lan", "thailand")),
            Map.entry("India", List.of("ấn", "ấn độ", "india", "indian", "bollywood")),
            Map.entry("United Kingdom", List.of("anh", "anh quốc", "uk", "britain", "british", "england")),
            Map.entry("France", List.of("pháp", "phap", "france", "french")),
            Map.entry("Germany", List.of("đức", "duc", "germany", "german")));

    private static final Map<String, List<String>> GENRE_MAPPING = Map.ofEntries(
            Map.entry("Hành động", List.of("hành động", "hanh dong", "action", "đánh nhau", "võ thuật", "vo thuat")),
            Map.entry("Hài", List.of("hài", "phim hài", "comedy", "hài hước", "hai huoc", "vui", "funny", "cười")),
            Map.entry("Chính kịch", List.of("chính kịch", "chinh kich", "drama", "tâm lý", "tam ly")),
            Map.entry("Lãng mạn", List.of("lãng mạn", "lang man", "romance", "tình cảm", "tinh cam", "yêu", "love")),
            Map.entry("Kinh dị", List.of("kinh dị", "kinh di", "horror", "ma", "ghost", "sợ hãi", "scary")),
            Map.entry("Khoa học viễn tưởng", List.of("khoa học", "sci-fi", "scifi", "sci fi", "viễn tưởng", "vien tuong", "công nghệ")),
            Map.entry("Gây cấn", List.of("gây cấn", "gay can", "thriller", "kịch tính", "kich tinh", "căng thẳng")),
            Map.entry("Phiêu lưu", List.of("phiêu lưu", "phieu luu", "adventure", "mạo hiểm", "mao hiem")),
            Map.entry("Hoạt hình", List.of("hoạt hình", "hoat hinh", "animation", "anime", "cartoon", "animated")),
            Map.entry("Gia đình", List.of("gia đình", "gia dinh", "family", "trẻ em", "tre em", "kids")),
            Map.entry("Hình sự", List.of("hình sự", "hinh su", "crime", "tội phạm", "toi pham", "gangster")),
            Map.entry("Bí ẩn", List.of("bí ẩn", "bi an", "mystery", "trinh thám", "detective")),
            Map.entry("Tài liệu", List.of("tài liệu", "tai lieu", "documentary", "document")),
            Map.entry("Chiến tranh", List.of("chiến tranh", "chien tranh", "war", "quân sự", "quan su")),
            Map.entry("Lịch sử", List.of("lịch sử", "lich su", "history", "historical")));

    private static final Map<String, List<String>> MOOD_MAPPING = Map.ofEntries(
            Map.entry("SAD", List.of("buồn", "buon", "sad", "depressed", "tâm trạng", "stress", "mệt mỏi", "met moi", "chán", "cô đơn", "thất vọng")),
            Map.entry("ANGRY", List.of("tức", "tuc", "giận", "gian", "angry", "mad", "bực", "buc", "phẫn nộ")),
            Map.entry("SCARED", List.of("sợ", "so", "scared", "afraid", "lo lắng", "anxiety", "hồi hộp")),
            Map.entry("HAPPY", List.of("vui", "happy", "hạnh phúc", "hanh phuc", "sảng khoái")),
            Map.entry("EXCITED", List.of("hứng", "hung", "excited", "năng lượng", "nhiệt huyết")),
            Map.entry("RELAXED", List.of("thư giãn", "thu gian", "relax", "nhẹ nhàng", "nhe nhang", "bình yên", "chill")),
            Map.entry("NEED_MOTIVATION", List.of("động lực", "dong luc", "motivation", "inspire", "cảm hứng", "khuyến khích")),
            Map.entry("NEED_LAUGH", List.of("cười", "cuoi", "laugh", "giải trí", "giai tri", "fun")),
            Map.entry("NEED_THINK", List.of("suy ngẫm", "suy ngam", "think", "triết lý", "triet ly", "ý nghĩa", "deep")),
            Map.entry("NEED_ADRENALINE", List.of("kích thích", "adrenaline", "gay cấn", "hồi hộp", "intense")));

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
            "NEED_ADRENALINE", List.of("Hành động", "Gây cấn", "Kinh dị"));

    // ---- 3. ADVANCED INTENT & ENTITY PARSING PROMPT ----
    private static final String FLAT_PROMPT =
            "Bạn là bộ não phân tích ý định (Intent Parser) của hệ thống Movie Intelligence trên nền tảng FFilm.\n" +
            "Nhiệm vụ: Trích xuất ý định và các thực thể của người dùng thành DUY NHẤT một JSON Object hợp lệ (không kèm markdown ```json).\n\n" +
            "# CÁC INTENT HỖ TRỢ:\n" +
            "1. LOOKUP: Tra cứu thông tin phim hoặc người cụ thể (nội dung/synopsis, đạo diễn, diễn viên, rating, năm chiếu, hoặc thông tin chung).\n" +
            "   Trường: intent='LOOKUP', q_subject (tên phim/người đã chuẩn hóa, sửa lỗi chính tả), original_title (tên tiếng Anh gốc nếu có), vietnamese_title (tên tiếng Việt chính thức tại VN nếu có), q_type='movie'|'person', q_attribute='synopsis'|'director'|'cast'|'rating'|'year'|'general'.\n" +
            "2. COMPARE: So sánh 2 bộ phim với nhau.\n" +
            "   Trường: intent='COMPARE', movie1 (tên phim 1), movie1_orig, movie1_vn, movie2 (tên phim 2), movie2_orig, movie2_vn.\n" +
            "3. SIMILAR_RECOMMEND: Đề xuất các phim tương tự một phim gốc (kèm tiêu chí tùy biến nếu có).\n" +
            "   Trường: intent='SIMILAR_RECOMMEND', base_movie (tên phim gốc), modifier (tiêu chí phụ, vd: 'nhẹ nhàng hơn', 'mới hơn', 'kịch tính hơn', 'rating cao nhất', 'lựa chọn khác'), excluded_genres (mảng string), excluded_directors (mảng string).\n" +
            "4. PERSON_QUERY: Hỏi về sự nghiệp hoặc quan hệ hợp tác của diễn viên/đạo diễn, hoặc các phim của họ.\n" +
            "   Trường: intent='PERSON_QUERY', person_name (chuẩn hóa họ tên), person_name_2 (nếu hỏi đóng chung), role='actor'|'director'|'co_star', query_type='filmography'|'co_star'|'top_rated'|'latest'|'director_of'.\n" +
            "5. USER_PERSONALIZED: Người dùng hỏi gợi ý dựa trên sở thích cá nhân, phim đã xem, phim yêu thích của chính họ.\n" +
            "   Trường: intent='USER_PERSONALIZED'.\n" +
            "6. FILTER: Tìm kiếm phim theo nhiều điều kiện kết hợp (thể loại, quốc gia, năm, điểm số, thời lượng, tâm trạng) hoặc phủ định (không thích thể loại gì, không muốn phim ai).\n" +
            "   Trường: intent='FILTER', f_country, f_genres (mảng String), f_min_rating (float, vd 7.5), f_max_duration (int phút, vd 120), f_year_from, f_year_to, f_actor, f_director, excluded_genres (mảng String), excluded_directors (mảng String), context_action='NARROW'|'SWITCH'|'CONTRADICT'|'BACKTRACK'|'NEW'.\n" +
            "7. TRENDING: Hỏi về top phim thịnh hành, phim hot, phổ biến nhất trên FFilm.\n" +
            "   Trường: intent='TRENDING'.\n" +
            "8. DESCRIPTION_SEARCH: Người dùng mô tả cốt truyện/tình tiết phim nhưng không nhớ tên.\n" +
            "   Trường: intent='DESCRIPTION_SEARCH'.\n" +
            "9. SUBSCRIPTION_INFO: Hỏi về gói cước, giá vé, thanh toán, hủy gói, chính sách hoàn tiền.\n" +
            "   Trường: intent='SUBSCRIPTION_INFO', subscription_query='plans'|'price'|'cancel'|'payment'|'refund'.\n" +
            "10. CHITCHAT: Chào hỏi xã giao, cảm ơn, khen ngợi, hoặc từ chối ngắn ('thôi', 'bỏ', 'ok').\n" +
            "   Trường: intent='CHITCHAT', reply (câu phản hồi thân thiện, ấm áp về FFilm).\n\n" +
            "# NGUYÊN TẮC CHUẨN HÓA THỰC THỂ & SỬA LỖI CHÍNH TẢ (BẮT BUỘC):\n" +
            "- Sửa lỗi chính tả tên phim/người: 'incepion' -> Inception (Kẻ Cắp Giấc Mơ), 'interstelar'/'interstella' -> Interstellar (Hố Đen Tử Thần), 'batmn' -> Batman (Kỵ Sĩ Bóng Đêm), 'spidermn'/'spider man' -> Spider-Man (Người Nhện), 'ke cap giac mo' -> Kẻ Cắp Giấc Mơ, 'phim no lan' -> Christopher Nolan, 'leonardo' -> Leonardo DiCaprio.\n" +
            "- Nếu là phim nước ngoài nổi tiếng, luôn cung cấp cả original_title (tiếng Anh) và vietnamese_title (tên phát hành tại VN) để hệ thống tra cứu đa tầng.\n\n" +
            "# QUY TẮC GIẢI QUYẾT TIÊU CHÍ PHỦ ĐỊNH (NEGATIVE PREFERENCES):\n" +
            "- Nếu người dùng nói: 'không kinh dị', 'đừng kinh dị', 'không thích phim buồn', 'không romance' -> đưa vào excluded_genres.\n" +
            "- Nếu người dùng nói: 'không muốn phim Nolan', 'không phải của Nolan', 'đừng của Christopher Nolan' -> đưa vào excluded_directors.\n" +
            "- Nếu người dùng đổi ý mâu thuẫn (vd trước đó chọn kinh dị, giờ nói 'thực ra tôi ghét kinh dị' hoặc 'thôi không kinh dị nữa') -> context_action='CONTRADICT', đưa 'Kinh dị' vào excluded_genres.\n\n" +
            "# QUY TẮC GIẢI QUYẾT THAM CHIẾU & ĐẠI TỪ (BẮT BUỘC):\n" +
            "- Nếu câu hỏi dùng đại từ ('nó', 'phim này', 'bộ này', 'đây', 'ai đóng', 'nội dung thế nào'): Ưu tiên lấy tên phim từ [NGỮ CẢNH TRANG HIỆN TẠI] (nếu trang là movie_detail) hoặc bộ phim vừa thảo luận gần nhất.\n" +
            "- Nếu câu hỏi nói 'phim thứ hai', 'phim thứ 2', 'cái 2', 'bộ đầu tiên', 'phim cuối': Lấy chính xác tên phim theo số thứ tự từ danh sách đề xuất turn trước.\n" +
            "- Nếu câu hỏi nói 'diễn viên đó', 'người đó', 'ông này', 'anh ấy', 'cô ấy': Lấy tên diễn viên vừa được nhắc tới trong lượt trả lời trước.\n" +
            "- Nếu người dùng đổi ý ('không, phim hàn', 'thôi phim mỹ', 'thực ra cho tôi kinh dị'): Đánh dấu context_action='SWITCH' và cập nhật tiêu chí mới.\n" +
            "- Nếu người dùng thu hẹp tiếp ('scifi', 'phim của hàn quốc', 'mới nhất', 'ngắn thôi', 'dưới 2 tiếng', 'rating trên 7.5'): Đánh dấu context_action='NARROW' và giữ lại các tiêu chí hợp lệ từ trước.\n" +
            "- Nếu người dùng muốn hoàn tác ('quay lại', 'bỏ điều kiện vừa rồi', 'danh sách trước'): Đánh dấu context_action='BACKTRACK'.\n\n" +
            "# LỊCH SỬ HỘI THOẠI & NGỮ CẢNH TRANG HIỆN TẠI:\n" +
            "%s\n\n" +
            "# CÂU HỎI HIỆN TẠI:\n" +
            "\"%s\"\n\n" +
            "JSON:";

    // ---- 4. SAFETY & INJECTION CHECKS ----

    private boolean isUnsafe(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        for (String keyword : BLACKLISTED_KEYWORDS) {
            if (lower.contains(keyword)) return true;
        }
        return lower.matches(".*\\b(sex|porn|xxx|chịch|địt|đụ)\\b.*");
    }

    private boolean isPromptInjection(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        for (String kw : PROMPT_INJECTION_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    // ---- 5. MAIN ORCHESTRATION PIPELINE ----

    public Map<String, Object> processMessage(String message, String conversationId) throws Exception {
        return processMessage(message, conversationId, null, null);
    }

    public Map<String, Object> processMessage(String message, String conversationId, Integer userId) throws Exception {
        return processMessage(message, conversationId, userId, null);
    }

    public Map<String, Object> processMessage(String message, String conversationId, Integer userId, Map<String, Object> pageContext) throws Exception {
        if (isUnsafe(message)) {
            return createResponse("Xin lỗi, nội dung này vi phạm chính sách an toàn của FFilm.", null);
        }
        if (isPromptInjection(message)) {
            return createResponse("Tôi là trợ lý Movie Intelligence độc quyền của FFilm. Tôi sẵn sàng hỗ trợ bạn tìm kiếm phim, diễn viên, đạo diễn và gói cước trên FFilm!", null);
        }
        if (!isConfigured()) {
            throw new Exception("Gemini API key chưa cấu hình");
        }

        ConversationContext context = getOrCreateContext(conversationId);
        if (pageContext != null) {
            context.setLastPageContext(pageContext);
            // Chỉ gán lastFocusedMovie theo pageContext khi người dùng thật sự đang mở trang movie_detail
            if ("movie_detail".equals(pageContext.get("page"))) {
                Object pMovieId = pageContext.get("movieId");
                if (pMovieId != null) {
                    try {
                        int mid = pMovieId instanceof Number ? ((Number) pMovieId).intValue() : Integer.parseInt(pMovieId.toString());
                        movieRepository.findById(mid).ifPresent(m -> {
                            context.setLastFocusedMovie(movieService.convertToMap(m));
                            context.setLastBaseMovie(movieService.convertToMap(m));
                            context.setLastSubjectType("Movie");
                            context.setLastSubjectId(m.getMovieID());
                        });
                    } catch (Exception ignored) {}
                }
            }
        }

        message = message.replace("\"", "").replace("'", "").trim();
        String cleanMsg = message.toLowerCase();

        // 1. Kiểm tra nhanh lệnh huỷ/dừng cực ngắn ("thôi", "bỏ", "dừng lại")
        if (cleanMsg.matches("^(thôi|bỏ|thôi bỏ|thoi|bo|thoi bo|dừng|cancel|thôi xem)$")) {
            context.setLastActiveGenres(new ArrayList<>());
            context.setLastActiveCountry(null);
            context.getExcludedGenres().clear();
            context.getExcludedDirectors().clear();
            return createResponse("Dạ vâng, tôi đã dừng thao tác vừa rồi. Bạn muốn tìm phim theo thể loại nào khác hoặc cần tôi hỗ trợ gì thêm không?", null);
        }

        // 2. Quay lại danh sách ứng viên cũ hoặc trạng thái trước đó (Backtracking)
        if (cleanMsg.matches(".*(quay lại|undo|bỏ điều kiện|bo dieu kien|danh sách lúc nãy|danh sách cũ|danh sách trước).*")) {
            if (context.popStateSnapshot()) {
                List<Map<String, Object>> restored = context.getLastCandidateMovies();
                if (restored != null && !restored.isEmpty()) {
                    saveContext(conversationId, context);
                    return createResponse("Đã khôi phục lại danh sách và các tiêu chí tìm kiếm trước đó của bạn:", restored);
                }
            } else if (context.getLastCandidateMovies() != null && !context.getLastCandidateMovies().isEmpty()) {
                return createResponse("Dưới đây là danh sách phim bạn vừa xem lúc nãy:", context.getLastCandidateMovies());
            }
        }

        // 3. Quay lại phim ban đầu ("thôi xem phim ban đầu", "phim ban đầu")
        if (cleanMsg.matches(".*(phim ban đầu|phim gốc|xem phim ban đầu|về phim đầu).*") &&
                context.getLastBaseMovie() != null) {
            Object idObj = context.getLastBaseMovie().get("id");
            if (idObj != null) {
                int bid = ((Number) idObj).intValue();
                Movie bm = movieRepository.findById(bid).orElse(null);
                if (bm != null) {
                    context.setLastFocusedMovie(movieService.convertToMap(bm));
                    return createResponse(formatSpecificMovieAnswer(bm, "general"), List.of(movieService.convertToMap(bm)), buildMovieDetailActions(bm));
                }
            }
        }

        // 4. Kiểm tra nhanh shortcut liệt kê thể loại
        if (cleanMsg.contains("liệt kê") && cleanMsg.contains("thể loại")) {
            return createResponse(formatGenresResponse(genreRepository.findAll(), "tất cả thể loại"), null);
        }

        // 5. Kiểm tra follow-up xem thêm thông thường
        boolean isFollowUp = context.getLastQuestionAsked() != null &&
                (cleanMsg.matches("^(có|co|ok|oke|ờ|u|uh|uhm|được|dc)$") ||
                        cleanMsg.matches(".*(xem thêm|thêm|tiếp|nữa|còn|next).*") ||
                        cleanMsg.matches(".*(còn nữa không|có gì khác).*") ||
                        cleanMsg.matches(".*(của ổng|của bả|của anh ấy|của cô ấy).*"));

        if (isFollowUp) {
            Map<String, Object> followUpResult = handleFollowUp(context, cleanMsg);
            saveContext(conversationId, context);
            return followUpResult;
        }

        // 5b. Kiểm tra ordinal reference ("cái thứ hai", "phim 1", "bộ đầu tiên", "cái thứ 2", "cái cuối")
        if (context.getLastCandidateMovies() != null && !context.getLastCandidateMovies().isEmpty() &&
                cleanMsg.matches(".*(đầu tiên|thứ nhất|thứ 1|thứ hai|thứ 2|cái 1|cái 2|phim 1|phim 2|cái đầu|cái cuối|cái thứ 2|bộ 2|cái kia|bộ thứ 2).*")) {
            String resolvedTitle = resolveOrdinalReference(cleanMsg, context);
            if (resolvedTitle != null && !resolvedTitle.isBlank() && !resolvedTitle.equalsIgnoreCase(cleanMsg)) {
                Map<String, Object> foundCard = context.getLastCandidateMovies().stream()
                        .filter(c -> resolvedTitle.equalsIgnoreCase((String) c.get("title")))
                        .findFirst().orElse(null);
                Movie m = null;
                if (foundCard != null && foundCard.get("id") != null) {
                    int mid = ((Number) foundCard.get("id")).intValue();
                    m = movieRepository.findById(mid).orElse(null);
                }
                if (m == null) {
                    m = movieRepository.findByTitleContainingIgnoreCase(resolvedTitle).stream().findFirst().orElse(null);
                }
                if (m != null) {
                    Map<String, Object> mMap = movieService.convertToMap(m);
                    context.setLastFocusedMovie(mMap);
                    context.setLastBaseMovie(mMap);
                    context.setLastCandidateMovies(List.of(mMap));
                    if (!m.getPersons().isEmpty()) {
                        context.setLastFocusedPerson(m.getPersons().iterator().next().getFullName());
                    }
                    if (m.getDirector() != null && !m.getDirector().isBlank()) {
                        context.setLastFocusedDirector(m.getDirector());
                    }
                    saveContext(conversationId, context);
                    return createResponse(formatSpecificMovieAnswer(m, "general"), List.of(mMap), buildMovieDetailActions(m));
                }
            }
        }

        // 6. Conversational Narrowing Shortcut: Thu hẹp hoặc sắp xếp tập ứng viên đang hiển thị
        if (isGenreOrMoodNarrowing(cleanMsg) && context.getLastCandidateMovies() != null && !context.getLastCandidateMovies().isEmpty()) {
            List<String> detectedG = detectGenres(cleanMsg);
            String detectedC = detectCountry(cleanMsg);

            // Sắp xếp trên tập ứng viên hiện tại
            if (cleanMsg.contains("mới nhất") || cleanMsg.contains("mới hơn") || cleanMsg.contains("gần đây")) {
                context.pushStateSnapshot();
                List<Map<String, Object>> sorted = new ArrayList<>(context.getLastCandidateMovies());
                sorted.sort((a, b) -> {
                    String ya = String.valueOf(a.getOrDefault("year", "0"));
                    String yb = String.valueOf(b.getOrDefault("year", "0"));
                    return yb.compareTo(ya);
                });
                context.setLastCandidateMovies(sorted);
                return createResponse("Đã sắp xếp danh sách theo phim phát hành mới nhất:", sorted);
            }
            if (cleanMsg.contains("điểm cao") || cleanMsg.contains("rating cao") || cleanMsg.contains("hay hơn")) {
                context.pushStateSnapshot();
                List<Map<String, Object>> sorted = new ArrayList<>(context.getLastCandidateMovies());
                sorted.sort((a, b) -> {
                    double ra = Double.parseDouble(String.valueOf(a.getOrDefault("rating", "0")));
                    double rb = Double.parseDouble(String.valueOf(b.getOrDefault("rating", "0")));
                    return Double.compare(rb, ra);
                });
                context.setLastCandidateMovies(sorted);
                return createResponse("Đã sắp xếp danh sách theo điểm đánh giá cao nhất:", sorted);
            }

            if (!detectedG.isEmpty() || detectedC != null) {
                MovieSearchFilters filters = new MovieSearchFilters();
                if (!detectedG.isEmpty()) {
                    filters.setGenres(detectedG);
                    context.setLastActiveGenres(detectedG);
                } else if (context.getLastActiveGenres() != null && !context.getLastActiveGenres().isEmpty()) {
                    filters.setGenres(new ArrayList<>(context.getLastActiveGenres()));
                }

                if (detectedC != null) {
                    filters.setCountry(normalizeCountryForDB(detectedC));
                    context.setLastActiveCountry(detectedC);
                } else if (context.getLastActiveCountry() != null) {
                    filters.setCountry(normalizeCountryForDB(context.getLastActiveCountry()));
                }

                List<Movie> matched = movieService.findMoviesByFilters(filters);
                if (!matched.isEmpty()) {
                    context.pushStateSnapshot();
                    List<Map<String, Object>> cards = matched.stream().limit(6)
                            .map(movieService::convertToMap)
                            .collect(Collectors.toList());
                    context.setLastCandidateMovies(cards);
                    context.setLastActiveIntent("FILTER");

                    StringBuilder answer = new StringBuilder("Đã thu hẹp danh sách");
                    if (filters.getGenres() != null && !filters.getGenres().isEmpty()) {
                        answer.append(" thể loại **").append(String.join(", ", filters.getGenres())).append("**");
                    }
                    if (filters.getCountry() != null) {
                        answer.append(" của **").append(filters.getCountry()).append("**");
                    }
                    answer.append(" cho bạn:");

                    List<Map<String, Object>> narrowingActions = List.of(
                            Map.of("type", "CHIP_REPLY", "text", "Điểm đánh giá cao nhất", "label", "⭐ Điểm cao nhất"),
                            Map.of("type", "CHIP_REPLY", "text", "Phát hành mới nhất", "label", "📅 Mới nhất"),
                            Map.of("type", "CHIP_REPLY", "text", "Phim của Mỹ", "label", "🇺🇸 Phim Mỹ"),
                            Map.of("type", "CHIP_REPLY", "text", "Phim của Hàn Quốc", "label", "🇰🇷 Phim Hàn")
                    );
                    saveContext(conversationId, context);
                    return createResponse(answer.toString(), cards, narrowingActions);
                }
            }
        }

        // 7. Phân tích Intent với Multi-Turn History
        String historySummary = buildHistorySummary(conversationId, userId, context);
        JSONObject brain = null;
        try {
            String prompt = String.format(FLAT_PROMPT, historySummary, message);
            JSONObject request = buildGeminiRequest_Simple(prompt);
            JSONObject response = callGeminiAPI(request);
            String jsonText = extractTextResponse(response);
            brain = parseJsonSafely(jsonText);
        } catch (Exception e) {
            log.warn("Gemini intent parsing failed, falling back to local domain heuristics: {}", e.getMessage());
        }

        // 8. Điều phối xử lý nghiệp vụ theo Intent
        Map<String, Object> result;
        if (brain == null) {
            result = runKeywordFallback(message, context, userId);
        } else {
            String intent = brain.optString("intent", "UNKNOWN").toUpperCase();
            log.info("Movie Intelligence Intent: {} | Payload: {}", intent, brain);

            switch (intent) {
                case "LOOKUP":
                    result = handleLookupIntent(brain, context);
                    break;
                case "COMPARE":
                    result = handleCompareIntent(brain, context);
                    break;
                case "SIMILAR_RECOMMEND":
                    result = handleSimilarRecommendIntent(brain, context);
                    break;
                case "PERSON_QUERY":
                    result = handlePersonQueryIntent(brain, context);
                    break;
                case "USER_PERSONALIZED":
                    result = handleUserPersonalizedIntent(brain, context, userId);
                    break;
                case "FILTER":
                case "SEMANTIC":
                    result = handleFilterIntent(brain, context);
                    break;
                case "TRENDING":
                    List<Movie> hot = movieService.getHotMoviesForAI(6);
                    List<Map<String, Object>> hotCards = hot.stream().map(movieService::convertToMap).collect(Collectors.toList());
                    context.setLastCandidateMovies(hotCards);
                    for (Movie m : hot) context.addShownMovieId(m.getMovieID());
                    List<Map<String, Object>> trendingActions = List.of(
                            Map.of("type", "CHIP_REPLY", "text", "⭐ Điểm cao nhất", "label", "⭐ Điểm cao nhất"),
                            Map.of("type", "CHIP_REPLY", "text", "Khoa học viễn tưởng", "label", "🚀 Sci-Fi"),
                            Map.of("type", "CHIP_REPLY", "text", "Phim của Hàn Quốc", "label", "🇰🇷 Phim Hàn")
                    );
                    result = createResponse("Dưới đây là các bộ phim đang thịnh hành và được yêu thích nhất trên FFilm hiện nay:", hotCards, trendingActions);
                    break;
                case "DESCRIPTION_SEARCH":
                    result = handleDescriptionSearchIntent(message, context);
                    break;
                case "SUBSCRIPTION_INFO":
                    result = createResponse(handleSubscriptionQuery(brain.optString("subscription_query", "plans")), null);
                    break;
                case "CHITCHAT":
                    String reply = brain.optString("reply", "Xin chào! Tôi là trợ lý Movie Intelligence của FFilm. Bạn đang tìm phim thể loại gì hôm nay?");
                    result = createResponse(reply, null);
                    break;
                default:
                    result = runKeywordFallback(message, context, userId);
                    break;
            }
        }

        saveContext(conversationId, context);
        return result;
    }

    // ---- 6. DOMAIN INTENT HANDLERS ----

    /**
     * Tra cứu thông tin phim hoặc người (nội dung, đạo diễn, diễn viên, rating, năm).
     */
    private Map<String, Object> handleLookupIntent(JSONObject brain, ConversationContext context) {
        String subject = brain.optString("q_subject", "").trim();
        String origTitle = brain.optString("original_title", "").trim();
        String vnTitle = brain.optString("vietnamese_title", "").trim();
        String attribute = brain.optString("q_attribute", "general").toLowerCase();

        // 1. Giải quyết ordinal reference ("phim thứ 2", "phim đầu tiên", "cái 2") từ context trước
        subject = resolveOrdinalReference(subject, context);

        if (subject.isEmpty() || subject.matches("^(nó|no|phim này|phim nay|bộ này|bo nay|đây|day|phim đó)$")) {
            Map<String, Object> pageCtx = context.getLastPageContext();
            if (pageCtx != null && "movie_detail".equals(pageCtx.get("page")) && pageCtx.get("movieTitle") != null) {
                subject = (String) pageCtx.get("movieTitle");
            } else if (context.getLastFocusedMovie() != null && context.getLastFocusedMovie().get("title") != null) {
                subject = (String) context.getLastFocusedMovie().get("title");
            } else if (context.getLastCandidateMovies() != null && !context.getLastCandidateMovies().isEmpty()) {
                subject = (String) context.getLastCandidateMovies().get(0).get("title");
            }
        }

        // 2. Disambiguation check: Nếu từ khóa có nhiều tác phẩm khác nhau trong kho FFilm (vd: Avatar 1 vs Avatar 2)
        if (origTitle.isEmpty() && vnTitle.isEmpty() && !subject.isEmpty() && subject.length() >= 2) {
            List<Movie> disambigList = movieEntityResolver.disambiguateMovieCandidates(subject);
            if (disambigList.size() > 1) {
                List<Map<String, Object>> cards = disambigList.stream().map(movieService::convertToMap).collect(Collectors.toList());
                context.setLastCandidateMovies(cards);
                StringBuilder sb = new StringBuilder("FFilm tìm thấy **").append(disambigList.size()).append("** tác phẩm phù hợp với từ khóa **\"").append(subject).append("\"**:\n\n");
                for (int i = 0; i < disambigList.size(); i++) {
                    Movie m = disambigList.get(i);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy");
                    String yr = m.getReleaseDate() != null ? sdf.format(m.getReleaseDate()) : "N/A";
                    sb.append(i + 1).append(". **").append(m.getTitle()).append("** (").append(yr).append(") — ⭐ ").append(String.format("%.1f", m.getRating())).append("/10\n");
                }
                sb.append("\nBạn muốn tìm hiểu chi tiết về bộ phim nào? (Ví dụ: 'Phim 1', 'Phim 2')");
                List<Map<String, Object>> actions = new ArrayList<>();
                for (int i = 0; i < disambigList.size(); i++) {
                    Movie m = disambigList.get(i);
                    actions.add(Map.of("type", "CHIP_REPLY", "label", (i + 1) + ". " + m.getTitle(), "text", "Thông tin phim " + m.getTitle()));
                }
                return createResponse(sb.toString(), cards, actions);
            }
        }

        // 3. Tra cứu canonical movie (kết hợp local DB & TMDb Entity Resolution)
        Movie movie = resolveCanonicalMovie(subject, origTitle, vnTitle);

        if (movie != null) {
            context.setLastFocusedMovie(movieService.convertToMap(movie));
            context.setLastBaseMovie(movieService.convertToMap(movie));
            context.setLastSubjectType("Movie");
            context.setLastSubjectId(movie.getMovieID());
            if (movie.getDirector() != null && !movie.getDirector().isEmpty()) {
                context.setLastFocusedDirector(movie.getDirector());
            }
            if (!movie.getPersons().isEmpty()) {
                context.setLastFocusedPerson(movie.getPersons().iterator().next().getFullName());
            }

            String answer = formatSpecificMovieAnswer(movie, attribute);
            List<Map<String, Object>> cards = List.of(movieService.convertToMap(movie));
            context.setLastCandidateMovies(cards);

            List<Map<String, Object>> actions = buildMovieDetailActions(movie);
            return createResponse(answer, cards, actions);
        }

        // 4. Fallback: Tra cứu theo nghệ sĩ / người
        List<Map<String, Object>> personMovies = movieService.searchMoviesCombined(subject);
        if (!personMovies.isEmpty()) {
            context.setLastFocusedPerson(subject);
            context.setLastSubjectType("Person");
            context.setLastSubjectId(subject);
            context.setLastCandidateMovies(personMovies);

            String answer = "Thông tin về nghệ sĩ **" + subject + "** trên FFilm. Dưới đây là các tác phẩm liên quan:";
            return createResponse(answer, personMovies.stream().limit(10).collect(Collectors.toList()));
        }

        // 5. Nếu phim không có trên FFilm: Báo rõ ràng giới hạn danh mục và gợi ý phim hot FFilm
        List<Movie> hotFallback = movieService.getHotMoviesForAI(5);
        String notFoundMsg = "Hiện tại bộ phim hoặc thông tin về **\"" + subject + "\"** chưa có trong kho phim của FFilm. " +
                "Tuy nhiên, FFilm có những tác phẩm đặc sắc đang được yêu thích dưới đây, mời bạn tham khảo nhé:";
        return createResponse(notFoundMsg, hotFallback.stream().map(movieService::convertToMap).collect(Collectors.toList()));
    }

    /**
     * So sánh 2 bộ phim với dữ liệu FFilm hai chiều (năm, thời lượng, rating, thể loại, đạo diễn, diễn viên).
     */
    private Map<String, Object> handleCompareIntent(JSONObject brain, ConversationContext context) {
        String m1Name = brain.optString("movie1", "").trim();
        String m1Orig = brain.optString("movie1_orig", "").trim();
        String m1Vn = brain.optString("movie1_vn", "").trim();

        String m2Name = brain.optString("movie2", "").trim();
        String m2Orig = brain.optString("movie2_orig", "").trim();
        String m2Vn = brain.optString("movie2_vn", "").trim();

        Movie m1 = resolveCanonicalMovie(m1Name, m1Orig, m1Vn);
        Movie m2 = resolveCanonicalMovie(m2Name, m2Orig, m2Vn);

        List<Map<String, Object>> cards = new ArrayList<>();
        if (m1 != null) cards.add(movieService.convertToMap(m1));
        if (m2 != null) cards.add(movieService.convertToMap(m2));
        context.setLastCandidateMovies(cards);

        if (m1 != null && m2 != null) {
            String comparison = generateGroundedComparison(m1, m2);
            List<Map<String, Object>> actions = List.of(
                    Map.of("type", "WATCH", "label", "▶ Xem " + m1.getTitle(), "url", "/movie/detail/" + m1.getMovieID()),
                    Map.of("type", "WATCH", "label", "▶ Xem " + m2.getTitle(), "url", "/movie/detail/" + m2.getMovieID()),
                    Map.of("type", "CHIP_REPLY", "label", "🎬 Phim nào đáng xem hơn?", "text", "Giữa " + m1.getTitle() + " và " + m2.getTitle() + " thì phim nào đáng xem hơn và lý do?")
            );
            return createResponse(comparison, cards, actions);
        } else if (m1 != null || m2 != null) {
            Movie existing = m1 != null ? m1 : m2;
            String missingName = m1 == null ? m1Name : m2Name;
            String answer = "FFilm có phim **" + existing.getTitle() + "** (Rating: " + existing.getRating() +
                    ", Thể loại: " + getMovieGenreNames(existing) + "), nhưng phim **" + missingName +
                    "** hiện chưa có trên nền tảng. Dưới đây là thông tin phim đang có sẵn để bạn thưởng thức:";
            List<Map<String, Object>> actions = List.of(
                    Map.of("type", "WATCH", "label", "▶ Xem " + existing.getTitle(), "url", "/movie/detail/" + existing.getMovieID()),
                    Map.of("type", "CHIP_REPLY", "label", "🎬 Phim tương tự", "text", "Gợi ý các phim tương tự " + existing.getTitle())
            );
            return createResponse(answer, cards, actions);
        } else {
            List<Map<String, Object>> actions = List.of(
                    Map.of("type", "CHIP_REPLY", "label", "🔥 Phim thịnh hành", "text", "Top phim thịnh hành nhất hiện nay trên FFilm"),
                    Map.of("type", "CHIP_REPLY", "label", "⭐ Top điểm cao", "text", "Gợi ý các phim có điểm đánh giá cao nhất")
            );
            return createResponse("Hiện tại cả hai bộ phim **" + m1Name + "** và **" + m2Name +
                    "** đều chưa có trên hệ thống FFilm. Bạn có muốn xem các phim đang thịnh hành không?",
                    movieService.getHotMoviesForAI(5).stream().map(movieService::convertToMap).collect(Collectors.toList()), actions);
        }
    }     /**
     * Gợi ý phim tương tự dựa trên phim gốc + tiêu chí phụ (nhẹ nhàng hơn, mới hơn, v.v.).
     */
    private Map<String, Object> handleSimilarRecommendIntent(JSONObject brain, ConversationContext context) {
        String baseName = brain.optString("base_movie", "").trim();
        String modifier = brain.optString("modifier", "").trim();

        // Parse negative preferences from brain
        JSONArray egJson = brain.optJSONArray("excluded_genres");
        if (egJson != null) {
            for (int i = 0; i < egJson.length(); i++) {
                String eg = egJson.optString(i, "").trim();
                if (!eg.isEmpty()) context.getExcludedGenres().add(eg);
            }
        }
        JSONArray edJson = brain.optJSONArray("excluded_directors");
        if (edJson != null) {
            for (int i = 0; i < edJson.length(); i++) {
                String ed = edJson.optString(i, "").trim();
                if (!ed.isEmpty()) context.getExcludedDirectors().add(ed);
            }
        }

        baseName = resolveOrdinalReference(baseName, context);
        Movie baseMovie = null;

        if (baseName.isEmpty() || baseName.matches("^(nó|no|phim này|phim nay|bộ này|bo nay|đây|day|phim đó|lựa chọn khác|khác|khac)$")) {
            // Ưu tiên giữ nguyên baseMovie đang neo để tránh nhảy context sang phim khác khi user liên tục đổi tiêu chí
            if (context.getLastBaseMovie() != null && context.getLastBaseMovie().get("id") != null) {
                int bid = ((Number) context.getLastBaseMovie().get("id")).intValue();
                baseMovie = movieRepository.findById(bid).orElse(null);
            } else if (context.getLastPageContext() != null && "movie_detail".equals(context.getLastPageContext().get("page")) && context.getLastPageContext().get("movieId") != null) {
                Object midObj = context.getLastPageContext().get("movieId");
                int mid = midObj instanceof Number ? ((Number) midObj).intValue() : Integer.parseInt(midObj.toString());
                baseMovie = movieRepository.findById(mid).orElse(null);
            } else if (context.getLastFocusedMovie() != null && context.getLastFocusedMovie().get("id") != null) {
                int fid = ((Number) context.getLastFocusedMovie().get("id")).intValue();
                baseMovie = movieRepository.findById(fid).orElse(null);
            } else if (context.getLastCandidateMovies() != null && !context.getLastCandidateMovies().isEmpty()) {
                Object cidObj = context.getLastCandidateMovies().get(0).get("id");
                if (cidObj != null) {
                    int cid = ((Number) cidObj).intValue();
                    baseMovie = movieRepository.findById(cid).orElse(null);
                }
            }
        } else {
            baseMovie = resolveCanonicalMovie(baseName, null, null);
        }

        if (baseMovie != null) {
            context.setLastBaseMovie(movieService.convertToMap(baseMovie));
            context.setLastFocusedMovie(movieService.convertToMap(baseMovie));
        }

        // Snapshot current state for backtrack support
        context.pushStateSnapshot();

        // Multi-attribute similarity ranking via MovieRecommendationEngine
        List<Movie> ranked = movieRecommendationEngine.rankSimilarCandidates(baseMovie, modifier, context);
        if (ranked.isEmpty()) {
            ranked = movieService.getHotMoviesForAI(5);
        }

        // Đánh dấu các phim đã hiện để tránh lặp lại ở turn sau
        ranked.forEach(m -> context.addShownMovieId(m.getMovieID()));

        List<Map<String, Object>> cards = ranked.stream()
                .map(movieService::convertToMap)
                .collect(Collectors.toList());
        context.setLastCandidateMovies(cards);

        StringBuilder answer = new StringBuilder();
        if (baseMovie != null) {
            answer.append("Dựa trên bộ phim **").append(baseMovie.getTitle()).append("** (")
                    .append(getMovieGenreNames(baseMovie))
                    .append(modifier.isEmpty() ? "" : ", với tiêu chí **\"" + modifier + "\"**")
                    .append("), FFilm gợi ý cho bạn các tác phẩm nổi bật sau:\n\n");
            if (!ranked.isEmpty()) {
                Movie topPick = ranked.get(0);
                String groundReason = movieRecommendationEngine.buildGroundedExplanation(topPick, baseMovie, modifier);
                answer.append("💡 *Lựa chọn nổi bật*: **").append(topPick.getTitle()).append("** — ").append(groundReason).append("\n\n");
            }
        } else {
            answer.append("Gợi ý các phim đặc sắc tương tự theo phong cách bạn yêu cầu:\n\n");
        }

        List<Map<String, Object>> actions = new ArrayList<>();
        if (baseMovie != null) {
            actions.add(Map.of("type", "WATCH", "label", "▶ Xem " + baseMovie.getTitle(), "url", "/movie/detail/" + baseMovie.getMovieID()));
        }
        actions.add(Map.of("type", "CHIP_REPLY", "label", "🌿 Phim nhẹ hơn", "text", "Có phim nào tương tự nhưng nhẹ nhàng hơn không?"));
        actions.add(Map.of("type", "CHIP_REPLY", "label", "⭐ Điểm cao hơn", "text", "Gợi ý phim tương tự nhưng có rating cao nhất"));
        actions.add(Map.of("type", "CHIP_REPLY", "label", "📅 Mới nhất", "text", "Phim tương tự mới phát hành gần đây"));
        actions.add(Map.of("type", "CHIP_REPLY", "label", "🇰🇷 Phim Hàn Quốc", "text", "Trong số đó có phim nào của Hàn Quốc không?"));

        return createResponse(answer.toString(), cards, actions);
    }

    /**
     * Tra cứu quan hệ diễn viên, đạo diễn hoặc phim đóng chung (Movie ↔ Person Graph Traversal).
     */
    private Map<String, Object> handlePersonQueryIntent(JSONObject brain, ConversationContext context) {
        String p1 = brain.optString("person_name", "").trim();
        String p2 = brain.optString("person_name_2", "").trim();
        String role = brain.optString("role", "").trim();
        String qType = brain.optString("query_type", "filmography").trim();

        // 1. Phim đóng chung giữa 2 người
        if (!p1.isEmpty() && !p2.isEmpty()) {
            List<Movie> commonMovies = movieEntityResolver.findCommonMoviesBetweenPersons(p1, p2);
            if (!commonMovies.isEmpty()) {
                List<Map<String, Object>> cards = commonMovies.stream().map(movieService::convertToMap).collect(Collectors.toList());
                context.setLastCandidateMovies(cards);
                String answer = "Tìm thấy **" + commonMovies.size() + "** tác phẩm có sự hợp tác chung giữa **" +
                        p1 + "** và **" + p2 + "** trên FFilm:";
                return createResponse(answer, cards);
            } else {
                return createResponse("Hiện tại trên FFilm chưa có phim nào có sự góp mặt chung của cả **" +
                        p1 + "** và **" + p2 + "**.", null);
            }
        }

        // 2. Tra cứu phim của 1 người (hỗ trợ phân giải đại từ: "diễn viên đó", "đạo diễn đó", "ông ấy", "cô ấy")
        String personName = !p1.isEmpty() ? p1 : p2;
        if (personName.isEmpty() || personName.matches(".*(diễn viên đó|dien vien do|người đó|nguoi do|anh ấy|cô ấy|ổng|bả|diễn viên này|đạo diễn đó|dao dien do|ông này|ông đó|người này).*")) {
            if ("director".equalsIgnoreCase(role) && context.getLastFocusedDirector() != null) {
                personName = context.getLastFocusedDirector();
            } else if (context.getLastFocusedPerson() != null && !context.getLastFocusedPerson().isEmpty()) {
                personName = context.getLastFocusedPerson();
            } else if (context.getLastFocusedMovie() != null) {
                Object midObj = context.getLastFocusedMovie().get("id");
                if (midObj != null) {
                    try {
                        int mid = midObj instanceof Number ? ((Number) midObj).intValue() : Integer.parseInt(midObj.toString());
                        Movie m = movieRepository.findById(mid).orElse(null);
                        if (m != null) {
                            if ("director".equalsIgnoreCase(role) && m.getDirector() != null && !m.getDirector().isEmpty()) {
                                personName = m.getDirector();
                                context.setLastFocusedDirector(personName);
                            } else if (!m.getPersons().isEmpty()) {
                                personName = m.getPersons().iterator().next().getFullName();
                                context.setLastFocusedPerson(personName);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // Multi-hop Graph Traversal qua MovieEntityResolver
        List<Movie> personMovies = movieEntityResolver.findMoviesByPerson(personName, role);
        if (!personMovies.isEmpty()) {
            context.setLastFocusedPerson(personName);
            if ("director".equalsIgnoreCase(role)) {
                context.setLastFocusedDirector(personName);
            }

            // Xử lý các tiêu chí con: rating cao nhất hoặc mới nhất
            if ("top_rated".equalsIgnoreCase(qType) || brain.toString().contains("hay nhất") || brain.toString().contains("rating cao")) {
                personMovies.sort((a, b) -> Float.compare(b.getRating(), a.getRating()));
            } else if ("latest".equalsIgnoreCase(qType) || brain.toString().contains("mới nhất")) {
                personMovies.sort((a, b) -> {
                    long t1 = a.getReleaseDate() != null ? a.getReleaseDate().getTime() : 0L;
                    long t2 = b.getReleaseDate() != null ? b.getReleaseDate().getTime() : 0L;
                    return Long.compare(t2, t1);
                });
            }

            List<Map<String, Object>> cards = personMovies.stream().limit(10).map(movieService::convertToMap).collect(Collectors.toList());
            context.setLastCandidateMovies(cards);

            String roleLabel = "director".equalsIgnoreCase(role) ? "đạo diễn" : "nghệ sĩ";
            String answer = "Các tác phẩm nổi bật của " + roleLabel + " **" + personName + "** trên hệ thống FFilm:";
            List<Map<String, Object>> actions = List.of(
                    Map.of("type", "CHIP_REPLY", "text", "Gợi ý phim hay nhất của " + personName, "label", "⭐ Phim hay nhất"),
                    Map.of("type", "CHIP_REPLY", "text", "Phim mới nhất của " + personName, "label", "📅 Phim mới nhất")
            );
            return createResponse(answer, cards, actions);
        }

        // Fallback: searchMoviesCombined
        List<Map<String, Object>> fallbackMovies = movieService.searchMoviesCombined(personName);
        if (!fallbackMovies.isEmpty()) {
            context.setLastFocusedPerson(personName);
            context.setLastCandidateMovies(fallbackMovies);
            String answer = "Các tác phẩm liên quan đến nghệ sĩ **" + personName + "** trên FFilm:";
            return createResponse(answer, fallbackMovies.stream().limit(10).collect(Collectors.toList()));
        }

        return createResponse("Không tìm thấy tác phẩm nào của nghệ sĩ **" + personName + "** trong cơ sở dữ liệu FFilm.", null);
    }

    /**
     * Cá nhân hóa dựa trên lịch sử xem (WatchHistory) và yêu thích (UserFavorite).
     */
    private Map<String, Object> handleUserPersonalizedIntent(JSONObject brain, ConversationContext context, Integer userId) {
        if (userId == null) {
            List<Movie> hot = movieService.getHotMoviesForAI(5);
            String promptLogin = "Để FFilm có thể gợi ý phim chuẩn xác theo sở thích và lịch sử xem của riêng bạn, bạn vui lòng **đăng nhập** tài khoản FFilm nhé! Tạm thời tôi xin gợi ý những phim đang thịnh hành nhất:";
            return createResponse(promptLogin, hot.stream().map(movieService::convertToMap).collect(Collectors.toList()));
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return createResponse("Không tìm thấy thông tin tài khoản người dùng.", null);
        }

        User user = userOpt.get();
        Set<Integer> watchedIds = watchHistoryRepository.findWatchedMovieIDsByUserID(userId);
        Page<WatchHistory> recentHistory = watchHistoryRepository.findByUserOrderByLastWatchedAtDesc(user, PageRequest.of(0, 10));

        // Thu thập các thể loại từ lịch sử xem
        Map<String, Long> genreFrequency = new HashMap<>();
        List<String> watchedTitles = new ArrayList<>();

        for (WatchHistory wh : recentHistory.getContent()) {
            Movie m = wh.getMovie();
            if (m != null) {
                watchedTitles.add(m.getTitle());
                for (Genre g : m.getGenres()) {
                    genreFrequency.put(g.getName(), genreFrequency.getOrDefault(g.getName(), 0L) + 1L);
                }
            }
        }

        // Lấy top 2 thể loại xem nhiều nhất
        List<String> preferredGenres = genreFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(2)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<Movie> recommendations = new ArrayList<>();
        if (!preferredGenres.isEmpty()) {
            MovieSearchFilters filters = new MovieSearchFilters();
            filters.setGenres(preferredGenres);
            List<Movie> genreMatches = movieService.findMoviesByFilters(filters);

            recommendations = genreMatches.stream()
                    .filter(m -> !watchedIds.contains(m.getMovieID()))
                    .limit(6)
                    .collect(Collectors.toList());
        }

        if (recommendations.isEmpty()) {
            recommendations = movieService.getHotMoviesForAI(6).stream()
                    .filter(m -> !watchedIds.contains(m.getMovieID()))
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> cards = recommendations.stream().map(movieService::convertToMap).collect(Collectors.toList());
        context.setLastCandidateMovies(cards);

        String answer;
        if (!watchedTitles.isEmpty() && !preferredGenres.isEmpty()) {
            answer = "Dựa trên các phim bạn đã xem gần đây (như *" + String.join(", ", watchedTitles.stream().limit(2).toList()) +
                    "*), bạn rất yêu thích thể loại **" + String.join(", ", preferredGenres) +
                    "**. Dưới đây là những bộ phim được đánh giá cao trên FFilm mà bạn chưa xem:";
        } else {
            answer = "Chào bạn! Bạn chưa có nhiều lịch sử xem phim trên FFilm. Hãy thử bắt đầu bằng các tác phẩm được yêu thích nhất dưới đây nhé:";
        }

        return createResponse(answer, cards);
    }

    /**
     * Lọc phim theo tiêu chí kết hợp (thể loại, quốc gia, năm, điểm số, thời lượng, loại trừ).
     */
    private Map<String, Object> handleFilterIntent(JSONObject brain, ConversationContext context) {
        MovieSearchFilters filters = parseFlatFilters(brain);

        String action = brain.optString("context_action", "NARROW").toUpperCase();

        if ("CONTRADICT".equals(action)) {
            // Xử lý khi người dùng đổi ý mâu thuẫn (vd trước thích kinh dị, nay ghét kinh dị)
            if (filters.getExcludedGenres() != null) {
                for (String eg : filters.getExcludedGenres()) {
                    if (context.getLastActiveGenres() != null) {
                        context.getLastActiveGenres().removeIf(g -> g.equalsIgnoreCase(eg));
                    }
                    context.getExcludedGenres().add(eg);
                }
            }
        } else if ("SWITCH".equals(action) || "NEW".equals(action)) {
            context.setLastActiveGenres(filters.getGenres() != null ? new ArrayList<>(filters.getGenres()) : new ArrayList<>());
            context.setLastActiveCountry(filters.getCountry());
        } else {
            // NARROW: Kế thừa thể loại / quốc gia nếu đang thu hẹp
            if ((filters.getGenres() == null || filters.getGenres().isEmpty()) &&
                    context.getLastActiveGenres() != null && !context.getLastActiveGenres().isEmpty()) {
                filters.setGenres(new ArrayList<>(context.getLastActiveGenres()));
            }
            if ((filters.getCountry() == null || filters.getCountry().isEmpty()) &&
                    context.getLastActiveCountry() != null && !context.getLastActiveCountry().isEmpty()) {
                filters.setCountry(normalizeCountryForDB(context.getLastActiveCountry()));
            }
        }

        // Tích hợp danh sách loại trừ (Negative Constraints) đang có trong context
        if (context.getExcludedGenres() != null && !context.getExcludedGenres().isEmpty()) {
            Set<String> mergedEx = new HashSet<>(context.getExcludedGenres());
            if (filters.getExcludedGenres() != null) mergedEx.addAll(filters.getExcludedGenres());
            filters.setExcludedGenres(new ArrayList<>(mergedEx));
        }
        if (context.getExcludedDirectors() != null && !context.getExcludedDirectors().isEmpty()) {
            Set<String> mergedDir = new HashSet<>(context.getExcludedDirectors());
            if (filters.getExcludedDirectors() != null) mergedDir.addAll(filters.getExcludedDirectors());
            filters.setExcludedDirectors(new ArrayList<>(mergedDir));
        }

        // Snapshot current state for backtrack support
        context.pushStateSnapshot();

        if (!filters.hasFilters()) {
            List<Movie> hot = movieService.getHotMoviesForAI(6);
            List<Map<String, Object>> cards = hot.stream().map(movieService::convertToMap).collect(Collectors.toList());
            context.setLastCandidateMovies(cards);
            return createResponse("Dưới đây là các bộ phim thịnh hành và nổi bật nhất trên FFilm:", cards);
        }

        if (filters.getGenres() != null && !filters.getGenres().isEmpty()) {
            context.setLastActiveGenres(filters.getGenres());
        }
        if (filters.getCountry() != null && !filters.getCountry().isEmpty()) {
            context.setLastActiveCountry(filters.getCountry());
        }

        context.setLastSubjectType("Filter");
        context.setLastSubjectId(filters);
        context.setLastQuestionAsked("ask_more_filter");

        List<Movie> movies = movieService.findMoviesByFilters(filters);
        if (movies.isEmpty()) {
            String conflictDiagnosis = movieRecommendationEngine.diagnoseConstraintConflict(filters);
            List<Map<String, Object>> emptyActions = List.of(
                    Map.of("type", "CHIP_REPLY", "text", "Quay lại danh sách trước", "label", "↩️ Quay lại"),
                    Map.of("type", "CHIP_REPLY", "text", "Top phim thịnh hành nhất hiện nay trên FFilm", "label", "🔥 Phim thịnh hành"),
                    Map.of("type", "CHIP_REPLY", "text", "Gợi ý các phim có rating cao nhất", "label", "⭐ Top điểm cao")
            );
            return createResponse(conflictDiagnosis, null, emptyActions);
        }

        List<Map<String, Object>> cards = movies.stream().limit(10)
                .map(movieService::convertToMap)
                .collect(Collectors.toList());
        context.setLastCandidateMovies(cards);
        for (Movie m : movies.stream().limit(10).toList()) {
            context.addShownMovieId(m.getMovieID());
        }

        String naturalText = generateNaturalResponse(filters, movies.size());
        List<Map<String, Object>> actions = List.of(
                Map.of("type", "CHIP_REPLY", "text", "Điểm đánh giá cao nhất", "label", "⭐ Điểm cao nhất"),
                Map.of("type", "CHIP_REPLY", "text", "Phát hành mới nhất", "label", "📅 Mới nhất"),
                Map.of("type", "CHIP_REPLY", "text", "Phim của Mỹ", "label", "🇺🇸 Phim Mỹ"),
                Map.of("type", "CHIP_REPLY", "text", "Phim của Hàn Quốc", "label", "🇰🇷 Phim Hàn")
        );
        return createResponse(naturalText, cards, actions);
    }

    /**
     * Tìm phim theo mô tả cốt truyện thông qua AISearchService và ground với DB.
     */
    private Map<String, Object> handleDescriptionSearchIntent(String message, ConversationContext context) {
        Map<String, Object> searchResult = aiSearchService.getMovieRecommendation(message);
        List<Map<String, Object>> cards = new ArrayList<>();
        String answer = "Xin lỗi, tôi chưa hiểu rõ mô tả.";

        if (Boolean.TRUE.equals(searchResult.get("success"))) {
            answer = (String) searchResult.get("answer");
            @SuppressWarnings("unchecked")
            List<String> suggestions = (List<String>) searchResult.get("suggestions");
            if (suggestions != null) {
                for (String title : suggestions) {
                    List<Movie> dbMovies = movieRepository.findByTitleContainingIgnoreCase(title.trim());
                    if (!dbMovies.isEmpty()) {
                        cards.add(movieService.convertToMap(dbMovies.get(0)));
                    }
                }
            }
        }

        context.setLastCandidateMovies(cards);
        return createResponse(answer, cards);
    }

    // ---- 7. GROUNDED ANSWER & COMPARISON GENERATORS ----

    private String formatSpecificMovieAnswer(Movie movie, String attribute) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy");
        String year = movie.getReleaseDate() != null ? sdf.format(movie.getReleaseDate()) : "N/A";
        String rating = movie.getRating() > 0 ? String.format("%.1f", movie.getRating()) : "Chưa có";
        String director = (movie.getDirector() != null && !movie.getDirector().isEmpty()) ? movie.getDirector() : "Đang cập nhật";
        String genres = getMovieGenreNames(movie);

        String topCast = movie.getPersons().stream()
                .limit(4)
                .map(Person::getFullName)
                .collect(Collectors.joining(", "));
        if (topCast.isEmpty()) topCast = "Đang cập nhật";

        StringBuilder sb = new StringBuilder();
        switch (attribute) {
            case "synopsis":
                sb.append("📖 **Nội dung phim ").append(movie.getTitle()).append("**:\n\n");
                sb.append(movie.getDescription() != null && !movie.getDescription().isEmpty()
                        ? movie.getDescription()
                        : "Nội dung phim đang được ban biên tập FFilm hoàn thiện.");
                sb.append("\n\n⭐ Điểm đánh giá: **").append(rating).append("/10** | 📅 Năm: **").append(year).append("**");
                break;

            case "director":
                sb.append("🎬 Đạo diễn của bộ phim **").append(movie.getTitle()).append("** là **").append(director).append("**.");
                break;

            case "cast":
                sb.append("🎭 Dàn diễn viên chính tham gia **").append(movie.getTitle()).append("** gồm có: **").append(topCast).append("**.");
                break;

            case "rating":
                sb.append("⭐ Bộ phim **").append(movie.getTitle()).append("** hiện có điểm đánh giá là **").append(rating).append("/10** trên FFilm.");
                break;

            case "year":
                sb.append("📅 Bộ phim **").append(movie.getTitle()).append("** được phát hành vào năm **").append(year).append("**.");
                break;

            default:
                sb.append("🎬 **").append(movie.getTitle()).append("** (").append(year).append(")\n");
                sb.append("⭐ Rating: **").append(rating).append("/10** | 🎭 Thể loại: **").append(genres).append("**\n");
                sb.append("🎥 Đạo diễn: **").append(director).append("** | 🎭 Diễn viên: **").append(topCast).append("**\n\n");
                if (movie.getDescription() != null && !movie.getDescription().isEmpty()) {
                    String desc = movie.getDescription();
                    if (desc.length() > 220) desc = desc.substring(0, 217) + "...";
                    sb.append("📝 ").append(desc);
                }
                break;
        }

        return sb.toString();
    }

    private String generateGroundedComparison(Movie m1, Movie m2) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy");
        String y1 = m1.getReleaseDate() != null ? sdf.format(m1.getReleaseDate()) : "N/A";
        String y2 = m2.getReleaseDate() != null ? sdf.format(m2.getReleaseDate()) : "N/A";

        String r1 = m1.getRating() > 0 ? String.format("%.1f", m1.getRating()) : "N/A";
        String r2 = m2.getRating() > 0 ? String.format("%.1f", m2.getRating()) : "N/A";

        String g1 = getMovieGenreNames(m1);
        String g2 = getMovieGenreNames(m2);

        String d1 = m1.getDirector() != null ? m1.getDirector() : "N/A";
        String d2 = m2.getDirector() != null ? m2.getDirector() : "N/A";

        // Thử tổng hợp thông minh bằng Gemini dựa trên Facts
        try {
            String prompt = String.format(
                    "Hãy so sánh súc tích, chuyên nghiệp 2 bộ phim sau cho người dùng FFilm dựa trên dữ liệu thực tế:\n" +
                    "- Phim 1: %s (Năm: %s, Điểm: %s/10, Thể loại: %s, Đạo diễn: %s, Thời lượng: %d phút)\n" +
                    "- Phim 2: %s (Năm: %s, Điểm: %s/10, Thể loại: %s, Đạo diễn: %s, Thời lượng: %d phút)\n" +
                    "Nội dung cần có:\n" +
                    "1. So sánh về phong cách thể loại và không khí phim.\n" +
                    "2. Nhận xét về chỉ đạo diễn xuất / đạo diễn.\n" +
                    "3. Gợi ý người xem nên chọn phim nào tùy theo tâm trạng.\n" +
                    "Độ dài khoảng 3-4 đoạn ngắn, thân thiện và khách quan.",
                    m1.getTitle(), y1, r1, g1, d1, m1.getDuration(),
                    m2.getTitle(), y2, r2, g2, d2, m2.getDuration()
            );

            JSONObject req = buildGeminiRequest_Simple(prompt);
            JSONObject res = callGeminiAPI(req);
            String text = extractTextResponse(res);
            if (text != null && !text.trim().isEmpty()) {
                return text.trim();
            }
        } catch (Exception e) {
            log.warn("Gemini comparison synthesis error: {}, using deterministic fallback", e.getMessage());
        }

        // Deterministic Fallback Table
        return String.format(
                "⚖️ **So sánh giữa %s và %s**:\n\n" +
                "• **%s**: Năm %s | Rating ⭐ %s/10 | Thể loại: %s | Đạo diễn: %s\n" +
                "• **%s**: Năm %s | Rating ⭐ %s/10 | Thể loại: %s | Đạo diễn: %s\n\n" +
                "💡 **Gợi ý**: Cả hai tác phẩm đều có sẵn chất lượng cao trên FFilm. Nếu bạn thích thể loại **%s**, hãy xem **%s**. Nếu muốn đổi gió sang **%s**, **%s** sẽ là lựa chọn tuyệt vời!",
                m1.getTitle(), m2.getTitle(),
                m1.getTitle(), y1, r1, g1, d1,
                m2.getTitle(), y2, r2, g2, d2,
                g1, m1.getTitle(), g2, m2.getTitle()
        );
    }

    // ---- 8. MULTI-TURN & CONTEXT RESOLVERS ----

    private String resolveOrdinalReference(String subject, ConversationContext context) {
        if (subject == null) return "";
        String s = subject.toLowerCase().trim();

        List<Map<String, Object>> candidates = context.getLastCandidateMovies();
        if (candidates != null && !candidates.isEmpty()) {
            if (s.matches(".*(thứ hai|thứ 2|phim 2|cái 2|cái thứ 2|bộ 2|cái kia|bộ thứ 2).*")) {
                if (candidates.size() >= 2) return (String) candidates.get(1).get("title");
            } else if (s.matches(".*(đầu tiên|thứ nhất|thứ 1|phim 1|cái 1|cái đầu|bộ đầu|bộ thứ nhất).*")) {
                return (String) candidates.get(0).get("title");
            } else if (s.matches(".*(thứ ba|thứ 3|phim 3|cái 3|bộ 3|bộ thứ 3).*")) {
                if (candidates.size() >= 3) return (String) candidates.get(2).get("title");
            } else if (s.matches(".*(thứ tư|thứ 4|phim 4|cái 4|bộ 4).*")) {
                if (candidates.size() >= 4) return (String) candidates.get(3).get("title");
            } else if (s.matches(".*(cuối cùng|phim cuối|cái cuối).*")) {
                return (String) candidates.get(candidates.size() - 1).get("title");
            } else if (s.matches(".*(phim đó|nó|phim này|phim vừa rồi|cái đó|bộ vừa rồi).*")) {
                if (context.getLastFocusedMovie() != null && context.getLastFocusedMovie().get("title") != null) {
                    return (String) context.getLastFocusedMovie().get("title");
                }
                return (String) candidates.get(0).get("title");
            }
        }
        return subject;
    }

    private String buildHistorySummary(String conversationId, Integer userId, ConversationContext context) {
        StringBuilder sb = new StringBuilder();

        // 1. Lấy tin nhắn từ Database nếu có
        List<AIChatHistory> history = Collections.emptyList();
        if (userId != null) {
            history = chatHistoryRepository.findTop10ByUserIdOrderByTimestampDesc(userId);
        } else if (conversationId != null) {
            history = chatHistoryRepository.findTop10BySessionIdOrderByTimestampDesc(conversationId);
        }

        if (history != null && !history.isEmpty()) {
            List<AIChatHistory> reversed = new ArrayList<>(history);
            Collections.reverse(reversed);
            for (AIChatHistory h : reversed) {
                String role = h.getRole() == AIChatHistory.SenderRole.USER ? "User" : "Bot";
                String msg = h.getMessage();
                if (msg != null && msg.length() > 80) msg = msg.substring(0, 77) + "...";
                sb.append("- ").append(role).append(": ").append(msg).append("\n");
            }
        }

        // 2. Thêm danh sách phim gợi ý ở turn trước nếu có
        if (context.getLastCandidateMovies() != null && !context.getLastCandidateMovies().isEmpty()) {
            sb.append("Danh sách phim đề xuất ở turn trước: ");
            int idx = 1;
            for (Map<String, Object> m : context.getLastCandidateMovies().stream().limit(4).toList()) {
                sb.append(idx++).append(". ").append(m.get("title")).append(" ");
            }
            sb.append("\n");
        }

        if (context.getLastFocusedMovie() != null) {
            sb.append("Phim vừa thảo luận: ").append(context.getLastFocusedMovie().get("title")).append("\n");
        }

        return sb.toString().trim();
    }

    private ConversationContext getOrCreateContext(String conversationId) {
        if (conversationCache != null) {
            ConversationContext ctx = conversationCache.get(conversationId, ConversationContext.class);
            if (ctx != null) return ctx;
        }
        return inMemoryContextStore.computeIfAbsent(conversationId, k -> new ConversationContext());
    }

    private void saveContext(String conversationId, ConversationContext context) {
        if (conversationCache != null) {
            conversationCache.put(conversationId, context);
        }
        inMemoryContextStore.put(conversationId, context);
    }

    // ---- 9. CHAT HISTORY DB PERSISTENCE ----

    public void saveChatHistory(String sessionId, Integer userId, String userMsg, String botMsg, List<Map<String, Object>> movies) {
        try {
            AIChatHistory userHistory = new AIChatHistory(userId, sessionId, userMsg, AIChatHistory.SenderRole.USER);
            chatHistoryRepository.save(userHistory);

            String metadata = null;
            if (movies != null && !movies.isEmpty()) {
                metadata = movies.stream()
                        .map(m -> String.valueOf(m.get("id")))
                        .collect(Collectors.joining(","));
            }

            AIChatHistory botHistory = new AIChatHistory(userId, sessionId, botMsg, AIChatHistory.SenderRole.BOT);
            botHistory.setMetadata(metadata);
            chatHistoryRepository.save(botHistory);
        } catch (Exception e) {
            log.error("Error saving chat history: {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> getChatHistory(String sessionId, Integer userId) {
        List<AIChatHistory> historyList;
        if (userId != null) {
            historyList = chatHistoryRepository.findByUserIdOrderByTimestampAsc(userId);
        } else {
            historyList = chatHistoryRepository.findBySessionIdOrderByTimestampAsc(sessionId);
        }

        return historyList.stream().map(h -> {
            Map<String, Object> msgMap = new HashMap<>();
            msgMap.put("role", h.getRole().toString());
            msgMap.put("message", h.getMessage());
            msgMap.put("timestamp", h.getTimestamp());

            if (h.getMetadata() != null && !h.getMetadata().isEmpty()) {
                try {
                    List<Integer> ids = Arrays.stream(h.getMetadata().split(","))
                            .map(Integer::parseInt)
                            .collect(Collectors.toList());

                    List<Map<String, Object>> movies = new ArrayList<>();
                    for (Integer id : ids) {
                        Movie m = movieRepository.findById(id).orElse(null);
                        if (m != null) movies.add(movieService.convertToMap(m));
                    }
                    msgMap.put("movies", movies);
                } catch (Exception ignored) {}
            }
            return msgMap;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void clearChatHistory(String sessionId, Integer userId, String conversationId) {
        try {
            if (userId != null) {
                chatHistoryRepository.deleteByUserId(userId);
            } else if (sessionId != null) {
                chatHistoryRepository.deleteBySessionId(sessionId);
            }
        } catch (Exception e) {
            log.error("Error clearing chat history: {}", e.getMessage());
        }
        clearContext(conversationId);
    }

    public void clearContext(String conversationId) {
        if (conversationId != null) {
            inMemoryContextStore.remove(conversationId);
            if (conversationCache != null) {
                conversationCache.evict(conversationId);
            }
        }
    }

    // ---- 10. SUBSCRIPTION LOGIC ----

    private String handleSubscriptionQuery(String queryType) {
        try {
            List<SubscriptionPlan> plans = planRepository.findAll();
            if (plans.isEmpty()) {
                return "Hiện tại thông tin về các gói cước của FFilm đang trong quá trình cập nhật. Bạn vui lòng theo dõi trang chủ để biết thêm chi tiết nhé!";
            }

            StringBuilder response = new StringBuilder();
            switch (queryType.toLowerCase()) {
                case "price":
                case "plans":
                    response.append("📋 **CÁC GÓI ĐĂNG KÝ FFILM**\n\n");
                    for (SubscriptionPlan plan : plans) {
                        response.append("✨ **").append(plan.getPlanName()).append("**\n");
                        response.append("💰 Giá: ").append(formatPrice(plan.getPrice())).append("\n");
                        if (plan.getDescription() != null && !plan.getDescription().isEmpty()) {
                            response.append("📝 ").append(plan.getDescription()).append("\n");
                        }
                        response.append("\n");
                    }
                    response.append("💡 **Lưu ý**: \n");
                    response.append("• Hỗ trợ xem chất lượng Full HD & 4K mượt mà\n");
                    response.append("• Hỗ trợ 24/7 qua chat hoặc hotline chăm sóc khách hàng\n\n");
                    response.append("Bạn có muốn đăng ký hoặc tìm hiểu thêm chi tiết gói nào không? 😊");
                    break;

                case "cancel":
                    response.append("🔄 **CHÍNH SÁCH HỦY ĐĂNG KÝ**\n\n");
                    response.append("Sau khi thanh toán gói cước, tài khoản của bạn sẽ duy trì trạng thái VIP hoạt động đến hết chu kỳ đã thanh toán.\n");
                    break;

                case "refund":
                    response.append("💸 **CHÍNH SÁCH HOÀN TIỀN**\n\n");
                    response.append("• FFilm không hỗ trợ hoàn tiền sau khi giao dịch đã kích hoạt thành công.\n");
                    response.append("• Quyền lợi gói đã mua vẫn có hiệu lực trọn vẹn đến hết chu kỳ.\n");
                    break;

                case "payment":
                    response.append("💳 **PHƯƠNG THỨC THANH TOÁN**\n\n");
                    response.append("FFilm hỗ trợ thanh toán an toàn, bảo mật qua:\n");
                    response.append("• Cổng thanh toán VNPay / Chuyển khoản QR code nhanh 24/7\n");
                    break;

                default:
                    return handleSubscriptionQuery("plans");
            }
            return response.toString();
        } catch (Exception e) {
            log.error("Error fetching subscription plans in AI agent", e);
            return "Xin lỗi, hiện tại tôi không thể lấy thông tin gói đăng ký. Vui lòng liên hệ bộ phận hỗ trợ FFilm.";
        }
    }

    // ---- 11. DETERMINISTIC KEYWORD & DOMAIN FALLBACK ----

    private Map<String, Object> runKeywordFallback(String msg, ConversationContext ctx, Integer userId) {
        String lower = msg.toLowerCase().trim();

        // 1. Subscription
        if (lower.matches(".*(gói|đăng ký|cước|giá|bao nhiêu tiền|thanh toán|hủy|premium).*")
                && lower.matches(".*(gói|cước|giá|tiền|hủy|thanh toán).*")) {
            String respText = handleSubscriptionQuery(lower.contains("hủy") ? "cancel" : lower.contains("thanh toán") ? "payment" : "plans");
            return createResponse(respText, null);
        }

        // 2. Movie title
        String cleanTitle = lower.replaceAll("^(phim|xem phim|tìm phim|có phim|film)\\s+", "")
                .replaceAll("\\s+(có|không|nào|gì|đâu)$", "").trim();

        if (cleanTitle.length() >= 2) {
            Movie canonical = resolveCanonicalMovie(cleanTitle, null, null);
            if (canonical != null) {
                Map<String, Object> movieMap = movieService.convertToMap(canonical);
                ctx.setLastFocusedMovie(movieMap);
                ctx.setLastBaseMovie(movieMap);
                ctx.setLastCandidateMovies(List.of(movieMap));
                if (!canonical.getPersons().isEmpty()) {
                    ctx.setLastFocusedPerson(canonical.getPersons().iterator().next().getFullName());
                }
                if (canonical.getDirector() != null && !canonical.getDirector().isBlank()) {
                    ctx.setLastFocusedDirector(canonical.getDirector());
                }
                return createResponse(formatSpecificMovieAnswer(canonical, "general"), List.of(movieMap), buildMovieDetailActions(canonical));
            }
            List<Movie> byTitle = movieService.searchMoviesByTitle(cleanTitle);
            if (!byTitle.isEmpty()) {
                List<Map<String, Object>> cards = byTitle.stream().limit(5).map(movieService::convertToMap).collect(Collectors.toList());
                ctx.setLastCandidateMovies(cards);
                if (cards.size() == 1) {
                    ctx.setLastFocusedMovie(cards.get(0));
                    ctx.setLastBaseMovie(cards.get(0));
                }
                return createResponse("Tìm thấy các bộ phim phù hợp với từ khóa **\"" + cleanTitle + "\"**:", cards);
            }
        }

        // 2b. Focused Movie pronoun / cast / director / plot fallback
        Map<String, Object> focused = ctx.getLastFocusedMovie() != null ? ctx.getLastFocusedMovie() : ctx.getLastBaseMovie();
        if (focused != null && (lower.contains("ai đóng") || lower.contains("diễn viên") || lower.contains("đạo diễn") || lower.contains("nội dung") || lower.contains("phim này") || lower.contains("phim đó"))) {
            Object midObj = focused.get("id");
            if (midObj == null) midObj = focused.get("movieID");
            if (midObj != null) {
                try {
                    int mid = midObj instanceof Number ? ((Number) midObj).intValue() : Integer.parseInt(midObj.toString());
                    Movie m = movieRepository.findById(mid).orElse(null);
                    if (m != null) {
                        if (lower.contains("ai đóng") || lower.contains("diễn viên")) {
                            if (!m.getPersons().isEmpty()) {
                                ctx.setLastFocusedPerson(m.getPersons().iterator().next().getFullName());
                            }
                            return createResponse(formatSpecificMovieAnswer(m, "cast"), List.of(focused), buildMovieDetailActions(m));
                        } else if (lower.contains("đạo diễn")) {
                            if (m.getDirector() != null && !m.getDirector().isBlank()) {
                                ctx.setLastFocusedDirector(m.getDirector());
                            }
                            return createResponse(formatSpecificMovieAnswer(m, "director"), List.of(focused), buildMovieDetailActions(m));
                        } else if (lower.contains("nội dung") || lower.contains("về cái gì")) {
                            return createResponse(formatSpecificMovieAnswer(m, "synopsis"), List.of(focused), buildMovieDetailActions(m));
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // 2c. Person filmography fallback
        if ((lower.contains("diễn viên đó") || lower.contains("đạo diễn đó") || lower.contains("còn phim nào") || lower.contains("phim khác"))
                && (ctx.getLastFocusedPerson() != null || ctx.getLastFocusedDirector() != null)) {
            String pName = ctx.getLastFocusedPerson() != null ? ctx.getLastFocusedPerson() : ctx.getLastFocusedDirector();
            String pRole = ctx.getLastFocusedPerson() != null ? "actor" : "director";
            JSONObject synthBrain = new JSONObject();
            synthBrain.put("person_name", pName);
            synthBrain.put("role", pRole);
            synthBrain.put("query_type", "filmography");
            return handlePersonQueryIntent(synthBrain, ctx);
        }

        // 2d. Ordinal candidate selection fallback
        List<Map<String, Object>> candidates = ctx.getLastCandidateMovies();
        if (candidates != null && !candidates.isEmpty() && lower.matches(".*(đầu tiên|thứ nhất|thứ 1|thứ hai|thứ 2|cái 1|cái 2|phim 1|phim 2|cái đầu|cái cuối).*")) {
            String resolvedTitle = resolveOrdinalReference(lower, ctx);
            if (resolvedTitle != null && !resolvedTitle.isBlank() && !resolvedTitle.equalsIgnoreCase(lower)) {
                Map<String, Object> foundCard = candidates.stream()
                        .filter(c -> resolvedTitle.equalsIgnoreCase((String) c.get("title")))
                        .findFirst().orElse(null);
                Movie m = null;
                if (foundCard != null && foundCard.get("id") != null) {
                    int mid = ((Number) foundCard.get("id")).intValue();
                    m = movieRepository.findById(mid).orElse(null);
                }
                if (m == null) {
                    m = movieRepository.findByTitleContainingIgnoreCase(resolvedTitle).stream().findFirst().orElse(null);
                }
                if (m != null) {
                    Map<String, Object> mMap = movieService.convertToMap(m);
                    ctx.setLastFocusedMovie(mMap);
                    ctx.setLastBaseMovie(mMap);
                    return createResponse(formatSpecificMovieAnswer(m, "general"), List.of(mMap), buildMovieDetailActions(m));
                }
            }
        }

        // 3. Person
        if (!lower.contains("phim") && msg.split("\\s+").length <= 4 && !msg.isEmpty()) {
            List<Map<String, Object>> personResults = movieService.searchMoviesCombined(msg);
            if (!personResults.isEmpty()) {
                ctx.setLastCandidateMovies(personResults);
                return createResponse("Các tác phẩm liên quan đến nghệ sĩ **" + msg + "**:", personResults.stream().limit(6).collect(Collectors.toList()));
            }
        }

        // 4. Mood
        List<String> moodGenres = detectMood(lower);
        if (!moodGenres.isEmpty()) {
            MovieSearchFilters f = new MovieSearchFilters();
            f.setGenres(moodGenres);
            return executeFilter(f, ctx, "phim phù hợp với tâm trạng của bạn");
        }

        // 5. Genre
        List<String> genres = detectGenres(lower);
        if (!genres.isEmpty()) {
            MovieSearchFilters f = new MovieSearchFilters();
            f.setGenres(genres);
            return executeFilter(f, ctx, "phim thể loại " + String.join(", ", genres));
        }

        // 6. Country
        String country = detectCountry(lower);
        if (country != null) {
            MovieSearchFilters f = new MovieSearchFilters();
            f.setCountry(normalizeCountryForDB(country));
            return executeFilter(f, ctx, "phim " + country);
        }

        // 7. Trending
        if (lower.matches(".*(hot|xu hướng|phổ biến|nổi bật|đang xem|mới nhất).*")) {
            List<Movie> hot = movieService.getHotMoviesForAI(5);
            List<Map<String, Object>> cards = hot.stream().map(movieService::convertToMap).collect(Collectors.toList());
            ctx.setLastCandidateMovies(cards);
            return createResponse("Dưới đây là các phim đang thịnh hành nhất trên FFilm:", cards);
        }

        // Final graceful fallback
        List<Movie> defaultHot = movieService.getHotMoviesForAI(5);
        List<Map<String, Object>> cards = defaultHot.stream().map(movieService::convertToMap).collect(Collectors.toList());
        ctx.setLastCandidateMovies(cards);
        return createResponse("Rất tiếc, tôi chưa tìm thấy kết quả phù hợp cho **\"" + msg + "\"**.\n\n" +
                "💡 Gợi ý:\n" +
                "• Tìm theo thể loại: 'phim hành động', 'phim kinh dị', 'phim hài'\n" +
                "• Tìm theo tâm trạng: 'tôi đang buồn', 'muốn cười bể bụng'\n" +
                "• So sánh phim: 'So sánh phim Mai và Bố Già'\n" +
                "• Phim tương tự: 'Phim giống Interstellar'\n" +
                "• Gói đăng ký: 'các gói cước', 'bảng giá gói'\n\n" +
                "Dưới đây là những phim đang hot nhất để bạn tham khảo:", cards);
    }

    private Map<String, Object> handleFollowUp(ConversationContext context, String message) {
        String q = context.getLastQuestionAsked();
        Object id = context.getLastSubjectId();
        List<Map<String, Object>> movies = new ArrayList<>();

        if ("ask_more_filter".equals(q) && id instanceof MovieSearchFilters f) {
            List<Movie> allMovies = movieService.findMoviesByFilters(f);
            List<Movie> newBatch = allMovies.stream()
                    .filter(m -> !context.getShownMovieIds().contains(m.getMovieID()))
                    .limit(10)
                    .collect(Collectors.toList());

            if (!newBatch.isEmpty()) {
                for (Movie m : newBatch) {
                    movies.add(movieService.convertToMap(m));
                    context.addShownMovieId(m.getMovieID());
                }
                context.setLastCandidateMovies(movies);
                return createResponse("Dưới đây là các kết quả tiếp theo:", movies);
            } else {
                return createResponse("Đã hiển thị hết danh sách phim phù hợp với tiêu chí này rồi ạ.", null);
            }
        }

        return runKeywordFallback(message, context, null);
    }

    private Map<String, Object> executeFilter(MovieSearchFilters f, ConversationContext ctx, String reason) {
        List<Movie> movies = movieService.findMoviesByFilters(f);
        if (movies.isEmpty()) {
            return createResponse("Hiện tại chưa có " + reason + " trong kho phim FFilm.", null);
        }

        List<Map<String, Object>> resultMovies = movies.stream().limit(10)
                .map(movieService::convertToMap)
                .collect(Collectors.toList());
        ctx.setLastCandidateMovies(resultMovies);
        return createResponse("FFilm tìm thấy " + movies.size() + " " + reason + ":", resultMovies);
    }

    // ---- 12. HELPER UTILITIES ----

    /**
     * Tra cứu thực thể phim chuẩn hóa (Canonical Entity Resolution) ủy thác cho MovieEntityResolver.
     */
    private Movie resolveCanonicalMovie(String queryTitle, String origTitle, String vnTitle) {
        return movieEntityResolver.resolveCanonicalMovie(queryTitle, origTitle, vnTitle);
    }

    private Movie findSingleMovie(String name) {
        return movieEntityResolver.resolveCanonicalMovie(name, null, null);
    }

    private String getMovieGenreNames(Movie movie) {
        if (movie.getGenres() == null || movie.getGenres().isEmpty()) return "Tổng hợp";
        return movie.getGenres().stream().map(Genre::getName).collect(Collectors.joining(", "));
    }

    private MovieSearchFilters parseFlatFilters(JSONObject j) {
        MovieSearchFilters f = new MovieSearchFilters();
        if (j == null) return f;
        try {
            if (j.has("f_country") && !j.isNull("f_country")) {
                String c = j.optString("f_country", "").trim();
                if (!c.isEmpty()) {
                    f.setCountry(normalizeCountryForDB(c));
                }
            }
            if (j.has("f_genres") && !j.isNull("f_genres")) {
                List<String> g = new ArrayList<>();
                JSONArray a = j.optJSONArray("f_genres");
                if (a != null) {
                    for (int i = 0; i < a.length(); i++) {
                        String gn = a.optString(i, "").trim();
                        if (!gn.isEmpty()) g.add(gn);
                    }
                }
                if (!g.isEmpty()) {
                    f.setGenres(g);
                }
            }
            if (j.has("excluded_genres") && !j.isNull("excluded_genres")) {
                List<String> eg = new ArrayList<>();
                JSONArray a = j.optJSONArray("excluded_genres");
                if (a != null) {
                    for (int i = 0; i < a.length(); i++) {
                        String gn = a.optString(i, "").trim();
                        if (!gn.isEmpty()) eg.add(gn);
                    }
                }
                if (!eg.isEmpty()) {
                    f.setExcludedGenres(eg);
                }
            }
            if (j.has("excluded_directors") && !j.isNull("excluded_directors")) {
                List<String> ed = new ArrayList<>();
                JSONArray a = j.optJSONArray("excluded_directors");
                if (a != null) {
                    for (int i = 0; i < a.length(); i++) {
                        String dn = a.optString(i, "").trim();
                        if (!dn.isEmpty()) ed.add(dn);
                    }
                }
                if (!ed.isEmpty()) {
                    f.setExcludedDirectors(ed);
                }
            }
            if (j.has("f_min_rating") && !j.isNull("f_min_rating")) {
                double minR = j.optDouble("f_min_rating", 0.0);
                if (minR > 0.0) {
                    f.setMinRating((float) minR);
                }
            }
            if (j.has("f_max_duration") && !j.isNull("f_max_duration")) {
                int maxDur = j.optInt("f_max_duration", 0);
                if (maxDur > 0) {
                    f.setMaxDuration(maxDur);
                }
            }
            if (j.has("f_year_from") && !j.isNull("f_year_from")) {
                int yf = j.optInt("f_year_from", 0);
                if (yf > 1900) f.setYearFrom(yf);
            }
            if (j.has("f_year_to") && !j.isNull("f_year_to")) {
                int yt = j.optInt("f_year_to", 0);
                if (yt > 1900) f.setYearTo(yt);
            }
            if (j.has("f_director") && !j.isNull("f_director")) {
                String d = j.optString("f_director", "").trim();
                if (!d.isEmpty()) f.setDirector(d);
            }
            if (j.has("f_actor") && !j.isNull("f_actor")) {
                String a = j.optString("f_actor", "").trim();
                if (!a.isEmpty()) f.setActor(a);
            }
        } catch (Exception e) {
            log.error("Error parsing flat filters JSON in AI agent", e);
        }
        return f;
    }

    private JSONObject parseJsonSafely(String text) {
        if (text == null) return null;
        try {
            text = text.replaceAll("```json|```", "").trim();
            int start = text.indexOf("{");
            int end = text.lastIndexOf("}");
            if (start >= 0 && end > start) {
                String jsonStr = text.substring(start, end + 1);
                JSONObject json = new JSONObject(jsonStr);
                if (json.has("intent")) return json;
            }
        } catch (Exception e) {
            log.debug("JSON parse note: {}", e.getMessage());
        }
        return null;
    }

    private JSONObject buildGeminiRequest_Simple(String prompt) {
        JSONObject config = new JSONObject();
        config.put("temperature", 0.1);
        config.put("maxOutputTokens", 2048);
        JSONArray safety = new JSONArray();
        safety.put(new JSONObject().put("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT").put("threshold", "BLOCK_LOW_AND_ABOVE"));
        return geminiClient.buildRequestBody(prompt, config, safety);
    }

    private JSONObject callGeminiAPI(JSONObject body) throws Exception {
        return geminiClient.call(body);
    }

    private String extractTextResponse(JSONObject json) {
        return geminiClient.extractText(json);
    }

    public boolean isConfigured() {
        return geminiClient.isConfigured();
    }

    private String formatPrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) == 0) return "Miễn phí";
        return String.format("%,.0fđ", price.doubleValue());
    }

    private String formatGenresResponse(List<Genre> genres, String reason) {
        StringBuilder sb = new StringBuilder("Danh sách " + reason + " trên FFilm:\n");
        genres.forEach(g -> sb.append("• ").append(g.getName()).append("\n"));
        return sb.toString();
    }

    private String generateNaturalResponse(MovieSearchFilters f, int count) {
        StringBuilder sb = new StringBuilder("Đã tìm thấy **");
        sb.append(count).append("** bộ phim");
        if (f.getGenres() != null && !f.getGenres().isEmpty()) {
            sb.append(" thể loại **").append(String.join(", ", f.getGenres())).append("**");
        }
        if (f.getCountry() != null) {
            sb.append(" của **").append(f.getCountry()).append("**");
        }
        if (f.getMinRating() != null) {
            sb.append(" có điểm đánh giá từ **").append(String.format("%.1f", f.getMinRating())).append("⭐ trở lên**");
        }
        if (f.getMaxDuration() != null) {
            sb.append(" thời lượng dưới **").append(f.getMaxDuration()).append(" phút**");
        }
        if (f.getExcludedGenres() != null && !f.getExcludedGenres().isEmpty()) {
            sb.append(" (loại trừ: **").append(String.join(", ", f.getExcludedGenres())).append("**)");
        }
        if (f.getExcludedDirectors() != null && !f.getExcludedDirectors().isEmpty()) {
            sb.append(" (không thuộc đạo diễn: **").append(String.join(", ", f.getExcludedDirectors())).append("**)");
        }
        if (f.getYearFrom() != null) {
            sb.append(" từ năm **").append(f.getYearFrom()).append("**");
        }
        sb.append(" phù hợp với yêu cầu của bạn:");
        return sb.toString();
    }

    private String detectCountry(String text) {
        if (text == null) return null;
        String lower = " " + text.toLowerCase().replaceAll("[.,?!:;\"']", " ") + " ";
        for (Map.Entry<String, List<String>> entry : COUNTRY_MAPPING.entrySet()) {
            for (String alias : entry.getValue()) {
                String al = alias.toLowerCase();
                if (lower.contains(" " + al + " ") || (al.length() > 3 && lower.contains(al))) {
                    return entry.getKey();
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
        if (userCountry == null || userCountry.trim().isEmpty()) return null;
        String c = userCountry.trim();
        switch (c) {
            case "South Korea":
            case "Korea":
            case "Hàn Quốc":
            case "Hàn":
                return "South Korea";
            case "Viet Nam":
            case "Vietnam":
            case "Việt Nam":
            case "Việt":
                return "Vietnam";
            case "United States":
            case "United States of America":
            case "Mỹ":
            case "Hoa Kỳ":
            case "USA":
            case "US":
                return "United States of America";
            case "United Kingdom":
            case "UK":
            case "Anh":
            case "Anh Quốc":
                return "United Kingdom";
            case "Japan":
            case "Nhật Bản":
            case "Nhật":
                return "Japan";
            case "China":
            case "Trung Quốc":
            case "Trung":
                return "China";
            case "France":
            case "Pháp":
                return "France";
            case "Germany":
            case "Đức":
                return "Germany";
            case "India":
            case "Ấn Độ":
            case "Ấn":
                return "India";
            case "Thailand":
            case "Thái Lan":
            case "Thái":
                return "Thailand";
            case "Australia":
            case "Úc":
                return "Australia";
            case "Spain":
            case "Tây Ban Nha":
                return "Spain";
            case "Italy":
            case "Ý":
                return "Italy";
            default:
                return userCountry;
        }
    }

    private List<Map<String, Object>> buildMovieDetailActions(Movie movie) {
        if (movie == null) return Collections.emptyList();
        List<Map<String, Object>> actions = new ArrayList<>();
        actions.add(Map.of("type", "WATCH", "label", "▶ Xem chi tiết", "url", "/movie/detail/" + movie.getMovieID()));
        actions.add(Map.of("type", "CHIP_REPLY", "label", "🎬 Phim tương tự", "text", "Gợi ý các phim tương tự " + movie.getTitle()));
        actions.add(Map.of("type", "CHIP_REPLY", "label", "🎭 Diễn viên & Đạo diễn", "text", "Ai tham gia diễn xuất trong " + movie.getTitle() + "?"));
        return actions;
    }

    private Map<String, Object> createResponse(String msg, List<Map<String, Object>> movies) {
        return createResponse(msg, movies, null);
    }

    private Map<String, Object> createResponse(String msg, List<Map<String, Object>> movies, List<Map<String, Object>> actions) {
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", msg);
        if (movies != null && !movies.isEmpty()) {
            res.put("movies", movies);
        }
        if (actions != null && !actions.isEmpty()) {
            res.put("actions", actions);
        }
        res.put("type", "website");
        res.put("timestamp", System.currentTimeMillis());
        return res;
    }

    private boolean isGenreOrMoodNarrowing(String msg) {
        if (msg == null || msg.trim().isEmpty()) return false;
        String lower = msg.toLowerCase().trim();
        if (lower.startsWith("thể loại") || lower.startsWith("chỉ xem") || lower.startsWith("lọc") ||
                lower.contains("chỉ lấy") || lower.contains("chỉ phim") || lower.contains("thuộc thể loại")) {
            return true;
        }
        return !detectGenres(lower).isEmpty() && lower.length() < 35;
    }

    public Map<String, Object> getProactiveSuggestions(String page, Integer movieId, String query, String genre, Integer userId) {
        List<Map<String, String>> suggestions = new ArrayList<>();

        if (movieId != null) {
            Movie movie = movieRepository.findById(movieId).orElse(null);
            if (movie != null) {
                suggestions.add(Map.of("label", "📖 Tóm tắt nội dung", "prompt", "Tóm tắt ngắn gọn nội dung phim " + movie.getTitle()));
                suggestions.add(Map.of("label", "🎭 Dàn diễn viên & đạo diễn", "prompt", "Ai đóng và ai đạo diễn phim " + movie.getTitle() + "?"));
                suggestions.add(Map.of("label", "🎬 Phim tương tự", "prompt", "Gợi ý các phim tương tự " + movie.getTitle()));
                return Map.of("page", page != null ? page : "movie_detail", "suggestions", suggestions);
            }
        }

        if (page != null && (page.contains("subscription") || page.contains("pricing") || page.contains("payment"))) {
            suggestions.add(Map.of("label", "💎 Quyền lợi VIP", "prompt", "FFilm VIP có những quyền lợi gì?"));
            suggestions.add(Map.of("label", "💰 Bảng giá gói cước", "prompt", "Giá các gói xem phim trên FFilm hiện nay?"));
            suggestions.add(Map.of("label", "💳 Phương thức thanh toán", "prompt", "FFilm hỗ trợ các hình thức thanh toán nào?"));
            return Map.of("page", page, "suggestions", suggestions);
        }

        if (genre != null && !genre.isBlank()) {
            suggestions.add(Map.of("label", "⭐ Điểm cao nhất", "prompt", "Top phim thể loại " + genre + " điểm cao nhất"));
            suggestions.add(Map.of("label", "📅 Mới nhất", "prompt", "Phim thể loại " + genre + " mới ra mắt"));
            return Map.of("page", page != null ? page : "genre", "suggestions", suggestions);
        }

        if (userId != null) {
            suggestions.add(Map.of("label", "🎯 Gợi ý theo gu của tôi", "prompt", "Gợi ý phim phù hợp với sở thích của tôi"));
        }
        suggestions.add(Map.of("label", "🔥 Phim đang hot", "prompt", "Top phim thịnh hành nhất hiện nay trên FFilm"));
        suggestions.add(Map.of("label", "🎬 Bom tấn hành động", "prompt", "Gợi ý vài phim hành động mãn nhãn"));
        suggestions.add(Map.of("label", "😂 Phim hài hước vui nhộn", "prompt", "Muốn xem phim hài nhẹ nhàng giải trí"));

        return Map.of("page", page != null ? page : "home", "suggestions", suggestions);
    }
}