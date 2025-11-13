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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class MovieDetailController {

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";

    // [G46] Bảng Map Ngôn ngữ
    private static final Map<String, String> LANGUAGE_MAP = new HashMap<>();
    static {
        // === CHÂU Á ===
        LANGUAGE_MAP.put("vi", "Tiếng Việt");
        LANGUAGE_MAP.put("zh", "Tiếng Trung (Quan thoại)");
        LANGUAGE_MAP.put("ja", "Tiếng Nhật");
        LANGUAGE_MAP.put("ko", "Tiếng Hàn");
        LANGUAGE_MAP.put("hi", "Tiếng Hindi");
        LANGUAGE_MAP.put("th", "Tiếng Thái");
        LANGUAGE_MAP.put("ms", "Tiếng Mã Lai");
        LANGUAGE_MAP.put("id", "Tiếng Indonesia");
        LANGUAGE_MAP.put("tl", "Tiếng Tagalog (Philippines)");
        LANGUAGE_MAP.put("ar", "Tiếng Ả Rập");
        LANGUAGE_MAP.put("he", "Tiếng Do Thái");
        LANGUAGE_MAP.put("tr", "Tiếng Thổ Nhĩ Kỳ");
        LANGUAGE_MAP.put("fa", "Tiếng Ba Tư (Farsi)");
        LANGUAGE_MAP.put("ur", "Tiếng Urdu");
        LANGUAGE_MAP.put("bn", "Tiếng Bengali");
        LANGUAGE_MAP.put("ta", "Tiếng Tamil");
        LANGUAGE_MAP.put("te", "Tiếng Telugu");
        LANGUAGE_MAP.put("kn", "Tiếng Kannada");
        LANGUAGE_MAP.put("ml", "Tiếng Malayalam");
        LANGUAGE_MAP.put("pa", "Tiếng Punjab");
        LANGUAGE_MAP.put("my", "Tiếng Miến Điện");
        LANGUAGE_MAP.put("km", "Tiếng Khmer");

        // === CHÂU ÂU ===
        LANGUAGE_MAP.put("en", "Tiếng Anh");
        LANGUAGE_MAP.put("fr", "Tiếng Pháp");
        LANGUAGE_MAP.put("es", "Tiếng Tây Ban Nha");
        LANGUAGE_MAP.put("de", "Tiếng Đức");
        LANGUAGE_MAP.put("it", "Tiếng Ý");
        LANGUAGE_MAP.put("pt", "Tiếng Bồ Đào Nha");
        LANGUAGE_MAP.put("ru", "Tiếng Nga");
        LANGUAGE_MAP.put("nl", "Tiếng Hà Lan");
        LANGUAGE_MAP.put("pl", "Tiếng Ba Lan");
        LANGUAGE_MAP.put("sv", "Tiếng Thụy Điển");
        LANGUAGE_MAP.put("da", "Tiếng Đan Mạch");
        LANGUAGE_MAP.put("no", "Tiếng Na Uy");
        LANGUAGE_MAP.put("fi", "Tiếng Phần Lan");
        LANGUAGE_MAP.put("el", "Tiếng Hy Lạp");
        LANGUAGE_MAP.put("cs", "Tiếng Séc");
        LANGUAGE_MAP.put("hu", "Tiếng Hungary");
        LANGUAGE_MAP.put("ro", "Tiếng Romania");
        LANGUAGE_MAP.put("uk", "Tiếng Ukraina");
        LANGUAGE_MAP.put("bg", "Tiếng Bulgaria");
        LANGUAGE_MAP.put("sr", "Tiếng Serbia");
        LANGUAGE_MAP.put("hr", "Tiếng Croatia");
        LANGUAGE_MAP.put("sk", "Tiếng Slovak");
        LANGUAGE_MAP.put("sl", "Tiếng Slovenia");
        LANGUAGE_MAP.put("et", "Tiếng Estonia");
        LANGUAGE_MAP.put("lv", "Tiếng Latvia");
        LANGUAGE_MAP.put("lt", "Tiếng Litva");
        LANGUAGE_MAP.put("is", "Tiếng Iceland");

        // === CHÂU MỸ ===
        // (Đã có en, es, fr, pt)
        LANGUAGE_MAP.put("qu", "Tiếng Quechua"); // Ngôn ngữ bản địa Nam Mỹ

        // === CHÂU PHI ===
        LANGUAGE_MAP.put("af", "Tiếng Afrikaans");
        LANGUAGE_MAP.put("sw", "Tiếng Swahili");
        LANGUAGE_MAP.put("zu", "Tiếng Zulu");
        LANGUAGE_MAP.put("xh", "Tiếng Xhosa");
        LANGUAGE_MAP.put("am", "Tiếng Amharic");
        LANGUAGE_MAP.put("yo", "Tiếng Yoruba");
        LANGUAGE_MAP.put("ha", "Tiếng Hausa");
        LANGUAGE_MAP.put("ig", "Tiếng Igbo");

        // === CHÂU ÚC / ĐẠI DƯƠNG ===
        // (Đã có en)
        LANGUAGE_MAP.put("mi", "Tiếng Māori"); // New Zealand
        LANGUAGE_MAP.put("sm", "Tiếng Samoa");

        // Ngôn ngữ khác
        LANGUAGE_MAP.put("la", "Tiếng Latin");
        LANGUAGE_MAP.put("eo", "Tiếng Esperanto");

        // Mã đặc biệt (ISO 639-1)
        LANGUAGE_MAP.put("xx", "Không có ngôn ngữ");
        LANGUAGE_MAP.put("cn", "Tiếng Quảng Đông"); // Lưu ý: 'zh' là Quan thoại
    }

    @Autowired
    private MovieService movieService;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * [G46] HÀM EAGER (ĐÚNG)
     */
    @GetMapping({ "/movie/detail/{id}", "/movie/detail" })
    public String movieDetail(
            @PathVariable(required = false) String id,
            @RequestParam(required = false) String movieId,
            Model model) {
        String finalIdStr = (id != null && !id.isEmpty()) ? id : movieId;
        if (finalIdStr == null || finalIdStr.isEmpty())
            return "redirect:/";

        try {
            int tmdbId = Integer.parseInt(finalIdStr);

            Movie movie = movieService.getMovieOrSync(tmdbId); // EAGER

            if (movie != null) {
                Map<String, Object> movieMap = movieService.convertToMap(movie);

                // [G46] Chuyển đổi ngôn ngữ
                String langCode = (String) movieMap.get("language"); // Lấy code (vd: "en" hoặc "—")
                movieMap.put("language", getLanguageName(langCode)); // Ghi đè (vd: "Tiếng Anh" hoặc "—")

                String trailerKey = movieService.findBestTrailerKey(tmdbId);
                String logoPath = movieService.findBestLogoPath(tmdbId);

                movieMap.put("trailerKey", trailerKey);
                movieMap.put("logoPath", logoPath);

                model.addAttribute("movie", movieMap);
                model.addAttribute("movieId", finalIdStr);
                model.addAttribute("clientSideLoad", false);

                // Tải các mục phụ (ĐÃ SỬA LỖI G46)
                model.addAttribute("trailers", movieService.findTrailers(tmdbId, 3));
                model.addAttribute("castList", loadCast(finalIdStr)); // (Đã sửa G46)
                model.addAttribute("trendingMovies", loadTrendingSidebar());
                model.addAttribute("similarMovies", loadSimilarMovies(finalIdStr));

                model.addAttribute("recommendTitle", "Có Thể Bạn Thích");
                model.addAttribute("recommendedMovies", loadRecommendedMovies(finalIdStr, tmdbId, model));

                return "movie/movie-detail";
            } else {
                return createClientSideFallback(finalIdStr, model);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return createClientSideFallback(finalIdStr, model);
        }
    }

    // (Hàm createClientSideFallback và moviePlayer giữ nguyên)
    private String createClientSideFallback(String movieId, Model model) {
        // ... (Giữ nguyên)
        System.out.println("⚠️ Using client-side fallback for movie ID: " + movieId);
        Map<String, Object> movieData = new HashMap<>();
        movieData.put("id", movieId);
        movieData.put("title", "Đang tải...");
        model.addAttribute("movie", movieData);
        model.addAttribute("movieId", movieId);
        model.addAttribute("clientSideLoad", true);
        return "movie/movie-detail";
    }


    /**
     * [G46] SỬA LỖI API STORM:
     * Đã chuyển sang gọi getPersonPartialOrSync (Lazy)
     */
    private List<Map<String, Object>> loadCast(String movieId) {
        List<Map<String, Object>> castList = new ArrayList<>();
        try {
            String url = BASE_URL + "/movie/" + movieId + "/credits?api_key=" + API_KEY + "&language=vi-VN";
            String resp = restTemplate.getForObject(url, String.class);
            JSONArray results = new JSONObject(resp).optJSONArray("cast");

            if (results != null) {
                for (int i = 0; i < Math.min(results.length(), 14); i++) {
                    JSONObject pJson = results.getJSONObject(i);

                    // [G46] SỬA LỖI: Gọi hàm LAZY
                    Person person = movieService.getPersonPartialOrSync(pJson);

                    if (person != null) {
                        Map<String, Object> personMap = movieService.convertToMap(person);
                        // [G46] Lấy vai diễn từ JSON (theo yêu cầu của bạn)
                        personMap.put("role", pJson.optString("character"));
                        castList.add(personMap);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi loadCast: " + e.getMessage());
        }
        return castList;
    }

    // (Hàm loadTrendingSidebar, loadSimilarMovies giữ nguyên - G46 đã tối ưu)
    public List<Map<String, Object>> loadTrendingSidebar() {
        String url = BASE_URL + "/trending/movie/week?api_key=" + API_KEY + "&language=vi-VN";
        Map<String, Object> data = movieService.loadAndSyncPaginatedMovies(url, 20);
        return (List<Map<String, Object>>) data.get("movies");
    }

    private List<Map<String, Object>> loadSimilarMovies(String movieId) {
        String url = BASE_URL + "/movie/" + movieId + "/similar?api_key=" + API_KEY + "&language=vi-VN";
        Map<String, Object> data = movieService.loadAndSyncPaginatedMovies(url, 20);
        return (List<Map<String, Object>>) data.get("movies");
    }

    /**
     * [G46] SỬA LỖI API STORM:
     * Bước 1 (Collection): Dùng syncMovieFromList (Lazy)
     */
    private List<Map<String, Object>> loadRecommendedMovies(String movieIdStr, int tmdbId, Model model) {

        Set<Integer> addedMovieIds = new HashSet<>();
        List<Map<String, Object>> finalRecommendations = new ArrayList<>();
        addedMovieIds.add(tmdbId);

        try {
            // BƯỚC 1 (Ưu tiên): Lấy Collection
            String detailUrl = BASE_URL + "/movie/" + tmdbId + "?api_key=" + API_KEY + "&language=vi-VN";
            String detailResp = restTemplate.getForObject(detailUrl, String.class);
            JSONObject movieJson = new JSONObject(detailResp);
            JSONObject collection = movieJson.optJSONObject("belongs_to_collection");

            if (collection != null) {
                int collectionId = collection.optInt("id");
                if (collectionId > 0) {
                    String collectionUrl = BASE_URL + "/collection/" + collectionId + "?api_key=" + API_KEY
                            + "&language=vi-VN";
                    String collectionResp = restTemplate.getForObject(collectionUrl, String.class);
                    JSONObject collectionJson = new JSONObject(collectionResp);
                    JSONArray parts = collectionJson.optJSONArray("parts");

                    if (parts != null && parts.length() > 0) {
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject part = parts.getJSONObject(i);
                            int partTmdbId = part.optInt("id");
                            if (addedMovieIds.contains(partTmdbId))
                                continue;

                            // [G46] SỬA LỖI: Gọi hàm LAZY
                            Movie movie = movieService.syncMovieFromList(part);

                            if (movie != null) {
                                finalRecommendations.add(movieService.convertToMap(movie));
                                addedMovieIds.add(partTmdbId);
                            }
                        }
                        if (!finalRecommendations.isEmpty()) {
                            model.addAttribute("recommendTitle",
                                    "🎬 Từ Bộ Sưu Tập: " + collectionJson.optString("name"));
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi G46 (load collection), tiếp tục: " + e.getMessage());
        }

        // BƯỚC 2: FALLBACK / FILL (Giữ nguyên - Đã tối ưu G46)
        String recommendUrl = BASE_URL + "/movie/" + movieIdStr + "/recommendations?api_key=" + API_KEY
                + "&language=vi-VN";
        Map<String, Object> fallbackData = movieService.loadAndSyncPaginatedMovies(recommendUrl, 20);
        // [G46] SỬA LỖI LẶP (G45)
        List<Map<String, Object>> fallbackMovies = (List<Map<String, Object>>) fallbackData.get("movies");

        for (Map<String, Object> movieMap : fallbackMovies) {
            int fallbackTmdbId = (int) movieMap.get("id");
            if (!addedMovieIds.contains(fallbackTmdbId)) {
                finalRecommendations.add(movieMap);
                addedMovieIds.add(fallbackTmdbId);
            }
        }
        if (model.getAttribute("recommendTitle").equals("Có Thể Bạn Thích")) {
            model.addAttribute("recommendTitle", "✨ Có Thể Bạn Thích");
        }
        return finalRecommendations;
    }

    /**
     * [G46] HÀM HELPER: Chuyển code (en) sang tên (Tiếng Anh)
     */
    private String getLanguageName(String code) {
        if (code == null || code.equals("N/A") || code.equals("—")) {
            return "—";
        }
        // Trả về tên đầy đủ, hoặc trả về code (viết hoa) nếu không tìm thấy
        return LANGUAGE_MAP.getOrDefault(code, code.toUpperCase());
    }




}