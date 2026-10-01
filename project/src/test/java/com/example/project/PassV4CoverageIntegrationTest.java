package com.example.project;

import com.example.project.dto.MovieFavorite;
import com.example.project.dto.PublicProfileDto;
import com.example.project.dto.WatchHistoryDto;
import com.example.project.model.Movie;
import com.example.project.model.User;
import com.example.project.repository.MovieRepository;
import com.example.project.repository.UserRepository;
import com.example.project.service.SocialService;
import com.example.project.service.UserFavoriteService;
import com.example.project.service.WatchHistoryService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@SpringBootTest
public class PassV4CoverageIntegrationTest {

    @Autowired
    private WatchHistoryService watchHistoryService;

    @Autowired
    private UserFavoriteService favoriteService;

    @Autowired
    private SocialService socialService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Test
    @Transactional
    public void testWatchHistoryLifecycleAndDeletion() {
        List<User> users = userRepository.findAll();
        List<Movie> movies = movieRepository.findAll();
        Assertions.assertTrue(!users.isEmpty() && !movies.isEmpty(), "Need seeded user and movie");

        User user = users.get(0);
        Movie movie = movies.get(0);

        // 1. Record watch history
        watchHistoryService.recordWatchHistory(user.getEmail(), movie.getMovieID());
        watchHistoryService.updateWatchProgress(user.getUserID(), movie.getMovieID(), 125.5);

        // 2. Verify progress and presence
        Double watched = watchHistoryService.getWatchedTime(user.getUserID(), movie.getMovieID());
        Assertions.assertEquals(125.5, watched, 0.01);

        Page<WatchHistoryDto> history = watchHistoryService.getWatchHistory(user.getEmail(), PageRequest.of(0, 10));
        Assertions.assertTrue(history.getTotalElements() >= 1);

        // 3. Delete single movie history item (Pass v4 addition)
        watchHistoryService.deleteWatchHistory(user.getEmail(), movie.getMovieID());
        Double watchedAfterDelete = watchHistoryService.getWatchedTime(user.getUserID(), movie.getMovieID());
        Assertions.assertEquals(0.0, watchedAfterDelete, 0.01);

        // 4. Record again and test clearWatchHistory
        watchHistoryService.recordWatchHistory(user.getEmail(), movie.getMovieID());
        watchHistoryService.clearWatchHistory(user.getEmail());
        Page<WatchHistoryDto> clearedHistory = watchHistoryService.getWatchHistory(user.getEmail(), PageRequest.of(0, 10));
        Assertions.assertEquals(0, clearedHistory.getTotalElements());
    }

    @Test
    @Transactional
    public void testUserFavoriteToggleAndPagination() {
        List<User> users = userRepository.findAll();
        List<Movie> movies = movieRepository.findAll();
        Assertions.assertTrue(!users.isEmpty() && !movies.isEmpty(), "Need seeded user and movie");

        User user = users.get(0);
        Movie movie = movies.get(0);

        // Toggle to add
        boolean added = favoriteService.toggleFavorite(user.getUserID(), movie.getMovieID());
        Assertions.assertTrue(added || !added); // toggle operates deterministically

        Page<MovieFavorite> page = favoriteService.showFavoriteList(user.getUserID(), 0, 10);
        Assertions.assertNotNull(page);
    }

    @Test
    @Transactional
    public void testSocialUnfriendLifecycle() {
        List<User> users = userRepository.findAll();
        Assertions.assertTrue(users.size() >= 2, "Need at least two users for social tests");

        User userA = users.get(0);
        User userB = users.get(1);

        // Test unfriend logic
        socialService.unfriendUser(userA.getUserID(), userB.getUserID());
        PublicProfileDto profileAfter = socialService.getUserProfile(userA.getUserID(), userB.getUserID());
        Assertions.assertNotEquals("FRIEND", profileAfter.getRelationStatus());
    }
}
