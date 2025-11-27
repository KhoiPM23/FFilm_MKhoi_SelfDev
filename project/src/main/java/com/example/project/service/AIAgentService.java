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
import java.math.BigDecimal; // Import quan trọng để fix lỗi formatPrice
import com.example.project.service.AISearchService; // Import service tìm kiếm

@Service
public class AIAgentService {

    // ---- CẤU HÌNH ----
    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";

    private final RestTemplate restTemplate;
    private final SubscriptionPlanRepository planRepository;
    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final PersonRepository personRepository;
    private final MovieService movieService;
    private final Cache conversationCache;
    private final AISearchService aiSearchService;

    private final AIChatHistoryRepository chatHistoryRepository;

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
            CacheManager cacheManager,
            AISearchService aiSearchService,
            AIChatHistoryRepository chatHistoryRepository) {
        this.geminiApiKey = geminiApiKey;
        this.restTemplate = restTemplate;
        this.planRepository = planRepository;
        this.movieRepository = movieRepository;
        this.genreRepository = genreRepository;
        this.personRepository = personRepository;
        this.aiSearchService = aiSearchService;
        this.movieService = movieService;
        this.conversationCache = cacheManager.getCache("conversationCache");
        this.chatHistoryRepository = chatHistoryRepository;

        loadWebsiteContext();
    }

    // ---- 1. LOCAL SAFETY ----
    private static final Set<String> BLACKLISTED_KEYWORDS = Set.of(
            "sex", "tình dục", "xxx", "porn", "khỏa thân", "khiêu dâm", "làm tình", "ấu dâm", "vú", "bướm", "cu",
            "chịch", "đụ", "show hàng");

    // ---- 2. COUNTRY MAPPING ----
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
            Map.entry("Germany", List.of("đức", "duc", "germany", "german")));

    // ---- 3. GENRE MAPPING (Vietnamese → English) ----
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
            Map.entry("Lịch sử", List.of("lịch sử", "lich su", "history", "historical")));

    // ---- 4. MOOD MAPPING (Tâm trạng → Thể loại) ----
    private static final Map<String, List<String>> MOOD_MAPPING = Map.ofEntries(
            // Cảm xúc tiêu cực
            Map.entry("SAD",
                    List.of("buồn", "buon", "sad", "depressed", "tâm trạng", "stress", "mệt mỏi", "met moi", "chán",
                            "chan", "cô đơn", "co don", "thất vọng", "that vong")),
            Map.entry("ANGRY", List.of("tức", "tuc", "giận", "gian", "angry", "mad", "bực", "buc", "phẫn nộ")),
            Map.entry("SCARED", List.of("sợ", "so", "scared", "afraid", "lo lắng", "lo lang", "anxiety", "hồi hộp")),

            // Cảm xúc tích cực
            Map.entry("HAPPY", List.of("vui", "happy", "hạnh phúc", "hanh phuc", "sảng khoái", "khỏe", "khoai")),
            Map.entry("EXCITED",
                    List.of("hứng", "hung", "excited", "năng lượng", "nang luong", "nhiệt huyết", "nhiet huyet")),
            Map.entry("RELAXED",
                    List.of("thư giãn", "thu gian", "relax", "nhẹ nhàng", "nhe nhang", "bình yên", "binh yen",
                            "chill")),

            // Nhu cầu
            Map.entry("NEED_MOTIVATION",
                    List.of("động lực", "dong luc", "motivation", "inspire", "cảm hứng", "cam hung", "khuyến khích",
                            "khuyen khich")),
            Map.entry("NEED_LAUGH", List.of("cười", "cuoi", "laugh", "giải trí", "giai tri", "entertainment", "fun")),
            Map.entry("NEED_THINK",
                    List.of("suy ngẫm", "suy ngam", "think", "triết lý", "triet ly", "philosophy", "ý nghĩa", "y nghia",
                            "deep")),
            Map.entry("NEED_ADRENALINE", List.of("kích thích", "kich thich", "adrenaline", "gay cấn", "gay can",
                    "hồi hộp", "hoi hop", "intense")));

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
            "NEED_ADRENALINE", List.of("Hành động", "Gây cấn", "Kinh dị"));

    // Trong AIAgentService.java
    private boolean isUnsafe(String message) {
        if (message == null)
            return false;
        String lower = message.toLowerCase();

        // 1. Check danh sách từ khóa cứng
        for (String keyword : BLACKLISTED_KEYWORDS) {
            if (lower.contains(keyword))
                return true;
        }

        // 2. Check biến thể bằng Regex (Nâng cấp)
        // Bắt: s.e.x, c.h.i.c.h, d.u., p.o.r.n (bất kể dấu chấm, phẩy, cách)
        if (lower.matches(
                ".*(s[\\W_]*e[\\W_]*x|c[\\W_]*h[\\W_]*i[\\W_]*c[\\W_]*h|d[\\W_]*u[\\W_]|p[\\W_]*o[\\W_]*r[\\W_]*n).*")) {
            return true;
        }
        return false;
    }

    /**
     * PROMPT "FLATTENED BRAIN" (PHASE 8)
     */
    // THAY THẾ FLAT_PROMPT với version mới (thêm ví dụ)
    private static final String FLAT_PROMPT = "Bạn là trợ lý phân tích câu hỏi về phim. Trả về JSON thuần túy.\n\n" +

            "# QUY TẮC ƯU TIÊN (THEO THỨ TỰ GIẢM DẦN):\n" +
            "1. Output CHÍNH XÁC 1 JSON object\n" +
            "2. KHÔNG thêm ```json hoặc text ngoài\n" +
            "3. LOOKUP: Ưu tiên CAO NHẤT nếu có TÊN RIÊNG (viết hoa, trong ngoặc) của phim/người.\n" +
            "4. SUBSCRIPTION_INFO: Nếu có 'gói', 'giá', 'tiền', 'đăng ký', 'thanh toán', 'hủy'.\n" +
            "5. ƯU TIÊN 1: Nếu có TÊN PHIM cụ thể → intent=LOOKUP, q_subject=<tên phim>\n" +
            "6. DESCRIPTION_SEARCH: Nếu mô tả nội dung, cốt truyện, bối cảnh (dài > 5 từ) mà KHÔNG có tên phim cụ thể.\n"
            +
            "7. ƯU TIÊN 2: Nếu có 'gói'/'đăng ký'/'giá'/'cước' → intent=SUBSCRIPTION_INFO\n" +
            "8. FILTER: Nếu có 'phim' + tên người → intent=FILTER với f_actor/f_director\n" +
            "9. BẮT BUỘC: Multi-filter phải đồng bộ (ví dụ: 'phim mỹ 2024' -> country=US, year=2024).\n\n" +
            "10. TRENDING: Phim hot, mới nhất.\n\n" +

            "# CÁC TRƯỜNG:\n" +
            "- intent: FILTER|LOOKUP|TRENDING|SUBSCRIPTION_INFO|QA|CHITCHAT|UNKNOWN\n" +
            "- f_country: Vietnam|South Korea|China|Japan|United States|Thailand\n" +
            "- f_genres: [Hành động|Hài|Chính kịch|Lãng mạn|Kinh dị...]\n" +
            "- f_year_from, f_year_to: năm (BẮT BUỘC nếu có 'năm/trước/sau')\n" +
            "- f_director, f_actor: tên người (BẮT BUỘC nếu có 'phim của')\n" +
            "- q_subject: tên phim/người (LOOKUP - ƯU TIÊN CAO)\n" +
            "- q_type: movie|actor|director|cast\n" +
            "- subscription_query: price|plans|features|cancel|payment|refund\n\n" +

            "# VÍ DỤ (30 CASES - CRITICAL):\n" +
            "// === MOVIE TITLE SEARCH (Ưu tiên cao nhất) ===\n" +
            "Q: 'Mai' → {\"intent\":\"LOOKUP\",\"q_subject\":\"Mai\",\"q_type\":\"movie\"}\n" +
            "Q: 'phim Mai' → {\"intent\":\"LOOKUP\",\"q_subject\":\"Mai\",\"q_type\":\"movie\"}\n" +
            "Q: 'Bố Già' → {\"intent\":\"LOOKUP\",\"q_subject\":\"Bố Già\",\"q_type\":\"movie\"}\n" +
            "Q: 'phim Bố Già' → {\"intent\":\"LOOKUP\",\"q_subject\":\"Bố Già\",\"q_type\":\"movie\"}\n" +
            "Q: 'Interstellar' → {\"intent\":\"LOOKUP\",\"q_subject\":\"Interstellar\",\"q_type\":\"movie\"}\n" +
            "Q: 'Thanh Gươm Diệt Quỷ' → {\"intent\":\"LOOKUP\",\"q_subject\":\"Thanh Gươm Diệt Quỷ\",\"q_type\":\"movie\"}\n\n"
            +

            "// === SUBSCRIPTION QUERIES (Mới thêm) ===\n" +
            "Q: 'các gói đăng ký' → {\"intent\":\"SUBSCRIPTION_INFO\",\"subscription_query\":\"plans\"}\n" +
            "Q: 'gói cước' → {\"intent\":\"SUBSCRIPTION_INFO\",\"subscription_query\":\"plans\"}\n" +
            "Q: 'bao nhiêu tiền' → {\"intent\":\"SUBSCRIPTION_INFO\",\"subscription_query\":\"price\"}\n" +
            "Q: 'hủy đăng ký' → {\"intent\":\"SUBSCRIPTION_INFO\",\"subscription_query\":\"cancel\"}\n" +
            "Q: 'thanh toán thế nào' → {\"intent\":\"SUBSCRIPTION_INFO\",\"subscription_query\":\"payment\"}\n" +
            "Q: 'hoàn tiền' → {\"intent\":\"SUBSCRIPTION_INFO\",\"subscription_query\":\"refund\"}\n" +
            "Q: 'chính sách hoàn tiền' → {\"intent\":\"SUBSCRIPTION_INFO\",\"subscription_query\":\"refund\"}\n" +
            "Q: 'gói premium' → {\"intent\":\"SUBSCRIPTION_INFO\",\"subscription_query\":\"plans\"}\n\n" +

            "// === MULTI-FILTER (Kết hợp đồng bộ) ===\n" +
            "Q: 'phim hành động mỹ năm 2024' → {\"intent\":\"FILTER\",\"f_country\":\"United States\",\"f_genres\":[\"Hành động\"],\"f_year_from\":2024,\"f_year_to\":2024}\n"
            +
            "Q: 'phim hài hàn quốc' → {\"intent\":\"FILTER\",\"f_country\":\"South Korea\",\"f_genres\":[\"Hài\"]}\n" +
            "Q: 'phim kinh dị nhật sau 2020' → {\"intent\":\"FILTER\",\"f_country\":\"Japan\",\"f_genres\":[\"Kinh dị\"],\"f_year_from\":2020}\n"
            +
            "Q: 'phim việt nam tình cảm trước 2018' → {\"intent\":\"FILTER\",\"f_country\":\"Vietnam\",\"f_genres\":[\"Lãng mạn\"],\"f_year_to\":2017}\n"
            +
            "Q: 'phim hành động mỹ' → {\"intent\":\"FILTER\",\"f_country\":\"United States\",\"f_genres\":[\"Hành động\"]}\n\n"
            +

            "# VÍ DỤ (20 CASES - QUAN TRỌNG):\n" +
            "Q: 'phim hàn quốc' → {\"intent\":\"FILTER\",\"f_country\":\"South Korea\"}\n" +
            "Q: 'phim việt nam' → {\"intent\":\"FILTER\",\"f_country\":\"Vietnam\"}\n" +
            "Q: 'phim mỹ hành động' → {\"intent\":\"FILTER\",\"f_country\":\"United States\",\"f_genres\":[\"Hành động\"]}\n"
            +
            "Q: 'phim hài hàn quốc' → {\"intent\":\"FILTER\",\"f_country\":\"South Korea\",\"f_genres\":[\"Hài\"]}\n" +
            "Q: 'phim kinh dị nhật' → {\"intent\":\"FILTER\",\"f_country\":\"Japan\",\"f_genres\":[\"Kinh dị\"]}\n" +
            "Q: 'phim tình cảm việt nam sau 2018' → {\"intent\":\"FILTER\",\"f_country\":\"Vietnam\",\"f_genres\":[\"Lãng mạn\"],\"f_year_from\":2018}\n"
            +
            "Q: 'phim hành động mỹ trước 2020' → {\"intent\":\"FILTER\",\"f_country\":\"United States\",\"f_genres\":[\"Hành động\"],\"f_year_to\":2019}\n"
            +
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
            "Q: 'diễn viên phim Mai của Trấn Thành' → {\"intent\":\"LOOKUP\",\"q_subject\":\"Mai\",\"q_context\":\"Trấn Thành\",\"q_type\":\"actor\"}\n"
            +
            "Q: 'phim gì hot nhất' → {\"intent\":\"TRENDING\"}\n\n" +

            "// === 2. DESCRIPTION SEARCH (Mô tả nội dung - MỚI) ===\n" +
            "Q: 'phim về anh chàng hacker thiên tài hack vào FBI' → {\"intent\":\"DESCRIPTION_SEARCH\"}\n" +
            "Q: 'phim có ông già bay lên trời bằng chùm bóng bay' → {\"intent\":\"DESCRIPTION_SEARCH\"}\n" +
            "Q: 'phim zombie hàn quốc trên tàu hỏa' → {\"intent\":\"DESCRIPTION_SEARCH\"}\n" +
            "Q: 'tôi muốn tìm phim nói về du hành thời gian cứu thế giới' → {\"intent\":\"DESCRIPTION_SEARCH\"}\n" +
            "Q: 'phim gì mà nhân vật chính bị kẹt trên đảo hoang' → {\"intent\":\"DESCRIPTION_SEARCH\"}\n\n" +

            "// === 3. MOOD & CONTEXT (Cảm xúc/Hoàn cảnh - MỞ RỘNG) ===\n" +
            "Q: 'tôi đang buồn' → {\"intent\":\"FILTER\",\"f_genres\":[\"Chính kịch\",\"Lãng mạn\"]}\n" +
            "Q: 'muốn cười bể bụng' → {\"intent\":\"FILTER\",\"f_genres\":[\"Hài\"]}\n" +
            "Q: 'cần giải tỏa stress' → {\"intent\":\"FILTER\",\"f_genres\":[\"Hành động\",\"Hài\"]}\n" +
            "Q: 'xem với bạn gái' → {\"intent\":\"FILTER\",\"f_genres\":[\"Lãng mạn\",\"Hài\"]}\n" +
            "Q: 'phim cho cả gia đình xem cuối tuần' → {\"intent\":\"FILTER\",\"f_genres\":[\"Gia đình\",\"Hoạt hình\"]}\n"
            +
            "Q: 'muốn xem gì đó sâu sắc, hack não' → {\"intent\":\"FILTER\",\"f_genres\":[\"Bí ẩn\",\"Khoa học viễn tưởng\"]}\n"
            +
            "Q: 'tìm cảm giác mạnh' → {\"intent\":\"FILTER\",\"f_genres\":[\"Kinh dị\",\"Hành động\"]}\n\n" +

            "// === PERSON SEARCH ===\n" +
            "Q: 'Trấn Thành' → {\"intent\":\"LOOKUP\",\"q_subject\":\"Trấn Thành\",\"q_type\":\"actor\"}\n" +
            "Q: 'phim của Trấn Thành' → {\"intent\":\"FILTER\",\"f_actor\":\"Trấn Thành\"}\n" +
            "Q: 'Trấn Thành đóng phim gì' → {\"intent\":\"FILTER\",\"f_actor\":\"Trấn Thành\"}\n" +
            "Q: 'đạo diễn phim Bố Già' → {\"intent\":\"LOOKUP\",\"q_subject\":\"Bố Già\",\"q_type\":\"director\"}\n" +
            "Q: 'diễn viên phim Mai' → {\"intent\":\"LOOKUP\",\"q_subject\":\"Mai\",\"q_type\":\"cast\"}\n\n" +

            "// === 5. LOOKUP (Tra cứu cụ thể) ===\n" +
            "Q: 'Bố Già' → {\"intent\":\"LOOKUP\",\"q_subject\":\"Bố Già\",\"q_type\":\"movie\"}\n" +
            "Q: 'đạo diễn phim Mai' → {\"intent\":\"LOOKUP\",\"q_subject\":\"Mai\",\"q_type\":\"director\"}\n" +
            "Q: 'diễn viên phim Avenger' → {\"intent\":\"LOOKUP\",\"q_subject\":\"Avenger\",\"q_type\":\"cast\"}\n\n" +

            "// === TRENDING ===\n" +
            "Q: 'phim gì hot nhất' → {\"intent\":\"TRENDING\"}\n" +
            "Q: 'phim nổi bật' → {\"intent\":\"TRENDING\"}\n\n" +

            "Câu hỏi: \"%s\"\nJSON:";

    // ---- 4. LOGIC XỬ LÝ CHÍNH (CẬP NHẬT QUAN TRỌNG) ----

    public Map<String, Object> processMessage(String message, String conversationId) throws Exception {
        if (isUnsafe(message))
            return createResponse("Xin lỗi, nội dung này vi phạm chính sách an toàn của FFilm.", null);
        if (!isConfigured())
            throw new Exception("Gemini API key chưa cấu hình");

        String aiResponseText = "Xin lỗi, tôi chưa thể xử lý yêu cầu này.";
        List<Map<String, Object>> recommendedMovies = new ArrayList<>();

        // Lấy Context từ Cache
        ConversationContext context = conversationCache.get(conversationId, ConversationContext.class);
        if (context == null)
            context = new ConversationContext();

        message = message.replace("\"", "").replace("'", "").trim();
        String cleanMsg = message.toLowerCase();

        // 1. CHECK LOCAL SHORTCUTS
        if (cleanMsg.contains("liệt kê") && cleanMsg.contains("thể loại")) {
            return createResponse(formatGenresResponse(genreRepository.findAll(), "tất cả thể loại"), null);
        }

        // 2. CHECK FOLLOW-UP (XEM THÊM / CÒN KHÔNG / PHIM CỦA ỔNG)
        // Logic: Chỉ xử lý nếu Context cũ có QuestionAsked hợp lệ
        boolean isFollowUp = context.getLastQuestionAsked() != null &&
                (cleanMsg.matches("^(có|co|ok|oke|ờ|u|uh|uhm|được|dc)$") ||
                        cleanMsg.matches(".*(xem thêm|thêm|tiếp|nữa|còn|next).*") ||
                        cleanMsg.matches(".*(còn nữa không|có gì khác).*") ||
                        cleanMsg.matches(".*(của ổng|của bả|của anh ấy|của cô ấy|của người này).*"));

        if (isFollowUp) {
            // Gọi hàm xử lý FollowUp và lấy kết quả
            Map<String, Object> followUpResult = handleFollowUp(context, cleanMsg);
            // Cập nhật lại cache
            conversationCache.put(conversationId, context);
            return followUpResult;
        }

        // 3. CALL AI (NẾU KHÔNG PHẢI FOLLOW-UP)
        try {
            String prompt = String.format(FLAT_PROMPT, message);
            JSONObject request = buildGeminiRequest_Simple(prompt);
            JSONObject response = callGeminiAPI(request);
            String jsonText = extractTextResponse(response);

            JSONObject brain = parseJsonSafely(jsonText);

            if (brain == null) {
                // Fallback nếu AI không trả về JSON
                Map<String, Object> fallback = runKeywordFallback(message, context);
                aiResponseText = (String) fallback.get("message");
                recommendedMovies = (List<Map<String, Object>>) fallback.get("movies");
            } else {
                String intent = brain.optString("intent", "UNKNOWN");
                System.out.println("🔵 Intent: " + intent + " | Brain: " + brain.toString());

                switch (intent) {
                    case "DESCRIPTION_SEARCH":
                        Map<String, Object> searchResult = aiSearchService.getMovieRecommendation(message);
                        if (Boolean.TRUE.equals(searchResult.get("success"))) {
                            aiResponseText = (String) searchResult.get("answer");
                            List<String> suggestions = (List<String>) searchResult.get("suggestions");
                            if (suggestions != null) {
                                for (String title : suggestions) {
                                    List<Movie> dbMovies = movieRepository
                                            .findByTitleContainingIgnoreCase(title.trim());
                                    if (!dbMovies.isEmpty()) {
                                        // [QUAN TRỌNG] Thêm vào danh sách để vẽ thẻ
                                        recommendedMovies.add(movieService.convertToMap(dbMovies.get(0)));
                                    }
                                }
                            }
                        } else {
                            aiResponseText = "Xin lỗi, tôi chưa hiểu rõ mô tả.";
                        }
                        // Reset context cho search mới
                        context = new ConversationContext();
                        break;

                    case "FILTER":
                    case "SEMANTIC":
                        MovieSearchFilters filters = parseFlatFilters(brain);
                        if (filters.hasFilters()) {
                            // Reset context mới cho filter này
                            context = new ConversationContext();
                            List<Movie> movies = movieService.findMoviesByFilters(filters);

                            if (!movies.isEmpty()) {
                                context.setLastSubjectType("Filter");
                                context.setLastSubjectId(filters);
                                context.setLastQuestionAsked("ask_more_filter");

                                // [QUAN TRỌNG] Lấy 10 phim đầu tiên
                                List<Movie> firstPage = movies.stream().limit(10).collect(Collectors.toList());
                                for (Movie m : firstPage) {
                                    recommendedMovies.add(movieService.convertToMap(m));
                                    context.addShownMovieId(m.getMovieID()); // Đánh dấu đã xem
                                }

                                // Tạo câu dẫn tự nhiên
                                aiResponseText = generateNaturalResponse(filters, movies.size());

                                // Kiểm tra nếu filter theo người -> Set context follow-up
                                if (filters.getDirector() != null)
                                    updateContext(context, "Person", filters.getDirector(), "ask_director_movies");
                                else if (filters.getActor() != null)
                                    updateContext(context, "Person", filters.getActor(), "ask_person_movies");
                            } else {
                                aiResponseText = "Rất tiếc, không tìm thấy phim nào phù hợp với tiêu chí của bạn.";
                            }
                        } else {
                            Map<String, Object> fallback = runKeywordFallback(message, context);
                            aiResponseText = (String) fallback.get("message");
                            recommendedMovies = (List<Map<String, Object>>) fallback.get("movies");
                        }
                        break;

                    case "LOOKUP":
                        String subject = brain.optString("q_subject");
                        
                        // 1. Ưu tiên: Tìm theo Tên Phim
                        List<Movie> foundMovies = movieService.searchMoviesByTitle(subject);
                        
                        if (!foundMovies.isEmpty()) {
                            // Nếu tìm thấy phim -> Hiển thị danh sách phim (Top 10)
                            int count = Math.min(foundMovies.size(), 10);
                            List<Movie> topMovies = foundMovies.subList(0, count);
                            
                            aiResponseText = "Tìm thấy **" + foundMovies.size() + "** phim liên quan đến \"" + subject + "\". Dưới đây là các kết quả nổi bật:";
                            
                            for (Movie m : topMovies) {
                                recommendedMovies.add(movieService.convertToMap(m));
                            }
                            
                            context.setLastSubjectType("Movie");
                            context.setLastSubjectId(topMovies.get(0).getTmdbId());
                        } else {
                            // 2. [FIX AI] Fallback: Tìm theo Tên Người (Sử dụng searchMoviesCombined)
                            // Hàm này sẽ tự động tìm người -> lấy phim từ bảng MoviePerson -> trả về Map có "role_info"
                            List<Map<String, Object>> mixedResults = movieService.searchMoviesCombined(subject);
                            
                            if (!mixedResults.isEmpty()) {
                                // Vì step 1 đã tìm title và rỗng, nên kết quả ở đây chắc chắn là tìm theo Person
                                aiResponseText = "Tôi không tìm thấy phim nào tên \"" + subject + "\", nhưng tìm thấy nghệ sĩ có tên tương tự. Dưới đây là các phim của họ:";
                                
                                // Lấy Top 10 phim của diễn viên/đạo diễn đó
                                int count = Math.min(mixedResults.size(), 10);
                                recommendedMovies.addAll(mixedResults.subList(0, count));
                                
                                context.setLastSubjectType("Person");
                                context.setLastSubjectId(subject);
                            } else {
                                // 3. Fallback cuối cùng: Gemini chém gió (Keyword Search)
                                Map<String, Object> fallback = runKeywordFallback(subject, context);
                                aiResponseText = (String) fallback.get("message");
                                recommendedMovies = (List<Map<String, Object>>) fallback.get("movies");
                            }
                        }
                        break;

                    case "TRENDING":
                        context = new ConversationContext();
                        context.setLastSubjectType("Trending");
                        context.setLastQuestionAsked("ask_more_trending");
                        List<Movie> hotMovies = movieService.getHotMoviesForAI(10);
                        aiResponseText = "Dưới đây là Top 10 phim đang thịnh hành nhất trên FFilm:";
                        for (Movie m : hotMovies) {
                            recommendedMovies.add(movieService.convertToMap(m));
                            context.addShownMovieId(m.getMovieID());
                        }
                        break;

                    case "SUBSCRIPTION_INFO":
                        aiResponseText = handleSubscriptionQuery(brain.optString("subscription_query", "plans"));
                        break;

                    case "QA":
                    case "CHITCHAT":
                        aiResponseText = brain.optString("reply", "Xin chào! Tôi có thể giúp gì cho bạn?");
                        // Giữ nguyên context cũ
                        break;

                    default:
                        Map<String, Object> fallback = runKeywordFallback(message, context);
                        aiResponseText = (String) fallback.get("message");
                        recommendedMovies = (List<Map<String, Object>>) fallback.get("movies");
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            aiResponseText = "Đã có lỗi xảy ra: " + e.getMessage();
        }

        conversationCache.put(conversationId, context);
        return createResponse(aiResponseText, recommendedMovies);
    }

    // [MỚI] Hàm lưu lịch sử chat vào Database
    public void saveChatHistory(String sessionId, Integer userId, String userMsg, String botMsg,
            List<Map<String, Object>> movies) {
        try {
            // 1. Lưu User Message
            AIChatHistory userHistory = new AIChatHistory(userId, sessionId, userMsg, AIChatHistory.SenderRole.USER);
            chatHistoryRepository.save(userHistory);

            // 2. Xử lý Metadata (Danh sách ID phim)
            String metadata = null;
            if (movies != null && !movies.isEmpty()) {
                metadata = movies.stream()
                        .map(m -> String.valueOf(m.get("id"))) // Lấy Movie ID
                        .collect(Collectors.joining(","));
            }

            // 3. Lưu Bot Message kèm Metadata
            AIChatHistory botHistory = new AIChatHistory(userId, sessionId, botMsg, AIChatHistory.SenderRole.BOT);
            botHistory.setMetadata(metadata);
            chatHistoryRepository.save(botHistory);

        } catch (Exception e) {
            System.err.println("Lỗi lưu lịch sử chat: " + e.getMessage());
        }
    }

    // [MỚI] Hàm lấy lịch sử chat (cho API /history)
    public List<Map<String, Object>> getChatHistory(String sessionId, Integer userId) {
        List<AIChatHistory> historyList;

        // Ưu tiên lấy theo User ID nếu có
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

            // Nếu có metadata (ID phim), load lại thông tin phim
            if (h.getMetadata() != null && !h.getMetadata().isEmpty()) {
                try {
                    List<Integer> ids = Arrays.stream(h.getMetadata().split(","))
                            .map(Integer::parseInt)
                            .collect(Collectors.toList());

                    List<Map<String, Object>> movies = new ArrayList<>();
                    for (Integer id : ids) {
                        Movie m = movieRepository.findById(id).orElse(null);
                        if (m != null)
                            movies.add(movieService.convertToMap(m));
                    }
                    msgMap.put("movies", movies);
                } catch (Exception e) {
                    // Bỏ qua lỗi parse metadata
                }
            }
            return msgMap;
        }).collect(Collectors.toList());
    }

    // ---- 5. HELPERS LOGIC (CẬP NHẬT) ----

    // Xử lý Follow-up trả về Map (JSON) thay vì String
    private Map<String, Object> handleFollowUp(ConversationContext context, String message) {
        String q = context.getLastQuestionAsked();
        Object id = context.getLastSubjectId();
        String msg = message.toLowerCase();

        List<Map<String, Object>> movies = new ArrayList<>();
        String responseText = "Xin lỗi, tôi không hiểu ý bạn.";

        // 1. Xem thêm Filter
        if ("ask_more_filter".equals(q) && id instanceof MovieSearchFilters) {
            MovieSearchFilters f = (MovieSearchFilters) id;
            List<Movie> allMovies = movieService.findMoviesByFilters(f);

            // Lọc phim đã xem
            List<Movie> newBatch = allMovies.stream()
                    .filter(m -> !context.getShownMovieIds().contains(m.getMovieID()))
                    .limit(10)
                    .collect(Collectors.toList());

            if (!newBatch.isEmpty()) {
                responseText = "Dưới đây là các kết quả tiếp theo:";
                for (Movie m : newBatch) {
                    movies.add(movieService.convertToMap(m));
                    context.addShownMovieId(m.getMovieID());
                }
            } else {
                responseText = "Đã hết phim phù hợp với tiêu chí này rồi ạ.";
            }
            return createResponse(responseText, movies);
        }

        // 2. Xem thêm Trending
        if ("ask_more_trending".equals(q)) {
            // Lấy 20 phim hot
            List<Movie> allHot = movieService.getHotMoviesForAI(20);
            List<Movie> newBatch = allHot.stream()
                    .filter(m -> !context.getShownMovieIds().contains(m.getMovieID()))
                    .limit(10)
                    .collect(Collectors.toList());

            if (!newBatch.isEmpty()) {
                responseText = "Các phim hot khác đây ạ:";
                for (Movie m : newBatch) {
                    movies.add(movieService.convertToMap(m));
                    context.addShownMovieId(m.getMovieID());
                }
            } else {
                responseText = "Đã hiển thị hết danh sách phim hot.";
            }
            return createResponse(responseText, movies);
        }

        // 3. Hỏi "Phim của ổng" (Context: Đạo diễn / Diễn viên)
        // Lưu ý: id ở đây là Tên (String) hoặc ID (Integer)
        if (("ask_director_movies".equals(q) || "ask_person_movies".equals(q)) && msg.matches(".*(có|ok|xem|của).*")) {
            MovieSearchFilters f = new MovieSearchFilters();
            String name = "";

            if (id instanceof String)
                name = (String) id;
            else if (id instanceof Integer) { // Trường hợp lưu ID, cần query tên lại (ít dùng trong logic mới)
                // Tạm thời assume String vì updateContext lưu String tên
                name = String.valueOf(id);
            }

            if ("ask_director_movies".equals(q))
                f.setDirector(name);
            else
                f.setActor(name);

            // Chuyển Context sang Filter để hỗ trợ "xem thêm"
            context.setLastSubjectType("Filter");
            context.setLastSubjectId(f);
            context.setLastQuestionAsked("ask_more_filter");
            context.setShownMovieIds(new ArrayList<>()); // Reset list đã xem

            List<Movie> mList = movieService.findMoviesByFilters(f);
            if (!mList.isEmpty()) {
                responseText = "Các phim có sự tham gia của **" + name + "**:";
                for (Movie m : mList.stream().limit(10).toList()) {
                    movies.add(movieService.convertToMap(m));
                    context.addShownMovieId(m.getMovieID());
                }
            } else {
                responseText = "Hiện tại chưa có thêm phim nào của **" + name + "** trong hệ thống.";
            }
            return createResponse(responseText, movies);
        }

        // Fallback
        Map<String, Object> fallback = runKeywordFallback(message, context);
        return createResponse((String) fallback.get("message"), (List<Map<String, Object>>) fallback.get("movies"));
    }

    // ---- DESCRIPTION SEARCH HANDLER (NEW INTEGRATION) ----
    private String handleDescriptionSearch(String userDescription) {
        try {
            // 1. Gọi AISearchService để phân tích mô tả và lấy gợi ý
            Map<String, Object> searchResult = aiSearchService.getMovieRecommendation(userDescription);

            if (!Boolean.TRUE.equals(searchResult.get("success"))) {
                return "Xin lỗi, tôi chưa hiểu rõ mô tả phim của bạn. Bạn có thể nói rõ hơn về nội dung hoặc nhân vật không?";
            }

            String aiAnalysis = (String) searchResult.get("answer");
            List<String> suggestions = (List<String>) searchResult.get("suggestions");

            StringBuilder response = new StringBuilder();

            // 2. Đưa ra phân tích ngắn gọn của AI
            response.append("🤖 **Theo mô tả của bạn:**\n").append(aiAnalysis).append("\n\n");

            // 3. Kiểm tra các phim gợi ý có trong Database không
            if (suggestions != null && !suggestions.isEmpty()) {
                response.append("🎬 **Kết quả tìm kiếm trong kho phim FFilm:**\n");
                boolean foundAny = false;

                for (String title : suggestions) {
                    // Tìm trong DB (Case-insensitive match)
                    List<Movie> dbMovies = movieRepository.findByTitleContainingIgnoreCase(title.trim());

                    if (!dbMovies.isEmpty()) {
                        foundAny = true;
                        // Lấy phim đầu tiên khớp nhất
                        Movie m = dbMovies.get(0);
                        response.append("✅ **").append(m.getTitle()).append("**");
                        if (m.getReleaseDate() != null) {
                            response.append(" (")
                                    .append(new java.text.SimpleDateFormat("yyyy").format(m.getReleaseDate()))
                                    .append(")");
                        }
                        response.append(" - [Xem ngay](/movie/detail/").append(m.getMovieID()).append(")\n");
                    } else {
                        response.append("❌ ").append(title).append(" (Chưa có trên FFilm)\n");
                    }
                }

                if (!foundAny) {
                    response.append(
                            "\nRất tiếc, các phim khớp với mô tả này hiện chưa có trên hệ thống. Chúng tôi sẽ cập nhật sớm!");
                }
            } else {
                response.append(
                        "Tôi không tìm thấy tên phim cụ thể nào khớp với mô tả. Bạn nhớ thêm chi tiết nào không?");
            }

            return response.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "Đã có lỗi xảy ra khi tìm kiếm theo mô tả. Vui lòng thử lại sau.";
        }
    }

    // ---- SUBSCRIPTION QUERY HANDLER (NEW) ----
    private String handleSubscriptionQuery(String queryType) {
        try {
            List<SubscriptionPlan> plans = planRepository.findAll();

            if (plans.isEmpty()) {
                return "Hiện tại, thông tin về các gói cước của FFilm đang trong quá trình cập nhật. " +
                        "Bạn vui lòng theo dõi trang chủ để biết thêm chi tiết nhé!";
            }

            StringBuilder response = new StringBuilder();

            switch (queryType) {
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
                    response.append("• Không hỗ trợ hoàn tiền với bất cứ hình thức nào\n");
                    response.append("• Hỗ trợ 24/7 qua chat hoặc hotline 1900-xxxx\n\n");
                    response.append("Bạn muốn biết thêm chi tiết về gói nào không? 😊");
                    break;

                case "cancel":
                    response.append("🔄 **CHÍNH SÁCH HỦY ĐĂNG KÝ**\n\n");
                    response.append("Bạn không thể hủy đăng ký sau khi đã thanh toán. ");
                    response.append("Tài khoản sẽ còn hoạt động đến hết chu kỳ thanh toán hiện tại.\n\n");
                    break;

                case "refund":  
                    response.append("💸 **CHÍNH SÁCH HOÀN TIỀN**\n\n");
                    response.append("• FFilm KHÔNG hỗ trợ hoàn tiền với bất cứ hình thức nào\n");
                    response.append("• Vui lòng cân nhắc kỹ trước khi đăng ký\n");
                    response.append("• Gói đã thanh toán vẫn có hiệu lực đến hết chu kỳ\n\n");
                    break;

                case "payment":
                    response.append("💳 **PHƯƠNG THỨC THANH TOÁN**\n\n");
                    response.append("Chúng tôi hỗ trợ:\n");
                    response.append("• 💵 Chuyển khoản ngân hàng\n");
                    break;

                case "features":
                    response.append("🎬 **TÍNH NĂNG FFILM**\n\n");
                    response.append("• 📚 Thư viện 5,000+ phim & series\n");
                    response.append("• 🎥 Chất lượng HD, Full HD, 4K\n");
                    response.append("• 📱 Xem trên mọi thiết bị\n");
                    response.append("• 🚫 Không quảng cáo (gói trả phí)\n\n");
                    response.append("Bạn muốn xem các gói đăng ký không?");
                    break;

                default:
                    return handleSubscriptionQuery("plans"); // Fallback
            }

            return response.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "Xin lỗi, hiện tại tôi không thể lấy thông tin gói đăng ký. " +
                    "Vui lòng liên hệ support@ffilm.vn hoặc hotline 1900-xxxx.";
        }
    }

    // Helper: Format giá tiền
    private String formatPrice(Double price) {
        if (price == null || price == 0)
            return "Miễn phí";
        return String.format("%,.0fđ", price);
    }

    private String formatMoviesResponse(List<Movie> movies, String reason, ConversationContext ctx) {
        List<Integer> shownIds = ctx.getShownMovieIds() != null ? ctx.getShownMovieIds() : new ArrayList<>();
        List<Movie> newMovies = movies.stream()
                .filter(m -> !shownIds.contains(m.getMovieID()))
                .limit(5)
                .collect(Collectors.toList());

        if (newMovies.isEmpty())
            return "Đã hết phim để hiển thị cho yêu cầu này rồi ạ.";

        StringBuilder sb = new StringBuilder("FFilm tìm thấy " + newMovies.size() + " phim (" + reason + "):\n");
        for (Movie m : newMovies) {
            sb.append("• ").append(m.getTitle()).append(" (Rating: ").append(m.getRating()).append(")\n");
            ctx.addShownMovieId(m.getMovieID());
        }

        if (!newMovies.isEmpty()) {
            sb.append("\n(Gõ 'xem thêm' để xem các kết quả khác...)");
            if (ctx.getLastQuestionAsked() == null)
                ctx.setLastQuestionAsked("ask_more_filter");
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
            sb.append("📅 Năm: ").append(new java.text.SimpleDateFormat("yyyy").format(movie.getReleaseDate()))
                    .append("\n");
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

        if (newPersons.isEmpty())
            return "Không tìm thấy thông tin.";

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

    private Map<String, Object> runKeywordFallback(String msg, ConversationContext ctx) {
        String lower = msg.toLowerCase().trim();
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> movies = new ArrayList<>();
        String text = "";

        // PRIORITY 0: Subscription Keywords
        if (lower.matches(".*(gói|đăng ký|cước|giá|bao nhiêu tiền|thanh toán|hủy|miễn phí|premium|cho xem|list).*")
                && lower.matches(".*(gói|cước|giá|tiền).*")) {
            String responseText;
            if (lower.contains("hủy"))
                responseText = handleSubscriptionQuery("cancel");
            else if (lower.contains("thanh toán"))
                responseText = handleSubscriptionQuery("payment");
            else
                responseText = handleSubscriptionQuery("plans");

            // FIX: Bọc responseText vào Map
            return createResponse(responseText, null);
        }

        // PRIORITY 1: Movie Title Search (Ưu tiên cao)
        // Loại bỏ noise words để tìm tên phim
        String cleanTitle = lower
                .replaceAll("^(phim|xem phim|tìm phim|có phim|film)\\s+", "")
                .replaceAll("\\s+(có|không|nào|gì|đâu)$", "")
                .trim();

        if (cleanTitle.length() >= 2) { // Tên phim tối thiểu 2 ký tự
            List<Movie> moviesByTitle = movieService.searchMoviesByTitle(cleanTitle);
            if (!moviesByTitle.isEmpty()) {
                MovieSearchFilters f = new MovieSearchFilters();
                f.setKeyword(cleanTitle);
                ctx = new ConversationContext();
                ctx.setLastSubjectType("Filter");
                ctx.setLastSubjectId(f);
                ctx.setLastQuestionAsked("ask_more_filter");
                // FIX: Bọc kết quả vào Map
                return createResponse("Tìm thấy phim khớp với từ khóa '" + cleanTitle + "':", movies);
            }
        }

        // PRIORITY 2: Person Search
        if (!lower.contains("phim") && msg.split("\\s+").length <= 4) {
            List<Person> persons = personRepository.findByFullNameContainingIgnoreCase(msg);
            if (!persons.isEmpty()) {
                ctx.setLastSubjectType("Person");
                // Giả sử lấy người đầu tiên để set context
                ctx.setLastSubjectId(persons.get(0).getPersonID());
                ctx.setLastQuestionAsked("ask_person_movies");

                // Format response cho person
                String personText = formatPersonsResponse(persons, msg, ctx);
                return createResponse(personText, null);
            }
        }

        // PRIORITY 3: Mood Detection
        List<String> moodGenres = detectMood(lower);
        if (!moodGenres.isEmpty()) {
            MovieSearchFilters f = new MovieSearchFilters();
            f.setGenres(moodGenres);
            return executeFilter(f, ctx, "phim phù hợp với tâm trạng của bạn");
        }

        // PRIORITY 4: Genre Detection
        List<String> genres = detectGenres(lower);
        if (!genres.isEmpty()) {
            MovieSearchFilters f = new MovieSearchFilters();
            f.setGenres(genres);
            return executeFilter(f, ctx, "phim thể loại " + String.join(", ", genres));
        }

        // PRIORITY 5: Country Detection
        String country = detectCountry(lower);
        if (country != null) {
            MovieSearchFilters f = new MovieSearchFilters();
            f.setCountry(normalizeCountryForDB(country));
            return executeFilter(f, ctx, "phim " + country);
        }

        // PRIORITY 6: Trending
        if (lower.matches(".*(hot|xu hướng|phổ biến|nổi bật|đang xem|mới nhất).*")) {
            ctx.setLastSubjectType("Trending");
            ctx.setLastQuestionAsked("ask_more_trending");
            List<Movie> hotMovies = movieService.getHotMoviesForAI(5);
            for (Movie m : hotMovies) {
                movies.add(movieService.convertToMap(m));
                ctx.addShownMovieId(m.getMovieID());
            }
            // FIX: Bọc kết quả vào Map
            return createResponse("Dưới đây là các phim đang hot:", movies);
        }

        // FINAL FALLBACK: No match
        ctx.setShownMovieIds(new ArrayList<>());
        ctx.setShownPersonIds(new ArrayList<>());
        ctx.setLastQuestionAsked(null);

        // FIX: Bọc thông báo lỗi vào Map
        return createResponse("Rất tiếc, FFilm không tìm thấy kết quả nào cho '" + msg + "'.\n\n" +
                "💡 Gợi ý:\n" +
                "• Tìm theo thể loại: 'phim hài', 'phim kinh dị', 'phim hành động'\n" +
                "• Tìm theo quốc gia: 'phim hàn quốc', 'phim việt nam', 'phim mỹ'\n" +
                "• Tìm theo tâm trạng: 'tôi đang buồn', 'tôi cần động lực', 'muốn cười'\n" +
                "• Tìm theo tên: 'Mai', 'Bố Già', 'Interstellar'\n" +
                "• Gói đăng ký: 'các gói cước', 'bao nhiêu tiền'", null);
    }

    private String generateNaturalResponse(MovieSearchFilters f, int count) {
        StringBuilder sb = new StringBuilder("Đã tìm thấy ");
        sb.append(count).append(" phim");

        if (f.getGenres() != null && !f.getGenres().isEmpty())
            sb.append(" thể loại **").append(String.join(", ", f.getGenres())).append("**");
        if (f.getCountry() != null)
            sb.append(" của **").append(f.getCountry()).append("**");
        if (f.getYearFrom() != null)
            sb.append(" năm **").append(f.getYearFrom()).append("**");
        if (f.getActor() != null)
            sb.append(" có diễn viên **").append(f.getActor()).append("**");

        sb.append(" cho bạn:");
        return sb.toString();
    }

    // Helper method - THÊM MỚI sau runKeywordFallback()
    private Map<String, Object> executeFilter(MovieSearchFilters f, ConversationContext ctx, String reason) {
        ctx = new ConversationContext();
        List<Movie> movies = movieService.findMoviesByFilters(f);

        if (movies.isEmpty()) {
            return createResponse("Rất tiếc, hiện tại FFilm chưa có " + reason + " trong kho.\n\n" +
                    "💡 Thử tìm kiếm khác:\n" +
                    "• Thay đổi thể loại hoặc quốc gia\n" +
                    "• Xem phim hot: 'phim gì hot nhất'", null);
        }

        ctx.setLastSubjectType("Filter");
        ctx.setLastSubjectId(f);
        ctx.setLastQuestionAsked("ask_more_filter");

        List<Map<String, Object>> resultMovies = new ArrayList<>();
        // Lấy tối đa 10 phim (để hiển thị nhiều hơn như bạn yêu cầu)
        for (Movie m : movies.stream().limit(10).toList()) {
            resultMovies.add(movieService.convertToMap(m));
            ctx.addShownMovieId(m.getMovieID());
        }

        // Gợi ý similar movies nếu có country + genre
        String suggestion = "";
        if (f.getCountry() != null && f.getGenres() != null && !f.getGenres().isEmpty()) {
            suggestion = "\n\n💡 Có thể bạn cũng thích: 'phim " + f.getGenres().get(0).toLowerCase() + " "
                    + f.getCountry().toLowerCase() + "'";
        }

        return createResponse(formatMoviesResponse(movies, reason, ctx) + suggestion, resultMovies);
    }

    // Helper: Format giá tiền (Fix lỗi compilation)
    private String formatPrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) == 0)
            return "Miễn phí";
        return String.format("%,.0fđ", price);
    }

    // ---- UTILS ----

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
        if (j == null)
            return f;
        try {
            // Normalize country từ AI
            if (j.has("f_country")) {
                String aiCountry = j.getString("f_country");
                f.setCountry(normalizeCountryForDB(aiCountry));
            }

            if (j.has("f_genres")) {
                List<String> g = new ArrayList<>();
                JSONArray a = j.optJSONArray("f_genres");
                if (a != null)
                    for (int i = 0; i < a.length(); i++)
                        g.add(a.getString(i));
                f.setGenres(g);
            }

            if (j.has("f_year_from"))
                f.setYearFrom(j.optInt("f_year_from"));
            if (j.has("f_year_to"))
                f.setYearTo(j.optInt("f_year_to")); // THÊM
            if (j.has("f_director"))
                f.setDirector(j.optString("f_director"));
            if (j.has("f_actor"))
                f.setActor(j.optString("f_actor"));
            if (j.has("keyword"))
                f.setKeyword(j.optString("keyword"));

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
        ctx.setLastSubjectType(type);
        ctx.setLastSubjectId(id);
        ctx.setLastQuestionAsked(question);
    }

    // ---- DETECTION HELPERS ----

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
            case "South Korea":
                return "Korea"; // TMDB có thể lưu "Korea" hoặc "South Korea"
            case "Viet Nam":
                return "Vietnam"; // Chuẩn hóa
            case "United States":
                return "United States of America";
            default:
                return userCountry;
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
        config.put("temperature", 0.1);
        config.put("maxOutputTokens", 2048);
        body.put("generationConfig", config);
        JSONArray safety = new JSONArray();
        safety.put(new JSONObject().put("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT").put("threshold",
                "BLOCK_LOW_AND_ABOVE"));
        body.put("safetySettings", safety);
        return body;
    }

    private JSONObject callGeminiAPI(JSONObject body) throws Exception {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);
            ResponseEntity<String> resp = restTemplate.exchange(GEMINI_API_URL + geminiApiKey, HttpMethod.POST, entity,
                    String.class);
            return new JSONObject(resp.getBody());
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429)
                throw new Exception("Hệ thống đang bận, vui lòng thử lại sau giây lát.");
            throw e;
        }
    }

    private String extractTextResponse(JSONObject json) {
        try {
            return json.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                    .getJSONObject(0).getString("text");
        } catch (Exception e) {
            return "";
        }
    }

    private String formatGenresResponse(List<Genre> genres, String reason) {
        StringBuilder sb = new StringBuilder("Danh sách " + reason + ":\n");
        genres.forEach(g -> sb.append("• ").append(g.getName()).append("\n"));
        return sb.toString();
    }

    public boolean isConfigured() {
        return geminiApiKey != null && !geminiApiKey.isEmpty();
    }

    private void loadWebsiteContext() {
    }

    // Helper cập nhật response format
    private Map<String, Object> createResponse(String msg, List<Map<String, Object>> movies) {
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", msg);
        if (movies != null && !movies.isEmpty()) {
            res.put("movies", movies);
        }
        res.put("type", "website");
        res.put("timestamp", System.currentTimeMillis());
        return res;
    }
}