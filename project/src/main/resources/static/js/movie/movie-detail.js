//==============================================================
/*---- 1. KHỞI TẠO & TẢI BẤT ĐỒNG BỘ ----*/
//==============================================================

document.addEventListener('DOMContentLoaded', () => {
    // [OFFLINE MODE] Tải trailer từ dữ liệu có sẵn trong DOM (Database)
    renderLocalTrailer();
    
    // Tải 3 carousel nặng (Similar, Recommended, Trending) từ DB nội bộ
    loadAsyncCarousels();

    // Khởi tạo video banner (nếu có)
    if (typeof YT !== 'undefined' && YT.Player) {
        if (typeof initHeroVideo === 'function') initHeroVideo();
    }
    
    // Hiển thị logo (nếu có path)
    if (typeof displayHeroExtras === 'function') displayHeroExtras();
});

//==============================================================
/*---- 2. HÀM RENDER TRAILER (OFFLINE) ----*/
//==============================================================

/**
 * [ĐÃ SỬA - OFFLINE] Không gọi API TMDB nữa.
 * Lấy Trailer Key trực tiếp từ thuộc tính data-trailer-key của Hero Banner.
 */
function renderLocalTrailer() {
    const sectionEl = document.getElementById('trailerSection');
    const gridEl = document.getElementById('trailerGrid');
    const fallbackEl = document.getElementById('trailerFallback');
    const heroBanner = document.getElementById('heroBanner');

    if (!sectionEl || !gridEl || !fallbackEl || !heroBanner) return;

    // 1. Lấy key từ DB đã được render ra HTML
    const trailerKey = heroBanner.getAttribute('data-trailer-key');
    
    // 2. Kiểm tra có trailer không
    if (trailerKey && trailerKey !== 'null' && trailerKey !== '') {
        sectionEl.style.display = 'block';
        fallbackEl.style.display = 'none';
        gridEl.style.display = 'grid';

        // 3. Render Video Card (Chỉ 1 video chính thức)
        gridEl.innerHTML = `
            <div class="trailer-card" onclick="openGlobalTrailer('${trailerKey}')">
                <img src="https://img.youtube.com/vi/${trailerKey}/mqdefault.jpg" alt="Trailer Chính Thức">
                <i class="fas fa-play-circle trailer-play"></i>
                <div class="trailer-title">Trailer Chính Thức</div>
            </div>
        `;
    } else {
        // Không có trailer trong DB
        sectionEl.style.display = 'block';
        gridEl.style.display = 'none';
        fallbackEl.style.display = 'block';
    }
}

//==============================================================
/*---- 3. HÀM TẢI CAROUSEL (OFFLINE - DATABASE ONLY) ----*/
//==============================================================

async function loadAsyncCarousels() {
    // Lấy ID từ meta tag (đã được Controller gán)
    const movieIdMeta = document.querySelector('meta[name="movie-id"]');
    if (!movieIdMeta) return;
    const movieId = movieIdMeta.content;

    // 1. Trending Sidebar (Gọi API nội bộ)
    loadAndRenderCarousel(`/api/movie/${movieId}/trending`, 'trendingSidebar', 'trending', null, null, null, null);
    
    // 2. Similar Movies (Gọi API nội bộ - query theo Genre DB)
    loadAndRenderCarousel(`/api/movie/${movieId}/similar`, 'similarSlider', 'card', 'similarPrevBtn', 'similarNextBtn', 'similarSection', null);

    // 3. Recommended Movies (Gọi API nội bộ - query theo Waterfall DB)
    loadAndRenderCarousel(`/api/movie/${movieId}/recommended`, 'recommendSlider', 'card', 'recommendPrevBtn', 'recommendNextBtn', 'recommendSection', 'recommendTitle');
}

/**
 * [MỚI - VĐ 9] Hàm helper tải và render 1 carousel
 */
