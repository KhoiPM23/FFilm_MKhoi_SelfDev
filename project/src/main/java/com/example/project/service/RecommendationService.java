package com.example.project.service;

import com.example.project.model.Movie;
import com.example.project.model.User;
import com.example.project.repository.MovieRepository;
import com.example.project.repository.UserReactionRepository;
import com.example.project.repository.UserRepository;
import com.example.project.repository.WatchHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// THÊM DÒNG NÀY
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

@Service
public class RecommendationService {
        private final UserRepository userRepository;
        private final WatchHistoryRepository watchHistoryRepository;
        private final UserReactionRepository userReactionRepository;
        private final MovieRepository movieRepository;

        public RecommendationService(UserRepository userRepository,
                        WatchHistoryRepository watchHistoryRepository,
                        UserReactionRepository userReactionRepository,
                        MovieRepository movieRepository) {
                this.userRepository = userRepository;
                this.watchHistoryRepository = watchHistoryRepository;
                this.userReactionRepository = userReactionRepository;
                this.movieRepository = movieRepository;
        }

        @Transactional(readOnly = true)
        public List<Movie> getRecommendations(Integer userID) {

                // THAY ĐỔI 3: Tạo một yêu cầu trang, ví dụ: trang 0, 20 phần tử
                // Đây chính là cách bạn "chỉ lấy danh sách" một cách an toàn
                Pageable top20 = PageRequest.of(0, 20);

                // 1. Lấy danh sách phim đã xem (để loại trừ)
                Set<Integer> watchedMovieIDs = watchHistoryRepository.findWatchedMovieIDsByUserID(userID);

                // 2. Lấy danh sách phim đã "Like"
                Set<Integer> likedMovieIDs = userReactionRepository.findLikedMovieIDsByUserID(userID);

                // 4. Xây dựng "Hồ sơ sở thích"
                Set<Integer> profileGenreIDs = movieRepository.findGenreIDsByMovieIDs(likedMovieIDs);

                // 6. Lấy gợi ý chính
                // THAY ĐỔI 6: Truyền `top20` và dùng `.getContent()`
                Page<Movie> moviePage = movieRepository.findRecommendations(profileGenreIDs, watchedMovieIDs, top20);
                return moviePage.getContent(); // Trả về List<Movie>
        }
}