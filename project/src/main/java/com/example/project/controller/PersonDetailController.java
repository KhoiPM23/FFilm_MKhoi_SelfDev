package com.example.project.controller;

import com.example.project.model.Movie;
import com.example.project.model.Person;
import com.example.project.service.MovieService; 
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate; 

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class PersonDetailController {

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";

    @Autowired
    private MovieService movieService; 

    @Autowired
    private RestTemplate restTemplate; 

    /**
     * [SỬA ĐỔI - PHẦN 2 & 3]
     * Ưu tiên {id} (PK)
     * Giữ ?id= (tmdbId) làm fallback cho Live Suggestion
     */
    @GetMapping({"/person/detail/{id}", "/person/detail"})
    public String personDetail(
            @PathVariable(required = false, name = "id") String id, // Đây là personID (PK)
            @RequestParam(required = false, name = "id") String idQuery, // Đây là tmdbId
            Model model
    ) {
        // Ưu tiên PathVariable (PK) trước
        String finalIdStr = (id != null && !id.isEmpty()) ? id : idQuery;
        if (finalIdStr == null || finalIdStr.isEmpty()) {
            return "redirect:/";
        }

        Person person = null;
        String tmdbIdStr = null; // Dùng để gọi API TMDB

        try {
            int numericId = Integer.parseInt(finalIdStr);
            
            if (id != null && !id.isEmpty()) {
                // KỊCH BẢN 1: Dùng /person/detail/{personID} (PK)
                System.out.println("Finding person by DB PK: " + numericId);
                person = movieService.getPersonByIdOrSync(numericId); // EAGER theo personID
                if (person != null && person.getTmdbId() != null) {
                    tmdbIdStr = String.valueOf(person.getTmdbId());
                }
            } else {
                // KỊCH BẢN 2: Dùng /person/detail?id={tmdbId} (từ Live Suggestion)
                System.out.println("Finding person by TMDB ID: " + numericId);
                person = movieService.getPersonOrSync(numericId); // EAGER theo tmdbId
                tmdbIdStr = finalIdStr; // tmdbId chính là idQuery
            }
            
            if (person == null) {
                return createClientSidePersonFallback(finalIdStr, model);
            }

            // --- Tải các phim đã tham gia (dùng tmdbIdStr) ---
            List<Map<String, Object>> moviesMapList = new ArrayList<>();
            
            // Chỉ gọi API credits nếu chúng ta có tmdbId
            if (tmdbIdStr != null && !tmdbIdStr.isEmpty()) {
                String creditsUrl = BASE_URL + "/person/" + tmdbIdStr + "/movie_credits?api_key=" + API_KEY + "&language=vi-VN";
                String creditsResp = restTemplate.getForObject(creditsUrl, String.class);
                
                Map<Integer, JSONObject> allMoviesJson = new HashMap<>();

                if (creditsResp != null) {
                    // ... (Logic parse JSON castArray và crewArray giữ nguyên) ...
                    JSONObject creditsJson = new JSONObject(creditsResp);
                    JSONArray castArray = creditsJson.optJSONArray("cast");
                    if (castArray != null) {
                        for (int i = 0; i < castArray.length(); i++) {
                            JSONObject item = castArray.getJSONObject(i);
                            item.put("role", item.optString("character", "")); 
                            allMoviesJson.put(item.optInt("id"), item);
                        }
                    }
                    JSONArray crewArray = creditsJson.optJSONArray("crew");
                    if (crewArray != null) {
                        for (int i = 0; i < crewArray.length(); i++) {
                            JSONObject item = crewArray.getJSONObject(i);
                            if (!allMoviesJson.containsKey(item.optInt("id"))) {
                                item.put("role", item.optString("job", "")); 
                                allMoviesJson.put(item.optInt("id"), item);
                            }
                        }
                    }

                    for (JSONObject item : allMoviesJson.values()) {
                        int movieTmdbId = item.optInt("id"); 
                        if (movieTmdbId <= 0) continue;

                        Movie movie = movieService.syncMovieFromList(item); // LAZY

                        if (movie != null) {
                            Map<String, Object> movieMap = movieService.convertToMap(movie);
                            movieMap.put("role_info", item.optString("role", "")); 
                            movieMap.put("popularity", item.optDouble("popularity", 0.0)); 
                            moviesMapList.add(movieMap);
                        }
                    }
                }
                
                moviesMapList.sort((a, b) -> Double.compare(
                    (Double)b.getOrDefault("popularity", 0.0), 
                    (Double)a.getOrDefault("popularity", 0.0)
                ));
            }
            // Nếu tmdbIdStr == null (person tự tạo), thì moviesMapList sẽ rỗng

            model.addAttribute("person", movieService.convertToMap(person));
            model.addAttribute("personId", String.valueOf(person.getPersonID())); // Luôn trả PK
            model.addAttribute("movies", moviesMapList); 
            model.addAttribute("clientSideLoad", false);

            return "person/person-detail";

        } catch (Exception e) {
            e.printStackTrace();
            return createClientSidePersonFallback(finalIdStr, model);
        }
    }

    /**
     * Fallback khi API lỗi (để client-side JS tự load)
     */
    private String createClientSidePersonFallback(String personId, Model model) {
        System.out.println("⚠️ Using client-side fallback for person ID: " + personId);

        Map<String, Object> personData = new HashMap<>();
        personData.put("id", personId);
        personData.put("name", "Đang tải...");
        personData.put("biography", "Đang tải thông tin...");
        personData.put("avatar", "/images/placeholder-person.jpg");
        personData.put("birthday", "—");
        personData.put("place_of_birth", "—");
        personData.put("known_for_department", "—");
        personData.put("popularity", 0.0);
        
        model.addAttribute("person", personData);
        model.addAttribute("personId", personId);
        model.addAttribute("movies", new ArrayList<>()); // Danh sách phim rỗng
        model.addAttribute("clientSideLoad", true);

        return "person/person-detail";
    }
}