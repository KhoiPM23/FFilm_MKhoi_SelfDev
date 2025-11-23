# 🔧 Hướng dẫn Test Comment Feature

## ⚠️ BẮT BUỘC: Restart Server

```bash
# Dừng server hiện tại (Ctrl+C trong terminal)
# Sau đó chạy lại:
./mvnw spring-boot:run
```

## 📝 Các lỗi đã sửa:

### 1. ✅ JSON Circular Reference (LỖI CHÍNH)
**Vấn đề:** Comment → User → Comments → User... (vòng lặp vô tận)

**Giải pháp:**
```java
@JsonIgnoreProperties({"comments", "reviews", "reports", ...})
private User user;

@JsonIgnoreProperties({"comments", "reviews", "reports", ...})
private Movie movie;
```

### 2. ✅ Date Format không đúng
**Trước:** `yyyy-MM-dd` (chỉ có ngày)
**Sau:** `yyyy-MM-dd HH:mm:ss` (có cả giờ, phút, giây)

### 3. ✅ Filter deleted comments
```java
.filter(c -> !"deleted".equals(c.getStatus()))
```

### 4. ✅ Thêm Console Logging
- `[CommentHandler] Loading comments...`
- `[CommentHandler] API Response: {...}`
- `[CommentHandler] Submitting: {...}`

## 🧪 Cách Test:

### Bước 1: Mở Browser Console
1. Nhấn **F12**
2. Chọn tab **Console**
3. Xóa log cũ (Clear console)

### Bước 2: Vào trang movie player
```
http://localhost:8080/movie/player/1
```

### Bước 3: Xem Console Logs
Bạn sẽ thấy:
```
[CommentHandler] Loading comments for movie ID: 1
[CommentHandler] API Response: {success: true, count: X, comments: [...]}
```

### Bước 4: Test Submit Comment

1. **Đăng nhập** vào hệ thống (nếu chưa)
2. Nhập comment: `"Test comment 123"`
3. Nhấn **Enter** hoặc **Gửi**
4. Xem console:

```javascript
[CommentHandler] Submitting: {movieId: 1, content: "Test comment 123"}
[CommentHandler] Submit response: {
  success: true, 
  message: "Thêm bình luận thành công",
  comment: {
    commentID: 5,
    content: "Test comment 123",
    createAt: "2025-11-21 15:30:00",
    status: "approved",
    user: {
      userID: 1,
      userName: "John Doe",
      email: "john@example.com"
    }
  }
}
[CommentHandler] Loading comments for movie ID: 1
[CommentHandler] API Response: {success: true, count: X+1, comments: [...]}
```

### Bước 5: Kiểm tra UI
✅ Comment xuất hiện **ngay lập tức**
✅ Số lượng comments tăng
✅ Input field được clear
✅ Notification xanh: "Bình luận của bạn đã được gửi!"

## 🐛 Troubleshooting:

### ❌ Lỗi: "Failed to fetch" hoặc Network Error
**Nguyên nhân:** Server chưa restart hoặc chưa chạy
**Giải pháp:** 
```bash
./mvnw spring-boot:run
```

### ❌ Lỗi: 401 Unauthorized
**Nguyên nhân:** Chưa đăng nhập hoặc session hết hạn
**Giải pháp:** Đăng nhập lại

### ❌ Lỗi: 500 Internal Server Error
**Kiểm tra server logs:**
```bash
# Xem terminal đang chạy server
# Tìm stack trace
```

**Có thể là:**
- Database connection failed
- User hoặc Movie không tồn tại
- JSON serialization error (nếu vẫn có circular reference)

### ❌ Comments không hiển thị sau khi submit
**Debug steps:**
1. Mở Console (F12)
2. Xem có log `[CommentHandler] API Response:` không?
3. Check `data.comments` có dữ liệu không?
4. Xem Network tab (F12) → Requests → Response

**Có thể là:**
- API trả về empty array
- Date format lỗi khiến parse failed
- User/Movie info thiếu

### ❌ Date hiển thị sai
**Kiểm tra:**
```javascript
// Trong console, test:
const date = new Date("2025-11-21 15:30:00");
console.log(date); // Phải là object Date hợp lệ
```

## 📊 Expected API Response:

### GET /api/comments/movie/1
```json
{
  "success": true,
  "count": 3,
  "comments": [
    {
      "commentID": 3,
      "content": "Phim hay quá!",
      "createAt": "2025-11-21 15:30:00",
      "status": "approved",
      "user": {
        "userID": 1,
        "userName": "John Doe",
        "email": "john@example.com",
        "role": "USER"
      }
    },
    {
      "commentID": 2,
      "content": "Rất cảm động",
      "createAt": "2025-11-21 14:20:00",
      "status": "approved",
      "user": {
        "userID": 2,
        "userName": "Jane Smith",
        "email": "jane@example.com",
        "role": "USER"
      }
    }
  ]
}
```

### POST /api/comments
**Request:**
```json
{
  "movieId": 1,
  "content": "Test comment"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Thêm bình luận thành công",
  "comment": {
    "commentID": 4,
    "content": "Test comment",
    "createAt": "2025-11-21 16:00:00",
    "status": "approved",
    "user": {...}
  }
}
```

## 🎯 Checklist:

- [ ] Server đã restart
- [ ] Browser console mở (F12)
- [ ] Đã đăng nhập
- [ ] Vào trang /movie/player/{id}
- [ ] Thấy log "[CommentHandler] Loading comments..."
- [ ] Nhập comment và submit
- [ ] Thấy log "[CommentHandler] Submitting..."
- [ ] Comment xuất hiện trong UI
- [ ] Số lượng comments tăng
- [ ] Notification hiển thị

## 📞 Nếu vẫn không work:

**Copy toàn bộ nội dung Console và gửi cho tôi:**
```
1. Mở Console (F12)
2. Copy tất cả logs
3. Paste vào message
```

Hoặc chụp màn hình:
- Console logs
- Network tab → Response của API
- Server terminal logs
