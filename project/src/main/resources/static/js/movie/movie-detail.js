// movie-detail.js - Movie Detail Page Logic

const TMDB_API_KEY = 'eac03c4e09a0f5099128e38cb0e67a8f';
const TMDB_BASE_URL = 'https://api.themoviedb.org/3';
const IMAGE_BASE_URL = 'https://image.tmdb.org/t/p';

class MovieDetailPage {
    constructor() {
        this.movieId = this.getMovieIdFromUrl();
        this.movieData = null;
        this.isFavorite = false;
        
        this.init();
    }
    
    init() {
        this.loadMovieDetails();
        this.bindEvents();
    }
    
    getMovieIdFromUrl() {
        // 1) ưu tiên query param: ?id=123
        const params = new URLSearchParams(window.location.search);
        if (params.has('id')) {
            return params.get('id');
        }

        // 2) fallback: lấy phần cuối của pathname (ví dụ /movie/detail/123)
        const pathParts = window.location.pathname.split('/').filter(Boolean);
        return pathParts[pathParts.length - 1] || null;
    }

    
    async loadMovieDetails() {
        try {
            // Fetch movie details
            const detailUrl = `${TMDB_BASE_URL}/movie/${this.movieId}?api_key=${TMDB_API_KEY}&language=vi-VN&append_to_response=videos,credits,similar,recommendations`;
            
            const response = await fetch(detailUrl);
            if (!response.ok) throw new Error('Movie not found');
            
            this.movieData = await response.json();
            
            // Update page content
            this.updateHeroSection();
            this.updateMovieInfo();
            this.loadCast();
            this.loadRelatedMovies();
            this.loadSimilarMovies();
            
            // Check if user has favorited (from localStorage for demo)
            this.checkFavoriteStatus();
            
        } catch (error) {
            console.error('Error loading movie details:', error);
            this.showError('Không thể tải thông tin phim');
        }
    }
    
    updateHeroSection() {
        const movie = this.movieData;
        
        // Set backdrop
        const heroSection = document.getElementById('heroSection');
        if (movie.backdrop_path) {
            heroSection.style.backgroundImage = `url(${IMAGE_BASE_URL}/original${movie.backdrop_path})`;
        }
        
        // Update title
        document.getElementById('movieTitle').textContent = movie.title;
        document.title = `${movie.title} - FFilm`;
        
        // Update poster
        const posterImg = document.getElementById('moviePoster');
        if (movie.poster_path) {
            posterImg.src = `${IMAGE_BASE_URL}/w500${movie.poster_path}`;
            posterImg.alt = movie.title;
        }
        
        // Update rating
        document.getElementById('movieRating').textContent = movie.vote_average.toFixed(1);
        
        // Update year
        if (movie.release_date) {
            document.getElementById('movieYear').textContent = new Date(movie.release_date).getFullYear();
        }
        
        // Update duration
        if (movie.runtime) {
            const hours = Math.floor(movie.runtime / 60);
            const minutes = movie.runtime % 60;
            document.getElementById('movieDuration').textContent = 
                hours > 0 ? `${hours}h ${minutes}p` : `${minutes} phút`;
        }
        
        // Update description
        document.getElementById('movieDescription').textContent = movie.overview || 'Chưa có mô tả';
        
        // Update genres
        const genresContainer = document.getElementById('movieGenres');
        if (movie.genres && movie.genres.length > 0) {
            genresContainer.innerHTML = movie.genres.map(genre => 
                `<span class="genre-tag">${genre.name}</span>`
            ).join('');
        }
    }
    
    updateMovieInfo() {
        const movie = this.movieData;
        
        // Director
        const director = movie.credits?.crew?.find(person => person.job === 'Director');
        if (director) {
            document.getElementById('director').textContent = director.name;
        }
        
        // Country
        if (movie.production_countries && movie.production_countries.length > 0) {
            document.getElementById('country').textContent = 
                movie.production_countries.map(c => c.name).join(', ');
        }
        
        // Language
        if (movie.spoken_languages && movie.spoken_languages.length > 0) {
            document.getElementById('language').textContent = 
                movie.spoken_languages.map(l => l.name).join(', ');
        }
        
        // Release year
        if (movie.release_date) {
            document.getElementById('releaseYear').textContent = 
                new Date(movie.release_date).getFullYear();
        }
    }
    
