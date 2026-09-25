package com.example.project.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Shared Gemini API transport layer.
 * Owns: API key, endpoint URL, model identity, HTTP invocation, request envelope construction.
 * Does NOT own: prompts, generation parameters, feature-specific response parsing.
 */
@Component
public class GeminiClient {

    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    private final RestTemplate restTemplate;

    public GeminiClient() {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(5000);
        rf.setReadTimeout(30000);
        this.restTemplate = new RestTemplate(rf);
    }

    /**
     * Check if the Gemini API key is configured.
     */
    public boolean isConfigured() {
        return geminiApiKey != null && !geminiApiKey.trim().isEmpty();
    }

    /**
     * Build standard Gemini request envelope with prompt text and optional generation config / safety settings.
     * Callers provide their own generationConfig and safetySettings as JSONObjects/JSONArrays.
     */
    public JSONObject buildRequestBody(String prompt, JSONObject generationConfig, JSONArray safetySettings) {
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

        if (generationConfig != null) {
            body.put("generationConfig", generationConfig);
        }
        if (safetySettings != null) {
            body.put("safetySettings", safetySettings);
        }

        return body;
    }

    /**
     * Send a pre-built request body to Gemini and return the raw JSON response.
     * Throws Exception on HTTP or network errors with user-friendly messages.
     */
    public JSONObject call(JSONObject requestBody) throws Exception {
        if (!isConfigured()) {
            throw new Exception("Gemini API key chưa cấu hình");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);

            ResponseEntity<String> resp = restTemplate.exchange(
                    GEMINI_API_URL + geminiApiKey, HttpMethod.POST, entity, String.class);

            String responseBody = resp.getBody();
            if (responseBody == null || responseBody.isEmpty()) {
                throw new Exception("Gemini API returned empty response body (HTTP " + resp.getStatusCode() + ")");
            }

            return new JSONObject(responseBody);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                throw new Exception("Hệ thống đang bận, vui lòng thử lại sau giây lát.");
            }
            System.err.println("Gemini HTTP error: status=" + e.getStatusCode());
            throw new Exception("Gemini API returned HTTP error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
        } catch (RestClientException e) {
            throw new Exception("Network error calling Gemini API: " + e.getMessage());
        }
    }

    /**
     * Convenience: extract text from standard Gemini response structure.
     * Returns candidates[0].content.parts[0].text or empty string on failure.
     */
    public String extractText(JSONObject geminiResponse) {
        try {
            return geminiResponse.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");
        } catch (Exception e) {
            return "";
        }
    }
}
