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
            movieCard.href = movie.movieUrl; // Link đến trang chi tiết
            movieCard.className = 'movie-card'; // Đảm bảo class này có CSS

            const posterUrl = movie.moviePosterPath 
                ? `${tmdbImageUrlPrefix}${movie.moviePosterPath}`
                : '/images/placeholder.jpg'; // Ảnh placeholder của bạn

            const lastWatched = new Date(movie.lastWatchedAt).toLocaleString('vi-VN');

            movieCard.innerHTML = `
                <img src="${posterUrl}" alt="${movie.movieTitle}">
                <div class="movie-card-info">
                    <h5>${movie.movieTitle}</h5>
                    <p>Xem lần cuối: ${lastWatched}</p>
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