// search.js - Advanced search với autocomplete và debounce

const TMDB_API_KEY = 'eac03c4e09a0f5099128e38cb0e67a8f';
const TMDB_BASE_URL = 'https://api.themoviedb.org/3';
const IMAGE_BASE_URL = 'https://image.tmdb.org/t/p';

// Debounce function
function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

// Search Manager Class
class SearchManager {
    constructor() {
        this.searchInput = document.getElementById('searchInput') || document.querySelector('.search-input');
        this.searchClear = document.getElementById('searchClear');
        this.currentController = null;
        this.autocompleteContainer = null;
        
        this.init();
    }
    
    init() {
        if (!this.searchInput) return;
        
        // Create autocomplete container
        this.createAutocompleteContainer();
        
        // Bind events
        this.searchInput.addEventListener('input', debounce((e) => {
            this.handleSearch(e.target.value);
        }, 300));
        
        this.searchInput.addEventListener('focus', () => {
            if (this.searchInput.value.trim()) {
                this.showAutocomplete();
            }
        });
        
        // Clear button
        if (this.searchClear) {
            this.searchClear.addEventListener('click', () => {
                this.clearSearch();
            });
        }
        
        // Close autocomplete when clicking outside
        document.addEventListener('click', (e) => {
            if (!this.searchInput.contains(e.target) && 
                !this.autocompleteContainer.contains(e.target)) {
                this.hideAutocomplete();
            }
        });
        
        // Handle Enter key
        this.searchInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                this.performFullSearch(this.searchInput.value);
                this.hideAutocomplete();
            }
        });
        
        // ESC to clear
        this.searchInput.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                this.clearSearch();
            }
        });
    }
    
    createAutocompleteContainer() {
        const container = this.searchInput.closest('.search-box') || this.searchInput.parentElement;
        
        this.autocompleteContainer = document.createElement('div');
        this.autocompleteContainer.className = 'autocomplete-dropdown';
        this.autocompleteContainer.style.cssText = `
            position: absolute;
            top: 100%;
            left: 0;
            right: 0;
            background: rgba(20, 20, 20, 0.98);
            backdrop-filter: blur(20px);
            border-radius: 0 0 8px 8px;
            max-height: 400px;
            overflow-y: auto;
            box-shadow: 0 8px 24px rgba(0,0,0,0.8);
            z-index: 1000;
            display: none;
            margin-top: 5px;
        `;
        
        container.style.position = 'relative';
        container.appendChild(this.autocompleteContainer);
    }
    
    async handleSearch(query) {
        const trimmedQuery = query.trim();
        
        // Show/hide clear button
        if (this.searchClear) {
            this.searchClear.style.display = trimmedQuery ? 'block' : 'none';
        }
        
        if (trimmedQuery.length < 2) {
            this.hideAutocomplete();
            return;
        }
        
        // Cancel previous request
        if (this.currentController) {
            this.currentController.abort();
        }
        
        this.currentController = new AbortController();
        
        try {
            const url = `${TMDB_BASE_URL}/search/movie?api_key=${TMDB_API_KEY}&language=vi-VN&query=${encodeURIComponent(trimmedQuery)}&page=1`;
            
            const response = await fetch(url, {
                signal: this.currentController.signal
            });
            
            if (!response.ok) throw new Error('Search failed');
            
            const data = await response.json();
            this.displayAutocomplete(data.results.slice(0, 8)); // Top 8 results
            
        } catch (error) {
            if (error.name !== 'AbortError') {
                console.error('Search error:', error);
            }
        }
    }
    
    displayAutocomplete(movies) {
        if (!movies || movies.length === 0) {
            this.autocompleteContainer.innerHTML = `
                <div style="padding: 20px; text-align: center; color: #b3b3b3;">
                    <i class="fas fa-search" style="font-size: 2rem; margin-bottom: 10px; opacity: 0.5;"></i>
                    <p>Không tìm thấy kết quả</p>
                </div>
            `;
            this.showAutocomplete();
            return;
        }
        
        const html = movies.map(movie => {
            const posterUrl = movie.poster_path 
                ? `${IMAGE_BASE_URL}/w92${movie.poster_path}`
                : '/images/placeholder.jpg';
            
            const year = movie.release_date ? new Date(movie.release_date).getFullYear() : '';
            const rating = movie.vote_average ? movie.vote_average.toFixed(1) : 'N/A';
            
            return `
                <div class="autocomplete-item" data-movie-id="${movie.id}" 
                     style="display: flex; gap: 12px; padding: 12px; cursor: pointer; 
                            transition: background 0.3s; border-bottom: 1px solid rgba(255,255,255,0.05);">
                    <img src="${posterUrl}" alt="${movie.title}" 
                         style="width: 50px; height: 75px; object-fit: cover; border-radius: 4px;">
                    <div style="flex: 1; min-width: 0;">
                        <h4 style="margin: 0 0 5px; font-size: 0.95rem; overflow: hidden; 
                                   text-overflow: ellipsis; white-space: nowrap;">
                            ${movie.title}
                        </h4>
                        <div style="display: flex; gap: 10px; font-size: 0.85rem; color: #b3b3b3;">
                            <span><i class="fas fa-star" style="color: #ffd700;"></i> ${rating}</span>
                            ${year ? `<span>${year}</span>` : ''}
                        </div>
                    </div>
                </div>
            `;
        }).join('');
        
        this.autocompleteContainer.innerHTML = html + `
            <div style="padding: 12px; text-align: center; border-top: 1px solid rgba(255,255,255,0.1);">
                <button onclick="searchManager.performFullSearch('${this.searchInput.value}')" 
                        style="background: none; border: none; color: #e50914; cursor: pointer; 
                               font-weight: 600; font-size: 0.9rem;">
                    Xem tất cả kết quả <i class="fas fa-arrow-right"></i>
                </button>
            </div>
        `;
        
        // Bind click events to items
        this.autocompleteContainer.querySelectorAll('.autocomplete-item').forEach(item => {
            item.addEventListener('mouseenter', function() {
                this.style.background = 'rgba(255,255,255,0.1)';
            });
            
            item.addEventListener('mouseleave', function() {
                this.style.background = 'transparent';
            });
            
            item.addEventListener('click', () => {
                const movieId = item.dataset.movieId;
                window.location.href = `/movie/detail/${movieId}`;
            });
        });
        
        this.showAutocomplete();
    }
    
    showAutocomplete() {
        this.autocompleteContainer.style.display = 'block';
    }
    
    hideAutocomplete() {
        this.autocompleteContainer.style.display = 'none';
    }
    
    clearSearch() {
        this.searchInput.value = '';
        if (this.searchClear) {
            this.searchClear.style.display = 'none';
        }
        this.hideAutocomplete();
        this.searchInput.focus();
        
        // Reset to homepage if on search results page
        if (window.location.pathname.includes('/search')) {
            window.location.href = '/';
        }
    }
    
    performFullSearch(query) {
        if (!query.trim()) return;
        
        // Navigate to search results page or update current page
        window.location.href = `/search?q=${encodeURIComponent(query)}`;
    }
}

