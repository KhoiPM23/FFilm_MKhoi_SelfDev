// =========================================================================
// CẤU HÌNH VÀ BIẾN TOÀN CỤC (G30)
// =========================================================================
let heroPlayer = null;
let videoTimeout = null;
let movieDetailCache = {}; 
let isGenreMapLoaded = true; // [G30] Đơn giản hóa, luôn mặc định là true
let carouselRotateInterval = null; 
let hoverPlayerMap = {}; 
let hoverVideoTimer = null; 
const HOVER_VIDEO_DELAY = 1500; 

const TMDB_API_KEY = 'eac03c4e09a0f5099128e38cb0e67a8f';
const TMDB_BASE_URL = 'https://api.themoviedb.org/3';

// DOM Elements (được truy vấn khi cần)
const heroBanner = document.getElementById('heroBanner');
const videoContainer = document.getElementById('heroVideoContainer');
const volumeBtn = document.getElementById('volumeBtn');
const miniCarouselTrack = document.getElementById('miniCarousel');


// =========================================================================
// LOGIC YOUTUBE PLAYER VÀ TRAILER (G5 - Giữ nguyên)
// =========================================================================

function initHeroVideo() {
    if (videoTimeout) clearTimeout(videoTimeout);
    if (heroPlayer) {
        heroPlayer.destroy();
        heroPlayer = null;
    }
    if (!videoContainer) return; // [G30] Thêm kiểm tra
    
    videoContainer.style.opacity = '0';
    heroBanner.setAttribute('data-video-active', 'false');

    const trailerKey = heroBanner.dataset.trailerKey;
    if (!trailerKey || trailerKey === 'null') {
        return;
    }

    videoTimeout = setTimeout(() => {
        heroPlayer = new YT.Player('heroPlayer', {
            height: '100%', width: '100%', videoId: trailerKey,
            playerVars: {
                'autoplay': 1, 'mute': 1, 'controls': 0, 'start': 5, 'loop': 1,
                'playlist': trailerKey, 'rel': 0, 'iv_load_policy': 3,
                'modestbranding': 1, 'showinfo': 0, 'origin': window.location.origin,
                'wmode': 'opaque', 'disablekb': 1, 'playsinline': 1
            },
            events: {
                'onReady': onPlayerReady,
                'onStateChange': onPlayerStateChange
            }
        });
        videoContainer.style.pointerEvents = 'none'; 
    }, 500); 
}

function displayHeroExtras() {
    if (!heroBanner) return;
    const heroLogo = document.getElementById('heroLogo');
    const heroTitleText = document.getElementById('heroTitleText');
    const contentRatingSpan = document.getElementById('contentRating')?.querySelector('span');

    // 1. Hiển thị Logo
    const logoPath = heroBanner.dataset.logoPath;
    if (logoPath && logoPath !== 'null') {
        if (heroLogo) {
            heroLogo.src = `https://image.tmdb.org/t/p/w500${logoPath}`;
            heroLogo.style.display = 'block';
        }
        if (heroTitleText) heroTitleText.style.display = 'none';
    } else {
        if (heroLogo) heroLogo.style.display = 'none';
        if (heroTitleText) heroTitleText.style.display = 'block';
    }

    // 2. Hiển thị Content Rating
    if (contentRatingSpan) {
        const movieId = heroBanner.dataset.movieId;
        if (!movieId) return;
        fetch(`${TMDB_BASE_URL}/movie/${movieId}/release_dates?api_key=${TMDB_API_KEY}`)
            .then(res => res.json())
            .then(detailData => {
                let finalRating = 'T';
                const usRelease = detailData.results?.find(r => r.iso_3166_1 === 'US'); 
                const certification = usRelease?.release_dates.find(d => d.certification && (d.type === 4 || d.type === 3))?.certification;
                if (certification) {
                    switch(certification.toUpperCase()) {
                        case 'G': case 'TV-G': finalRating = 'T'; break;
                        case 'PG': case 'PG-13': case 'TV-PG': finalRating = 'T13+'; break;
                        case 'R': case 'TV-14': finalRating = 'T16+'; break;
                        case 'NC-17': case 'TV-MA': finalRating = 'T18+'; break;
                        default: finalRating = 'T';
                    }
                }
                contentRatingSpan.textContent = finalRating;
            }).catch(e => {
                contentRatingSpan.textContent = 'T'; // Mặc định
            });
    }
}

function onPlayerReady(event) {
    event.target.playVideo();
    if (videoContainer) videoContainer.style.pointerEvents = 'auto'; 

    setTimeout(() => {
        if (videoContainer) videoContainer.style.opacity = '1';
        if (heroBanner) heroBanner.setAttribute('data-video-active', 'true');
    }, 1000);
    setupVolumeControl();
}

