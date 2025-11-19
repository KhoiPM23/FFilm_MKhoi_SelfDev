package com.example.project.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Service;

import com.example.project.dto.AddUserFavoriteRequest;
import com.example.project.dto.MovieFavorite;
import com.example.project.model.Movie;
import com.example.project.model.User;
import com.example.project.model.UserFavorite;
import com.example.project.repository.FavoriteRepository;
import com.example.project.repository.MovieRepository;
import com.example.project.repository.UserRepository;

import lombok.RequiredArgsConstructor;

//Đoạn này của Linh

@Service
@RequiredArgsConstructor // TỐT HƠN @Autowired
public class UserFavoriteService {

    @Autowired
    private FavoriteRepository favoriteRepository;

    public boolean addFavorite(AddUserFavoriteRequest req) {

        if (favoriteRepository.existsByUserIDAndMovieID(req.getUserID(), req.getMovieID())) {
            return false;
        }

        UserFavorite uf = new UserFavorite();
        uf.setUserID(req.getUserID());
        uf.setMovieID(req.getMovieID());
        uf.setCreateAt(req.getCreateAt());
        System.out.println("Movie ID = " + uf.getMovieID());
        favoriteRepository.save(uf);
        return true;
    }

    public Page<MovieFavorite> showFavoriteList(Integer userID, Integer page, Integer size) {
        if (userID == null) {
            throw new IllegalArgumentException("userId không được null");
        }
        if (page < 0)
            page = 0;
        if (size <= 0)
            size = 10;
        Pageable pageable = PageRequest.of(page, size);
        return favoriteRepository.findMoviesByUserID(userID, pageable);
    }

}