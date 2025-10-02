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
        // Extract movie ID from URL: /movie/detail/123
        const pathParts = window.location.pathname.split('/');
        return pathParts[pathParts.length - 1];
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
                     onclick="location.href='/movie/detail/${movie.id}'">
                    <div class="movie-poster">
                        <img src="${posterUrl}" alt="${movie.title}">
                        <div class="movie-overlay">
                            <button class="play-btn">
                                <i class="fas fa-play"></i>
                            </button>
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
