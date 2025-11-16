package com.example.project.service;

import com.example.project.dto.MovieRequest;
import com.example.project.model.*;
import com.example.project.repository.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
// <-- Thêm
// <-- Thêm
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream; // <-- Thêm

import org.springframework.data.domain.Page; // <-- Thêm
import org.springframework.data.domain.PageRequest; // <-- Thêm
import org.springframework.data.domain.Sort; // <-- Thêm

@Service
public class MovieService {

    @Autowired private MovieRepository movieRepository;
    @Autowired private GenreRepository genreRepository;
    @Autowired private PersonRepository personRepository;
    @Autowired private CategoryRepository categoryRepository;
    
    @Autowired
    private RestTemplate restTemplate;

    // [THÊM MỚI] Cho phép Controller truy cập
    public MovieRepository getMovieRepository() {
        return movieRepository;
    }
    
    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";
    
    // (Hàm initGenres giữ nguyên)
    @Transactional
    public void initGenres() {
        // ... (Giữ nguyên)
        System.out.println("Kiểm tra và khởi tạo Genres...");
        Map<Integer, String> tmdbGenres = new HashMap<>();
        tmdbGenres.put(28, "Hành động");
        tmdbGenres.put(12, "Phiêu lưu");
        tmdbGenres.put(16, "Hoạt hình");
        tmdbGenres.put(35, "Hài");
        tmdbGenres.put(80, "Hình sự");
        tmdbGenres.put(99, "Tài liệu");
        tmdbGenres.put(18, "Chính kịch");
        tmdbGenres.put(10751, "Gia đình");
        tmdbGenres.put(14, "Giả tưởng");
        tmdbGenres.put(36, "Lịch sử");
        tmdbGenres.put(27, "Kinh dị");
        tmdbGenres.put(10402, "Nhạc");
        tmdbGenres.put(9648, "Bí ẩn");
        tmdbGenres.put(10749, "Lãng mạn");
        tmdbGenres.put(878, "Khoa học viễn tưởng");
        tmdbGenres.put(10770, "Chương trình truyền hình");
        tmdbGenres.put(53, "Gây cấn");
        tmdbGenres.put(10752, "Chiến tranh");
        tmdbGenres.put(37, "Miền Tây");
        
        for (Map.Entry<Integer, String> entry : tmdbGenres.entrySet()) {
            if (genreRepository.findByTmdbGenreId(entry.getKey()).isEmpty()) {
                genreRepository.save(new Genre(entry.getKey(), entry.getValue()));
            }
        }
        System.out.println("✅ Đã khởi tạo Genres.");
    }

    // ===============================================
    // CÁC HÀM ĐỒNG BỘ CỐT LÕI (LOGIC MỚI)
    // ===============================================

    /**
     * [MỚI - FIX LỖI BIÊN DỊCH]
     * Lấy movie theo movieID (DB PK), tự động sync nếu cần
     */
    @Transactional
    public Movie getMovieByIdOrSync(int movieID) {
        // Bước 1: Tìm theo PK
        Optional<Movie> existing = movieRepository.findById(movieID);
        
        if (existing.isEmpty()) {
            System.err.println("⚠️ Movie not found with ID: " + movieID);
            return null;
        }
        
        Movie movie = existing.get();
        
        // Bước 2: Kiểm tra tmdbId
        if (movie.getTmdbId() == null) {
            // Phim tự tạo → Trả luôn
            System.out.println("✅ [Custom Movie] ID: " + movieID);
            return movie;
        }
        
        // Bước 3: Kiểm tra cờ "N/A" (bản 'cụt')
        if ("N/A".equals(movie.getDirector())) {
            System.out.println("♻️ [Movie EAGER] Nâng cấp chi tiết cho movie ID: " + movieID);
            // Gọi hàm fetch API (Eager)
            return fetchAndSaveMovieDetail(movie.getTmdbId(), movie);
        }
        
        // Bước 4: Đã đầy đủ → Trả luôn
        return movie;
    }

    /**
     * [G46] HÀM EAGER (MOVIE): Chỉ dùng cho TRANG CHI TIẾT
     * (Hàm này kích hoạt nâng cấp)
     */
    @Transactional
    public Movie getMovieOrSync(int tmdbId) {
        Optional<Movie> existing = movieRepository.findByTmdbId(tmdbId);
        
        if (existing.isPresent()) {
            Movie movie = existing.get();
            
            // [SỬA LỖI] CHỈ "N/A" MỚI LÀ CỜ. NULL LÀ DỮ LIỆU RỖNG.
            // Dùng "N/A".equals() để tránh lỗi NullPointerException
            if ("N/A".equals(movie.getDirector())) {
                System.out.println("♻️ [Movie EAGER] Nâng cấp chi tiết cho phim ID: " + tmdbId);
                // Gọi API đầy đủ, lấp đầy các trường thiếu
                return fetchAndSaveMovieDetail(tmdbId, movie); 
            } else {
                // Trả về bản ghi (director có thể là "Guillermo..." hoặc NULL/rỗng '')
                return movie; 
            }
        }
        
        // Phim chưa có trong DB
        System.out.println("✳️ [Movie EAGER] Tạo mới chi tiết cho phim ID: " + tmdbId);
        return fetchAndSaveMovieDetail(tmdbId, null); 
    }

    /**
     * [G46] HÀM LAZY (MOVIE): Dùng cho TRANG DANH SÁCH
     * (Hàm này KHÔNG kích hoạt nâng cấp)
     */
    @Transactional
    public Movie syncMovieFromList(JSONObject jsonItem) {
        int tmdbId = jsonItem.optInt("id");
        if (tmdbId <= 0) return null;
        
        // [VẤN ĐỀ 8] LỌC PHIM SPAM/18+
        if (jsonItem.optBoolean("adult", false)) return null; // Lọc 18+
        if (jsonItem.optDouble("vote_average", 0) < 0.1) return null; // Phim ảo
        if (jsonItem.optInt("vote_count", 0) < 5) return null; // Spam
        
        // 1. Check DB trước
        Optional<Movie> existing = movieRepository.findByTmdbId(tmdbId);
        if (existing.isPresent()) {
            // === FIX BUG 1 ===
            // Tìm thấy rồi, trả về ngay. KHÔNG ĐƯỢC NÂNG CẤP HAY GHI ĐÈ
            // Điều này đảm bảo "Minh Khôi" được giữ nguyên
            return existing.get(); 
        }

        // 2. Nếu chưa có, tạo mới bản "cụt" (partial)
        System.out.println("✳️ [Movie LAZY] Tạo mới bản cụt cho ID: " + tmdbId);
        Movie movie = new Movie();
        movie.setTmdbId(tmdbId);
        
        // [SỬA] Các trường cơ bản (Lấy đầy đủ từ Lazy)
        movie.setTitle(jsonItem.optString("title", jsonItem.optString("name", "N/A")));
        movie.setDescription(jsonItem.optString("overview", null)); // (Mô tả)
        movie.setPosterPath(jsonItem.optString("poster_path", null));
        movie.setBackdropPath(jsonItem.optString("backdrop_path", null));
        movie.setRating((float) jsonItem.optDouble("vote_average", 0.0));
        movie.setReleaseDate(parseDate(jsonItem.optString("release_date", jsonItem.optString("first_air_date"))));
        
        // [SỬA] Lấy Duration + Country ngay lập tức (Không còn là cờ)
        movie.setDuration(jsonItem.optInt("runtime", 0)); 
        JSONArray countries = jsonItem.optJSONArray("production_countries");
        if (countries != null && countries.length() > 0) {
            movie.setCountry(countries.getJSONObject(0).optString("name"));
        } else {
             movie.setCountry(null); // Set NULL nếu API (List/Detail) không có
        }

        // [SỬA] CÁC CỜ (FLAG) "N/A" (Chỉ còn 2)
        movie.setDirector("N/A"); // Cờ Nâng Cấp
        movie.setLanguage("N/A"); // Cờ Nâng Cấp
        
        // Các trường SET 0 (không phải cờ, sẽ được Eager lấp đầy)
        movie.setBudget(0L); 
        movie.setRevenue(0L);
        
        // Thể loại (Lấy từ genre_ids nếu có)
        JSONArray genreIdsJson = jsonItem.optJSONArray("genre_ids");
        if (genreIdsJson != null && genreIdsJson.length() > 0) {
            List<Integer> genreIds = new ArrayList<>();
            for (int i = 0; i < genreIdsJson.length(); i++) {
                genreIds.add(genreIdsJson.optInt(i));
            }
            List<Genre> genres = genreRepository.findByTmdbGenreIdIn(genreIds);
            movie.setGenres(genres);
        }
        
        // Các trường nội bộ (isFree, url) sẽ dùng giá trị default
        // movie.setFree(false);
        // movie.setUrl("CHƯA CẬP NHẬT");
        
        return movieRepository.save(movie);
    }