function onPlayerStateChange(event) {
    if (event.data === YT.PlayerState.ENDED) {
        heroPlayer.seekTo(5, true);
    }
}

function setupVolumeControl() {
    if (volumeBtn && heroPlayer) {
        volumeBtn.onclick = () => {
            if (heroPlayer.isMuted()) {
                heroPlayer.unMute();
                volumeBtn.innerHTML = '<i class="fas fa-volume-up"></i>';
            } else {
                heroPlayer.mute();
                volumeBtn.innerHTML = '<i class="fas fa-volume-mute"></i>';
            }
        };
    }
}

window.onYouTubeIframeAPIReady = function() {
    if (document.getElementById('heroBanner')) {
        initHeroVideo(); 
    }
}

// =========================================================================
// LOGIC CHUYỂN BANNER & MINI CAROUSEL (G8 - Giữ nguyên)
// =========================================================================

/* [Dán đè hàm này vào script.js] */

window.switchBanner = function(cardElement) {
    const newId = cardElement.dataset.movieId;
    if (!heroBanner || newId === heroBanner.dataset.movieId) return;
    
    // [SỬA] Lấy data-set cơ bản từ card
    const movieData = cardElement.dataset;
    const heroContentEl = document.querySelector('.hero-content');
    
    // 1. Fade-out nội dung cũ
    if (heroContentEl) {
        heroContentEl.style.transition = 'opacity 0.25s ease-out';
        heroContentEl.style.opacity = '0';
    }
    
    // 2. Hủy video cũ
    if (heroPlayer) {
        heroPlayer.destroy();
        heroPlayer = null;
    }
    if (videoContainer) videoContainer.style.opacity = '0';
    heroBanner.setAttribute('data-video-active', 'false');

    // 3. Delay 250ms để đợi fade-out
    setTimeout(() => {
        // 4. Cập nhật banner data (CƠ BẢN)
        heroBanner.style.backgroundImage = `url(${movieData.backdrop})`;
        heroBanner.dataset.movieId = newId;
        heroBanner.dataset.title = movieData.title;
        // (Xóa 2 dòng trailerKey và logoPath ở đây)

        // 5. Cập nhật DOM (Query 1 lần)
        const heroTitleText = document.getElementById('heroTitleText');
        const ratingSpan = document.querySelector('.hero-meta .rating span');
        const yearDiv = document.querySelector('.hero-meta .year');
        const heroOverview = document.getElementById('heroDesc');
        const heroPlayLink = document.querySelector('.hero-actions .btn-play');
        const heroLikeBtn = document.getElementById('heroLikeBtn');
        const heroShareBtn = document.getElementById('heroShareBtn');
        const heroDuration = document.getElementById('heroDuration');
        const heroCountry = document.getElementById('heroCountry');
        
        // 6. Cập nhật Text
        if (heroTitleText) heroTitleText.textContent = movieData.title;
        if (ratingSpan) ratingSpan.textContent = movieData.rating;
        if (yearDiv) yearDiv.textContent = movieData.year;
        if (heroOverview) heroOverview.textContent = movieData.overview;
        
        // [FIX] CẬP NHẬT DURATION VÀ COUNTRY (ĐÃ HOẠT ĐỘNG)
        if (heroDuration) {
            heroDuration.textContent = (movieData.runtime === '—' || movieData.runtime == 0) ? '—' : movieData.runtime + ' phút';
        }
        if (heroCountry) {
            heroCountry.textContent = movieData.country || 'Quốc gia';
        }

        // 7. Cập nhật các nút
        if (heroPlayLink) heroPlayLink.href = `/movie/detail/${newId}`;
        if (heroLikeBtn) heroLikeBtn.setAttribute('data-movie-id', newId);
        if (heroShareBtn) {
            heroShareBtn.setAttribute('data-movie-id', newId);
            heroShareBtn.setAttribute('data-movie-title', movieData.title);
        }
        
        // [SỬA] GỌI HÀM HELPER MỚI ĐỂ FETCH TRAILER/LOGO
        fetchAndApplyBannerExtras(newId);

        // Reset các trường
        const descToggleBtn = document.getElementById('descToggle');
        if (heroOverview) heroOverview.classList.remove('expanded');
        if (descToggleBtn) descToggleBtn.classList.remove('expanded'); 

        // 8. Hiệu ứng Fade-in
        if (heroContentEl) {
            heroContentEl.style.transition = 'none';
            heroContentEl.style.transform = 'translateX(-60px)';
            heroContentEl.style.opacity = '0';
            heroContentEl.offsetHeight; 
            
            setTimeout(() => {
                heroContentEl.style.transition = 'transform 0.6s cubic-bezier(0.25, 0.46, 0.45, 0.94), opacity 0.6s ease-out';
                heroContentEl.style.transform = 'translateX(0)';
                heroContentEl.style.opacity = '1';
            }, 50);
        }
    }, 250); 

    // 9. Cập nhật mini-carousel
    document.querySelectorAll('.mini-card').forEach(c => c.classList.remove('active'));
    cardElement.classList.add('active');
    centerActiveMiniCard(cardElement);
    
    stopAutoRotate();
    startAutoRotate();
}

