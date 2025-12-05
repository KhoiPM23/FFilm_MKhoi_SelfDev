package com.example.project.service;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.example.project.model.Genre;
import com.example.project.model.Movie;
import com.example.project.repository.GenreRepository;
import com.example.project.repository.MovieRepository;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TmdbSyncService {

    @Autowired private MovieService movieService;
    @Autowired private RestTemplate restTemplate;
    @Autowired private GenreRepository genreRepository;
    @Autowired private MovieRepository movieRepository;

    @Value("${tmdb.api.key}")
    private String apiKey;

    private final String BASE_URL = "https://api.themoviedb.org/3";
    
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    public void stopScan() {
        if (isRunning.get()) {
            stopRequested.set(true);
            System.out.println("[STOP COMMAND] Admin yêu cầu dừng quét!");
        }
    }

    @Async
    public CompletableFuture<String> startSmartScan() {
        if (isRunning.get()) {
            return CompletableFuture.completedFuture("Tiến trình đang chạy...");
        }
        
        isRunning.set(true);
        stopRequested.set(false); // Reset cờ dừng
        
        long startTime = System.currentTimeMillis();
        int totalImported = 0;
        int TARGET_LIMIT = 20000;

        try {
            System.out.println("[SMART SCAN] Bắt đầu. Mục tiêu: " + TARGET_LIMIT + " phim.");
            Set<Integer> processedIds = new HashSet<>();

            System.out.println("[PHASE 1] Quét phim Việt Nam & Collections...");
            
            String vietUrl = BASE_URL + "/discover/movie?api_key=" + apiKey 
                           + "&language=vi-VN&with_original_language=vi&sort_by=release_date.desc";
            totalImported += scanPages(vietUrl, 30, processedIds); 
            
            if (stopRequested.get()) return stopResult();

            String marvelUrl = BASE_URL + "/discover/movie?api_key=" + apiKey 
                             + "&language=vi-VN&with_companies=420&sort_by=revenue.desc";
            totalImported += scanPages(marvelUrl, 5, processedIds); 
            
            if (stopRequested.get()) return stopResult();

            System.out.println("[PHASE 2] Cân bằng thể loại...");
            List<Genre> allGenres = genreRepository.findAll();
            
            for (Genre genre : allGenres) {
                if (stopRequested.get()) break;
                
                int pages = (movieRepository.count() > TARGET_LIMIT) ? 1 : 25;
                
                System.out.println("   -> Quét thể loại: " + genre.getName() + " (" + pages + " trang)");
                
                String genreUrl = BASE_URL + "/discover/movie?api_key=" + apiKey 
                                + "&language=vi-VN&with_genres=" + genre.getTmdbGenreId()
                                + "&sort_by=vote_count.desc"; 
                
                totalImported += scanPages(genreUrl, pages, processedIds);
                Thread.sleep(100); 
            }
            
            if (stopRequested.get()) return stopResult();

        long currentDbCount = movieRepository.count();
        long missingMovies = TARGET_LIMIT - currentDbCount;

        if (missingMovies > 0) {
            System.out.println("[PHASE 3] Đang thiếu " + missingMovies + " phim. Bắt đầu chế độ QUÉT TỔNG LỰC...");

            int pagesNeeded = (int) (missingMovies / 20) + 20;

            if (pagesNeeded > 300) pagesNeeded = 600;

            System.out.println("   -> Hệ thống sẽ quét sâu " + pagesNeeded + " trang từ danh sách Popular...");

            String popularUrl = BASE_URL + "/movie/popular?api_key=" + apiKey + "&language=vi-VN";
            
            int filledCount = scanPages(popularUrl, pagesNeeded, processedIds);
            
            totalImported += filledCount;
            System.out.println("[PHASE 3] Đã lấp thêm được " + filledCount + " phim.");
        } else {
            System.out.println("Kho phim đã đầy (" + currentDbCount + "/" + TARGET_LIMIT + "). Bỏ qua Phase 3.");
        }

        } catch (Exception e) {
            System.err.println("[SMART SCAN] Lỗi: " + e.getMessage());
        } finally {
            isRunning.set(false);
            stopRequested.set(false);
        }

        long duration = (System.currentTimeMillis() - startTime) / 1000;
        return CompletableFuture.completedFuture("Hoàn tất Smart Scan! Đã xử lý: " + totalImported + " phim trong " + duration + "s.");
    }


    @Async
    public CompletableFuture<String> scanDailyUpdate() {
        if (isRunning.get()) return CompletableFuture.completedFuture("Hệ thống bận...");
        
        isRunning.set(true);
        stopRequested.set(false);
        System.out.println("[DAILY SCAN] Bắt đầu cập nhật phim mới...");

        try {
            Set<Integer> processedIds = new HashSet<>();
            
            String trendingUrl = BASE_URL + "/trending/movie/day?api_key=" + apiKey + "&language=vi-VN";
            scanPages(trendingUrl, 10, processedIds);
            
            if (stopRequested.get()) return stopResult();

            String nowPlayingUrl = BASE_URL + "/movie/now_playing?api_key=" + apiKey + "&language=vi-VN";
            scanPages(nowPlayingUrl, 10, processedIds);

        } catch (Exception e) {
            System.err.println("[DAILY SCAN] Lỗi: " + e.getMessage());
        } finally {
            isRunning.set(false);
            stopRequested.set(false);
        }
        
        return CompletableFuture.completedFuture("Đã hoàn tất cập nhật hàng ngày.");
    }


    private int scanPages(String baseUrl, int maxPages, Set<Integer> processedIds) throws InterruptedException {
        int count = 0;
        for (int i = 1; i <= maxPages; i++) {
            if (stopRequested.get()) {
                System.out.println("Phát hiện lệnh DỪNG khi đang quét trang " + i);
                break;
            }
            
            String separator = baseUrl.contains("?") ? "&" : "?";
            String url = baseUrl + separator + "page=" + i;
            
            count += processPage(url, processedIds);
            Thread.sleep(100); // Tránh DDOS
        }
        return count;
    }

    private int processPage(String url, Set<Integer> processedIds) {
        int count = 0;
        try {
            String resp = restTemplate.getForObject(url, String.class);
            if (resp == null) return 0;

            JSONObject json = new JSONObject(resp);
            JSONArray results = json.optJSONArray("results");
            if (results == null) return 0;
            
            LocalDate today = LocalDate.now();

            for (int i = 0; i < results.length(); i++) {
                if (stopRequested.get()) break;

                JSONObject item = results.getJSONObject(i);
                int tmdbId = item.optInt("id");

                if (processedIds.contains(tmdbId)) continue;

                Movie existingMovie = movieRepository.findByTmdbId(tmdbId).orElse(null);
                boolean isUpdate = (existingMovie != null);

                String releaseDateStr = item.optString("release_date", null);
                if (releaseDateStr != null && !releaseDateStr.isEmpty()) {
                    try {
                        LocalDate releaseDate = LocalDate.parse(releaseDateStr);
                        if (releaseDate.isAfter(today)) continue; 
                    } catch (DateTimeParseException e) { continue; }
                }

                if (!isUpdate) {
                    boolean isAdult = item.optBoolean("adult", false);
                    int voteCount = item.optInt("vote_count", 0);
                    String lang = item.optString("original_language", "en");
                    boolean isVietnamese = "vi".equalsIgnoreCase(lang);

                    if (isAdult && voteCount < 50) continue;

                    if (!isAdult && !isVietnamese && voteCount < 5) continue;
                }

                try {

                    Movie savedMovie = movieService.fetchAndSaveMovieDetail(tmdbId, existingMovie);

                    if (savedMovie != null) {
                        processedIds.add(tmdbId);
                        count++;
                        String action = isUpdate ? " Updated" : " Inserted";
                    }
                    
                    Thread.sleep(250);
                } catch (Exception e) {
                    System.err.println("Lỗi xử lý ID " + tmdbId + ": " + e.getMessage());
                }
            }
        } catch (Exception e) { 
            System.err.println("Lỗi processPage: " + e.getMessage());
        }
        return count;
    }

    
    private CompletableFuture<String> stopResult() {
        isRunning.set(false);
        return CompletableFuture.completedFuture("Đã tạm dừng theo lệnh Admin.");
    }

    // Giữ lại hàm Bulk Scan cũ 
    @Async
    public CompletableFuture<String> startBulkScan(int startPage, int endPage) {
        if (isRunning.get()) return CompletableFuture.completedFuture("Bận...");
        isRunning.set(true);
        stopRequested.set(false);
        
        try {
            Set<Integer> processedIds = new HashSet<>();
            for (int page = startPage; page <= endPage; page++) {
                if (stopRequested.get()) break;
                String url = BASE_URL + "/movie/popular?api_key=" + apiKey + "&language=vi-VN&page=" + page;
                processPage(url, processedIds);
                Thread.sleep(200);
            }
        } catch(Exception e) {} finally { isRunning.set(false); }
        return CompletableFuture.completedFuture("Đã quét xong.");
    }
    

    @Async
    public void syncDailyUpdates() {
        scanDailyUpdate(); 
    }

    public boolean isScanning() {
        return isRunning.get();
    }


    public void importMovieFromTmdb(Long tmdbId) {

        if (movieRepository.existsByTmdbId(tmdbId)) {
            throw new RuntimeException("Phim này đã tồn tại trong hệ thống (TMDB ID: " + tmdbId + ")");
        }

        try {
            Movie savedMovie = movieService.fetchAndSaveMovieDetail(tmdbId.intValue(), null);
            
            if (savedMovie == null) {
                 throw new RuntimeException("Không tìm thấy phim trên TMDB hoặc lỗi khi lưu dữ liệu.");
            }

        } catch (HttpClientErrorException.NotFound e) {
            throw new RuntimeException("TMDB ID " + tmdbId + " không tồn tại trên hệ thống TMDB!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Lỗi import: " + e.getMessage());
        }
    }
  
}