async function loadAndRenderCarousel(apiUrl, targetId, renderType, prevBtnId, nextBtnId, sectionId, titleId) {
    try {
        const response = await fetch(apiUrl);
        if (!response.ok) throw new Error(`API ${apiUrl} failed`);
        
        const data = await response.json();
        
        let movies = [];
        let title = null;
        // [MỚI] Lấy thêm dữ liệu ảnh
        let headerImage = null;
        let headerLogo = null;
        
        if (Array.isArray(data)) {
            movies = data;
        } else { 
            movies = data.movies || [];
            title = data.title || null;
            headerImage = data.headerImage;
            headerLogo = data.headerLogo;
        }

        const container = document.getElementById(targetId);
        if (!container) return;
        
        if (movies.length === 0) {
            if(sectionId) document.getElementById(sectionId).style.display = 'none';
            return;
        }
        
        //----- Hiển thị section
        if(sectionId) document.getElementById(sectionId).style.display = 'block';
        
        //----- [LOGIC MỚI - UI/UX CAO CẤP] Hiển thị Tiêu đề + Ảnh
        if(titleId) {
            const titleEl = document.getElementById(titleId);
            if (titleEl) {
                // Reset nội dung cũ
                titleEl.innerHTML = ''; 
                
                // TRƯỜNG HỢP 1: STUDIO (Phiên bản Tinh Tế - Logo là nhân vật chính)
                if (headerLogo) {
                    const rawName = title.includes(':') ? title.split(':')[1].trim() : title;
                    
                    titleEl.innerHTML = `
                        <div class="studio-header-container">
                            <span class="section-label">TỪ STUDIO</span>
                            <div class="studio-logo-wrapper">
                                <img src="${headerLogo}" alt="${rawName}">
                            </div>
                        </div>`;
                }
                // TRƯỜNG HỢP 2: COLLECTION (Cinematic Header - Siêu ngầu)
                else if (headerImage) {
                    // Lấy tên Collection sạch
                    const colName = title.includes(':') ? title.split(':')[1].trim() : title;
                    
                    // Thay thế toàn bộ section header bằng một banner lớn
                    // Chúng ta cần can thiệp vào cha của titleEl để style full width
                    const headerWrapper = titleEl.closest('.section-header');
                    if(headerWrapper) {
                        headerWrapper.classList.add('collection-mode');
                        headerWrapper.style.backgroundImage = `url('${headerImage}')`;
                        
                        titleEl.innerHTML = `
                            <div class="collection-overlay">
                                <span class="collection-subtitle">TRỌN BỘ SƯU TẬP</span>
                                <h2 class="collection-title">${colName}</h2>
                            </div>
                        `;
                    }
                } 
                // TRƯỜNG HỢP 3: Text thường (Đạo diễn, Thể loại...)
                else {
                    titleEl.innerHTML = `<h2 class="section-title"><i class="fas fa-star" style="color:#e50914; margin-right:10px"></i> ${title}</h2>`;
                }
            }
        }

        //----- Render HTML (Giữ nguyên)
        if (renderType === 'trending') {
            container.innerHTML = movies.map((m, i) => renderTrendingCard(m, i)).join('');
        } else {
            container.innerHTML = movies.map(m => renderMovieCard(m)).join('');
        }
        
        // ... (Phần init hover giữ nguyên) ...
        if (typeof initHoverCards === 'function') initHoverCards();
        if (typeof initializeAllCarousels === 'function') initializeAllCarousels();

    } catch (e) {
        console.error(`Lỗi tải carousel ${apiUrl}:`, e);
        if(sectionId) document.getElementById(sectionId).style.display = 'none';
    }
}

//==============================================================
/*---- 3. HÀM RENDER HTML (JS) ----*/
//==============================================================

/**
 * [ĐÃ SỬA LỖI] Hàm render HTML cho Card (Đồng bộ với script.js)
 * Thêm đầy đủ sự kiện onclick cho các nút.
 */
