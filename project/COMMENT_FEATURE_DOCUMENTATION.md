# Tài liệu Tính năng Comment

## 📋 Tổng quan
Hệ thống Comment cho phép người dùng đã đăng nhập bình luận về phim. Comments được hiển thị real-time và tự động cập nhật.

## 🏗️ Kiến trúc

### 1. **Repository Layer** (`CommentRepository.java`)
```java
- findByMovieIdOrderByCreateAtDesc() - Lấy comments đã duyệt của phim
- findByMovie_MovieIDOrderByCreateAtDesc() - Lấy tất cả comments
- findByUser_UserIDOrderByCreateAtDesc() - Lấy comments của user
- countByMovie_MovieID() - Đếm số lượng comments
```

### 2. **Service Layer** (`CommentService.java`)
```java
- getCommentsByMovieId(movieId) - Lấy danh sách comments
- addComment(movieId, userId, content) - Thêm comment mới
- deleteComment(commentId, userId) - Xóa comment (soft delete)
- countCommentsByMovieId(movieId) - Đếm comments
```

**Validation:**
- Kiểm tra user và movie tồn tại
- Chỉ cho phép user chủ sở hữu xóa comment
- Tự động set status = "approved"

### 3. **Controller Layer** (`CommentController.java`)

#### API Endpoints:

**GET `/api/comments/movie/{movieId}`**
- Lấy tất cả comments của một phim
- Response:
```json
{
  "success": true,
  "count": 5,
  "comments": [...]
}
```

**POST `/api/comments`**
- Thêm comment mới (yêu cầu đăng nhập)
- Request Body:
```json
{
  "movieId": 1,
  "content": "Phim hay quá!"
}
```
- Response:
```json
{
  "success": true,
  "message": "Thêm bình luận thành công",
  "comment": {...}
}
```

**DELETE `/api/comments/{commentId}`**
- Xóa comment (yêu cầu đăng nhập)
- Chỉ user chủ sở hữu mới xóa được

**GET `/api/comments/count/{movieId}`**
- Đếm số lượng comments

### 4. **Frontend** (`player.html` + `comment-handler.js`)

#### Giao diện:
- ✅ Form nhập comment (chỉ hiện khi đã đăng nhập)
- ✅ Thông báo yêu cầu đăng nhập
- ✅ Danh sách comments với avatar, tên, thời gian
- ✅ Đếm số lượng comments real-time
- ✅ Format thời gian thông minh (vừa xong, X phút trước...)

#### JavaScript Features:
- Auto-load comments khi trang load
- Submit bằng Enter hoặc nút Gửi
- Disable button khi input trống
- Loading spinner khi submit
- Success/Error notifications
- XSS protection (escape HTML)
- Real-time comment count update

## 🔐 Bảo mật

1. **Authentication**: Kiểm tra session trước khi thêm/xóa comment
2. **Authorization**: Chỉ user chủ sở hữu mới xóa được comment của mình
3. **XSS Protection**: Escape HTML trong content
4. **Validation**: Không cho phép comment trống

## 📊 Database Schema

```sql
Comment Table:
- commentID (PK, Auto-increment)
- content (NVARCHAR, NOT NULL)
- createAt (DATE, NOT NULL)
- status (VARCHAR - approved/deleted/pending)
- userID (FK -> Users)
- movieID (FK -> Movie)
- parent_commentID (FK -> Comment, nullable)
```

## 🚀 Cách sử dụng

### Người dùng chưa đăng nhập:
1. Vào trang xem phim
2. Thấy thông báo "Bạn cần đăng nhập để bình luận"
3. Click link đăng nhập

### Người dùng đã đăng nhập:
1. Vào trang xem phim
2. Nhập nội dung vào ô "Viết bình luận..."
3. Nhấn Enter hoặc nút "Gửi"
4. Comment xuất hiện ngay lập tức
5. Các user khác sẽ thấy comment này

## 🧪 Testing

### Test scenarios:
1. ✅ User chưa đăng nhập không thể comment
2. ✅ User đã đăng nhập có thể comment
3. ✅ Comment hiển thị real-time
4. ✅ Đếm số lượng comments chính xác
5. ✅ Format thời gian đúng
6. ✅ XSS protection hoạt động
7. ✅ Validation input rỗng

### Manual Testing:
```bash
# 1. Start server
./mvnw spring-boot:run

# 2. Đăng nhập vào hệ thống

# 3. Vào một phim bất kỳ
http://localhost:8080/movie/player/1

# 4. Thử các thao tác:
- Nhập comment và gửi
- Xem comment hiển thị
- Mở tab mới và xem comment đã xuất hiện
- Thử xóa comment (nếu implement)
```

## 🔧 Troubleshooting

### Comment không hiển thị?
- Kiểm tra console browser (F12) xem có lỗi API không
- Kiểm tra server logs
- Verify movieID có đúng không

### Không submit được comment?
- Kiểm tra đã đăng nhập chưa
- Verify session user có tồn tại không
- Check network tab xem request có gửi đi không

### Lỗi 401 Unauthorized?
- Session đã hết hạn, cần đăng nhập lại
- Cookie bị xóa

## 📝 TODO / Future Improvements

- [ ] Thêm tính năng reply comment (nested comments)
- [ ] Like/dislike comments
- [ ] Report comments
- [ ] Edit comments
- [ ] Admin moderation (approve/reject)
- [ ] Pagination cho nhiều comments
- [ ] Real-time updates (WebSocket)
- [ ] Rich text editor
- [ ] Mention users (@username)
- [ ] Upload ảnh trong comment

## 📞 Support
Nếu có vấn đề, vui lòng kiểm tra:
1. Server logs: `target/logs/`
2. Browser console (F12)
3. Network requests (F12 -> Network tab)
