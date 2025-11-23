package com.example.project.service;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.project.model.Genre;
import com.example.project.model.Movie;
import com.example.project.repository.GenreRepository;
import com.example.project.repository.MovieRepository;

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
    
    // Cờ trạng thái
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    // 1. HÀM DỪNG QUÉT
    public void stopScan() {
        if (isRunning.get()) {
            stopRequested.set(true);
            System.out.println("🛑 [STOP COMMAND] Admin yêu cầu dừng quét!");
        }
    }

    /**
     * 2. CHIẾN LƯỢC QUÉT THÔNG MINH (SMART SCAN - 3 GIAI ĐOẠN)
     * Mục tiêu: 5000 phim chất lượng, đa dạng thể loại.
     */
    @Async
    public CompletableFuture<String> startSmartScan() {
        if (isRunning.get()) {
            return CompletableFuture.completedFuture("Tiến trình đang chạy...");
        }
        
        isRunning.set(true);
        stopRequested.set(false); // Reset cờ dừng
        
        long startTime = System.currentTimeMillis();
        int totalImported = 0;
        int TARGET_LIMIT = 5000;

        try {
            System.out.println("🚀 [SMART SCAN] Bắt đầu. Mục tiêu: " + TARGET_LIMIT + " phim.");
            Set<Integer> processedIds = new HashSet<>();

            // --- GIAI ĐOẠN 1: HÀNG NHÀ & HÀNG HIỆU (Ưu tiên cao nhất) ---
            System.out.println("👉 [PHASE 1] Quét phim Việt Nam & Collections...");
            
            // 1.1. Phim Việt Nam (Lấy mới nhất về trước)
            String vietUrl = BASE_URL + "/discover/movie?api_key=" + apiKey 
                           + "&language=vi-VN&with_original_language=vi&sort_by=release_date.desc";
            totalImported += scanPages(vietUrl, 5, processedIds); // Quét 5 trang
            
            if (stopRequested.get()) return stopResult();

            // 1.2. Marvel (Ví dụ company ID 420)
            String marvelUrl = BASE_URL + "/discover/movie?api_key=" + apiKey 
                             + "&language=vi-VN&with_companies=420&sort_by=revenue.desc";
            totalImported += scanPages(marvelUrl, 3, processedIds); // Quét 3 trang
            
            if (stopRequested.get()) return stopResult();

            // --- GIAI ĐOẠN 2: CÂN BẰNG THỂ LOẠI (Quan trọng nhất) ---
            System.out.println("👉 [PHASE 2] Cân bằng thể loại...");
            List<Genre> allGenres = genreRepository.findAll();
            
            for (Genre genre : allGenres) {
                if (stopRequested.get()) break;
                
                //  Nếu kho chưa đầy, quét sâu 15 trang (300 phim) mỗi thể loại để làm nền tảng vững chắc
                int pages = (movieRepository.count() > TARGET_LIMIT) ? 1 : 15;
                
                System.out.println("   -> Quét thể loại: " + genre.getName() + " (" + pages + " trang)");
                
                String genreUrl = BASE_URL + "/discover/movie?api_key=" + apiKey 
                                + "&language=vi-VN&with_genres=" + genre.getTmdbGenreId()
                                + "&sort_by=vote_count.desc"; // Lấy phim nổi tiếng nhất (kinh điển)
                
                totalImported += scanPages(genreUrl, pages, processedIds);
                Thread.sleep(100); // Nghỉ nhẹ
            }
            
            if (stopRequested.get()) return stopResult();

            // --- GIAI ĐOẠN 3: LẤP ĐẦY TỔNG LỰC (FORCE FILL) ---
        long currentDbCount = movieRepository.count();
        long missingMovies = TARGET_LIMIT - currentDbCount;

        if (missingMovies > 0) {
            System.out.println("🔥 [PHASE 3] Đang thiếu " + missingMovies + " phim. Bắt đầu chế độ QUÉT TỔNG LỰC...");

            // Công thức: (Số phim thiếu / 20 phim mỗi trang) + 20 trang bù trừ (tránh trùng lặp)
            int pagesNeeded = (int) (missingMovies / 20) + 20;

            // Giới hạn an toàn: Không quét quá 300 trang (6000 phim) trong 1 lần để tránh treo máy quá lâu
            if (pagesNeeded > 300) pagesNeeded = 300;

            System.out.println("   -> Hệ thống sẽ quét sâu " + pagesNeeded + " trang từ danh sách Popular...");

            String popularUrl = BASE_URL + "/movie/popular?api_key=" + apiKey + "&language=vi-VN";
            
            // Gọi hàm quét với số trang đã tính toán
            int filledCount = scanPages(popularUrl, pagesNeeded, processedIds);
            
            totalImported += filledCount;
            System.out.println("✅ [PHASE 3] Đã lấp thêm được " + filledCount + " phim.");
        } else {
            System.out.println("✅ Kho phim đã đầy (" + currentDbCount + "/" + TARGET_LIMIT + "). Bỏ qua Phase 3.");
        }

        } catch (Exception e) {
            System.err.println("❌ [SMART SCAN] Lỗi: " + e.getMessage());
        } finally {
            isRunning.set(false);
            stopRequested.set(false);
        }

        long duration = (System.currentTimeMillis() - startTime) / 1000;
        return CompletableFuture.completedFuture("Hoàn tất Smart Scan! Đã xử lý: " + totalImported + " phim trong " + duration + "s.");
    }

    /**
     * 3. CHIẾN LƯỢC QUÉT NÔNG (DAILY UPDATE)
     * Mục tiêu: Cập nhật phim mới ra mắt, phim đang hot hôm nay.
     */
    @Async
    public CompletableFuture<String> scanDailyUpdate() {
        if (isRunning.get()) return CompletableFuture.completedFuture("Hệ thống bận...");
        
        isRunning.set(true);
        stopRequested.set(false);
        System.out.println("☀️ [DAILY SCAN] Bắt đầu cập nhật phim mới...");

        try {
            Set<Integer> processedIds = new HashSet<>();
            
            // 1. Trending Day (Phim đang hot hôm nay)
            String trendingUrl = BASE_URL + "/trending/movie/day?api_key=" + apiKey + "&language=vi-VN";
            scanPages(trendingUrl, 5, processedIds);
            
            if (stopRequested.get()) return stopResult();

            // 2. Now Playing (Phim đang chiếu rạp)
            String nowPlayingUrl = BASE_URL + "/movie/now_playing?api_key=" + apiKey + "&language=vi-VN";
            scanPages(nowPlayingUrl, 5, processedIds);

        } catch (Exception e) {
            System.err.println("❌ [DAILY SCAN] Lỗi: " + e.getMessage());
        } finally {
            isRunning.set(false);
            stopRequested.set(false);
        }
        
        return CompletableFuture.completedFuture("Đã hoàn tất cập nhật hàng ngày.");
    }

    // --- HÀM HỖ TRỢ ---

    // Hàm quét nhiều trang
    private int scanPages(String baseUrl, int maxPages, Set<Integer> processedIds) throws InterruptedException {
        int count = 0;
        for (int i = 1; i <= maxPages; i++) {
            if (stopRequested.get()) {
                System.out.println("⚠️ Phát hiện lệnh DỪNG khi đang quét trang " + i);
                break;
            }
            
            String separator = baseUrl.contains("?") ? "&" : "?";
            String url = baseUrl + separator + "page=" + i;
            
            count += processPage(url, processedIds);
            Thread.sleep(100); // Tránh DDOS
        }
        return count;
    }

    // Hàm xử lý từng trang
    private int processPage(String url, Set<Integer> processedIds) {
        int count = 0;
        try {
            String resp = restTemplate.getForObject(url, String.class);
            if (resp == null) return 0;

            JSONObject json = new JSONObject(resp);
            JSONArray results = json.optJSONArray("results");
            if (results == null) return 0;

            for (int i = 0; i < results.length(); i++) {
                if (stopRequested.get()) break; // Dừng ngay trong vòng lặp item

                JSONObject item = results.getJSONObject(i);
                int tmdbId = item.optInt("id");

                if (processedIds.contains(tmdbId)) continue;
                
                // Gọi MovieService để Upsert (Ghi đè hoặc Tạo mới)
                // Hàm syncMovieFromList bên MovieService đã có logic ghi đè
                try {
                    // [FIX] BƯỚC 1: Tìm xem phim này đã có trong DB chưa?
                    Movie existingMovie = movieRepository.findByTmdbId(tmdbId).orElse(null);

                    // [FIX] BƯỚC 2: Truyền phim cũ vào (nếu có) để hàm này thực hiện UPDATE thay vì
                    // INSERT
                    // Nếu existingMovie != null -> Hệ thống sẽ update đè rating, duration,
                    // poster...
                    // Nếu existingMovie == null -> Hệ thống tạo mới bình thường.
                    Movie savedMovie = movieService.fetchAndSaveMovieDetail(tmdbId, existingMovie);

                    // [Logic đếm và check ảnh null hôm trước đã thêm]
                    if (savedMovie != null) {
                        processedIds.add(tmdbId);
                        count++;
                    }

                    Thread.sleep(400);
                } catch (Exception e) {
                    System.err.println("Lỗi xử lý ID " + tmdbId + ": " + e.getMessage());
                }

                processedIds.add(tmdbId);
                count++;
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

    // Giữ lại hàm Bulk Scan cũ cho Admin nếu cần
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
    
    // Giữ hàm cũ để Scheduler gọi (nếu chưa sửa Scheduler)
    @Async
    public void syncDailyUpdates() {
        scanDailyUpdate(); // Chuyển tiếp sang hàm mới
    }

    // [THÊM MỚI] Hàm này để Controller hỏi trạng thái
    public boolean isScanning() {
        return isRunning.get();
    }
}