package com.example.project.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TmdbSyncService {

    @Autowired private MovieService movieService;
    @Autowired private RestTemplate restTemplate;

    @Value("${tmdb.api.key}")
    private String apiKey;

    private final String BASE_URL = "https://api.themoviedb.org/3";
    
    // Cờ kiểm soát để dừng quét khẩn cấp nếu cần
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /**
     * 1. CHỨC NĂNG QUÉT SÂU (ADMIN TRIGGER)
     * Quét phim theo dải trang (Ví dụ: Từ page 1 -> 500)
     */
    @Async // Chạy luồng riêng, không treo giao diện Admin
    public CompletableFuture<String> startBulkScan(int startPage, int endPage) {
        if (isRunning.get()) {
            return CompletableFuture.completedFuture("Tiến trình đang chạy, vui lòng đợi...");
        }
        isRunning.set(true);
        
        int totalImported = 0;
        long startTime = System.currentTimeMillis();
        Set<Integer> processedIds = new HashSet<>();

        System.out.println("🚀 [ADMIN SCAN] Bắt đầu quét từ trang " + startPage + " đến " + endPage);

        try {
            for (int page = startPage; page <= endPage; page++) {
                if (!isRunning.get()) break; // Cho phép Admin dừng giữa chừng

                // Quét API Popular để lấy danh sách phim chất lượng
                String url = BASE_URL + "/movie/popular?api_key=" + apiKey + "&language=vi-VN&page=" + page;
                int count = processPage(url, processedIds, false);
                totalImported += count;

                System.out.println("✅ [ADMIN SCAN] Hoàn thành Page " + page + " - Đã thêm/update: " + count);
                
                // Sleep nhẹ để tránh 429 Too Many Requests từ TMDB
                Thread.sleep(200); 
            }
        } catch (Exception e) {
            System.err.println("❌ [ADMIN SCAN] Lỗi: " + e.getMessage());
        } finally {
            isRunning.set(false);
        }

        long duration = (System.currentTimeMillis() - startTime) / 1000;
        return CompletableFuture.completedFuture("Hoàn tất quét! Tổng xử lý: " + totalImported + " phim trong " + duration + "s.");
    }

    /**
     * 2. CHỨC NĂNG TỰ ĐỘNG (CRON JOB 3H SÁNG)
     * SỬA ĐỔI: Thêm tham số forceUpdate = true để ghi đè dữ liệu cũ
     */
    @Async
    public void syncDailyUpdates() {
        if (isRunning.get()) return;
        isRunning.set(true);
        System.out.println("⏰ [DAILY SYNC] Bắt đầu cập nhật phim mới (Force Update)...");

        try {
            Set<Integer> processedIds = new HashSet<>();
            
            // Quét Trending - Force Update = true
            String trendingUrl = BASE_URL + "/trending/movie/day?api_key=" + apiKey + "&language=vi-VN";
            processPage(trendingUrl, processedIds, true); // <--- True để ghi đè

            // Quét 20 trang Popular - Force Update = true
            for (int i = 1; i <= 20; i++) {
                String popularUrl = BASE_URL + "/movie/popular?api_key=" + apiKey + "&language=vi-VN&page=" + i;
                processPage(popularUrl, processedIds, true); // <--- True để ghi đè
                Thread.sleep(200);
            }
            
            // Top Rated thì chỉ cần quét thêm, không cần update quá thường xuyên (để false hoặc true tùy bạn)
            for (int i = 1; i <= 10; i++) {
                String topUrl = BASE_URL + "/movie/top_rated?api_key=" + apiKey + "&language=vi-VN&page=" + i;
                processPage(topUrl, processedIds, false); // <--- False: Chỉ thêm mới nếu chưa có
                Thread.sleep(200);
            }

        } catch (Exception e) {
            System.err.println("❌ [DAILY SYNC] Lỗi: " + e.getMessage());
        } finally {
            isRunning.set(false);
            System.out.println("⏰ [DAILY SYNC] Kết thúc cập nhật.");
        }
    }

    // Hàm dừng khẩn cấp
    public void stopScan() {
        isRunning.set(false);
    }

    // --- CORE PROCESSOR (ĐÃ CẬP NHẬT LOGIC) ---
    // Thêm tham số boolean forceUpdate
    private int processPage(String url, Set<Integer> processedIds, boolean forceUpdate) {
        int count = 0;
        try {
            String resp = restTemplate.getForObject(url, String.class);
            if (resp == null) return 0;

            JSONObject json = new JSONObject(resp);
            JSONArray results = json.optJSONArray("results");
            if (results == null) return 0;

            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.getJSONObject(i);
                int tmdbId = item.optInt("id");

                if (processedIds.contains(tmdbId)) continue;

                if (forceUpdate) {
                    // Nếu là Daily Sync: Gọi hàm update trực tiếp (Ghi đè)
                    // Bạn cần thêm hàm updateMovieFromTmdb vào MovieService (xem bên dưới)
                    // Hoặc dùng tạm cách xóa đi tạo lại (không khuyến khích),
                    // Cách tốt nhất: Gọi hàm fetchAndSaveMovieDetail của MovieService
                    // Vì hàm fetchAndSaveMovieDetail trong MovieService là private, 
                    // ta sẽ sửa MovieService một chút để public nó hoặc tạo wrapper.
                    
                    // GIẢI PHÁP ĐƠN GIẢN NHẤT VỚI CODE HIỆN TẠI:
                    // Hàm getMovieOrSync của bạn đang check "N/A".
                    // Ta sẽ gọi hàm forceUpdateMovie(tmdbId) (sẽ tạo ở bước sau)
                    movieService.forceUpdateMovie(tmdbId);
                } else {
                    // Nếu là Bulk Scan: Giữ nguyên logic cũ (Chỉ thêm nếu thiếu)
                    movieService.getMovieOrSync(tmdbId);
                }
                
                processedIds.add(tmdbId);
                count++;
            }
        } catch (Exception e) { 
            System.err.println("Lỗi processPage: " + e.getMessage());
        }
        return count;
    }
}