    loadCast() {
        const castContainer = document.getElementById('castGrid');
        const cast = this.movieData.credits?.cast?.slice(0, 12) || [];
        
        if (cast.length === 0) {
            castContainer.innerHTML = '<p style="color: #b3b3b3;">Chưa có thông tin diễn viên</p>';
            return;
        }
        
        castContainer.innerHTML = cast.map(person => {
            const photoUrl = person.profile_path 
                ? `${IMAGE_BASE_URL}/w185${person.profile_path}`
                : '/images/placeholder-person.jpg';
            
            return `
                <div class="cast-card">
                    <div class="cast-avatar">
                        <img src="${photoUrl}" alt="${person.name}" 
                             onerror="this.src='/images/placeholder-person.jpg'">
                    </div>
                    <p class="cast-name">${person.name}</p>
                    <p class="cast-character">${person.character || 'N/A'}</p>
                </div>
            `;
        }).join('');
    }
    
    loadRelatedMovies() {
        const movies = this.movieData.recommendations?.results?.slice(0, 20) || [];
        this.renderCarousel('relatedTrack', movies, 'relatedPrevBtn', 'relatedNextBtn');
    }
    
    loadSimilarMovies() {
        const movies = this.movieData.similar?.results?.slice(0, 20) || [];
        this.renderCarousel('similarTrack', movies, 'similarPrevBtn', 'similarNextBtn');
    }
    
    renderCarousel(trackId, movies, prevBtnId, nextBtnId) {
        const track = document.getElementById(trackId);
        
        if (!movies || movies.length === 0) {
            track.innerHTML = '<p style="padding: 20px; color: #b3b3b3;">Không có phim liên quan</p>';
            return;
        }
        
        track.innerHTML = movies.map(movie => {
            const posterUrl = movie.poster_path 
                ? `${IMAGE_BASE_URL}/w342${movie.poster_path}`
                : '/images/placeholder.jpg';
            
            return `
                <div class="movie-card" style="min-width: 200px; flex-shrink: 0;"
                    onclick="location.href='/movie/detail.html?id=${movie.id}'">
                    <div class="movie-poster">
                    <img src="${posterUrl}" alt="${movie.title}">
                    <div class="movie-overlay">
                        <button class="play-btn"><i class="fas fa-play"></i></button>
                    </div>
                    </div>
                    <div class="movie-info">
                    <h3>${movie.title}</h3>
                    <p class="movie-rating">⭐ ${movie.vote_average.toFixed(1)}</p>
                    </div>
                </div>
                `;

        }).join('');
        
        // Initialize carousel controls
        this.initCarouselControls(trackId, prevBtnId, nextBtnId);
    }
    
    initCarouselControls(trackId, prevBtnId, nextBtnId) {
        const track = document.getElementById(trackId);
        const prevBtn = document.getElementById(prevBtnId);
        const nextBtn = document.getElementById(nextBtnId);
        
        if (!track || !prevBtn || !nextBtn) return;
        
        let scrollPosition = 0;
        const scrollAmount = 600;
        
        prevBtn.addEventListener('click', () => {
            scrollPosition = Math.max(0, scrollPosition - scrollAmount);
            track.style.transform = `translateX(-${scrollPosition}px)`;
        });
        
        nextBtn.addEventListener('click', () => {
            const maxScroll = track.scrollWidth - track.parentElement.offsetWidth;
            scrollPosition = Math.min(maxScroll, scrollPosition + scrollAmount);
            track.style.transform = `translateX(-${scrollPosition}px)`;
        });
    }
    