/**
 * [HÀM MỚI] Gọi API /banner-detail/ để lấy trailer và logo
 * Dán hàm này vào script.js (bên dưới hàm switchBanner)
 */
async function fetchAndApplyBannerExtras(tmdbId) {
    if (!heroBanner) return;
    
    try {
        // Gọi API đã có sẵn
        const response = await fetch(`/api/movie/banner-detail/${tmdbId}`);
        if (!response.ok) throw new Error('API banner-detail failed');
        
        const data = await response.json();
        
        // Gán data vào banner
        heroBanner.dataset.trailerKey = data.trailerKey || '';
        heroBanner.dataset.logoPath = data.logoPath || '';

        // Kích hoạt 2 hàm hiển thị
        displayHeroExtras(); // Hàm này sẽ lo logo
        
        if (typeof YT !== 'undefined' && YT.Player) {
            initHeroVideo(); // Hàm này sẽ lo trailer
        }

    } catch (error) {
        console.warn('Lỗi fetchAndApplyBannerExtras:', error.message);
        // Nếu lỗi, vẫn chạy 2 hàm để reset (ẩn logo, không chạy trailer)
        heroBanner.dataset.trailerKey = '';
        heroBanner.dataset.logoPath = '';
        displayHeroExtras(); 
        initHeroVideo(); 
    }
}

function centerActiveMiniCard(activeCard) {
    if (!activeCard || !miniCarouselTrack) return;
    const trackRect = miniCarouselTrack.getBoundingClientRect();
    const cardRect = activeCard.getBoundingClientRect();
    const scrollPosition = activeCard.offsetLeft - (trackRect.width / 2) + (cardRect.width / 2);
    miniCarouselTrack.scrollTo({ left: scrollPosition, behavior: 'smooth' });
}

function startAutoRotate() {
    if (carouselRotateInterval) clearInterval(carouselRotateInterval);
    if (!miniCarouselTrack) return; // [G30] Kiểm tra
    const cards = Array.from(miniCarouselTrack.querySelectorAll('.mini-card'));
    if (cards.length < 2) return;

    carouselRotateInterval = setInterval(() => {
        const activeCard = miniCarouselTrack.querySelector('.mini-card.active');
        let nextCardIndex = 0;
        if (activeCard) {
            let currentIndex = cards.indexOf(activeCard);
            nextCardIndex = (currentIndex + 1) % cards.length;
        }
        if (cards[nextCardIndex]) {
            window.switchBanner(cards[nextCardIndex]);
        }
    }, 11000); // 11 giây
}

function stopAutoRotate() {
    if (carouselRotateInterval) {
        clearInterval(carouselRotateInterval);
        carouselRotateInterval = null;
    }
}

if (miniCarouselTrack) {
    miniCarouselTrack.addEventListener('mouseenter', stopAutoRotate);
    miniCarouselTrack.addEventListener('mouseleave', startAutoRotate);
}

// =========================================================================
// LOGIC UI CHUNG (Header, Description, BackToTop) (Giữ nguyên)
// =========================================================================

function setupHeaderScroll() {
    const header = document.querySelector('.main-header');
    const hero = document.querySelector('.hero-banner');
    if (header && hero) {
        const heroHeight = hero.offsetHeight; 
        window.addEventListener('scroll', () => {
            if (window.scrollY > (heroHeight > 100 ? heroHeight - 100 : 100)) { // [G30] Sửa lỗi heroHeight = 0
                header.classList.add('scrolled');
            } else {
                header.classList.remove('scrolled');
            }
        });
    } else if (header) { // [G30] Fallback cho các trang không có banner
         window.addEventListener('scroll', () => {
            if (window.scrollY > 70) {
                header.classList.add('scrolled');
            } else {
                header.classList.remove('scrolled');
            }
        });
    }
}

function setupDescriptionToggle() {
    const descToggleBtn = document.getElementById('descToggle');
    const heroOverview = document.getElementById('heroDesc');
    const heroContentEl = document.querySelector('.hero-content');
    
    if (!descToggleBtn || !heroOverview || !heroContentEl) return;
    
    descToggleBtn.addEventListener('mouseenter', () => {
        heroOverview.classList.add('expanded');
        descToggleBtn.classList.add('expanded');
    });
    
    heroContentEl.addEventListener('mouseleave', () => {
        heroOverview.classList.remove('expanded');
        descToggleBtn.classList.remove('expanded');
    });
}

