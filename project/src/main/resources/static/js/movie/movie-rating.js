/**
 * movie-rating.js
 * Quản lý tương tác đánh giá 1-5 sao của cộng đồng FFilm
 * Tách biệt hoàn toàn metadata điểm TMDB và FFilm User Rating
 */
class MovieRating {
    constructor() {
        this.container = document.getElementById('heroRatingBox');
        if (!this.container) return;

        const metaMovieId = document.querySelector('meta[name="movie-id"]');
        this.movieId = metaMovieId ? parseInt(metaMovieId.content) : null;
        if (!this.movieId) {
            const pathParts = window.location.pathname.split('/');
            this.movieId = parseInt(pathParts[pathParts.length - 1]);
        }

        this.starsGroup = document.getElementById('ratingStarsGroup');
        this.statusEl = document.getElementById('ratingBoxStatus');
        this.removeBtn = document.getElementById('btnRemoveRating');
        this.heroCommunityRating = document.getElementById('heroCommunityRatingText');
        this.heroRatingCount = document.getElementById('heroRatingCountText');

        const initialUserRating = this.container.getAttribute('data-user-rating');
        this.currentUserRating = (initialUserRating && initialUserRating !== 'null') ? parseInt(initialUserRating) : null;

        this.userIdEl = document.getElementById('currentUserId');
        this.currentUserId = this.userIdEl && this.userIdEl.value ? parseInt(this.userIdEl.value) : null;

        this.labels = {
            1: '1/5 sao - Rất tệ',
            2: '2/5 sao - Tạm được',
            3: '3/5 sao - Bình thường',
            4: '4/5 sao - Rất hay',
            5: '5/5 sao - Tuyệt phẩm'
        };

        this.init();
    }

