// package com.example.project.service;

// import com.example.project.repository.MovieRepository;
// import org.json.JSONArray;
// import org.json.JSONObject;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.boot.context.event.ApplicationReadyEvent;
// import org.springframework.context.event.EventListener;
// import org.springframework.scheduling.annotation.Async;
// import org.springframework.stereotype.Service;
// import org.springframework.web.client.RestTemplate;

// import java.util.Arrays;
// import java.util.HashSet;
// import java.util.List;
// import java.util.Set;
// import java.util.concurrent.CompletableFuture;

// @Service
// public class TmdbSeederService {

//     @Autowired private MovieRepository movieRepository;
//     @Autowired private MovieService movieService;
//     @Autowired private RestTemplate restTemplate;

//     @Value("${tmdb.api.key}")
//     private String apiKey;

//     private final String BASE_URL = "https://api.themoviedb.org/3";
//     private static final int TARGET_MOVIES = 10000; // Mục tiêu 10,000 phim

//     // Danh sách ID các Collection nổi tiếng
//     private static final List<Integer> POPULAR_COLLECTIONS = Arrays.asList(
//         86311, 1241, 10, 230, 9485, 87359, 52984, 556, 645, 119, 
//         295, 86066, 33514, 422834, 131295, 131292, 131296, 729322, 912503, 531330
//     );

//     // Danh sách ID các Studio lớn
//     private static final List<Integer> POPULAR_COMPANIES = Arrays.asList(
//         2, 33, 174, 4, 25, 1
//     );

//     @Async
//     @EventListener(ApplicationReadyEvent.class)
//     public CompletableFuture<Void> seedDatabase() {
//         long currentCount = movieRepository.count();
        
//         // [SỬA ĐỔI QUAN TRỌNG] Chỉ dừng nếu đã đủ TARGET_MOVIES (10,000)
//         if (currentCount >= TARGET_MOVIES) { 
//             System.out.println("✅ [SEEDER] Kho phim đã có " + currentCount + " bộ. Đủ chỉ tiêu.");
//             return CompletableFuture.completedFuture(null);
//         }

//         System.out.println("🚀 [SEEDER] Tiếp tục nạp dữ liệu... Hiện có: " + currentCount + ". Mục tiêu: " + TARGET_MOVIES);
//         long startTime = System.currentTimeMillis();
        
//         // Load các ID đã có để tránh check lại (Optional, giúp chạy nhanh hơn chút)
//         // Ở đây ta dùng Set mới cho đơn giản, logic check trùng đã có trong processPage
//         Set<Integer> processedTmdbIds = new HashSet<>();

//         try {
//             // 1. PHIM VIỆT NAM
//             System.out.println("🇻🇳 [SEEDER 1/6] Quét phim Việt Nam...");
//             scanMoviesByDiscovery(processedTmdbIds, "with_original_language=vi", 100, 0); 

//             // 2. COLLECTION (Franchise)
//             System.out.println("📦 [SEEDER 2/6] Quét Franchise...");
//             for (Integer colId : POPULAR_COLLECTIONS) {
//                 scanCollection(colId, processedTmdbIds);
//                 Thread.sleep(50);
//             }

//             // 3. TOP RATED & POPULAR
//             System.out.println("⭐ [SEEDER 3/6] Quét Top Rated & Popular...");
//             scanMoviesByEndpoint(processedTmdbIds, "/movie/popular", 300, 50); 
//             scanMoviesByEndpoint(processedTmdbIds, "/movie/top_rated", 200, 50); 
            
//             // 4. STUDIO LỚN
//             System.out.println("🏢 [SEEDER 4/6] Quét Studio lớn...");
//             for (Integer compId : POPULAR_COMPANIES) {
//                 if (movieRepository.count() >= TARGET_MOVIES) break;
//                 scanMoviesByDiscovery(processedTmdbIds, "with_companies=" + compId + "&sort_by=revenue.desc", 30, 20);
//             }

//             // 5. THỂ LOẠI
//             System.out.println("🎨 [SEEDER 5/6] Bổ sung Thể loại...");
//             int[] genres = {28, 12, 16, 35, 80, 18, 10751, 14, 36, 27, 10749, 878, 53};
//             for (int genreId : genres) {
//                 if (movieRepository.count() >= TARGET_MOVIES) break;
//                 scanMoviesByDiscovery(processedTmdbIds, "with_genres=" + genreId + "&sort_by=vote_count.desc", 50, 20);
//             }

