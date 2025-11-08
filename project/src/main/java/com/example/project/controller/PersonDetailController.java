package com.example.project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.json.JSONObject;
import org.json.JSONArray;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

@Controller
public class PersonDetailController {

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";
    private final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p";

    @Autowired(required = false)
    private RestTemplate restTemplate;

    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            restTemplate = new RestTemplate();
        }
        return restTemplate;
    }

    /**
     * Hỗ trợ:
     *  - /person/detail/{id}  (path variable, ưu tiên)
     *  - /person/detail?id=123 (query param, fallback)
     */
    @GetMapping({"/person/detail/{pid}", "/person/detail"})
    public String personDetail(
            @PathVariable(required = false, name = "pid") String pid,
            @RequestParam(required = false, name = "id") String idQuery,
            Model model
    ) {
        String finalId = (pid != null && !pid.isEmpty()) ? pid : idQuery;
        RestTemplate rest = getRestTemplate();

        try {
            if (finalId == null || finalId.isEmpty()) {
                model.addAttribute("error", "Person ID không hợp lệ");
                model.addAttribute("errorDetails", "Vui lòng cung cấp ID diễn viên hợp lệ");
                return "error";
            }

            System.out.println("🔍 Loading person detail for ID: " + finalId);

            String personUrl = BASE_URL + "/person/" + finalId + "?api_key=" + API_KEY + "&language=vi-VN";
            String creditsUrl = BASE_URL + "/person/" + finalId + "/movie_credits?api_key=" + API_KEY + "&language=vi-VN";

            // Retry fetching person details
            String personResp = null;
            int retries = 3;
            int delay = 800;
            for (int i = 0; i < retries; i++) {
                try {
                    personResp = rest.getForObject(personUrl, String.class);
                    if (personResp != null && !personResp.isEmpty()) break;
                } catch (Exception ex) {
                    System.err.println("⚠️ Person attempt " + (i+1) + " failed: " + ex.getMessage());
                    if (i < retries - 1) {
                        try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        delay *= 2;
                    }
                }
            }

            // Fetch credits (small retry)
            String creditsResp = null;
            for (int i = 0; i < 2; i++) {
                try {
                    creditsResp = rest.getForObject(creditsUrl, String.class);
                    if (creditsResp != null && !creditsResp.isEmpty()) break;
                } catch (Exception ex) {
                    if (i == 0) {
                        try { Thread.sleep(400); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    }
                }
            }

            if (personResp == null || personResp.isEmpty()) {
                System.err.println("❌ No person response, using client-side fallback");
                return createClientSidePersonFallback(finalId, model);
            }

            JSONObject personJson = new JSONObject(personResp);

            Map<String, Object> personData = new HashMap<>();
            personData.put("id", finalId);
            personData.put("name", personJson.optString("name", "Unknown"));
            personData.put("biography", personJson.optString("biography", "Chưa có tiểu sử"));
            String profilePath = personJson.optString("profile_path", "");
            personData.put("avatar", !profilePath.isEmpty() ? IMAGE_BASE_URL + "/w500" + profilePath : "/images/placeholder-person.jpg");
            personData.put("birthday", personJson.optString("birthday", ""));
            personData.put("place_of_birth", personJson.optString("place_of_birth", "—"));
            personData.put("known_for_department", personJson.optString("known_for_department", "—"));
            personData.put("popularity", personJson.has("popularity") ? personJson.optDouble("popularity", 0.0) : 0.0);

            List<Map<String, Object>> movies = new ArrayList<>();
            if (creditsResp != null && !creditsResp.isEmpty()) {
                JSONObject creditsJson = new JSONObject(creditsResp);
                JSONArray castArray = creditsJson.optJSONArray("cast");
                JSONArray crewArray = creditsJson.optJSONArray("crew");

                if (castArray != null) {
                    for (int i = 0; i < castArray.length(); i++) {
                        JSONObject mv = castArray.getJSONObject(i);
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", mv.optInt("id"));
                        m.put("title", mv.optString("title", mv.optString("original_title", "—")));
                        m.put("poster_path", mv.optString("poster_path", ""));
                        m.put("character", mv.optString("character", ""));
                        m.put("popularity", mv.optDouble("popularity", 0.0));
                        movies.add(m);
                    }
                }

                if (crewArray != null) {
                    for (int i = 0; i < crewArray.length(); i++) {
                        JSONObject mv = crewArray.getJSONObject(i);
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", mv.optInt("id"));
                        m.put("title", mv.optString("title", mv.optString("original_title", "—")));
                        m.put("poster_path", mv.optString("poster_path", ""));
                        m.put("job", mv.optString("job", ""));
                        m.put("popularity", mv.optDouble("popularity", 0.0));
                        movies.add(m);
                    }
                }
            }

            movies.sort((a, b) -> Double.compare((double) b.getOrDefault("popularity", 0.0), (double) a.getOrDefault("popularity", 0.0)));
            if (movies.size() > 24) movies = movies.subList(0, 24);

            personData.put("movies", movies);

            model.addAttribute("person", personData);
            model.addAttribute("personId", finalId);

            System.out.println("✅ Person detail loaded successfully: " + personData.get("name"));
            return "person/person-detail";

        } catch (Exception e) {
            System.err.println("❌ Error loading person detail: " + e.getMessage());
            e.printStackTrace();
            if (finalId != null && !finalId.isEmpty()) {
                return createClientSidePersonFallback(finalId, model);
            }
            model.addAttribute("error", "Lỗi khi tải thông tin diễn viên");
            model.addAttribute("errorDetails", e.getMessage());
            return "error";
        }
    }

    private String createClientSidePersonFallback(String personId, Model model) {
        System.out.println("⚠️ Using client-side fallback for person ID: " + personId);

        Map<String, Object> personData = new HashMap<>();
        personData.put("id", personId);
        personData.put("name", "Đang tải...");
        personData.put("biography", "Đang tải thông tin...");
        personData.put("avatar", "/images/placeholder-person.jpg");
        personData.put("birthday", "");
        personData.put("place_of_birth", "—");
        personData.put("known_for_department", "—");
        personData.put("popularity", 0.0);
        personData.put("movies", new ArrayList<>());

        model.addAttribute("person", personData);
        model.addAttribute("personId", personId);
        model.addAttribute("clientSideLoad", true);

        return "person/person-detail";
    }
}
