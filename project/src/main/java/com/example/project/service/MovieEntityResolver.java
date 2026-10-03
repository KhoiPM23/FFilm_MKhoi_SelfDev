package com.example.project.service;

import com.example.project.model.Movie;
import com.example.project.model.MoviePerson;
import com.example.project.model.Person;
import com.example.project.repository.MoviePersonRepository;
import com.example.project.repository.MovieRepository;
import com.example.project.repository.PersonRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Component chuyên trách phân giải thực thể điện ảnh (Canonical Entity Resolution)
 * và đồ thị quan hệ Phim ↔ Nghệ sĩ (Movie ↔ Person Graph).
 */
@Component
public class MovieEntityResolver {

    private static final Logger log = LoggerFactory.getLogger(MovieEntityResolver.class);

    private final MovieRepository movieRepository;
    private final PersonRepository personRepository;
    private final MoviePersonRepository moviePersonRepository;
    private final MovieService movieService;
    private final TmdbClient tmdbClient;

    @Autowired
    public MovieEntityResolver(
            MovieRepository movieRepository,
            PersonRepository personRepository,
            MoviePersonRepository moviePersonRepository,
            MovieService movieService,
            @Autowired(required = false) TmdbClient tmdbClient) {
        this.movieRepository = movieRepository;
        this.personRepository = personRepository;
        this.moviePersonRepository = moviePersonRepository;
        this.movieService = movieService;
        this.tmdbClient = tmdbClient;
    }