    bindEvents() {
        // Watch button
        const watchBtn = document.getElementById('watchBtn');
        if (watchBtn) {
            watchBtn.addEventListener('click', () => this.handleWatch());
        }
        
        // Trailer button
        const trailerBtn = document.getElementById('trailerBtn');
        if (trailerBtn) {
            trailerBtn.addEventListener('click', () => this.handleTrailer());
        }
        
        // Favorite button
        const favoriteBtn = document.getElementById('favoriteBtn');
        if (favoriteBtn) {
            favoriteBtn.addEventListener('click', () => this.toggleFavorite());
        }
        
        // Share button
        const shareBtn = document.getElementById('shareBtn');
        if (shareBtn) {
            shareBtn.addEventListener('click', () => this.handleShare());
        }
        
        // Download button (placeholder)
        const downloadBtn = document.getElementById('downloadBtn');
        if (downloadBtn) {
            downloadBtn.addEventListener('click', () => {
                this.showToast('Tính năng tải xuống đang được phát triển', 'info');
            });
        }
        
        // Close trailer modal
        const closeTrailer = document.getElementById('closeTrailer');
        if (closeTrailer) {
            closeTrailer.addEventListener('click', () => this.closeTrailerModal());
        }
        
        // Close modal on ESC key
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                this.closeTrailerModal();
            }
        });
        
        // Close modal on background click
        const trailerModal = document.getElementById('trailerModal');
        if (trailerModal) {
            trailerModal.addEventListener('click', (e) => {
                if (e.target === trailerModal) {
                    this.closeTrailerModal();
                }
            });
        }
    }
    
    handleWatch() {
        // Redirect to player page
        window.location.href = `/movie/player/${this.movieId}`;
    }
    
    handleTrailer() {
        const videos = this.movieData.videos?.results || [];
        const trailer = videos.find(v => v.type === 'Trailer' && v.site === 'YouTube') 
                     || videos.find(v => v.site === 'YouTube');
        
        if (!trailer) {
            this.showToast('Trailer chưa có sẵn', 'error');
            return;
        }
        
        // Open trailer modal
        const modal = document.getElementById('trailerModal');
        const iframe = document.getElementById('trailerIframe');
        
        iframe.src = `https://www.youtube.com/embed/${trailer.key}?autoplay=1`;
        modal.classList.add('active');
        
        // Prevent body scroll
        document.body.style.overflow = 'hidden';
    }
    
    closeTrailerModal() {
        const modal = document.getElementById('trailerModal');
        const iframe = document.getElementById('trailerIframe');
        
        modal.classList.remove('active');
        iframe.src = '';
        
        // Restore body scroll
        document.body.style.overflow = '';
    }
    
    toggleFavorite() {
        this.isFavorite = !this.isFavorite;
        
        const btn = document.getElementById('favoriteBtn');
        if (this.isFavorite) {
            btn.classList.add('active');
            this.saveFavorite();
            this.showToast('Đã thêm vào danh sách yêu thích', 'success');
        } else {
            btn.classList.remove('active');
            this.removeFavorite();
            this.showToast('Đã xóa khỏi danh sách yêu thích', 'success');
        }
        }
    
    checkFavoriteStatus() {
        // Check localStorage for favorite status
        const favorites = JSON.parse(localStorage.getItem('favorites') || '[]');
        this.isFavorite = favorites.includes(this.movieId);
        
        const btn = document.getElementById('favoriteBtn');
        if (this.isFavorite && btn) {
            btn.classList.add('active');
        }
    }
    
    saveFavorite() {
        const favorites = JSON.parse(localStorage.getItem('favorites') || '[]');
        if (!favorites.includes(this.movieId)) {
            favorites.push(this.movieId);
            localStorage.setItem('favorites', JSON.stringify(favorites));
        }
    }
    
    removeFavorite() {
        let favorites = JSON.parse(localStorage.getItem('favorites') || '[]');
        favorites = favorites.filter(id => id !== this.movieId);
        localStorage.setItem('favorites', JSON.stringify(favorites));
    }
    
    async handleShare() {
        const shareData = {
            title: this.movieData.title,
            text: this.movieData.overview?.substring(0, 100) + '...',
            url: window.location.href
        };
        
        if (navigator.share) {
            try {
                await navigator.share(shareData);
                this.showToast('Đã chia sẻ thành công', 'success');
            } catch (error) {
                if (error.name !== 'AbortError') {
                    this.copyToClipboard();
                }
            }
        } else {
            this.copyToClipboard();
        }
    }
    
    copyToClipboard() {
        navigator.clipboard.writeText(window.location.href).then(() => {
            this.showToast('Đã sao chép link phim', 'success');
        }).catch(() => {
            this.showToast('Không thể sao chép link', 'error');
        });
    }
    
    showError(message) {
        const heroSection = document.getElementById('heroSection');
        heroSection.innerHTML = `
            <div style="display: flex; flex-direction: column; align-items: center; 
                        justify-content: center; height: 100%; text-align: center; padding: 40px;">
                <i class="fas fa-exclamation-circle" style="font-size: 4rem; color: #e50914; margin-bottom: 20px;"></i>
                <h2 style="font-size: 2rem; margin-bottom: 15px;">${message}</h2>
                <button onclick="history.back()" class="btn-watch" style="margin-top: 20px;">
                    <i class="fas fa-arrow-left"></i> Quay lại
                </button>
            </div>
        `;
    }
    
    showToast(message, type = 'success') {
        // Create toast if not exists
        let toast = document.getElementById('movieToast');
        if (!toast) {
            toast = document.createElement('div');
            toast.id = 'movieToast';
            toast.style.cssText = `
                position: fixed;
                bottom: 30px;
                right: 30px;
                background: #1f1f1f;
                color: white;
                padding: 16px 24px;
                border-radius: 8px;
                box-shadow: 0 4px 12px rgba(0,0,0,0.5);
                z-index: 10000;
                opacity: 0;
                transform: translateY(20px);
                transition: all 0.3s;
                border-left: 4px solid ${type === 'success' ? '#46d369' : '#e50914'};
            `;
            document.body.appendChild(toast);
        }
        
        toast.textContent = message;
        toast.style.borderLeftColor = type === 'success' ? '#46d369' : '#e50914';
        
        // Show toast
        setTimeout(() => {
            toast.style.opacity = '1';
            toast.style.transform = 'translateY(0)';
        }, 100);
        
        // Hide after 3 seconds
        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateY(20px)';
        }, 3000);
    }
}

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    new MovieDetailPage();
});

