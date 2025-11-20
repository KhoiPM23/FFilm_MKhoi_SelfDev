Nội dung này giải thích rõ lý do, luồng hoạt động (flow) và cung cấp code mẫu để chuyển đổi từ việc dùng `tmdbId` (bên ngoài) sang `movieID` (khóa chính nội bộ) cho các bảng quan hệ như **Yêu thích, Lịch sử, Reaction**.

-----

# 📄 GUIDELINE: CHUYỂN ĐỔI LOGIC SỬ DỤNG MOVIE\_ID (INTERNAL PK)

**Người viết:** Phan Minh Khôi
**Mục tiêu:** Thống nhất logic xử lý dữ liệu phim trong Database.
**Áp dụng cho:** Tính năng Yêu thích (Favorites), Reaction (Like/Dislike), Lịch sử xem (History), Bình luận (Comment).

-----

## 1\. Vấn đề hiện tại (Tại sao phải đổi?)

Hiện tại, một số bảng (như `UserReaction`, `UserFavorite`) đang cố gắng lưu trực tiếp `tmdbId` hoặc join với bảng Movie qua cột `tmdbId`.

  * **Rủi ro:** `tmdbId` không phải là khóa chính (Primary Key) của bảng `Movie` trong DB của chúng ta. Việc join qua một cột không phải PK làm giảm hiệu năng và gây khó khăn khi cấu hình khóa ngoại (Foreign Key) trong JPA/Hibernate.
  * **Giải pháp:** Tất cả các bảng vệ tinh (Reaction, Favorite, History...) **PHẢI** liên kết với bảng `Movie` thông qua `movieID` (ID tự tăng nội bộ của hệ thống), không dùng `tmdbId`.

-----

## 2\. Nguyên tắc hoạt động (Flow chuẩn)

Dù Frontend vẫn gửi `tmdbId` (vì FE lấy từ API TMDB), nhưng Backend phải chịu trách nhiệm chuyển đổi (Mapping) trước khi lưu vào DB.

**Quy trình xử lý tại Service:**

1.  **Nhận Request:** Controller nhận `tmdbId` từ Frontend.
2.  **Tìm kiếm (Lookup):** Service tìm trong bảng `Movie` xem đã có phim nào có `tmdbId` này chưa.
      * *Nếu có:* Lấy ra Entity `Movie` (có chứa `movieID`).
      * *Nếu chưa:* Gọi hàm `syncMovieFromList` (Lazy Sync) hoặc `importMovie` để tạo mới phim vào DB -\> Lấy được Entity `Movie` mới.
3.  **Lưu quan hệ:** Gán Entity `Movie` này vào bảng quan hệ (ví dụ `UserReaction`). Hibernate sẽ tự động lấy `movieID` để làm khóa ngoại.

-----

## 3\. Code mẫu triển khai (Implementation Guide)

### A. Sửa Entity (Model)

Không lưu `Integer tmdbId` trong các bảng quan hệ nữa. Hãy dùng đối tượng `Movie`.

**❌ Cách cũ (Không nên dùng):**

```java
// UserReaction.java
@Column(name = "tmdb_id")
private Integer tmdbId; // Sai: Không liên kết chặt chẽ với bảng Movie
```

**✅ Cách mới (Chuẩn):**

```java
// UserReaction.java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "movieID", nullable = false) // Trỏ thẳng vào PK của bảng Movie
private Movie movie;
```

### B. Sửa Service (Logic chuyển đổi)

Đây là phần quan trọng nhất để ae xử lý logic.

**Ví dụ: Chức năng Like/Dislike (UserReactionService)**

```java
@Transactional
public void toggleLike(int userId, int tmdbId) {
    // BƯỚC 1: Tìm phim trong DB nội bộ bằng tmdbId
    Movie movie = movieRepository.findByTmdbId(tmdbId)
        .orElseGet(() -> {
            // Nếu chưa có trong DB, tự động đồng bộ từ TMDB về ngay lập tức
            // (Sử dụng lại hàm sync mà chúng ta đã viết ở MovieService)
            return movieService.getMovieOrSync(tmdbId);
        });

    // BƯỚC 2: Xử lý logic với object Movie đã có (lúc này movie đã có movieID)
    User user = userRepository.findById(userId).orElseThrow(...);

    Optional<UserReaction> reactionOpt = reactionRepository.findByUserAndMovie(user, movie);
    
    if (reactionOpt.isPresent()) {
        // Update existing
        UserReaction reaction = reactionOpt.get();
        reaction.setLiked(!reaction.isLiked());
        reactionRepository.save(reaction);
    } else {
        // Create new
        UserReaction newReaction = new UserReaction();
        newReaction.setUser(user);
        newReaction.setMovie(movie); // Hibernate tự lấy movie.getMovieID() để lưu
        newReaction.setLiked(true);
        reactionRepository.save(newReaction);
    }
}
```

### C. Sửa Repository

Query theo Object `Movie` thay vì số nguyên `tmdbId`.

```java
// UserReactionRepository.java
// Tìm theo Object User và Object Movie
Optional<UserReaction> findByUserAndMovie(User user, Movie movie);

// Hoặc nếu muốn query native thì phải join qua movieID
@Query("SELECT r FROM UserReaction r WHERE r.user.id = :uid AND r.movie.tmdbId = :tmdbId")
Optional<UserReaction> findByUidAndTmdbId(@Param("uid") int uid, @Param("tmdbId") int tmdbId);
```

-----

## 4\. Checklist cho anh em (To-do List)

1.  [ ] **UserFavorite:** Kiểm tra `UserFavoriteService`. Đảm bảo khi user bấm "Thêm vào yêu thích", hệ thống tìm `movieID` từ `tmdbId` trước khi lưu.
2.  [ ] **WatchHistory:** Khi user xem phim (`/movie/player/{tmdbId}`), controller nhận `tmdbId`. Hãy đảm bảo `WatchHistoryService` convert sang `Movie` entity trước khi lưu vào bảng lịch sử.
3.  [ ] **Player Controller:** URL vẫn giữ là `/movie/player/{tmdbId}` để đẹp và chuẩn SEO/API, nhưng bên trong hàm controller phải gọi `movieService.getMovieByTmdbId(tmdbId)` để lấy dữ liệu nội bộ.

-----

**Lưu ý:** Việc đồng bộ này giúp chúng ta nhất quán dữ liệu. Nếu sau này cần đổi thông tin phim, chỉ cần sửa 1 chỗ trong bảng `Movie`, tất cả lịch sử/yêu thích sẽ tự cập nhật theo.