package com.example.project.service;

import com.example.project.dto.MovieSearchFilters;
import com.example.project.model.ConversationContext;
import com.example.project.model.Genre;
import com.example.project.model.Movie;
import com.example.project.model.Person;
import com.example.project.repository.MovieRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Component chuyên trách thuật toán đề xuất phim thông minh (Movie Intelligence Recommendation Engine):
 * 1. Chấm điểm tương đồng đa chiều (Director, Cast, Genre Jaccard, Rating, Tone, Recency).
 * 2. Tuân thủ nghiêm ngặt Negative Preferences (Loại trừ thể loại, diễn viên, đạo diễn).
 * 3. Sinh lời giải thích căn cứ trên dữ liệu thực tế (Grounded Explanation).
 * 4. Chẩn đoán mâu thuẫn tiêu chí khi kết quả rỗng (Constraint Conflict Diagnosis).
 */
@Component
public class MovieRecommendationEngine {

    private static final Logger log = LoggerFactory.getLogger(MovieRecommendationEngine.class);

    private final MovieRepository movieRepository;
    private final MovieService movieService;

    @Autowired
    public MovieRecommendationEngine(MovieRepository movieRepository, MovieService movieService) {
        this.movieRepository = movieRepository;
        this.movieService = movieService;
    }

    /**
     * Overload thuận tiện sử dụng ConversationContext và MovieService để lấy danh sách candidate movies.
     */
    public List<Movie> rankSimilarCandidates(Movie baseMovie, String modifier, ConversationContext context) {
        List<Movie> candidatePool = Collections.emptyList();
        if (baseMovie != null && baseMovie.getGenres() != null && !baseMovie.getGenres().isEmpty()) {
            MovieSearchFilters f = new MovieSearchFilters();
            f.setGenres(baseMovie.getGenres().stream().map(Genre::getName).collect(Collectors.toList()));
            candidatePool = movieService.findMoviesByFilters(f);
        }
        if (candidatePool.isEmpty()) {
            candidatePool = movieService.getHotMoviesForAI(20);
        }

        List<String> exGenres = (context != null && context.getExcludedGenres() != null)
                ? new ArrayList<>(context.getExcludedGenres()) : Collections.emptyList();
        List<String> exDirectors = (context != null && context.getExcludedDirectors() != null)
                ? new ArrayList<>(context.getExcludedDirectors()) : Collections.emptyList();
        Set<Integer> shownIds = (context != null && context.getShownMovieIds() != null)
                ? new HashSet<>(context.getShownMovieIds()) : Collections.emptySet();

        List<ScoredMovie> scored = rankSimilarCandidates(baseMovie, candidatePool, modifier, exGenres, exDirectors, shownIds);
        return scored.stream().map(ScoredMovie::getMovie).limit(5).collect(Collectors.toList());
    }

    /**
     * Tạo lời giải thích minh bạch dựa trên dữ liệu thực tế (Grounded Explanation).
     */
    public String buildGroundedExplanation(Movie movie, Movie baseMovie, String modifier) {
        if (movie == null) return "";
        StringBuilder sb = new StringBuilder();
        if (baseMovie != null && movie.getDirector() != null && !movie.getDirector().isEmpty()
                && movie.getDirector().equalsIgnoreCase(baseMovie.getDirector())) {
            sb.append("Cùng đạo diễn xuất sắc ").append(movie.getDirector()).append(". ");
        }
        if (movie.getRating() >= 7.5) {
            sb.append("Được đánh giá rất cao trên FFilm (⭐ ").append(String.format("%.1f", movie.getRating())).append("/10). ");
        }
        if (movie.getGenres() != null && !movie.getGenres().isEmpty()) {
            String gNames = movie.getGenres().stream().map(Genre::getName).limit(2).collect(Collectors.joining(", "));
            sb.append("Mang đậm phong cách ").append(gNames).append(". ");
        }
        return sb.toString().trim();
    }

