package com.example.project.service;

import com.example.project.model.Genre; // <-- THÊM
import com.example.project.model.Movie;
import com.example.project.model.Person; // <-- THÊM
import com.example.project.model.SubscriptionPlan;
import com.example.project.repository.GenreRepository;
import com.example.project.repository.MovieRepository;
import com.example.project.repository.PersonRepository; // <-- THÊM
import com.example.project.repository.SubscriptionPlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired; // <-- THÊM
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
// import org.springframework.http.client.SimpleClientHttpRequestFactory; // <-- XÓA
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AIAgentService {

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    // [FIX LỖI 404] Giữ nguyên v1beta và model 2.5-flash của bạn
    private static final String GEMINI_API_URL = 
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";

    // [SỬA LỖI KIẾN TRÚC] Khai báo là final
    private final RestTemplate restTemplate;
    private final SubscriptionPlanRepository planRepository;
    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final PersonRepository personRepository; // <-- THÊM
    private final MovieService movieService;

    private Map<String, Object> websiteContext;

    /**
     * [VIẾT LẠI - SỬA LỖI CRASH]
     * Sử dụng Constructor Injection để Spring tiêm tất cả dependencies.
     */
    @Autowired
    public AIAgentService(
            @Value("${gemini.api.key:}") String geminiApiKey,
            RestTemplate restTemplate, // <-- Tiêm từ RestTemplateConfig
            SubscriptionPlanRepository planRepository,
            MovieRepository movieRepository,
            GenreRepository genreRepository,
            PersonRepository personRepository, // <-- THÊM
            MovieService movieService
    ) {
        this.geminiApiKey = geminiApiKey;
        this.restTemplate = restTemplate;
        this.planRepository = planRepository;
        this.movieRepository = movieRepository;
        this.genreRepository = genreRepository;
        this.personRepository = personRepository; // <-- THÊM
        this.movieService = movieService;
        
        loadWebsiteContext();
    }

    private void loadWebsiteContext() {
        try {
            ClassPathResource resource = new ClassPathResource("static/data/ai-context.json"); //
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

    /**
     * [VIẾT LẠI - RAG 2.0] Xử lý message (ĐÃ GỠ BỎ RAG 1.0)
     */
    public Map<String, Object> processMessage(String message, String conversationId) throws Exception {
        System.out.println("========================================");
        System.out.println("🔵 SERVICE: processMessage() called (RAG 2.0 Mode)");
        System.out.println("Message: " + message);
        System.out.println("========================================");
        
        if (!isConfigured()) {
            System.err.println("❌ Gemini API key not configured!");
            throw new Exception("Gemini API key chưa cấu hình");
        }

        // 1. [SỬA VĐ 5] Lấy prompt hệ thống (từ DB + JSON)
        String systemPrompt = buildSystemPrompt(); // ĐÃ FIX (biết gói cước, phim, thể loại)
        
        // 2. [SỬA VĐ 5] Nhận diện ý định (RAG 2.0)
        IntentType intent = detectIntent(message);
        String finalPrompt;
        String aiResponseText; // Câu trả lời cuối cùng

        // 3. [SỬA VĐ 5] Xử lý (Retrieval hoặc Generation)
        try {
            if (intent == IntentType.SEARCH_MOVIE) {
                System.out.println("🔵 (RAG 2.0) Intent: SEARCH_MOVIE");
                String cleanedMessage = cleanSearchQuery(message, "phim", "tìm", "gợi ý");
                List<Movie> movies = movieService.searchMoviesByTitle(cleanedMessage);
                // [FIX] Java tự trả lời, KHÔNG gọi AI
                aiResponseText = formatMoviesResponse(movies, cleanedMessage); 
                
            } else if (intent == IntentType.SEARCH_PERSON) {
                System.out.println("🔵 (RAG 2.0) Intent: SEARCH_PERSON");
                String cleanedMessage = cleanSearchQuery(message, "diễn viên", "đạo diễn", "phim của");
                List<Person> persons = personRepository.findByFullNameContainingIgnoreCase(cleanedMessage); 
                // [FIX] Java tự trả lời, KHÔNG gọi AI
                aiResponseText = formatPersonsResponse(persons, cleanedMessage);
                
            } else if (intent == IntentType.SEARCH_GENRE) {
                System.out.println("🔵 (RAG 2.0) Intent: SEARCH_GENRE");
                String cleanedMessage = cleanSearchQuery(message, "thể loại", "phim thể loại");
                List<Genre> genres = genreRepository.findByNameContainingIgnoreCase(cleanedMessage); 
                // [FIX] Java tự trả lời, KHÔNG gọi AI
                aiResponseText = formatGenresResponse(genres, cleanedMessage);

            } else {
                // (IntentType.Q_A) - Câu hỏi thông thường
                System.out.println("🔵 (RAG 2.0) Intent: Q_A. Calling Gemini...");
                finalPrompt = buildFinalPrompt_QA(systemPrompt, message); // Prompt Q&A
                
                JSONObject requestBody = buildGeminiRequest_Simple(finalPrompt);
                JSONObject geminiResponse = callGeminiAPI(requestBody);
                aiResponseText = extractTextResponse(geminiResponse); // Lấy câu trả lời
            }
        } catch (Exception e) {
             System.err.println("Lỗi RAG/Gemini (processMessage): " + e.getMessage());
             e.printStackTrace();
             aiResponseText = "Xin lỗi, tôi gặp lỗi khi xử lý yêu cầu: " + e.getMessage();
        }
        
        System.out.println("🟢 (RAG 2.0) Response generated.");

        Map<String, Object> result = Map.of(
            "success", true,
            "message", aiResponseText,
            "type", "website",
            "timestamp", System.currentTimeMillis()
        );
        
        System.out.println("✅ SERVICE: Returning result");
        return result;
    }

    // Enum nội bộ để phân loại ý định
    private enum IntentType { SEARCH_MOVIE, SEARCH_PERSON, SEARCH_GENRE, Q_A }

    /**
     * [MỚI - RAG 2.0] Nhận diện ý định người dùng
     */
    private IntentType detectIntent(String message) {
        String lower = message.toLowerCase();
        
        String[] personKeywords = {"diễn viên", "đạo diễn", "phim của"};
        String[] genreKeywords = {"thể loại", "phim thể loại"};
        String[] movieKeywords = {"tìm phim", "phim về", "gợi ý phim", "phim nào"};
        // [FIX] Thêm từ khóa Q&A
        String[] qaKeywords = {"là gì", "tại sao", "như thế nào", "gói cước", "chính sách", "liên hệ", "có bao nhiêu"};

        for (String kw : qaKeywords) {
            if (lower.contains(kw)) return IntentType.Q_A;
        }
        for (String kw : personKeywords) {
            if (lower.contains(kw)) return IntentType.SEARCH_PERSON;
        }
        for (String kw : genreKeywords) {
            if (lower.contains(kw)) return IntentType.SEARCH_GENRE;
        }
        for (String kw : movieKeywords) {
            if (lower.contains(kw)) return IntentType.SEARCH_MOVIE;
        }
        
        // Mặc định cuối cùng: Tìm Phim (vd: người dùng chỉ gõ "Avengers")
        return IntentType.SEARCH_MOVIE;
    }

    /**
     * [MỚI - RAG 2.0] Tách từ khóa khỏi câu hỏi
     */
    private String cleanSearchQuery(String message, String... keywordsToRemove) {
        String cleaned = message.toLowerCase();
        for (String kw : keywordsToRemove) {
            cleaned = cleaned.replace(kw, "");
        }
        cleaned = cleaned.replace("tôi muốn", "").replace("tìm giúp tôi", "").replace("bạn biết gì về", "");
        return cleaned.trim();
    }


    /**
     * [MỚI - RAG 2.0] Các hàm tự trả lời (Không gọi AI)
     */
    private String formatMoviesResponse(List<Movie> movies, String keyword) {
        if (movies == null || movies.isEmpty()) {
            return "Rất tiếc, FFilm hiện chưa tìm thấy phim nào khớp với từ khóa '" + keyword + "'.";
        }
        StringBuilder sb = new StringBuilder("Chào bạn, FFilm tìm thấy " + movies.size() + " phim khớp (dưới đây là 5 phim hàng đầu):\n");
        movies.stream().limit(5).forEach(m -> {
            sb.append("• ").append(m.getTitle())
              .append(" (Rating: ").append(String.format("%.1f", m.getRating())).append(")\n");
        });
        sb.append("\nBạn có thể tìm kiếm tên phim cụ thể để FFilm hỗ trợ tốt hơn nhé!");
        return sb.toString();
    }

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
    
    private String formatGenresResponse(List<Genre> genres, String keyword) {
        if (genres == null || genres.isEmpty()) {
            return "Rất tiếc, FFilm không tìm thấy thể loại '" + keyword + "'.";
        }
        StringBuilder sb = new StringBuilder("Chào bạn, FFilm tìm thấy " + genres.size() + " thể loại khớp:\n");
        genres.stream().limit(5).forEach(g -> {
            sb.append("• ").append(g.getName()).append("\n");
        });
        return sb.toString();
    }
    
    /**
     * [MỚI - RAG 2.0] Xây dựng Prompt cho Q&A
     */
    private String buildFinalPrompt_QA(String systemPrompt, String userMessage) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(systemPrompt); // Chèn kiến thức hệ thống
        prompt.append("\n\n---BẮT ĐẦU YÊU CẦU---\n");
        prompt.append("Câu hỏi của người dùng: \"").append(userMessage).append("\"\n");
        prompt.append("\nHãy trả lời câu hỏi của người dùng dựa trên Kiến thức hệ thống của bạn.");
        prompt.append("\nTrả lời NGẮN GỌN, thân thiện, và LUÔN LUÔN bằng Tiếng Việt.");
        return prompt.toString();
    }


    /**
     * [SỬA LỖI VĐ 5] Lấy thông tin động từ DB (Thêm Gói cước, Diễn viên)
     */
    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là trợ lý AI của FFilm, một nền tảng xem phim trực tuyến. ");
        sb.append("QUAN TRỌNG: LUÔN LUÔN trả lời bằng Tiếng Việt.\n\n"); // [FIX VĐ 4]
        
        try {
            long movieCount = movieRepository.count();
            long genreCount = genreRepository.count();
            long personCount = personRepository.count(); // <-- THÊM
            
            sb.append("Thông tin hệ thống FFilm (Dữ liệu động):\n");
            sb.append("- Tổng số phim hiện tại: ").append(movieCount).append(" bộ phim.\n");
            sb.append("- Tổng số thể loại: ").append(genreCount).append(" thể loại.\n");
            sb.append("- Tổng số diễn viên/đạo diễn: ").append(personCount).append(" người.\n"); // <-- THÊM
            
            // [FIX LỖI VĐ 5] Thêm logic lấy Gói cước
            List<SubscriptionPlan> plans = planRepository.findAll();
            if (!plans.isEmpty()) {
                sb.append("- Các gói đăng ký:\n");
                plans.forEach(p -> {
                    if (p.isStatus()) { 
                        sb.append("  • ").append(p.getPlanName()).append(": ")
                          .append(String.format("%,.0f", p.getPrice())).append("đ/tháng. (Mô tả: ")
                          .append(p.getDescription()).append(")\n");
                    }
                });
            } else {
                 sb.append("- Hiện chưa có thông tin gói cước.\n");
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi lấy dữ liệu động cho AI Agent (System Prompt): " + e.getMessage());
        }

        // Lấy dữ liệu TĨNH từ ai-context.json
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

    /**
     * [GIỮ NGUYÊN - FIX LỖI v1beta] Tạo JSON request body (Q&A đơn giản)
     */
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

        return body;
    }

    /**
     * [GIỮ NGUYÊN - SỬA LỖI VĐ 1 & 4] Viết lại hàm gọi API để parse JSON an toàn
     */
    private JSONObject callGeminiAPI(JSONObject body) throws Exception {
        try {
            String apiUrl = GEMINI_API_URL + geminiApiKey; // Giữ v1beta
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

            System.out.println("🌐 Calling Gemini (v1beta)...");
            ResponseEntity<String> resp = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);
            
            String responseBody = resp.getBody();
            System.out.println("📡 Status: " + resp.getStatusCode());
            
            if (responseBody == null || responseBody.isEmpty()) {
                throw new Exception("Gemini trả về body rỗng");
            }

            JSONObject json = new JSONObject(responseBody);
            
            if (json.has("error")) {
                JSONObject error = json.optJSONObject("error");
                if (error != null && error.optInt("code") == 404) {
                     throw new Exception("Gemini API Lỗi 404: Model 'gemini-2.5-flash' không tìm thấy trên 'v1beta'. Vui lòng kiểm tra lại URL API trong AIAgentService.java.");
                }
                throw new Exception("Gemini error: " + (error != null ? error.optString("message", "Unknown") : "Unknown"));
            }

            if (json.has("promptFeedback")) {
                JSONObject feedback = json.optJSONObject("promptFeedback");
                String reason = (feedback != null) ? feedback.optString("blockReason", "") : "";
                if (!reason.isEmpty()) {
                    System.err.println("❌ Gemini Blocked: " + reason);
                    // [SỬA] Trả về thông báo thân thiện thay vì crash
                    return new JSONObject().put("error", new JSONObject().put("message", "Yêu cầu của bạn bị chặn vì lý do: " + reason));
                }
            }

            return json; // Trả về toàn bộ JSON response

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            System.err.println("❌ HTTP Error: " + e.getStatusCode());
            System.err.println("Body: " + e.getResponseBodyAsString());
            throw new Exception("Gemini API lỗi: " + e.getResponseBodyAsString());
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            throw new Exception(e.getMessage() != null ? e.getMessage() : "Lỗi gọi Gemini API");
        }
    }
    
    /**
     * [VIẾT LẠI - SỬA LỖI VĐ 1] Lấy text từ JSON response (Parse an toàn)
     */
    private String extractTextResponse(JSONObject jsonResponse) throws Exception {
        try {
            // [FIX LỖI image_9021a5.jpg] Kiểm tra lỗi do API trả về (vd: 404)
            if (jsonResponse.has("error")) {
                return "Xin lỗi, có lỗi xảy ra: " + jsonResponse.getJSONObject("error").getString("message");
            }
            
            JSONArray candidates = jsonResponse.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                JSONObject feedback = jsonResponse.optJSONObject("promptFeedback");
                if (feedback != null && "SAFETY".equals(feedback.optString("blockReason"))) {
                    return "Xin lỗi, nội dung này vi phạm chính sách an toàn của chúng tôi.";
                }
                throw new Exception("Không tìm thấy 'candidates' trong response.");
            }

            JSONObject candidate = candidates.getJSONObject(0);
            JSONObject content = candidate.optJSONObject("content");
            if (content == null) throw new Exception("Không tìm thấy 'content'.");

            JSONArray parts = content.optJSONArray("parts");
            // [FIX LỖI image_9021a5.jpg]
            if (parts == null || parts.length() == 0) {
                System.err.println("Lỗi parse AI Response: 'parts' not found. JSON: " + jsonResponse.toString());
                return "Xin lỗi, tôi gặp lỗi khi xử lý phản hồi từ AI (parts not found).";
            }

            String text = parts.getJSONObject(0).optString("text", "");
            if (text.isEmpty()) {
                String reason = candidate.optString("finishReason", "");
                if ("SAFETY".equals(reason)) return "Xin lỗi, nội dung này vi phạm chính sách an toàn.";
                if ("MAX_TOKENS".equals(reason)) return "Câu trả lời quá dài, tôi không thể hiển thị hết.";
                return "Xin lỗi, AI trả về phản hồi rỗng.";
            }
            
            return text.trim();
        } catch (Exception e) {
            System.err.println("Lỗi parse AI Response: " + e.getMessage());
            System.err.println("JSON gốc: " + jsonResponse.toString());
            return "Xin lỗi, tôi gặp lỗi khi xử lý phản hồi từ AI (Exception: " + e.getMessage() + ").";
        }
    }

    private Map<String, Object> getDefaultContext() {
        return Map.of(
            "about", "FFilm là nền tảng xem phim trực tuyến hàng đầu Việt Nam.",
            "features", Arrays.asList("Thư viện 15,000+ phim", "Chất lượng 4K", "Không quảng cáo"),
            "plans", Collections.emptyList(),
            "policies", Map.of("Hoàn tiền", "14 ngày đầu", "Bảo mật", "SSL 256-bit")
        );
    }

    public boolean isConfigured() {
        return geminiApiKey != null && !geminiApiKey.trim().isEmpty();
    }
}