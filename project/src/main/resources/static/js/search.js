  // ============ SEARCH PAGE - COMPLETE UNIFIED SCRIPT ============
    (function() {
        'use strict';
        
        const API_KEY = 'eac03c4e09a0f5099128e38cb0e67a8f';
        const API_BASE = 'https://api.themoviedb.org/3';
        const IMG_BASE = 'https://image.tmdb.org/t/p';
        
        // DOM Elements
        const mainInput = document.getElementById('mainSearchInput');
        const voiceBtn = document.getElementById('voiceSearchPageBtn');
        const filterBtn = document.getElementById('filterPageBtn');
        const aiBtn = document.getElementById('aiSearchPageBtn');
        const liveSuggestions = document.getElementById('liveSuggestions');
        const filtersPanel = document.getElementById('filtersPanel');
        const pagination = document.getElementById('pagination');
        const relatedCarousel = document.getElementById('relatedCarousel');
        const aiCarousel = document.getElementById('aiCarousel');
        const trendingCarousel = document.getElementById('trendingCarousel');
        const personSearchCache = new Map();
        
        // State Management
        const PAGE_SIZE = 6; // Load 6 items per scroll
        let cachedResults = [];
        let displayedCount = 0;
        let nextApiPage = 2;
        let totalApiPages = 1;
        let lastQuery = '';
        let peopleCache = new Map();
        let loadingMore = false;
        let abortController = null;

        // [TỐI ƯU 1] Thêm bộ nhớ đệm phía Client
        const queryCache = new Map();
        
        // Filter State
        const filterState = {
            quickFilter: null,
            genres: [],
            yearFrom: null,
            yearTo: null,
            minRating: 0
        };
        
        const GENRES = [
            {id: 28, name: 'Hành động'}, {id: 12, name: 'Phiêu lưu'}, {id: 16, name: 'Hoạt hình'},
            {id: 35, name: 'Hài'}, {id: 80, name: 'Hình sự'}, {id: 18, name: 'Chính kịch'},
            {id: 10751, name: 'Gia đình'}, {id: 14, name: 'Giả tưởng'}, {id: 27, name: 'Kinh dị'},
            {id: 10749, name: 'Lãng mạn'}, {id: 878, name: 'Khoa học viễn tưởng'}, 
            {id: 53, name: 'Gây cấn'}, {id: 10752, name: 'Chiến tranh'}
        ];

        // =========================================================================
        // 2. KHÔI PHỤC & NÂNG CẤP LOGIC AI SEARCH (AUTH + SESSION STORAGE)
        // =========================================================================
        function initAISearch() {
            const aiToggle = document.getElementById('aiSearchPageBtn'); 
            const aiModal = document.getElementById('aiSearchModal');
            const aiClose = document.getElementById('aiModalClose');
            const aiCancel = document.getElementById('aiCancelBtn');
            const aiSearchBtn = document.getElementById('aiSearchBtn');
            const aiInput = document.getElementById('aiSearchInput');
            const aiLoading = document.getElementById('aiLoading');
            const aiError = document.getElementById('aiError');
            const aiMovieResults = document.getElementById('aiMovieResults');

            // Key lưu trữ session
            const STORAGE_KEY = 'ffilm_ai_search_state';

            // --- [NEW] 1. KHÔI PHỤC TRẠNG THÁI CŨ (NẾU CÓ) ---
            function restoreState() {
                const savedState = sessionStorage.getItem(STORAGE_KEY);
                if (savedState && aiInput && aiMovieResults) {
                    try {
                        const data = JSON.parse(savedState);
                        // Khôi phục text đã nhập
                        aiInput.value = data.prompt || '';
                        // Khôi phục kết quả
                        if (data.answer || (data.suggestions && data.suggestions.length > 0)) {
                            aiMovieResults.hidden = false;
                            renderAIRecommendation(data.answer, data.suggestions || []);
                        }
                    } catch (e) {
                        console.error("Lỗi khôi phục trạng thái AI Search", e);
                        sessionStorage.removeItem(STORAGE_KEY);
                    }
                }
            }

            // Gọi hàm khôi phục ngay khi init
            restoreState();

            // --- [NEW] 2. CHECK LOGIN KHI MỞ MODAL ---
            aiToggle?.addEventListener('click', (e) => {
                e.stopPropagation();
                
                // Kiểm tra đăng nhập (Biến global từ header.html)
                if (typeof window.isUserLoggedIn !== 'undefined' && !window.isUserLoggedIn) {
                    window.location.href = '/login';
                    return;
                }

                if(aiModal) {
                    aiModal.hidden = false;
                    document.body.style.overflow = 'hidden';
                    // Focus vào input nếu chưa có nội dung, hoặc giữ nguyên nếu đã restore
                    if (!aiInput.value) setTimeout(() => aiInput.focus(), 100);
                }
            });

            // Close modal logic
            const closeModal = () => {
                if(aiModal) {
                    aiModal.hidden = true;
                    document.body.style.overflow = '';
                }
            };

            aiClose?.addEventListener('click', closeModal);
            aiCancel?.addEventListener('click', closeModal);
            aiModal?.querySelector('.ai-modal-overlay')?.addEventListener('click', closeModal);

            // Example chips
            document.querySelectorAll('.example-chip').forEach(chip => {
                chip.addEventListener('click', function() {
                    aiInput.value = this.dataset.example;
                    aiInput.focus();
                });
            });

            // AI Search Submit
            aiSearchBtn?.addEventListener('click', async () => {
                const description = aiInput.value.trim();

                if (!description) {
                    showAIError('Vui lòng nhập mô tả về bộ phim bạn muốn tìm');
                    return;
                }

                // UI Loading
                if(aiLoading) aiLoading.hidden = false;
                if(aiError) aiError.hidden = true;
                if(aiMovieResults) aiMovieResults.hidden = true;
                aiSearchBtn.disabled = true;

                try {
                    const response = await fetch('/api/ai-search/suggest', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ description })
                    });

                    const data = await response.json();

                    if (!data.success) {
                        throw new Error(data.message || 'AI failed');
                    }

                    if(aiLoading) aiLoading.hidden = true;

                    if (aiMovieResults) {
                        aiMovieResults.hidden = false;
                        renderAIRecommendation(data.answer, data.suggestions || []);
                        
                        // --- [NEW] 3. LƯU TRẠNG THÁI VÀO SESSION STORAGE ---
                        sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
                            prompt: description,
                            answer: data.answer,
                            suggestions: data.suggestions || []
                        }));
                    }

                } catch (error) {
                    if(aiLoading) aiLoading.hidden = true;
                    showAIError(error.message);
                } finally {
                    aiSearchBtn.disabled = false;
                }
            });

            function renderAIRecommendation(answer, suggestions) {
                if (!aiMovieResults) return;

                let html = `
                    <div class="ai-answer-box">
                        <div class="ai-answer-header">
                            <i class="fas fa-robot"></i>
                            <strong>Gợi ý từ AI:</strong>
                        </div>
                        <p class="ai-answer-text">${escapeHtml(answer || '')}</p>
                    </div>
                `;

                if (suggestions && suggestions.length > 0) {
                    html += `
                        <div class="ai-suggestions-box">
                            <p class="suggestions-label">
                                <i class="fas fa-lightbulb"></i>
                                Tìm kiếm các từ khóa này:
                            </p>
                            <div class="ai-suggestions-chips">
                                ${suggestions.map(s => `
                                    <button class="ai-suggestion-chip" data-query="${escapeHtml(s)}">
                                        <i class="fas fa-search"></i>
                                        ${escapeHtml(s)}
                                    </button>
                                `).join('')}
                            </div>
                        </div>
                    `;
                }

                aiMovieResults.innerHTML = html;

                // Gán sự kiện click -> Chuyển trang (State đã được lưu ở bước submit)
                aiMovieResults.querySelectorAll('.ai-suggestion-chip').forEach(chip => {
                    chip.addEventListener('click', function() {
                        const query = this.dataset.query;
                        if(mainInput) {
                            mainInput.value = query;
                            // Đóng modal trước khi search để trải nghiệm mượt hơn
                            closeModal(); 
                            performSearch(); 
                        }
                    });
                });
            }

            function showAIError(message) {
                if(aiError) {
                    aiError.hidden = false;
                    const msgEl = document.getElementById('aiErrorMessage');
                    if(msgEl) msgEl.textContent = message;
                }
            }
        }

        
        // ============ LIVE SUGGESTIONS (FIX VĐ 1, 5, 6) ============
        let searchTimeout;
        
        if (mainInput && liveSuggestions) {
            mainInput.addEventListener('input', (e) => {
                clearTimeout(searchTimeout);
                const query = e.target.value.trim();
                
                if (query.length < 2) {
                    hideLiveSuggestions();
                    return;
                }
                
                // [FIX PERFORMANCE] Tăng delay từ 300ms → 500ms (giảm số API call)
                searchTimeout = setTimeout(() => fetchLiveSuggestions(query), 500);
            });
            
            liveSuggestions.addEventListener('scroll', debounce(async function() {
                if (liveSuggestions.scrollTop + liveSuggestions.clientHeight >= 
                    liveSuggestions.scrollHeight - 50) {
                    await loadMoreSuggestions();
                }
            }, 150));
        }   

        /**
         * [TỐI ƯU] Tìm kiếm Live Search với Cache
         */
        async function fetchLiveSuggestions(query) {
            // 1. Kiểm tra Cache trước (Phản hồi siêu tốc)
            if (queryCache.has(query)) {
                cachedResults = queryCache.get(query);
                displayedCount = 0;
                liveSuggestions.innerHTML = '';
                
                if (cachedResults.length === 0) {
                    liveSuggestions.innerHTML = '<div style="padding:14px;text-align:center;color:rgba(255,255,255,0.6)">Không tìm thấy kết quả trong thư viện</div>';
                    liveSuggestions.style.display = 'block';
                } else {
                    loadMoreSuggestions(); 
                }
                return; // Kết thúc, không gọi API
            }

            // 2. Nếu không có Cache thì mới gọi API
            if (abortController) abortController.abort();
            abortController = new AbortController();
            
            try {
                const response = await fetch(
                    `/api/movie/search-db?query=${encodeURIComponent(query)}`,
                    { signal: abortController.signal }
                );

                if (response.ok) {
                    const results = await response.json();
                    
                    // [TỐI ƯU] Lưu kết quả vào Cache
                    queryCache.set(query, results);
                    
                    cachedResults = results;
                    displayedCount = 0;
                    
                    liveSuggestions.innerHTML = '';
                    if (results.length === 0) {
                        liveSuggestions.innerHTML = '<div style="padding:14px;text-align:center;color:rgba(255,255,255,0.6)">Không tìm thấy kết quả trong thư viện</div>';
                        liveSuggestions.style.display = 'block';
                    } else {
                        loadMoreSuggestions(); 
                    }
                }
            } catch (error) {
                if (error.name !== 'AbortError') {
                    console.error('Search error:', error);
                }
            }
        }
        
        /**
         * [SỬA VĐ 3] - Không cần gọi ensureMoviesHaveData nữa
         */
        async function loadMoreSuggestions() {
            if (loadingMore) return;
            if (!cachedResults) return;
            
            loadingMore = true;
            
            const start = displayedCount;
            const end = Math.min(cachedResults.length, displayedCount + 10);
            const chunk = cachedResults.slice(start, end);
            const append = start > 0;
            
            if (chunk.length === 0) {
                if (displayedCount === 0) {
                    liveSuggestions.innerHTML = '<div style="padding:14px;text-align:center;color:rgba(255,255,255,0.6)">Không tìm thấy kết quả</div>';
                    liveSuggestions.style.display = 'block';
                }
                loadingMore = false;
                return;
            }
            
            // [SỬA VĐ 3] Bỏ gọi ensureMoviesHaveData(chunk)
            
            // Render
            renderSuggestionsChunk(chunk, append);
            displayedCount = end;
            loadingMore = false;
        }
        
        
        
        /**
         * [ĐÃ SỬA - OFFLINE] Chuyển hướng trực tiếp bằng ID nội bộ
         */
        window.goToDetail = function(element) {
            // Lấy ID từ data-id (đã được renderSuggestionsChunk gán là PK của DB)
            const movieId = element.dataset.id; 
            if (movieId) {
                location.href = `/movie/detail/${movieId}`;
            }
        }

        // ✅ HÀM HELPER: Hiển thị toast error
        function showErrorToast(message) {
            // Tạo toast element
            const toast = document.createElement('div');
            toast.className = 'error-toast';
            toast.innerHTML = `
                <i class="fas fa-exclamation-circle"></i>
                <span>${message}</span>
            `;
            
            // Thêm CSS (nếu chưa có)
            if (!document.getElementById('toast-style')) {
                const style = document.createElement('style');
                style.id = 'toast-style';
                style.innerHTML = `
                    .error-toast {
                        position: fixed;
                        top: 80px;
                        right: 20px;
                        background: rgba(229, 9, 20, 0.95);
                        color: white;
                        padding: 16px 20px;
                        border-radius: 8px;
                        box-shadow: 0 4px 12px rgba(0,0,0,0.4);
                        z-index: 99999;
                        display: flex;
                        align-items: center;
                        gap: 12px;
                        font-size: 14px;
                        max-width: 400px;
                        animation: slideIn 0.3s ease-out;
                    }
                    @keyframes slideIn {
                        from { transform: translateX(100%); opacity: 0; }
                        to { transform: translateX(0); opacity: 1; }
                    }
                `;
                document.head.appendChild(style);
            }
            
            document.body.appendChild(toast);
            
            // Tự động ẩn sau 4s
            setTimeout(() => {
                toast.style.animation = 'slideIn 0.3s ease-out reverse';
                setTimeout(() => toast.remove(), 300);
            }, 4000);
        }
        
        /**
         * [VIẾT LẠI - FIX VĐ 3 & 5] - Cập nhật renderSuggestionsChunk
         */
        function renderSuggestionsChunk(chunk, append = false) {
            const html = chunk.map(item => {
                // Xác định dữ liệu từ DB hay API
                const isFromDB = item.hasOwnProperty('tmdbId') || item.hasOwnProperty('role_info'); 
                
                let id = item.id;
                let tmdbId = item.tmdbId || item.id;
                let title = escapeHtml(item.title || item.name || 'Unknown');
                let year = (item.year || item.release_date || '').substring(0, 4) || '—';
                let rating = item.rating || (item.vote_average ? item.vote_average.toFixed(1) : '—');
                let poster = item.poster || (item.poster_path ? `${IMG_BASE}/w92${item.poster_path}` : '/images/placeholder.jpg');
                
                // [QUAN TRỌNG] Lấy Role Info từ API mới (ưu tiên role_info từ searchMoviesCombined)
                let roleInfo = item.role_info || item._personRole || null; 

                // Data attribute cho sự kiện click
                let dataAttrs = `data-id="${id}"`;
                if (isFromDB) {
                    dataAttrs += ` data-type="db" data-tmdb-id="${tmdbId}"`;
                } else {
                    dataAttrs += ` data-type="api" data-tmdb-id="${tmdbId}" data-title="${title}"`;
                }

                // [SỬA VĐ 5] Logic hiển thị role (Màu vàng/cam nổi bật)
                let roleHtml = '';
                if (roleInfo) {
                    roleHtml = `<div style="color: #ffc107; font-size: 0.85rem; margin-top: 3px; font-weight: 500; display: flex; align-items: center;">
                                    <i class="fas fa-user-tag" style="font-size: 0.75rem; margin-right: 5px;"></i>
                                    ${escapeHtml(roleInfo)}
                                </div>`;
                }

                const meta = `
                    <div style="display:flex; align-items:center; gap:8px; color:rgba(255,255,255,0.6);">
                        <span>${year}</span>
                        <span>•</span>
                        <span style="color:#ffd700">★ ${rating}</span>
                    </div>
                `;
                
                return `
                    <div class="suggestion-item" onclick="goToDetail(this)" ${dataAttrs} 
                        style="display:flex;gap:12px;padding:10px;align-items:start;cursor:pointer;border-bottom:1px solid rgba(255,255,255,0.05);transition:background 0.2s;">
                        <img src="${poster}" alt="${title}" onerror="this.src='/images/placeholder.jpg'" 
                            style="width:45px;height:68px;object-fit:cover;border-radius:4px;flex-shrink:0;">
                        <div class="suggestion-info" style="flex:1;min-width:0;">
                            <div class="suggestion-title" style="font-weight:600;font-size:0.95rem;color:#fff;margin-bottom:2px;">${title}</div>
                            <div class="suggestion-meta" style="font-size:0.8rem;">
                                ${meta}
                            </div>
                            ${roleHtml}
                        </div>
                    </div>
                `;
            }).join('');
            
            if (append) {
                liveSuggestions.insertAdjacentHTML('beforeend', html);
            } else {
                liveSuggestions.innerHTML = html;
            }
            
            liveSuggestions.style.display = 'block';
            
            // Hover effect
            liveSuggestions.querySelectorAll('.suggestion-item').forEach(item => {
                item.addEventListener('mouseenter', function() { this.style.background = 'rgba(255,255,255,0.1)'; });
                item.addEventListener('mouseleave', function() { this.style.background = ''; });
            });
        }
        
        function hideLiveSuggestions() {
            if (liveSuggestions) {
                liveSuggestions.style.display = 'none';
            }
        }
        
        
        
        
        
        // ============ FILTERS SYSTEM (FIX VĐ 3) ============
        function initFilters() {
            if (!filtersPanel || !filterBtn) return;
            
            const filtersClose = document.getElementById('filtersClose');
            const clearFiltersBtn = document.getElementById('clearFilters');
            const applyFiltersBtn = document.getElementById('applyFilters');
            const minRatingSlider = document.getElementById('minRating');
            const ratingValue = document.getElementById('ratingValue');
            
            renderGenres();
            
            // [FIX CUỐI] Khôi phục trạng thái filter từ URL params
            restoreFilterStateFromURL();
            
            // Toggle panel
            filterBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                filtersPanel.hidden = !filtersPanel.hidden;
                filtersPanel.classList.toggle('show');
                filterBtn.classList.toggle('active');
            });
            
            filtersClose?.addEventListener('click', () => {
                filtersPanel.hidden = true;
                filtersPanel.classList.remove('show');
                filterBtn.classList.remove('active');
            });
            
            // Rating slider
            minRatingSlider?.addEventListener('input', (e) => {
                if (ratingValue) {
                    ratingValue.textContent = parseFloat(e.target.value).toFixed(1);
                }
            });
            
            // Clear filters
            clearFiltersBtn?.addEventListener('click', () => {
                filterState.quickFilter = null;
                filterState.genres = [];
                filterState.yearFrom = null;
                filterState.yearTo = null;
                filterState.minRating = 0;
                
                document.querySelectorAll('.quick-filter-chip').forEach(c => c.classList.remove('active'));
                document.querySelectorAll('.genre-chip').forEach(c => c.classList.remove('active'));
                
                const yearFrom = document.getElementById('yearFrom');
                const yearTo = document.getElementById('yearTo');
                if (yearFrom) yearFrom.value = '';
                if (yearTo) yearTo.value = '';
                if (minRatingSlider) minRatingSlider.value = 0;
                if (ratingValue) ratingValue.textContent = '0.0';
                // Trong hàm resetFilters()
                document.getElementById('filter-isFree').value = "";
            });
            
            // Apply filters
            applyFiltersBtn?.addEventListener('click', () => {
                applyFilters();
            });
            
            // Close on click outside
            document.addEventListener('click', (e) => {
                if (liveSuggestions && liveSuggestions.style.display === 'block') {
                    if (!mainInput.contains(e.target) && 
                        !liveSuggestions.contains(e.target) && 
                        (!filtersPanel || !filtersPanel.contains(e.target))) {
                        liveSuggestions.style.display = 'none';
                    }
                }

                if (filtersPanel && filtersPanel.classList.contains('show')) {
                    if (!filtersPanel.contains(e.target) && !filterBtn.contains(e.target)) {
                        filtersPanel.hidden = true;
                        filtersPanel.classList.remove('show');
                        filterBtn.classList.remove('active');
                    }
                }
            });
        }
        
        function renderGenres() {
            const container = document.getElementById('genreFilters');
            if (!container) return;
            
            container.innerHTML = GENRES.map(g => 
                `<button class="genre-chip" data-genre="${g.id}">${g.name}</button>`
            ).join('');
            
            // Quick filters
            document.querySelectorAll('.quick-filter-chip').forEach(chip => {
                chip.addEventListener('click', function() {
                    document.querySelectorAll('.quick-filter-chip').forEach(c => c.classList.remove('active'));
                    this.classList.add('active');
                    filterState.quickFilter = this.dataset.filter;
                });
            });
            
            // Genre chips
            container.querySelectorAll('.genre-chip').forEach(chip => {
                chip.addEventListener('click', function() {
                    const genreId = parseInt(this.dataset.genre);
                    this.classList.toggle('active');
                    
                    if (filterState.genres.includes(genreId)) {
                        filterState.genres = filterState.genres.filter(id => id !== genreId);
                    } else {
                        filterState.genres.push(genreId);
                    }
                });
            });
        }

        /**
         * [FIX CUỐI] Khôi phục trạng thái filter từ URL parameters
         */
        function restoreFilterStateFromURL() {
            const urlParams = new URLSearchParams(window.location.search);
            
            // 1. Quick Filter
            const quickFilter = urlParams.get('quickFilter');
            if (quickFilter) {
                document.querySelectorAll('.quick-filter-chip').forEach(chip => {
                    if (chip.dataset.filter === quickFilter) {
                        chip.classList.add('active');
                        filterState.quickFilter = quickFilter;
                    }
                });
            }
            
            // 2. Genres
            const genres = urlParams.get('genres');
            if (genres) {
                const genreIds = genres.split(',').map(id => parseInt(id));
                filterState.genres = genreIds;
                document.querySelectorAll('.genre-chip').forEach(chip => {
                    if (genreIds.includes(parseInt(chip.dataset.genre))) {
                        chip.classList.add('active');
                    }
                });
            }
            
            // 3. Year Range
            const yearFrom = urlParams.get('yearFrom');
            const yearTo = urlParams.get('yearTo');
            if (yearFrom) {
                document.getElementById('yearFrom').value = yearFrom;
                filterState.yearFrom = yearFrom;
            }
            if (yearTo) {
                document.getElementById('yearTo').value = yearTo;
                filterState.yearTo = yearTo;
            }
            
            // 4. Min Rating
            const minRating = urlParams.get('minRating');
            if (minRating) {
                const slider = document.getElementById('minRating');
                const valueDisplay = document.getElementById('ratingValue');
                if (slider) slider.value = minRating;
                if (valueDisplay) valueDisplay.textContent = parseFloat(minRating).toFixed(1);
                filterState.minRating = parseFloat(minRating);
            }

            // 5. [MỚI] Restore Is Free/Paid
            const isFree = urlParams.get('isFree');
            if (isFree !== null) {
                const isFreeSelect = document.getElementById('filter-isFree');
                if (isFreeSelect) isFreeSelect.value = isFree;
            }
        }
        
        /**
         * [FIX VĐ 3] - Gửi filter về server (reload trang)
         */
        function applyFilters() {
            const query = mainInput?.value?.trim() || '';
            
            if (!query) {
                alert("Vui lòng nhập từ khóa tìm kiếm trước khi lọc.");
                if (mainInput) mainInput.focus();
                return;
            }
            
            // Thu thập filter values
            const yearFrom = document.getElementById('yearFrom')?.value || '';
            const yearTo = document.getElementById('yearTo')?.value || '';
            const minRating = document.getElementById('minRating')?.value || '0';
            const genreIds = filterState.genres.join(',');
            const quickFilter = filterState.quickFilter || '';
            
            // Lấy giá trị dropdown Free/Paid
            const isFreeVal = document.getElementById('filter-isFree')?.value;
            
            // Tạo URL với params
            const params = new URLSearchParams();
            params.set('query', query);
            params.set('page', '1'); // Luôn về trang 1 khi filter
            
            if (genreIds) params.set('genres', genreIds);
            if (yearFrom) params.set('yearFrom', yearFrom);
            if (yearTo) params.set('yearTo', yearTo);
            if (minRating !== '0') params.set('minRating', minRating);
            if (quickFilter) params.set('quickFilter', quickFilter);
            
            // [FIX] Gửi tham số đúng chuẩn
            if (isFreeVal && isFreeVal !== "") {
                params.set('isFree', isFreeVal);
            }

            // ✅ RELOAD trang với filter params
            window.location.href = `/search?${params.toString()}`;
        }
            
            // ============ SEARCH ON ENTER ============
            mainInput?.addEventListener('keydown', (e) => {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    performSearch();
                }
            });
            
            function performSearch() {
                const query = mainInput?.value.trim();
                if (query) {
                    window.location.href = `/search?query=${encodeURIComponent(query)}`;
                }
            }
            
            
            
            
            
            // Load Trending Carousel
            if (trendingCarousel) {
                loadTrendingCarousel();
            }
            
            /**
             * [ĐÃ SỬA - OFFLINE] Load Trending từ DB nội bộ
             */
            async function loadTrendingCarousel() {
                try {
                    // Gọi API nội bộ lấy danh sách phim hot (Top 10)
                    // ID '0' là dummy vì endpoint này không cần ID cụ thể
                    const response = await fetch('/api/movie/0/trending');
                    
                    if (!response.ok) throw new Error('API trending failed');
                    
                    const movies = await response.json();

                    if (movies && movies.length > 0) {
                        // Render bằng hàm createMovieCardHTML có sẵn
                        trendingCarousel.innerHTML = movies.map((movie, index) => {
                            return createMovieCardHTML(movie, index, true); 
                        }).join('');
                        
                        // Kích hoạt lại các tính năng UI
                        if (typeof initHoverCards === 'function') initHoverCards();
                        if (typeof initializeAllCarousels === 'function') initializeAllCarousels();
                    } else {
                        trendingCarousel.innerHTML = '<p style="text-align:center; padding:20px; color:#666">Chưa có dữ liệu xu hướng.</p>';
                    }

                } catch (error) {
                    console.error('Error loading trending:', error);
                }
            }

            // ============ UTILITY FUNCTIONS ============
            function debounce(fn, wait = 250) {
                let timeout;
                return function(...args) {
                    clearTimeout(timeout);
                    timeout = setTimeout(() => fn.apply(this, args), wait);
                };
            }
            
            function escapeHtml(str) {
                if (!str) return '';
                const map = {
                    '&': '&amp;',
                    '<': '&lt;',
                    '>': '&gt;',
                    '"': '&quot;',
                    "'": '&#39;'
                };
                return str.replace(/[&<>"']/g, m => map[m]);
            }

            function normalizeVietnamese(str) {
                if (!str) return '';
                return str.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
            }
            
            
            /**
             * HÀM MỚI: TẠO CHUỖI HTML CHO MOVIE CARD CHUẨN
             * Hàm này tái tạo 1-1 cấu trúc từ index.html và hover-card.html
             * @param {object} movie - Object phim từ API TMDB
             * @param {number} index - Vị trí trong danh sách (cho ranked)
             * @param {boolean} isRanked - Áp dụng style 'ranked' hay không
             */
            function createMovieCardHTML(movie, index, isRanked = false) {
                const movieId = movie.id;
                // Hàm escape để tránh lỗi ký tự
                const escapeHtml = (str) => (str || '').replace(/[&<>"']/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[m]);
                
                const movieTitle = escapeHtml(movie.title || 'Unknown');
                const poster = movie.poster || '/images/placeholder.jpg';
                const backdrop = movie.backdrop || '/images/placeholder.jpg';
                const rating = movie.rating || '—';
                const year = movie.year || '—';
                const safeOverview = escapeHtml(movie.overview || 'Đang tải mô tả...');

                // Data chuẩn từ Service mới
                const runtime = movie.runtime ? movie.runtime + ' phút' : '—';
                const contentRating = movie.contentRating || 'T';
                const country = movie.country || 'Quốc tế';
                
                // [FIX LỖI HIỂN THỊ] Logic Genre + Tooltip
                let genresHtml = '<span class="genre-tag">Không có</span>';
                if (movie.genres && movie.genres.length > 0) {
                    const getName = (g) => (typeof g === 'object' && g.name) ? g.name : g;

                    genresHtml = movie.genres.slice(0, 2)
                        .map(g => `<span class="genre-tag">${escapeHtml(getName(g))}</span>`)
                        .join('');
                    if (movie.genres.length > 2) {
                        const remaining = movie.genres.slice(2);
                        const tooltipHtml = remaining
                            .map(g => `<div class="genre-bubble">${escapeHtml(getName(g))}</div>`)
                            .join('');
                        genresHtml += `
                            <span class="genre-tag genre-tag-more" 
                                  onmouseenter="if(window.showGenreTooltip) window.showGenreTooltip(this)" 
                                  onmouseleave="if(window.hideGenreTooltip) window.hideGenreTooltip(this)">
                                +${remaining.length}
                                <div class="custom-genre-tooltip">${tooltipHtml}</div>
                            </span>
                        `;
                    }
                }
                
                const playerId = `hover-player-search-${movieId}-${index}`; 
                const rankClass = isRanked ? `ranked rank-${((index % 5) + 1)}` : '';
                const rankOverlay = isRanked ? '<div class="ranking-overlay"></div>' : '';
                const rankNumber = isRanked ? `<div class="ranking-number">${index + 1}</div>` : '';
                
                // Lấy hàm Global an toàn
                const clickDetail = `event.stopPropagation(); (window.goToMovieDetail || function(btn){ location.href = '/movie/detail/' + btn.dataset.movieId; })(this)`;
                const clickLike = `event.stopPropagation(); (window.toggleHoverLike || function(btn){ btn.classList.toggle('active'); })(this)`;
                const clickShare = `event.stopPropagation(); (window.showShareModal || function(btn){ alert('Share not loaded'); })(this)`;

                return `
                <div class="movie-card ${rankClass}" data-movie-id="${movieId}">
                    <div class="movie-poster">
                        <img src="${poster}" alt="${movieTitle}" onerror="this.src='/images/placeholder.jpg'">
                        ${rankOverlay}
                        ${rankNumber}
                    </div>
                    <div class="movie-info">
                        <h3>${movieTitle}</h3>
                        <p class="movie-rating">⭐ <span>${rating}</span></p>
                    </div>
                    
                    <div class="movie-hover-card" data-movie-id="${movieId}">
                        <div class="hover-card-media">
                            <img class="hover-card-image" src="${backdrop}" alt="${movieTitle}" onerror="this.src='/images/placeholder.jpg'">
                            <div class="hover-player-container"><div class="hover-player" id="${playerId}"></div></div>
                            <button class="hover-volume-btn" type="button" title="Bật/Tắt tiếng" onclick="event.stopPropagation();"><i class="fas fa-volume-mute"></i></button>
                        </div>
                        <div class="hover-card-content">
                            <div class="hover-card-actions">
                                <button class="hover-play-btn" type="button" data-movie-id="${movieId}" onclick="${clickDetail}">
                                    <i class="fas fa-play"></i> Xem ngay
                                </button>
                                <button class="hover-action-icon hover-like-btn" type="button" 
                                        data-movie-id="${movieId}" data-tmdb-id="${movie.tmdbId || movieId}" 
                                        onclick="${clickLike}" title="Thêm vào danh sách">
                                    <i class="far fa-heart"></i>
                                </button>
                                <button class="hover-action-icon hover-share-btn" type="button"
                                        data-movie-id="${movieId}" data-movie-title="${movieTitle}"
                                        onclick="${clickShare}" title="Chia sẻ">
                                    <i class="fas fa-share-alt"></i>
                                </button>
                            </div>
                            <h3 class="hover-card-title">${movieTitle}</h3>
                            <div class="hover-card-meta">
                                <span class="meta-rating"><i class="fas fa-star"></i> <span>${rating}</span></span>
                                <span class="meta-year">${year}</span>
                                <span class="meta-quality">HD</span>
                            </div>
                            <div class="hover-card-meta-extra">
                                <span class="meta-extra-rating">${contentRating}</span>
                                <span class="meta-extra-runtime" style="white-space: nowrap;">${runtime}</span>
                                <span class="meta-extra-country">${country}</span>
                            </div>
                            <div class="hover-card-genres">${genresHtml}</div>
                            <p class="hover-card-description">${safeOverview}</p>
                        </div>
                    </div>
                </div>
                `;
            }
            
            
            
            // ============ VOICE SEARCH ============
            if (voiceBtn) {
                if ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window) {
                    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
                    const recognition = new SpeechRecognition();
                    recognition.lang = 'vi-VI';
                    recognition.interimResults = false;
                    
                    voiceBtn.addEventListener('click', (e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        
                        if (voiceBtn.classList.contains('recording')) {
                            recognition.stop();
                        } else {
                            try {
                                recognition.start();
                            } catch (err) {
                                console.warn('Voice recognition error:', err);
                            }
                        }
                    });
                    
                    recognition.onstart = () => {
                        voiceBtn.classList.add('recording');
                    };
                    
                    recognition.onend = () => {
                        voiceBtn.classList.remove('recording');
                    };
                    
                    recognition.onresult = (event) => {
                        const transcript = event.results[0][0].transcript;
                        if (mainInput) {
                            mainInput.value = transcript;
                            mainInput.focus();
                            fetchLiveSuggestions(transcript);
                        }
                    };
                    
                    recognition.onerror = (event) => {
                        voiceBtn.classList.remove('recording');
                        console.warn('Voice recognition error:', event.error);
                    };
                } else {
                    voiceBtn.style.display = 'none';
                }
            }
            
            // ============ AI SEARCH BUTTON ============
            if (aiBtn) {
                aiBtn.addEventListener('click', () => {
                    // Open AI modal (assuming it exists in header fragment)
                    const aiModal = document.getElementById('aiSearchModal');
                    if (aiModal) {
                        aiModal.hidden = false;
                        document.body.style.overflow = 'hidden';
                    }
                });
            }
            
            // ============ INITIALIZE ============
            initFilters();
            initAISearch();

            // ✅ Khởi tạo hover cards VÀ CAROUSELS sau khi DOM load
            document.addEventListener('DOMContentLoaded', function() {
                // [FIX CUỐI] Thêm class has-results nếu có query
                const urlParams = new URLSearchParams(window.location.search);
                if (urlParams.get('query')) {
                    document.body.classList.add('has-results');
                }
                // Import functions từ script.js
                if (typeof initHoverCards === 'function') {
                    initHoverCards();
                }
                
                // Đảm bảo genre map đã load
                if (typeof loadGenreMap === 'function') {
                    loadGenreMap();
                }

                // [THÊM] KÍCH HOẠT CÁC CAROUSEL RENDER BỞI JAVA
                if (typeof initCarousel === 'function') {
                    // (trendingCarousel đã được init ở hàm loadTrendingCarousel)
                    if (document.getElementById('relatedCarousel')) {
                        initCarousel('relatedCarousel', 'relatedCarouselPrev', 'relatedCarouselNext');
                    }
                    if (document.getElementById('aiCarousel')) {
                        initCarousel('aiCarousel', 'aiCarouselPrev', 'aiCarouselNext');
                    }
                }
            });
            
            // Debug helpers
            window.__searchDebug = {
                getCached: () => cachedResults,
                getDisplayedCount: () => displayedCount,
                getFilterState: () => filterState,
                loadMore: loadMoreSuggestions,
                applyFilters: applyFilters
            };
            
            console.log('✅ Search page script initialized successfully');
            
        })();