    /**
     * Chấm điểm và xếp hạng ứng viên dựa trên phim gốc + tiêu chí phụ (modifier) + các ràng buộc loại trừ.
     */
    public List<ScoredMovie> rankSimilarCandidates(
            Movie baseMovie,
            List<Movie> candidatePool,
            String modifier,
            List<String> excludedGenres,
            List<String> excludedDirectors,
            Set<Integer> shownMovieIds) {

        if (candidatePool == null || candidatePool.isEmpty()) {
            return Collections.emptyList();
        }

        final int baseId = (baseMovie != null) ? baseMovie.getMovieID() : -1;
        final String baseDirector = (baseMovie != null && baseMovie.getDirector() != null) ? baseMovie.getDirector().toLowerCase() : "";
        final Set<String> baseCast = (baseMovie != null && baseMovie.getPersons() != null)
                ? baseMovie.getPersons().stream().map(p -> p.getFullName().toLowerCase()).collect(Collectors.toSet())
                : Collections.emptySet();
        final Set<String> baseGenres = (baseMovie != null && baseMovie.getGenres() != null)
                ? baseMovie.getGenres().stream().map(g -> g.getName().toLowerCase()).collect(Collectors.toSet())
                : Collections.emptySet();

        List<String> exG = (excludedGenres != null) ? excludedGenres.stream().map(String::toLowerCase).toList() : Collections.emptyList();
        List<String> exD = (excludedDirectors != null) ? excludedDirectors.stream().map(String::toLowerCase).toList() : Collections.emptyList();
        String modLower = (modifier != null) ? modifier.toLowerCase().trim() : "";

        List<ScoredMovie> scoredList = new ArrayList<>();

        for (Movie m : candidatePool) {
            // Không đề xuất lại chính phim gốc hoặc phim đã hiển thị gần đây
            if (m.getMovieID() == baseId || (shownMovieIds != null && shownMovieIds.contains(m.getMovieID()))) {
                continue;
            }

            // Kiểm tra Negative Preferences: Loại trừ thể loại
            if (!exG.isEmpty() && m.getGenres() != null) {
                boolean hasExcludedGenre = m.getGenres().stream().anyMatch(g -> exG.stream().anyMatch(ex -> g.getName().toLowerCase().contains(ex)));
                if (hasExcludedGenre) continue;
            }

            // Kiểm tra Negative Preferences: Loại trừ đạo diễn
            if (!exD.isEmpty() && m.getDirector() != null) {
                String dLower = m.getDirector().toLowerCase();
                boolean hasExcludedDirector = exD.stream().anyMatch(dLower::contains);
                if (hasExcludedDirector) continue;
            }

            // Bắt đầu tính điểm Relevance Score
            double score = 0.0;
            StringBuilder reasonBuilder = new StringBuilder();

            // 1. Cùng Đạo diễn (Trọng số cao: 3.5)
            if (!baseDirector.isEmpty() && m.getDirector() != null && m.getDirector().toLowerCase().contains(baseDirector)) {
                score += 3.5;
                reasonBuilder.append("Cùng đạo diễn ").append(m.getDirector()).append("; ");
            }

            // 2. Diễn viên đóng chung (Trọng số 2.0 mỗi diễn viên)
            if (!baseCast.isEmpty() && m.getPersons() != null) {
                long sharedCastCount = m.getPersons().stream()
                        .filter(p -> baseCast.contains(p.getFullName().toLowerCase()))
                        .count();
                if (sharedCastCount > 0) {
                    score += sharedCastCount * 2.0;
                    String sharedNames = m.getPersons().stream()
                            .filter(p -> baseCast.contains(p.getFullName().toLowerCase()))
                            .map(Person::getFullName)
                            .limit(2)
                            .collect(Collectors.joining(", "));
                    reasonBuilder.append("Cùng diễn viên ").append(sharedNames).append("; ");
                }
            }

            // 3. Jaccard Thể loại (Trọng số 2.5)
            if (!baseGenres.isEmpty() && m.getGenres() != null && !m.getGenres().isEmpty()) {
                Set<String> mGenres = m.getGenres().stream().map(g -> g.getName().toLowerCase()).collect(Collectors.toSet());
                long intersection = mGenres.stream().filter(baseGenres::contains).count();
                long union = baseGenres.size() + mGenres.size() - intersection;
                double jaccard = union > 0 ? ((double) intersection / union) : 0.0;
                score += jaccard * 2.5;
            }

            // 4. Chất lượng / Rating (Trọng số 1.5)
            if (m.getRating() > 0) {
                score += (m.getRating() / 10.0) * 1.5;
            }

            // 5. Cùng quốc gia (Trọng số 0.5)
            if (baseMovie != null && baseMovie.getCountry() != null && m.getCountry() != null &&
                    baseMovie.getCountry().equalsIgnoreCase(m.getCountry())) {
                score += 0.5;
            }

            // 6. Tùy biến Modifier
            if (!modLower.isEmpty()) {
                String mGenresStr = (m.getGenres() != null)
                        ? m.getGenres().stream().map(Genre::getName).collect(Collectors.joining(", ")).toLowerCase()
                        : "";

                if (modLower.contains("nhẹ hơn") || modLower.contains("nhẹ nhàng") || modLower.contains("chill") || modLower.contains("hài")) {
                    if (mGenresStr.contains("hài") || mGenresStr.contains("gia đình") || mGenresStr.contains("hoạt hình") || mGenresStr.contains("lãng mạn")) {
                        score += 3.0;
                        reasonBuilder.append("Không khí nhẹ nhàng, thư giãn; ");
                    }
                    if (mGenresStr.contains("kinh dị") || mGenresStr.contains("chiến tranh")) {
                        score -= 5.0; // phạt nặng
                    }
                } else if (modLower.contains("căng thẳng") || modLower.contains("kịch tính") || modLower.contains("gay cấn")) {
                    if (mGenresStr.contains("gây cấn") || mGenresStr.contains("hành động") || mGenresStr.contains("bí ẩn")) {
                        score += 3.0;
                        reasonBuilder.append("Kịch tính, căng thẳng hấp dẫn; ");
                    }
                } else if (modLower.contains("ngắn hơn") || modLower.contains("ngắn thôi")) {
                    if (baseMovie != null && m.getDuration() > 0 && m.getDuration() < baseMovie.getDuration()) {
                        score += 2.5;
                        reasonBuilder.append("Thời lượng ngắn gọn (").append(m.getDuration()).append(" phút); ");
                    } else if (m.getDuration() > 0 && m.getDuration() <= 100) {
                        score += 2.0;
                        reasonBuilder.append("Thời lượng dưới 100 phút; ");
                    }
                } else if (modLower.contains("mới hơn") || modLower.contains("gần đây")) {
                    if (m.getReleaseDate() != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy");
                        int yr = Integer.parseInt(sdf.format(m.getReleaseDate()));
                        if (yr >= 2020) score += 2.0;
                        reasonBuilder.append("Phát hành gần đây (").append(yr).append("); ");
                    }
                } else if (modLower.contains("rating cao") || modLower.contains("điểm cao") || modLower.contains("cao nhất") || modLower.contains("hay hơn")) {
                    score += (m.getRating() >= 8.0 ? 3.0 : 1.0);
                    reasonBuilder.append("Điểm đánh giá cao (").append(String.format("%.1f", m.getRating())).append("/10); ");
                }
            }

            if (score > 0) {
                String cleanReason = reasonBuilder.toString().trim();
                if (cleanReason.endsWith(";")) cleanReason = cleanReason.substring(0, cleanReason.length() - 1);
                scoredList.add(new ScoredMovie(m, score, cleanReason));
            }
        }

        // Sắp xếp giảm dần theo điểm Relevance
        scoredList.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return scoredList;
    }

