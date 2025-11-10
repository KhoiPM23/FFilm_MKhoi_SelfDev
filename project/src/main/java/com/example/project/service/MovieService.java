package com.example.project.service;

import com.example.project.dto.MovieRequest;
import com.example.project.dto.TmdbMovieDto;
import com.example.project.model.Category;
import com.example.project.model.Movie;
import com.example.project.model.Person;
import com.example.project.repository.CategoryRepository;
import com.example.project.repository.MovieRepository;
import com.example.project.repository.PersonRepository;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException; // <-- BỔ SUNG IMPORT NÀY
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional; 
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

    // (Các hàm CRUD cơ bản giữ nguyên)
    public List<Movie> getAllMovies() {
        return movieRepository.findAll();
    }
    public Movie getMovieById(int movieId) {
        return movieRepository.findById(movieId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phim với ID: " + movieId));
    }
    @Transactional
    public void deleteMovie(int movieId) {
        if (!movieRepository.existsById(movieId)) {
            throw new RuntimeException("Không tìm thấy phim để xóa");
        }
        movieRepository.deleteById(movieId);
    }
    @Transactional
    public Movie createMovie(MovieRequest request) {
        Movie movie = new Movie();
        mapRequestToMovie(request, movie);
        return movieRepository.save(movie);
    }
    @Transactional
    public Movie updateMovie(int movieId, MovieRequest request) {
        Movie movie = getMovieById(movieId); 
        mapRequestToMovie(request, movie);
        return movieRepository.save(movie);
    }

    // ---
    // HÀM 1: DÙNG CHO ADMIN (ContentMovieController)
    // ---
    @Transactional
    public Movie importFromTmdb(int tmdbId) {
        if (movieRepository.findByTmdbId(tmdbId).isPresent()) {
            throw new RuntimeException("Phim với TMDB ID: " + tmdbId + " đã tồn tại.");
        }
        
        TmdbMovieDto dto = tmdbService.fetchMovieById(tmdbId);
        Movie movie = new Movie();
        
        // SỬA LỖI VI PHẠM @NotBlank / @NotNull
        // 1. Tiêu đề (@NotBlank)
        String title = dto.getTitle();
        movie.setTitle( (title != null && !title.trim().isEmpty()) ? title.trim() : "N/A" );

        // 2. Mô tả (@NotBlank)
        String overview = dto.getOverview();
        movie.setDescription( (overview != null && !overview.trim().isEmpty()) ? overview.trim() : "Chưa có mô tả" );

        // 3. Ngày phát hành (@NotNull)
        Date releaseDate = parseDate(dto.getReleaseDate());
        movie.setReleaseDate( releaseDate != null ? releaseDate : new Date() ); // Mặc định là hôm nay

        // Các trường khác
        movie.setTmdbId(dto.getId());
        movie.setPosterPath(dto.getPosterPath());
        movie.setBackdropPath(dto.getBackdropPath());
        movie.setRating(dto.getRating());
        movie.setDuration(dto.getRuntime());
        movie.setUrl("CHƯA CẬP NHẬT"); // Thỏa mãn @NotBlank
        movie.setFree(false);

        return movieRepository.save(movie);
    }

    // ---
    // HÀM 2: DÙNG CHO LOGIC CŨ (Nếu có)
    // ---
    @Transactional
    public Movie findOrImportMovieByTmdbId(int tmdbId) {
        Optional<Movie> existingMovie = movieRepository.findByTmdbId(tmdbId);
        if (existingMovie.isPresent()) {
            return existingMovie.get();
        }
        
        TmdbMovieDto dto = tmdbService.fetchMovieById(tmdbId);
        Movie movie = new Movie();

        // SỬA LỖI VI PHẠM @NotBlank / @NotNull (Tương tự hàm trên)
        String title = dto.getTitle();
        movie.setTitle( (title != null && !title.trim().isEmpty()) ? title.trim() : "N/A" );
        
        String overview = dto.getOverview();
        movie.setDescription( (overview != null && !overview.trim().isEmpty()) ? overview.trim() : "Chưa có mô tả" );
        
        Date releaseDate = parseDate(dto.getReleaseDate());
        movie.setReleaseDate( releaseDate != null ? releaseDate : new Date() ); 
        
        movie.setTmdbId(dto.getId());
        movie.setPosterPath(dto.getPosterPath());
        movie.setBackdropPath(dto.getBackdropPath());
        movie.setRating(dto.getRating());
        movie.setDuration(dto.getRuntime());
        movie.setUrl("CHƯA CẬP NHẬT"); 
        movie.setFree(false); 

        return movieRepository.save(movie);
    }

    /**
     * SỬA LỖI P5 (HÀM HIỆN TẠI): Đảm bảo @NotBlank cho title và description
     */
    @Transactional
    public Movie syncMovieFromTmdbData(JSONObject tmdbListData) {
        if (tmdbListData == null) {
            return null;
        }
        
        int tmdbId = tmdbListData.optInt("id");
        if (tmdbId == 0) {
            return null; 
        }

        Optional<Movie> existingMovie = movieRepository.findByTmdbId(tmdbId);
        if (existingMovie.isPresent()) {
            return existingMovie.get(); 
        }

        Movie movie = new Movie();
        movie.setTmdbId(tmdbId);
        
        // ========== SỬA LỖI @NotBlank CHO TITLE ==========
        // Dùng optString(key) có thể trả về "" (chuỗi rỗng), vi phạm @NotBlank
        String title = tmdbListData.optString("title"); // Lấy giá trị, có thể là ""
        movie.setTitle( (title != null && !title.trim().isEmpty()) ? title.trim() : "N/A" ); // Kiểm tra và set fallback
        
        // ========== SỬA LỖI @NotBlank CHO DESCRIPTION ==========
        String overview = tmdbListData.optString("overview");
        movie.setDescription( (overview != null && !overview.trim().isEmpty()) ? overview.trim() : "Chưa có mô tả" );
        
        // (Các trường khác)
        movie.setPosterPath(tmdbListData.optString("poster_path", ""));
        movie.setBackdropPath(tmdbListData.optString("backdrop_path", ""));
        movie.setRating((float) tmdbListData.optDouble("vote_average", 0.0));
        
        // (Logic @NotNull cho releaseDate đã đúng)
        String releaseDateStr = tmdbListData.optString("release_date", "");
        Date releaseDate = parseDate(releaseDateStr);
        movie.setReleaseDate( releaseDate != null ? releaseDate : new Date() );
        
        // (Cố gắng lấy runtime)
        int runtime = 0;
        try {
            TmdbMovieDto dto = tmdbService.fetchMovieById(tmdbId);
            if (dto != null) {
                runtime = dto.getRuntime();
            }
        } catch (Exception e) {
            System.err.println("Warning (P5): Không thể fetch chi tiết (runtime) cho TMDB ID: " + tmdbId 
                             + ". Sẽ lưu phim với runtime=0. Lỗi: " + e.getMessage());
        }
        
        movie.setDuration(runtime); 
        movie.setUrl("CHƯA CẬP NHẬT"); // Thỏa mãn @NotBlank
        movie.setFree(false);

        return movieRepository.save(movie);
    }
    
    // (Hàm mapRequestToMovie giữ nguyên)
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
        
        if (request.getCategoryIds() != null) {
            List<Category> categories = categoryRepository.findAllById(request.getCategoryIds());
            movie.setCategories(categories);
        }
        
        if (request.getPersonIds() != null) {
            List<Person> persons = personRepository.findAllById(request.getPersonIds());
            movie.setPersons(persons);
        }
    }
    
    // (Hàm parseDate giữ nguyên)
    private Date parseDate(String dateString) {
        if (dateString == null || dateString.isEmpty() || dateString.equals("null")) {
            return null;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(dateString);
        } catch (ParseException e) { // <-- Sửa: Cần import java.text.ParseException
            return null;
        }
    }



    // Lấy phim theo ID for player.html
    public List<Movie> getRecommendedMovies(){
        return movieRepository.findTop10ByOrderByReleaseDateDesc();
    }
    public Movie getMovieId(int id){
        Optional<Movie> movieOtp = movieRepository.findById(id);

        if (movieOtp.isPresent()){
            return movieOtp.get();
        } else {
            throw new RuntimeException ("Không tìm thấy phim có ID: " + id);
        }
    }
}