function setupBackToTopButton() {
    let backToTopBtn = document.getElementById('backToTopBtn');
    if (!backToTopBtn) {
        backToTopBtn = document.createElement('button');
        backToTopBtn.id = 'backToTopBtn';
        backToTopBtn.innerHTML = '<i class="fas fa-chevron-up"></i>';
        backToTopBtn.className = 'back-to-top-btn'; // [G30] Dùng class cho CSS
        document.body.appendChild(backToTopBtn);
        
        // [G30] Thêm CSS vào <head>
        const style = document.createElement('style');
        style.innerHTML = `
            .back-to-top-btn {
                position: fixed; bottom: 30px; right: 110px; z-index: 99999;
                width: 50px; height: 50px; border: none; border-radius: 50%;
                background-color: rgba(229, 9, 20, 0.9); color: white;
                cursor: pointer; opacity: 0; visibility: hidden;
                transition: all 0.3s ease; transform: translateY(20px);
                box-shadow: 0 4px 12px rgba(0,0,0,0.5); font-size: 18px;
            }
        `;
        document.head.appendChild(style);
    }
    
    window.addEventListener('scroll', () => {
        if (window.scrollY > 400) {
            backToTopBtn.style.opacity = '1';
            backToTopBtn.style.visibility = 'visible';
            backToTopBtn.style.transform = 'translateY(0)';
        } else {
            backToTopBtn.style.opacity = '0';
            backToTopBtn.style.visibility = 'hidden';
            backToTopBtn.style.transform = 'translateY(20px)';
        }
    });
    
    backToTopBtn.addEventListener('click', () => {
        window.scrollTo({ top: 0, behavior: 'smooth' });
    });
}

// =========================================================================
// [G30] TỐI ƯU CAROUSEL (TỰ ĐỘNG KHỞI TẠO)
// =========================================================================

/**
 * [G30] HÀM MỚI: Tự động tìm và khởi tạo tất cả carousel trên trang.
 * Thay thế hàm initializeCarousel(sliderId, prevBtnId, nextBtnId) cũ.
 */
function initializeAllCarousels() {
    // Tìm tất cả các section carousel
    const carouselSections = document.querySelectorAll('.movie-list-section, .movies');
    
    carouselSections.forEach((section, index) => {
        const slider = section.querySelector('.movie-slider');
        const prevBtn = section.querySelector('.nav-btn.prev-btn');
        const nextBtn = section.querySelector('.nav-btn.next-btn');
        
        // Chỉ khởi tạo nếu có đủ 3 thành phần
        if (!slider || !prevBtn || !nextBtn) {
            return;
        }

        // Gán ID duy nhất nếu chưa có (quan trọng cho logic resize)
        if (!slider.id) slider.id = `auto-slider-${index}`;
        
        const container = slider.parentElement;
        let currentScroll = 0;
        let cardWidth = 215; // (200px + 15px gap)

        function updateSliderState() {
            if (!container || !slider) return;
            const cards = slider.querySelectorAll('.movie-card');
            
            // [SỬA VĐ 2] XÓA KHỐI IF (cards.length === 0)
            // Logic này làm ẩn nút vĩnh viễn nếu data load sau
            
            // Luôn hiển thị nút
            prevBtn.style.display = 'block';
            nextBtn.style.display = 'block';

            cardWidth = cards[0] ? cards[0].offsetWidth + 15 : 215; // +15 là gap, 215 là fallback
            const containerWidth = container.offsetWidth;
            const maxScroll = Math.max(0, (cards.length * cardWidth) - containerWidth + 15);
            
            slider.style.transform = `translateX(-${currentScroll}px)`;
            
            prevBtn.disabled = currentScroll <= 0;
            nextBtn.disabled = currentScroll >= maxScroll;
            
            prevBtn.classList.toggle('disabled', prevBtn.disabled);
            nextBtn.classList.toggle('disabled', nextBtn.disabled);
        }

        prevBtn.addEventListener('click', function() {
            const containerWidth = container.offsetWidth;
            currentScroll = Math.max(0, currentScroll - containerWidth * 0.8);
            updateSliderState();
        });
        
        nextBtn.addEventListener('click', function() {
            const containerWidth = container.offsetWidth;
            const maxScroll = Math.max(0, (slider.scrollWidth - containerWidth));
            currentScroll = Math.min(maxScroll, currentScroll + containerWidth * 0.8);
            updateSliderState();
        });

        // [G30] Dùng ResizeObserver để theo dõi thay đổi kích thước
        // Chính xác hơn window.addEventListener('resize')
        const resizeObserver = new ResizeObserver(() => {
            updateSliderState();
        });
        resizeObserver.observe(container);

        updateSliderState(); // Gọi lần đầu
    });
}


