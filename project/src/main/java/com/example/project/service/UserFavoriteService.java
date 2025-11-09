package com.example.project.service;

import com.example.project.dto.AddUserFavoriteRequest;
import com.example.project.model.Movie;
import com.example.project.model.User;
import com.example.project.model.UserFavorite;
import com.example.project.repository.FavoriteRepository; // DÙNG 1 TÊN DUY NHẤT
import com.example.project.repository.MovieRepository;
import com.example.project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor // TỐT HƠN @Autowired
public class UserFavoriteService {

    private FavoriteRepository favoriteRepository;
    private UserRepository userRepository;
    private MovieRepository movieRepository;

    // THÊM FAVORITE
    public boolean addFavorite(AddUserFavoriteRequest req) {
        Optional<User> userOpt = userRepository.findById(req.getUserID());
        Optional<Movie> movieOpt = movieRepository.findById(req.getMovieID());
        if (userOpt.isEmpty() || movieOpt.isEmpty()) {
            return false;
        }

        if (favoriteRepository.existsByUserIdAndMovieId(req.getUserID(), req.getMovieID())) {
            return false;
        }

        UserFavorite uf = new UserFavorite();
        uf.setUserID(req.getUserID());
        uf.setMovieID(req.getMovieID());
        uf.setCreateAt(req.getCreateAt());

        favoriteRepository.save(uf);
        return true;
    }

    public Page<Movie> showFavoriteList(Integer userId, int page, int size) {
        if (userId == null) {
            throw new IllegalArgumentException("userId không được null");
        }
        if (page < 0)
            page = 0;
        if (size <= 0)
            size = 10;

        Pageable pageable = PageRequest.of(page, size);
        return favoriteRepository.findMoviesByUserId(userId, pageable);
    }
}