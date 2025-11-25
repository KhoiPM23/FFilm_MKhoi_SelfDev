package com.example.project.service;

import com.example.project.dto.MovieRequest;
import com.example.project.model.*;
// MỚI
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable; 
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils; // [Fix lỗi hasText]
import jakarta.persistence.criteria.Predicate;

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
import com.example.project.model.Collection;

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
    private ProductionCompanyRepository companyRepository;
    @Autowired
    private CollectionRepository collectionRepository;
    @Autowired
    private MoviePersonRepository moviePersonRepository;


    @Autowired
    private RestTemplate restTemplate;

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";

    // Cho phép Controller truy cập Repository
    public MovieRepository getMovieRepository() {
        return movieRepository;
    }

    // [MỚI] Bảng Map ngôn ngữ chuyển từ Controller sang Service
    private static final Map<String, String> LANGUAGE_MAP = new HashMap<>();
    static {
        // Châu Á
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
        // Châu Âu
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
        // Khác
        LANGUAGE_MAP.put("qu", "Tiếng Quechua");
        LANGUAGE_MAP.put("af", "Tiếng Afrikaans");
        LANGUAGE_MAP.put("sw", "Tiếng Swahili");
        LANGUAGE_MAP.put("zu", "Tiếng Zulu");
        LANGUAGE_MAP.put("xh", "Tiếng Xhosa");
        LANGUAGE_MAP.put("am", "Tiếng Amharic");
        LANGUAGE_MAP.put("yo", "Tiếng Yoruba");
        LANGUAGE_MAP.put("ha", "Tiếng Hausa");
        LANGUAGE_MAP.put("ig", "Tiếng Igbo");
        LANGUAGE_MAP.put("mi", "Tiếng Māori");
        LANGUAGE_MAP.put("sm", "Tiếng Samoa");
        LANGUAGE_MAP.put("la", "Tiếng Latin");
        LANGUAGE_MAP.put("eo", "Tiếng Esperanto");
        // Mã đặc biệt
        LANGUAGE_MAP.put("xx", "Không có ngôn ngữ");
        LANGUAGE_MAP.put("cn", "Tiếng Quảng Đông");
    }

    private String getLanguageName(String code) {
        if (code == null)
            return "N/A";
        return LANGUAGE_MAP.getOrDefault(code, code.toUpperCase());
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

    /**
     * [CORE] Hàm xử lý phim từ danh sách TMDB
     * Logic: Lọc Rác -> Kiểm tra tồn tại -> Ghi đè (Update) hoặc Tạo mới (Create)
     */
    @Transactional
    public Movie syncMovieFromList(JSONObject jsonItem) {
        int tmdbId = jsonItem.optInt("id");
        if (tmdbId <= 0) return null;

        // --- BỘ LỌC CHẤT LƯỢNG ---
        // --- BỘ LỌC CHẤT LƯỢNG (CẬP NHẬT) ---
        String posterPath = jsonItem.optString("poster_path", null);
        String backdropPath = jsonItem.optString("backdrop_path", null);

        // [LOGIC MỚI] Bắt buộc phải có cả Poster VÀ Backdrop
        // Nếu thiếu 1 trong 2 thì bỏ qua luôn (return null)
        if (!isValidImage(posterPath) || !isValidImage(backdropPath)) {
            // System.out.println("⚠️ Bỏ qua phim ID " + tmdbId + " vì thiếu ảnh.");
            return null;
        }

        // --- [CẬP NHẬT] ĐỒNG BỘ LOGIC BỘ LỌC ---
        boolean isAdult = jsonItem.optBoolean("adult", false);
        int voteCount = jsonItem.optInt("vote_count", 0);
        String lang = jsonItem.optString("original_language", "en");
        double voteAverage = jsonItem.optDouble("vote_average", 0.0);

        // 1. Rule 18+
        if (isAdult && voteCount < 50) return null;

        // 2. Rule Việt Nam & Quốc tế
        boolean isVietnamese = "vi".equalsIgnoreCase(lang);
        if (!isAdult && !isVietnamese && voteCount < 5) return null;
        // -------------------------

        // --- XỬ LÝ GHI ĐÈ / TẠO MỚI ---
        Movie movie;
        Optional<Movie> existing = movieRepository.findByTmdbId(tmdbId);

        if (existing.isPresent()) {
            movie = existing.get();
            // System.out.println("🔄 [UPDATE] ID: " + tmdbId + " | Rating cũ: " + movie.getRating() + " -> Mới: " + voteAverage);
        } else {
            movie = new Movie();
            movie.setTmdbId(tmdbId);
            // System.out.println("✳️ [NEW] ID: " + tmdbId);
        }

        // --- CẬP NHẬT THÔNG TIN (Cho cả mới và cũ) ---
        movie.setTitle(jsonItem.optString("title", jsonItem.optString("name", "N/A")));
        movie.setDescription(jsonItem.optString("overview", ""));
        movie.setPosterPath(posterPath);
        movie.setBackdropPath(jsonItem.optString("backdrop_path", null));
        
        // Cập nhật Rating mới nhất (QUAN TRỌNG: Ghi đè rating cũ)
        movie.setRating((float) voteAverage);
        // movie.setVoteCount(voteCount); // Nếu Entity Movie có field này thì bỏ comment

        // Xử lý ngày phát hành
        String dateStr = jsonItem.optString("release_date", jsonItem.optString("first_air_date"));
        if (dateStr != null && !dateStr.isEmpty()) {
            try {
                // Giả sử bạn có hàm parseDate hoặc dùng SimpleDateFormat
                // movie.setReleaseDate(...); 
                movie.setReleaseDate(java.sql.Date.valueOf(dateStr)); // Cách đơn giản nếu chuỗi chuẩn yyyy-MM-dd
            } catch (Exception e) { }
        }

        // Xử lý Thể loại (Map lại nếu TMDB thay đổi)
        JSONArray genreIdsJson = jsonItem.optJSONArray("genre_ids");
        if (genreIdsJson != null && genreIdsJson.length() > 0) {
            List<Integer> genreIds = new ArrayList<>();
            for (int i = 0; i < genreIdsJson.length(); i++) {
                genreIds.add(genreIdsJson.optInt(i));
            }
            List<Genre> genres = genreRepository.findByTmdbGenreIdIn(genreIds);
            movie.setGenres(new HashSet<>(genres));
        }
        
        // Mặc định cho các trường bắt buộc khác nếu tạo mới
        if (movie.getDirector() == null) movie.setDirector("Updating...");
        if (movie.getCountry() == null) movie.setCountry("N/A");

        return movieRepository.save(movie);
    }


    // ---- 4. CORE SYNC LOGIC (PERSON) ----

    // Lấy Person theo personID (PK), tự động sync đầy đủ (EAGER) nếu cần.
    @Transactional
    public Person getPersonByIdOrSync(int personID) {
        Optional<Person> existing = personRepository.findById(personID);
        if (existing.isEmpty()) return null;

        Person person = existing.get();
        
        // ✅ LUÔN TRẢ VỀ PERSON (Dù có N/A hay không)
        // Nếu có tmdbId và Bio = "N/A" thì thử fetch (nhưng không crash nếu API off)
        if (person.getTmdbId() != null && "N/A".equals(person.getBio())) {
            try {
                Person updated = fetchAndSavePersonDetail(person.getTmdbId(), person);
                return updated != null ? updated : person; // ← Fallback về person cũ nếu API lỗi
            } catch (Exception e) {
                System.err.println("⚠️ API off, dùng data cũ cho Person ID: " + personID);
                return person; // ← Trả về person "cụt" thay vì null
            }
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
        if (tmdbId <= 0) return null;

        Optional<Person> existing = personRepository.findByTmdbId(tmdbId);
        if (existing.isPresent()) return existing.get();

        // [SỬA ĐỔI]: Thay vì tạo bản cụt, gọi ngay hàm lấy chi tiết để lưu full data
        System.out.println("⬇️ [Person EAGER] Tải chi tiết ngay lập tức cho ID: " + tmdbId);
        
        // Gọi hàm fetchAndSavePersonDetail (đã có sẵn bên dưới trong file này)
        // Hàm này sẽ gọi API /person/{id} lấy bio, birthday, place_of_birth...
        return fetchAndSavePersonDetail(tmdbId, null);
    }

    // ---- 5. CORE SYNC HELPERS (PRIVATE) ----

    @Transactional
    public Movie fetchAndSaveMovieDetail(int tmdbId, Movie movieToUpdate) {
        try {
            // [QUAN TRỌNG] Thêm "release_dates" vào append_to_response
            // [QUAN TRỌNG] Đổi include_adult=true để lấy dữ liệu gốc nếu phim đó đã qua vòng lọc ở trên
            String url = BASE_URL + "/movie/" + tmdbId + "?api_key=" + API_KEY
                    + "&language=vi-VN&append_to_response=credits,videos,images,keywords,release_dates"
                    + "&include_image_language=vi,en,null&include_video_language=vi,en,null&include_adult=true";

            String resp = restTemplate.getForObject(url, String.class);
            JSONObject json = new JSONObject(resp);

            // --- [CẬP NHẬT] BỘ LỌC CHI TIẾT (Ảnh & Thời lượng) ---
            
            // 1. Kiểm tra Thời lượng (Runtime)
            // Phim phải có thời lượng > 0 phút. (Trừ phim sắp chiếu chưa có thông tin)
            int runtime = json.optInt("runtime", 0);
            String status = json.optString("status", "");
            
            // Lưu ý: Phim "Planned" hoặc "Rumored" có thể chưa có runtime, nhưng phim "Released" bắt buộc phải có.
            // Ở đây ta chặn cứng runtime <= 0 để đảm bảo chất lượng xem.
            if (runtime <= 0) {
                // System.out.println("❌ Bỏ qua ID " + tmdbId + " - Thời lượng 0 phút.");
                return null;
            }

            // --- [LOGIC MỚI] KIỂM TRA ẢNH NGAY SAU KHI GỌI API ---
            String poster = json.optString("poster_path", null);
            String backdrop = json.optString("backdrop_path", null);

            // Nếu thiếu 1 trong 2 ảnh -> KHÔNG LƯU, return null ngay lập tức
            if (!isValidImage(poster) || !isValidImage(backdrop)) {
                System.out.println("❌ [Filter] Bỏ qua ID " + tmdbId + " - Thiếu Poster hoặc Banner.");
                return null; 
            }

            Movie movie = (movieToUpdate != null) ? movieToUpdate : new Movie();

            // Basic Info
            movie.setTmdbId(tmdbId);
            movie.setTitle(json.optString("title"));
            movie.setDescription(json.optString("overview"));
            movie.setPosterPath(json.optString("poster_path"));
            movie.setBackdropPath(json.optString("backdrop_path"));
            movie.setReleaseDate(parseDate(json.optString("release_date")));
            movie.setDuration(json.optInt("runtime", 0));
            movie.setRating((float) json.optDouble("vote_average", 0.0));
            movie.setBudget(json.optLong("budget", 0));
            movie.setRevenue(json.optLong("revenue", 0));

            // Extra Info
            movie.setPopularity(json.optDouble("popularity", 0.0));
            movie.setVoteCount(json.optInt("vote_count", 0));
            movie.setLanguage(getLanguageName(json.optString("original_language")));

            // [MỚI] Lấy Content Rating (T13, T16...)
            movie.setContentRating(extractContentRating(json));

            // Media Cache
            String trailerKey = findBestTrailerKeyFromJSON(json);
            if (trailerKey != null)
                movie.setTrailerKey(trailerKey);

            String logoPath = findBestLogoFromJSON(json);
            if (logoPath != null)
                movie.setLogoPath(logoPath);

            // Collection
            JSONObject colJson = json.optJSONObject("belongs_to_collection");
            if (colJson != null && colJson.optInt("id") > 0) {
                int colId = colJson.optInt("id");
                Collection collection = collectionRepository.findByTmdbId(colId).orElseGet(() -> {
                    Collection newCol = new Collection();
                    newCol.setTmdbId(colId);
                    newCol.setName(colJson.optString("name"));
                    newCol.setPosterPath(colJson.optString("poster_path"));
                    newCol.setBackdropPath(colJson.optString("backdrop_path"));
                    return collectionRepository.save(newCol);
                });
                movie.setCollection(collection);
            }

            // Companies
            JSONArray companiesJson = json.optJSONArray("production_companies");
            if (companiesJson != null) {
                Set<ProductionCompany> companies = new HashSet<>();
                for (int i = 0; i < companiesJson.length(); i++) {
                    JSONObject cJson = companiesJson.getJSONObject(i);
                    int cId = cJson.optInt("id");
                    if (cId == 0)
                        continue;
                    ProductionCompany company = companyRepository.findByTmdbId(cId).orElseGet(() -> {
                        ProductionCompany newComp = new ProductionCompany();
                        newComp.setTmdbId(cId);
                        newComp.setName(cJson.optString("name"));
                        newComp.setLogoPath(cJson.optString("logo_path"));
                        newComp.setOriginCountry(cJson.optString("origin_country"));
                        return companyRepository.save(newComp);
                    });
                    companies.add(company);
                }
                movie.setProductionCompanies(companies);
            }

            // Country
            JSONArray countries = json.optJSONArray("production_countries");
            if (countries != null && countries.length() > 0) {
                movie.setCountry(countries.getJSONObject(0).optString("name"));
            }

            // Genres
            JSONArray genresJson = json.optJSONArray("genres");
            if (genresJson != null) {
                List<Integer> genreIds = new ArrayList<>();
                for (int i = 0; i < genresJson.length(); i++)
                    genreIds.add(genresJson.getJSONObject(i).optInt("id"));
                movie.setGenres(new HashSet<>(genreRepository.findByTmdbGenreIdIn(genreIds)));
            }

            // Credits
            JSONObject credits = json.optJSONObject("credits");
            if (credits != null) {
                Movie savedMovie = movieRepository.save(movie); 
                
                // 1. Xử lý Crew (Đạo diễn)
                JSONArray crew = credits.optJSONArray("crew");
                if (crew != null) {
                    for (int i = 0; i < crew.length(); i++) {
                        JSONObject pJson = crew.getJSONObject(i);
                        if ("Director".equals(pJson.optString("job"))) {
                            Person p = getPersonPartialOrSync(pJson);
                            if(p != null) {
                                savedMovie.setDirector(p.getFullName());
                                saveMoviePersonRole(savedMovie.getMovieID(), p.getPersonID(), "Director", "Director");
                            }
                        }
                    }
                }

                // 2. Xử lý Cast (Diễn viên)
                JSONArray cast = credits.optJSONArray("cast");
                if (cast != null) {
                    for (int i = 0; i < Math.min(cast.length(), 20); i++) {
                        JSONObject pJson = cast.getJSONObject(i);
                        Person p = getPersonPartialOrSync(pJson);
                        if (p != null) {
                            // [ĐÃ XÓA] persons.add(p);
                            String character = pJson.optString("character");
                            saveMoviePersonRole(savedMovie.getMovieID(), p.getPersonID(), character, "Acting");
                        }
                    }
                }
                // [ĐÃ XÓA] savedMovie.setPersons(persons);
                return movieRepository.save(savedMovie);
            }

            return movieRepository.save(movie);

        } catch (Exception e) {
            System.err.println("❌ Lỗi Sync Movie ID " + tmdbId + ": " + e.getMessage());
            return null;
        }
    }


    // Helper check ảnh hợp lệ
    private boolean isValidImage(String path) {
        return path != null && !path.isEmpty() && !"null".equals(path) && path.length() > 4;
    }
    // [MỚI] Lấy danh sách phim miễn phí từ DB
    public Page<Movie> getFreeMoviesFromDB(int limit, int page) {
        // Sắp xếp theo ngày phát hành giảm dần (phim mới nhất lên đầu) hoặc rating
        PageRequest pageable = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "releaseDate"));
        return movieRepository.findByIsFreeTrue(pageable);
    }


    @Transactional
    public void forceUpdateMovie(int tmdbId) {
        // Tìm phim trong DB
        Optional<Movie> existing = movieRepository.findByTmdbId(tmdbId);
        // Nếu có thì update, chưa có thì tạo mới (tham số thứ 2 là null)
        fetchAndSaveMovieDetail(tmdbId, existing.orElse(null));
    }

    // --- [LOGIC MỚI] HÀM BÓC TÁCH CONTENT RATING ---
    private String extractContentRating(JSONObject json) {
        JSONObject releaseDates = json.optJSONObject("release_dates");
        if (releaseDates == null)
            return "T"; // Mặc định T (All ages)

        JSONArray results = releaseDates.optJSONArray("results");
        if (results == null)
            return "T";

        String rating = "T";

        // Ưu tiên tìm Rating của Mỹ (US) để map chuẩn
        for (int i = 0; i < results.length(); i++) {
            JSONObject item = results.getJSONObject(i);
            if ("US".equals(item.optString("iso_3166_1"))) {
                JSONArray dates = item.optJSONArray("release_dates");
                if (dates != null && dates.length() > 0) {
                    // Lấy certification đầu tiên khác rỗng
                    for (int j = 0; j < dates.length(); j++) {
                        String cert = dates.getJSONObject(j).optString("certification");
                        if (!cert.isEmpty()) {
                            return mapCertificationToVN(cert);
                        }
                    }
                }
            }
        }
        return rating;
    }

    private String mapCertificationToVN(String cert) {
        // Map chuẩn US -> VN (FPT Style)
        switch (cert.toUpperCase()) {
            case "G":
            case "TV-G":
            case "TV-Y":
            case "TV-Y7":
                return "P"; // Phổ biến
            case "PG":
            case "PG-13":
            case "TV-PG":
                return "T13"; // 13+
            case "R":
            case "TV-14":
                return "T16"; // 16+
            case "NC-17":
            case "TV-MA":
                return "T18"; // 18+
            default:
                return "T13";
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
            return personToUpdate;
        }
    }


    // ---- 6. CAROUSEL / MERGE LOGIC ----

    // Enum định nghĩa tiêu chí sort cho carousel.
    public enum SortBy {
        HOT,
        NEW
    }

    /**
     * [REFACTOR] Xử lý danh sách phim để hiển thị (Convert & Limit)
     * Loại bỏ hoàn toàn logic merge API cũ.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> processMovieList(List<Movie> movies, int limit) {
        return movies.stream()
                .limit(limit)
                .map(this::convertToMap)
                .collect(Collectors.toList());
    }

    /**
     * [SMART FALLBACK V2] Phim liên quan: Phân tích nhóm -> Top Rated -> Popular.
     * Tăng phạm vi quét lên 200 để tránh bị lọc hết.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRelatedMoviesFromList(List<Map<String, Object>> sourceMovies, int limit) {
        List<Map<String, Object>> finalResults = new ArrayList<>();
        Set<Integer> excludeIds = new HashSet<>();

        if (sourceMovies != null) {
            for (Map<String, Object> m : sourceMovies) {
                excludeIds.add((Integer) m.get("id"));
            }
        }

        // 1. Phân tích đặc điểm (Genre/Director)
        if (sourceMovies != null && !sourceMovies.isEmpty()) {
            Map<String, Integer> genreCount = new HashMap<>();
            Map<String, Integer> directorCount = new HashMap<>();

            for (Map<String, Object> m : sourceMovies) {
                List<String> genres = (List<String>) m.get("genres");
                if (genres != null)
                    genres.forEach(g -> genreCount.put(g, genreCount.getOrDefault(g, 0) + 1));

                String director = (String) m.get("director");
                if (director != null && !"N/A".equals(director) && !"Đang cập nhật".equals(director)) {
                    directorCount.put(director, directorCount.getOrDefault(director, 0) + 1);
                }
            }

            String topGenre = genreCount.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey)
                    .orElse(null);
            String topDirector = directorCount.entrySet().stream().max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(null);

            MovieSearchFilters filter = new MovieSearchFilters();
            boolean hasCriteria = false;

            if (topDirector != null && directorCount.get(topDirector) >= 2) {
                filter.setDirector(topDirector);
                hasCriteria = true;
            } else if (topGenre != null) {
                filter.setGenres(Arrays.asList(topGenre));
                filter.setMinRating(5.0f);
                hasCriteria = true;
            }

            if (hasCriteria) {
                // Lấy nhiều hơn (50) để trừ hao
                List<Movie> candidates = findMoviesByFilters(filter).stream().limit(50).collect(Collectors.toList());
                for (Movie m : candidates) {
                    if (!excludeIds.contains(m.getMovieID())) {
                        finalResults.add(convertToMap(m));
                        excludeIds.add(m.getMovieID());
                    }
                    if (finalResults.size() >= limit)
                        break;
                }
            }
        }

        // 2. [BACKFILL 1] Bù bằng Top Rated (Quét sâu 200 phim)
        if (finalResults.size() < limit) {
            Page<Movie> topRated = movieRepository.findAllByOrderByRatingDesc(PageRequest.of(0, 200));
            for (Movie m : topRated) {
                if (finalResults.size() >= limit)
                    break;
                if (!excludeIds.contains(m.getMovieID())) {
                    finalResults.add(convertToMap(m));
                    excludeIds.add(m.getMovieID());
                }
            }
        }

        // 3. [BACKFILL 2] Bù bằng Popular (Quét sâu 200 phim) - Đảm bảo không bao giờ
        // rỗng
        if (finalResults.size() < limit) {
            // Dùng PageRequest sort theo popularity
            Page<Movie> popularMovies = movieRepository
                    .findAll(PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "popularity")));
            for (Movie m : popularMovies) {
                if (finalResults.size() >= limit)
                    break;
                if (!excludeIds.contains(m.getMovieID())) {
                    finalResults.add(convertToMap(m));
                    excludeIds.add(m.getMovieID());
                }
            }
        }

        return finalResults;
    }

    /**
     * [LOGIC WATERFALL CAO CẤP] Gợi ý phim theo 5 lớp ưu tiên.
     * Hỗ trợ trả về Logo/Backdrop của Collection/Studio để làm đẹp UI.
     */
    @Transactional
    public List<Map<String, Object>> getRecommendedMoviesWaterfall(Movie movie, Map<String, Object> response) {
        Set<Integer> addedIds = new HashSet<>();
        List<Map<String, Object>> finalRecommendations = new ArrayList<>();
        int limit = 20;

        // Loại trừ phim hiện tại
        addedIds.add(movie.getMovieID());
        if (movie.getTmdbId() != null)
            addedIds.add(movie.getTmdbId());

        // ----- LỚP 1: COLLECTION (Vũ trụ điện ảnh - Ưu tiên số 1)
        try {
            if (movie.getCollection() != null) {
                Collection col = movie.getCollection();
                List<Movie> colMovies = col.getMovies(); // Lấy từ DB (Lazy load ok vì có @Transactional)

                if (colMovies != null && !colMovies.isEmpty()) {
                    for (Movie m : colMovies) {
                        if (!addedIds.contains(m.getMovieID())) {
                            finalRecommendations.add(convertToMap(m));
                            addedIds.add(m.getMovieID());
                        }
                    }
                    if (!finalRecommendations.isEmpty()) {
                        response.put("title", "Trọn Bộ: " + col.getName());
                        // Trả về ảnh Collection để UI hiển thị
                        if (col.getBackdropPath() != null)
                            response.put("headerImage", "https://image.tmdb.org/t/p/original" + col.getBackdropPath());
                        else if (col.getPosterPath() != null)
                            response.put("headerImage", "https://image.tmdb.org/t/p/w500" + col.getPosterPath());

                        finalRecommendations.sort(getRelevanceComparator(SortBy.NEW));
                        return finalRecommendations.stream().limit(limit).collect(Collectors.toList());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        finalRecommendations.clear();

        // ----- LỚP 2: STUDIO (Hãng phim - Ưu tiên số 2)
        try {
            if (movie.getProductionCompanies() != null && !movie.getProductionCompanies().isEmpty()) {
                // Lấy hãng đầu tiên
                ProductionCompany studio = movie.getProductionCompanies().iterator().next();
                Set<Movie> studioMovies = studio.getMovies(); // Lấy từ DB (Lazy ok)

                if (studioMovies != null) {
                    for (Movie m : studioMovies) {
                        if (!addedIds.contains(m.getMovieID())) {
                            finalRecommendations.add(convertToMap(m));
                            addedIds.add(m.getMovieID());
                        }
                    }
                }

                if (finalRecommendations.size() >= 4) { // Cần ít nhất 4 phim để tạo list
                    response.put("title", "Từ Studio: " + studio.getName());
                    
                    // [QUAN TRỌNG] Gửi thêm thông tin để Frontend tạo link
                    response.put("recoType", "Studio");
                    response.put("recoName", studio.getName());
                    // Logo Studio để hiển thị đẹp
                    if (studio.getLogoPath() != null)
                        response.put("recoLogo", studio.getLogoPath()); 
                    // ID Studio để tạo link
                    response.put("sourceId", studio.getId()); 

                    return finalRecommendations.stream().limit(limit).collect(Collectors.toList());
                }
            }
        } catch (Exception e) {
            /* Ignore */ 
        }

        finalRecommendations.clear();

        // ----- LỚP 3: DIRECTOR (Đạo diễn - Ưu tiên số 3)
        try {
            if (movie.getDirector() != null && !"N/A".equals(movie.getDirector())) {
                MovieSearchFilters filter = new MovieSearchFilters();
                filter.setDirector(movie.getDirector());
                List<Movie> directorMovies = findMoviesByFilters(filter);

                for (Movie m : directorMovies) {
                    if (!addedIds.contains(m.getMovieID())) {
                        finalRecommendations.add(convertToMap(m));
                        addedIds.add(m.getMovieID());
                    }
                }

                if (finalRecommendations.size() >= 3) {
                    response.put("title", "Phim cùng Đạo diễn: " + movie.getDirector());
                    return finalRecommendations.stream().limit(limit).collect(Collectors.toList());
                }
            }
        } catch (Exception e) {
            /* Ignore */ }

        finalRecommendations.clear();

        // ----- LỚP 4: GENRE + COUNTRY (Thể loại tương đồng)
        try {
            if (movie.getGenres() != null && !movie.getGenres().isEmpty()) {
                MovieSearchFilters filter = new MovieSearchFilters();
                List<String> gNames = new ArrayList<>();
                gNames.add(movie.getGenres().iterator().next().getName()); // Lấy genre chính
                filter.setGenres(gNames);
                filter.setCountry(movie.getCountry()); // Cùng quốc gia

                List<Movie> similar = findMoviesByFilters(filter);
                for (Movie m : similar) {
                    if (!addedIds.contains(m.getMovieID())) {
                        finalRecommendations.add(convertToMap(m));
                        addedIds.add(m.getMovieID());
                    }
                }

                if (finalRecommendations.size() >= 5) {
                    response.put("title", "Phim " + gNames.get(0) + " tương tự");
                    return finalRecommendations.stream().limit(limit).collect(Collectors.toList());
                }
            }
        } catch (Exception e) {
        }

        // ----- LỚP 5: FALLBACK (Phim Hot - Cuối cùng)
        response.put("title", "Có thể bạn sẽ thích");
        return loadRecommendedFallback(movie.getTmdbId(), addedIds, limit);
    }


    /**
     * [SMART FALLBACK V2] Gợi ý AI: Hot Movies -> New Movies.
     * Tăng phạm vi quét để đảm bảo luôn có phim hiển thị.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> loadRecommendedFallback(Integer tmdbId, Set<Integer> addedMovieIds, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();

        // 1. Thử lấy phim Hot (Weighted Score) - Quét 100 phim
        Page<Movie> hotMovies = getHotMoviesFromDB(100);
        for (Movie m : hotMovies) {
            Integer mPkId = m.getMovieID();
            Integer mTmdb = m.getTmdbId();
            // Check loại trừ
            boolean isExist = addedMovieIds.contains(mPkId) || (mTmdb != null && addedMovieIds.contains(mTmdb));
            // Check trùng với chính phim gốc (nếu có)
            boolean isSelf = (tmdbId != null && tmdbId.equals(mTmdb));

            if (!isExist && !isSelf) {
                results.add(convertToMap(m));
                addedMovieIds.add(mPkId);
            }
            if (results.size() >= limit)
                return results;
        }

        // 2. [BACKFILL] Lấy Phim Mới (Newest) - Quét 100 phim
        Page<Movie> newMovies = getNewMoviesFromDB(100);
        for (Movie m : newMovies) {
            Integer mPkId = m.getMovieID();
            Integer mTmdb = m.getTmdbId();
            boolean isExist = addedMovieIds.contains(mPkId) || (mTmdb != null && addedMovieIds.contains(mTmdb));
            boolean isSelf = (tmdbId != null && tmdbId.equals(mTmdb));

            if (!isExist && !isSelf) {
                results.add(convertToMap(m));
                addedMovieIds.add(mPkId);
            }
            if (results.size() >= limit)
                return results;
        }

        return results;
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

    // Helper: Tìm Trailer Youtube tốt nhất từ JSON
    private String findBestTrailerKeyFromJSON(JSONObject json) {
        JSONObject videos = json.optJSONObject("videos");
        if (videos == null)
            return null;

        JSONArray results = videos.optJSONArray("results");
        if (results == null || results.length() == 0)
            return null;

        // Ưu tiên 1: Youtube + Trailer + Tiếng Việt (nếu có trong tương lai)
        for (int i = 0; i < results.length(); i++) {
            JSONObject v = results.getJSONObject(i);
            if ("YouTube".equals(v.optString("site")) && "Trailer".equals(v.optString("type"))
                    && "vi".equals(v.optString("iso_639_1"))) {
                return v.optString("key");
            }
        }
        // Ưu tiên 2: Youtube + Trailer (Bất kỳ ngôn ngữ)
        for (int i = 0; i < results.length(); i++) {
            JSONObject v = results.getJSONObject(i);
            if ("YouTube".equals(v.optString("site")) && "Trailer".equals(v.optString("type"))) {
                return v.optString("key");
            }
        }
        // Ưu tiên 3: Teaser
        for (int i = 0; i < results.length(); i++) {
            JSONObject v = results.getJSONObject(i);
            if ("YouTube".equals(v.optString("site"))) {
                return v.optString("key");
            }
        }
        return null;
    }

    // Helper: Tìm Logo tốt nhất từ JSON
    private String findBestLogoFromJSON(JSONObject json) {
        JSONObject images = json.optJSONObject("images");
        if (images == null)
            return null;

        JSONArray logos = images.optJSONArray("logos");
        if (logos == null || logos.length() == 0)
            return null;

        // Ưu tiên: Tiếng Việt -> Tiếng Anh -> Cái đầu tiên
        String bestLogo = null;
        for (int i = 0; i < logos.length(); i++) {
            JSONObject l = logos.getJSONObject(i);
            String lang = l.optString("iso_639_1");
            if ("vi".equals(lang))
                return l.optString("file_path");
            if ("en".equals(lang) && bestLogo == null)
                bestLogo = l.optString("file_path");
        }
        return (bestLogo != null) ? bestLogo : logos.getJSONObject(0).optString("file_path");
    }

    // ---- 7. DB QUERY HELPERS ----

    /**
     * [THUẬT TOÁN MỚI] Lấy phim Hot dựa trên điểm số cân bằng (Weighted Score)
     * Score = Rating * 0.7 + (Popularity/100) * 0.3
     * Yêu cầu: voteCount >= minVoteCount
     */
    @Transactional(readOnly = true)
    public Page<Movie> getHotMoviesFromDB(int limit) {
        // 1. Lấy danh sách ứng viên (Top 1000 phim có vote > 5, sort theo popularity để
        // lấy pool tốt)
        // Lưu ý: PageRequest ở đây dùng Popularity để fetch nhanh data "tiềm năng"
        Page<Movie> candidates = movieRepository.findAll(
                (root, query, cb) -> cb.ge(root.get("voteCount"), 5),
                PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "popularity")));

        List<Movie> movies = new ArrayList<>(candidates.getContent());

        // 2. Sắp xếp lại bằng Java (In-memory) với công thức công bằng hơn
        movies.sort((m1, m2) -> {
            double score1 = calculateWeightedScore(m1);
            double score2 = calculateWeightedScore(m2);
            return Double.compare(score2, score1); // Descending
        });

        // 3. Cắt list theo limit và trả về Page
        int actualLimit = Math.min(limit, movies.size());
        List<Movie> pagedList = movies.subList(0, actualLimit);

        return new org.springframework.data.domain.PageImpl<>(pagedList);
    }

    // Helper tính điểm (Rating 0-10, Popularity 0-vô cùng)
    private double calculateWeightedScore(Movie m) {
        double rating = m.getRating();
        double pop = (m.getPopularity() != null) ? m.getPopularity() : 0;
        // Normalize popularity (giả sử max pop ~ 5000, lấy log để giảm ảnh hưởng của
        // outlier)
        double popScore = (pop > 0) ? Math.log10(pop) * 2 : 0;
        // Công thức: Rating quan trọng hơn (70%), độ nổi tiếng (30%)
        return (rating * 0.7) + (popScore * 0.3);
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
    // Chuyển đổi Person Entity sang Map<String, Object> (Sử dụng PK là 'id')
    public Map<String, Object> convertToMap(Movie movie) {
        if (movie == null)
            return null;
        Map<String, Object> map = new HashMap<>();
        map.put("id", movie.getMovieID());
        map.put("tmdbId", movie.getTmdbId());
        map.put("title", movie.getTitle());
        map.put("overview", movie.getDescription() != null ? movie.getDescription() : "Đang cập nhật nội dung...");

        // Rating & Vote
        map.put("rating", String.format("%.1f", movie.getRating()));
        map.put("rating_raw", movie.getRating());
        map.put("voteCount", movie.getVoteCount() != null ? movie.getVoteCount() : 0);

        // Images
        String poster = movie.getPosterPath();
        String backdrop = movie.getBackdropPath();
        map.put("poster",
                (poster != null && !poster.isEmpty())
                        ? (poster.startsWith("http") ? poster : "https://image.tmdb.org/t/p/w500" + poster)
                        : "/images/placeholder.jpg");
        map.put("backdrop",
                (backdrop != null && !backdrop.isEmpty())
                        ? (backdrop.startsWith("http") ? backdrop : "https://image.tmdb.org/t/p/original" + backdrop)
                        : "/images/placeholder.jpg");
        map.put("isFree", movie.isFree());
        // Metadata an toàn
        map.put("contentRating", movie.getContentRating() != null ? movie.getContentRating() : "T");

        if (movie.getReleaseDate() != null) {
            map.put("year", new SimpleDateFormat("yyyy").format(movie.getReleaseDate()));
            map.put("releaseDate", new SimpleDateFormat("yyyy-MM-dd").format(movie.getReleaseDate()));
        } else {
            map.put("year", "N/A");
            map.put("releaseDate", "");
        }

        // [FIX QUAN TRỌNG] Thêm Budget & Revenue để trang Detail không bị lỗi
        map.put("budget", movie.getBudget() != null ? movie.getBudget() : 0L);
        map.put("revenue", movie.getRevenue() != null ? movie.getRevenue() : 0L);

        map.put("runtime", (movie.getDuration() > 0 ? movie.getDuration() : 0)); // Để số nguyên cho JS dễ xử lý
        map.put("director", movie.getDirector() != null ? movie.getDirector() : "Đang cập nhật");
        map.put("country", movie.getCountry() != null ? movie.getCountry() : "Quốc tế");
        map.put("language", movie.getLanguage() != null ? movie.getLanguage() : "Đang cập nhật");
        map.put("popularity", movie.getPopularity() != null ? movie.getPopularity() : 0.0);

        // [FIX VĐ 5] Trả về List Map để lấy được cả ID và Name
        List<Map<String, Object>> genres = new ArrayList<>();
        if (movie.getGenres() != null) {
            movie.getGenres().forEach(g -> {
                Map<String, Object> gMap = new HashMap<>();
                gMap.put("id", g.getTmdbGenreId()); // ID dùng để link sang Discover
                gMap.put("name", g.getName());      // Tên để hiển thị
                genres.add(gMap);
            });
        }
        map.put("genres", genres);

        return map;
    }



    public Map<String, Object> convertToMap(Movie movie, String role) {
        Map<String, Object> map = convertToMap(movie);
        if (map != null)
            map.put("role_info", role);
        return map;
    }

    public Map<String, Object> convertToMap(Person p) {
        if (p == null)
            return null;
        Map<String, Object> map = new HashMap<>();
        map.put("id", p.getPersonID());
        map.put("name", p.getFullName());
        map.put("avatar", p.getProfilePath() != null ? "https://image.tmdb.org/t/p/w500" + p.getProfilePath()
                : "/images/placeholder-user.jpg");
        map.put("known_for_department", p.getKnownForDepartment() != null ? p.getKnownForDepartment() : "—");
        map.put("birthday", p.getBirthday() != null ? new SimpleDateFormat("yyyy-MM-dd").format(p.getBirthday()) : "—");
        map.put("place_of_birth", p.getPlaceOfBirth() != null ? p.getPlaceOfBirth() : "—");
        map.put("popularity", p.getPopularity() != null ? String.format("%.1f", p.getPopularity()) : "0");
        map.put("biography", p.getBio() != null && !p.getBio().isEmpty() ? p.getBio() : "Chưa có thông tin tiểu sử.");
        return map;
    }

    private Date parseDate(String dateString) {
        if (dateString == null || dateString.isEmpty())
            return null;
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(dateString);
        } catch (ParseException e) {
            return null;
        }
    }

    // ---- 10. TRAILER & LOGO FINDERS ----

    // [CẬP NHẬT] Lấy Trailer Key từ DB (Offline Mode)
    public String findBestTrailerKey(int movieID) {
        Movie movie = movieRepository.findById(movieID).orElse(null);
        if (movie != null && movie.getTrailerKey() != null && !movie.getTrailerKey().isEmpty()) {
            return movie.getTrailerKey();
        }
        // Fallback: Nếu DB chưa có (phim cũ chưa sync lại), có thể trả về null hoặc gọi
        // API tạm (nhưng ta đang muốn bỏ API)
        return null;
    }

    public List<Map<String, Object>> findTrailers(int tmdbId, int limit) {
        List<Map<String, Object>> trailers = new ArrayList<>();

        // Tìm phim trong DB bằng tmdbId
        Optional<Movie> movieOpt = movieRepository.findByTmdbId(tmdbId);

        if (movieOpt.isPresent()) {
            Movie movie = movieOpt.get();
            if (movie.getTrailerKey() != null && !movie.getTrailerKey().isEmpty()) {
                Map<String, Object> trailer = new HashMap<>();
                trailer.put("key", movie.getTrailerKey());
                trailer.put("name", "Trailer Chính Thức");
                trailers.add(trailer);
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

    // [CẬP NHẬT] Lấy Logo Path từ DB (Offline Mode)
    public String findBestLogoPath(int movieID) {
        Movie movie = movieRepository.findById(movieID).orElse(null);
        if (movie != null && movie.getLogoPath() != null && !movie.getLogoPath().isEmpty()) {
            return movie.getLogoPath();
        }
        return null;
    }

    // ---- 11. CRUD CƠ BẢN ----

    public List<Movie> getAllMovies() {
        return movieRepository.findAll();
    }

    @Transactional(readOnly = true)
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
        
        // --- [FIX LOGIC] ---
        // Tạo tmdbId giả định (số âm) dựa trên timestamp để đảm bảo unique và không null
        int generatedId = (int) (System.currentTimeMillis() / 1000) * -1;
        movie.setTmdbId(generatedId);
        
        // Thiết lập các giá trị mặc định khác để tránh lỗi null
        if (movie.getDirector() == null) movie.setDirector("N/A");
        if (movie.getLanguage() == null) movie.setLanguage("Việt Nam"); // Hoặc N/A
        if (movie.getCountry() == null) movie.setCountry("Việt Nam");   // Mặc định phim thủ công
        if (movie.getVoteCount() == null) movie.setVoteCount(0);
        if (movie.getPopularity() == null) movie.setPopularity(0.0);
        if (movie.getBudget() == null) movie.setBudget(0L);
        if (movie.getRevenue() == null) movie.setRevenue(0L);
        
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

    /**
     * [FIX CORE] Lấy chi tiết phim và chuyển sang Map trong cùng 1 Transaction.
     * Đảm bảo không bao giờ bị lỗi LazyInitializationException cho Controller/API.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getMovieDetailMap(int movieId) {
        Movie movie = movieRepository.findByIdWithDetails(movieId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phim ID: " + movieId));

        // 1. Ép tải dữ liệu Lazy (chạm vào collection để Hibernate fetch)
        if (movie.getGenres() != null)
            movie.getGenres().size();
        if (movie.getPersons() != null)
            movie.getPersons().size();

        // 2. Convert sang Map (Lúc này Session vẫn còn sống -> An toàn tuyệt đối)
        Map<String, Object> map = convertToMap(movie);

        // 3. Bổ sung Trailer/Logo từ DB
        map.put("trailerKey", findBestTrailerKey(movieId));
        map.put("logoPath", findBestLogoPath(movieId));

        // 4. Lấy Cast kèm Role chuẩn từ bảng MoviePerson (SỬA ĐỔI: Lấy cả Đạo diễn)
        List<Map<String, Object>> castList = new ArrayList<>();
        List<MoviePerson> moviePersons = moviePersonRepository.findByMovieID(movieId);
        
        if (!moviePersons.isEmpty()) {
            // Tạo 2 list tạm để gom nhóm
            List<Map<String, Object>> directors = new ArrayList<>();
            List<Map<String, Object>> actors = new ArrayList<>();

            for (MoviePerson mp : moviePersons) {
                Person p = personRepository.findById(mp.getPersonID()).orElse(null);
                if (p != null) {
                    Map<String, Object> pMap = convertToMap(p);
                    String job = mp.getJob();

                    if ("Director".equalsIgnoreCase(job)) {
                        pMap.put("role", "Đạo diễn"); // Gán cứng role Đạo diễn
                        directors.add(pMap);
                    } else if ("Acting".equalsIgnoreCase(job)) {
                        pMap.put("role", mp.getCharacterName() != null ? mp.getCharacterName() : "Diễn viên");
                        actors.add(pMap);
                    }
                }
            }
            
            // Ưu tiên: Đưa Đạo diễn lên đầu danh sách
            castList.addAll(directors);
            castList.addAll(actors);
            
        } else {
            // Fallback: Nếu chưa có dữ liệu trong bảng mới, dùng logic cũ (giữ nguyên fallback)
            if (movie.getPersons() != null) {
                castList = movie.getPersons().stream().limit(12).map(p -> {
                    Map<String, Object> m = convertToMap(p);
                    m.put("role", "Diễn viên");
                    return m;
                }).collect(Collectors.toList());
            }
        }
        map.put("castList", castList);
        
        return map;
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
    /**
     * [MỚI] Hàm lọc phim nâng cao cho trang quản lý
     * Hỗ trợ: Tìm kiếm (ID/Tên), Lọc (Free/Paid), Phân trang
     */
    public Page<Movie> getAdminMovies(String keyword, Boolean isFree, Pageable pageable) {
        return movieRepository.findAll((Specification<Movie>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Tìm theo từ khóa (Tên hoặc ID)
            if (StringUtils.hasText(keyword)) {
                String likePattern = "%" + keyword.trim() + "%";
                Predicate titleLike = cb.like(root.get("title"), likePattern);
                
                // Thử parse ID nếu keyword là số
                Predicate idEqual = null;
                try {
                    int id = Integer.parseInt(keyword.trim());
                    idEqual = cb.equal(root.get("movieID"), id);
                } catch (NumberFormatException e) {}

                if (idEqual != null) {
                    predicates.add(cb.or(titleLike, idEqual));
                } else {
                    predicates.add(titleLike);
                }
            }

            // 2. Lọc theo trạng thái Free/Paid
            if (isFree != null) {
                predicates.add(cb.equal(root.get("isFree"), isFree));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }
    @Transactional
    public ProductionCompany getCompanyByIdOrSync(int id) {
        return companyRepository.findById(id).orElse(null);
    }

    @Transactional
    public ProductionCompany getCompanyOrSync(int tmdbId) {
        return companyRepository.findByTmdbId(tmdbId).orElseGet(() -> fetchAndSaveCompany(tmdbId));
    }

    // Helper: Tải thông tin Studio từ TMDB
    private ProductionCompany fetchAndSaveCompany(int tmdbId) {
        try {
            String url = BASE_URL + "/company/" + tmdbId + "?api_key=" + API_KEY;
            String resp = restTemplate.getForObject(url, String.class);
            JSONObject json = new JSONObject(resp);

            ProductionCompany comp = new ProductionCompany();
            comp.setTmdbId(tmdbId);
            comp.setName(json.optString("name"));
            comp.setLogoPath(json.optString("logo_path"));
            comp.setOriginCountry(json.optString("origin_country"));

            return companyRepository.save(comp);
        } catch (Exception e) {
            System.err.println("Lỗi sync Company ID " + tmdbId + ": " + e.getMessage());
            return null;
        }
    }
    // [PHASE 5 - SEARCH FIX] Tìm kiếm tổng hợp (Title + Person Role)
    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchMoviesCombined(String query) {
        Map<Integer, Map<String, Object>> resultMap = new LinkedHashMap<>(); // Dùng LinkedHashMap để giữ thứ tự

        // 1. Tìm theo Tên Phim
        List<Movie> byTitle = searchMoviesByTitle(query);
        for (Movie m : byTitle) {
            resultMap.put(m.getMovieID(), convertToMap(m));
        }

        // 2. Tìm theo Tên Người
        List<Person> persons = personRepository.findByFullNameContainingIgnoreCase(query);
        for (Person p : persons) {
            List<MoviePerson> mps = moviePersonRepository.findByPersonID(p.getPersonID());

            for (MoviePerson mp : mps) {
                int mid = mp.getMovieID();
                
                // [LOGIC MỚI] Xác định role ưu tiên
                String currentJob = mp.getJob(); // Director, Acting...
                String roleDisplay;
                
                if ("Director".equalsIgnoreCase(currentJob)) {
                    roleDisplay = "Đạo diễn: " + p.getFullName();
                } else {
                    String charName = mp.getCharacterName();
                    roleDisplay = "Diễn viên" + (charName != null ? ": " + charName : "") + " (" + p.getFullName() + ")";
                }

                if (resultMap.containsKey(mid)) {
                    // Nếu phim đã có trong list:
                    // Kiểm tra nếu role cũ là Diễn viên mà role mới là Đạo diễn -> Ghi đè (Ưu tiên Đạo diễn)
                    Map<String, Object> existingMap = resultMap.get(mid);
                    String oldRole = (String) existingMap.get("role_info");
                    
                    if (oldRole == null || (!oldRole.contains("Đạo diễn") && roleDisplay.contains("Đạo diễn"))) {
                        existingMap.put("role_info", roleDisplay);
                    }
                } else {
                    // Nếu chưa có -> Thêm mới
                    movieRepository.findByIdWithDetails(mid).ifPresent(m -> { // Dùng hàm mới đã JOIN FETCH
                        Map<String, Object> map = convertToMap(m);
                        map.put("role_info", roleDisplay);
                        resultMap.put(mid, map);
                    });
                }
            }
        }
        return new ArrayList<>(resultMap.values());
    }
    @Transactional
    public void saveMoviePersonRole(int movieId, int personId, String character, String job) {
        try {
            MoviePerson mp = moviePersonRepository.findByMovieIDAndPersonID(movieId, personId)
                    .orElse(new MoviePerson(movieId, personId));
            if (character != null && !character.isEmpty()) mp.setCharacterName(character);
            if (job != null && !job.isEmpty()) mp.setJob(job);
            moviePersonRepository.save(mp);
        } catch (Exception e) { /* Ignore duplicate */ }
    }



}