// Xử lý nút Xem ngay/Play
document.querySelectorAll('.btn-watch').forEach(btn => {
    btn.addEventListener('click', () => {
        const movieId = btn.dataset.movieId;
        if (movieId) {
            window.location.href = `/movie/detail/${movieId}`;
        } else {
            console.warn('Movie ID không xác định cho nút watch');
        }
    });
});




// ============ LOAD EPISODES FOR TV SHOWS ============
async function loadEpisodes() {
    const episodesTab = document.getElementById('episodes');
    if (!episodesTab) return;
    
    try {
        const movieId = document.body.dataset.movieId || getMovieIdFromUrl();
        
        // Check if it's a TV show
        const detailRes = await fetch(
            `${TMDB_BASE_URL}/tv/${movieId}?api_key=${TMDB_API_KEY}&language=vi-VN`
        );
        
        if (!detailRes.ok) {
            episodesTab.innerHTML = '<p style="padding:40px;text-align:center;color:#999;">Đây là phim lẻ, không có tập phim</p>';
            return;
        }
        
        const tvShow = await detailRes.json();
        const seasons = tvShow.seasons || [];
        
        if (seasons.length === 0) {
            episodesTab.innerHTML = '<p style="padding:40px;text-align:center;color:#999;">Không có thông tin tập phim</p>';
            return;
        }
        
        // Render seasons
        episodesTab.innerHTML = `
            <div style="margin-bottom:30px;">
                <label style="font-weight:600;margin-bottom:10px;display:block;">Chọn season:</label>
                <select id="seasonSelect" style="padding:10px;background:#1f1f1f;color:white;border:1px solid rgba(255,255,255,0.2);border-radius:6px;font-size:1rem;">
                    ${seasons.map(s => `<option value="${s.season_number}">Season ${s.season_number} (${s.episode_count} tập)</option>`).join('')}
                </select>
            </div>
            <div id="episodesContainer" style="display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:20px;"></div>
        `;
        
        const seasonSelect = document.getElementById('seasonSelect');
        const episodesContainer = document.getElementById('episodesContainer');
        
        async function loadSeasonEpisodes(seasonNumber) {
            const res = await fetch(
                `${TMDB_BASE_URL}/tv/${movieId}/season/${seasonNumber}?api_key=${TMDB_API_KEY}&language=vi-VN`
            );
            const season = await res.json();
            const episodes = season.episodes || [];
            
            episodesContainer.innerHTML = episodes.map(ep => `
                <div style="background:#1f1f1f;border-radius:8px;overflow:hidden;cursor:pointer;transition:transform 0.3s;" 
                     onmouseenter="this.style.transform='translateY(-4px)'"
                     onmouseleave="this.style.transform='translateY(0)'">
                    <img src="${ep.still_path ? IMG_BASE + '/w500' + ep.still_path : '/images/placeholder.jpg'}" 
                         style="width:100%;aspect-ratio:16/9;object-fit:cover;">
                    <div style="padding:15px;">
                        <h4 style="margin-bottom:8px;">Tập ${ep.episode_number}: ${ep.name}</h4>
                        <p style="font-size:0.85rem;color:#999;line-height:1.5;">${ep.overview || 'Chưa có mô tả'}</p>
                    </div>
                </div>
            `).join('');
        }
        
        seasonSelect.addEventListener('change', (e) => {
            loadSeasonEpisodes(e.target.value);
        });
        
        // Load first season
        loadSeasonEpisodes(seasons[0].season_number);
        
    } catch (error) {
        console.error('Error loading episodes:', error);
        if (episodesTab) {
            episodesTab.innerHTML = '<p style="padding:40px;text-align:center;color:#999;">Đây là phim lẻ, không có tập phim</p>';
        }
    }
}