    /**
     * Chẩn đoán nguyên nhân khi tổ hợp bộ lọc trả về 0 kết quả và đề xuất nới lỏng.
     */
    public String diagnoseConstraintConflict(MovieSearchFilters filters) {
        StringBuilder diag = new StringBuilder("Rất tiếc, kho phim FFilm hiện chưa có phim nào thỏa mãn đồng thời tất cả các tiêu chí sau:\n\n");
        List<String> criteria = new ArrayList<>();

        if (filters.getDirector() != null && !filters.getDirector().isEmpty()) {
            criteria.add("• Đạo diễn: **" + filters.getDirector() + "**");
        }
        if (filters.getActor() != null && !filters.getActor().isEmpty()) {
            criteria.add("• Diễn viên: **" + filters.getActor() + "**");
        }
        if (filters.getCountry() != null && !filters.getCountry().isEmpty()) {
            criteria.add("• Quốc gia: **" + filters.getCountry() + "**");
        }
        if (filters.getGenres() != null && !filters.getGenres().isEmpty()) {
            criteria.add("• Thể loại: **" + String.join(", ", filters.getGenres()) + "**");
        }
        if (filters.getMinRating() != null && filters.getMinRating() > 0) {
            criteria.add("• Điểm đánh giá tối thiểu: **" + filters.getMinRating() + "/10**");
        }
        if (filters.getMaxDuration() != null && filters.getMaxDuration() > 0) {
            criteria.add("• Thời lượng tối đa: **" + filters.getMaxDuration() + " phút**");
        }
        if (filters.getYearFrom() != null && filters.getYearFrom() > 1900) {
            criteria.add("• Phát hành sau năm: **" + filters.getYearFrom() + "**");
        }

        diag.append(String.join("\n", criteria)).append("\n\n");
        diag.append("💡 **Gợi ý nới lỏng**: Bạn có thể bỏ bớt một điều kiện (ví dụ: mở rộng thời lượng, giảm mức rating hoặc không giới hạn quốc gia) để FFilm hiển thị những tác phẩm phù hợp nhất!");

        return diag.toString();
    }

    public static class ScoredMovie {
        private final Movie movie;
        private final double score;
        private final String groundedReason;

        public ScoredMovie(Movie movie, double score, String groundedReason) {
            this.movie = movie;
            this.score = score;
            this.groundedReason = groundedReason;
        }

        public Movie getMovie() { return movie; }
        public double getScore() { return score; }
        public String getGroundedReason() { return groundedReason; }
    }
}