// =========================================================================
// LOGIC HOVER CARD (G12 - Giữ nguyên)
// =========================================================================

async function enhanceHoverCard(card) {
    const movieId = card.dataset.movieId;
    const hoverCard = card.querySelector('.movie-hover-card');
    if (!movieId || !hoverCard) return;

    if (movieDetailCache[movieId]) {
        updateHoverCardUI(hoverCard, movieDetailCache[movieId]);
        return;
    }

    try {
        const resp = await fetch(`/api/movie/hover-detail/${movieId}`);
        if (!resp.ok) throw new Error('Failed to fetch hover details');
        
        const responseData = await resp.json();
        const detailData = responseData.movie; 
        const trailerKey = responseData.trailerKey; 

        // Lấy Content Rating
        let finalRating = 'T';
        try {
            const ratingResp = await fetch(`${TMDB_BASE_URL}/movie/${movieId}/release_dates?api_key=${TMDB_API_KEY}`);
            const ratingData = await ratingResp.json();
            const usRelease = ratingData.results.find(r => r.iso_3166_1 === 'US');
            const certification = usRelease?.release_dates.find(d => d.certification && (d.type === 4 || d.type === 3))?.certification;
            if (certification) {
                 switch(certification.toUpperCase()) {
                    case 'G': case 'TV-G': finalRating = 'T'; break;
                    case 'PG': case 'PG-13': case 'TV-PG': finalRating = 'T13+'; break;
                    case 'R': case 'TV-14': finalRating = 'T16+'; break;
                    case 'NC-17': case 'TV-MA': finalRating = 'T18+'; break;
                    default: finalRating = 'T';
                }
            }
        } catch(e) { /* Bỏ qua lỗi rating */ }

        // Lưu cache
        const cacheData = {
            runtime: detailData.runtime + ' phút',
            country: detailData.country,
            contentRating: finalRating,
            genres: detailData.genres, 
            trailerKey: trailerKey 
        };
        
        movieDetailCache[movieId] = cacheData;
        updateHoverCardUI(hoverCard, cacheData);

    } catch (error) {
        console.warn('⚠️ Error enhancing hover card (G12):', error);
    }
}

// [G38] THAY THẾ TOÀN BỘ HÀM NÀY
function updateHoverCardUI(hoverCard, data) {
    const ratingEl = hoverCard.querySelector('.meta-extra-rating');
    const runtimeEl = hoverCard.querySelector('.meta-extra-runtime');
    const countryEl = hoverCard.querySelector('.meta-extra-country');
    
    if (ratingEl) { ratingEl.textContent = data.contentRating; ratingEl.classList.remove('loading-meta'); }
    if (runtimeEl) { 
        runtimeEl.textContent = data.runtime; 
        runtimeEl.classList.remove('loading-meta');
        runtimeEl.style.whiteSpace = 'nowrap'; 
    }
    if (countryEl) { countryEl.textContent = data.country; countryEl.classList.remove('loading-meta'); }

    // [G38] SỬA LỖI LOGIC RENDER GENRE
    const genresContainer = hoverCard.querySelector('.hover-card-genres');
    if (!genresContainer) return;
    
    const maxGenresToShow = 2; // [G37] Giữ nguyên = 2 (đúng yêu cầu)
    
    if (data.genres && data.genres.length > 0) {
        // 1. Render 2 thẻ tag đầu tiên
        genresContainer.innerHTML = data.genres.slice(0, maxGenresToShow)
            .map(name => `<span class="genre-tag">${name}</span>`).join('');
        
        // 2. Kiểm tra xem CÓ CÒN THỪA không
        if (data.genres.length > maxGenresToShow) {
            const remainingGenres = data.genres.slice(maxGenresToShow);
            
            // 3. [G38] SỬA LỖI TÊN CLASS:
            // Sửa "genre-tooltip" -> "custom-genre-tooltip"
            // Sửa "<div>" -> "<div class='genre-bubble'>"
            genresContainer.innerHTML += 
                `<span class="genre-tag genre-tag-more" onmouseenter="showGenreTooltip(this)" onmouseleave="hideGenreTooltip(this)">
                    +${remainingGenres.length}
                    <div class="custom-genre-tooltip">${remainingGenres.map(g => `<div class="genre-bubble">${g}</div>`).join('')}</div>
                </span>`;
        }
    } else {
        genresContainer.innerHTML = `<span class="genre-tag">Không có</span>`;
    }
    // [G38] KẾT THÚC SỬA LỖI

    if (data.trailerKey) {
        if (hoverVideoTimer) clearTimeout(hoverVideoTimer);
        hoverVideoTimer = setTimeout(() => {
            playHoverVideo(hoverCard, data.trailerKey);
        }, HOVER_VIDEO_DELAY);
    }
}