    init() {
        if (!this.starsGroup) return;

        // Render trạng thái ban đầu
        this.renderStars(this.currentUserRating);

        const stars = this.starsGroup.querySelectorAll('.star-item');
        stars.forEach(star => {
            const val = parseInt(star.getAttribute('data-val'));

            // Hover preview
            star.addEventListener('mouseenter', () => {
                this.highlightStars(val);
                if (this.statusEl) {
                    this.statusEl.textContent = this.labels[val] || `${val}/5 sao`;
                }
            });

            // Click to rate
            star.addEventListener('click', (e) => {
                e.preventDefault();
                this.submitRating(val);
            });
        });

        // Mouse leave: khôi phục về rating thực tế của user
        this.starsGroup.addEventListener('mouseleave', () => {
            this.renderStars(this.currentUserRating);
            if (this.statusEl) {
                this.statusEl.textContent = this.currentUserRating ? this.labels[this.currentUserRating] || `${this.currentUserRating}/5 sao` : 'Chưa đánh giá';
            }
        });

        // Xóa rating
        if (this.removeBtn) {
            this.removeBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.removeRating();
            });
        }
    }

    renderStars(rating) {
        if (!this.starsGroup) return;
        const stars = this.starsGroup.querySelectorAll('.star-item');
        stars.forEach(star => {
            const val = parseInt(star.getAttribute('data-val'));
            if (rating && val <= rating) {
                star.classList.add('active');
            } else {
                star.classList.remove('active');
            }
        });

        if (this.removeBtn) {
            this.removeBtn.style.display = rating ? 'inline-flex' : 'none';
        }
    }

    highlightStars(val) {
        if (!this.starsGroup) return;
        const stars = this.starsGroup.querySelectorAll('.star-item');
        stars.forEach(star => {
            const starVal = parseInt(star.getAttribute('data-val'));
            if (starVal <= val) {
                star.classList.add('hover');
            } else {
                star.classList.remove('hover');
            }
        });
    }

    async submitRating(rating) {
        if (!this.currentUserId) {
            this.showToast('Vui lòng đăng nhập để đánh giá phim!', 'error');
            setTimeout(() => {
                window.location.href = '/login';
            }, 1200);
            return;
        }

        // Optimistic UI
        const previousRating = this.currentUserRating;
        this.currentUserRating = rating;
        this.renderStars(rating);
        if (this.statusEl) {
            this.statusEl.textContent = this.labels[rating] || `${rating}/5 sao`;
        }

        try {
            const response = await fetch('/api/reviews', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    movieId: this.movieId,
                    rating: rating
                })
            });

            const data = await response.json();
            if (response.ok && data.success) {
                this.showToast(data.message || 'Đánh giá thành công!', 'success');
                this.updateCommunityStats(data.communityRating, data.ratingCount);
                this.currentUserRating = data.userRating;
                this.container.setAttribute('data-user-rating', data.userRating);

                // Kích hoạt event cho commentHandler cập nhật badge
                window.dispatchEvent(new CustomEvent('ffilm:movie-rated', {
                    detail: {
                        movieId: this.movieId,
                        userId: this.currentUserId,
                        rating: rating
                    }
                }));
            } else {
                this.currentUserRating = previousRating;
                this.renderStars(previousRating);
                this.showToast(data.message || 'Không thể lưu đánh giá', 'error');
            }
        } catch (error) {
            console.error('[MovieRating] Error submitting rating:', error);
            this.currentUserRating = previousRating;
            this.renderStars(previousRating);
            this.showToast('Lỗi kết nối khi gửi đánh giá', 'error');
        }
    }

    async removeRating() {
        if (!this.currentUserRating) return;

        const previousRating = this.currentUserRating;
        this.currentUserRating = null;
        this.renderStars(null);
        if (this.statusEl) {
            this.statusEl.textContent = 'Chưa đánh giá';
        }

        try {
            const response = await fetch(`/api/reviews/movie/${this.movieId}`, {
                method: 'DELETE'
            });

            const data = await response.json();
            if (response.ok && data.success) {
                this.showToast(data.message || 'Đã xóa đánh giá', 'success');
                this.updateCommunityStats(data.communityRating, data.ratingCount);
                this.container.removeAttribute('data-user-rating');

                window.dispatchEvent(new CustomEvent('ffilm:movie-rating-removed', {
                    detail: {
                        movieId: this.movieId,
                        userId: this.currentUserId
                    }
                }));
            } else {
                this.currentUserRating = previousRating;
                this.renderStars(previousRating);
                this.showToast(data.message || 'Không thể xóa đánh giá', 'error');
            }
        } catch (error) {
            console.error('[MovieRating] Error removing rating:', error);
            this.currentUserRating = previousRating;
            this.renderStars(previousRating);
            this.showToast('Lỗi kết nối khi xóa đánh giá', 'error');
        }
    }

    updateCommunityStats(communityRating, ratingCount) {
        if (this.heroCommunityRating) {
            this.heroCommunityRating.textContent = (communityRating && communityRating > 0)
                ? Number(communityRating).toFixed(1)
                : 'Chưa có';
        }
        if (this.heroRatingCount) {
            this.heroRatingCount.textContent = (ratingCount && ratingCount > 0)
                ? `(${ratingCount})`
                : '';
        }
    }

    showToast(message, type = 'success') {
        const existing = document.querySelector('.rating-toast');
        if (existing) existing.remove();

        const toast = document.createElement('div');
        toast.className = `rating-toast ${type}`;
        toast.innerHTML = `
            <i class="fas fa-${type === 'success' ? 'check-circle' : 'exclamation-circle'}"></i>
            <span>${message}</span>
        `;

        Object.assign(toast.style, {
            position: 'fixed',
            bottom: '30px',
            right: '30px',
            backgroundColor: type === 'success' ? 'rgba(16, 185, 129, 0.95)' : 'rgba(239, 68, 68, 0.95)',
            color: '#fff',
            padding: '12px 22px',
            borderRadius: '10px',
            boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            zIndex: '10000',
            backdropFilter: 'blur(8px)',
            fontWeight: '600',
            fontSize: '0.95rem',
            animation: 'slideInRatingToast 0.3s ease-out'
        });

        document.body.appendChild(toast);

        setTimeout(() => {
            toast.style.transition = 'opacity 0.3s, transform 0.3s';
            toast.style.opacity = '0';
            toast.style.transform = 'translateY(10px)';
            setTimeout(() => toast.remove(), 300);
        }, 2500);
    }
}

// Auto init on DOM ready
document.addEventListener('DOMContentLoaded', () => {
    window.movieRatingHandler = new MovieRating();
});
