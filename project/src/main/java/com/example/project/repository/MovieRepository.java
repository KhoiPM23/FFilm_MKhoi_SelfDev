package com.example.project.repository;

import com.example.project.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Integer>, JpaSpecificationExecutor<Movie> {

  Optional<Movie> findByTmdbId(Integer tmdbId);

  Optional<Movie> findByMovieID(Integer movieID);

  List<Movie> findTop20ByOrderByReleaseDateDesc();

  @Query("SELECT m.tmdbId FROM Movie m WHERE m.tmdbId IN :tmdbIds")
  List<Integer> findTmdbIdsIn(@Param("tmdbIds") List<Integer> tmdbIds);

  // [FIX] Sửa lỗi tìm kiếm Tiếng Việt (case-insensitive) bằng Native Query
  @Query(value = "SELECT * FROM Movie m WHERE UPPER(m.title) LIKE N'%' + UPPER(:title) + '%'", nativeQuery = true)
  List<Movie> findByTitleContainingIgnoreCase(@Param("title") String title);

  List<Movie> findByTmdbIdIn(List<Integer> tmdbIds);

  Page<Movie> findAllByOrderByRatingDesc(Pageable pageable);

  Page<Movie> findAllByOrderByReleaseDateDesc(Pageable pageable);

  Page<Movie> findAllByGenres_TmdbGenreId(Integer tmdbGenreId, Pageable pageable);

  @Query("SELECT DISTINCT g.genreID FROM Movie m JOIN m.genres g WHERE m.movieID IN :movieIDs")
  Set<Integer> findGenreIDsByMovieIDs(@Param("movieIDs") Set<Integer> movieIDs);

  @Query("""
          SELECT m
          FROM Movie m JOIN m.genres g
          WHERE g.genreID IN :profileGenreIDs
            AND m.movieID NOT IN :watchedMovieIDs
          GROUP BY m
          ORDER BY m.releaseDate DESC,
                   COUNT(DISTINCT g.genreID) DESC,
                   m.rating DESC
      """)
  Page<Movie> findRecommendations(
      @Param("profileGenreIDs") Set<Integer> profileGenreIDs,
      @Param("watchedMovieIDs") Set<Integer> watchedMovieIDs,
      Pageable pageable);
}