    /**
     * [THÊM MỚI - PHẦN 2]
     * Lấy Person theo personID (DB PK), tự động sync nếu cần
     */
    @Transactional
    public Person getPersonByIdOrSync(int personID) {
        Optional<Person> existing = personRepository.findById(personID);
        
        if (existing.isEmpty()) {
            System.err.println("⚠️ Person not found with ID: " + personID);
            return null;
        }
        
        Person person = existing.get();
        
        // Phim tự tạo (không có tmdbId) -> Trả luôn
        if (person.getTmdbId() == null) {
            System.out.println("✅ [Custom Person] ID: " + personID);
            return person;
        }
        
        // Kiểm tra cờ "N/A"
        if ("N/A".equals(person.getBio())) {
            System.out.println("♻️ [Person EAGER] Nâng cấp chi tiết cho person ID: " + personID);
            return fetchAndSavePersonDetail(person.getTmdbId(), person);
        }
        
        // Đã đầy đủ -> Trả luôn
        return person;
    }

    /**
     * [G46] HÀM EAGER (PERSON): Chỉ dùng cho TRANG CHI TIẾT
     */
    @Transactional
    public Person getPersonOrSync(int tmdbId) {
        Optional<Person> existing = personRepository.findByTmdbId(tmdbId);
        
        if (existing.isPresent()) {
            Person person = existing.get();
            
            // [SỬA LỖI] CHỈ "N/A" MỚI LÀ CỜ. NULL LÀ DỮ LIỆU RỖNG.
            if ("N/A".equals(person.getBio())) {
                System.out.println("♻️ [Person EAGER] Nâng cấp chi tiết cho Person ID: " + tmdbId);
                return fetchAndSavePersonDetail(tmdbId, person); 
            } else {
                return person; // Trả về bản ghi (bio có thể là data hoặc NULL/rỗng)
            }
        }
        
        System.out.println("✳️ [Person EAGER] Tạo mới chi tiết cho Person ID: " + tmdbId);
        return fetchAndSavePersonDetail(tmdbId, null); 
    }
    
    /**
     * [G46] HÀM LAZY (PERSON): Dùng cho TRANG DANH SÁCH
     */
    @Transactional
    public Person getPersonPartialOrSync(JSONObject json) {
        int tmdbId = json.optInt("id");
        if (tmdbId <= 0) return null;
        
        Optional<Person> existing = personRepository.findByTmdbId(tmdbId);
        if (existing.isPresent()) {
            return existing.get(); // Nếu có rồi, trả về ngay.
        }

        System.out.println("✳️ [Person LAZY] Tạo mới bản cụt cho ID: " + tmdbId);
        Person p = new Person();
        p.setTmdbId(tmdbId);
        
        // [SỬA] Lazy load CHỈ lấy Tên và Ảnh
        p.setFullName(json.optString("name"));
        p.setProfilePath(json.optString("profile_path", null));
        
        // [SỬA] CÁC CỜ (FLAG) "N/A"
        p.setKnownForDepartment("N/A");
        p.setBio("N/A"); 
        
        // [SỬA] CÁC CỜ (FLAG) "NULL" (Chờ Eager lấp đầy)
        p.setBirthday(null);
        p.setPlaceOfBirth(null);
        p.setPopularity(null); // <--- ĐÃ SỬA: Biến thành cờ NULL
        
        return personRepository.save(p);
    }

    /**
     * [G46] HÀM LAZY (ĐỌC DB): Dùng cho Banner, Hover Card
     * [SỬA] Nâng cấp "cụt" -> "vừa" (Lấy duration/country) khi hover.
     */
    /**
     * [SỬA LỖI V9] Nâng cấp đầy đủ (poster, rating) nếu bản "cụt" bị lỗi
     */
    /**
     * === FIX BUG 1 ===
     * Sửa hàm getMoviePartial để không ghi đè dữ liệu thủ công
     */
    @Transactional
    public Movie getMoviePartial(int tmdbId) {
        Optional<Movie> existing = movieRepository.findByTmdbId(tmdbId);
        
        if (existing.isPresent()) {
            Movie movie = existing.get();
            
            // KIỂM TRA CỜ: Nếu là bản "cụt" (director="N/A")
            // Hoặc bản ghi thiếu thông tin (duration=0 VÀ director=null)
            boolean isPartial = "N/A".equals(movie.getDirector());
            boolean isMissingData = (movie.getDuration() == 0 && movie.getDirector() == null);

            if (isPartial || isMissingData) {
                
                // Phim này "cụt", gọi API chi tiết 1 LẦN để lấp đầy
                try {
                    System.out.println("♻️ [Movie-Partial] Nâng cấp (cho Hover/Suggestion) ID: " + tmdbId);
                    String url = BASE_URL + "/movie/" + tmdbId + "?api_key=" + API_KEY + "&language=vi-VN&include_adult=false";
                    String resp = restTemplate.getForObject(url, String.class);
                    if (resp == null) return movie; 
                    
                    JSONObject detailJson = new JSONObject(resp);
                    
                    // NÂNG CẤP CÓ CHỌN LỌC (KHÔNG GHI ĐÈ)
                    
                    // Chỉ cập nhật nếu trường là "N/A" (cờ) hoặc null/0/rỗng (thiếu)
                    // KHÔNG CHẠM VÀO TITLE, DESCRIPTION, POSTER, BACKDROP, URL, ISFREE
                    
                     if (movie.getReleaseDate() == null) {
                        movie.setReleaseDate(parseDate(detailJson.optString("release_date")));
                    }
                    if (movie.getRating() == 0.0f) {
                        movie.setRating((float) detailJson.optDouble("vote_average", 0.0));
                    }
                    
                    // Luôn lấp đầy các trường này nếu chúng là cờ/trống
                    if (movie.getDuration() == 0) {
                        movie.setDuration(detailJson.optInt("runtime", 0));
                    }
                    if (movie.getCountry() == null || movie.getCountry().isEmpty()) {
                        JSONArray countries = detailJson.optJSONArray("production_countries");
                        if (countries != null && countries.length() > 0) {
                            movie.setCountry(countries.getJSONObject(0).optString("name"));
                        } else {
                            movie.setCountry(null); 
                        }
                    }
                    if (movie.getGenres() == null || movie.getGenres().isEmpty()) {
                        JSONArray genresJson = detailJson.optJSONArray("genres");
                        if (genresJson != null && genresJson.length() > 0) {
                            List<Integer> genreIds = new ArrayList<>();
                            for (int i = 0; i < genresJson.length(); i++) {
                                genreIds.add(genresJson.getJSONObject(i).optInt("id"));
                            }
                            List<Genre> genres = genreRepository.findByTmdbGenreIdIn(genreIds);
                            movie.setGenres(genres); 
                        }
                    }
                    
                    // Xóa cờ "N/A"
                    if ("N/A".equals(movie.getDirector())) {
                        movie.setDirector(null); // Sẽ được lấp đầy bởi hàm Eager (fetchAndSaveMovieDetail) nếu cần
                    }
                     if ("N/A".equals(movie.getLanguage())) {
                        movie.setLanguage(detailJson.optString("original_language", null));
                    }
                    
                    return movieRepository.save(movie); // Lưu bản nâng cấp "vừa"
                    
                } catch (Exception e) {
                    System.err.println("Lỗi N+1 (Hover) cho ID " + tmdbId + ": " + e.getMessage());
                    return movie; // Trả tạm bản cụt nếu API lỗi
                }
            }
            
            return movie; // Trả về bản đủ (vì director != "N/A" và duration != 0)
        }
        
        // Nếu không có (lần đầu load), gọi API chi tiết 1 lần để tạo bản "vừa"
        try {
            System.out.println("✳️ [Movie-Partial] Tạo mới bản cụt (có duration) cho ID: " + tmdbId);
            String url = BASE_URL + "/movie/" + tmdbId + "?api_key=" + API_KEY + "&language=vi-VN&include_adult=false";
            String resp = restTemplate.getForObject(url, String.class);
            if (resp != null) {
                // Gọi hàm Lazy (an toàn)
                // Hàm này sẽ lấy duration, country từ API chi tiết
                return syncMovieFromList(new JSONObject(resp)); 
            }
        } catch (Exception e) {
            System.err.println("Lỗi getMoviePartial (tạo mới): " + e.getMessage());
        }
        return null; 
    }
    // === KẾT THÚC FIX BUG 1 ===


