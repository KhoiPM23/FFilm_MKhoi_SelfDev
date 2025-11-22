package com.example.project.controller;

import com.example.project.dto.MovieRequest;
import com.example.project.model.Movie;
import com.example.project.service.MovieService;
import com.example.project.service.TmdbSyncService;

import jakarta.validation.Valid; // <-- THÊM IMPORT NÀY
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/content/movies") // API dành riêng cho Content Manager
@CrossOrigin(origins = "*") // Cho phép gọi từ bên ngoài
public class ContentMovieController {

    @Autowired
    private MovieService movieService;

    @Autowired
    private TmdbSyncService tmdbSyncService;
    /**
     * Lấy tất cả phim
     * Endpoint: GET /api/content/movies
     */
    @GetMapping
    public ResponseEntity<List<Movie>> getAllMovies() {
        return ResponseEntity.ok(movieService.getAllMovies());
    }

    /**
     * Lấy 1 phim bằng ID
     * Endpoint: GET /api/content/movies/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Movie> getMovieById(@PathVariable int id) {
        try {
            Movie movie = movieService.getMovieById(id);
            return ResponseEntity.ok(movie);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Kịch bản 1: Import phim từ TMDB
     * Endpoint: POST /api/content/movies/import/{tmdbId}
     */
    @PostMapping("/import/{tmdbId}")
    public ResponseEntity<?> importMovie(@PathVariable int tmdbId) {
        try {
            Movie importedMovie = movieService.importFromTmdb(tmdbId);
            return ResponseEntity.status(HttpStatus.CREATED).body(importedMovie);
        } catch (Exception e) {
            // Lỗi RuntimeException từ service (vd: Phim đã tồn tại) sẽ được GlobalExceptionHandler xử lý
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Kịch bản 2: Thêm phim thủ công
     * Endpoint: POST /api/content/movies
     * Body: (Xem MovieRequest.java)
     */
    @PostMapping
    public ResponseEntity<?> createMovie(@Valid @RequestBody MovieRequest movieRequest) {
        // Annotation @Valid sẽ tự động kích hoạt validation
        // Nếu thất bại, GlobalExceptionHandler sẽ bắt và trả về lỗi 400
        try {
            Movie createdMovie = movieService.createMovie(movieRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdMovie);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Cập nhật phim (sửa banner, metadata, url, v.v.)
     * Endpoint: PUT /api/content/movies/{id}
     * Body: (Xem MovieRequest.java)
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateMovie(@PathVariable int id, @Valid @RequestBody MovieRequest movieRequest) {
        // @Valid cũng được áp dụng cho update
        try {
            Movie updatedMovie = movieService.updateMovie(id, movieRequest);
            return ResponseEntity.ok(updatedMovie);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Xóa phim
     * Endpoint: DELETE /api/content/movies/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMovie(@PathVariable int id) {
        try {
            movieService.deleteMovie(id);
            return ResponseEntity.noContent().build(); // HTTP 204
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // [THÊM HÀM NÀY VÀO ContentMovieController.java]

    /**
     * Endpoint cho client-side (Live Suggestion) yêu cầu đồng bộ nhanh 1 danh sách ID.
     * Endpoint: POST /api/content/movies/sync
     * Body: [123, 456, 789]
     */
    @PostMapping("/sync")
    public ResponseEntity<?> syncMoviesByIds(@RequestBody List<Integer> tmdbIds) {
        try {
            movieService.syncTmdbIds(tmdbIds);
            return ResponseEntity.ok(Map.of("success", true, "message", "Đã yêu cầu đồng bộ " + tmdbIds.size() + " phim."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * API MỚI: Admin Trigger Bulk Scan
     * Gọi: POST /api/content/movies/scan-bulk?fromPage=1&toPage=50
     */
    @PostMapping("/scan-bulk")
    public ResponseEntity<?> scanBulkMovies(
            @RequestParam(defaultValue = "1") int fromPage,
            @RequestParam(defaultValue = "10") int toPage) {
        
        if (toPage < fromPage) {
            return ResponseEntity.badRequest().body(Map.of("message", "Page kết thúc phải lớn hơn Page bắt đầu"));
        }
        
        // Gọi service chạy ngầm (Async)
        tmdbSyncService.startBulkScan(fromPage, toPage);
        
        return ResponseEntity.ok(Map.of(
            "success", true, 
            "message", "Đã bắt đầu quét ngầm từ trang " + fromPage + " đến " + toPage + ". Bạn có thể đóng tab này."
        ));
    }

    /**
     * API MỚI: Dừng quét khẩn cấp
     */
    @PostMapping("/stop-scan")
    public ResponseEntity<?> stopScan() {
        tmdbSyncService.stopScan();
        return ResponseEntity.ok(Map.of("message", "Đã gửi lệnh dừng quét."));
    }
}