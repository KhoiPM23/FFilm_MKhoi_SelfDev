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

    @GetMapping({"/person/detail/{pid}", "/person/detail"})
    public String personDetail(
            @PathVariable(required = false, name = "pid") String pid,
            @RequestParam(required = false, name = "id") String idQuery,
            Model model
    ) {
        String finalIdStr = (pid != null && !pid.isEmpty()) ? pid : idQuery;
        if (finalIdStr == null || finalIdStr.isEmpty()) {
            return "redirect:/";
        }

        try {
            int tmdbId = Integer.parseInt(finalIdStr);
            
            // [G42] HÀM EAGER (ĐÚNG)
            Person person = movieService.getPersonOrSync(tmdbId);
            
            if (person == null) {
                return createClientSidePersonFallback(finalIdStr, model);
            }

            String creditsUrl = BASE_URL + "/person/" + finalIdStr + "/movie_credits?api_key=" + API_KEY + "&language=vi-VN";
            String creditsResp = restTemplate.getForObject(creditsUrl, String.class);
            
            List<Map<String, Object>> moviesMapList = new ArrayList<>();
            Map<Integer, JSONObject> allMoviesJson = new HashMap<>();

            if (creditsResp != null) {
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

                    // [G42] SỬA LỖI API STORM:
                    Movie movie = movieService.syncMovieFromList(item); // ĐÚNG (Lazy)

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

            model.addAttribute("person", movieService.convertToMap(person));
            model.addAttribute("personId", finalIdStr);
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