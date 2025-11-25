package com.example.project.repository;

import com.example.project.model.MoviePerson;
import com.example.project.model.MoviePersonId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MoviePersonRepository extends JpaRepository<MoviePerson, MoviePersonId> {
    List<MoviePerson> findByMovieID(int movieID);
    Optional<MoviePerson> findByMovieIDAndPersonID(int movieID, int personID);
    List<MoviePerson> findByPersonID(int personID);
    void deleteByMovieID(int movieID); // Để xóa role cũ khi sync lại
}