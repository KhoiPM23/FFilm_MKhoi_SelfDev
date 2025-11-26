// comment-handler.js - Xử lý tính năng Comment

class CommentHandler {
    constructor() {
        this.movieId = this.getMovieIdFromUrl();
        this.commentInput = document.getElementById('comment-input-field');
        this.submitBtn = document.getElementById('btn-submit-comment');
        this.commentList = document.getElementById('comment-list');
        this.commentCount = document.getElementById('comment-count');

        this.init();
    }

    getMovieIdFromUrl() {
        const pathParts = window.location.pathname.split('/');
        return parseInt(pathParts[pathParts.length - 1]);
    }

    init() {
        // Load comments khi trang vừa load
        this.loadComments();

        if (this.commentInput && this.submitBtn) {
            this.commentInput.addEventListener('input', () => {
                const content = this.commentInput.value.trim();
                this.submitBtn.disabled = content.length === 0;
            });

            this.commentInput.addEventListener('keypress', (e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    this.submitComment();
                }
            });
            this.submitBtn.addEventListener('mousedown', (e) => {
                e.preventDefault();
            });

            // Submit comment khi click button
            this.submitBtn.addEventListener('click', () => {
                this.submitComment();
            });
        }
    }

    /**
     * Load danh sách comments từ API
     */
    async loadComments() {
        try {
            console.log(`[CommentHandler] Loading comments for movie ID: ${this.movieId}`);
            const response = await fetch(`/api/comments/movie/${this.movieId}`);

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            console.log('[CommentHandler] API Response:', data);

            if (data.success) {
                this.renderComments(data.comments);
                this.updateCommentCount(data.count);
            } else {
                console.error('[CommentHandler] Failed:', data.message);
                this.showError('Không thể tải bình luận');
            }
        } catch (error) {
            console.error('[CommentHandler] Error loading comments:', error);
            this.commentList.innerHTML = `
                <div style="text-align: center; color: #ff6b6b; padding: 20px">
                    <i class="fas fa-exclamation-circle"></i> ${error.message}
                </div>
            `;
        }
    }

    /**
     * Render danh sách comments
     */
    renderComments(comments) {
        if (!comments || comments.length === 0) {
            this.commentList.innerHTML = `
                <div style="text-align: center; color: #777; padding: 20px">
                    Chưa có bình luận nào. Hãy là người đầu tiên!
                </div>
            `;
            return;
        }

        this.commentList.innerHTML = comments.map(comment => this.createCommentHTML(comment)).join('');
    }

    /**
     * Tạo HTML cho một comment
     */
    createCommentHTML(comment) {
        const userName = comment.user?.userName || 'Ẩn danh';
        const userInitial = userName.charAt(0).toUpperCase();
        const createAt = this.formatDate(comment.createAt);
        const content = this.escapeHtml(comment.content);

        return `
            <div class="comment-item" data-comment-id="${comment.commentID}">
                <div class="user-avatar">${userInitial}</div>
                <div class="comment-content">
                    <div class="comment-author">
                        <span>${userName}</span>
                        <span class="comment-time">${createAt}</span>
                    </div>
                    <p style="color: #ddd">${content}</p>
                </div>
            </div>
        `;
    }

    /**
     * Submit comment mới
     */
    async submitComment() {
        const content = this.commentInput.value.trim();

        if (content.length === 0) {
            return;
        }

        // Disable button để tránh spam
        this.submitBtn.disabled = true;
        this.submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Đang gửi...';

        try {
            console.log('[CommentHandler] Submitting:', { movieId: this.movieId, content });

            const response = await fetch('/api/comments', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json; charset=utf-8',
                },
                body: JSON.stringify({
                    movieId: this.movieId,
                    content: content
                })
            });

            const data = await response.json();
            console.log('[CommentHandler] Submit response:', data);

            if (data.success) {
                // Clear input
                this.commentInput.value = '';

                // Reload comments
                await this.loadComments();
            } else {
                if (response.status === 401) {
                    this.showError('Bạn cần đăng nhập để bình luận');
                    setTimeout(() => {
                        window.location.href = '/login';
                    }, 2000);
                } else {
                    this.showError(data.message || 'Không thể gửi bình luận');
                }
            }
        } catch (error) {
            console.error('[CommentHandler] Error submitting:', error);
            this.showError('Lỗi kết nối. Vui lòng thử lại sau.');
        } finally {
            this.submitBtn.disabled = false;
            this.submitBtn.textContent = 'Gửi';
        }
    }

    /**
     * Cập nhật số lượng comments
     */
    updateCommentCount(count) {
        if (this.commentCount) {
            this.commentCount.textContent = count;
        }
    }

    /**
     * Format date
     */
    formatDate(dateString) {
        if (!dateString) return 'Vừa xong';

        try {
            const date = new Date(dateString);

            if (isNaN(date.getTime())) {
                console.warn('[CommentHandler] Invalid date:', dateString);
                return 'Vừa xong';
            }

            const now = new Date();
            const diff = now - date;

            if (diff < 60000) {
                return 'Vừa xong';
            }

            if (diff < 3600000) {
                const minutes = Math.floor(diff / 60000);
                return `${minutes} phút trước`;
            }

            if (diff < 86400000) {
                const hours = Math.floor(diff / 3600000);
                return `${hours} giờ trước`;
            }

            if (diff < 604800000) {
                const days = Math.floor(diff / 86400000);
                return `${days} ngày trước`;
            }

            const day = String(date.getDate()).padStart(2, '0');
            const month = String(date.getMonth() + 1).padStart(2, '0');
            const year = date.getFullYear();
            const hours = String(date.getHours()).padStart(2, '0');
            const minutes = String(date.getMinutes()).padStart(2, '0');

            return `${day}/${month}/${year} ${hours}:${minutes}`;
        } catch (error) {
            console.error('[CommentHandler] Error formatting date:', error, dateString);
            return 'Vừa xong';
        }
    }

    /**
     * Escape HTML để tránh XSS
     */
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * Hiển thị thông báo lỗi
     */
    showError(message) {
        this.showNotification(message, 'error');
    }

    /**
     * Hiển thị thông báo thành công
     */
    showSuccess(message) {
        this.showNotification(message, 'success');
    }

    /**
     * Hiển thị notification
     */
    showNotification(message, type = 'info') {
        // Tạo notification element
        const notification = document.createElement('div');
        notification.className = `comment-notification ${type}`;
        notification.innerHTML = `
            <i class="fas fa-${type === 'success' ? 'check-circle' : 'exclamation-circle'}"></i>
            <span>${message}</span>
        `;

        // Style cho notification
        Object.assign(notification.style, {
            position: 'fixed',
            top: '20px',
            right: '20px',
            backgroundColor: type === 'success' ? '#10b981' : '#ef4444',
            color: '#fff',
            padding: '15px 20px',
            borderRadius: '8px',
            boxShadow: '0 4px 12px rgba(0,0,0,0.3)',
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            zIndex: '10000',
            animation: 'slideInRight 0.3s ease-out'
        });

        document.body.appendChild(notification);

        // Tự động xóa sau 3 giây
        setTimeout(() => {
            notification.style.animation = 'slideOutRight 0.3s ease-out';
            setTimeout(() => {
                notification.remove();
            }, 300);
        }, 3000);
    }
}

// Thêm CSS animations
const style = document.createElement('style');
style.textContent = `
    @keyframes slideInRight {
        from {
            transform: translateX(100%);
            opacity: 0;
        }
        to {
            transform: translateX(0);
            opacity: 1;
        }
    }
    
    @keyframes slideOutRight {
        from {
            transform: translateX(0);
            opacity: 1;
        }
        to {
            transform: translateX(100%);
            opacity: 0;
        }
    }
`;
document.head.appendChild(style);

// Initialize khi DOM ready
document.addEventListener('DOMContentLoaded', () => {
    new CommentHandler();
});