    /**
     * [G46] HÀM LAZY (LIST): Dùng cho Home, Discover carousels
     * (Sửa lỗi API Storm G29)
     */
    @Transactional
    public Map<String, Object> loadAndSyncPaginatedMovies(String fullApiUrl, int limit) {
        Map<String, Object> responseMap = new HashMap<>();
        List<Map<String, Object>> movies = new ArrayList<>();
        int totalResults = 0;
        int totalPages = 1;
        try {
            String resp = restTemplate.getForObject(fullApiUrl, String.class);
            if (resp == null || resp.isEmpty()) {
                throw new RuntimeException("API response is null or empty for: " + fullApiUrl);
            }
            JSONObject json = new JSONObject(resp);
            JSONArray results = json.optJSONArray("results");
            totalResults = json.optInt("total_results", 0);
            totalPages = Math.min(json.optInt("total_pages", 1), 500); 
            if (results != null) {
                for (int i = 0; i < Math.min(results.length(), limit); i++) { 
                    JSONObject item = results.getJSONObject(i);
                    
                    // [VẤN ĐỀ 8] LỌC PHIM SPAM/18+
                    if (item.optBoolean("adult", false)) continue;
                    if (item.optDouble("vote_average", 0) < 0.1) continue;
                    if (item.optInt("vote_count", 0) < 5) continue;
                    
                    String mediaType = item.optString("media_type", "movie");
                    if (mediaType.equals("movie") || mediaType.equals("tv")) {
                        
                        int tmdbId = item.optInt("id");
                        if (tmdbId <= 0) continue; 

                        // === SỬA LỖI N+1 ===
                        // Thay vì gọi getMoviePartial (nặng), gọi syncMovieFromList (nhẹ)
                        Movie movie = this.syncMovieFromList(item);
                        
                        if (movie != null) {
                            movies.add(this.convertToMap(movie));
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi loadAndSyncPaginatedMovies (" + fullApiUrl + "): " + e.getMessage());
        }
        responseMap.put("movies", movies);
        responseMap.put("totalResults", totalResults);
        responseMap.put("totalPages", totalPages);
        return responseMap;
    }

    @Transactional
    public List<Movie> searchMoviesByTitle(String title) {
        return movieRepository.findByTitleContainingIgnoreCase(title);
    }

    /**
     * [THÊM MỚI] 
     * Lấy một danh sách phim từ DB dựa trên tmdbIds
     * và trả về một Map<tmdbId, Map> để JS dễ dàng tra cứu.
     */
    @Transactional
    public Map<Integer, Map<String, Object>> getMoviesByTmdbIds(List<Integer> tmdbIds) {
        if (tmdbIds == null || tmdbIds.isEmpty()) {
            return Collections.emptyMap(); 
        }
        
        List<Movie> dbMovies = movieRepository.findByTmdbIdIn(tmdbIds);
        
        return dbMovies.stream()
            .collect(Collectors.toMap(
                Movie::getTmdbId,           // Key là tmdbId
                movie -> convertToMap(movie) // Value là map đã được chuyển đổi
            ));
    }

    // ===============================================
    // CÁC HÀM HELPER NỘI BỘ (PRIVATE)
    // ===============================================

    /**
     * [G46] HÀM NÂNG CẤP (EAGER HELPER - MOVIE):
     * Lấp đầy các trường "N/A"
     */
    @Transactional
    private Movie fetchAndSaveMovieDetail(int tmdbId, Movie movieToUpdate) {
        try {
            String url = BASE_URL + "/movie/" + tmdbId + "?api_key=" + API_KEY + "&language=vi-VN&append_to_response=credits&include_adult=false";
            String resp = restTemplate.getForObject(url, String.class);
            JSONObject json = new JSONObject(resp);

            Movie movie = (movieToUpdate != null) ? movieToUpdate : new Movie();
            
            // [G46] LƯU ĐẦY ĐỦ CÁC TRƯỜNG (Ghi đè N/A và NULL)
            movie.setTmdbId(tmdbId);
            
            // === FIX BUG 1 (Bảo vệ dữ liệu thủ công) ===
            // Chỉ ghi đè nếu trường là null, N/A, hoặc rỗng
            if (movie.getTitle() == null || movie.getTitle().isEmpty() || movie.getTitle().equals("N/A")) {
                movie.setTitle(json.optString("title", "N/A"));
            }
            if (movie.getDescription() == null || movie.getDescription().isEmpty()) {
                movie.setDescription(json.optString("overview", null));
            }
             if (movie.getPosterPath() == null || movie.getPosterPath().isEmpty()) {
                movie.setPosterPath(json.optString("poster_path", null));
            }
            if (movie.getBackdropPath() == null || movie.getBackdropPath().isEmpty()) {
                movie.setBackdropPath(json.optString("backdrop_path", null));
            }
            // (Không bảo vệ isFree, url vì hàm này chỉ EAGER, không phải CREATE)
            // ==========================================

            movie.setReleaseDate(parseDate(json.optString("release_date")));
            movie.setDuration(json.optInt("runtime", 0)); 
            movie.setRating((float) json.optDouble("vote_average", 0.0));
            
            // [G46] Lấy đầy đủ (Ghi đè N/A hoặc 0)
            movie.setBudget(json.optLong("budget", 0)); 
            movie.setRevenue(json.optLong("revenue", 0)); 
            movie.setLanguage(json.optString("original_language", null)); 
            
            JSONArray countries = json.optJSONArray("production_countries");
            if (countries != null && countries.length() > 0) {
                movie.setCountry(countries.getJSONObject(0).optString("name")); 
            } else {
                movie.setCountry(null); // [G46] Set NULL nếu API không có
            }

            JSONArray genresJson = json.optJSONArray("genres");
            if (genresJson != null && genresJson.length() > 0) {
                List<Integer> genreIds = new ArrayList<>();
                for (int i = 0; i < genresJson.length(); i++) {
                    genreIds.add(genresJson.getJSONObject(i).optInt("id"));
                }
                List<Genre> genres = genreRepository.findByTmdbGenreIdIn(genreIds);
                movie.setGenres(genres); 
            }

            // [G46] Đồng bộ diễn viên (LAZY)
            JSONObject credits = json.optJSONObject("credits");
            if (credits != null) {
                List<Person> persons = new ArrayList<>();
                JSONArray crew = credits.optJSONArray("crew");
                if (crew != null) {
                    for (int i = 0; i < crew.length(); i++) {
                        JSONObject p = crew.getJSONObject(i);
                        if ("Director".equals(p.optString("job"))) {
                            movie.setDirector(p.optString("name")); // LẤY ĐỦ
                            
                            Person director = getPersonPartialOrSync(p); // (Lazy)
                            if (director != null) persons.add(director);
                            
                            break; 
                        }
                    }
                }
                if (movie.getDirector() == null) movie.setDirector(null); // [G46] Set NULL nếu API không có
                
                JSONArray cast = credits.optJSONArray("cast");
                if (cast != null) {
                    for (int i = 0; i < Math.min(cast.length(), 10); i++) {
                         Person actor = getPersonPartialOrSync(cast.getJSONObject(i)); // (Lazy)
                         if (actor != null) persons.add(actor);
                    }
                }
                movie.setPersons(persons);
            }
            
            return movieRepository.save(movie);
        } catch (Exception e) {
            System.err.println("Lỗi API fetchAndSaveMovieDetail (ID: " + tmdbId + "): " + e.getMessage());
            e.printStackTrace(); 
            return null; 
        }
    }
    
    /**
     * [G46] HÀM NÂNG CẤP (EAGER HELPER - PERSON):
     * Lấp đầy các trường "N/A"
     */
    @Transactional
    private Person fetchAndSavePersonDetail(int tmdbId, Person personToUpdate) {
        try {
            String url = BASE_URL + "/person/" + tmdbId + "?api_key=" + API_KEY + "&language=vi-VN";
            String resp = restTemplate.getForObject(url, String.class);
            JSONObject json = new JSONObject(resp);
            
            Person p = (personToUpdate != null) ? personToUpdate : new Person();
            
            p.setTmdbId(tmdbId);
            
            // === FIX BUG 1 (Bảo vệ dữ liệu thủ công) ===
            if (p.getFullName() == null || p.getFullName().isEmpty()) {
                p.setFullName(json.optString("name"));
            }
             if (p.getProfilePath() == null || p.getProfilePath().isEmpty()) {
                p.setProfilePath(json.optString("profile_path", null));
            }
            // ==========================================
            
            // [G46] LẤY ĐẦY ĐỦ (Ghi đè N/A và NULL)
            p.setBio(json.optString("biography", null)); 
            p.setBirthday(parseDate(json.optString("birthday"))); 
            p.setPlaceOfBirth(json.optString("place_of_birth", null)); 
            p.setKnownForDepartment(json.optString("known_for_department", null));
            p.setPopularity(json.optDouble("popularity", 0.0)); 

            return personRepository.save(p);
        } catch (Exception e) {
            System.err.println("Lỗi API fetchAndSavePersonDetail (ID: " + tmdbId + "): " + e.getMessage());
            return null; 
        }
    }


    // ===============================================
    // [GIẢI PHÁP 3] LOGIC GỘP CHO CAROUSEL
    // ===============================================

    // Dán 3 khối này vào bên trong class MovieService (src/main/java/com/example/project/service/MovieService.java)

    /**
     * [MỚI - VĐ 6] Enum định nghĩa tiêu chí sort
     */
    public enum SortBy {
        HOT, // Sắp xếp theo độ hot (Popularity + Rating)
        NEW  // Sắp xếp theo ngày ra mắt (Release Date)
    }

    /**
     * [MỚI - VĐ 6] Helper: Lấy danh sách phim từ API (đã sync và convert)
     */
    @Transactional
    private List<Map<String, Object>> fetchApiMovies(String fullApiUrl, int limit) {
        List<Map<String, Object>> movies = new ArrayList<>();
        try {
            String resp = restTemplate.getForObject(fullApiUrl, String.class);
            if (resp == null || resp.isEmpty()) {
                throw new RuntimeException("API response is null or empty for: " + fullApiUrl);
            }
            JSONObject json = new JSONObject(resp);
            JSONArray results = json.optJSONArray("results");
            
            if (results != null) {
                for (int i = 0; i < Math.min(results.length(), limit); i++) {
                    JSONObject item = results.getJSONObject(i);
                    // Lọc 18+ và spam
                    if (item.optBoolean("adult", false) || item.optDouble("vote_average", 0) < 0.1 || item.optInt("vote_count", 0) < 5) {
                        continue;
                    }
                    String mediaType = item.optString("media_type", "movie");
                    if (mediaType.equals("movie") || mediaType.equals("tv")) {
                        int tmdbId = item.optInt("id");
                        if (tmdbId <= 0) continue;
                        
                        Movie movie = this.syncMovieFromList(item); // Dùng Lazy
                        if (movie != null) {
                            Map<String, Object> map = this.convertToMap(movie);
                            // [VĐ 6] Thêm trường popularity thô để sort
                            map.put("popularity_raw", item.optDouble("popularity", 0.0));
                            movies.add(map);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi fetchApiMovies (" + fullApiUrl + "): " + e.getMessage());
        }
        return movies;
    }

    /**
     * [MỚI - VĐ 6] Helper: Tạo Comparator để sort "công bằng"
     */
    private Comparator<Map<String, Object>> getRelevanceComparator(SortBy sortBy) {
        if (sortBy == SortBy.NEW) {
            // Sắp xếp theo ngày ra mắt (Mới nhất lên đầu)
            return (m1, m2) -> {
                String date1 = (String) m1.getOrDefault("releaseDate", "1900-01-01");
                String date2 = (String) m2.getOrDefault("releaseDate", "1900-01-01");
                if (date1 == null || date1.isEmpty()) date1 = "1900-01-01";
                if (date2 == null || date2.isEmpty()) date2 = "1900-01-01";
                return date2.compareTo(date1); // So sánh chuỗi (YYYY-MM-DD)
            };
        }
        
        // Mặc định (SortBy.HOT)
        return (m1, m2) -> {
            // Phim custom (không có popularity_raw) sẽ dùng 0
            double pop1 = (double) m1.getOrDefault("popularity_raw", 0.0);
            double pop2 = (double) m2.getOrDefault("popularity_raw", 0.0);
            
            // Lấy rating (đã được convertToMap thành String)
            double rating1 = 0.0;
            double rating2 = 0.0;
            try { rating1 = Double.parseDouble((String) m1.get("rating")); } catch (Exception e) {}
            try { rating2 = Double.parseDouble((String) m2.get("rating")); } catch (Exception e) {}

            // [VĐ 6] Thuật toán "chen chân":
            // 80% trọng số cho Popularity (ưu tiên TMDB), 20% cho Rating (cơ hội cho phim custom)
            // Dùng Math.log10 để giảm chênh lệch quá lớn của popularity
            double score1 = (pop1 > 0 ? Math.log10(pop1) : 0) * 0.8 + (rating1 * 0.6);
            double score2 = (pop2 > 0 ? Math.log10(pop2) : 0) * 0.8 + (rating2 * 0.6);

            return Double.compare(score2, score1);
        };
    }

    /**
     * [MỚI - VĐ 6] Helper cho Lớp 5 (Fallback) - Dùng SortBy.NEW
     * (Hàm này được chuyển từ Controller về Service)
     */
    @Transactional
    public List<Map<String, Object>> loadRecommendedFallback(Integer tmdbId, Set<Integer> addedMovieIds, int limit) {
        String apiUrl;
        if (tmdbId != null) {
            apiUrl = BASE_URL + "/movie/" + tmdbId + "/recommendations?api_key=" + API_KEY + "&language=vi-VN";
        } else {
            // Phim custom không có recommendations, dùng trending
            apiUrl = BASE_URL + "/trending/movie/week?api_key=" + API_KEY + "&language=vi-VN&page=1";
        }
        
        // Dùng NEW DB làm base (để khác với Similar)
        Page<Movie> dbMovies = getNewMoviesFromDB(40); // Gọi hàm nội bộ
        
        List<Map<String, Object>> merged = getMergedCarouselMovies(
            apiUrl, dbMovies, limit + 5, MovieService.SortBy.NEW); // +5 để tăng khả năng lọc

        return merged.stream()
            .filter(m -> {
                Integer mTmdbId = (Integer) m.get("tmdbId");
                Integer mPkId = (Integer) m.get("id");
                // Check cả tmdbId và pkId
                if (mTmdbId != null && addedMovieIds.contains(mTmdbId)) return false;
                if (mTmdbId == null && mPkId != null && addedMovieIds.contains(mPkId)) return false; 
                return true;
            })
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * [MỚI - VĐ 6] Helper: Tìm Keyword ID quan trọng (cho Lớp 2)
     */
    private Integer findKeywords(JSONArray keywords, Map<String, Integer> priorityMap) {
        if (keywords == null) return null;
        
        for (int i = 0; i < keywords.length(); i++) {
            JSONObject kw = keywords.getJSONObject(i);
            String name = kw.optString("name").toLowerCase();
            if (priorityMap.containsKey(name)) {
                return priorityMap.get(name); // Trả về ID của keyword
            }
        }
        return null; // Không tìm thấy keyword ưu tiên
    }

    /**
     * [MỚI - VĐ 6] Logic "Waterfall" 5 lớp (Đã chuyển về Service)
     * (Lớp 1: Collection, Lớp 2: Keyword, Lớp 3: Studio, Lớp 4: Director, Lớp 5: Fallback)
     */
    @Transactional
    public List<Map<String, Object>> getRecommendedMoviesWaterfall(Movie movie, Map<String, Object> response) {
        
        Set<Integer> addedMovieIds = new HashSet<>();
        List<Map<String, Object>> finalRecommendations = new ArrayList<>();
        int limit = 20;
        Integer tmdbId = movie.getTmdbId();

        if (tmdbId != null) {
            addedMovieIds.add(tmdbId);
        }

        if (tmdbId == null) {
            return loadRecommendedFallback(tmdbId, addedMovieIds, limit);
        }

        // --- Bắt đầu Waterfall ---
        JSONObject movieDetailJson = null;
        try {
            // [SỬA VĐ 6] Thêm "keywords" vào append_to_response
            String detailUrl = BASE_URL + "/movie/" + tmdbId + "?api_key=" + API_KEY + "&language=vi-VN&append_to_response=credits,keywords";
            String detailResp = restTemplate.getForObject(detailUrl, String.class);
            movieDetailJson = new JSONObject(detailResp);
        } catch (Exception e) {
            System.err.println("Lỗi gọi API Detail (Waterfall): " + e.getMessage());
            return loadRecommendedFallback(tmdbId, addedMovieIds, limit);
        }

        // === LỚP 1: COLLECTION (Bộ sưu tập) ===
        try {
            JSONObject collection = movieDetailJson.optJSONObject("belongs_to_collection");
            if (collection != null) {
                int collectionId = collection.optInt("id");
                String collectionUrl = BASE_URL + "/collection/" + collectionId + "?api_key=" + API_KEY + "&language=vi-VN";
                String collectionResp = restTemplate.getForObject(collectionUrl, String.class);
                JSONObject collectionJson = new JSONObject(collectionResp);
                JSONArray parts = collectionJson.optJSONArray("parts");
                
                if (parts != null && parts.length() > 0) {
                    for (int i = 0; i < parts.length(); i++) {
                        JSONObject part = parts.getJSONObject(i); 
                        int partTmdbId = part.optInt("id");
                        if (partTmdbId <= 0 || addedMovieIds.contains(partTmdbId)) continue;
                        
                        Movie syncedMovie = syncMovieFromList(part); // Gọi hàm nội bộ
                        if (syncedMovie != null) {
                            finalRecommendations.add(convertToMap(syncedMovie)); // Gọi hàm nội bộ
                            addedMovieIds.add(partTmdbId); 
                        }
                    }
                    if (finalRecommendations.size() >= 2) { 
                        response.put("title", "🎬 Từ Bộ Sưu Tập: " + collectionJson.optString("name"));
                        finalRecommendations.sort(getRelevanceComparator(MovieService.SortBy.NEW)); // Gọi hàm nội bộ
                        return finalRecommendations.stream().limit(limit).collect(Collectors.toList());
                    }
                }
            }
        } catch (Exception e) { System.err.println("Lỗi Lớp 1 (Collection): " + e.getMessage()); }
        
        finalRecommendations.clear();

        // === LỚP 2 (MỚI): FRANCHISE (Keyword) ===
        try {
            // Định nghĩa các Keyword Franchise quan trọng
            Map<String, Integer> priorityKeywords = new HashMap<>();
            priorityKeywords.put("demon slayer", 210024);
            priorityKeywords.put("dragon ball", 114820);
            priorityKeywords.put("one piece", 13091);
            priorityKeywords.put("marvel cinematic universe (mcu)", 180547);
            priorityKeywords.put("fast and furious", 9903);
            priorityKeywords.put("harry potter", 1241);
            
            JSONObject keywordsJson = movieDetailJson.optJSONObject("keywords");
            JSONArray keywordsArray = (keywordsJson != null) ? keywordsJson.optJSONArray("keywords") : null;
            Integer keywordId = findKeywords(keywordsArray, priorityKeywords); // Gọi helper nội bộ
            String keywordName = priorityKeywords.entrySet().stream()
                                    .filter(entry -> entry.getValue().equals(keywordId))
                                    .map(Map.Entry::getKey)
                                    .findFirst().orElse(null);

            if (keywordId != null) {
                String apiUrl = BASE_URL + "/discover/movie?api_key=" + API_KEY + "&language=vi-VN&with_keywords=" + keywordId + "&sort_by=popularity.desc";
                
                List<Map<String, Object>> apiMovies = fetchApiMovies(apiUrl, limit + 1); // Gọi helper nội bộ

                apiMovies.stream()
                    .filter(m -> !addedMovieIds.contains(m.get("tmdbId")))
                    .limit(limit)
                    .forEach(m -> {
                        finalRecommendations.add(m);
                        addedMovieIds.add((Integer) m.get("tmdbId"));
                    });

                if (finalRecommendations.size() >= 3) {
                    response.put("title", "📚 Cùng vũ trụ: " + keywordName);
                    return finalRecommendations;
                }
            }
        } catch (Exception e) { System.err.println("Lỗi Lớp 2 (Keyword): " + e.getMessage()); }
        
        finalRecommendations.clear();

        // === LỚP 3: STUDIO (Nhà sản xuất) ===
        try {
            JSONArray studios = movieDetailJson.optJSONArray("production_companies");
            Integer studioId = null;
            String studioName = null;
            if (studios != null && studios.length() > 0) {
                List<Integer> priorityStudios = List.of(10342, 3, 420, 13183); // Ghibli, Pixar, Marvel, Ufotable
                for (int i = 0; i < studios.length(); i++) {
                    JSONObject s = studios.getJSONObject(i);
                    if (priorityStudios.contains(s.optInt("id"))) {
                        studioId = s.optInt("id");
                        studioName = s.optString("name");
                        break;
                    }
                }
                List<String> commonStudios = List.of("Warner Bros.", "Universal Pictures", "Paramount", "Columbia Pictures", "20th Century Fox");
                if (studioId == null) {
                    JSONObject firstStudio = studios.getJSONObject(0);
                    if (!commonStudios.contains(firstStudio.optString("name"))) {
                        studioId = firstStudio.optInt("id");
                        studioName = firstStudio.optString("name");
                    }
                }
            }
            
            if (studioId != null && studioId > 0) {
                String apiUrl = BASE_URL + "/discover/movie?api_key=" + API_KEY + "&language=vi-VN&with_companies=" + studioId + "&sort_by=popularity.desc";
                
                List<Map<String, Object>> apiMovies = fetchApiMovies(apiUrl, limit + 1); // Gọi helper nội bộ

                apiMovies.stream()
                    .filter(m -> !addedMovieIds.contains(m.get("tmdbId")))
                    .limit(limit)
                    .forEach(m -> {
                        finalRecommendations.add(m);
                        addedMovieIds.add((Integer) m.get("tmdbId"));
                    });
                
                if (finalRecommendations.size() >= 3) {
                    response.put("title", "🏢 Từ Studio: " + studioName);
                    return finalRecommendations;
                }
            }
        } catch (Exception e) { System.err.println("Lỗi Lớp 3 (Studio): " + e.getMessage()); }

        finalRecommendations.clear();
        
        // === LỚP 4: DIRECTOR (Đạo diễn) ===
        try {
            JSONObject credits = movieDetailJson.optJSONObject("credits");
            JSONArray crew = (credits != null) ? credits.optJSONArray("crew") : null;
            Integer directorId = null;
            String directorName = null;
            if (crew != null) {
                for (int i = 0; i < crew.length(); i++) {
                    JSONObject p = crew.getJSONObject(i);
                    if ("Director".equals(p.optString("job"))) {
                        directorId = p.optInt("id");
                        directorName = p.optString("name");
                        break; 
                    }
                }
            }
            
            if (directorId != null && directorId > 0) {
                String apiUrl = BASE_URL + "/discover/movie?api_key=" + API_KEY + "&language=vi-VN&with_crew=" + directorId + "&sort_by=popularity.desc";
                
                List<Map<String, Object>> apiMovies = fetchApiMovies(apiUrl, limit + 1); // Gọi helper nội bộ
                
                apiMovies.stream()
                    .filter(m -> !addedMovieIds.contains(m.get("tmdbId")))
                    .limit(limit)
                    .forEach(m -> {
                        finalRecommendations.add(m);
                        addedMovieIds.add((Integer) m.get("tmdbId"));
                    });

                if (finalRecommendations.size() >= 3) {
                    response.put("title", "🎥 Phim cùng Đạo diễn: " + directorName);
                    return finalRecommendations;
                }
            }
        } catch (Exception e) { System.err.println("Lỗi Lớp 4 (Director): " + e.getMessage()); }

        // === LỚP 5: FALLBACK (Phim Mới) ===
        return loadRecommendedFallback(tmdbId, addedMovieIds, limit);
    }

    /**
     * [VIẾT LẠI - VĐ 6] HÀM GỘP MỚI (Thuật toán Relevance)
     * Lấy phim từ DB và API, gộp, sort relevance, ưu tiên DB
     * @param apiUrl (Link API TMDB)
     * @param dbMovies (Trang kết quả từ DB, đã fetch nhiều)
     * @param limit (Số lượng cuối cùng, vd: 20)
     * @param sortBy (Tiêu chí sort: HOT hoặc NEW)
     * @return Danh sách Map đã gộp, sort và giới hạn
     */
    @Transactional
    public List<Map<String, Object>> getMergedCarouselMovies(
            String apiUrl,
            Page<Movie> dbMovies,
            int limit,
            SortBy sortBy) { // Thêm sortBy

        // 1. Lấy 40 phim API (đã sync và convert, có 'popularity_raw')
        // [FIX VĐ 6] Thêm &include_adult=false
        String safeApiUrl = apiUrl.contains("?") ? apiUrl + "&include_adult=false" : apiUrl + "?include_adult=false";
        List<Map<String, Object>> apiMovies = fetchApiMovies(safeApiUrl, 40);

        // 2. Convert 40 phim DB (thêm 'popularity_raw' = 0 cho phim custom)
        List<Map<String, Object>> dbMoviesList = dbMovies.getContent().stream()
            .map(movie -> {
                Map<String, Object> map = convertToMap(movie);
                // Phim custom (tmdbId=null) không có pop, set 0
                map.put("popularity_raw", 0.0); 
                return map;
            })
            .collect(Collectors.toList());
            
        // 3. Tạo Map (TMDB ID -> MovieMap) từ DB để check trùng (ưu tiên DB)
        Map<Integer, Map<String, Object>> dbTmdbIdMap = dbMoviesList.stream()
            .filter(m -> m.get("tmdbId") != null)
            .collect(Collectors.toMap(
                m -> (Integer) m.get("tmdbId"),
                m -> m,
                (existing, replacement) -> existing // Giữ cái đầu tiên nếu trùng tmdbId trong DB
            ));

        List<Map<String, Object>> finalMergedList = new ArrayList<>();
        Set<Integer> addedTmdbIds = new HashSet<>();

        // 4. [VĐ 6] Lặp API list (Ưu tiên DB win)
        for (Map<String, Object> apiMovie : apiMovies) {
            Integer tmdbId = (Integer) apiMovie.get("tmdbId");
            if (tmdbId == null) continue;

            if (dbTmdbIdMap.containsKey(tmdbId)) {
                // Nếu DB có -> Lấy bản DB (đã được sửa/custom)
                finalMergedList.add(dbTmdbIdMap.get(tmdbId));
            } else {
                // Nếu DB không có -> Lấy bản API (đã sync)
                finalMergedList.add(apiMovie);
            }
            addedTmdbIds.add(tmdbId);
        }

        // 5. [VĐ 6] Lặp DB list (Thêm phim custom "chen chân")
        for (Map<String, Object> dbMovie : dbMoviesList) {
            Integer tmdbId = (Integer) dbMovie.get("tmdbId");

            if (tmdbId == null) {
                // Phim custom (tmdbId = null) -> Luôn thêm
                finalMergedList.add(dbMovie);
            } else if (!addedTmdbIds.contains(tmdbId)) {
                // Phim DB có tmdbId nhưng API không có (ví dụ: API trả 40 phim khác) -> Vẫn thêm
                finalMergedList.add(dbMovie);
            }
        }
        
        // 6. [VĐ 6] Sort "công bằng" theo relevance
        Comparator<Map<String, Object>> comparator = getRelevanceComparator(sortBy);
        finalMergedList.sort(comparator);

        // 7. Trả về
        return finalMergedList.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * [GIẢI PHÁP 3] Các hàm query DB mới (gọi repository)
     */
    public Page<Movie> getHotMoviesFromDB(int limit) {
        // Lấy 20 phim có rating cao nhất từ DB
        return movieRepository.findAllByOrderByRatingDesc(PageRequest.of(0, limit));
    }
    public Page<Movie> getNewMoviesFromDB(int limit) {
        // Lấy 10 phim có ngày ra mắt mới nhất từ DB
        return movieRepository.findAllByOrderByReleaseDateDesc(PageRequest.of(0, limit));
    }
    public Page<Movie> getMoviesByGenreFromDB(int tmdbGenreId, int limit, int page) {
        // Lấy 10 phim theo genre từ DB, hỗ trợ phân trang
        return movieRepository.findAllByGenres_TmdbGenreId(tmdbGenreId, PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "rating")));
    }


    // ===============================================
    // CÁC HÀM CONVERT VÀ UTILS (G46)
    // ===============================================

    /**
     * [SỬA ĐỔI - PHẦN 3]
     * Đảm bảo 'id' trả về là movieID (DB PK)
     */
    public Map<String, Object> convertToMap(Movie movie) {
        if (movie == null) return null;
        Map<String, Object> map = new HashMap<>();
        
        // === THAY ĐỔI CỐT LÕI ===
        map.put("id", movie.getMovieID()); // <-- SỬA: Dùng PK của DB
        // === HẾT THAY ĐỔI ===
        
        map.put("tmdbId", movie.getTmdbId());
        map.put("title", movie.getTitle());
        map.put("overview", movie.getDescription());
        map.put("rating", String.format("%.1f", movie.getRating()));
        
        // === FIX BUG 1 (Hiển thị poster/backdrop) ===
        // Ưu tiên PosterPath (thường là link TMDB)
        String poster = "/images/placeholder.jpg";
        if (movie.getPosterPath() != null && !movie.getPosterPath().isEmpty()) {
            if (movie.getPosterPath().startsWith("http")) {
                poster = movie.getPosterPath(); // Dùng link tuyệt đối (nếu có)
            } else {
                poster = "https://image.tmdb.org/t/p/w500" + movie.getPosterPath(); // Ghép link TMDB
            }
        }
        // Fallback: Dùng URL (nếu là link ảnh)
        else if (movie.getUrl() != null && (movie.getUrl().startsWith("http") && (movie.getUrl().endsWith(".jpg") || movie.getUrl().endsWith(".png")))) {
             poster = movie.getUrl();
        }
        map.put("poster", poster);
        
        // Tương tự cho backdrop
        String backdrop = "/images/placeholder.jpg";
         if (movie.getBackdropPath() != null && !movie.getBackdropPath().isEmpty()) {
            if (movie.getBackdropPath().startsWith("http")) {
                backdrop = movie.getBackdropPath();
            } else {
                backdrop = "https://image.tmdb.org/t/p/original" + movie.getBackdropPath();
            }
        }
        map.put("backdrop", backdrop);
        // ==========================================

        if (movie.getReleaseDate() != null) {
            map.put("year", new SimpleDateFormat("yyyy").format(movie.getReleaseDate()));
            map.put("releaseDate", new SimpleDateFormat("yyyy-MM-dd").format(movie.getReleaseDate()));
        } else { map.put("year", "N/A"); map.put("releaseDate", ""); }
        
        map.put("runtime", (movie.getDuration() > 0) ? movie.getDuration() : "—");
        map.put("director", (movie.getDirector() != null && !movie.getDirector().equals("N/A")) ? movie.getDirector() : "—");
        map.put("country", (movie.getCountry() != null && !movie.getCountry().isEmpty()) ? movie.getCountry() : "Quốc gia");
        map.put("language", (movie.getLanguage() != null && !movie.getLanguage().equals("N/A")) ? movie.getLanguage() : "—");
        
        NumberFormat fmt = NumberFormat.getCurrencyInstance(Locale.US);
        fmt.setMaximumFractionDigits(0);
        map.put("budget", (movie.getBudget() != null && movie.getBudget() > 0) ? fmt.format(movie.getBudget()) : "—");
        map.put("revenue", (movie.getRevenue() != null && movie.getRevenue() > 0) ? fmt.format(movie.getRevenue()) : "—");

        List<String> genres = new ArrayList<>();
        if (movie.getGenres() != null) {
            movie.getGenres().forEach(g -> genres.add(g.getName()));
        }
        map.put("genres", genres);

        return map;
    }

    /**
     * [MỚI - FIX VĐ 5]
     * Overload hàm convertToMap để nhận thêm role_info (vai diễn/công việc)
     * (Hàm này bị thiếu ở lượt trước, gây lỗi biên dịch image_5a81e1.png)
     */
    public Map<String, Object> convertToMap(Movie movie, String role) {
        // 1. Gọi hàm convert 1 tham số (đã có) để lấy map cơ bản
        Map<String, Object> map = this.convertToMap(movie);
        
        // 2. Thêm trường role_info (nếu có)
        if (map != null && role != null && !role.isEmpty()) {
            map.put("role_info", role);
        }
        
        // 3. Trả về map đã bổ sung
        return map;
    }

    /**
     * [SỬA ĐỔI - PHẦN 3]
     * Đảm bảo 'id' trả về là personID (DB PK)
     */
    public Map<String, Object> convertToMap(Person p) {
        if (p == null) return null;
        Map<String, Object> map = new HashMap<>();

        // === THAY ĐỔI CỐT LÕI ===
        map.put("id", p.getPersonID()); // <-- SỬA: Dùng PK của DB
        // === HẾT THAY ĐỔI ===
        
        map.put("tmdbId", p.getTmdbId()); // Vẫn giữ tmdbId để tham chiếu
        map.put("name", p.getFullName());
        map.put("avatar", p.getProfilePath() != null ? "https://image.tmdb.org/t/p/w500" + p.getProfilePath() : "/images/placeholder-person.jpg");

        map.put("biography", (p.getBio() != null && !p.getBio().equals("N/A")) ? p.getBio() : "Đang cập nhật...");
        map.put("birthday", p.getBirthday() != null ? new SimpleDateFormat("dd-MM-yyyy").format(p.getBirthday()) : "—");
        map.put("place_of_birth", (p.getPlaceOfBirth() != null && !p.getPlaceOfBirth().isEmpty()) ? p.getPlaceOfBirth() : "—");
        map.put("known_for_department", (p.getKnownForDepartment() != null && !p.getKnownForDepartment().equals("N/A")) ? p.getKnownForDepartment() : "—");
        map.put("popularity", p.getPopularity() != null ? p.getPopularity() : 0.0);
        return map;
    }

    // (Hàm parseDate giữ nguyên)
    private Date parseDate(String dateString) {
        // ... (Giữ nguyên)
        if (dateString == null || dateString.isEmpty() || dateString.equals("null")) {
            return null;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(dateString);
        } catch (ParseException e) {
            return null;
        }
    }

    // [G46] SỬA LỖI API STORM:
    // syncTmdbIds (cho Live Suggestion) phải gọi hàm LAZY.
    @Transactional
    public void syncTmdbIds(List<Integer> tmdbIds) {
        if (tmdbIds == null || tmdbIds.isEmpty()) return;
        
        List<Integer> existingIds = movieRepository.findTmdbIdsIn(tmdbIds);
        List<Integer> idsToFetch = new ArrayList<>();
        for (Integer id : tmdbIds) {
            if (!existingIds.contains(id)) idsToFetch.add(id);
        }
        if (idsToFetch.isEmpty()) return;

        for (Integer id : idsToFetch) {
            try {
                // [G46] Gọi API chi tiết 1 lần để lấy JSON
                String url = BASE_URL + "/movie/" + id + "?api_key=" + API_KEY + "&language=vi-VN&include_adult=false";
                String resp = restTemplate.getForObject(url, String.class);
                if (resp != null) {
                    syncMovieFromList(new JSONObject(resp)); // Gọi hàm Lazy
                }
            } catch (Exception e) {
                System.err.println("Lỗi sync nhanh ID " + id + ": " + e.getMessage());
            }
        }
    }



    
    
    /**
     * [SỬA LỖI] Nhận movieID (PK), tìm tmdbId, sau đó gọi findTrailers.
     */
    public String findBestTrailerKey(int movieID) {
        // Lấy phim từ DB
        Movie movie = movieRepository.findById(movieID).orElse(null);
        if (movie == null || movie.getTmdbId() == null) {
            return null; // Phim tự tạo hoặc không có tmdbId sẽ không có trailer
        }
        
        // Gọi hàm findTrailers (đã sửa) với tmdbId
        List<Map<String, Object>> trailers = findTrailers(movie.getTmdbId(), 1);
        if (trailers.isEmpty()) return null;
        return (String) trailers.get(0).get("key");
    }
    public List<Map<String, Object>> findTrailers(int tmdbId, int limit) {
        // ... (Giữ nguyên)
        List<Map<String, Object>> trailers = new ArrayList<>();
        Set<String> existingKeys = new HashSet<>();
        try {
            String urlVi = BASE_URL + "/movie/" + tmdbId + "/videos?api_key=" + API_KEY + "&language=vi-VN&include_adult=false";
            String respVi = restTemplate.getForObject(urlVi, String.class);
            parseAndAddTrailers(respVi, trailers, existingKeys, limit);
        } catch (Exception e) {
            System.err.println("Lỗi findTrailers (vi-VN): " + e.getMessage());
        }
        if (trailers.size() < limit) {
            try {
                String urlEn = BASE_URL + "/movie/" + tmdbId + "/videos?api_key=" + API_KEY + "&language=en-US&include_adult=false";
                String respEn = restTemplate.getForObject(urlEn, String.class);
                parseAndAddTrailers(respEn, trailers, existingKeys, limit);
            } catch (Exception e) {
                System.err.println("Lỗi findTrailers (en-US): " + e.getMessage());
            }
        }
        return trailers;
    }
    private void parseAndAddTrailers(String jsonResponse, List<Map<String, Object>> trailers, Set<String> existingKeys, int limit) {
        // ... (Giữ nguyên)
        if (jsonResponse == null || jsonResponse.isEmpty()) return;
        try {
            JSONArray results = new JSONObject(jsonResponse).optJSONArray("results");
            if (results != null) {
                for (int i = 0; i < results.length(); i++) {
                    if (trailers.size() >= limit) break; 
                    JSONObject v = results.getJSONObject(i);
                    String site = v.optString("site");
                    String type = v.optString("type");
                    String key = v.optString("key");
                    if ("YouTube".equals(site) && ("Trailer".equals(type) || "Teaser".equals(type)) && key != null && !existingKeys.contains(key)) {
                        Map<String, Object> trailer = new HashMap<>();
                        trailer.put("key", key);
                        trailer.put("name", v.optString("name"));
                        trailers.add(trailer);
                        existingKeys.add(key); 
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi parseAndAddTrailers: " + e.getMessage());
        }
    }
    
    /**
     * [SỬA LỖI] Nhận movieID (PK), tìm tmdbId, sau đó gọi API TMDB.
     * [SỬA LỖI] Ưu tiên title từ DB cho logic tìm logo (Vấn đề 8)
     */
    public String findBestLogoPath(int movieID) {
        Movie movie = movieRepository.findById(movieID).orElse(null);
        if (movie == null || movie.getTmdbId() == null) {
            return null; // Phim tự tạo hoặc không có tmdbId
        }

        Integer tmdbId = movie.getTmdbId();
        String dbTitle = movie.getTitle(); // Lấy title từ DB

        try {
            String url = BASE_URL + "/movie/" + tmdbId + "/images?api_key=" + API_KEY + "&include_image_language=vi,en,null";
            String resp = restTemplate.getForObject(url, String.class);
            JSONObject json = new JSONObject(resp);
            JSONArray logos = json.optJSONArray("logos");
            if (logos == null || logos.length() == 0) return null;
            
            JSONObject bestLogo = null;

            // [SỬA VĐ 8] Logic ưu tiên logo mới:
            // 1. Ưu tiên "vi"
            for (int i = 0; i < logos.length(); i++) {
                if ("vi".equals(logos.getJSONObject(i).optString("iso_639_1"))) {
                    bestLogo = logos.getJSONObject(i); break;
                }
            }
            // 2. Nếu không có "vi", ưu tiên "en"
            if (bestLogo == null) {
                for (int i = 0; i < logos.length(); i++) {
                    if ("en".equals(logos.getJSONObject(i).optString("iso_639_1"))) {
                        bestLogo = logos.getJSONObject(i); break;
                    }
                }
            }
            // 3. Nếu không có "en", lấy logo đầu tiên (bất kể ngôn ngữ)
            if (bestLogo == null) bestLogo = logos.getJSONObject(0);

            // (Logic ưu tiên title DB của bạn rất khó implement
            // vì TMDB API không cho tìm logo bằng tên, chỉ bằng ID.
            // Logic ưu tiên "vi" -> "en" -> "bất kỳ" ở trên là giải pháp tốt nhất.)

            return bestLogo.optString("file_path");
        } catch (Exception e) {
            System.err.println("Lỗi API findBestLogoPath (ID: " + tmdbId + "): " + e.getMessage());
            return null;
        }
    }

    // (Các hàm CRUD cũ giữ nguyên)
    public List<Movie> getAllMovies() { return movieRepository.findAll(); }
    public Movie getMovieById(int movieId) {
        return movieRepository.findById(movieId).orElseThrow(() -> new RuntimeException("Không tìm thấy phim với ID: " + movieId));
    }
    @Transactional
    public void deleteMovie(int movieId) { movieRepository.deleteById(movieId); }
    @Transactional
    public Movie importFromTmdb(int tmdbId) { return getMovieOrSync(tmdbId); }
    @Transactional
    public Movie createMovie(MovieRequest request) {
        Movie movie = new Movie();
        mapRequestToMovie(request, movie); 
        return movieRepository.save(movie);
    }
    @Transactional
    public Movie updateMovie(int movieId, MovieRequest request) {
        Movie movie = getMovieById(movieId); 
        mapRequestToMovie(request, movie);
        return movieRepository.save(movie);
    }
    private void mapRequestToMovie(MovieRequest request, Movie movie) {
        movie.setTitle(request.getTitle());
        movie.setDescription(request.getDescription());
        movie.setReleaseDate(request.getReleaseDate());
        movie.setDuration(request.getDuration());
        movie.setRating(request.getRating());
        movie.setFree(request.isFree());
        movie.setUrl(request.getUrl());
        movie.setPosterPath(request.getPosterPath());
        movie.setBackdropPath(request.getBackdropPath());
    }
}