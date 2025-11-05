package com.example.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.util.*;

@Service
public class AIAgentService {

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    private static final String GEMINI_API_URL = 
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";

    private static final String TMDB_API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private static final String TMDB_BASE = "https://api.themoviedb.org/3";

    private final RestTemplate restTemplate;
    private Map<String, Object> websiteContext;

    public AIAgentService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(30000);
        this.restTemplate = new RestTemplate(factory);
        loadWebsiteContext();
    }

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

    public Map<String, Object> processMessage(String message, String conversationId) throws Exception {
        System.out.println("========================================");
        System.out.println("🔵 SERVICE: processMessage() called");
        System.out.println("Message: " + message);
        System.out.println("ConversationId: " + conversationId);
        System.out.println("========================================");
        
        if (!isConfigured()) {
            System.err.println("❌ Gemini API key not configured!");
            throw new Exception("Gemini API key chưa cấu hình");
        }
        
        System.out.println("🔵 API Key configured: YES");

        boolean isMovieQuery = detectMovieQuery(message);
        System.out.println("🔵 Is movie query: " + isMovieQuery);
        
        String prompt;
        if (isMovieQuery) {
            System.out.println("🔵 Searching movies...");
            List<Map<String, Object>> movies = searchMovies(message);
            System.out.println("🔵 Found " + movies.size() + " movies");
            prompt = buildMoviePrompt(message, movies);
        } else {
            System.out.println("🔵 Building website prompt...");
            prompt = buildWebsitePrompt(message);
        }

        System.out.println("🔵 Prompt built (length: " + prompt.length() + ")");
        System.out.println("🔵 Calling Gemini API...");
        
        String aiResponse = callGeminiAPI(prompt);
        
        System.out.println("🟢 Gemini response received (length: " + aiResponse.length() + ")");

        Map<String, Object> result = Map.of(
            "success", true,
            "message", aiResponse,
            "type", isMovieQuery ? "movie" : "website",
            "timestamp", System.currentTimeMillis()
        );
        
        System.out.println("✅ SERVICE: Returning result");
        return result;
    }

    private boolean detectMovieQuery(String message) {
        String lower = message.toLowerCase();
        String[] keywords = {"phim", "movie", "xem", "tìm", "gợi ý", "diễn viên", "đạo diễn"};
        for (String kw : keywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private List<Map<String, Object>> searchMovies(String query) {
        try {
            String url = TMDB_BASE + "/search/multi?api_key=" + TMDB_API_KEY + 
                        "&language=vi-VN&query=" + query + "&page=1";
            
            String response = restTemplate.getForObject(url, String.class);
            JSONObject json = new JSONObject(response);
            JSONArray results = json.optJSONArray("results");

            List<Map<String, Object>> movies = new ArrayList<>();
            if (results != null) {
                for (int i = 0; i < Math.min(5, results.length()); i++) {
                    JSONObject item = results.getJSONObject(i);
                    if (item.optString("media_type").equals("person")) continue;

                    movies.add(Map.of(
                        "id", item.optInt("id"),
                        "title", item.optString("title", item.optString("name", "")),
                        "overview", item.optString("overview", ""),
                        "rating", item.optDouble("vote_average", 0.0),
                        "year", extractYear(item)
                    ));
                }
            }
            return movies;
        } catch (Exception e) {
            System.err.println("TMDB error: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private String extractYear(JSONObject item) {
        String date = item.optString("release_date", item.optString("first_air_date", ""));
        return date.length() >= 4 ? date.substring(0, 4) : "";
    }

    private String buildMoviePrompt(String userMessage, List<Map<String, Object>> movies) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Bạn là trợ lý AI của FFilm.\n\n");
        prompt.append("Người dùng hỏi: \"").append(userMessage).append("\"\n\n");

        if (!movies.isEmpty()) {
            prompt.append("Kết quả tìm kiếm:\n");
            for (Map<String, Object> movie : movies) {
                prompt.append("- ").append(movie.get("title"))
                      .append(" (").append(movie.get("year")).append(")")
                      .append(" - ⭐ ").append(movie.get("rating")).append("\n");
            }
        } else {
            prompt.append("Không tìm thấy phim phù hợp.\n");
        }

        prompt.append("\nTrả lời NGẮN GỌN (2-3 câu), thân thiện.\n");
        return prompt.toString();
    }

    private String buildWebsitePrompt(String userMessage) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Bạn là trợ lý AI của FFilm.\n\n");
        prompt.append("THÔNG TIN VỀ FFILM:\n");
        prompt.append(formatContext(websiteContext));
        prompt.append("\nNgười dùng hỏi: \"").append(userMessage).append("\"\n");
        prompt.append("Trả lời NGẮN GỌN, CHÍNH XÁC.\n");
        return prompt.toString();
    }

    private String formatContext(Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        
        if (context.containsKey("about")) {
            sb.append("Về FFilm: ").append(context.get("about")).append("\n\n");
        }
        
        if (context.containsKey("features")) {
            sb.append("Tính năng:\n");
            List<String> features = (List<String>) context.get("features");
            features.forEach(f -> sb.append("- ").append(f).append("\n"));
        }

        if (context.containsKey("plans")) {
            sb.append("\nGói đăng ký:\n");
            List<Map<String, Object>> plans = (List<Map<String, Object>>) context.get("plans");
            plans.forEach(p -> {
                sb.append("• ").append(p.get("name")).append(": ")
                  .append(p.get("price")).append(" - ")
                  .append(p.get("description")).append("\n");
            });
        }

        if (context.containsKey("policies")) {
            sb.append("\nChính sách:\n");
            Map<String, String> policies = (Map<String, String>) context.get("policies");
            policies.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        }

        return sb.toString();
    }

    private String callGeminiAPI(String prompt) throws Exception {
        try {
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

            String apiUrl = GEMINI_API_URL + geminiApiKey;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

            System.out.println("🌐 Calling Gemini...");
            ResponseEntity<String> resp = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);
            
            String responseBody = resp.getBody();
            System.out.println("📡 Status: " + resp.getStatusCode());
            
            if (responseBody == null || responseBody.isEmpty()) {
                throw new Exception("Gemini trả về body rỗng");
            }

            JSONObject json = new JSONObject(responseBody);
            
            // Check for error
            if (json.has("error")) {
                JSONObject error = json.getJSONObject("error");
                throw new Exception("Gemini error: " + error.optString("message", "Unknown"));
            }

            // Parse response
            if (!json.has("candidates")) {
                throw new Exception("Response thiếu field 'candidates'");
            }

            JSONArray candidates = json.getJSONArray("candidates");
            if (candidates.length() == 0) {
                throw new Exception("Candidates rỗng");
            }

            JSONObject candidate = candidates.getJSONObject(0);
            JSONObject contentObj = candidate.getJSONObject("content");
            JSONArray partsArr = contentObj.getJSONArray("parts");
            String text = partsArr.getJSONObject(0).getString("text");

            if (text.isEmpty()) {
                throw new Exception("Text rỗng");
            }

            System.out.println("✅ Got response: " + text.substring(0, Math.min(50, text.length())));
            return text.trim();

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