// Call in DOMContentLoaded
document.addEventListener('DOMContentLoaded', () => {
    loadEpisodes();
});




// ============ LOAD CAST WITH CIRCULAR AVATARS ============
async function loadCastEnhanced() {
    const castContainer = document.getElementById('castContainer');
    if (!castContainer) return;
    
    try {
        const movieId = document.body.dataset.movieId || getMovieIdFromUrl();
        const response = await fetch(
            `${TMDB_BASE_URL}/movie/${movieId}/credits?api_key=${TMDB_API_KEY}&language=vi-VN`
        );
        
        if (!response.ok) throw new Error('Failed to load cast');
        
        const data = await response.json();
        const cast = (data.cast || []).slice(0, 12);
        
        if (cast.length === 0) {
            castContainer.innerHTML = '<p style="color:#999;text-align:center;padding:40px;">Chưa có thông tin diễn viên</p>';
            return;
        }
        
        castContainer.innerHTML = cast.map(person => {
            const photoUrl = person.profile_path 
                ? `${IMAGE_BASE_URL}/w185${person.profile_path}`
                : '/images/placeholder-person.jpg';
            
            return `
                <div class="cast-card" style="text-align:center;cursor:pointer;transition:transform 0.3s;" 
                     onclick="location.href='/person/detail/${person.id}'"
                     onmouseenter="this.style.transform='translateY(-8px)'" 
                     onmouseleave="this.style.transform='translateY(0)'">
                    <div style="width:120px;height:120px;margin:0 auto 12px;border-radius:50%;overflow:hidden;border:3px solid rgba(255,255,255,0.1);">
                        <img src="${photoUrl}" alt="${person.name}" 
                             onerror="this.src='/images/placeholder-person.jpg'"
                             style="width:100%;height:100%;object-fit:cover;">
                    </div>
                    <div style="padding:0 8px;">
                        <p style="font-weight:600;font-size:0.95rem;margin-bottom:4px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${person.name}</p>
                        <p style="font-size:0.85rem;color:#999;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${person.character || 'N/A'}</p>
                    </div>
                </div>
            `;
        }).join('');
        
        console.log('✅ Cast loaded successfully');
        
    } catch (error) {
        console.error('Error loading cast:', error);
        if (castContainer) {
            castContainer.innerHTML = '<p style="color:#999;text-align:center;padding:40px;">Không thể tải thông tin diễn viên</p>';
        }
    }
}