// [G17] Hàm mới cho Tooltip
window.showGenreTooltip = function(element) {
    const tooltip = element.querySelector('.custom-genre-tooltip'); // <-- ĐÃ SỬA
    if (tooltip) tooltip.style.display = 'flex'; // [G39] Dùng 'flex' (vì CSS đặt là flex-direction: column)
}
window.hideGenreTooltip = function(element) {
    const tooltip = element.querySelector('.custom-genre-tooltip'); // <-- ĐÃ SỬA
    if (tooltip) tooltip.style.display = 'none';
}


function playHoverVideo(hoverCard, videoId) {
    const playerId = hoverCard.querySelector('.hover-player')?.id;
    if (!playerId) return;

    if (hoverPlayerMap[playerId] && hoverPlayerMap[playerId].player) {
        hoverPlayerMap[playerId].player.destroy();
        clearInterval(hoverPlayerMap[playerId].monitorInterval);
    }

    const playerContainer = hoverCard.querySelector('.hover-player-container');
    if (playerContainer) playerContainer.style.opacity = '0'; 

    const player = new YT.Player(playerId, {
        height: '100%', width: '100%', videoId: videoId,
        playerVars: {
            'autoplay': 1, 'mute': 1, 'controls': 0, 'start': 5, 
            'modestbranding': 1, 'showinfo': 0, 'rel': 0, 
            'iv_load_policy': 3, 'fs': 0, 'disablekb': 1,
            'origin': window.location.origin
        },
        events: {
            'onStateChange': onHoverPlayerStateChange 
        }
    });
    
    hoverPlayerMap[playerId] = { player: player, container: playerContainer, monitorInterval: null };
}

function onHoverPlayerStateChange(event) {
    const player = event.target;
    const iframe = player.getIframe();
    if (!iframe) return;
    const playerId = iframe.id;
    
    if (event.data === YT.PlayerState.PLAYING) {
        const hoverPlayerData = hoverPlayerMap[playerId];
        if (hoverPlayerData) {
            if (!hoverPlayerData.container) {
                 hoverPlayerData.container = iframe.closest('.hover-player-container');
            }
            if (hoverPlayerData.container) {
                hoverPlayerData.container.style.transition = 'opacity 0.4s ease-out';
                setTimeout(() => {
                     hoverPlayerData.container.style.opacity = '1';
                }, 300);
            }
            const duration = player.getDuration();
            const endSeconds = duration - 20; 
            if (hoverPlayerData.monitorInterval) {
                clearInterval(hoverPlayerData.monitorInterval);
            }
            hoverPlayerData.monitorInterval = setInterval(() => {
                if (player && typeof player.getPlayerState === 'function' && player.getPlayerState() === YT.PlayerState.PLAYING) {
                    if (player.getCurrentTime() >= endSeconds) {
                        player.seekTo(5); 
                    }
                } else {
                     clearInterval(hoverPlayerData.monitorInterval);
                }
            }, 1000);
        }
    }
}

function stopHoverVideo(card) {
    if (hoverVideoTimer) {
        clearTimeout(hoverVideoTimer);
        hoverVideoTimer = null;
    }
    
    const playerId = card.querySelector('.hover-player')?.id;
    if (playerId && hoverPlayerMap[playerId]) {
        const data = hoverPlayerMap[playerId];
        if (data.monitorInterval) {
            clearInterval(data.monitorInterval);
        }
        if (data.player && typeof data.player.destroy === 'function') {
            data.player.destroy();
        }
        delete hoverPlayerMap[playerId];
    }
    
    const playerContainer = card.querySelector('.hover-player-container');
    if (playerContainer) {
        playerContainer.style.transition = 'none'; 
        playerContainer.style.opacity = '0';
    }
}

/**
 * [MỚI - FIX VĐ 3] Xử lý nút volume trên hover card
 */
function toggleHoverVolume(hoverCard, volumeBtn) {
    const playerId = hoverCard.querySelector('.hover-player')?.id;
    if (!playerId || !hoverPlayerMap[playerId] || !hoverPlayerMap[playerId].player) return;

    const player = hoverPlayerMap[playerId].player;
    const icon = volumeBtn.querySelector('i');

    if (player.isMuted()) {
        player.unMute();
        icon.className = 'fas fa-volume-up';
    } else {
        player.mute();
        icon.className = 'fas fa-volume-mute';
    }
}

