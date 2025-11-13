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
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MovieService {

    @Autowired private MovieRepository movieRepository;
    @Autowired private GenreRepository genreRepository;
    @Autowired private PersonRepository personRepository;
    @Autowired private CategoryRepository categoryRepository;
    
    @Autowired
    private RestTemplate restTemplate;
    
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
        
        // 1. Check DB trước
        Optional<Movie> existing = movieRepository.findByTmdbId(tmdbId);
        if (existing.isPresent()) {
            return existing.get(); // Nếu có rồi (dù "cụt" hay "đủ"), trả về ngay.
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
        
        return movieRepository.save(movie);
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
    @Transactional
    public Movie getMoviePartial(int tmdbId) {
        Optional<Movie> existing = movieRepository.findByTmdbId(tmdbId);
        
        if (existing.isPresent()) {
            Movie movie = existing.get();
            
            // KIỂM TRA CỜ: Nếu là bản "cụt" (director="N/A")
            // (Chúng ta dùng duration==0 cũng được, nhưng director="N/A" an toàn hơn)
            if (movie.getDirector() != null && movie.getDirector().equals("N/A")) {
                
                // Phim này "cụt", gọi API chi tiết 1 LẦN để lấp đầy
                try {
                    System.out.println("♻️ [Movie-Partial] Nâng cấp (cho Hover) ID: " + tmdbId);
                    String url = BASE_URL + "/movie/" + tmdbId + "?api_key=" + API_KEY + "&language=vi-VN";
                    String resp = restTemplate.getForObject(url, String.class);
                    if (resp == null) return movie; // Trả tạm bản cụt nếu API lỗi
                    
                    JSONObject detailJson = new JSONObject(resp);
                    
                    // Lấp đầy các trường còn thiếu (duration, country)
                    // (Không lấp đầy cờ N/A, để Eager lo)
                    movie.setDuration(detailJson.optInt("runtime", 0));
                    JSONArray countries = detailJson.optJSONArray("production_countries");
                    if (countries != null && countries.length() > 0) {
                        movie.setCountry(countries.getJSONObject(0).optString("name"));
                    } else {
                        movie.setCountry(null); 
                    }
                    
                    // Lấy thể loại từ detailJson
                    JSONArray genresJson = detailJson.optJSONArray("genres");
                    if (genresJson != null && genresJson.length() > 0) {
                        List<Integer> genreIds = new ArrayList<>();
                        for (int i = 0; i < genresJson.length(); i++) {
                            genreIds.add(genresJson.getJSONObject(i).optInt("id"));
                        }
                        List<Genre> genres = genreRepository.findByTmdbGenreIdIn(genreIds);
                        movie.setGenres(genres); 
                    }
                    
                    return movieRepository.save(movie); // Lưu bản nâng cấp "vừa"
                    
                } catch (Exception e) {
                    System.err.println("Lỗi N+1 (Hover) cho ID " + tmdbId + ": " + e.getMessage());
                    return movie; // Trả tạm bản cụt nếu API lỗi
                }
            }
            
            return movie; // Trả về bản đủ (vì director != "N/A")
        }
        
        // Nếu không có (lần đầu load), gọi API chi tiết 1 lần để tạo bản "vừa"
        try {
            System.out.println("✳️ [Movie-Partial] Tạo mới bản cụt (có duration) cho ID: " + tmdbId);
            String url = BASE_URL + "/movie/" + tmdbId + "?api_key=" + API_KEY + "&language=vi-VN";
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
                    String mediaType = item.optString("media_type", "movie"); 
                    if (mediaType.equals("movie") || mediaType.equals("tv")) {
                        
                        // [G46] SỬA LỖI G29: Gọi hàm LAZY
                        Movie movie = this.syncMovieFromList(item); // ĐÚNG
                        
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
            String url = BASE_URL + "/movie/" + tmdbId + "?api_key=" + API_KEY + "&language=vi-VN&append_to_response=credits";
            String resp = restTemplate.getForObject(url, String.class);
            JSONObject json = new JSONObject(resp);

            Movie movie = (movieToUpdate != null) ? movieToUpdate : new Movie();
            
            // [G46] LƯU ĐẦY ĐỦ CÁC TRƯỜNG (Ghi đè N/A và NULL)
            movie.setTmdbId(tmdbId);
            movie.setTitle(json.optString("title", "N/A"));
            movie.setDescription(json.optString("overview", null));
            movie.setReleaseDate(parseDate(json.optString("release_date")));
            movie.setDuration(json.optInt("runtime", 0)); 
            movie.setRating((float) json.optDouble("vote_average", 0.0));
            movie.setPosterPath(json.optString("poster_path", null));
            movie.setBackdropPath(json.optString("backdrop_path", null));
            
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
            p.setFullName(json.optString("name"));
            p.setProfilePath(json.optString("profile_path", null));
            
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
    // CÁC HÀM CONVERT VÀ UTILS (G46)
    // ===============================================

    /**
     * [G46] Sửa lỗi hiển thị UI cho "0 phút" và "N/A"
     */
    public Map<String, Object> convertToMap(Movie movie) {
        if (movie == null) return null;
        Map<String, Object> map = new HashMap<>();
        map.put("id", movie.getTmdbId());
        map.put("title", movie.getTitle());
        map.put("overview", movie.getDescription());
        map.put("rating", String.format("%.1f", movie.getRating()));
        map.put("poster", movie.getPosterPath() != null ? "https://image.tmdb.org/t/p/w500" + movie.getPosterPath() : "/images/placeholder.jpg");
        map.put("backdrop", movie.getBackdropPath() != null ? "https://image.tmdb.org/t/p/original" + movie.getBackdropPath() : "/images/placeholder.jpg");
        
        if (movie.getReleaseDate() != null) {
            map.put("year", new SimpleDateFormat("yyyy").format(movie.getReleaseDate()));
            map.put("releaseDate", new SimpleDateFormat("yyyy-MM-dd").format(movie.getReleaseDate()));
        } else { map.put("year", "N/A"); map.put("releaseDate", ""); }
        
        // [SỬA LỖI "phút phút"]
        // Trả về SỐ (150) hoặc DẤU "—". Giao diện sẽ tự thêm " phút".
        map.put("runtime", (movie.getDuration() > 0) ? movie.getDuration() : "—"); // <--- ĐÃ SỬA
        
        map.put("director", (movie.getDirector() != null && !movie.getDirector().equals("N/A")) ? movie.getDirector() : "—");
        
        // [GIỮ NGUYÊN] Logic "Quốc gia" đã đúng
        // Nó lấy "United States of America" cho Frankenstein, 
        // và trả "Quốc gia" cho phim có country=NULL (như ảnh 1).
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
     * [G46] Sửa lỗi hiển thị UI cho cờ "N/A"
     */
    public Map<String, Object> convertToMap(Person p) {
        if (p == null) return null;
        Map<String, Object> map = new HashMap<>();
        map.put("id", p.getTmdbId());
        map.put("name", p.getFullName());
        map.put("avatar", p.getProfilePath() != null ? "https://image.tmdb.org/t/p/w500" + p.getProfilePath() : "/images/placeholder-person.jpg");

        // [G46] Sửa logic hiển thị cờ "N/A"
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
                String url = BASE_URL + "/movie/" + id + "?api_key=" + API_KEY + "&language=vi-VN";
                String resp = restTemplate.getForObject(url, String.class);
                if (resp != null) {
                    syncMovieFromList(new JSONObject(resp)); // Gọi hàm Lazy
                }
            } catch (Exception e) {
                System.err.println("Lỗi sync nhanh ID " + id + ": " + e.getMessage());
            }
        }
    }
    
    // (Các hàm G23 - findBestTrailerKey, findTrailers, parseAndAddTrailers - giữ nguyên)
    public String findBestTrailerKey(int tmdbId) {
        // ... (GiV
        List<Map<String, Object>> trailers = findTrailers(tmdbId, 1);
        if (trailers.isEmpty()) return null;
        return (String) trailers.get(0).get("key");
    }
    public List<Map<String, Object>> findTrailers(int tmdbId, int limit) {
        // ... (Giữ nguyên)
        List<Map<String, Object>> trailers = new ArrayList<>();
        Set<String> existingKeys = new HashSet<>();
        try {
            String urlVi = BASE_URL + "/movie/" + tmdbId + "/videos?api_key=" + API_KEY + "&language=vi-VN";
            String respVi = restTemplate.getForObject(urlVi, String.class);
            parseAndAddTrailers(respVi, trailers, existingKeys, limit);
        } catch (Exception e) {
            System.err.println("Lỗi findTrailers (vi-VN): " + e.getMessage());
        }
        if (trailers.size() < limit) {
            try {
                String urlEn = BASE_URL + "/movie/" + tmdbId + "/videos?api_key=" + API_KEY + "&language=en-US";
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
    
    // (Hàm findBestLogoPath giữ nguyên)
    public String findBestLogoPath(int tmdbId) {
        // ... (Giữ nguyên)
        try {
            String url = BASE_URL + "/movie/" + tmdbId + "/images?api_key=" + API_KEY + "&include_image_language=vi,en,null";
            String resp = restTemplate.getForObject(url, String.class);
            JSONObject json = new JSONObject(resp);
            JSONArray logos = json.optJSONArray("logos");
            if (logos == null || logos.length() == 0) return null;
            JSONObject bestLogo = null;
            for (int i = 0; i < logos.length(); i++) {
                if ("vi".equals(logos.getJSONObject(i).optString("iso_639_1"))) {
                    bestLogo = logos.getJSONObject(i); break;
                }
            }
            if (bestLogo == null) {
                for (int i = 0; i < logos.length(); i++) {
                    if ("en".equals(logos.getJSONObject(i).optString("iso_639_1"))) {
                        bestLogo = logos.getJSONObject(i); break;
                    }
                }
            }
            if (bestLogo == null) bestLogo = logos.getJSONObject(0);
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