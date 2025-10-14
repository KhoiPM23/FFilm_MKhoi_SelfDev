// package com.example.project.controller;

// import org.springframework.stereotype.Controller;
// import org.springframework.ui.Model;
// import org.springframework.web.bind.annotation.GetMapping;
// import org.springframework.web.bind.annotation.PathVariable;
// import org.springframework.web.client.RestTemplate;
// import org.json.JSONArray;
// import org.json.JSONObject;

// import java.util.ArrayList;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;

// @Controller
// public class MovieDetailController {

//     private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
//     private final String BASE_URL = "https://api.themoviedb.org/3";
//     private final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p";

//     @GetMapping("/movie/detail/{id}")
//     public String movieDetail(@PathVariable String id, Model model) {
//         try {
//             RestTemplate restTemplate = new RestTemplate();
            
//             // Fetch movie details
//             String detailUrl = BASE_URL + "/movie/" + id + "?api_key=" + API_KEY + 
//                               "&language=vi-VN&append_to_response=credits,similar,recommendations";
            
//             String response = restTemplate.getForObject(detailUrl, String.class);
            
//             if (response == null || response.isEmpty()) {
//                 return "error";
//             }
            
//             JSONObject movie = new JSONObject(response);
            
//             Map<String, Object> movieData = new HashMap<>();
//             movieData.put("id", id);
//             movieData.put("title", movie.optString("title", "Unknown"));
//             movieData.put("overview", movie.optString("overview", ""));
//             movieData.put("backdrop", IMAGE_BASE_URL + "/original" + movie.optString("backdrop_path", ""));
//             movieData.put("poster", IMAGE_BASE_URL + "/w500" + movie.optString("poster_path", ""));
//             movieData.put("rating", String.format("%.1f", movie.optDouble("vote_average", 0.0)));
//             movieData.put("releaseDate", movie.optString("release_date", ""));
//             movieData.put("runtime", movie.optInt("runtime", 0));
            
//             // Get genres
//             JSONArray genres = movie.optJSONArray("genres");
//             List<String> genreList = new ArrayList<>();
//             if (genres != null) {
//                 for (int i = 0; i < genres.length(); i++) {
//                     genreList.add(genres.getJSONObject(i).optString("name"));
//                 }
//             }
//             movieData.put("genres", genreList);
            
//             // Get director
//             JSONObject credits = movie.optJSONObject("credits");
//             if (credits != null) {
//                 JSONArray crew = credits.optJSONArray("crew");
//                 if (crew != null) {
//                     for (int i = 0; i < crew.length(); i++) {
//                         JSONObject person = crew.getJSONObject(i);
//                         if ("Director".equals(person.optString("job"))) {
//                             movieData.put("director", person.optString("name"));
//                             break;
//                         }
//                     }
//                 }
//             }
            
//             // Get production countries
//             JSONArray countries = movie.optJSONArray("production_countries");
//             if (countries != null && countries.length() > 0) {
//                 movieData.put("country", countries.getJSONObject(0).optString("name"));
//             }
            
//             // Get languages
//             JSONArray languages = movie.optJSONArray("spoken_languages");
//             if (languages != null && languages.length() > 0) {
//                 movieData.put("language", languages.getJSONObject(0).optString("name"));
//             }
            
//             model.addAttribute("movie", movieData);
            
//             return "movie-detail";
            
//         } catch (Exception e) {
//             System.err.println("Error loading movie detail: " + e.getMessage());
//             e.printStackTrace();
//             return "error";
//         }
//     }
// }