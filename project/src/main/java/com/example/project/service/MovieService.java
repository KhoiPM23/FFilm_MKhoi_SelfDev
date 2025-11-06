package com.example.project.service;

import com.example.project.dto.MovieRequest;
import com.example.project.dto.TmdbMovieDto;
import com.example.project.model.Category;
import com.example.project.model.Movie;
import com.example.project.model.Person;
import com.example.project.repository.CategoryRepository;
import com.example.project.repository.MovieRepository;
import com.example.project.repository.PersonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MovieService {

    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private TmdbService tmdbService;

    // Lấy tất cả phim
    public List<Movie> getAllMovies() {
        return movieRepository.findAll();
    }

    // Lấy 1 phim
    public Movie getMovieById(int movieId) {
        return movieRepository.findById(movieId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phim với ID: " + movieId));
    }

    // Xóa phim
    @Transactional
    public void deleteMovie(int movieId) {
        if (!movieRepository.existsById(movieId)) {
            throw new RuntimeException("Không tìm thấy phim để xóa");
        }
        movieRepository.deleteById(movieId);
    }

    // Thêm phim thủ công
    @Transactional
    public Movie createMovie(MovieRequest request) {
        Movie movie = new Movie();
        // Ánh xạ các trường cơ bản
        mapRequestToMovie(request, movie);
        return movieRepository.save(movie);
    }

    // Cập nhật phim
    @Transactional
    public Movie updateMovie(int movieId, MovieRequest request) {
        Movie movie = getMovieById(movieId); // Lấy phim đã tồn tại
        // Ánh xạ các trường cập nhật
        mapRequestToMovie(request, movie);
        return movieRepository.save(movie);
    }

    // Import từ TMDB
    @Transactional
    public Movie importFromTmdb(int tmdbId) {
        // 1. Kiểm tra xem phim này đã import chưa
        if (movieRepository.findByTmdbId(tmdbId).isPresent()) {
            throw new RuntimeException("Phim với TMDB ID: " + tmdbId + " đã tồn tại.");
        }
        
        // 2. Gọi TMDB Service
        TmdbMovieDto dto = tmdbService.fetchMovieById(tmdbId);
        
        // 3. Chuyển DTO thành Entity
        Movie movie = new Movie();
        movie.setTmdbId(dto.getId());
        movie.setTitle(dto.getTitle());
        movie.setDescription(dto.getOverview());
        movie.setPosterPath(dto.getPosterPath());
        movie.setBackdropPath(dto.getBackdropPath());
        movie.setRating(dto.getRating());
        movie.setDuration(dto.getRuntime());
        movie.setReleaseDate(parseDate(dto.getReleaseDate()));
        
        // Cần Content Manager nhập URL video và set isFree sau
        movie.setUrl("CHƯA CẬP NHẬT"); // Đặt giá trị mặc định
        movie.setFree(false);

        // 4. Lưu vào DB
        return movieRepository.save(movie);
    }


    // Hàm helper để ánh xạ DTO -> Entity
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
        
        // Xử lý quan hệ Many-to-Many
        if (request.getCategoryIds() != null) {
            List<Category> categories = categoryRepository.findAllById(request.getCategoryIds());
            movie.setCategories(categories);
        }
        
        if (request.getPersonIds() != null) {
            List<Person> persons = personRepository.findAllById(request.getPersonIds());
            movie.setPersons(persons);
        }
    }
    
    // Hàm helper để parse ngày tháng
    private Date parseDate(String dateString) {
        if (dateString == null || dateString.isEmpty()) return null;
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(dateString);
        } catch (Exception e) {
            return null;
        }
    }
}