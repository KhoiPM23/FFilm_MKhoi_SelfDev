package com.example.project.controller;

import com.example.project.dto.MovieRequest;
import com.example.project.model.Movie;
import com.example.project.service.MovieService;
import com.example.project.service.TmdbSyncService;

import jakarta.validation.Valid; 
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/content/movies") 
@CrossOrigin(origins = "*") 
public class ContentMovieController {

    @Autowired
    private MovieService movieService;

    @Autowired
    private TmdbSyncService tmdbSyncService;

    @GetMapping
    public ResponseEntity<List<Movie>> getAllMovies() {
        return ResponseEntity.ok(movieService.getAllMovies());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Movie> getMovieById(@PathVariable int id) {
        try {
            Movie movie = movieService.getMovieById(id);
            return ResponseEntity.ok(movie);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }


    @PostMapping("/import-tmdb") 
@ResponseBody 
public ResponseEntity<?> importFromTmdb(@RequestParam Long tmdbId) {
    try {
        tmdbSyncService.importMovieFromTmdb(tmdbId);
        
        return ResponseEntity.ok(Collections.singletonMap("message", "Import phim thành công!"));
        
    } catch (RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Collections.singletonMap("error", e.getMessage()));
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Collections.singletonMap("error", "Lỗi hệ thống: " + e.getMessage()));
    }
}

    @PostMapping
    public ResponseEntity<?> createMovie(@Valid @RequestBody MovieRequest movieRequest) {

        try {
            Movie createdMovie = movieService.createMovie(movieRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdMovie);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateMovie(@PathVariable int id, @Valid @RequestBody MovieRequest movieRequest) {
 
        try {
            Movie updatedMovie = movieService.updateMovie(id, movieRequest);
            return ResponseEntity.ok(updatedMovie);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMovie(@PathVariable int id) {
        try {
            movieService.deleteMovie(id);
            return ResponseEntity.noContent().build(); 
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }


    @PostMapping("/sync")
    public ResponseEntity<?> syncMoviesByIds(@RequestBody List<Integer> tmdbIds) {
        try {
            movieService.syncTmdbIds(tmdbIds);
            return ResponseEntity.ok(Map.of("success", true, "message", "Đã yêu cầu đồng bộ " + tmdbIds.size() + " phim."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }


    @PostMapping("/scan-bulk")
    public ResponseEntity<?> scanBulkMovies(
            @RequestParam(defaultValue = "1") int fromPage,
            @RequestParam(defaultValue = "10") int toPage) {
        
        if (toPage < fromPage) {
            return ResponseEntity.badRequest().body(Map.of("message", "Page kết thúc phải lớn hơn Page bắt đầu"));
        }

        tmdbSyncService.startBulkScan(fromPage, toPage);
        
        return ResponseEntity.ok(Map.of(
            "success", true, 
            "message", "Đã bắt đầu quét ngầm từ trang " + fromPage + " đến " + toPage + ". Bạn có thể đóng tab này."
        ));
    }

    
    @GetMapping("/search")
    public ResponseEntity<List<Movie>> searchMovies(@RequestParam String query) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.ok(movieService.getAllMovies());
        }
        List<Movie> results = movieService.searchMoviesByTitle(query.trim());
        return ResponseEntity.ok(results);
    }

    @GetMapping("/manage")
    public ResponseEntity<Page<Movie>> getMoviesForManagement(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isFree,
            @RequestParam(defaultValue = "movieID") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        
        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        PageRequest pageable = PageRequest.of(page, size, sort);
        
        Page<Movie> moviePage = movieService.getAdminMovies(keyword, isFree, pageable);
        return ResponseEntity.ok(moviePage);
    }

    @PostMapping("/scan-smart")
    public ResponseEntity<?> triggerSmartScan() {
        tmdbSyncService.startSmartScan();
        return ResponseEntity.ok(Map.of("message", "Đã khởi động Quét Thông Minh (Mục tiêu 5000 phim)..."));
    }

    @PostMapping("/scan-daily")
    public ResponseEntity<?> triggerDailyScan() {
        tmdbSyncService.scanDailyUpdate();
        return ResponseEntity.ok(Map.of("message", "Đã khởi động Cập nhật Hàng Ngày..."));
    }

    @PostMapping("/stop-scan")
    public ResponseEntity<?> stopScan() {
        tmdbSyncService.stopScan();
        return ResponseEntity.ok(Map.of("message", "Đã gửi lệnh DỪNG quét."));
    }

    @GetMapping("/scan-status")
    public ResponseEntity<?> getScanStatus() {
        boolean running = tmdbSyncService.isScanning(); 
        return ResponseEntity.ok(Map.of("isRunning", running));
    }
}