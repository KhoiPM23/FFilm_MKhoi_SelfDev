package com.example.project;

import com.example.project.dto.MovieSearchFilters;
import com.example.project.model.Movie;
import com.example.project.repository.MovieRepository;
import com.example.project.service.MovieService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * MEASUREMENT ONLY. Prints "PERF|" lines (no assertions on speed) so results are real, not estimated.
 * Runs against the configured dev database => [LIMITATION] not a production-scale dataset.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
public class AIQueryPerformanceTest {

    @Autowired private MovieService movieService;
    @Autowired private MovieRepository movieRepository;
    @Autowired private EntityManagerFactory emf;

    private static MovieSearchFilters f(Consumer<MovieSearchFilters> c) {
        MovieSearchFilters x = new MovieSearchFilters();
        c.accept(x);
        return x;
    }

    @Test
    @DisplayName("PERF: findMoviesByFilters query path measurement")
    void measureFilterPath() {
        Statistics st = emf.unwrap(SessionFactory.class).getStatistics();
        long catalog = movieRepository.count();
        System.out.println("PERF|catalogSize=" + catalog);
        // Warm-up (not reported): pool, JIT, schema metadata, SQL Server buffer cache for the small query
        movieService.findMoviesByFilters(f(x -> { x.setGenres(List.of("Hành Động")); x.setCountry("South Korea"); }));

        Map<String, MovieSearchFilters> scenarios = new LinkedHashMap<>();
        scenarios.put("P1a Action", f(x -> x.setGenres(List.of("Hành Động"))));
        scenarios.put("P1b Action+KR", f(x -> { x.setGenres(List.of("Hành Động")); x.setCountry("South Korea"); }));
        scenarios.put("P1c Action+KR+rating>=8", f(x -> { x.setGenres(List.of("Hành Động")); x.setCountry("South Korea"); x.setMinRating(8f); }));
        scenarios.put("P1d Action+KR+rating>=8+dur<=120", f(x -> { x.setGenres(List.of("Hành Động")); x.setCountry("South Korea"); x.setMinRating(8f); x.setMaxDuration(120); }));
        scenarios.put("P2a Action+exclude Horror (Java-side)", f(x -> { x.setGenres(List.of("Hành Động")); x.setExcludedGenres(List.of("Kinh Dị")); }));
        scenarios.put("P2b Action+exclude director (Java-side)", f(x -> { x.setGenres(List.of("Hành Động")); x.setExcludedDirectors(List.of("Christopher Nolan")); }));
        scenarios.put("P2c rating>=7 only (large set)", f(x -> x.setMinRating(7f)));
        scenarios.put("P1a-repeat Action (run LAST)", f(x -> x.setGenres(List.of("Hành Động"))));

        for (Map.Entry<String, MovieSearchFilters> e : scenarios.entrySet()) {
            List<Long> times = new ArrayList<>();
            int returned = 0;
            long entityLoads = 0, statements = 0, collLoads = 0;
            for (int run = 0; run < 4; run++) {           // run 0 = cold, 1..3 = warm
                st.clear();
                long t0 = System.nanoTime();
                List<Movie> r = movieService.findMoviesByFilters(e.getValue());
                long ms = (System.nanoTime() - t0) / 1_000_000;
                times.add(ms);
                if (run == 1) {
                    returned = r.size();
                    entityLoads = st.getEntityLoadCount();
                    statements = st.getPrepareStatementCount();
                    collLoads = st.getCollectionLoadCount();
                }
            }
            double warmAvg = (times.get(1) + times.get(2) + times.get(3)) / 3.0;
            System.out.println("PERF|" + e.getKey()
                    + "|returned=" + returned
                    + "|entityLoads=" + entityLoads
                    + "|collectionLoads=" + collLoads
                    + "|sqlStatements=" + statements
                    + "|allRunsMs=" + times
                    + "|coldMs=" + times.get(0)
                    + "|warmAvgMs=" + String.format("%.1f", warmAvg));
        }
    }
}