// (Các hàm UI (Like, Share) giữ nguyên)
window.toggleHoverLike = function(button) {
    button.classList.toggle('active');
    const icon = button.querySelector('i');
    if (button.classList.contains('active')) {
        icon.classList.remove('far'); icon.classList.add('fas'); icon.style.color = '#E50914';
    } else {
        icon.classList.remove('fas'); icon.classList.add('far'); icon.style.color = '';
    }
}
window.goToMovieDetail = function(button) {
    const movieId = button.dataset.movieId;
    if (movieId) location.href = '/movie/detail/' + movieId;
}
// (MỚI) Các hàm cho Share Modal
window.showShareModal = function(button) {
    // Đọc dữ liệu từ data-* attributes của nút
    const movieId = button.dataset.movieId;
    const movieTitle = button.dataset.movieTitle;
    
    const url = `${window.location.origin}/movie/detail/${movieId}`;
    
    const overlay = document.getElementById('shareModalOverlay');
    const input = document.getElementById('shareLinkInput');
    const copyBtn = document.getElementById('shareCopyBtn');
    
    if (input) input.value = url;
    if (copyBtn) {
        copyBtn.textContent = 'Sao chép';
        copyBtn.classList.remove('copied');
    }
    
    // Cập nhật link cho social
    document.getElementById('shareFacebook').href = `https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(url)}`;
    document.getElementById('shareX').href = `https://twitter.com/intent/tweet?url=${encodeURIComponent(url)}&text=${encodeURIComponent(movieTitle)}`;
    document.getElementById('shareEmail').href = `mailto:?subject=${encodeURIComponent(movieTitle)}&body=Xem phim này nhé: ${encodeURIComponent(url)}`;

    if (overlay) overlay.classList.add('active');
}

window.closeShareModal = function() {
    const overlay = document.getElementById('shareModalOverlay');
    if (overlay) overlay.classList.remove('active');
}

window.copyShareLink = function() {
    const input = document.getElementById('shareLinkInput');
    const copyBtn = document.getElementById('shareCopyBtn');
    
    input.select();
    input.setSelectionRange(0, 99999); // For mobile
    
    try {
        navigator.clipboard.writeText(input.value).then(() => {
            if (copyBtn) {
                copyBtn.textContent = 'Đã chép!';
                copyBtn.classList.add('copied');
            }
        });
    } catch (err) {
        console.error('Không thể sao chép:', err);
    }
}

// (Các hàm xử lý hover (mouseenter/mouseleave) giữ nguyên)
let hoverTimeout;
function handleCardHover(event) {
    const card = event.currentTarget;
    const hoverCard = card.querySelector('.movie-hover-card');
    if (!hoverCard) return;

    // Logic né cạnh (Edge detection)
    const cardRect = card.getBoundingClientRect();
    const viewportWidth = window.innerWidth;
    const hoverCardWidth = 340; // Từ CSS
    const spaceRight = viewportWidth - cardRect.right;
    const spaceLeft = cardRect.left;
    let originX = 'center'; 
    if (spaceRight < (hoverCardWidth / 2) && spaceLeft > (hoverCardWidth / 2)) {
        originX = 'calc(100% - 30px)'; // Né sang trái
    }
    else if (spaceLeft < (hoverCardWidth / 2) && spaceRight > (hoverCardWidth / 2)) {
        originX = '30px'; // Né sang phải
    }
    hoverCard.style.transformOrigin = `${originX} center`;

    clearTimeout(hoverTimeout);
    hoverTimeout = setTimeout(() => {
        enhanceHoverCard(card);
    }, 800); 
}

function handleCardMouseLeave(event) {
    const card = event.currentTarget;
    const hoverCard = card.querySelector('.movie-hover-card');
    
    clearTimeout(hoverTimeout);
    
    setTimeout(() => {
        if (hoverCard && !hoverCard.matches(':hover')) {
            stopHoverVideo(card);
        }
    }, 100); 

    if (hoverCard) {
        setTimeout(() => {
            hoverCard.style.transformOrigin = 'center center';
        }, 300);
    }
}

/**
 * [G30] TỐI ƯU: Hàm này giờ sẽ tự động tìm tất cả card trên trang.
 * Sẽ được gọi 1 lần duy nhất trong DOMContentLoaded.
 */
function initHoverCards() {
    // Dùng querySelectorAll để tìm tất cả các card CÓ hover-card bên trong
    const movieCards = document.querySelectorAll('.movie-card[data-movie-id]');
    
    movieCards.forEach(card => {
        // Kiểm tra xem đã gán sự kiện chưa
        if (card.dataset.hoverBound === 'true') return;

        const hoverCard = card.querySelector('.movie-hover-card');
        if (hoverCard) {
            card.addEventListener('mouseenter', handleCardHover);
            card.addEventListener('mouseleave', handleCardMouseLeave);
            
            hoverCard.addEventListener('mouseleave', () => {
                stopHoverVideo(card);
            });
            
            // [FIX VĐ 3] Gán sự kiện cho nút Volume
            const volumeBtn = hoverCard.querySelector('.hover-volume-btn');
            if (volumeBtn) {
                volumeBtn.addEventListener('click', (e) => {
                    e.stopPropagation(); // Ngăn card bị click
                    toggleHoverVolume(hoverCard, volumeBtn);
                });
            }
            
            card.dataset.hoverBound = 'true';
        }
    });
}