    /**
     * Tra cứu thực thể phim chuẩn hóa (Canonical Entity Resolution):
     * 1. Tra cứu exact match trong local DB theo vnTitle và queryTitle.
     * 2. Tra cứu TMDb Search API để lấy canonical tmdbId chuẩn.
     * 3. Đối chiếu tmdbId với MovieRepository để lấy tác phẩm tương ứng trong FFilm.
     * 4. Phân biệt rõ MOVIE vs DOCUMENTARY/TV.
     */
    public Movie resolveCanonicalMovie(String queryTitle, String origTitle, String vnTitle) {
        if ((queryTitle == null || queryTitle.trim().isEmpty()) &&
                (origTitle == null || origTitle.trim().isEmpty()) &&
                (vnTitle == null || vnTitle.trim().isEmpty())) {
            return null;
        }

        // 1. Exact match theo tên tiếng Việt
        if (vnTitle != null && !vnTitle.trim().isEmpty()) {
            List<Movie> exactVn = findMoviesRanked(vnTitle.trim());
            if (!exactVn.isEmpty() && exactVn.get(0).getTitle().equalsIgnoreCase(vnTitle.trim())) {
                return exactVn.get(0);
            }
        }

        // 2. Exact match theo queryTitle
        if (queryTitle != null && !queryTitle.trim().isEmpty()) {
            List<Movie> exactQuery = findMoviesRanked(queryTitle.trim());
            if (!exactQuery.isEmpty() && exactQuery.get(0).getTitle().equalsIgnoreCase(queryTitle.trim())) {
                return exactQuery.get(0);
            }
        }

        // 3. Tra cứu qua TMDb Search API
        String searchKey = (origTitle != null && !origTitle.trim().isEmpty())
                ? origTitle.trim()
                : ((queryTitle != null && !queryTitle.trim().isEmpty()) ? queryTitle.trim() : vnTitle);

        if (tmdbClient != null && searchKey != null && !searchKey.trim().isEmpty()) {
            try {
                String q = URLEncoder.encode(searchKey.trim(), StandardCharsets.UTF_8);
                String tmdbJson = tmdbClient.get("/search/movie", "query=" + q + "&language=vi-VN");
                if (tmdbJson != null && !tmdbJson.isEmpty()) {
                    JSONObject obj = new JSONObject(tmdbJson);
                    JSONArray results = obj.optJSONArray("results");
                    if (results != null && results.length() > 0) {
                        for (int i = 0; i < Math.min(results.length(), 4); i++) {
                            JSONObject item = results.getJSONObject(i);
                            int tmdbId = item.optInt("id");
                            if (tmdbId > 0) {
                                Optional<Movie> dbMatch = movieRepository.findByTmdbId(tmdbId);
                                if (dbMatch.isPresent()) {
                                    log.info("Canonical TMDb Match: '{}' -> tmdbId {} ('{}')", searchKey, tmdbId, dbMatch.get().getTitle());
                                    return dbMatch.get();
                                }
                            }
                        }

                        // Thử tìm theo tiêu đề tiếng Việt trả về từ TMDb nếu chưa map theo ID
                        for (int i = 0; i < Math.min(results.length(), 3); i++) {
                            String tmdbTitle = results.getJSONObject(i).optString("title");
                            if (tmdbTitle != null && !tmdbTitle.isEmpty()) {
                                List<Movie> byTmdbTitle = findMoviesRanked(tmdbTitle);
                                if (!byTmdbTitle.isEmpty() && byTmdbTitle.get(0).getTitle().equalsIgnoreCase(tmdbTitle)) {
                                    return byTmdbTitle.get(0);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("TMDb canonical lookup error for '{}': {}", searchKey, e.getMessage());
            }
        }

        // 4. Tra cứu local DB qua origTitle
        if (origTitle != null && !origTitle.trim().isEmpty()) {
            List<Movie> exactOrig = findMoviesRanked(origTitle.trim());
            if (!exactOrig.isEmpty() && exactOrig.get(0).getTitle().equalsIgnoreCase(origTitle.trim())) {
                return exactOrig.get(0);
            }
        }

        // 5. Fallback tra cứu partial match trong local DB
        if (queryTitle != null && !queryTitle.trim().isEmpty()) {
            List<Movie> partials = findMoviesRanked(queryTitle.trim());
            if (!partials.isEmpty()) {
                Movie top = partials.get(0);
                String topTitle = top.getTitle().toLowerCase();
                // Bỏ qua documentary/making of nếu query đang tìm phim truyện
                if (topTitle.contains(":") && (topTitle.contains("odyssey") || topTitle.contains("tài liệu") || topTitle.contains("making of"))) {
                    log.warn("Bỏ qua documentary/spinoff '{}' cho query '{}'", top.getTitle(), queryTitle);
                    return null;
                }
                return top;
            }
        }

        return null;
    }

    /**
     * Phát hiện nếu có nhiều phim ứng viên có thể gây mơ hồ (Ambiguous Titles).
     * Ví dụ: "The Host" (Quái Vật Sông Hàn 2006 vs Vật Chủ 2013) hoặc các phần phim khác nhau.
     */
    public List<Movie> detectAmbiguousCandidates(String queryTitle) {
        if (queryTitle == null || queryTitle.trim().isEmpty()) return Collections.emptyList();
        String q = queryTitle.trim().toLowerCase();
        List<Movie> allMatches = movieRepository.findByTitleContainingIgnoreCase(q);

        // Lọc các phim có title thực sự tương đồng
        List<Movie> distinctCandidates = allMatches.stream()
                .filter(m -> {
                    String t = m.getTitle().toLowerCase();
                    return t.contains(q) || q.contains(t);
                })
                .limit(5)
                .collect(Collectors.toList());

        return distinctCandidates.size() > 1 ? distinctCandidates : Collections.emptyList();
    }

    public List<Movie> disambiguateMovieCandidates(String queryTitle) {
        return detectAmbiguousCandidates(queryTitle);
    }

    public List<Movie> findCommonMoviesBetweenPersons(String p1Name, String p2Name) {
        return resolveCoStarMovies(p1Name, p2Name);
    }

    /**
     * Tra cứu danh sách phim của một nghệ sĩ theo vai trò (Actor hoặc Director).
     */
    public List<Movie> findMoviesByPerson(String personName, String role) {
        if (personName == null || personName.trim().isEmpty()) return Collections.emptyList();
        String pTrim = personName.trim();
        List<Person> matched = personRepository.findByFullNameContainingIgnoreCase(pTrim);
        if (matched.isEmpty()) {
            return getPersonMovies(pTrim);
        }
        Person person = matched.get(0);
        List<MoviePerson> mps = moviePersonRepository.findByPersonID(person.getPersonID());
        if (role != null && !role.trim().isEmpty()) {
            String rLower = role.trim().toLowerCase();
            mps = mps.stream()
                    .filter(mp -> (mp.getJob() != null && mp.getJob().toLowerCase().contains(rLower)) ||
                                  (mp.getCharacterName() != null && mp.getCharacterName().toLowerCase().contains(rLower)))
                    .collect(Collectors.toList());
        }
        List<Movie> movies = new ArrayList<>();
        for (MoviePerson mp : mps) {
            movieRepository.findById(mp.getMovieID()).ifPresent(movies::add);
        }
        if (movies.isEmpty()) {
            return getPersonMovies(pTrim);
        }
        return movies;
    }

    /**
     * Tra cứu danh sách phim của một nghệ sĩ (Actor hoặc Director).
     */
    public List<Movie> getPersonMovies(String personName) {
        if (personName == null || personName.trim().isEmpty()) return Collections.emptyList();
        List<Map<String, Object>> mapList = movieService.searchMoviesCombined(personName.trim());
        List<Movie> result = new ArrayList<>();
        for (Map<String, Object> m : mapList) {
            Object idObj = m.get("id");
            if (idObj != null) {
                int mid = ((Number) idObj).intValue();
                movieRepository.findById(mid).ifPresent(result::add);
            }
        }
        return result;
    }

    /**
     * Lấy bộ phim có rating cao nhất của một nghệ sĩ.
     */
    public Movie getHighestRatedMovieByPerson(String personName) {
        List<Movie> movies = getPersonMovies(personName);
        return movies.stream()
                .max(Comparator.comparingDouble(Movie::getRating))
                .orElse(null);
    }

    /**
     * Lấy phim mới nhất của một nghệ sĩ.
     */
    public Movie getLatestMovieByPerson(String personName) {
        List<Movie> movies = getPersonMovies(personName);
        return movies.stream()
                .filter(m -> m.getReleaseDate() != null)
                .max(Comparator.comparing(Movie::getReleaseDate))
                .orElse(null);
    }

    /**
     * Tìm các phim hợp tác chung giữa 2 nghệ sĩ.
     */
    public List<Movie> resolveCoStarMovies(String p1Name, String p2Name) {
        if (p1Name == null || p2Name == null || p1Name.trim().isEmpty() || p2Name.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<Person> l1 = personRepository.findByFullNameContainingIgnoreCase(p1Name.trim());
        List<Person> l2 = personRepository.findByFullNameContainingIgnoreCase(p2Name.trim());

        if (l1.isEmpty() || l2.isEmpty()) return Collections.emptyList();

        Person p1 = l1.get(0);
        Person p2 = l2.get(0);

        Set<Integer> mIds1 = moviePersonRepository.findByPersonID(p1.getPersonID()).stream()
                .map(MoviePerson::getMovieID).collect(Collectors.toSet());
        Set<Integer> mIds2 = moviePersonRepository.findByPersonID(p2.getPersonID()).stream()
                .map(MoviePerson::getMovieID).collect(Collectors.toSet());

        mIds1.retainAll(mIds2);

        return mIds1.stream()
                .map(id -> movieRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<Movie> findMoviesRanked(String name) {
        if (name == null || name.trim().isEmpty()) return Collections.emptyList();
        String n = name.trim();
        List<Movie> all = movieRepository.findByTitleContainingIgnoreCase(n);
        if (all.isEmpty()) return all;
        String nUpper = n.toUpperCase();
        // Ưu tiên: exact → starts-with → contains
        List<Movie> exact = all.stream().filter(m -> m.getTitle().equalsIgnoreCase(n)).collect(Collectors.toList());
        if (!exact.isEmpty()) return exact;
        List<Movie> starts = all.stream().filter(m -> m.getTitle().toUpperCase().startsWith(nUpper)).collect(Collectors.toList());
        if (!starts.isEmpty()) return starts;
        return all;
    }
}
