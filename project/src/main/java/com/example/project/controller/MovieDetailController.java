package com.example.project.controller;

import com.example.project.model.Movie;
import com.example.project.model.Person;
import com.example.project.service.MovieService;
import com.example.project.dto.UserSessionDto; // [THÊM] Import UserSessionDto
import com.example.project.repository.FavoriteRepository; // [THÊM] Import Repo

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class MovieDetailController {

    // ---- 1. CẤU HÌNH & REPOSITORY ----

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";

    @Autowired
    private MovieService movieService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private FavoriteRepository favoriteRepository;

    // Bảng Map Ngôn ngữ (Dùng cho hiển thị trên UI)
    private static final Map<String, String> LANGUAGE_MAP = createLanguageMap();

    // ----- Helper: Tạo Map Ngôn ngữ tĩnh
    private static Map<String, String> createLanguageMap() {
        Map<String, String> map = new HashMap<>();
        // Châu Á
        map.put("vi", "Tiếng Việt");
        map.put("zh", "Tiếng Trung (Quan thoại)");
        map.put("ja", "Tiếng Nhật");
        map.put("ko", "Tiếng Hàn");
        map.put("hi", "Tiếng Hindi");
        map.put("th", "Tiếng Thái");
        map.put("ms", "Tiếng Mã Lai");
        map.put("id", "Tiếng Indonesia");
        map.put("tl", "Tiếng Tagalog (Philippines)");
        map.put("ar", "Tiếng Ả Rập");
        map.put("he", "Tiếng Do Thái");
        map.put("tr", "Tiếng Thổ Nhĩ Kỳ");
        map.put("fa", "Tiếng Ba Tư (Farsi)");
        map.put("ur", "Tiếng Urdu");
        map.put("bn", "Tiếng Bengali");
        map.put("ta", "Tiếng Tamil");
        map.put("te", "Tiếng Telugu");
        map.put("kn", "Tiếng Kannada");
        map.put("ml", "Tiếng Malayalam");
        map.put("pa", "Tiếng Punjab");
        map.put("my", "Tiếng Miến Điện");
        map.put("km", "Tiếng Khmer");
        // Châu Âu
        map.put("en", "Tiếng Anh");
        map.put("fr", "Tiếng Pháp");
        map.put("es", "Tiếng Tây Ban Nha");
        map.put("de", "Tiếng Đức");
        map.put("it", "Tiếng Ý");
        map.put("pt", "Tiếng Bồ Đào Nha");
        map.put("ru", "Tiếng Nga");
        map.put("nl", "Tiếng Hà Lan");
        map.put("pl", "Tiếng Ba Lan");
        map.put("sv", "Tiếng Thụy Điển");
        map.put("da", "Tiếng Đan Mạch");
        map.put("no", "Tiếng Na Uy");
        map.put("fi", "Tiếng Phần Lan");
        map.put("el", "Tiếng Hy Lạp");
        map.put("cs", "Tiếng Séc");
        map.put("hu", "Tiếng Hungary");
        map.put("ro", "Tiếng Romania");
        map.put("uk", "Tiếng Ukraina");
        map.put("bg", "Tiếng Bulgaria");
        map.put("sr", "Tiếng Serbia");
        map.put("hr", "Tiếng Croatia");
        map.put("sk", "Tiếng Slovak");
        map.put("sl", "Tiếng Slovenia");
        map.put("et", "Tiếng Estonia");
        map.put("lv", "Tiếng Latvia");
        map.put("lt", "Tiếng Litva");
        map.put("is", "Tiếng Iceland");
        // Khác
        map.put("qu", "Tiếng Quechua");
        map.put("af", "Tiếng Afrikaans");
        map.put("sw", "Tiếng Swahili");
        map.put("zu", "Tiếng Zulu");
        map.put("xh", "Tiếng Xhosa");
        map.put("am", "Tiếng Amharic");
        map.put("yo", "Tiếng Yoruba");
        map.put("ha", "Tiếng Hausa");
        map.put("ig", "Tiếng Igbo");
        map.put("mi", "Tiếng Māori");
        map.put("sm", "Tiếng Samoa");
        map.put("la", "Tiếng Latin");
        map.put("eo", "Tiếng Esperanto");
        // Mã đặc biệt
        map.put("xx", "Không có ngôn ngữ");
        map.put("cn", "Tiếng Quảng Đông");
        return map;
    }

    @GetMapping({ "/movie/detail/{id}", "/movie/detail" })
    public String movieDetail(
            @PathVariable(required = false) String id,
            @RequestParam(required = false) String movieId,
            Model model) {
        String finalIdStr = (id != null && !id.isEmpty()) ? id : movieId;
        if (finalIdStr == null || finalIdStr.isEmpty())
            return "redirect:/";

        try {
            // ----- Lấy movieID (Database PK) và đồng bộ EAGER
            int movieID = Integer.parseInt(finalIdStr);
            Movie movie = movieService.getMovieByIdOrSync(movieID);

            if (movie != null) {
                Map<String, Object> movieMap = movieService.convertToMap(movie);

                // ----- Xử lý ngôn ngữ hiển thị
                String langCode = (String) movieMap.get("language");
                movieMap.put("language", getLanguageName(langCode));

                Integer tmdbId = movie.getTmdbId();
                if (tmdbId != null) {
                    // ----- Lấy Trailer Key và Logo Path (Gọi Service bằng PK)
                    movieMap.put("trailerKey", movieService.findBestTrailerKey(movieID));
                    movieMap.put("logoPath", movieService.findBestLogoPath(movieID));

                    // ----- Tải Cast (LAZY) và Trailers (chỉ 3 cái đầu)
                    model.addAttribute("castList", loadCast(String.valueOf(tmdbId)));
                    model.addAttribute("trailers", movieService.findTrailers(tmdbId, 3));

                    // ----- Gán tmdbId cho JS (tải bất đồng bộ)
                    model.addAttribute("tmdbId", String.valueOf(tmdbId));

                    // ----- Khởi tạo giá trị gợi ý mặc định
                    model.addAttribute("recommendTitle", "Có Thể Bạn Thích");

                } else {
                    // ----- Xử lý cho phim tự tạo (Custom Movie)
                    movieMap.put("trailerKey", null);
                    movieMap.put("logoPath", null);
                    model.addAttribute("trailers", new ArrayList<>());
                    model.addAttribute("castList", new ArrayList<>());
                    model.addAttribute("recommendTitle", "Phim Khác");
                }

                model.addAttribute("movie", movieMap);
                model.addAttribute("movieId", String.valueOf(movieID));
                model.addAttribute("clientSideLoad", false);

                return "movie/movie-detail";
            } else {
                return createClientSideFallback(finalIdStr, model);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return createClientSideFallback(finalIdStr, model);
        }
    }

    private List<Map<String, Object>> loadCast(String movieId) {
        List<Map<String, Object>> castList = new ArrayList<>();
        try {
            String url = BASE_URL + "/movie/" + movieId + "/credits?api_key=" + API_KEY + "&language=vi-VN";
            String resp = restTemplate.getForObject(url, String.class);
            JSONArray results = new JSONObject(resp).optJSONArray("cast");

            if (results != null) {
                // ----- Giới hạn 14 diễn viên
                for (int i = 0; i < Math.min(results.length(), 14); i++) {
                    JSONObject pJson = results.getJSONObject(i);

                    // Gọi hàm LAZY sync (tạo bản cụt nếu chưa có)
                    Person person = movieService.getPersonPartialOrSync(pJson);

                    if (person != null) {
                        Map<String, Object> personMap = movieService.convertToMap(person);
                        // Lấy vai diễn (character) từ JSON credits
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

    // Helper: Chuyển code (en) sang tên (Tiếng Anh, v.v.)
    private String getLanguageName(String code) {
        if (code == null || code.equals("N/A") || code.equals("—")) {
            return "—";
        }
        // Trả về tên đầy đủ, hoặc trả về code (viết hoa) nếu không tìm thấy
        return LANGUAGE_MAP.getOrDefault(code, code.toUpperCase());
    }

    // Helper: Fallback khi DB không tìm thấy phim (hoặc lỗi nặng)
    private String createClientSideFallback(String movieId, Model model) {
        System.out.println("⚠️ Using client-side fallback for movie ID: " + movieId);

        Map<String, Object> movieData = new HashMap<>();
        movieData.put("id", movieId);
        movieData.put("title", "Đang tải...");

        model.addAttribute("movie", movieData);
        model.addAttribute("movieId", movieId);
        model.addAttribute("clientSideLoad", true);

        // Gán các list rỗng để tránh lỗi Thymeleaf
        model.addAttribute("trailers", new ArrayList<>());
        model.addAttribute("castList", new ArrayList<>());
        model.addAttribute("recommendTitle", "Phim Khác");

        return "movie/movie-detail";
    }
}