function setupLazyLoading() {
    const sections = document.querySelectorAll('.movie-list-section, .movies'); // [G30] Thêm class .movies
    const observer = new IntersectionObserver((entries, observer) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('loaded');
                observer.unobserve(entry.target);
            }
        });
    }, {
        rootMargin: '0px 0px -100px 0px',
        threshold: 0.05
    });
    sections.forEach(section => {
        observer.observe(section);
    });
}

// =========================================================================
// [G30] TỐI ƯU PAGINATION (CHUYỂN VÀO SCRIPT.JS)
// =========================================================================

/**
 * [G30] HÀM MỚI: Tự động khởi tạo pagination nếu tìm thấy
 */
function initializePagination() {
    const paginationEl = document.getElementById('pagination');
    if (!paginationEl) return; // Không phải trang search/discover

    const currentPage = parseInt(paginationEl.dataset.currentPage) || 1;
    const totalPages = parseInt(paginationEl.dataset.totalPages) || 1;
    
    if (totalPages <= 1) return; // Không cần render nếu chỉ có 1 trang

    let html = '';
    
    // Nút Previous
    html += renderPageButton(currentPage - 1, '<i class="fas fa-chevron-left"></i>', currentPage > 1);
    
    // Các nút số
    const start = Math.max(1, currentPage - 2);
    const end = Math.min(totalPages, currentPage + 2);
    
    if (start > 1) {
        html += renderPageButton(1, '1', true);
        if (start > 2) html += '<span class="page-ellipsis">...</span>';
    }
    
    for (let i = start; i <= end; i++) {
        html += renderPageButton(i, i.toString(), true, i === currentPage);
    }
    
    if (end < totalPages) {
        if (end < totalPages - 1) html += '<span class="page-ellipsis">...</span>';
        html += renderPageButton(totalPages, totalPages.toString(), true);
    }
    
    // Nút Next
    html += renderPageButton(currentPage + 1, '<i class="fas fa-chevron-right"></i>', currentPage < totalPages);
    
    paginationEl.innerHTML = html;

    // [G30] Thêm CSS cho ellipsis (nếu chưa có)
    if (!document.getElementById('pagination-style')) {
        const style = document.createElement('style');
        style.id = 'pagination-style';
        style.innerHTML = `.page-ellipsis { padding: 0 10px; color: #666; align-self: center; }`;
        document.head.appendChild(style);
    }
}

/**
 * [G30] HÀM HELPER: Tạo 1 nút pagination
 */
function renderPageButton(page, text, enabled, isActive = false) {
    const url = buildPageUrl(page);
    const activeClass = isActive ? 'active' : '';
    const disabledClass = !enabled ? 'disabled' : '';
    
    if (!enabled) {
        return `<button class="page-btn ${disabledClass}" disabled>${text}</button>`;
    }
    
    // [G30] Sửa: Dùng thẻ <a> thay vì <button onclick> để tốt cho SEO
    return `<a href="${url}" class="page-btn ${activeClass}">${text}</a>`;
}

/**
 * [G30] HÀM HELPER: Xây dựng URL cho trang mới (tự động đọc query params)
 */
function buildPageUrl(page) {
    const urlParams = new URLSearchParams(window.location.search);
    urlParams.set('page', page.toString());
    return `${window.location.pathname}?${urlParams.toString()}`;
}


// =========================================================================
// KHỞI TẠO CHUNG (G30 - ĐÃ TỐI ƯU)
// =========================================================================

document.addEventListener('DOMContentLoaded', () => {
    // 1. Khởi tạo các thành phần UI chung
    setupHeaderScroll();
    setupDescriptionToggle(); // (Chỉ chạy nếu tìm thấy element)
    setupBackToTopButton();
    setupLazyLoading(); // (Tự tìm .movie-list-section)
    
    // 2. Khởi tạo Banner (Nếu có)
    if (document.getElementById('heroBanner')) {
        startAutoRotate(); // (Chỉ chạy nếu tìm thấy miniCarouselTrack)
        displayHeroExtras(); 
        
        // Gọi initHeroVideo nếu API đã sẵn sàng
        if (typeof YT !== 'undefined' && YT.Player) {
            initHeroVideo();
        }
    }

    // 3. [G30] TỐI ƯU: Gọi 1 lần duy nhất, tự động tìm
    initializeAllCarousels();
    initHoverCards(); 
    initializePagination(); 
});