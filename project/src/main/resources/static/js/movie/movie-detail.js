//==============================================================//
/*---- 1. KHỞI TẠO VÀ CẤU HÌNH ----*/
//==============================================================//

(function() {
    'use strict';
    
    //----- Constants
    const TMDB_API_KEY_DETAIL = 'eac03c4e09a0f5099128e38cb0e67a8f'; 
    const BASE_URL_DETAIL = 'https://api.themoviedb.org/3';
    
    //----- DOM IDs
    const sectionEl = document.getElementById('trailerSection');
    const gridEl = document.getElementById('trailerGrid');
    const fallbackEl = document.getElementById('trailerFallback');
    const currentMovieId = document.querySelector('meta[name="movie-id"]')?.content;
    const currentTmdbId = document.querySelector('meta[name="tmdb-id"]')?.content;

    //----- Khởi tạo Event Listeners
    document.addEventListener('DOMContentLoaded', () => {
        // [UI] KÍCH HOẠT LOGO & VIDEO
        if (typeof displayHeroExtras === 'function') displayHeroExtras();
        if (typeof YT !== 'undefined' && YT.Player) if (typeof initHeroVideo === 'function') initHeroVideo();
        
        // [ASYNC] Tải trailer và Carousel
        loadTrailers();
        loadAsyncCarousels();
        
        // [UI] Kiểm tra Mô tả
        checkDescriptionHeight();
    });

    //==============================================================//
    /*---- 2. UI FUNCTIONS (ONCLICK) ----*/
    //==============================================================//

    //----- Mở Global Trailer Modal
    window.openGlobalTrailer = function(videoId) {
        document.getElementById('globalTrailerModal').classList.add('active');
        document.getElementById('globalTrailerFrame').src = `https://www.youtube.com/embed/${videoId}?autoplay=1&rel=0`;
    }

    //----- Đóng Global Trailer Modal
    window.closeGlobalTrailer = function() {
        document.getElementById('globalTrailerModal').classList.remove('active');
        document.getElementById('globalTrailerFrame').src = '';
    }

    //----- Toggle Xem tất cả Diễn viên
    window.toggleCast = function() {
        const castGrid = document.getElementById('castGrid');
        const btn = document.getElementById('castToggleBtn');
        castGrid.classList.toggle('expanded');
        if (btn) btn.textContent = btn.textContent === 'Xem tất cả' ? 'Thu gọn' : 'Xem tất cả';
    }

    //----- Toggle Mô tả (FPT Play Style)
    window.toggleFPTDescription = function() {
        const descContent = document.getElementById('descContent');
        const descText = document.getElementById('descText');
        const btn = document.getElementById('descToggleBtn');
        
        descContent.classList.toggle('expanded');
        descText.classList.toggle('expanded');
        if (btn) {
            btn.classList.toggle('expanded');
            btn.querySelector('span').textContent = btn.classList.contains('expanded') ? 'Ẩn bớt' : 'Xem thêm';
        }
    }
    
    //----- Kiểm tra chiều cao mô tả để ẩn nút 'Xem thêm'
    function checkDescriptionHeight() {
        const desc = document.getElementById('descText');
        const descContent = document.getElementById('descContent');
        const btn = document.getElementById('descToggleBtn');
        
        //----- Chỉ chạy nếu các phần tử tồn tại
        if (!desc || !descContent || !btn) return;
        
        //----- Logic: Nếu nội dung scrollHeight nhỏ hơn clientHeight + một chút margin
        if (desc.scrollHeight <= desc.clientHeight + 5) {
            if (btn) btn.style.display = 'none';
        }
    }

    //----- Toggle Emoji Picker (Comment)
    window.toggleEmojiPicker = function(event) {
        event.stopPropagation();
        const picker = document.getElementById('emojiPicker');
        if (picker) picker.style.display = picker.style.display === 'none' ? 'flex' : 'none';
    }
    
    //----- Thêm Emoji vào input (Comment)
    window.addEmoji = function(emoji) {
        const input = document.getElementById('commentInput');
        if (input) {
            input.value += emoji;
            input.focus();
        }
    }
    
    //----- Đóng Emoji Picker khi click ra ngoài
    document.addEventListener('click', function(event) {
        const picker = document.getElementById('emojiPicker');
        if (picker && picker.style.display === 'flex') {
            if (!picker.contains(event.target) && !event.target.classList.contains('emoji-btn')) {
                picker.style.display = 'none';
            }
        }
    });


    //==============================================================//
    /*---- 3. ASYNC TRAILER LOAD ----*/
    //==============================================================//

    //----- Tải trailer từ TMDB (dùng tmdbId)
    async function loadTrailers() {
        if (!sectionEl || !gridEl || !fallbackEl) return;

        try {
            if (!currentTmdbId) {
                //----- Phim tự tạo
                console.log('Phim tự tạo, không tải trailer TMDB.');
                sectionEl.style.display = 'block'; gridEl.style.display = 'none'; fallbackEl.style.display = 'block';
                return; 
            }

            let res = await fetch(`${BASE_URL_DETAIL}/movie/${currentTmdbId}/videos?api_key=${TMDB_API_KEY_DETAIL}&language=vi-VN&include_adult=false`);
            let data = await res.json();
            let videos = (data.results || []).filter(v => v.site === 'YouTube' && ['Trailer','Teaser','Featurette'].includes(v.type));
            
            //----- Fallback tiếng Anh nếu thiếu
            if (videos.length < 3) { 
                let resEn = await fetch(`${BASE_URL_DETAIL}/movie/${currentTmdbId}/videos?api_key=${TMDB_API_KEY_DETAIL}&language=en-US&include_adult=false`);
                let dataEn = await resEn.json();
                let videosEn = (dataEn.results || []).filter(v => v.site === 'YouTube' && ['Trailer','Teaser'].includes(v.type));
                const existing = new Set(videos.map(v => v.key));
                videos = [...videos, ...videosEn.filter(v => !existing.has(v.key))];
            }

            const top3 = videos.slice(0, 3); 
            sectionEl.style.display = 'block';

            if (top3.length > 0) {
                gridEl.innerHTML = top3.map(v => `
                    <div class="trailer-card" onclick="openGlobalTrailer('${v.key}')">
                        <img src="https://img.youtube.com/vi/${v.key}/mqdefault.jpg" alt="${v.name}">
                        <i class="fas fa-play-circle trailer-play"></i>
                        <div class="trailer-title">${v.name}</div>
                    </div>
                `).join('');
                gridEl.style.display = 'grid'; fallbackEl.style.display = 'none'; 
            } else {
                gridEl.style.display = 'none'; fallbackEl.style.display = 'block'; 
            }
        } catch (e) { 
            console.error("Lỗi tải trailer:", e); 
            if (sectionEl) sectionEl.style.display = 'block';
            if (gridEl) gridEl.style.display = 'none';
            if (fallbackEl) fallbackEl.style.display = 'block'; 
        }
    }

    //==============================================================//
    /*---- 4. ASYNC CAROUSEL LOAD ----*/
    //==============================================================//

    //----- Tải 3 carousel nặng bằng JS
    async function loadAsyncCarousels() {
        if (!currentMovieId) return;

        // 1. Tải Trending
        loadAndRenderCarousel(`/api/movie/${currentMovieId}/trending`, 'trendingSidebar', 'trending', null, null, null, null);
        
        // 2. Tải Similar
        loadAndRenderCarousel(`/api/movie/${currentMovieId}/similar`, 'similarSlider', 'card', 'similarPrevBtn', 'similarNextBtn', 'similarSection', null);

        // 3. Tải Recommended
        loadAndRenderCarousel(`/api/movie/${currentMovieId}/recommended`, 'recommendSlider', 'card', 'recommendPrevBtn', 'recommendNextBtn', 'recommendSection', 'recommendTitle');
    }

    /**
     * Hàm helper tải và render carousel
     */
    async function loadAndRenderCarousel(apiUrl, targetId, renderType, prevBtnId, nextBtnId, sectionId, titleId) {
        try {
            const response = await fetch(apiUrl);
            if (!response.ok) throw new Error(`API ${apiUrl} failed`);
            
            const data = await response.json();
            let movies = Array.isArray(data) ? data : (data.movies || []);
            let title = Array.isArray(data) ? null : (data.title || null);

            const container = document.getElementById(targetId);
            if (!container) return;
            
            if (movies.length === 0) {
                if(sectionId) document.getElementById(sectionId).style.display = 'none';
                return;
            }
            
            //----- Hiển thị section
            if(sectionId) document.getElementById(sectionId).style.display = 'block';
            if(titleId && title) document.getElementById(titleId).textContent = title;

            //----- Render HTML
            if (renderType === 'trending') {
                container.innerHTML = movies.map((m, i) => renderTrendingCard(m, i)).join('');
            } else {
                container.innerHTML = movies.map(m => renderMovieCard(m)).join('');
            }
            
            //----- Kích hoạt lại Hover Cards và Carousel
            if (typeof initHoverCards === 'function') initHoverCards();
            if (typeof initializeAllCarousels === 'function') initializeAllCarousels();

        } catch (e) {
            console.error(`Lỗi tải carousel ${apiUrl}:`, e);
            if(sectionId) document.getElementById(sectionId).style.display = 'none';
        }
    }

    //----- Hàm render HTML cho Card (Similar/Recommended)
    function renderMovieCard(movie) {
        const poster = movie.poster || '/images/placeholder.jpg';
        return `
            <div class="movie-card" data-movie-id="${movie.id}">
                <div class="movie-poster">
                    <img src="${poster}" alt="${movie.title}" onerror="this.src='/images/placeholder.jpg'" loading="lazy">
                </div>
                <div class="movie-info">
                    <h3>${movie.title}</h3>
                    <p class="movie-rating">⭐ <span>${movie.rating}</span></p>
                </div>
                <div class="movie-hover-card" data-movie-id="${movie.id}">
                    <div class="hover-card-media">
                        <img class="hover-card-image" src="${movie.backdrop || '/images/placeholder.jpg'}" alt="${movie.title}" onerror="this.src='/images/placeholder.jpg'">
                        <div class="hover-player-container"><div class="hover-player" id="hover-player-async-${movie.id}"></div></div>
                        <button class="hover-volume-btn" type="button" title="Bật/Tắt tiếng"><i class="fas fa-volume-mute"></i></button>
                    </div>
                    <div class="hover-card-content">
                        <div class="hover-card-actions">
                            <button class="hover-play-btn" type="button" data-movie-id="${movie.id}" onclick="event.stopPropagation(); goToMovieDetail(this)"><i class="fas fa-play"></i> Xem ngay</button>
                            <button class="hover-action-icon hover-like-btn" type="button" onclick="event.stopPropagation(); toggleHoverLike(this)" title="Thêm vào danh sách"><i class="far fa-heart"></i></button>
                            <button class="hover-action-icon hover-share-btn" type="button" data-movie-id="${movie.id}" data-movie-title="${movie.title}" onclick="event.stopPropagation(); showShareModal(this)" title="Chia sẻ"><i class="fas fa-share-alt"></i></button>
                        </div>
                        <h3 class="hover-card-title">${movie.title}</h3>
                        <div class="hover-card-meta">
                            <span class="meta-rating"><i class="fas fa-star"></i> <span>${movie.rating}</span></span>
                            <span class="meta-year">${movie.year}</span>
                            <span class="meta-quality">HD</span>
                        </div>
                        <div class="hover-card-meta-extra">
                            <span class="meta-extra-rating loading-meta">T</span>
                            <span class="meta-extra-runtime loading-meta">— phút</span>
                            <span class="meta-extra-country loading-meta">Quốc gia</span>
                        </div>
                        <div class="hover-card-genres"><span class="genre-tag loading-genre">Đang tải...</span></div>
                        <p class="hover-card-description">${movie.overview || 'Đang tải mô tả...'}</p>
                    </div>
                </div>
            </div>
        `;
    }

    //----- Hàm render HTML cho Trending Sidebar
    function renderTrendingCard(m, i) {
        const rankClass = 'rank-' + ((i % 5) + 1);
        const backdrop = m.backdrop || '/images/placeholder.jpg';
        return `
            <div class="trending-item ${rankClass}" onclick="window.location.href='/movie/detail/${m.id}'">
                <div class="trending-thumb">
                    <img src="${backdrop}" alt="${m.title}" onerror="this.src='/images/placeholder.jpg'">
                    <div class="ranking-number">${i + 1}</div>
                </div>
                <div class="trending-info">
                    <div class="trending-title">${m.title}</div>
                    <div class="trending-meta">${m.year} • ⭐ ${m.rating}</div>
                </div>
            </div>
        `;
    }

})();