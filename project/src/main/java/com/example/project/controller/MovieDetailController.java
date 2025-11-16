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
     * [ĐÃ SỬA THEO KẾ HOẠCH BƯỚC 1]
     * Thay đổi logic để nhận {id} là movieID (DB PK) thay vì tmdbId.
     */
    @GetMapping({"/movie/detail/{id}", "/movie/detail"})
    public String movieDetail(
            @PathVariable(required = false) String id,
            @RequestParam(required = false) String movieId, // Giữ để tương thích link cũ
            Model model
    ) {
        String finalIdStr = (id != null && !id.isEmpty()) ? id : movieId;
        if (finalIdStr == null || finalIdStr.isEmpty()) return "redirect:/";

        try {
            // THAY ĐỔI: Parse {id} thành movieID (Database Primary Key)
            int movieID = Integer.parseInt(finalIdStr);
            
            // THAY ĐỔI: Gọi hàm service mới
            Movie movie = movieService.getMovieByIdOrSync(movieID); // EAGER theo movieID

            if (movie != null) {
                Map<String, Object> movieMap = movieService.convertToMap(movie);
                
                // [SỬA] Chuyển đổi ngôn ngữ
                String langCode = (String) movieMap.get("language"); 
                movieMap.put("language", getLanguageName(langCode)); 
                
                // Lấy tmdbId (có thể null nếu là phim tự tạo)
                Integer tmdbId = movie.getTmdbId();

                // Các API call bên ngoài (TMDB) VẪN PHẢI dùng tmdbId
                if (tmdbId != null) {
                    String trailerKey = movieService.findBestTrailerKey(movieID);
                    String logoPath = movieService.findBestLogoPath(movieID);
                    
                    movieMap.put("trailerKey", trailerKey);
                    movieMap.put("logoPath", logoPath);

                    // Tải các mục phụ (Đã sửa lỗi G46)
                    model.addAttribute("trailers", movieService.findTrailers(tmdbId, 3)); 
                    model.addAttribute("castList", loadCast(String.valueOf(tmdbId))); // Sửa: Dùng tmdbId
                    
                    // [GIẢI PHÁP 2] Xóa 3 carousel nặng, chuyển sang JS tải bất đồng bộ
                    model.addAttribute("trendingMovies", new ArrayList<>()); // Trả list rỗng
                    model.addAttribute("similarMovies", new ArrayList<>()); // Trả list rỗng
                    model.addAttribute("recommendedMovies", new ArrayList<>()); // Trả list rỗng
                    model.addAttribute("recommendTitle", "Có Thể Bạn Thích"); // Giữ lại title
                    
                } else {
                    // Xử lý cho phim tự tạo (không có tmdbId)
                    movieMap.put("trailerKey", null);
                    movieMap.put("logoPath", null);
                    model.addAttribute("trailers", new ArrayList<>());
                    model.addAttribute("castList", new ArrayList<>());
                    model.addAttribute("trendingMovies", new ArrayList<>()); // Trả list rỗng
                    model.addAttribute("similarMovies", new ArrayList<>()); // Trả list rỗng
                    model.addAttribute("recommendedMovies", new ArrayList<>()); // Trả list rỗng
                    model.addAttribute("recommendTitle", "Phim Khác");
                }

                model.addAttribute("movie", movieMap);
                model.addAttribute("movieId", String.valueOf(movieID)); // Sửa: Truyền movieID
                
                // [GIẢI PHÁP 2] Thêm tmdbId vào model để JS sử dụng
                if (tmdbId != null) {
                    model.addAttribute("tmdbId", String.valueOf(tmdbId)); 
                }
                
                model.addAttribute("clientSideLoad", false); // Thêm cờ để JS biết là client-side load

                return "movie/movie-detail";
            } else {
                // Phim không tồn tại trong DB với movieID này
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
    
    @GetMapping("/movie/player/{id}")
    public String moviePlayer(@PathVariable String id, Model model) {
        // ... (Giữ nguyên)
        if (id == null || id.isEmpty()) return "redirect:/";
        model.addAttribute("movieId", id);
        return "player";
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