// ============ LOAD SIMILAR MOVIES CAROUSEL ============
async function loadSimilarMoviesEnhanced() {
    const similarContainer = document.getElementById('similarMovies');
    if (!similarContainer) return;
    
    try {
        const movieId = document.body.dataset.movieId || getMovieIdFromUrl();
        const response = await fetch(
            `${TMDB_BASE_URL}/movie/${movieId}/similar?api_key=${TMDB_API_KEY}&language=vi-VN&page=1`
        );
        
        if (!response.ok) throw new Error('Failed to load similar movies');
        
        const data = await response.json();
        const movies = (data.results || []).slice(0, 20);
        
        if (movies.length === 0) {
            similarContainer.innerHTML = '<p style="color:#999;padding:40px;text-align:center;">Không có phim tương tự</p>';
            return;
        }
        
        similarContainer.innerHTML = movies.map(movie => {
            const posterUrl = movie.poster_path 
                ? `${IMAGE_BASE_URL}/w342${movie.poster_path}`
                : '/images/placeholder.jpg';
            
            return `
                <div class="movie-card" style="min-width:200px;flex-shrink:0;cursor:pointer;transition:transform 0.3s;"
                     onclick="location.href='/movie/detail/${movie.id}'"
                     onmouseenter="this.style.transform='scale(1.05)'"
                     onmouseleave="this.style.transform='scale(1)'">
                    <div class="movie-poster" style="position:relative;border-radius:8px;overflow:hidden;">
                        <img src="${posterUrl}" alt="${movie.title}" 
                             style="width:100%;aspect-ratio:2/3;object-fit:cover;"
                             onerror="this.src='/images/placeholder.jpg'">
                        <div class="movie-overlay" style="position:absolute;inset:0;background:linear-gradient(0deg,rgba(0,0,0,0.9) 0%,transparent 50%);opacity:0;transition:opacity 0.3s;display:flex;align-items:flex-end;padding:15px;"
                             onmouseenter="this.style.opacity='1'"
                             onmouseleave="this.style.opacity='0'">
                            <div style="width:100%;">
                                <p style="font-weight:600;margin-bottom:5px;">${movie.title}</p>
                                <p style="font-size:0.85rem;color:#999;">⭐ ${movie.vote_average.toFixed(1)}</p>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }).join('');
        
        // Setup carousel navigation
        initCarouselControls('similarMovies', 'similarPrev', 'similarNext');
        
        console.log('✅ Similar movies loaded successfully');
        
    } catch (error) {
        console.error('Error loading similar movies:', error);
        if (similarContainer) {
            similarContainer.innerHTML = '<p style="color:#999;padding:40px;text-align:center;">Không thể tải phim tương tự</p>';
        }
    }
}

// ============ LOAD RECOMMENDED MOVIES CAROUSEL ============
async function loadRecommendedMoviesEnhanced() {
    const recommendedContainer = document.getElementById('recommendedMovies');
    if (!recommendedContainer) return;
    
    try {
        const movieId = document.body.dataset.movieId || getMovieIdFromUrl();
        const response = await fetch(
            `${TMDB_BASE_URL}/movie/${movieId}/recommendations?api_key=${TMDB_API_KEY}&language=vi-VN&page=1`
        );
        
        if (!response.ok) throw new Error('Failed to load recommendations');
        
        const data = await response.json();
        const movies = (data.results || []).slice(0, 20);
        
        if (movies.length === 0) {
            recommendedContainer.innerHTML = '<p style="color:#999;padding:40px;text-align:center;">Không có gợi ý</p>';
            return;
        }
        
        recommendedContainer.innerHTML = movies.map(movie => {
            const posterUrl = movie.poster_path 
                ? `${IMAGE_BASE_URL}/w342${movie.poster_path}`
                : '/images/placeholder.jpg';
            
            return `
                <div class="movie-card" style="min-width:200px;flex-shrink:0;cursor:pointer;transition:transform 0.3s;"
                     onclick="location.href='/movie/detail/${movie.id}'"
                     onmouseenter="this.style.transform='scale(1.05)'"
                     onmouseleave="this.style.transform='scale(1)'">
                    <div class="movie-poster" style="position:relative;border-radius:8px;overflow:hidden;">
                        <img src="${posterUrl}" alt="${movie.title}" 
                             style="width:100%;aspect-ratio:2/3;object-fit:cover;"
                             onerror="this.src='/images/placeholder.jpg'">
                        <div class="movie-overlay" style="position:absolute;inset:0;background:linear-gradient(0deg,rgba(0,0,0,0.9) 0%,transparent 50%);opacity:0;transition:opacity 0.3s;display:flex;align-items:flex-end;padding:15px;"
                             onmouseenter="this.style.opacity='1'"
                             onmouseleave="this.style.opacity='0'">
                            <div style="width:100%;">
                                <p style="font-weight:600;margin-bottom:5px;">${movie.title}</p>
                                <p style="font-size:0.85rem;color:#999;">⭐ ${movie.vote_average.toFixed(1)}</p>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }).join('');
        
        // Setup carousel navigation
        initCarouselControls('recommendedMovies', 'recommendedPrev', 'recommendedNext');
        
        console.log('✅ Recommended movies loaded successfully');
        
    } catch (error) {
        console.error('Error loading recommended movies:', error);
        if (recommendedContainer) {
            recommendedContainer.innerHTML = '<p style="color:#999;padding:40px;text-align:center;">Không thể tải gợi ý</p>';
        }
    }
}

