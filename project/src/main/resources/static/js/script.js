/**
 * =========================================================================================
 * FFilm Main UI Script (Module Pattern - IIFE)
 * Tối ưu hóa hiệu năng, quản lý trạng thái và xử lý tương tác giao diện người dùng.
 * =========================================================================================
 */
(function () {
  "use strict";

  // =========================================================================
  // 1. CẤU HÌNH VÀ BIẾN TOÀN CỤC (GLOBAL CONFIG AND STATE)
  // =========================================================================

  // API & Keys
  const TMDB_API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
  const TMDB_BASE_URL = "https://api.themoviedb.org/3";

  // Core State Variables
  let heroPlayer = null;
  let videoTimeout = null;
  let movieDetailCache = {};
  let carouselRotateInterval = null;
  let hoverPlayerMap = {};
  let hoverVideoTimer = null;
  let hoverTimeout = null; // Biến timeout cho hover card

  // Constants
  const HOVER_VIDEO_DELAY = 1500; // Delay video hover (1.5s)
  const isGenreMapLoaded = true; // [G30] Đơn giản hóa, luôn mặc định là true

  // DOM Elements (được truy vấn khi cần hoặc khởi tạo sớm)
  const heroBanner = document.getElementById("heroBanner");
  const videoContainer = document.getElementById("heroVideoContainer");
  const volumeBtn = document.getElementById("volumeBtn");
  const miniCarouselTrack = document.getElementById("miniCarousel");

  // =========================================================================
  // 2. LOGIC YOUTUBE PLAYER VÀ TRAILER (YOUTUBE PLAYER & TRAILER LOGIC)
  // =========================================================================

  /**
   * Khởi tạo YouTube Player cho Hero Banner.
   */
  function initHeroVideo() {
    if (videoTimeout) clearTimeout(videoTimeout);
    if (heroPlayer) {
      heroPlayer.destroy();
      heroPlayer = null;
    }
    if (!videoContainer) return; // [G30] Thêm kiểm tra

    videoContainer.style.opacity = "0";
    heroBanner.setAttribute("data-video-active", "false");

    const trailerKey = heroBanner.dataset.trailerKey;
    if (!trailerKey || trailerKey === "null") {
      return;
    }

    videoTimeout = setTimeout(() => {
      heroPlayer = new YT.Player("heroPlayer", {
        height: "100%",
        width: "100%",
        videoId: trailerKey,
        playerVars: {
          autoplay: 1,
          mute: 1,
          controls: 0,
          start: 5,
          loop: 1,
          playlist: trailerKey,
          rel: 0,
          iv_load_policy: 3,
          modestbranding: 1,
          showinfo: 0,
          origin: window.location.origin,
          wmode: "opaque",
          disablekb: 1,
          playsinline: 1,
        },
        events: {
          onReady: onPlayerReady,
          onStateChange: onPlayerStateChange,
        },
      });
      videoContainer.style.pointerEvents = "none";
    }, 500);
  }

  /**
   * Hiển thị Logo của Banner (Offline Mode).
   * Content Rating đã được xử lý bởi switchBanner hoặc Thymeleaf ban đầu.
   */
  function displayHeroExtras() {
    if (!heroBanner) return;
    const heroLogo = document.getElementById("heroLogo");
    const heroTitleText = document.getElementById("heroTitleText");

    // Hiển thị Logo nếu có path trong DB
    const logoPath = heroBanner.dataset.logoPath;
    if (logoPath && logoPath !== "null" && logoPath !== "") {
      if (heroLogo) {
        heroLogo.src = `https://image.tmdb.org/t/p/w500${logoPath}`;
        heroLogo.style.display = "block";
      }
      if (heroTitleText) heroTitleText.style.display = "none";
    } else {
      if (heroLogo) heroLogo.style.display = "none";
      if (heroTitleText) heroTitleText.style.display = "block";
    }
  }

  /**
   * Xử lý khi YouTube Player đã sẵn sàng.
   * @param {object} event - Sự kiện YT Player Ready.
   */
  function onPlayerReady(event) {
    event.target.playVideo();
    if (videoContainer) videoContainer.style.pointerEvents = "auto";

    setTimeout(() => {
      if (videoContainer) videoContainer.style.opacity = "1";
      if (heroBanner) heroBanner.setAttribute("data-video-active", "true");
    }, 1000);
    setupVolumeControl();
  }

  /**
   * Xử lý thay đổi trạng thái của YouTube Player (ví dụ: lặp lại khi ENDED).
   * @param {object} event - Sự kiện YT Player State Change.
   */
  function onPlayerStateChange(event) {
    if (event.data === YT.PlayerState.ENDED) {
      heroPlayer.seekTo(5, true);
    }
  }

  /**
   * Thiết lập điều khiển âm lượng cho Hero Player.
   */
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

  /**
   * Callback bắt buộc khi YouTube Iframe API được tải.
   */
  window.onYouTubeIframeAPIReady = function () {
    if (document.getElementById("heroBanner")) {
      initHeroVideo();
    }
  };

  // =========================================================================
  // 3. LOGIC CHUYỂN BANNER & MINI CAROUSEL (BANNER ROTATION LOGIC)
  // =========================================================================

  /**
   * Chuyển đổi Hero Banner sang nội dung của một Mini Card.
   * @param {HTMLElement} cardElement - Thẻ Mini Card được click.
   */
  window.switchBanner = function (cardElement) {
    const newId = cardElement.dataset.movieId;
    if (!heroBanner || newId === heroBanner.dataset.movieId) return;

    const movieData = cardElement.dataset;
    const heroContentEl = document.querySelector(".hero-content");

    // 1. Fade-out nội dung cũ
    if (heroContentEl) {
      heroContentEl.style.transition = "opacity 0.25s ease-out";
      heroContentEl.style.opacity = "0";
    }

    // 2. Hủy video cũ
    if (heroPlayer) {
      heroPlayer.destroy();
      heroPlayer = null;
    }
    if (videoContainer) videoContainer.style.opacity = "0";
    heroBanner.setAttribute("data-video-active", "false");

    // 3. Delay 250ms
    setTimeout(() => {
      // 4. Cập nhật banner data (CƠ BẢN)
      heroBanner.style.backgroundImage = `url(${movieData.backdrop})`;
      heroBanner.dataset.movieId = newId;
      heroBanner.dataset.title = movieData.title;

      // 5. Cập nhật DOM (Query 1 lần)
      const heroTitleText = document.getElementById("heroTitleText");
      const ratingSpan = document.querySelector(".hero-meta .rating span");
      const yearDiv = document.querySelector(".hero-meta .year");
      const heroOverview = document.getElementById("heroDesc");
      const heroPlayLink = document.querySelector(".hero-actions .btn-play");
      const heroLikeBtn = document.getElementById("heroLikeBtn");
      const heroShareBtn = document.getElementById("heroShareBtn");
      const heroDuration = document.getElementById("heroDuration");
      const heroCountry = document.getElementById("heroCountry");

      // 6. Cập nhật Text
      if (heroTitleText) heroTitleText.textContent = movieData.title;
      if (ratingSpan) ratingSpan.textContent = movieData.rating;
      if (yearDiv) yearDiv.textContent = movieData.year;
      if (heroOverview) heroOverview.textContent = movieData.overview;

      // [MỚI] Cập nhật Content Rating từ DB (Offline)
      const contentRatingSpan = document
        .getElementById("contentRating")
        ?.querySelector("span");
      if (contentRatingSpan) {
        contentRatingSpan.textContent = movieData.contentRating || "T";
      }

      // CẬP NHẬT DURATION VÀ COUNTRY
      if (heroDuration) {
        heroDuration.textContent =
          movieData.runtime === "—" || movieData.runtime == 0
            ? "—"
            : movieData.runtime + " phút";
      }
      if (heroCountry) {
        heroCountry.textContent = movieData.country || "Quốc gia";
      }

      // 7. Cập nhật các nút
      if (heroPlayLink) heroPlayLink.href = `/movie/detail/${newId}`;
      if (heroLikeBtn) heroLikeBtn.setAttribute("data-movie-id", newId);
      if (heroShareBtn) {
        heroShareBtn.setAttribute("data-movie-id", newId);
        heroShareBtn.setAttribute("data-movie-title", movieData.title);
      }

      // GỌI HÀM HELPER FETCH TRAILER/LOGO
      fetchAndApplyBannerExtras(newId);

      // Reset các trường UI
      const descToggleBtn = document.getElementById("descToggle");
      if (heroOverview) heroOverview.classList.remove("expanded");
      if (descToggleBtn) descToggleBtn.classList.remove("expanded");

      // 8. Hiệu ứng Fade-in
      if (heroContentEl) {
        heroContentEl.style.transition = "none";
        heroContentEl.style.transform = "translateX(-60px)";
        heroContentEl.style.opacity = "0";
        heroContentEl.offsetHeight;

        setTimeout(() => {
          heroContentEl.style.transition =
            "transform 0.6s cubic-bezier(0.25, 0.46, 0.45, 0.94), opacity 0.6s ease-out";
          heroContentEl.style.transform = "translateX(0)";
          heroContentEl.style.opacity = "1";
        }, 50);
      }
    }, 250);

    // 9. Cập nhật mini-carousel
    document
      .querySelectorAll(".mini-card")
      .forEach((c) => c.classList.remove("active"));
    cardElement.classList.add("active");
    centerActiveMiniCard(cardElement);

    // Reset auto rotate
    stopAutoRotate();
    startAutoRotate();
  };

  /**
   * Gọi API backend để lấy trailer key và logo path mới nhất.
   * @param {string} movieId - DB Movie ID (PK).
   */
  async function fetchAndApplyBannerExtras(movieId) {
    if (!heroBanner) return;

    try {
      const response = await fetch(`/api/movie/banner-detail/${movieId}`);
      if (!response.ok) throw new Error("API banner-detail failed");

      const data = await response.json();

      // Gán data vào banner
      heroBanner.dataset.trailerKey = data.trailerKey || "";
      heroBanner.dataset.logoPath = data.logoPath || "";

      // Kích hoạt 2 hàm hiển thị
      displayHeroExtras();

      if (typeof YT !== "undefined" && YT.Player) {
        initHeroVideo();
      }
    } catch (error) {
      console.warn("Lỗi fetchAndApplyBannerExtras:", error.message);
      // Fallback: reset data
      heroBanner.dataset.trailerKey = "";
      heroBanner.dataset.logoPath = "";
      displayHeroExtras();
      initHeroVideo();
    }
  }

  /**
   * Căn giữa thẻ mini card đang hoạt động trong carousel.
   * @param {HTMLElement} activeCard - Thẻ mini card đang hoạt động.
   */
  function centerActiveMiniCard(activeCard) {
    if (!activeCard || !miniCarouselTrack) return;
    const trackRect = miniCarouselTrack.getBoundingClientRect();
    const cardRect = activeCard.getBoundingClientRect();
    const scrollPosition =
      activeCard.offsetLeft - trackRect.width / 2 + cardRect.width / 2;
    miniCarouselTrack.scrollTo({ left: scrollPosition, behavior: "smooth" });
  }

  /**
   * Bắt đầu quay carousel tự động.
   */
  function startAutoRotate() {
    if (carouselRotateInterval) clearInterval(carouselRotateInterval);
    if (!miniCarouselTrack) return;
    const cards = Array.from(miniCarouselTrack.querySelectorAll(".mini-card"));
    if (cards.length < 2) return;

    carouselRotateInterval = setInterval(() => {
      const activeCard = miniCarouselTrack.querySelector(".mini-card.active");
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

  /**
   * Dừng quay carousel tự động.
   */
  function stopAutoRotate() {
    if (carouselRotateInterval) {
      clearInterval(carouselRotateInterval);
      carouselRotateInterval = null;
    }
  }

  // Gán sự kiện cho mini carousel
  if (miniCarouselTrack) {
    miniCarouselTrack.addEventListener("mouseenter", stopAutoRotate);
    miniCarouselTrack.addEventListener("mouseleave", startAutoRotate);
  }

  // =========================================================================
  // 4. LOGIC UI CHUNG (COMMON UI LOGIC)
  // =========================================================================

  /**
   * Xử lý thay đổi màu nền của Header khi cuộn trang.
   */
  function setupHeaderScroll() {
    const header = document.querySelector(".main-header");
    const hero = document.querySelector(".hero-banner");
    if (header && hero) {
      const heroHeight = hero.offsetHeight;
      window.addEventListener("scroll", () => {
        if (window.scrollY > (heroHeight > 100 ? heroHeight - 100 : 100)) {
          header.classList.add("scrolled");
        } else {
          header.classList.remove("scrolled");
        }
      });
    } else if (header) {
      // Fallback cho các trang không có banner
      window.addEventListener("scroll", () => {
        if (window.scrollY > 70) {
          header.classList.add("scrolled");
        } else {
          header.classList.remove("scrolled");
        }
      });
    }
  }

  /**
   * Xử lý hiệu ứng mở rộng/thu gọn mô tả phim khi di chuột.
   */
  function setupDescriptionToggle() {
    const descToggleBtn = document.getElementById("descToggle");
    const heroOverview = document.getElementById("heroDesc");
    const heroContentEl = document.querySelector(".hero-content");

    if (!descToggleBtn || !heroOverview || !heroContentEl) return;

    descToggleBtn.addEventListener("mouseenter", () => {
      heroOverview.classList.add("expanded");
      descToggleBtn.classList.add("expanded");
    });

    heroContentEl.addEventListener("mouseleave", () => {
      heroOverview.classList.remove("expanded");
      descToggleBtn.classList.remove("expanded");
    });
  }

  /**
   * Tạo và xử lý nút "Cuộn lên đầu trang".
   */
  function setupBackToTopButton() {
    let backToTopBtn = document.getElementById("backToTopBtn");
    if (!backToTopBtn) {
      backToTopBtn = document.createElement("button");
      backToTopBtn.id = "backToTopBtn";
      backToTopBtn.innerHTML = '<i class="fas fa-chevron-up"></i>';
      backToTopBtn.className = "back-to-top-btn";
      document.body.appendChild(backToTopBtn);

      // [G30] Thêm CSS (Vì không có file CSS riêng)
      const style = document.createElement("style");
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

    window.addEventListener("scroll", () => {
      if (window.scrollY > 400) {
        backToTopBtn.style.opacity = "1";
        backToTopBtn.style.visibility = "visible";
        backToTopBtn.style.transform = "translateY(0)";
      } else {
        backToTopBtn.style.opacity = "0";
        backToTopBtn.style.visibility = "hidden";
        backToTopBtn.style.transform = "translateY(20px)";
      }
    });

    backToTopBtn.addEventListener("click", () => {
      window.scrollTo({ top: 0, behavior: "smooth" });
    });
  }

  /**
   * Thiết lập Lazy Loading cho các section phim.
   */
  function setupLazyLoading() {
    const sections = document.querySelectorAll(".movie-list-section, .movies");
    const observer = new IntersectionObserver(
      (entries, observer) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("loaded");
            observer.unobserve(entry.target);
          }
        });
      },
      {
        rootMargin: "0px 0px -100px 0px",
        threshold: 0.05,
      }
    );
    sections.forEach((section) => {
      observer.observe(section);
    });
  }

  // =========================================================================
  // 5. LOGIC CAROUSEL CHUNG (CAROUSEL AUTODETECTION)
  // =========================================================================

  /**
   * Tự động tìm và khởi tạo tất cả carousel trên trang.
   */
  function initializeAllCarousels() {
    const carouselSections = document.querySelectorAll(
      ".movie-list-section, .movies"
    );

    carouselSections.forEach((section, index) => {
      const slider = section.querySelector(".movie-slider");
      const prevBtn = section.querySelector(".nav-btn.prev-btn");
      const nextBtn = section.querySelector(".nav-btn.next-btn");

      if (!slider || !prevBtn || !nextBtn) {
        return;
      }

      if (!slider.id) slider.id = `auto-slider-${index}`;

      const container = slider.parentElement;
      let currentScroll = 0;
      let cardWidth = 215;

      function updateSliderState() {
        if (!container || !slider) return;
        const cards = slider.querySelectorAll(".movie-card");

        if (cards.length === 0) {
          prevBtn.style.display = "none";
          nextBtn.style.display = "none";
          return;
        }
        prevBtn.style.display = "block";
        nextBtn.style.display = "block";

        cardWidth = cards[0] ? cards[0].offsetWidth + 15 : 215;
        const containerWidth = container.offsetWidth;
        const maxScroll = Math.max(
          0,
          cards.length * cardWidth - containerWidth + 15
        );

        slider.style.transform = `translateX(-${currentScroll}px)`;

        prevBtn.disabled = currentScroll <= 0;
        nextBtn.disabled = currentScroll >= maxScroll;

        prevBtn.classList.toggle("disabled", prevBtn.disabled);
        nextBtn.classList.toggle("disabled", nextBtn.disabled);
      }

      prevBtn.addEventListener("click", function () {
        const containerWidth = container.offsetWidth;
        currentScroll = Math.max(0, currentScroll - containerWidth * 0.8);
        updateSliderState();
      });

      nextBtn.addEventListener("click", function () {
        const containerWidth = container.offsetWidth;
        const maxScroll = Math.max(0, slider.scrollWidth - containerWidth);
        currentScroll = Math.min(
          maxScroll,
          currentScroll + containerWidth * 0.8
        );
        updateSliderState();
      });

      // Dùng ResizeObserver để theo dõi thay đổi kích thước
      const resizeObserver = new ResizeObserver(() => {
        updateSliderState();
      });
      resizeObserver.observe(container);

      updateSliderState(); // Gọi lần đầu
    });
  }

  // =========================================================================
  // 6. LOGIC HOVER CARD VÀ LAZY LOAD (HOVER CARD LOGIC)
  // =========================================================================

  /**
   * Tải dữ liệu chi tiết và cập nhật Hover Card.
   * @param {HTMLElement} card - Thẻ Movie Card đang được hover.
   */
  async function enhanceHoverCard(card) {
    const movieId = card.dataset.movieId;
    const hoverCard = card.querySelector(".movie-hover-card");
    if (!movieId || !hoverCard) return;

    if (movieDetailCache[movieId]) {
      updateHoverCardUI(hoverCard, movieDetailCache[movieId]);
      return;
    }

    try {
      const resp = await fetch(`/api/movie/hover-detail/${movieId}`);
      if (!resp.ok) throw new Error("Failed to fetch hover details");

      const responseData = await resp.json();
      const detailData = responseData.movie;
      const trailerKey = responseData.trailerKey;

      // [MỚI] Lấy Content Rating trực tiếp từ API nội bộ (DB)
      // MovieService.convertToMap đã include field 'contentRating'
      const finalRating = detailData.contentRating || "T";

      // Lưu cache
      const cacheData = {
        runtime: detailData.runtime + " phút",
        country: detailData.country,
        contentRating: finalRating,
        genres: detailData.genres,
        trailerKey: trailerKey,
      };

      movieDetailCache[movieId] = cacheData;
      updateHoverCardUI(hoverCard, cacheData);
    } catch (error) {
      console.warn("⚠️ Error enhancing hover card (G12):", error);
    }
  }

  /**
   * Cập nhật giao diện Hover Card với dữ liệu đã tải.
   * @param {HTMLElement} hoverCard - Phần tử Hover Card.
   * @param {object} data - Dữ liệu đã tải (bao gồm genres dạng List Map).
   */
  function updateHoverCardUI(hoverCard, data) {
    const ratingEl = hoverCard.querySelector(".meta-extra-rating");
    const runtimeEl = hoverCard.querySelector(".meta-extra-runtime");
    const countryEl = hoverCard.querySelector(".meta-extra-country");

    if (ratingEl) {
      ratingEl.textContent = data.contentRating || "T";
      ratingEl.classList.remove("loading-meta");
    }
    if (runtimeEl) {
      runtimeEl.textContent = data.runtime || "—";
      runtimeEl.classList.remove("loading-meta");
      runtimeEl.style.whiteSpace = "nowrap";
    }
    if (countryEl) {
      countryEl.textContent = data.country || "Quốc tế";
      countryEl.classList.remove("loading-meta");
    }

    // --- [FIX LOGIC GENRE] ---
    const genresContainer = hoverCard.querySelector(".hover-card-genres");
    if (genresContainer) {
        // Helper an toàn để lấy tên thể loại (dù là String hay Object)
        const getGenreName = (g) => {
            if (!g) return "";
            return (typeof g === 'object' && g.name) ? g.name : g;
        };

        if (data.genres && Array.isArray(data.genres) && data.genres.length > 0) {
            const maxGenresToShow = 2;
            
            // 1. Render 2 thẻ đầu tiên
            let html = data.genres.slice(0, maxGenresToShow)
                .map(g => `<span class="genre-tag">${getGenreName(g)}</span>`)
                .join('');

            // 2. Xử lý phần còn thừa (+N)
            if (data.genres.length > maxGenresToShow) {
                const remaining = data.genres.slice(maxGenresToShow);
                const tooltipHtml = remaining
                    .map(g => `<div class="genre-bubble">${getGenreName(g)}</div>`)
                    .join('');
                
                html += `
                    <span class="genre-tag genre-tag-more" 
                          onmouseenter="window.showGenreTooltip && window.showGenreTooltip(this)" 
                          onmouseleave="window.hideGenreTooltip && window.hideGenreTooltip(this)">
                        +${remaining.length}
                        <div class="custom-genre-tooltip">${tooltipHtml}</div>
                    </span>
                `;
            }
            genresContainer.innerHTML = html;
        } else {
            genresContainer.innerHTML = `<span class="genre-tag">Không có</span>`;
        }
    }

    // Bắt đầu phát video (nếu có trailer)
    if (data.trailerKey) {
      if (hoverVideoTimer) clearTimeout(hoverVideoTimer);
      hoverVideoTimer = setTimeout(() => {
        playHoverVideo(hoverCard, data.trailerKey);
      }, HOVER_VIDEO_DELAY);
    }
  }

  /**
   * Hiển thị Tooltip Genre (Được gọi từ onmouseenter).
   * @param {HTMLElement} element - Phần tử .genre-tag-more.
   */
  window.showGenreTooltip = function (element) {
    const tooltip = element.querySelector(".custom-genre-tooltip");
    if (tooltip) tooltip.style.display = "flex";
  };

  /**
   * Ẩn Tooltip Genre (Được gọi từ onmouseleave).
   * @param {HTMLElement} element - Phần tử .genre-tag-more.
   */
  window.hideGenreTooltip = function (element) {
    const tooltip = element.querySelector(".custom-genre-tooltip");
    if (tooltip) tooltip.style.display = "none";
  };

  /**
   * Khởi tạo và phát video trailer trên Hover Card.
   * @param {HTMLElement} hoverCard - Phần tử Hover Card.
   * @param {string} videoId - YouTube Video ID.
   */
  function playHoverVideo(hoverCard, videoId) {
    const playerId = hoverCard.querySelector(".hover-player")?.id;
    if (!playerId) return;

    if (hoverPlayerMap[playerId] && hoverPlayerMap[playerId].player) {
      hoverPlayerMap[playerId].player.destroy();
      clearInterval(hoverPlayerMap[playerId].monitorInterval);
    }

    const playerContainer = hoverCard.querySelector(".hover-player-container");
    if (playerContainer) playerContainer.style.opacity = "0";

    const player = new YT.Player(playerId, {
      height: "100%",
      width: "100%",
      videoId: videoId,
      playerVars: {
        autoplay: 1,
        mute: 1,
        controls: 0,
        start: 5,
        modestbranding: 1,
        showinfo: 0,
        rel: 0,
        iv_load_policy: 3,
        fs: 0,
        disablekb: 1,
        origin: window.location.origin,
      },
      events: {
        onStateChange: onHoverPlayerStateChange,
      },
    });

    hoverPlayerMap[playerId] = {
      player: player,
      container: playerContainer,
      monitorInterval: null,
    };
  }

  /**
   * Xử lý trạng thái phát của hover player (lặp lại video).
   * @param {object} event - Sự kiện YT Player State Change.
   */
  function onHoverPlayerStateChange(event) {
    const player = event.target;
    const iframe = player.getIframe();
    if (!iframe) return;
    const playerId = iframe.id;

    if (event.data === YT.PlayerState.PLAYING) {
      const hoverPlayerData = hoverPlayerMap[playerId];
      if (hoverPlayerData) {
        if (!hoverPlayerData.container) {
          hoverPlayerData.container = iframe.closest(".hover-player-container");
        }
        if (hoverPlayerData.container) {
          hoverPlayerData.container.style.transition = "opacity 0.4s ease-out";
          setTimeout(() => {
            hoverPlayerData.container.style.opacity = "1";
          }, 300);
        }
        const duration = player.getDuration();
        const endSeconds = duration - 15; // Lặp lại 15s trước khi hết
        if (hoverPlayerData.monitorInterval) {
          clearInterval(hoverPlayerData.monitorInterval);
        }
        hoverPlayerData.monitorInterval = setInterval(() => {
          if (
            player &&
            typeof player.getPlayerState === "function" &&
            player.getPlayerState() === YT.PlayerState.PLAYING
          ) {
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

  /**
   * Dừng và hủy video player trên Hover Card.
   * @param {HTMLElement} card - Thẻ Movie Card.
   */
  function stopHoverVideo(card) {
    if (hoverVideoTimer) {
      clearTimeout(hoverVideoTimer);
      hoverVideoTimer = null;
    }

    const playerId = card.querySelector(".hover-player")?.id;
    if (playerId && hoverPlayerMap[playerId]) {
      const data = hoverPlayerMap[playerId];
      if (data.monitorInterval) {
        clearInterval(data.monitorInterval);
      }
      if (data.player && typeof data.player.destroy === "function") {
        data.player.destroy();
      }
      delete hoverPlayerMap[playerId];
    }

    const playerContainer = card.querySelector(".hover-player-container");
    if (playerContainer) {
      playerContainer.style.transition = "none";
      playerContainer.style.opacity = "0";
    }
  }

  /**
   * Bật/Tắt âm lượng video trên Hover Card.
   * @param {HTMLElement} hoverCard - Phần tử Hover Card.
   * @param {HTMLElement} volumeBtn - Nút Volume.
   */
  function toggleHoverVolume(hoverCard, volumeBtn) {
    const playerId = hoverCard.querySelector(".hover-player")?.id;
    if (
      !playerId ||
      !hoverPlayerMap[playerId] ||
      !hoverPlayerMap[playerId].player
    )
      return;

    const player = hoverPlayerMap[playerId].player;
    const icon = volumeBtn.querySelector("i");

    if (player.isMuted()) {
      player.unMute();
      icon.className = "fas fa-volume-up";
    } else {
      player.mute();
      icon.className = "fas fa-volume-mute";
    }
  }

  /**
   * Xử lý khi di chuột vào Movie Card (Kiểm tra vị trí và bắt đầu tải data).
   * @param {object} event - Sự kiện mouseenter.
   */
  function handleCardHover(event) {
    const card = event.currentTarget;
    const hoverCard = card.querySelector(".movie-hover-card");
    if (!hoverCard) return;

    // Logic né cạnh (Edge detection)
    const cardRect = card.getBoundingClientRect();
    const viewportWidth = window.innerWidth;
    const hoverCardWidth = 340;
    const spaceRight = viewportWidth - cardRect.right;
    const spaceLeft = cardRect.left;
    let originX = "center";
    if (spaceRight < hoverCardWidth / 2 && spaceLeft > hoverCardWidth / 2) {
      originX = "calc(100% - 30px)"; // Né sang trái
    } else if (
      spaceLeft < hoverCardWidth / 2 &&
      spaceRight > hoverCardWidth / 2
    ) {
      originX = "30px"; // Né sang phải
    }
    hoverCard.style.transformOrigin = `${originX} center`;

    clearTimeout(hoverTimeout);
    hoverTimeout = setTimeout(() => {
      enhanceHoverCard(card);
    }, 800);
  }

  /**
   * Xử lý khi di chuột ra khỏi Movie Card (Dừng tải data).
   * @param {object} event - Sự kiện mouseleave.
   */
  function handleCardMouseLeave(event) {
    const card = event.currentTarget;
    const hoverCard = card.querySelector(".movie-hover-card");

    clearTimeout(hoverTimeout);

    setTimeout(() => {
      if (hoverCard && !hoverCard.matches(":hover")) {
        stopHoverVideo(card);
      }
    }, 100);

    if (hoverCard) {
      setTimeout(() => {
        hoverCard.style.transformOrigin = "center center";
      }, 300);
    }
  }

  /**
   * Tự động tìm và gán sự kiện hover cho tất cả Movie Cards trên trang.
   */
  function initHoverCards() {
    const movieCards = document.querySelectorAll(".movie-card[data-movie-id]");

    movieCards.forEach((card) => {
      if (card.dataset.hoverBound === "true") return;

      const hoverCard = card.querySelector(".movie-hover-card");
      if (hoverCard) {
        card.addEventListener("mouseenter", handleCardHover);
        card.addEventListener("mouseleave", handleCardMouseLeave);

        hoverCard.addEventListener("mouseleave", () => {
          stopHoverVideo(card);
        });

        // Gán sự kiện cho nút Volume
        const volumeBtn = hoverCard.querySelector(".hover-volume-btn");
        if (volumeBtn) {
          volumeBtn.addEventListener("click", (e) => {
            e.stopPropagation();
            toggleHoverVolume(hoverCard, volumeBtn);
          });
        }

        card.dataset.hoverBound = "true";
      }
    });
  }

  let userFavoriteMovieIds = new Set();

  async function loadUserFavoriteStatus() {
    try {
      const response = await fetch("/favorites/api/list");
      if (response.status === 401) {
        console.log("User chưa đăng nhập → bỏ qua trạng thái yêu thích");
        return;
      }
      if (!response.ok) throw new Error("Lỗi tải danh sách yêu thích");

      const movieIds = await response.json();
      userFavoriteMovieIds = new Set(movieIds.map((id) => Number(id))); // ép kiểu số

      // Cập nhật toàn bộ nút like trên trang (bao gồm cả card đã render sẵn)
      updateAllLikeButtons();
      console.log("Đã tải trạng thái yêu thích:", movieIds);
    } catch (err) {
      console.error("Lỗi load favorite status:", err);
    }
  }

  function updateAllLikeButtons() {
    document.querySelectorAll(".hover-like-btn").forEach((btn) => {
      const movieId = parseInt(btn.dataset.movieId);
      if (isNaN(movieId)) return;

      const icon = btn.querySelector("i");
      if (userFavoriteMovieIds.has(movieId)) {
        btn.classList.add("liked");
        icon.classList.remove("far");
        icon.classList.add("fas");
      } else {
        btn.classList.remove("liked");
        icon.classList.add("far");
        icon.classList.remove("fas");
      }
    });
  }

  // =========================================================================
  // 7. LOGIC ACTION BUTTONS (GLOBAL ACTIONS)
  // =========================================================================

  // =========================================================
  // [FIX] HÀM XỬ LÝ YÊU THÍCH (LIKE/UNLIKE) CHO TOÀN BỘ APP
  // =========================================================
  window.toggleHoverLike = function (button) {
    const movieId = parseInt(button.dataset.movieId);
    const isLiked = button.classList.contains("liked");

    fetch(`/favorites/${movieId}`, {
      method: "POST",
      headers: { "X-Requested-With": "XMLHttpRequest" },
    })
      .then((res) => res.json())
      .then((data) => {
        if (data.status === "added") {
          button.classList.add("liked");
          button.querySelector("i").classList.remove("far");
          button.querySelector("i").classList.add("fas");
          userFavoriteMovieIds.add(movieId);
        } else if (data.status === "removed") {
          button.classList.remove("liked");
          button.querySelector("i").classList.add("far");
          button.querySelector("i").classList.remove("fas");
          userFavoriteMovieIds.delete(movieId);
        } else if (data.status === "unauthorized") {
          alert("Vui lòng đăng nhập để sử dụng tính năng này!");
          window.location.href = "/login";
        }
      })
      .catch((err) => {
        console.error("Lỗi toggle favorite:", err);
        alert("Có lỗi xảy ra, vui lòng thử lại!");
      });
  };

  // Hàm phụ trợ đổi màu icon (Dùng chung)
  function updateLikeButtonVisual(btn, icon, isActive) {
    if (isActive) {
      btn.classList.add("active");
      icon.classList.remove("far");
      icon.classList.add("fas");
      icon.style.color = "#E50914"; // Đỏ
    } else {
      btn.classList.remove("active");
      icon.classList.remove("fas");
      icon.classList.add("far");
      icon.style.color = ""; // Trắng (hoặc mặc định)
    }
  }

  // Hàm hiển thị thông báo nhỏ (Toast)
  function showToast(message, type) {
    let toast = document.getElementById("toast");
    if (!toast) {
      // Tạo toast nếu chưa có (để dùng cho mọi trang)
      toast = document.createElement("div");
      toast.id = "toast";
      toast.className = "toast";
      toast.innerHTML =
        '<i class="toast-icon"></i><span id="toastMessage"></span>';
      document.body.appendChild(toast);

      // CSS động cho toast (nếu file css chưa có)
      toast.style.cssText =
        "position: fixed; top: 80px; right: 20px; background: #333; color: #fff; padding: 15px 25px; border-radius: 8px; z-index: 99999; display: none; align-items: center; gap: 10px; box-shadow: 0 5px 15px rgba(0,0,0,0.5); transition: opacity 0.3s ease;";
    }

    const msgSpan = document.getElementById("toastMessage");
    const icon = toast.querySelector(".toast-icon");

    msgSpan.textContent = message;

    // Màu sắc theo loại
    if (type === "success") {
      toast.style.borderLeft = "5px solid #28a745";
      icon.className = "toast-icon fas fa-check-circle";
      icon.style.color = "#28a745";
    } else {
      toast.style.borderLeft = "5px solid #dc3545";
      icon.className = "toast-icon fas fa-exclamation-circle";
      icon.style.color = "#dc3545";
    }

    toast.style.display = "flex";
    toast.style.opacity = "1";

    setTimeout(() => {
      toast.style.opacity = "0";
      setTimeout(() => {
        toast.style.display = "none";
      }, 300);
    }, 3000);
  }

  /**
   * Chuyển hướng đến trang chi tiết phim.
   * @param {HTMLElement} button - Nút Play/Xem ngay.
   */
  window.goToMovieDetail = function (button) {
    const movieId = button.dataset.movieId;
    if (movieId) location.href = "/movie/detail/" + movieId;
  };

  /**
   * Hiển thị Modal Chia sẻ.
   * @param {HTMLElement} button - Nút Share.
   */
  window.showShareModal = function (button) {
    const movieId = button.dataset.movieId;
    const movieTitle = button.dataset.movieTitle;

    const url = `${window.location.origin}/movie/detail/${movieId}`;

    const overlay = document.getElementById("shareModalOverlay");
    const input = document.getElementById("shareUrlInput");
    const copyBtn = document.getElementById("copyButton");

    if (input) input.value = url;
    if (copyBtn) {
      copyBtn.textContent = "Sao chép";
      copyBtn.classList.remove("copied");
    }

    // Cập nhật link cho social
    document.getElementById(
      "shareFacebook"
    ).href = `https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(
      url
    )}`;
    document.getElementById(
      "shareX"
    ).href = `https://twitter.com/intent/tweet?url=${encodeURIComponent(
      url
    )}&text=${encodeURIComponent(movieTitle)}`;
    document.getElementById(
      "shareEmail"
    ).href = `mailto:?subject=${encodeURIComponent(
      movieTitle
    )}&body=Xem phim này nhé: ${encodeURIComponent(url)}`;

    if (overlay) overlay.classList.add("active");
  };

  /**
   * Đóng Modal Chia sẻ.
   */
  window.closeShareModal = function () {
    const overlay = document.getElementById("shareModalOverlay");
    if (overlay) {
      overlay.classList.remove("active");
      document.body.style.overflow = "";
    }
  };

  /**
   * Sao chép URL vào clipboard.
   */
  window.copyShareLink = function () {
    const input = document.getElementById("shareUrlInput");
    const copyBtn = document.getElementById("copyButton");

    input.select();
    input.setSelectionRange(0, 99999);

    try {
      navigator.clipboard.writeText(input.value).then(() => {
        if (copyBtn) {
          copyBtn.textContent = "Đã chép!";
          copyBtn.classList.add("copied");
        }
      });
    } catch (err) {
      console.error("Không thể sao chép:", err);
    }
  };

  // =========================================================================
  // 8. LOGIC PAGINATION (PAGINATION LOGIC)
  // =========================================================================

  /**
   * Tự động khởi tạo pagination nếu tìm thấy.
   */
  function initializePagination() {
    const paginationEl = document.getElementById("pagination");
    if (!paginationEl) return;

    const currentPage = parseInt(paginationEl.dataset.currentPage) || 1;
    const totalPages = parseInt(paginationEl.dataset.totalPages) || 1;

    if (totalPages <= 1) return;

    let html = "";

    // Nút Previous
    html += renderPageButton(
      currentPage - 1,
      '<i class="fas fa-chevron-left"></i>',
      currentPage > 1
    );

    // Các nút số
    const maxPagesToShow = 5;
    let startPage = Math.max(1, currentPage - Math.floor(maxPagesToShow / 2));
    let endPage = Math.min(totalPages, startPage + maxPagesToShow - 1);

    if (endPage - startPage + 1 < maxPagesToShow) {
      startPage = Math.max(1, endPage - maxPagesToShow + 1);
    }

    if (startPage > 1) {
      html += renderPageButton(1, "1", true);
      if (startPage > 2) html += '<span class="page-ellipsis">...</span>';
    }

    for (let i = startPage; i <= endPage; i++) {
      html += renderPageButton(i, i.toString(), true, i === currentPage);
    }

    if (endPage < totalPages) {
      if (endPage < totalPages - 1)
        html += '<span class="page-ellipsis">...</span>';
      html += renderPageButton(totalPages, totalPages.toString(), true);
    }

    // Nút Next
    html += renderPageButton(
      currentPage + 1,
      '<i class="fas fa-chevron-right"></i>',
      currentPage < totalPages
    );

    paginationEl.innerHTML = html;

    // [G30] Thêm CSS cho ellipsis (nếu chưa có)
    if (!document.getElementById("pagination-style")) {
      const style = document.createElement("style");
      style.id = "pagination-style";
      style.innerHTML = `.page-ellipsis { padding: 0 10px; color: #666; align-self: center; }`;
      document.head.appendChild(style);
    }
  }

  /**
   * Tạo 1 nút pagination HTML.
   * @param {number} page - Số trang.
   * @param {string} text - Nội dung hiển thị (số hoặc icon).
   * @param {boolean} enabled - Trạng thái kích hoạt.
   * @param {boolean} [isActive=false] - Là trang hiện tại.
   * @returns {string} Chuỗi HTML.
   */
  function renderPageButton(page, text, enabled, isActive = false) {
    const url = buildPageUrl(page);
    const activeClass = isActive ? "active" : "";
    const disabledClass = !enabled ? "disabled" : "";

    if (!enabled) {
      return `<button class="page-btn ${disabledClass}" disabled>${text}</button>`;
    }

    // [G30] Dùng thẻ <a> thay vì <button onclick> để tốt cho SEO
    return `<a href="${url}" class="page-btn ${activeClass}">${text}</a>`;
  }

  /**
   * Xây dựng URL cho trang mới, giữ lại các query parameters cũ.
   * @param {number} page - Số trang mới.
   * @returns {string} URL mới.
   */
  function buildPageUrl(page) {
    const urlParams = new URLSearchParams(window.location.search);
    urlParams.set("page", page.toString());
    return `${window.location.pathname}?${urlParams.toString()}`;
  }

  /**
   * [MỚI] Thiết lập phím tắt toàn cục (Global Shortcuts).
   */
  function setupGlobalShortcuts() {
    document.addEventListener("keydown", (e) => {
      // Bỏ qua nếu người dùng đang nhập liệu trong ô input/textarea
      if (
        ["INPUT", "TEXTAREA", "SELECT"].includes(
          document.activeElement.tagName
        ) ||
        document.activeElement.isContentEditable
      ) {
        return;
      }

      // Phím '/': Focus tìm kiếm hoặc chuyển trang
      if (e.key === "/") {
        e.preventDefault(); // Ngăn ký tự '/' bị gõ vào ô input nếu focus quá nhanh

        // Trường hợp 1: Đang ở trang Search (đã có ô nhập liệu chính)
        const mainInput = document.getElementById("mainSearchInput");
        if (mainInput) {
          mainInput.focus();
          // Mẹo: Đặt con trỏ về cuối văn bản nếu đã có chữ
          const val = mainInput.value;
          mainInput.value = "";
          mainInput.value = val;
        }
        // Trường hợp 2: Đang ở trang khác -> Chuyển sang trang Search
        else {
          window.location.href = "/search";
        }
      }
    });
  }

  // =========================================================================
  // 9. KHỞI TẠO CHUNG (INITIALIZATION)
  // =========================================================================

  /**
   * Khởi tạo tất cả các thành phần khi DOM đã tải.
   */
  document.addEventListener("DOMContentLoaded", () => {
    // 1. Khởi tạo các thành phần UI chung
    setupHeaderScroll();
    setupDescriptionToggle();
    setupBackToTopButton();
    setupLazyLoading();
    setupGlobalShortcuts();

    // 2. Khởi tạo Banner (Nếu có)
    if (document.getElementById("heroBanner")) {
      startAutoRotate();
      displayHeroExtras();

      // Gọi initHeroVideo nếu API đã sẵn sàng
      if (typeof YT !== "undefined" && YT.Player) {
        initHeroVideo();
      }
    }

    // 3. Tự động tìm và khởi tạo các thành phần động
    initializeAllCarousels();
    initHoverCards();
    initializePagination();
    loadUserFavoriteStatus();
  });
  window.initHoverCards = initHoverCards;
  window.initializeAllCarousels = initializeAllCarousels;
  window.enhanceHoverCard = enhanceHoverCard; // Phòng hờ nếu cần gọi trực tiếp
})();


/* --- SOCIAL GLOBAL FUNCTIONS (Dùng chung cho Lobby, Profile, Hover Card) --- */

function sendFriendRequest(targetId, btnElement) {
    if(btnElement) btnElement.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
    
    fetch('/social/add-friend/' + targetId, { method: 'POST' })
        .then(res => res.json())
        .then(data => {
            if(data.status === 'SENT') {
                if(btnElement) {
                    btnElement.innerHTML = '<i class="fas fa-user-times"></i> Hủy lời mời';
                    btnElement.classList.replace('btn-primary-vipro', 'btn-secondary-vipro');
                    btnElement.setAttribute('onclick', 'alert("Đã gửi lời mời!")');
                }
                alert("Đã gửi lời mời kết bạn!");
            } else {
                alert(data.message || "Lỗi gửi lời mời");
            }
        })
        .catch(err => {
            console.error(err);
            alert("Lỗi kết nối");
        });
}

function acceptFriendRequest(senderId, btnElement) {
    if(btnElement) btnElement.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';

    fetch('/social/accept-friend/' + senderId, { method: 'POST' })
        .then(res => res.json())
        .then(data => {
            if(data.status === 'FRIEND') {
                alert("Đã trở thành bạn bè!");
                location.reload(); // Reload để cập nhật UI toàn bộ
            }
        });
}

function openChat(userId) {
    window.location.href = '/messenger?uid=' + userId;
}

/* --- XỬ LÝ KẾT BẠN VIPRO --- */
function handleFriendRequest(notiId, linkProfile, action, btnElement) {
    // 1. Parse ID từ link (VD: /social/profile/5 -> lấy 5)
    // Cách này hơi thủ công, tốt nhất DTO Notification nên có senderId. 
    // Nhưng với dữ liệu hiện tại, ta tạm dùng cách này:
    const senderId = linkProfile.split('/').pop(); 

    if (action === 'REJECT') {
        // Xóa âm thầm, không thông báo
        // Gọi API Reject
        fetch('/social/reject-friend/' + senderId, { method: 'POST' });
        
        // UI: Xóa noti hoặc hiện "Đã gỡ"
        const container = btnElement.closest('.friend-request-actions');
        container.innerHTML = '<span style="font-size:12px; color:#aaa;">Đã gỡ lời mời</span>';
        
        // Đánh dấu noti là đã đọc luôn
        markAsRead(notiId);
        
    } else if (action === 'ACCEPT') {
        // Gọi API Accept
        fetch('/social/accept-friend/' + senderId, { method: 'POST' })
            .then(res => {
                if(res.ok) {
                    const container = btnElement.closest('.friend-request-actions');
                    container.innerHTML = '<span style="font-size:12px; color:#31a24c;">Đã chấp nhận</span>';
                    markAsRead(notiId);
                }
            });
    }
}

/* --- CINEMATIC POPUP (Dùng chung cho toàn web) --- */
function showCinematicConfirm(msg, onConfirm) {
    // Check xem đã có modal chưa, chưa thì tạo
    if (!document.getElementById('cineModal')) {
        const modalHtml = `
        <div id="cineModal" style="display:none; position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.8); z-index:9999; justify-content:center; align-items:center; backdrop-filter:blur(5px);">
            <div style="background:#1a1a1a; padding:30px; border-radius:12px; width:350px; text-align:center; border:1px solid #333; box-shadow: 0 20px 50px rgba(0,0,0,0.5);">
                <i class="fas fa-question-circle" style="font-size:40px; color:#ffd700; margin-bottom:15px;"></i>
                <h3 style="color:#fff; margin-bottom:10px;">Xác nhận</h3>
                <p id="cineMsg" style="color:#ccc; margin-bottom:25px; font-size:14px;"></p>
                <div style="display:flex; justify-content:center; gap:15px;">
                    <button id="cineCancel" style="padding:8px 20px; background:#333; color:#fff; border:none; border-radius:6px; cursor:pointer;">Hủy</button>
                    <button id="cineOk" style="padding:8px 20px; background:#ffd700; color:#000; border:none; border-radius:6px; font-weight:bold; cursor:pointer;">Đồng ý</button>
                </div>
            </div>
        </div>`;
        document.body.insertAdjacentHTML('beforeend', modalHtml);
    }

    const modal = document.getElementById('cineModal');
    document.getElementById('cineMsg').innerText = msg;
    modal.style.display = 'flex';

    // Bind event
    document.getElementById('cineCancel').onclick = function() {
        modal.style.display = 'none';
    };
    document.getElementById('cineOk').onclick = function() {
        modal.style.display = 'none';
        if (onConfirm) onConfirm();
    };
}