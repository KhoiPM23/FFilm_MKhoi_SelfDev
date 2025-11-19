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

    //---- 1. CẤU HÌNH & REPOSITORY ----

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";

    @Autowired
    private MovieService movieService; 

    @Autowired
    private RestTemplate restTemplate; 

    //---- 2. MAIN DETAIL LOGIC ----

    // Hiển thị chi tiết diễn viên/đạo diễn (Chấp nhận PK hoặc tmdbId)
    @GetMapping({"/person/detail/{id}", "/person/detail"})
    public String personDetail(
            @PathVariable(required = false, name = "id") String id, // DB PK
            @RequestParam(required = false, name = "id") String idQuery, // TMDB ID
            Model model
    ) {
        String finalIdStr = (id != null && !id.isEmpty()) ? id : idQuery;
        if (finalIdStr == null || finalIdStr.isEmpty()) return "redirect:/";

        Person person = null;
        String tmdbIdStr = null;

        try {
            int numericId = Integer.parseInt(finalIdStr);
            
            //----- Kịch bản 1: Dùng /person/detail/{personID} (PK)
            if (id != null && !id.isEmpty()) {
                System.out.println("Finding person by DB PK: " + numericId);
                person = movieService.getPersonByIdOrSync(numericId); // EAGER sync theo PK
                if (person != null) tmdbIdStr = String.valueOf(person.getTmdbId());
            } else {
            //----- Kịch bản 2: Dùng /person/detail?id={tmdbId}
                System.out.println("Finding person by TMDB ID: " + numericId);
                person = movieService.getPersonOrSync(numericId); // EAGER sync theo tmdbId
                tmdbIdStr = finalIdStr;
            }
            
            if (person == null || tmdbIdStr == null) {
                // Nếu Person không tồn tại trong DB và TMDB ID không hợp lệ
                return createClientSidePersonFallback(finalIdStr, model);
            }

            //----- Bước 1: Gọi API Credits
            String creditsUrl = BASE_URL + "/person/" + tmdbIdStr + "/movie_credits?api_key=" + API_KEY + "&language=vi-VN";
            String creditsResp = restTemplate.getForObject(creditsUrl, String.class);
            
            List<Map<String, Object>> moviesMapList = new ArrayList<>();
            Map<Integer, JSONObject> allMoviesJson = new HashMap<>();

            if (creditsResp != null) {
                JSONObject creditsJson = new JSONObject(creditsResp);
                
                //----- Bước 2: Parse Cast (Ưu tiên Cast hơn Crew nếu trùng)
                JSONArray castArray = creditsJson.optJSONArray("cast");
                if (castArray != null) {
                    for (int i = 0; i < castArray.length(); i++) {
                        JSONObject item = castArray.getJSONObject(i);
                        item.put("role", item.optString("character", "")); 
                        allMoviesJson.put(item.optInt("id"), item);
                    }
                }
                
                //----- Bước 3: Parse Crew (Director/Writer)
                JSONArray crewArray = creditsJson.optJSONArray("crew");
                if (crewArray != null) {
                    for (int i = 0; i < crewArray.length(); i++) {
                        JSONObject item = crewArray.getJSONObject(i);
                        // Chỉ thêm nếu phim chưa có trong danh sách (vì đã thêm Cast)
                        if (!allMoviesJson.containsKey(item.optInt("id"))) {
                            item.put("role", item.optString("job", "")); 
                            allMoviesJson.put(item.optInt("id"), item);
                        }
                    }
                }
                
                //----- Bước 4: Đồng bộ và Convert
                for (JSONObject item : allMoviesJson.values()) {
                    int movieTmdbId = item.optInt("id"); 
                    if (movieTmdbId <= 0) continue;

                    // Sync LAZY (chỉ tạo bản cụt nếu chưa có)
                    Movie movie = movieService.syncMovieFromList(item);

                    if (movie != null) {
                        Map<String, Object> movieMap = movieService.convertToMap(movie);
                        movieMap.put("role_info", item.optString("role", "")); 
                        movieMap.put("popularity", item.optDouble("popularity", 0.0)); 
                        moviesMapList.add(movieMap);
                    }
                }
            }
            
            //----- Bước 5: Sắp xếp theo độ nổi tiếng (Popularity)
            moviesMapList.sort((a, b) -> Double.compare(
                (Double)b.getOrDefault("popularity", 0.0), 
                (Double)a.getOrDefault("popularity", 0.0)
            ));

            //----- Bước 6: Gán Model
            model.addAttribute("person", movieService.convertToMap(person));
            model.addAttribute("personId", String.valueOf(person.getPersonID()));
            model.addAttribute("movies", moviesMapList); 
            model.addAttribute("clientSideLoad", false);

            return "person/person-detail";

        } catch (Exception e) {
            e.printStackTrace();
            return createClientSidePersonFallback(finalIdStr, model);
        }
    }

    //---- 3. HELPER FUNCTIONS ----

    // Helper: Fallback khi API lỗi (để client-side JS tự load)
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