//             // 6. [MỚI] VÉT CẠN (FILL REMAINING)
//             // Nếu chạy hết các bước trên mà vẫn chưa đủ, quét tiếp phim Popular ở các trang sâu hơn
//             // Giảm điều kiện minVoteCount xuống thấp hơn để lấy được nhiều phim hơn
//             if (movieRepository.count() < TARGET_MOVIES) {
//                 System.out.println("🔄 [SEEDER 6/6] Vét cạn phim Popular để đạt mục tiêu...");
//                 // Quét sâu tới 500 trang, giảm vote yêu cầu xuống 10
//                 scanMoviesByEndpoint(processedTmdbIds, "/movie/popular", 500, 10);
//             }

//         } catch (Exception e) {
//             System.err.println("❌ [SEEDER] Lỗi: " + e.getMessage());
//         }

//         long duration = (System.currentTimeMillis() - startTime) / 1000;
//         long finalCount = movieRepository.count();
//         System.out.println("🎉 [SEEDER] KẾT THÚC! Tổng số phim hiện tại: " + finalCount + ". (" + duration + "s)");
        
//         return CompletableFuture.completedFuture(null);
//     }

//     // --- Helper: Quét Collection ---
//     private void scanCollection(int collectionId, Set<Integer> processedIds) {
//         try {
//             String url = BASE_URL + "/collection/" + collectionId + "?api_key=" + apiKey + "&language=vi-VN";
//             String resp = restTemplate.getForObject(url, String.class);
//             if (resp == null) return;

//             JSONObject json = new JSONObject(resp);
//             JSONArray parts = json.optJSONArray("parts");
//             if (parts == null) return;

//             for (int i = 0; i < parts.length(); i++) {
//                 int tmdbId = parts.getJSONObject(i).optInt("id");
//                 // Kiểm tra nhanh trong set local trước khi gọi service
//                 if (!processedIds.contains(tmdbId)) {
//                     movieService.getMovieOrSync(tmdbId);
//                     processedIds.add(tmdbId);
//                 }
//             }
//         } catch (Exception e) { /* Ignore */ }
//     }

//     // --- Helper: Quét theo Endpoint ---
//     private void scanMoviesByEndpoint(Set<Integer> processedIds, String endpoint, int maxPages, int minVoteCount) {
//         for (int page = 1; page <= maxPages; page++) {
//             // Kiểm tra realtime số lượng trong DB để dừng sớm
//             if (movieRepository.count() >= TARGET_MOVIES) return;
            
//             try {
//                 String url = BASE_URL + endpoint + "?api_key=" + apiKey + "&language=vi-VN&page=" + page;
//                 processPage(url, processedIds, minVoteCount);
//                 if (page % 10 == 0) Thread.sleep(100);
//             } catch (Exception e) { /* Ignore */ }
//         }
//     }

//     // --- Helper: Quét theo Discovery ---
//     private void scanMoviesByDiscovery(Set<Integer> processedIds, String params, int maxPages, int minVoteCount) {
//         for (int page = 1; page <= maxPages; page++) {
//             if (movieRepository.count() >= TARGET_MOVIES) return;
            
//             try {
//                 String url = BASE_URL + "/discover/movie?api_key=" + apiKey + "&language=vi-VN&page=" + page + "&" + params;
//                 processPage(url, processedIds, minVoteCount);
//                 if (page % 10 == 0) Thread.sleep(100);
//             } catch (Exception e) { /* Ignore */ }
//         }
//     }

//     // --- CORE PROCESSOR ---
//     private void processPage(String url, Set<Integer> processedIds, int minVoteCount) {
//         try {
//             String resp = restTemplate.getForObject(url, String.class);
//             if (resp == null) return;

//             JSONObject json = new JSONObject(resp);
//             JSONArray results = json.optJSONArray("results");
//             if (results == null) return;

//             for (int i = 0; i < results.length(); i++) {
//                 if (movieRepository.count() >= TARGET_MOVIES) return;

//                 JSONObject item = results.getJSONObject(i);
//                 int tmdbId = item.optInt("id");

//                 if (processedIds.contains(tmdbId)) continue;

//                 int voteCount = item.optInt("vote_count", 0);
//                 if (voteCount < minVoteCount) continue;

//                 String poster = item.optString("poster_path");
//                 if (poster == null || poster.isEmpty() || "null".equals(poster)) continue;

//                 movieService.getMovieOrSync(tmdbId);
//                 processedIds.add(tmdbId);
//             }
//         } catch (Exception e) { /* Ignore */ }
//     }
// }