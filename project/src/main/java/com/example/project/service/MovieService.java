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
import com.example.project.dto.MovieSearchFilters;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Join;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
public class MovieService {

    // ---- 1. CẤU HÌNH & REPOSITORY ----

    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private GenreRepository genreRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private RestTemplate restTemplate;

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";

    // Cho phép Controller truy cập Repository
    public MovieRepository getMovieRepository() {
        return movieRepository;
    }

    // ---- 2. KHỞI TẠO DỮ LIỆU CƠ BẢN ----

    // Khởi tạo các thể loại (Genre) cơ bản từ TMDB ID.
    @Transactional
    public void initGenres() {
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

    // ---- 3. CORE SYNC LOGIC (MOVIE) ----

    // Lấy movie theo movieID (PK), tự động sync đầy đủ (EAGER) nếu cần.
    // Dùng cho Trang Chi Tiết (MovieDetailController).
    @Transactional
    public Movie getMovieByIdOrSync(int movieID) {
        Optional<Movie> existing = movieRepository.findById(movieID);
        if (existing.isEmpty())
            return null;

        Movie movie = existing.get();
        if (movie.getTmdbId() == null)
            return movie; // Phim tự tạo

        // Kiểm tra cờ "N/A" (bản 'cụt') -> EAGER load
        if ("N/A".equals(movie.getDirector())) {
            System.out.println("♻️ [Movie EAGER] Nâng cấp chi tiết cho movie ID: " + movieID);
            return fetchAndSaveMovieDetail(movie.getTmdbId(), movie);
        }
        return movie;
    }

    // Lấy movie theo tmdbId (TMDB ID), tự động sync đầy đủ (EAGER) nếu cần.
    // Dùng cho Import từ Admin/ContentManager.
    @Transactional
    public Movie getMovieOrSync(int tmdbId) {
        Optional<Movie> existing = movieRepository.findByTmdbId(tmdbId);
        if (existing.isPresent()) {
            Movie movie = existing.get();
            // Chỉ nâng cấp khi gặp cờ "N/A"
            if ("N/A".equals(movie.getDirector())) {
                System.out.println("♻️ [Movie EAGER] Nâng cấp chi tiết cho phim ID: " + tmdbId);
                return fetchAndSaveMovieDetail(tmdbId, movie);
            } else {
                return movie;
            }
        }

        System.out.println("✳️ [Movie EAGER] Tạo mới chi tiết cho phim ID: " + tmdbId);
        return fetchAndSaveMovieDetail(tmdbId, null);
    }

    // Đồng bộ phim từ API List (LAZY): Chỉ lưu bản "cụt" (partial).
    // Dùng cho Trang Danh Sách/Search/Carousel.
    @Transactional
    public Movie syncMovieFromList(JSONObject jsonItem) {
        int tmdbId = jsonItem.optInt("id");
        if (tmdbId <= 0)
            return null;

        // ----- Lọc phim spam/18+
        // if (jsonItem.optBoolean("adult", false)) return null;
        // if (jsonItem.optDouble("vote_average", 0) < 0.1) return null;
        // if (jsonItem.optInt("vote_count", 0) < 5) return null;

        // ----- Kiểm tra DB: Nếu đã có, trả về ngay (KHÔNG GHI ĐÈ)
        Optional<Movie> existing = movieRepository.findByTmdbId(tmdbId);
        if (existing.isPresent()) {
            return existing.get();
        }

        // ----- Tạo mới bản "cụt"
        System.out.println("✳️ [Movie LAZY] Tạo mới bản cụt cho ID: " + tmdbId);
        Movie movie = new Movie();
        movie.setTmdbId(tmdbId);

        // Lấy các trường cơ bản
        movie.setTitle(jsonItem.optString("title", jsonItem.optString("name", "N/A")));
        movie.setDescription(jsonItem.optString("overview", null));
        movie.setPosterPath(jsonItem.optString("poster_path", null));
        movie.setBackdropPath(jsonItem.optString("backdrop_path", null));
        movie.setRating((float) jsonItem.optDouble("vote_average", 0.0));
        movie.setReleaseDate(parseDate(jsonItem.optString("release_date", jsonItem.optString("first_air_date"))));

        // Lấy Duration + Country
        movie.setDuration(jsonItem.optInt("runtime", 0));
        JSONArray countries = jsonItem.optJSONArray("production_countries");
        if (countries != null && countries.length() > 0) {
            movie.setCountry(countries.getJSONObject(0).optString("name"));
        } else {
            movie.setCountry(null);
        }

        // Đặt cờ "N/A" (Chờ Eager lấp đầy)
        movie.setDirector("N/A");
        movie.setLanguage("N/A");

        // Thể loại
        JSONArray genreIdsJson = jsonItem.optJSONArray("genre_ids");
        if (genreIdsJson != null && genreIdsJson.length() > 0) {
            List<Integer> genreIds = new ArrayList<>();
            for (int i = 0; i < genreIdsJson.length(); i++) {
                genreIds.add(genreIdsJson.optInt(i));
            }
            List<Genre> genres = genreRepository.findByTmdbGenreIdIn(genreIds);
            movie.setGenres(new HashSet<>(genres));
        }

        return movieRepository.save(movie);
    }

    // Lấy movie (PARTIAL) theo tmdbId: Cố gắng nâng cấp bản "cụt" (cho Hover Card)
    @Transactional
    public Movie getMoviePartial(int tmdbId) {
        Optional<Movie> existing = movieRepository.findByTmdbId(tmdbId);
        if (existing.isEmpty()) {
            // Nếu chưa có, gọi API chi tiết 1 lần để tạo bản "vừa"
            try {
                System.out.println("✳️ [Movie-Partial] Tạo mới bản cụt (có duration) cho ID: " + tmdbId);
                String url = BASE_URL + "/movie/" + tmdbId + "?api_key=" + API_KEY
                        + "&language=vi-VN&include_adult=false";
                String resp = restTemplate.getForObject(url, String.class);
                if (resp != null)
                    return syncMovieFromList(new JSONObject(resp));
            } catch (Exception e) {
                System.err.println("Lỗi getMoviePartial (tạo mới): " + e.getMessage());
            }
            return null;
        }

        Movie movie = existing.get();
        boolean isPartial = "N/A".equals(movie.getDirector());

        if (isPartial) {
            // ----- Nâng cấp bản "cụt" bằng 1 API call
            try {
                System.out.println("♻️ [Movie-Partial] Nâng cấp (cho Hover/Suggestion) ID: " + tmdbId);
                String url = BASE_URL + "/movie/" + tmdbId + "?api_key=" + API_KEY
                        + "&language=vi-VN&include_adult=false";
                String resp = restTemplate.getForObject(url, String.class);
                if (resp == null)
                    return movie;

                JSONObject detailJson = new JSONObject(resp);

                // Nâng cấp có chọn lọc (không ghi đè dữ liệu thủ công)
                if (movie.getReleaseDate() == null)
                    movie.setReleaseDate(parseDate(detailJson.optString("release_date")));
                if (movie.getRating() == 0.0f)
                    movie.setRating((float) detailJson.optDouble("vote_average", 0.0));
                if (movie.getDuration() == 0)
                    movie.setDuration(detailJson.optInt("runtime", 0));

                // Cập nhật Country, Genres và xóa cờ N/A
                JSONArray countries = detailJson.optJSONArray("production_countries");
                if (movie.getCountry() == null || movie.getCountry().isEmpty()) {
                    if (countries != null && countries.length() > 0)
                        movie.setCountry(countries.getJSONObject(0).optString("name"));
                    else
                        movie.setCountry(null);
                }

                if (movie.getGenres() == null || movie.getGenres().isEmpty()) {
                    JSONArray genresJson = detailJson.optJSONArray("genres");
                    if (genresJson != null && genresJson.length() > 0) {
                        List<Integer> genreIds = new ArrayList<>();
                        for (int i = 0; i < genresJson.length(); i++)
                            genreIds.add(genresJson.getJSONObject(i).optInt("id"));
                        movie.setGenres(new HashSet<>(genreRepository.findByTmdbGenreIdIn(genreIds)));
                    }
                }
                if ("N/A".equals(movie.getDirector()))
                    movie.setDirector(null);
                if ("N/A".equals(movie.getLanguage()))
                    movie.setLanguage(detailJson.optString("original_language", null));

                return movieRepository.save(movie);

            } catch (Exception e) {
                System.err.println("Lỗi Movie-Partial cho ID " + tmdbId + ": " + e.getMessage());
                return movie;
            }
        }
        return movie;
    }

    // ---- 4. CORE SYNC LOGIC (PERSON) ----

    // Lấy Person theo personID (PK), tự động sync đầy đủ (EAGER) nếu cần.
    @Transactional
    public Person getPersonByIdOrSync(int personID) {
        Optional<Person> existing = personRepository.findById(personID);
        if (existing.isEmpty())
            return null;

        Person person = existing.get();
        if (person.getTmdbId() == null)
            return person;

        // Kiểm tra cờ "N/A" -> EAGER load
        if ("N/A".equals(person.getBio())) {
            System.out.println("♻️ [Person EAGER] Nâng cấp chi tiết cho person ID: " + personID);
            return fetchAndSavePersonDetail(person.getTmdbId(), person);
        }
        return person;
    }

    // Lấy Person theo tmdbId (TMDB ID), tự động sync đầy đủ (EAGER) nếu cần.
    @Transactional
    public Person getPersonOrSync(int tmdbId) {
        Optional<Person> existing = personRepository.findByTmdbId(tmdbId);
        if (existing.isPresent()) {
            Person person = existing.get();
            if ("N/A".equals(person.getBio())) {
                System.out.println("♻️ [Person EAGER] Nâng cấp chi tiết cho Person ID: " + tmdbId);
                return fetchAndSavePersonDetail(tmdbId, person);
            } else {
                return person;
            }
        }
        System.out.println("✳️ [Person EAGER] Tạo mới chi tiết cho Person ID: " + tmdbId);
        return fetchAndSavePersonDetail(tmdbId, null);
    }

    // Đồng bộ Person từ API List (LAZY): Chỉ lưu bản "cụt" (partial).
    @Transactional
    public Person getPersonPartialOrSync(JSONObject json) {
        int tmdbId = json.optInt("id");
        if (tmdbId <= 0)
            return null;

        Optional<Person> existing = personRepository.findByTmdbId(tmdbId);
        if (existing.isPresent())
            return existing.get();

        // ----- Tạo mới bản "cụt"
        System.out.println("✳️ [Person LAZY] Tạo mới bản cụt cho ID: " + tmdbId);
        Person p = new Person();
        p.setTmdbId(tmdbId);

        // Lazy load CHỈ lấy Tên và Ảnh
        p.setFullName(json.optString("name"));
        p.setProfilePath(json.optString("profile_path", null));

        // Đặt cờ "N/A" (Chờ Eager lấp đầy)
        p.setKnownForDepartment("N/A");
        p.setBio("N/A");

        // Các trường SET NULL (Chờ Eager lấp đầy)
        p.setBirthday(null);
        p.setPlaceOfBirth(null);
        p.setPopularity(null);

        return personRepository.save(p);
    }

    // ---- 5. CORE SYNC HELPERS (PRIVATE) ----

    // Helper: Nâng cấp đầy đủ các trường (EAGER) cho Movie
    @Transactional
    private Movie fetchAndSaveMovieDetail(int tmdbId, Movie movieToUpdate) {
        try {
            String url = BASE_URL + "/movie/" + tmdbId + "?api_key=" + API_KEY
                    + "&language=vi-VN&append_to_response=credits&include_adult=false";
            String resp = restTemplate.getForObject(url, String.class);
            JSONObject json = new JSONObject(resp);

            Movie movie = (movieToUpdate != null) ? movieToUpdate : new Movie();

            // ----- Bảo vệ dữ liệu tay (chỉ ghi đè nếu null/rỗng/N/A)
            movie.setTmdbId(tmdbId);
            if (movie.getTitle() == null || movie.getTitle().isEmpty() || movie.getTitle().equals("N/A"))
                movie.setTitle(json.optString("title", "N/A"));
            if (movie.getDescription() == null || movie.getDescription().isEmpty())
                movie.setDescription(json.optString("overview", null));
            if (movie.getPosterPath() == null || movie.getPosterPath().isEmpty())
                movie.setPosterPath(json.optString("poster_path", null));
            if (movie.getBackdropPath() == null || movie.getBackdropPath().isEmpty())
                movie.setBackdropPath(json.optString("backdrop_path", null));

            // ----- Lấy đầy đủ (Ghi đè N/A hoặc 0)
            movie.setReleaseDate(parseDate(json.optString("release_date")));
            movie.setDuration(json.optInt("runtime", 0));
            movie.setRating((float) json.optDouble("vote_average", 0.0));
            movie.setBudget(json.optLong("budget", 0));
            movie.setRevenue(json.optLong("revenue", 0));
            movie.setLanguage(json.optString("original_language", null));

            // Country
            JSONArray countries = json.optJSONArray("production_countries");
            if (countries != null && countries.length() > 0)
                movie.setCountry(countries.getJSONObject(0).optString("name"));
            else
                movie.setCountry(null);

            // Genres
            JSONArray genresJson = json.optJSONArray("genres");
            if (genresJson != null && genresJson.length() > 0) {
                List<Integer> genreIds = new ArrayList<>();
                for (int i = 0; i < genresJson.length(); i++)
                    genreIds.add(genresJson.getJSONObject(i).optInt("id"));
                movie.setGenres(new HashSet<>(genreRepository.findByTmdbGenreIdIn(genreIds)));
            }

            // Đồng bộ diễn viên và đạo diễn (LAZY)
            JSONObject credits = json.optJSONObject("credits");
            if (credits != null) {
                Set<Person> persons = new HashSet<>();
                JSONArray crew = credits.optJSONArray("crew");
                if (crew != null) {
                    for (int i = 0; i < crew.length(); i++) {
                        JSONObject p = crew.getJSONObject(i);
                        if ("Director".equals(p.optString("job"))) {
                            movie.setDirector(p.optString("name"));
                            Person director = getPersonPartialOrSync(p);
                            if (director != null)
                                persons.add(director);
                            break;
                        }
                    }
                }
                if (movie.getDirector() == null)
                    movie.setDirector(null);

                JSONArray cast = credits.optJSONArray("cast");
                if (cast != null) {
                    for (int i = 0; i < Math.min(cast.length(), 10); i++) {
                        Person actor = getPersonPartialOrSync(cast.getJSONObject(i));
                        if (actor != null)
                            persons.add(actor);
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

    // Helper: Nâng cấp đầy đủ các trường (EAGER) cho Person
    @Transactional
    private Person fetchAndSavePersonDetail(int tmdbId, Person personToUpdate) {
        try {
            String url = BASE_URL + "/person/" + tmdbId + "?api_key=" + API_KEY + "&language=vi-VN";
            String resp = restTemplate.getForObject(url, String.class);
            JSONObject json = new JSONObject(resp);

            Person p = (personToUpdate != null) ? personToUpdate : new Person();

            p.setTmdbId(tmdbId);

            // ----- Bảo vệ dữ liệu tay
            if (p.getFullName() == null || p.getFullName().isEmpty())
                p.setFullName(json.optString("name"));
            if (p.getProfilePath() == null || p.getProfilePath().isEmpty())
                p.setProfilePath(json.optString("profile_path", null));

            // ----- LẤY ĐẦY ĐỦ (Ghi đè N/A và NULL)
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

    // ---- 6. CAROUSEL / MERGE LOGIC ----

    // Enum định nghĩa tiêu chí sort cho carousel.
    public enum SortBy {
        HOT,
        NEW
    }

    /**
     * Hàm gộp danh sách phim từ API và DB, sau đó sắp xếp theo relevance.
     */
    @Transactional
    public List<Map<String, Object>> getMergedCarouselMovies(
            String apiUrl,
            Page<Movie> dbMovies,
            int limit,
            SortBy sortBy) {

        // 1. Lấy 40 phim API (đã sync LAZY và convert, có 'popularity_raw')
        String safeApiUrl = apiUrl.contains("?") ? apiUrl + "&include_adult=false" : apiUrl + "?include_adult=false";
        List<Map<String, Object>> apiMovies = fetchApiMovies(safeApiUrl, 40);

        // 2. Convert 40 phim DB (thêm 'popularity_raw' = 0)
        List<Map<String, Object>> dbMoviesList = dbMovies.getContent().stream()
                .map(movie -> {
                    Map<String, Object> map = convertToMap(movie);
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
                        (existing, replacement) -> existing));

        List<Map<String, Object>> finalMergedList = new ArrayList<>();
        Set<Integer> addedTmdbIds = new HashSet<>();

        // 4. Lặp API list (Ưu tiên DB win)
        for (Map<String, Object> apiMovie : apiMovies) {
            Integer tmdbId = (Integer) apiMovie.get("tmdbId");
            if (tmdbId == null)
                continue;

            if (dbTmdbIdMap.containsKey(tmdbId))
                finalMergedList.add(dbTmdbIdMap.get(tmdbId));
            else
                finalMergedList.add(apiMovie);
            addedTmdbIds.add(tmdbId);
        }

        // 5. Lặp DB list (Thêm phim custom "chen chân")
        for (Map<String, Object> dbMovie : dbMoviesList) {
            Integer tmdbId = (Integer) dbMovie.get("tmdbId");
            if (tmdbId == null)
                finalMergedList.add(dbMovie);
            else if (!addedTmdbIds.contains(tmdbId))
                finalMergedList.add(dbMovie);
        }

        // 6. Sort "công bằng" theo relevance
        Comparator<Map<String, Object>> comparator = getRelevanceComparator(sortBy);
        finalMergedList.sort(comparator);

        return finalMergedList.stream().limit(limit).collect(Collectors.toList());
    }

    // Logic "Waterfall" 5 lớp cho gợi ý phim (Recommended Movies).
    @Transactional
    public List<Map<String, Object>> getRecommendedMoviesWaterfall(Movie movie, Map<String, Object> response) {
        // [NOTE]: Nội dung hàm này sử dụng lại logic cũ, chỉ được sắp xếp lại.
        Set<Integer> addedMovieIds = new HashSet<>();
        List<Map<String, Object>> finalRecommendations = new ArrayList<>();
        int limit = 20;
        Integer tmdbId = movie.getTmdbId();

        if (tmdbId != null)
            addedMovieIds.add(tmdbId);
        if (tmdbId == null)
            return loadRecommendedFallback(tmdbId, addedMovieIds, limit);

        // ----- Fetch Movie Detail + Keywords
        JSONObject movieDetailJson = null;
        try {
            String detailUrl = BASE_URL + "/movie/" + tmdbId + "?api_key=" + API_KEY
                    + "&language=vi-VN&append_to_response=credits,keywords";
            String detailResp = restTemplate.getForObject(detailUrl, String.class);
            movieDetailJson = new JSONObject(detailResp);
        } catch (Exception e) {
            System.err.println("Lỗi gọi API Detail (Waterfall): " + e.getMessage());
            return loadRecommendedFallback(tmdbId, addedMovieIds, limit);
        }

        // ----- LỚP 1: COLLECTION (Bộ sưu tập)
        try {
            JSONObject collection = movieDetailJson.optJSONObject("belongs_to_collection");
            if (collection != null) {
                int collectionId = collection.optInt("id");
                String collectionUrl = BASE_URL + "/collection/" + collectionId + "?api_key=" + API_KEY
                        + "&language=vi-VN";
                JSONObject collectionJson = new JSONObject(restTemplate.getForObject(collectionUrl, String.class));
                JSONArray parts = collectionJson.optJSONArray("parts");

                if (parts != null && parts.length() > 0) {
                    for (int i = 0; i < parts.length(); i++) {
                        JSONObject part = parts.getJSONObject(i);
                        int partTmdbId = part.optInt("id");
                        if (partTmdbId <= 0 || addedMovieIds.contains(partTmdbId))
                            continue;
                        Movie syncedMovie = syncMovieFromList(part);
                        if (syncedMovie != null) {
                            finalRecommendations.add(convertToMap(syncedMovie));
                            addedMovieIds.add(partTmdbId);
                        }
                    }
                    if (finalRecommendations.size() >= 2) {
                        response.put("title", "🎬 Từ Bộ Sưu Tập: " + collectionJson.optString("name"));
                        finalRecommendations.sort(getRelevanceComparator(MovieService.SortBy.NEW));
                        return finalRecommendations.stream().limit(limit).collect(Collectors.toList());
                    }
                }
            }
        } catch (Exception e) {
            /* Lỗi Lớp 1 */ }

        finalRecommendations.clear();

        // ----- LỚP 2: FRANCHISE (Keyword)
        try {
            Map<String, Integer> priorityKeywords = new HashMap<>();
            priorityKeywords.put("demon slayer", 210024);
            priorityKeywords.put("dragon ball", 114820);
            priorityKeywords.put("one piece", 13091);
            priorityKeywords.put("marvel cinematic universe (mcu)", 180547);
            priorityKeywords.put("fast and furious", 9903);
            priorityKeywords.put("harry potter", 1241);

            JSONObject keywordsJson = movieDetailJson.optJSONObject("keywords");
            JSONArray keywordsArray = (keywordsJson != null) ? keywordsJson.optJSONArray("keywords") : null;
            Integer keywordId = findKeywords(keywordsArray, priorityKeywords);
            String keywordName = priorityKeywords.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(keywordId)).map(Map.Entry::getKey).findFirst()
                    .orElse(null);

            if (keywordId != null) {
                String apiUrl = BASE_URL + "/discover/movie?api_key=" + API_KEY + "&language=vi-VN&with_keywords="
                        + keywordId + "&sort_by=popularity.desc";
                List<Map<String, Object>> apiMovies = fetchApiMovies(apiUrl, limit + 1);
                apiMovies.stream().filter(m -> !addedMovieIds.contains(m.get("tmdbId"))).limit(limit).forEach(m -> {
                    finalRecommendations.add(m);
                    addedMovieIds.add((Integer) m.get("tmdbId"));
                });

                if (finalRecommendations.size() >= 3) {
                    response.put("title", "📚 Cùng vũ trụ: " + keywordName);
                    return finalRecommendations;
                }
            }
        } catch (Exception e) {
            /* Lỗi Lớp 2 */ }

        finalRecommendations.clear();

        // ----- LỚP 3: STUDIO (Nhà sản xuất)
        try {
            JSONArray studios = movieDetailJson.optJSONArray("production_companies");
            Integer studioId = null;
            String studioName = null;
            if (studios != null && studios.length() > 0) {
                List<Integer> priorityStudios = List.of(10342, 3, 420, 13183);
                for (int i = 0; i < studios.length(); i++) {
                    JSONObject s = studios.getJSONObject(i);
                    if (priorityStudios.contains(s.optInt("id"))) {
                        studioId = s.optInt("id");
                        studioName = s.optString("name");
                        break;
                    }
                }
                List<String> commonStudios = List.of("Warner Bros.", "Universal Pictures", "Paramount",
                        "Columbia Pictures", "20th Century Fox");
                if (studioId == null) {
                    JSONObject firstStudio = studios.getJSONObject(0);
                    if (!commonStudios.contains(firstStudio.optString("name"))) {
                        studioId = firstStudio.optInt("id");
                        studioName = firstStudio.optString("name");
                    }
                }
            }

            if (studioId != null && studioId > 0) {
                String apiUrl = BASE_URL + "/discover/movie?api_key=" + API_KEY + "&language=vi-VN&with_companies="
                        + studioId + "&sort_by=popularity.desc";
                List<Map<String, Object>> apiMovies = fetchApiMovies(apiUrl, limit + 1);
                apiMovies.stream().filter(m -> !addedMovieIds.contains(m.get("tmdbId"))).limit(limit).forEach(m -> {
                    finalRecommendations.add(m);
                    addedMovieIds.add((Integer) m.get("tmdbId"));
                });

                if (finalRecommendations.size() >= 3) {
                    response.put("title", "🏢 Từ Studio: " + studioName);
                    return finalRecommendations;
                }
            }
        } catch (Exception e) {
            /* Lỗi Lớp 3 */ }

        finalRecommendations.clear();

        // ----- LỚP 4: DIRECTOR (Đạo diễn)
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
                String apiUrl = BASE_URL + "/discover/movie?api_key=" + API_KEY + "&language=vi-VN&with_crew="
                        + directorId + "&sort_by=popularity.desc";
                List<Map<String, Object>> apiMovies = fetchApiMovies(apiUrl, limit + 1);
                apiMovies.stream().filter(m -> !addedMovieIds.contains(m.get("tmdbId"))).limit(limit).forEach(m -> {
                    finalRecommendations.add(m);
                    addedMovieIds.add((Integer) m.get("tmdbId"));
                });

                if (finalRecommendations.size() >= 3) {
                    response.put("title", "🎥 Phim cùng Đạo diễn: " + directorName);
                    return finalRecommendations;
                }
            }
        } catch (Exception e) {
            /* Lỗi Lớp 4 */ }

        // ----- LỚP 5: FALLBACK (Phim Mới)
        return loadRecommendedFallback(tmdbId, addedMovieIds, limit);
    }

    // Helper: Lớp 5 (Fallback) - Dùng SortBy.NEW
    @Transactional
    public List<Map<String, Object>> loadRecommendedFallback(Integer tmdbId, Set<Integer> addedMovieIds, int limit) {
        String apiUrl;
        if (tmdbId != null)
            apiUrl = BASE_URL + "/movie/" + tmdbId + "/recommendations?api_key=" + API_KEY + "&language=vi-VN";
        else
            apiUrl = BASE_URL + "/trending/movie/week?api_key=" + API_KEY + "&language=vi-VN&page=1";

        Page<Movie> dbMovies = getNewMoviesFromDB(40);

        List<Map<String, Object>> merged = getMergedCarouselMovies(apiUrl, dbMovies, limit + 5,
                MovieService.SortBy.NEW);

        return merged.stream()
                .filter(m -> {
                    Integer mTmdbId = (Integer) m.get("tmdbId");
                    Integer mPkId = (Integer) m.get("id");
                    if (mTmdbId != null && addedMovieIds.contains(mTmdbId))
                        return false;
                    if (mTmdbId == null && mPkId != null && addedMovieIds.contains(mPkId))
                        return false;
                    return true;
                })
                .limit(limit)
                .collect(Collectors.toList());
    }

    // Helper: Lấy danh sách phim từ API (đã sync LAZY)
    @Transactional
    private List<Map<String, Object>> fetchApiMovies(String fullApiUrl, int limit) {
        List<Map<String, Object>> movies = new ArrayList<>();
        try {
            String resp = restTemplate.getForObject(fullApiUrl, String.class);
            JSONObject json = new JSONObject(resp);
            JSONArray results = json.optJSONArray("results");

            if (results != null) {
                for (int i = 0; i < Math.min(results.length(), limit); i++) {
                    JSONObject item = results.getJSONObject(i);
                    if (item.optBoolean("adult", false) || item.optDouble("vote_average", 0) < 0.1
                            || item.optInt("vote_count", 0) < 5)
                        continue;

                    String mediaType = item.optString("media_type", "movie");
                    if (mediaType.equals("movie") || mediaType.equals("tv")) {
                        int tmdbId = item.optInt("id");
                        if (tmdbId <= 0)
                            continue;

                        Movie movie = this.syncMovieFromList(item);
                        if (movie != null) {
                            Map<String, Object> map = this.convertToMap(movie);
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

    // Helper: Tạo Comparator để sort "công bằng" (HOT/NEW)
    private Comparator<Map<String, Object>> getRelevanceComparator(SortBy sortBy) {
        if (sortBy == SortBy.NEW) {
            // ----- Sắp xếp theo ngày ra mắt (Mới nhất lên đầu)
            return (m1, m2) -> {
                String date1 = (String) m1.getOrDefault("releaseDate", "1900-01-01");
                String date2 = (String) m2.getOrDefault("releaseDate", "1900-01-01");
                if (date1 == null || date1.isEmpty())
                    date1 = "1900-01-01";
                if (date2 == null || date2.isEmpty())
                    date2 = "1900-01-01";
                return date2.compareTo(date1);
            };
        }

        // ----- Mặc định (SortBy.HOT) - Thuật toán "chen chân"
        return (m1, m2) -> {
            double pop1 = (double) m1.getOrDefault("popularity_raw", 0.0);
            double pop2 = (double) m2.getOrDefault("popularity_raw", 0.0);

            double rating1 = 0.0;
            try {
                rating1 = Double.parseDouble((String) m1.get("rating"));
            } catch (Exception e) {
            }
            double rating2 = 0.0;
            try {
                rating2 = Double.parseDouble((String) m2.get("rating"));
            } catch (Exception e) {
            }

            double score1 = (pop1 > 0 ? Math.log10(pop1) : 0) * 0.8 + (rating1 * 0.6);
            double score2 = (pop2 > 0 ? Math.log10(pop2) : 0) * 0.8 + (rating2 * 0.6);

            return Double.compare(score2, score1);
        };
    }

    // Helper: Tìm Keyword ID quan trọng (cho Lớp 2)
    private Integer findKeywords(JSONArray keywords, Map<String, Integer> priorityMap) {
        if (keywords == null)
            return null;
        for (int i = 0; i < keywords.length(); i++) {
            JSONObject kw = keywords.getJSONObject(i);
            String name = kw.optString("name").toLowerCase();
            if (priorityMap.containsKey(name))
                return priorityMap.get(name);
        }
        return null;
    }

    // ---- 7. DB QUERY HELPERS ----

    // Lấy phim hot (rating cao nhất)
    public Page<Movie> getHotMoviesFromDB(int limit) {
        return movieRepository.findAllByOrderByRatingDesc(PageRequest.of(0, limit));
    }

    // Lấy phim mới (ngày ra mắt mới nhất)
    public Page<Movie> getNewMoviesFromDB(int limit) {
        return movieRepository.findAllByOrderByReleaseDateDesc(PageRequest.of(0, limit));
    }

    // Lấy phim theo TMDB Genre ID
    public Page<Movie> getMoviesByGenreFromDB(int tmdbGenreId, int limit, int page) {
        return movieRepository.findAllByGenres_TmdbGenreId(tmdbGenreId,
                PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "rating")));
    }

    // ---- 8. SEARCH & SYNC UTILS ----

    // Tìm kiếm phim theo tiêu đề (Native Query)
    @Transactional
    public List<Movie> searchMoviesByTitle(String title) {
        return movieRepository.findByTitleContainingIgnoreCase(title);
    }

    // Lấy một danh sách phim từ DB dựa trên tmdbIds
    @Transactional
    public Map<Integer, Map<String, Object>> getMoviesByTmdbIds(List<Integer> tmdbIds) {
        if (tmdbIds == null || tmdbIds.isEmpty())
            return Collections.emptyMap();
        List<Movie> dbMovies = movieRepository.findByTmdbIdIn(tmdbIds);
        return dbMovies.stream().collect(Collectors.toMap(Movie::getTmdbId, movie -> convertToMap(movie)));
    }

    // Đồng bộ nhanh một danh sách tmdbIds (LAZY)
    @Transactional
    public void syncTmdbIds(List<Integer> tmdbIds) {
        if (tmdbIds == null || tmdbIds.isEmpty())
            return;
        List<Integer> existingIds = movieRepository.findTmdbIdsIn(tmdbIds);
        List<Integer> idsToFetch = new ArrayList<>();
        for (Integer id : tmdbIds)
            if (!existingIds.contains(id))
                idsToFetch.add(id);
        if (idsToFetch.isEmpty())
            return;

        for (Integer id : idsToFetch) {
            try {
                String url = BASE_URL + "/movie/" + id + "?api_key=" + API_KEY + "&language=vi-VN&include_adult=false";
                String resp = restTemplate.getForObject(url, String.class);
                if (resp != null)
                    syncMovieFromList(new JSONObject(resp));
            } catch (Exception e) {
                System.err.println("Lỗi sync nhanh ID " + id + ": " + e.getMessage());
            }
        }
    }

    // ---- 9. CONVERTERS & FORMATTERS ----

    // Chuyển đổi Movie Entity sang Map<String, Object> (Sử dụng PK là 'id')
    public Map<String, Object> convertToMap(Movie movie) {
        if (movie == null)
            return null;
        Map<String, Object> map = new HashMap<>();

        // ----- Dữ liệu cốt lõi
        map.put("id", movie.getMovieID()); // PK của DB
        map.put("tmdbId", movie.getTmdbId());
        map.put("title", movie.getTitle());
        map.put("overview", movie.getDescription());
        map.put("rating", String.format("%.1f", movie.getRating()));

        // ----- Logic Poster & Backdrop
        String poster = "/images/placeholder.jpg";
        if (movie.getPosterPath() != null && !movie.getPosterPath().isEmpty()) {
            if (movie.getPosterPath().startsWith("http"))
                poster = movie.getPosterPath();
            else
                poster = "https://image.tmdb.org/t/p/w500" + movie.getPosterPath();
        } else if (movie.getUrl() != null && (movie.getUrl().startsWith("http")
                && (movie.getUrl().endsWith(".jpg") || movie.getUrl().endsWith(".png")))) {
            poster = movie.getUrl();
        }
        map.put("poster", poster);

        String backdrop = "/images/placeholder.jpg";
        if (movie.getBackdropPath() != null && !movie.getBackdropPath().isEmpty()) {
            if (movie.getBackdropPath().startsWith("http"))
                backdrop = movie.getBackdropPath();
            else
                backdrop = "https://image.tmdb.org/t/p/original" + movie.getBackdropPath();
        }
        map.put("backdrop", backdrop);

        // ----- Dữ liệu phụ
        if (movie.getReleaseDate() != null) {
            map.put("year", new SimpleDateFormat("yyyy").format(movie.getReleaseDate()));
            map.put("releaseDate", new SimpleDateFormat("yyyy-MM-dd").format(movie.getReleaseDate()));
        } else {
            map.put("year", "N/A");
            map.put("releaseDate", "");
        }

        map.put("runtime", (movie.getDuration() > 0) ? movie.getDuration() : "—");
        map.put("director",
                (movie.getDirector() != null && !movie.getDirector().equals("N/A")) ? movie.getDirector() : "—");
        map.put("country",
                (movie.getCountry() != null && !movie.getCountry().isEmpty()) ? movie.getCountry() : "Quốc gia");
        map.put("language",
                (movie.getLanguage() != null && !movie.getLanguage().equals("N/A")) ? movie.getLanguage() : "—");

        NumberFormat fmt = NumberFormat.getCurrencyInstance(Locale.US);
        fmt.setMaximumFractionDigits(0);
        map.put("budget", (movie.getBudget() != null && movie.getBudget() > 0) ? fmt.format(movie.getBudget()) : "—");
        map.put("revenue",
                (movie.getRevenue() != null && movie.getRevenue() > 0) ? fmt.format(movie.getRevenue()) : "—");

        List<String> genres = new ArrayList<>();
        if (movie.getGenres() != null)
            movie.getGenres().forEach(g -> genres.add(g.getName()));
        map.put("genres", genres);

        return map;
    }

    // Overload: Chuyển đổi Movie Entity sang Map<String, Object> và thêm role_info
    public Map<String, Object> convertToMap(Movie movie, String role) {
        Map<String, Object> map = this.convertToMap(movie);
        if (map != null && role != null && !role.isEmpty())
            map.put("role_info", role);
        return map;
    }

    // Chuyển đổi Person Entity sang Map<String, Object> (Sử dụng PK là 'id')
    public Map<String, Object> convertToMap(Person p) {
        if (p == null)
            return null;
        Map<String, Object> map = new HashMap<>();

        map.put("id", p.getPersonID());
        map.put("tmdbId", p.getTmdbId());
        map.put("name", p.getFullName());
        map.put("avatar", p.getProfilePath() != null ? "https://image.tmdb.org/t/p/w500" + p.getProfilePath()
                : "/images/placeholder-person.jpg");

        map.put("biography", (p.getBio() != null && !p.getBio().equals("N/A")) ? p.getBio() : "Đang cập nhật...");
        map.put("birthday", p.getBirthday() != null ? new SimpleDateFormat("dd-MM-yyyy").format(p.getBirthday()) : "—");
        map.put("place_of_birth",
                (p.getPlaceOfBirth() != null && !p.getPlaceOfBirth().isEmpty()) ? p.getPlaceOfBirth() : "—");
        map.put("known_for_department",
                (p.getKnownForDepartment() != null && !p.getKnownForDepartment().equals("N/A"))
                        ? p.getKnownForDepartment()
                        : "—");
        map.put("popularity", p.getPopularity() != null ? p.getPopularity() : 0.0);
        return map;
    }

    // Parse chuỗi ngày tháng yyyy-MM-dd sang Date.
    private Date parseDate(String dateString) {
        if (dateString == null || dateString.isEmpty() || dateString.equals("null"))
            return null;
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(dateString);
        } catch (ParseException e) {
            return null;
        }
    }

    // ---- 10. TRAILER & LOGO FINDERS ----

    // Tìm Trailer Key tốt nhất (Gọi bằng Movie ID (PK))
    public String findBestTrailerKey(int movieID) {
        Movie movie = movieRepository.findById(movieID).orElse(null);
        if (movie == null || movie.getTmdbId() == null)
            return null;

        List<Map<String, Object>> trailers = findTrailers(movie.getTmdbId(), 1);
        if (trailers.isEmpty())
            return null;
        return (String) trailers.get(0).get("key");
    }

    // Lấy danh sách Trailer từ TMDB (Gọi bằng TMDB ID)
    public List<Map<String, Object>> findTrailers(int tmdbId, int limit) {
        List<Map<String, Object>> trailers = new ArrayList<>();
        Set<String> existingKeys = new HashSet<>();
        try {
            String urlVi = BASE_URL + "/movie/" + tmdbId + "/videos?api_key=" + API_KEY
                    + "&language=vi-VN&include_adult=false";
            String respVi = restTemplate.getForObject(urlVi, String.class);
            parseAndAddTrailers(respVi, trailers, existingKeys, limit);
        } catch (Exception e) {
            System.err.println("Lỗi findTrailers (vi-VN): " + e.getMessage());
        }
        if (trailers.size() < limit) {
            try {
                String urlEn = BASE_URL + "/movie/" + tmdbId + "/videos?api_key=" + API_KEY
                        + "&language=en-US&include_adult=false";
                String respEn = restTemplate.getForObject(urlEn, String.class);
                parseAndAddTrailers(respEn, trailers, existingKeys, limit);
            } catch (Exception e) {
                System.err.println("Lỗi findTrailers (en-US): " + e.getMessage());
            }
        }
        return trailers;
    }

    // ----- Helper: Parse JSON và thêm Trailer
    private void parseAndAddTrailers(String jsonResponse, List<Map<String, Object>> trailers, Set<String> existingKeys,
            int limit) {
        if (jsonResponse == null || jsonResponse.isEmpty())
            return;
        try {
            JSONArray results = new JSONObject(jsonResponse).optJSONArray("results");
            if (results != null) {
                for (int i = 0; i < results.length(); i++) {
                    if (trailers.size() >= limit)
                        break;
                    JSONObject v = results.getJSONObject(i);
                    String site = v.optString("site");
                    String type = v.optString("type");
                    String key = v.optString("key");
                    if ("YouTube".equals(site) && ("Trailer".equals(type) || "Teaser".equals(type)) && key != null
                            && !existingKeys.contains(key)) {
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

    // Tìm Logo Path tốt nhất (Gọi bằng Movie ID (PK))
    public String findBestLogoPath(int movieID) {
        Movie movie = movieRepository.findById(movieID).orElse(null);
        if (movie == null || movie.getTmdbId() == null)
            return null;

        Integer tmdbId = movie.getTmdbId();

        try {
            String url = BASE_URL + "/movie/" + tmdbId + "/images?api_key=" + API_KEY
                    + "&include_image_language=vi,en,null";
            String resp = restTemplate.getForObject(url, String.class);
            JSONObject json = new JSONObject(resp);
            JSONArray logos = json.optJSONArray("logos");
            if (logos == null || logos.length() == 0)
                return null;

            JSONObject bestLogo = null;

            // ----- Ưu tiên "vi" -> "en" -> đầu tiên
            for (int i = 0; i < logos.length(); i++) {
                if ("vi".equals(logos.getJSONObject(i).optString("iso_639_1"))) {
                    bestLogo = logos.getJSONObject(i);
                    break;
                }
            }
            if (bestLogo == null) {
                for (int i = 0; i < logos.length(); i++) {
                    if ("en".equals(logos.getJSONObject(i).optString("iso_639_1"))) {
                        bestLogo = logos.getJSONObject(i);
                        break;
                    }
                }
            }
            if (bestLogo == null)
                bestLogo = logos.getJSONObject(0);

            return bestLogo.optString("file_path");
        } catch (Exception e) {
            System.err.println("Lỗi API findBestLogoPath (ID: " + tmdbId + "): " + e.getMessage());
            return null;
        }
    }

    // ---- 11. CRUD CƠ BẢN ----

    public List<Movie> getAllMovies() {
        return movieRepository.findAll();
    }

    public Movie getMovieById(int movieId) {
        return movieRepository.findById(movieId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phim với ID: " + movieId));
    }

    @Transactional
    public void deleteMovie(int movieId) {
        movieRepository.deleteById(movieId);
    }

    @Transactional
    public Movie importFromTmdb(int tmdbId) {
        return getMovieOrSync(tmdbId);
    }

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

    // ---- 12. ADVANCED FILTER LOGIC (MỚI) ----

    /**
     * Tìm phim dựa trên các bộ lọc động từ AI (Phase 1)
     */
    @Transactional(readOnly = true) // readOnly = true để tăng tốc độ query SELECT
    public List<Movie> findMoviesByFilters(MovieSearchFilters filters) {
        System.out.println("🔵 MovieService: Finding movies by filters: " + filters.toString());

        // 1. Tạo Specification (bộ điều kiện WHERE động)
        Specification<Movie> spec = createMovieSpecification(filters);

        // 2. Thực thi query
        // Chúng ta dùng Sort mặc định theo Rating giảm dần
        List<Movie> results = movieRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "rating"));

        System.out.println("🔵 MovieService: Found " + results.size() + " movies.");
        return results;
    }

    /**
     * Helper xây dựng Specification (bộ điều kiện WHERE)
     */
    private Specification<Movie> createMovieSpecification(MovieSearchFilters filters) {
        // (root, query, cb) -> cb = CriteriaBuilder
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // QUAN TRỌNG: Tránh N+1 query khi join
            // Chúng ta báo JPA fetch các bảng liên quan trong 1 lần query
            if (filters.getGenres() != null && !filters.getGenres().isEmpty()) {
                root.fetch("genres", jakarta.persistence.criteria.JoinType.LEFT);
            }
            if (filters.getActor() != null || filters.getDirector() != null) {
                root.fetch("persons", jakarta.persistence.criteria.JoinType.LEFT);
            }
            // Đảm bảo không bị trùng lặp kết quả khi JOIN
            query.distinct(true);

            // 1. Filter: Keyword (Title/Description)
            if (filters.getKeyword() != null && !filters.getKeyword().isEmpty()) {
                String likePattern = "%" + filters.getKeyword() + "%";
                // Tìm ở Title HOẶC Description
                predicates.add(cb.or(
                        cb.like(root.get("title"), likePattern),
                        cb.like(root.get("description"), likePattern)));
            }

            // 2. Filter: Country
            if (filters.getCountry() != null && !filters.getCountry().isEmpty()) {
                predicates.add(cb.like(root.get("country"), "%" + filters.getCountry() + "%"));
            }

            // 3. Filter: Director
            if (filters.getDirector() != null && !filters.getDirector().isEmpty()) {
                predicates.add(cb.like(root.get("director"), "%" + filters.getDirector() + "%"));
            }

            // 4. Filter: Year From (Năm >=)
            if (filters.getYearFrom() != null) {
                try {
                    Date dateFrom = new SimpleDateFormat("yyyy-MM-dd").parse(filters.getYearFrom() + "-01-01");
                    predicates.add(cb.greaterThanOrEqualTo(root.get("releaseDate"), dateFrom));
                } catch (Exception e) {
                    /* Bỏ qua nếu năm lỗi */ }
            }

            // 5. Filter: Year To (Năm <=)
            if (filters.getYearTo() != null) {
                try {
                    Date dateTo = new SimpleDateFormat("yyyy-MM-dd").parse(filters.getYearTo() + "-12-31");
                    predicates.add(cb.lessThanOrEqualTo(root.get("releaseDate"), dateTo));
                } catch (Exception e) {
                    /* Bỏ qua nếu năm lỗi */ }
            }

            // 6. Filter: Min Rating
            if (filters.getMinRating() != null && filters.getMinRating() > 0) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("rating"), filters.getMinRating()));
            }

            // 7. Filter: Duration
            if (filters.getMinDuration() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("duration"), filters.getMinDuration()));
            }
            if (filters.getMaxDuration() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("duration"), filters.getMaxDuration()));
            }

            // 8. Filter: Genres (JOIN)
            if (filters.getGenres() != null && !filters.getGenres().isEmpty()) {
                Join<Movie, Genre> genreJoin = root.join("genres");

                // THAY ĐỔI (VĐ 9): Dùng 'OR' thay vì 'AND'
                // User muốn phim "tình cảm HOẶC lãng mạn", không phải "tình cảm VÀ lãng mạn"
                List<Predicate> genrePredicates = new ArrayList<>();
                for (String genreName : filters.getGenres()) {
                    genrePredicates.add(cb.like(genreJoin.get("name"), "%" + genreName + "%"));
                }
                predicates.add(cb.or(genrePredicates.toArray(new Predicate[0])));
            }

            // 9. Filter: Actor (JOIN)
            if (filters.getActor() != null && !filters.getActor().isEmpty()) {
                Join<Movie, Person> personJoin = root.join("persons");
                predicates.add(cb.like(personJoin.get("fullName"), "%" + filters.getActor() + "%"));
            }

            // Kết hợp tất cả điều kiện bằng AND
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ---- 13. AI AGENT HELPERS (PHASE 3) ----

    /**
     * [PHASE 3] Lấy 5 phim hot nhất từ DB cho AI (Persona "Lười biếng")
     */
    @Transactional(readOnly = true)
    public List<Movie> getHotMoviesForAI(int limit) {
        // Hàm getHotMoviesFromDB đã có sẵn, chỉ cần lấy content
        return movieRepository.findAllByOrderByRatingDesc(PageRequest.of(0, limit)).getContent();
    }

    /**
     * [PHASE 3] Lấy Diễn viên từ Tên phim (Persona "Tò mò")
     */
    @Transactional(readOnly = true)
    public Set<Person> findPersonsByMovieTitle(String title) {
        // Dùng hàm searchMoviesByTitle (Native Query) đã có sẵn
        List<Movie> movies = searchMoviesByTitle(title);
        if (movies.isEmpty()) {
            return Collections.emptySet();
        }
        // Lấy phim đầu tiên (chính xác nhất) và EAGER load Persons
        Movie movie = getMovieByIdOrSync(movies.get(0).getMovieID());
        return movie.getPersons();
    }

    /**
     * [PHASE 3] Lấy Đạo diễn từ Tên phim (Persona "Tò mò")
     */
    @Transactional(readOnly = true)
    public String findDirectorByMovieTitle(String title) {
        List<Movie> movies = searchMoviesByTitle(title);
        if (movies.isEmpty()) {
            return null;
        }
        // Lấy phim đầu tiên (chính xác nhất) và EAGER load Director
        Movie movie = getMovieByIdOrSync(movies.get(0).getMovieID());
        return movie.getDirector();
    }

    /**
     * [PHASE 6.1] Tìm phim theo Tên VÀ Ngữ cảnh (Đạo diễn/Diễn viên)
     * Giúp phân biệt "Phim Mai" (Trấn Thành) vs "Phim Mai" (Bob Carruthers)
     */
    @Transactional(readOnly = true)
    public Movie findMovieByTitleAndContext(String title, String contextName) {
        // 1. Tìm tất cả phim có tên khớp (gần đúng)
        List<Movie> candidates = searchMoviesByTitle(title);
        if (candidates.isEmpty())
            return null;

        if (contextName == null || contextName.isEmpty()) {
            return getMovieByIdOrSync(candidates.get(0).getMovieID()); // Fallback: Lấy phim đầu tiên
        }

        String contextLower = contextName.toLowerCase();

        // 2. Lọc trong danh sách candidates
        for (Movie m : candidates) {
            Movie fullMovie = getMovieByIdOrSync(m.getMovieID()); // Eager load để lấy Director/Persons

            // Check Đạo diễn
            if (fullMovie.getDirector() != null && fullMovie.getDirector().toLowerCase().contains(contextLower)) {
                return fullMovie;
            }

            // Check Diễn viên (Duyệt qua Set<Person>)
            if (fullMovie.getPersons() != null) {
                for (Person p : fullMovie.getPersons()) {
                    if (p.getFullName().toLowerCase().contains(contextLower)) {
                        return fullMovie;
                    }
                }
            }
        }

        // 3. Nếu không khớp ngữ cảnh nào, trả về phim đầu tiên (hoặc null tùy strategy)
        return getMovieByIdOrSync(candidates.get(0).getMovieID());
    }

    // [MỚI] Tìm phim trong DB dựa trên tên diễn viên/đạo diễn
    @Transactional(readOnly = true)
    public List<Movie> searchMoviesByPersonName(String name) {
        List<Person> persons = personRepository.findByFullNameContainingIgnoreCase(name);
        Set<Movie> movies = new HashSet<>();
        for (Person p : persons) {
            // Vì fetch type có thể là LAZY, gọi size() để đảm bảo Hibernate load dữ liệu
            p.getMovies().size();
            movies.addAll(p.getMovies());
        }
        return new ArrayList<>(movies);
    }

    // [MỚI] Tìm danh sách Person theo tên (để Controller tự xử lý role)
    public List<Person> searchPersons(String name) {
        return personRepository.findByFullNameContainingIgnoreCase(name);
    }

    // ----- Helper: Map DTO sang Entity (cho CRUD)
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