// ============ LOAD BUDGET INFO ============
async function loadBudgetInfo() {
    const budgetEl = document.getElementById('budget');
    if (!budgetEl) return;
    
    try {
        const movieId = document.body.dataset.movieId || getMovieIdFromUrl();
        const response = await fetch(
            `${TMDB_BASE_URL}/movie/${movieId}?api_key=${TMDB_API_KEY}&language=vi-VN`
        );
        
        if (!response.ok) throw new Error('Failed to load movie details');
        
        const movie = await response.json();
        
        if (movie.budget && movie.budget > 0) {
            const budgetInMillions = (movie.budget / 1000000).toFixed(1);
            budgetEl.textContent = `$${budgetInMillions}M`;
        } else {
            budgetEl.textContent = '—';
        }
        
    } catch (error) {
        console.error('Error loading budget:', error);
        if (budgetEl) budgetEl.textContent = '—';
    }
}

// ============ HELPER: Get Movie ID ============
function getMovieIdFromUrl() {
    const params = new URLSearchParams(window.location.search);
    if (params.has('id')) {
        return params.get('id');
    }
    
    const pathParts = window.location.pathname.split('/').filter(Boolean);
    return pathParts[pathParts.length - 1] || null;
}

// ============ CAROUSEL NAVIGATION HELPER ============
function initCarouselControls(trackId, prevBtnId, nextBtnId) {
    const track = document.getElementById(trackId);
    const prevBtn = document.getElementById(prevBtnId);
    const nextBtn = document.getElementById(nextBtnId);
    
    if (!track || !prevBtn || !nextBtn) return;
    
    let scrollPosition = 0;
    const scrollAmount = 600;
    
    prevBtn.addEventListener('click', () => {
        scrollPosition = Math.max(0, scrollPosition - scrollAmount);
        track.style.transform = `translateX(-${scrollPosition}px)`;
        updateButtonStates();
    });
    
    nextBtn.addEventListener('click', () => {
        const maxScroll = track.scrollWidth - track.parentElement.offsetWidth;
        scrollPosition = Math.min(maxScroll, scrollPosition + scrollAmount);
        track.style.transform = `translateX(-${scrollPosition}px)`;
        updateButtonStates();
    });
    
    function updateButtonStates() {
        const maxScroll = track.scrollWidth - track.parentElement.offsetWidth;
        prevBtn.disabled = scrollPosition <= 0;
        nextBtn.disabled = scrollPosition >= maxScroll;
        prevBtn.style.opacity = prevBtn.disabled ? '0.3' : '1';
        nextBtn.style.opacity = nextBtn.disabled ? '0.3' : '1';
    }
    
    updateButtonStates();
    
    window.addEventListener('resize', updateButtonStates);
}

// ============ AUTO-CALL ON PAGE LOAD ============
document.addEventListener('DOMContentLoaded', function() {
    // Call enhanced functions
    loadCastEnhanced();
    loadSimilarMoviesEnhanced();
    loadRecommendedMoviesEnhanced();
    loadBudgetInfo();
    
    console.log('✅ Movie detail enhancements initialized');
});