function renderMovieCard(movie) {
    const escapeHtml = (str) => (str || '').replace(/[&<>"']/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[m]);
    
    const poster = movie.poster || '/images/placeholder.jpg';
    const backdrop = movie.backdrop || '/images/placeholder.jpg';
    const title = escapeHtml(movie.title || 'Unknown');
    const rating = movie.rating || '—';
    const year = movie.year || '—';
    const overview = escapeHtml(movie.overview || 'Đang tải mô tả...');
    
    // [MỚI] Dữ liệu đầy đủ
    const runtime = movie.runtime || '—';
    const contentRating = movie.contentRating || 'T';
    const country = movie.country || 'Quốc tế';
    
    // [CODE MỚI] Logic Genre Tooltip
    let genresHtml = '<span class="genre-tag">Không có</span>';
    if (movie.genres && movie.genres.length > 0) {
        genresHtml = movie.genres.slice(0, 2).map(g => `<span class="genre-tag">${escapeHtml(g)}</span>`).join('');
        
        if (movie.genres.length > 2) {
            const remaining = movie.genres.slice(2);
            const tooltipHtml = remaining.map(g => `<div class="genre-bubble">${escapeHtml(g)}</div>`).join('');
            
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

    const playerId = `hover-player-async-${movie.id}`;

    return `
        <div class="movie-card" data-movie-id="${movie.id}">
            <div class="movie-poster">
                <img src="${poster}" alt="${title}" onerror="this.src='/images/placeholder.jpg'" loading="lazy">
            </div>
            <div class="movie-info">
                <h3>${title}</h3>
                <p class="movie-rating">⭐ <span>${rating}</span></p>
            </div>
            <div class="movie-hover-card" data-movie-id="${movie.id}">
                <div class="hover-card-media">
                    <img class="hover-card-image" src="${backdrop}" alt="${title}" onerror="this.src='/images/placeholder.jpg'">
                    <div class="hover-player-container"><div class="hover-player" id="${playerId}"></div></div>
                    <button class="hover-volume-btn" type="button" title="Bật/Tắt tiếng" onclick="event.stopPropagation();"><i class="fas fa-volume-mute"></i></button>
                </div>
                <div class="hover-card-content">
                    <div class="hover-card-actions">
                        <button class="hover-play-btn" type="button" data-movie-id="${movie.id}" onclick="event.stopPropagation(); window.goToMovieDetail(this)"><i class="fas fa-play"></i> Xem ngay</button>
                        <button class="hover-action-icon hover-like-btn" type="button" data-movie-id="${movie.id}" data-tmdb-id="${movie.tmdbId || movie.id}" onclick="event.stopPropagation(); window.toggleHoverLike(this)" title="Thêm vào danh sách"><i class="far fa-heart"></i></button>
                        <button class="hover-action-icon hover-share-btn" type="button" data-movie-id="${movie.id}" data-movie-title="${title}" onclick="event.stopPropagation(); window.showShareModal(this)" title="Chia sẻ"><i class="fas fa-share-alt"></i></button>
                    </div>
                    <h3 class="hover-card-title">${title}</h3>
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
                    <p class="hover-card-description">${overview}</p>
                </div>
            </div>
        </div>
    `;
}

/**
 * [MỚI - VĐ 9] Hàm render HTML cho Trending Sidebar (Cần cho loadAsyncCarousels)
 */
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

//==============================================================
/*---- 4. LOGIC XỬ LÝ UI TÙY CHỈNH (GLOBAL) ----*/
//==============================================================

window.openGlobalTrailer = function(videoId) {
    document.getElementById('globalTrailerModal').classList.add('active');
    document.getElementById('globalTrailerFrame').src = `https://www.youtube.com/embed/${videoId}?autoplay=1&rel=0`;
}
window.closeGlobalTrailer = function() {
    document.getElementById('globalTrailerModal').classList.remove('active');
    document.getElementById('globalTrailerFrame').src = '';
}
window.toggleCast = function() {
    document.getElementById('castGrid').classList.toggle('expanded');
    const btn = document.getElementById('castToggleBtn');
    btn.textContent = btn.textContent === 'Xem tất cả' ? 'Thu gọn' : 'Xem tất cả';
}
window.toggleFPTDescription = function() {
    document.getElementById('descContent').classList.toggle('expanded');
    document.getElementById('descText').classList.toggle('expanded');
    const btn = document.getElementById('descToggleBtn');
    btn.classList.toggle('expanded');
    btn.querySelector('span').textContent = btn.classList.contains('expanded') ? 'Ẩn bớt' : 'Xem thêm';
}