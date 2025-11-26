document.addEventListener('DOMContentLoaded', () => {
    const historyListContainer = document.getElementById('history-list'); // Đảm bảo ID này tồn tại trong HTML
    const loadMoreButton = document.getElementById('load-more');
    const tmdbImageUrlPrefix = 'https://image.tmdb.org/t/p/w500';

    let currentPage = 0;
    let totalPages = 1;

    // === XÓA BỎ CÁC BIẾN CSRF ===
    // const csrfToken = ... (ĐÃ XÓA)
    // const csrfHeader = ... (ĐÃ XÓA)

    async function fetchHistory(page) {
        if (page >= totalPages) {
            loadMoreButton.textContent = 'Không còn phim nào';
            loadMoreButton.disabled = true;
            return;
        }

        try {
            const response = await fetch(`/api/history?page=${page}&size=20`, {
                method: 'GET',
                headers: {
                    'Accept': 'application/json'
                    // === XÓA HEADER CSRF KHỎI ĐÂY ===
                    // [csrfHeader]: csrfToken (ĐÃ XÓA)
                }
            });

            if (response.status === 401) {
                 historyListContainer.innerHTML = '<p>Bạn cần <a href="/login">đăng nhập</a> để xem lịch sử.</p>';
                 loadMoreButton.style.display = 'none';
                 return;
            }
            if (!response.ok) {
                throw new Error(`Error ${response.status}: ${response.statusText}`);
            }

            const pageData = await response.json();
            
            totalPages = pageData.totalPages;
            currentPage = pageData.number + 1; // pageData.number là 0-based

            if (pageData.content.length > 0) {
                renderMovies(pageData.content);
            } else if (page === 0) {
                historyListContainer.innerHTML = '<p>Bạn chưa xem bộ phim nào.</p>';
            }
            
            if (currentPage >= totalPages) {
                loadMoreButton.style.display = 'none';
            }

        } catch (error) {
            console.error('Failed to fetch watch history:', error);
            historyListContainer.innerHTML = '<p>Đã xảy ra lỗi khi tải lịch sử xem.</p>';
        }
    }

    function renderMovies(movies) {
        movies.forEach(movie => {
            const movieCard = document.createElement('a');
            movieCard.href = movie.movieUrl;
            movieCard.className = 'movie-card';

            const posterUrl = movie.moviePosterPath 
                ? `https://image.tmdb.org/t/p/w500${movie.moviePosterPath}`
                : '/images/placeholder.jpg';

            const lastWatched = new Date(movie.lastWatchedAt).toLocaleString('vi-VN');

            // --- LOGIC MỚI: TÍNH THỜI GIAN ĐÃ XEM (TEXT) ---
            let watchedTimeText = "Chưa xem";
            let currentSec = movie.currentTime || 0;
            const durationMin = movie.duration || 0;
            const totalSeconds = durationMin * 60;

            if (currentSec > 0) {
                // 1. Xử lý vụ mili-giây (nếu số quá lớn so với tổng thời lượng)
                if (durationMin > 0 && currentSec > totalSeconds) {
                    currentSec = currentSec / 1000;
                }

                // 2. Format thời gian (Giờ : Phút : Giây)
                const h = Math.floor(currentSec / 3600);
                const m = Math.floor((currentSec % 3600) / 60);
                const s = Math.floor(currentSec % 60);

                // Logic hiển thị chuỗi
                if (h > 0) {
                    watchedTimeText = `Đã xem đến: ${h}g ${m}p ${s}s`;
                } else {
                    watchedTimeText = `Đã xem đến: ${m}p ${s}s`;
                }
            }
            // ------------------------------------------------

            movieCard.innerHTML = `
                <div class="poster-wrapper">
                    <img src="${posterUrl}" alt="${movie.movieTitle}" loading="lazy">
                    </div>
                <div class="movie-card-info">
                    <h5>${movie.movieTitle}</h5>
                    
                    <p class="watched-time">
                        <i class="fas fa-play-circle"></i> ${watchedTimeText}
                    </p>
                    
                    <p class="last-watched-date">Ngày xem: ${lastWatched}</p>
                </div>
            `;
            
            if(historyListContainer) {
                 historyListContainer.appendChild(movieCard);
            }
        });
    }

    // Tải trang đầu tiên
    if(historyListContainer && loadMoreButton) {
        fetchHistory(0);

        // Xử lý nút "Tải thêm"
        loadMoreButton.addEventListener('click', () => {
            fetchHistory(currentPage);
        });
    }
});