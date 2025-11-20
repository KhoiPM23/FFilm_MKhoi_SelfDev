package com.example.project.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

@Service
public class AISearchService {

    // API key đọc từ application.properties hoặc env (gemini.api.key)
    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    // Base URL cho model đã test (gemini-2.5-flash)
    private static final String GEMINI_API_URL_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";

    private final RestTemplate restTemplate;

    public AISearchService() {
        // cấu hình RestTemplate với timeout
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(5000);
        rf.setReadTimeout(30000);
        this.restTemplate = new RestTemplate(rf);
    }

    /**
     * Get AI recommendation with structured response
     * Returns map with keys: success(boolean), answer(String), suggestions(List<String>), optionally error(String)
     */
    public Map<String, Object> getMovieRecommendation(String description) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (!isConfigured()) {
                throw new Exception("Gemini API key is not configured (gemini.api.key).");
            }

            String prompt = buildOptimizedPrompt(description);
            String aiResponse = callGeminiAPI(prompt);

            // Parse AI response
            Map<String, Object> parsed = parseAIResponse(aiResponse);
            result.put("success", true);
            result.put("answer", parsed.get("answer"));
            result.put("suggestions", parsed.get("suggestions"));

        } catch (Exception e) {
            // DEV: in stacktrace để debug; sau khi chạy ổn, bạn có thể giảm logging.
            e.printStackTrace();
            System.err.println("AI recommendation error: " + e.getMessage());

            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("answer", "Xin lỗi, AI đang gặp sự cố. Vui lòng thử lại.");
            result.put("suggestions", Collections.emptyList());
        }

        return result;
    }

    // [THÊM MỚI - DÀNH RIÊNG CHO AI AGENT]
    public Map<String, Object> getMovieRecommendationForAgent(String description) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!isConfigured()) {
                result.put("answer", "AI chưa sẵn sàng.");
                result.put("movies", new ArrayList<>());
                return result;
            }

            String prompt = "Bạn là chuyên gia điện ảnh. Với mô tả: \"" + description + "\"\n" +
                    "Trả về JSON thuần (không markdown):\n" +
                    "{\n" +
                    "  \"answer\": \"Câu trả lời duyên dáng 2-3 câu\",\n" +
                    "  \"movies\": [\n" +
                    "    {\"title\": \"Tên Tiếng Anh\", \"alt_title\": \"Tên Tiếng Việt\"},\n" +
                    "    {\"title\": \"Your Name\", \"alt_title\": \"Tên Cậu Là Gì\"}\n" +
                    "  ]\n" +
                    "}\n" +
                    "Gợi ý 4-6 phim. Nếu không biết tên Việt thì để rỗng alt_title.";

            String aiResponse = callGeminiAPI(prompt);
            String clean = aiResponse.replaceAll("```json|```", "").trim();
            JSONObject json = new JSONObject(clean);
            
            result.put("answer", json.optString("answer", "Dưới đây là gợi ý phim:"));
            
            List<Map<String, String>> movies = new ArrayList<>();
            JSONArray arr = json.optJSONArray("movies");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject m = arr.getJSONObject(i);
                    Map<String, String> movie = new HashMap<>();
                    movie.put("title", m.optString("title", ""));
                    movie.put("alt_title", m.optString("alt_title", ""));
                    movies.add(movie);
                }
            }
            result.put("movies", movies);
            
        } catch (Exception e) {
            result.put("answer", "Xin lỗi, AI gặp lỗi.");
            result.put("movies", new ArrayList<>());
        }
        return result;
    }

    private String buildOptimizedPrompt(String userDescription) {
        return "Bạn là chuyên gia tư vấn phim. Dựa trên mô tả sau, hãy:\n\n" +
                "1. Trả lời NGẮN GỌN (2-3 câu) về phim phù hợp\n" +
                "2. Đề xuất 3-5 TÊN CỤ THỂ: tên phim, diễn viên hoặc đạo diễn để tìm kiếm\n\n" +
                "Mô tả: \"" + userDescription + "\"\n\n" +
                "Format trả lời:\n" +
                "TRẢ LỜI: [Câu trả lời ngắn gọn của bạn]\n" +
                "GỢI Ý: [Tên phim 1], [Tên phim 2], [Diễn viên/Đạo diễn nếu có]\n" + 
                "QUAN TRỌNG: Phần GỢI Ý chỉ chứa tên, KHÔNG chứa ký tự đặc biệt như (), [], :, hoặc năm.\n\n" + 
                "Bắt đầu trả lời:";
    }

    private String callGeminiAPI(String prompt) throws Exception {
        JSONObject requestBody = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        JSONObject part = new JSONObject();
        part.put("text", prompt);
        parts.put(part);
        content.put("parts", parts);
        contents.put(content);
        requestBody.put("contents", contents);

        JSONObject generationConfig = new JSONObject();
        generationConfig.put("temperature", 0.7);
        generationConfig.put("maxOutputTokens", 2048);
        generationConfig.put("topP", 0.9);
        requestBody.put("generationConfig", generationConfig);

        String apiUrl = GEMINI_API_URL_BASE + geminiApiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);

        try {
            ResponseEntity<String> resp = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);
            String responseBody = resp.getBody();

            if (responseBody == null || responseBody.isEmpty()) {
                throw new Exception("Gemini API returned empty response body (HTTP " + resp.getStatusCode() + ")");
            }

            // DEV debug: in raw response để biết chính xác API trả gì (sau khi ổn thì gỡ)
            System.out.println("Gemini raw response (status=" + resp.getStatusCode() + "): " + responseBody);

            JSONObject json = new JSONObject(responseBody);

            // Robust extraction of text from various response shapes (avoid picking up "role":"model")
            String extractedText = null;

            // 1) candidates -> content.parts[*].text OR candidate.text
            if (json.has("candidates")) {
                JSONArray candidates = json.getJSONArray("candidates");
                for (int ci = 0; ci < candidates.length() && extractedText == null; ci++) {
                    JSONObject cand = candidates.getJSONObject(ci);

                    // candidate.text (some responses)
                    if (cand.has("text")) {
                        String t = cand.optString("text", "").trim();
                        if (!t.isEmpty() && !t.equalsIgnoreCase("model")) extractedText = t;
                    }

                    // candidate.content.parts[*].text
                    if (extractedText == null && cand.has("content")) {
                        JSONObject contentObj = cand.getJSONObject("content");

                        if (contentObj.has("parts")) {
                            JSONArray partsArr = contentObj.getJSONArray("parts");
                            for (int pi = 0; pi < partsArr.length() && extractedText == null; pi++) {
                                JSONObject p = partsArr.optJSONObject(pi);
                                if (p != null) {
                                    String t = p.optString("text", "").trim();
                                    if (!t.isEmpty()) extractedText = t;
                                }
                            }
                        }

                        // fallback: content.text (rare)
                        if (extractedText == null && contentObj.has("text")) {
                            String t = contentObj.optString("text", "").trim();
                            if (!t.isEmpty()) extractedText = t;
                        }

                        // fallback: scan content values but IGNORE keys like "role" and values equal "model"
                        if (extractedText == null) {
                            Iterator<String> keys = contentObj.keys();
                            while (keys.hasNext() && extractedText == null) {
                                String k = keys.next();
                                if (k.equalsIgnoreCase("role")) continue; // skip
                                try {
                                    Object val = contentObj.get(k);
                                    if (val instanceof String) {
                                        String t = ((String) val).trim();
                                        if (!t.isEmpty() && !t.equalsIgnoreCase("model")) extractedText = t;
                                    }
                                } catch (Exception ignore) { }
                            }
                        }
                    }
                }
            }

            // 2) output style
            if (extractedText == null && json.has("output")) {
                JSONArray output = json.getJSONArray("output");
                for (int oi = 0; oi < output.length() && extractedText == null; oi++) {
                    JSONObject o0 = output.getJSONObject(oi);

                    if (o0.has("content")) {
                        JSONArray contentArr = o0.getJSONArray("content");
                        for (int ci = 0; ci < contentArr.length() && extractedText == null; ci++) {
                            JSONObject c = contentArr.getJSONObject(ci);
                            if (c.has("text")) {
                                String t = c.optString("text", "").trim();
                                if (!t.isEmpty()) extractedText = t;
                            }
                        }
                    }
                    if (extractedText == null && o0.has("text")) {
                        String t = o0.optString("text", "").trim();
                        if (!t.isEmpty()) extractedText = t;
                    }
                }
            }

            // 3) top-level text
            if (extractedText == null && json.has("text")) {
                String t = json.optString("text", "").trim();
                if (!t.isEmpty()) extractedText = t;
            }

            // 4) If still null, inspect finishReason to give clearer message
            if (extractedText == null) {
                if (json.has("candidates")) {
                    JSONArray candidates = json.getJSONArray("candidates");
                    if (candidates.length() > 0) {
                        JSONObject first = candidates.getJSONObject(0);
                        String finishReason = first.optString("finishReason", "");
                        if (!finishReason.isEmpty()) {
                            throw new Exception("Gemini finished early: " + finishReason + "; raw=" + responseBody);
                        }
                    }
                }
                throw new Exception("Cannot parse Gemini response structure; raw=" + responseBody);
            }

            // Return the extracted text
            return extractedText;



        } catch (HttpClientErrorException e) {
            System.err.println("Gemini HTTP error: status=" + e.getStatusCode());
            System.err.println("Gemini response body: " + e.getResponseBodyAsString());
            throw new Exception("Gemini API returned HTTP error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
        } catch (RestClientException e) {
            e.printStackTrace();
            throw new Exception("Network error calling Gemini API: " + e.getMessage());
        }
    }

    private Map<String, Object> parseAIResponse(String aiText) {
        Map<String, Object> result = new HashMap<>();
        if (aiText == null) aiText = "";

        try {
            // Normalize: replace fancy punctuation, collapse multiple spaces
            String normalized = aiText.replaceAll("\\r", "").trim();

            System.out.println("AI returned text for parsing: " + normalized);

            String answer = "";
            String suggestionsText = "";

            // Prefer explicit labels (TRẢ LỜI / GỢI Ý) case-insensitive
            String upper = normalized.toUpperCase(Locale.ROOT);
            if (upper.contains("TRẢ LỜI:") || upper.contains("GỢI Ý:") || upper.contains("GỢI Ý")) {
                // Try splitting on GỢI Ý (any case)
                String[] parts = normalized.split("(?i)GỢI\\s*Ý[:\\-]?");
                if (parts.length > 0) {
                    answer = parts[0].replaceAll("(?i)TRẢ\\s*LỜI[:\\-]?", "").trim();
                    suggestionsText = parts.length > 1 ? parts[1].trim() : "";
                }
            } else if (upper.contains("GỢI Ý") || upper.contains("Gợi ý") || normalized.contains("SUGGEST") || normalized.contains("GỢI")) {
                // fallback: look for common separators and keywords
                // try to find "Gợi ý" position
                int idx = normalized.toLowerCase().indexOf("gợi ý");
                if (idx >= 0) {
                    answer = normalized.substring(0, idx).replaceAll("(?i)TRẢ\\s*LỜI[:\\-]?", "").trim();
                    suggestionsText = normalized.substring(idx).replaceFirst("(?i)gợi\\s*ý[:\\-]?", "").trim();
                } else {
                    // last-resort: first line is answer, rest is suggestions
                    String[] lines = normalized.split("\\n");
                    answer = lines.length > 0 ? lines[0].trim() : normalized;
                    suggestionsText = lines.length > 1 ? String.join(" ", Arrays.copyOfRange(lines, 1, lines.length)).trim() : "";
                }
            } else {
                // Default fallback: first line is answer, rest are suggestions
                String[] lines = normalized.split("\\n");
                answer = lines.length > 0 ? lines[0].trim() : normalized;
                suggestionsText = lines.length > 1 ? String.join(" ", Arrays.copyOfRange(lines, 1, lines.length)).trim() : "";
            }

            // Final sanitization
            answer = answer.replaceAll("^[:\\-\\s]+", "").replace("**", "").trim();
            if (answer.isEmpty()) {
                answer = normalized.length() > 200 ? normalized.substring(0, 200) : normalized;
            }

            List<String> suggestions = extractSuggestions(suggestionsText);

            // If suggestions empty, try to extract named entities from the full aiText as a fallback
            if (suggestions.isEmpty()) {
                suggestions = extractEntitiesFallback(normalized);
            }

            result.put("answer", answer);
            result.put("suggestions", suggestions);

        } catch (Exception e) {
            e.printStackTrace();
            result.put("answer", aiText == null ? "" : (aiText.length() > 200 ? aiText.substring(0, 200) : aiText));
            result.put("suggestions", Collections.emptyList());
        }

        return result;
    }

    // [THÊM MỚI] Hàm này dành riêng cho Chatbot (Trả về JSON cấu trúc chuẩn)
    public Map<String, Object> getStructuredRecommendation(String description) {
        Map<String, Object> result = new HashMap<>();
        if (!isConfigured()) return result;

        try {
            // Prompt ép kiểu JSON để lấy tên gốc và tên việt
            String prompt = "Bạn là chuyên gia điện ảnh. Với mô tả: \"" + description + "\"\n" +
                    "Hãy gợi ý 5 bộ phim phù hợp nhất.\n" +
                    "BẮT BUỘC trả về JSON thuần túy (không markdown, không giải thích thêm) theo định dạng:\n" +
                    "{ \"answer\": \"Lời dẫn ngắn gọn 1-2 câu...\", \"movies\": [\"Tên phim 1 (Tên gốc)\", \"Tên tiếng Việt 1\", \"Tên phim 2\"] }";

            String responseText = callGeminiAPI(prompt);
            
            // Clean JSON string
            String cleanJson = responseText.replaceAll("```json", "").replaceAll("```", "").trim();
            JSONObject json = new JSONObject(cleanJson);
            
            result.put("success", true);
            result.put("answer", json.optString("answer", "Đây là các phim phù hợp:"));
            
            JSONArray moviesArr = json.optJSONArray("movies");
            List<String> suggestions = new ArrayList<>();
            if (moviesArr != null) {
                for (int i = 0; i < moviesArr.length(); i++) {
                    suggestions.add(moviesArr.getString(i));
                }
            }
            result.put("suggestions", suggestions);

        } catch (Exception e) {
            System.err.println("AI Chat Suggestion Error: " + e.getMessage());
            result.put("suggestions", new ArrayList<>());
        }
        return result;
    }

    // Simple fallback: try to pick capitalized phrases / words as possible movie names (very basic)
    private List<String> extractEntitiesFallback(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        // Look for sequences of words starting with uppercase or words with digits
        // This is heuristic; you can replace with NER later
        String[] tokens = text.split("[,\\.\\n;\\-\\(\\)]");
        for (String tok : tokens) {
            tok = tok.trim();
            if (tok.length() < 3) continue;
            // skip if looks like "role: model" or other metadata
            if (tok.toLowerCase().contains("role") || tok.toLowerCase().contains("model")) continue;
            // accept if contains uppercase letter (simple heuristic)
            if (tok.matches(".*[A-ZÀ-Ỳ].*") || tok.matches(".*\\d.*")) {
                out.add(tok);
            } else {
                // also accept if looks like multiple words (possible movie title)
                if (tok.split("\\s+").length >= 2 && tok.length() <= 60) out.add(tok);
            }
        }
        return out.stream().distinct().limit(6).toList();
    }



    private List<String> extractSuggestions(String text) {
        if (text == null) return Collections.emptyList();

        List<String> suggestions = new ArrayList<>();
        // [FIX VĐ 4] Bổ sung regex để loại bỏ nhiều ký tự đặc biệt hơn
        text = text.replaceAll("(?i)(phim|tên|diễn viên|đạo diễn|gợi ý|suggestion|movie)", "");
        String[] tokens = text.split("[,;\\n]");

        for (String token : tokens) {
            String cleaned = token.trim()
                    .replaceAll("^[\\-\\*•\\d\\.]+\\s*", "") // Xóa dấu gạch đầu dòng
                    .replaceAll("[\"'`():\\[\\]]", ""); // [FIX VĐ 4] Xóa () [] : " ' `

            if (!cleaned.isEmpty() && cleaned.length() > 2) {
                suggestions.add(cleaned);
            }
        }

        return suggestions.stream().distinct().limit(6).toList();
    }

    public boolean isConfigured() {
        return geminiApiKey != null && !geminiApiKey.trim().isEmpty();
    }
}