// Genre Filter Manager
class GenreFilterManager {
    constructor() {
        this.genreLinks = document.querySelectorAll('.genre-link, .main-nav a');
        this.init();
    }
    
    init() {
        this.genreLinks.forEach(link => {
            link.addEventListener('click', (e) => {
                const href = link.getAttribute('href');
                
                // Only handle genre/category links
                if (href && href.includes('genre') || link.classList.contains('genre-link')) {
                    e.preventDefault();
                    this.filterByGenre(link);
                }
            });
        });
    }
    
    filterByGenre(linkElement) {
        // Remove active class from all
        this.genreLinks.forEach(l => l.classList.remove('active'));
        
        // Add active to clicked
        linkElement.classList.add('active');
        
        const genreId = linkElement.dataset.genreId || this.extractGenreFromText(linkElement.textContent);
        
        if (genreId) {
            this.loadMoviesByGenre(genreId);
        }
    }
    
    extractGenreFromText(text) {
        // Map genre names to TMDB genre IDs
        const genreMap = {
            'Hành động': 28,
            'Phiêu lưu': 12,
            'Hoạt hình': 16,
            'Hài': 35,
            'Tội phạm': 80,
            'Tài liệu': 99,
            'Chính kịch': 18,
            'Gia đình': 10751,
            'Giả tưởng': 14,
            'Lịch sử': 36,
            'Kinh dị': 27,
            'Nhạc': 10402,
            'Bí ẩn': 9648,
            'Lãng mạn': 10749,
            'Khoa học viễn tưởng': 878,
            'Phim truyền hình': 10770,
            'Giật gân': 53,
            'Chiến tranh': 10752,
            'Miền Tây': 37
        };
        
        return genreMap[text.trim()] || null;
    }
    
    async loadMoviesByGenre(genreId) {
        try {
            const url = `${TMDB_BASE_URL}/discover/movie?api_key=${TMDB_API_KEY}&language=vi-VN&with_genres=${genreId}&sort_by=popularity.desc&page=1`;
            
            const response = await fetch(url);
            const data = await response.json();
            
            // Update the movie grid on homepage
            this.updateMovieGrid(data.results);
            
        } catch (error) {
            console.error('Genre filter error:', error);
        }
    }
    
    updateMovieGrid(movies) {
        // Find the main movie grid (first one usually)
        const grids = document.querySelectorAll('.movie-slider');
        if (grids.length === 0) return;
        
        const targetGrid = grids[0]; // Update the first grid
        
        targetGrid.innerHTML = movies.map(movie => {
            const posterUrl = movie.poster_path 
                ? `${IMAGE_BASE_URL}/w500${movie.poster_path}`
                : '/images/placeholder.jpg';
            
            return `
                <div class="movie-card" onclick="location.href='/movie/detail/${movie.id}'">
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
        
        // Scroll to the grid
        targetGrid.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
}

// Initialize on page load
let searchManager;
let genreFilterManager;

document.addEventListener('DOMContentLoaded', () => {
    searchManager = new SearchManager();
    genreFilterManager = new GenreFilterManager();
});

// Export for global use
window.searchManager = searchManager;