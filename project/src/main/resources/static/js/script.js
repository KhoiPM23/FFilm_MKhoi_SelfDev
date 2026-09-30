/**
 * =========================================================================================
 * FFilm Main UI Script (Module Pattern - IIFE)
 * Tối ưu hóa hiệu năng, quản lý trạng thái và xử lý tương tác giao diện người dùng.
 * =========================================================================================
 */
(function () {
  "use strict";

  if (window.__FFILM_SCRIPT_LOADED__) return;
  window.__FFILM_SCRIPT_LOADED__ = true;

  // =========================================================================
  // 1. CẤU HÌNH VÀ BIẾN TOÀN CỤC (GLOBAL CONFIG AND STATE)
  // =========================================================================

  // Core State Variables
  let heroPlayer = null;
  let videoTimeout = null;
  let movieDetailCache = {};
  let carouselRotateInterval = null;
  let hoverPlayerMap = {};
  let hoverVideoTimer = null;
  let hoverTimeout = null; // Biến timeout cho hover card

  const HOVER_VIDEO_DELAY = 60; // Khởi chạy video hover siêu tốc (0.06s - giảm 50%)
  const isGenreMapLoaded = true; // [G30] Đơn giản hóa, luôn mặc định là true

  // DOM Elements (được truy vấn khi cần hoặc khởi tạo sớm)
  const heroBanner = document.getElementById("heroBanner");
  const videoContainer = document.getElementById("heroVideoContainer");
  const volumeBtn = document.getElementById("volumeBtn");
  const miniCarouselTrack = document.getElementById("miniCarousel");

  // =========================================================================
  // 2. LOGIC YOUTUBE PLAYER VÀ TRAILER (YOUTUBE PLAYER & TRAILER LOGIC)
  // =========================================================================

  let heroFadeTimeout = null;

  /**
   * Reset DOM và hủy player cũ an toàn.
   */
  function resetHeroVideoDOM() {
    if (videoTimeout) clearTimeout(videoTimeout);
    if (heroFadeTimeout) clearTimeout(heroFadeTimeout);
    if (heroPlayer) {
      try { heroPlayer.destroy(); } catch (e) { }
      heroPlayer = null;
    }
    if (videoContainer) {
      videoContainer.style.opacity = "0";
      videoContainer.innerHTML = '<div id="heroPlayer"></div>';
    }
    if (heroBanner) {
      heroBanner.setAttribute("data-video-active", "false");
    }
  }

  /**
   * Khởi tạo YouTube Player cho Hero Banner.
   */
  function initHeroVideo() {
    resetHeroVideoDOM();
    if (!videoContainer) return;

    const trailerKey = heroBanner ? heroBanner.dataset.trailerKey : "";
    if (!trailerKey || trailerKey === "null") {
      return;
    }

    // Khởi chạy ngay (50ms) trong nền ở opacity: 0 để YouTube buffer và tự giấu nút pause ở giữa
    videoTimeout = setTimeout(() => {
      heroPlayer = new YT.Player("heroPlayer", {
        height: "100%",
        width: "100%",
        videoId: trailerKey,
        playerVars: {
          autoplay: 1,
          mute: 1,
          controls: 0,
          rel: 0,
          iv_load_policy: 3,
          modestbranding: 1,
          showinfo: 0,
          fs: 0,
          origin: window.location.origin,
        },
        events: {
          onReady: onPlayerReady,
          onStateChange: onPlayerStateChange,
        },
      });
      if (videoContainer) videoContainer.style.pointerEvents = "none";
    }, 50);
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
    try { event.target.mute(); } catch (e) { }
    if (videoContainer) videoContainer.style.pointerEvents = "none";
    setupVolumeControl();
  }

  /**
   * Xử lý thay đổi trạng thái của YouTube Player (ví dụ: lặp lại khi ENDED).
   * @param {object} event - Sự kiện YT Player State Change.
   */
  function onPlayerStateChange(event) {
    if (event.data === YT.PlayerState.PLAYING) {
      if (heroFadeTimeout) clearTimeout(heroFadeTimeout);
      // Đợi 1.2s (giảm ~50% từ 2.5s) để YouTube tự làm mờ và ẩn hoàn toàn splash icon Pause ở giữa màn hình
      heroFadeTimeout = setTimeout(() => {
        if (videoContainer) videoContainer.style.opacity = "1";
        if (heroBanner) heroBanner.setAttribute("data-video-active", "true");
      }, 1200);
    } else if (event.data === YT.PlayerState.ENDED) {
      if (heroPlayer && typeof heroPlayer.seekTo === "function") {
        heroPlayer.seekTo(5, true);
        heroPlayer.playVideo();
      }
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

    // 1. Slide-out nội dung cũ mượt mà sang trái
    if (heroContentEl) {
      heroContentEl.classList.remove("slide-in-prep", "slide-in-active");
      heroContentEl.classList.add("slide-out");
    }

    // 2. Hủy video cũ và reset DOM
    resetHeroVideoDOM();

    // 3. Delay 220ms
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
      if (heroLikeBtn) {
        heroLikeBtn.setAttribute("data-movie-id", newId);
        heroLikeBtn.classList.remove("active");
        const heartIcon = heroLikeBtn.querySelector("i");
        if (heartIcon) {
          heartIcon.className = "far fa-heart";
          heartIcon.style.color = "";
        }
        fetch(`/favorites/api/check/${newId}`)
          .then((res) => res.json())
          .then((data) => {
            if (data && data.isFavorite) {
              heroLikeBtn.classList.add("active");
              if (heartIcon) {
                heartIcon.className = "fas fa-heart";
                heartIcon.style.color = "#E50914";
              }
            }
          })
          .catch(() => { });
      }
      const volumeBtnEl = document.getElementById("volumeBtn");
      if (volumeBtnEl) {
        volumeBtnEl.innerHTML = '<i class="fas fa-volume-mute"></i>';
      }
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

      // 8. Hiệu ứng Slide-in từ trái sang phải mượt mà (không giựt)
      if (heroContentEl) {
        heroContentEl.classList.remove("slide-out");
        heroContentEl.classList.add("slide-in-prep");
        void heroContentEl.offsetWidth; // Force reflow
        requestAnimationFrame(() => {
          heroContentEl.classList.remove("slide-in-prep");
          heroContentEl.classList.add("slide-in-active");
        });
      }
    }, 220);

    // 9. Cập nhật mini-carousel
    const activeMovieId = cardElement.dataset.movieId;
    document.querySelectorAll(".mini-card").forEach((c) => {
      if (c.dataset.movieId === String(activeMovieId)) {
        c.classList.add("active");
      } else {
        c.classList.remove("active");
        const fill = c.querySelector(".mini-card-progress-fill");
        if (fill) fill.style.width = "0%";
      }
    });
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
      miniCarouselTrack.scrollLeft +
      (cardRect.left - trackRect.left) -
      trackRect.width / 2 +
      cardRect.width / 2;
    miniCarouselTrack.scrollTo({ left: scrollPosition, behavior: "smooth" });
  }

  /**
   * Di chuyển mini carousel tới/lùi 1 phim và chuyển banner.
   * @param {number} direction - 1 (tới) hoặc -1 (lùi).
   */
  function advanceMiniCarousel(direction) {
    if (!miniCarouselTrack) return;
    const cards = Array.from(miniCarouselTrack.querySelectorAll(".mini-card"));
    if (cards.length === 0) return;

    // Tìm thẻ mini-card đang ở gần tâm viewport của track nhất
    const trackRect = miniCarouselTrack.getBoundingClientRect();
    const trackCenter = trackRect.left + trackRect.width / 2;

    let closestCard = null;
    let minDistance = Infinity;

    cards.forEach((card) => {
      const r = card.getBoundingClientRect();
      const cardCenter = r.left + r.width / 2;
      const dist = Math.abs(cardCenter - trackCenter);
      if (dist < minDistance) {
        minDistance = dist;
        closestCard = card;
      }
    });

    let targetCard = null;
    if (closestCard) {
      if (direction > 0) {
        targetCard = closestCard.nextElementSibling;
      } else {
        targetCard = closestCard.previousElementSibling;
      }
    }

    if (!targetCard) {
      targetCard = direction > 0 ? cards[0] : cards[cards.length - 1];
    }

    if (targetCard && typeof window.switchBanner === "function") {
      window.switchBanner(targetCard);
    }
  }

  /**
   * Khởi tạo cuộn vô hạn cho Mini Carousel trong Hero Banner.
   */
  function initMiniCarouselInfiniteScroll() {
    const track = document.getElementById("miniCarousel");
    if (!track) return;
    if (track.dataset.infiniteInit === "true") return;

    const originalCards = Array.from(track.querySelectorAll(".mini-card"));
    if (originalCards.length < 2) return;

    track.dataset.infiniteInit = "true";

    // Nhân bản danh sách (Set 1 trước, Set 3 sau) để tạo vòng lặp vô hạn
    const set1Frag = document.createDocumentFragment();
    const set3Frag = document.createDocumentFragment();

    originalCards.forEach((c) => {
      const c1 = c.cloneNode(true);
      c1.classList.remove("active");
      c1.onclick = function () {
        window.switchBanner(c1);
      };
      set1Frag.appendChild(c1);

      const c3 = c.cloneNode(true);
      c3.classList.remove("active");
      c3.onclick = function () {
        window.switchBanner(c3);
      };
      set3Frag.appendChild(c3);
    });

    track.insertBefore(set1Frag, track.firstChild);
    track.appendChild(set3Frag);

    // Tính toán chiều rộng 1 set để wrap vô hạn
    requestAnimationFrame(() => {
      const allCards = Array.from(track.querySelectorAll(".mini-card"));
      const N = originalCards.length;
      if (allCards.length < 3 * N) return;

      const set1First = allCards[0];
      const set2First = allCards[N];
      const singleSetWidth = set2First.offsetLeft - set1First.offsetLeft;

      // Scroll đến vị trí card active trong Set 2 ban đầu
      const activeCard = track.querySelector(".mini-card.active") || set2First;
      const trackRect = track.getBoundingClientRect();
      const cardRect = activeCard.getBoundingClientRect();
      const initialScroll =
        track.scrollLeft +
        (cardRect.left - trackRect.left) -
        trackRect.width / 2 +
        cardRect.width / 2;
      track.scrollLeft = initialScroll;

      // Lắng nghe scroll để tự động reset vị trí vô hạn (không giật hình)
      let isAdjusting = false;
      track.addEventListener("scroll", () => {
        if (isAdjusting || singleSetWidth <= 0) return;
        const currentScroll = track.scrollLeft;
        const baseOffset = set2First.offsetLeft;

        // Nếu cuộn qua Set 3 -> nhảy ngược lại Set 2
        if (currentScroll >= baseOffset + singleSetWidth) {
          isAdjusting = true;
          track.scrollLeft = currentScroll - singleSetWidth;
          isAdjusting = false;
        }
        // Nếu cuộn ngược vào Set 1 -> nhảy tiến lên Set 2
        else if (currentScroll <= baseOffset - singleSetWidth) {
          isAdjusting = true;
          track.scrollLeft = currentScroll + singleSetWidth;
          isAdjusting = false;
        }
      });
    });

    // (Mouse wheel cuộn ngang đã được tắt theo yêu cầu để trang cuộn dọc bình thường)

    // Hỗ trợ kéo chuột (Drag-to-scroll)
    let isDown = false;
    let startX = 0;
    let scrollStart = 0;
    let hasMoved = false;

    track.addEventListener("mousedown", (e) => {
      isDown = true;
      hasMoved = false;
      startX = e.pageX - track.offsetLeft;
      scrollStart = track.scrollLeft;
      track.style.cursor = "grabbing";
    });

    window.addEventListener("mousemove", (e) => {
      if (!isDown) return;
      const x = e.pageX - track.offsetLeft;
      const diff = x - startX;
      if (Math.abs(diff) > 6) hasMoved = true;
      track.scrollLeft = scrollStart - diff;
    });

    window.addEventListener("mouseup", () => {
      if (isDown) {
        isDown = false;
        track.style.cursor = "grab";
      }
    });

    // Ngăn click nhầm khi đang kéo chuột
    track.addEventListener(
      "click",
      (e) => {
        if (hasMoved) {
          e.stopPropagation();
          e.preventDefault();
          hasMoved = false;
        }
      },
      true
    );

    // Gán nút Next / Prev cho mini carousel
    const prevBtn = document.getElementById("miniPrevBtn");
    const nextBtn = document.getElementById("miniNextBtn");

    if (nextBtn) {
      nextBtn.addEventListener("click", (e) => {
        e.preventDefault();
        e.stopPropagation();
        advanceMiniCarousel(1);
      });
    }

    if (prevBtn) {
      prevBtn.addEventListener("click", (e) => {
        e.preventDefault();
        e.stopPropagation();
        advanceMiniCarousel(-1);
      });
    }
  }

  /**
   * Bắt đầu quay carousel tự động (luôn cuộn tới vô hạn).
   */
  let progressAnimation = null;
  let autoRotateStartTime = 0;
  const ROTATE_INTERVAL = 11000;

  function updateProgressBar() {
    if (!miniCarouselTrack) return;
    const progressBars = miniCarouselTrack.querySelectorAll(".mini-card.active .mini-card-progress-fill");
    if (!progressBars || progressBars.length === 0) return;

    if (!carouselRotateInterval) {
      progressBars.forEach((b) => (b.style.width = "0%"));
      return;
    }

    const now = Date.now();
    const elapsed = now - autoRotateStartTime;
    const progress = Math.max(0, Math.min(100, (elapsed / ROTATE_INTERVAL) * 100));

    progressBars.forEach((b) => {
      b.style.width = `${progress}%`;
    });

    if (progress < 100) {
      progressAnimation = requestAnimationFrame(updateProgressBar);
    }
  }

  function startAutoRotate() {
    if (carouselRotateInterval) clearInterval(carouselRotateInterval);
    if (progressAnimation) cancelAnimationFrame(progressAnimation);

    if (!miniCarouselTrack) return;
    const cards = Array.from(miniCarouselTrack.querySelectorAll(".mini-card"));
    if (cards.length < 2) return;

    autoRotateStartTime = Date.now();
    progressAnimation = requestAnimationFrame(updateProgressBar);

    carouselRotateInterval = setInterval(() => {
      advanceMiniCarousel(1);
    }, ROTATE_INTERVAL);
  }

  function stopAutoRotate() {
    if (carouselRotateInterval) {
      clearInterval(carouselRotateInterval);
      carouselRotateInterval = null;
    }
    if (progressAnimation) {
      cancelAnimationFrame(progressAnimation);
      progressAnimation = null;
    }
    if (miniCarouselTrack) {
      miniCarouselTrack.querySelectorAll(".mini-card-progress-fill").forEach((fill) => {
        fill.style.width = "0%";
      });
    }
    const progressBar = document.getElementById("heroProgressFill");
    if (progressBar) {
      progressBar.style.width = "0%";
    }
  }

  // Mini carousel tự động quay liên tục theo ROTATE_INTERVAL (hover không reset timer/progress)

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
            setTimeout(() => {
              entry.target.classList.add("loaded");
            }, 80);
            observer.unobserve(entry.target);
          }
        });
      },
      {
        rootMargin: "0px 0px -50px 0px",
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
      const nav = section.querySelector(".carousel-nav");

      if (nav && nav.parentElement !== section) {
        section.appendChild(nav); // Di chuyển nav ra ngoài cùng section để dễ định vị absolute
      }

      const prevBtn = section.querySelector(".nav-btn.prev-btn");
      const nextBtn = section.querySelector(".nav-btn.next-btn");

      if (!slider || !prevBtn || !nextBtn) {
        return;
      }

      const container = slider.parentElement;
      let currentScroll = 0;

      function updateSliderState() {
        if (!container || !slider) return;
        const maxScroll = Math.max(0, slider.scrollWidth - container.offsetWidth);
        currentScroll = Math.max(0, Math.min(currentScroll, maxScroll));
        slider.style.transition = "transform 0.4s ease";
        slider.style.transform = `translateX(-${currentScroll}px)`;

        if (maxScroll <= 0) {
          prevBtn.style.display = "none";
          nextBtn.style.display = "none";
        } else {
          prevBtn.style.display = "flex";
          nextBtn.style.display = "flex";
          prevBtn.disabled = currentScroll <= 5;
          nextBtn.disabled = currentScroll >= maxScroll - 5;
          prevBtn.classList.toggle("disabled", prevBtn.disabled);
          nextBtn.classList.toggle("disabled", nextBtn.disabled);
        }
      }

      if (slider.dataset.initialized) {
        updateSliderState();
        return;
      }
      slider.dataset.initialized = 'true';

      if (!slider.id) slider.id = `auto-slider-${index}`;

      const cardObserver = new MutationObserver(() => {
        updateSliderState();
      });
      cardObserver.observe(slider, { childList: true });

      function getCardStride() {
        const firstCard = slider.querySelector(".movie-card");
        if (!firstCard) return 220;
        return firstCard.offsetWidth + 15;
      }

      prevBtn.addEventListener("click", function () {
        const containerWidth = container.offsetWidth;
        const stride = getCardStride();
        const step = Math.max(stride, Math.floor(containerWidth / stride) * stride);
        currentScroll = Math.max(0, currentScroll - step);
        currentScroll = Math.round(currentScroll / stride) * stride;
        updateSliderState();
      });

      nextBtn.addEventListener("click", function () {
        const containerWidth = container.offsetWidth;
        const maxScroll = Math.max(0, slider.scrollWidth - containerWidth);
        const stride = getCardStride();
        const step = Math.max(stride, Math.floor(containerWidth / stride) * stride);
        currentScroll = Math.min(
          maxScroll,
          currentScroll + step
        );
        currentScroll = Math.round(currentScroll / stride) * stride;
        if (currentScroll > maxScroll) currentScroll = maxScroll;
        updateSliderState();
      });

      // DRAG-TO-SCROLL với MAGNETIC SNAP cho main carousels
      let isDown = false;
      let startX = 0;
      let dragStartScroll = 0;
      let hasMoved = false;

      const onPointerMove = (e) => {
        if (!isDown) return;
        const x = e.pageX;
        const diff = x - startX;

        if (Math.abs(diff) > 6) hasMoved = true;

        const containerWidth = container.offsetWidth;
        const maxScroll = Math.max(0, slider.scrollWidth - containerWidth);

        let newScroll = dragStartScroll - diff;
        if (newScroll < 0) newScroll = 0;
        if (newScroll > maxScroll) newScroll = maxScroll;

        currentScroll = newScroll;
        slider.style.transition = "none";
        slider.style.transform = `translateX(-${currentScroll}px)`;
      };

      const onPointerUp = (e) => {
        if (isDown) {
          isDown = false;
          container.style.cursor = "grab";
          if (hasMoved) {
            const containerWidth = container.offsetWidth;
            const maxScroll = Math.max(0, slider.scrollWidth - containerWidth);
            const stride = getCardStride();
            const snappedScroll = Math.round(currentScroll / stride) * stride;
            currentScroll = Math.max(0, Math.min(snappedScroll, maxScroll));
          }
          slider.style.transition = "transform 0.35s cubic-bezier(0.25, 1, 0.5, 1)";
          updateSliderState();
        }
        window.removeEventListener("pointermove", onPointerMove);
        window.removeEventListener("pointerup", onPointerUp);
        window.removeEventListener("pointercancel", onPointerUp);
      };

      container.addEventListener("pointerdown", (e) => {
        // Chỉ bắt drag nếu là chuột trái hoặc touch
        if (e.pointerType === "mouse" && e.button !== 0) return;
        isDown = true;
        hasMoved = false;
        startX = e.pageX;
        dragStartScroll = currentScroll;
        container.style.cursor = "grabbing";

        window.addEventListener("pointermove", onPointerMove);
        window.addEventListener("pointerup", onPointerUp);
        window.addEventListener("pointercancel", onPointerUp);
      });

      // Ngăn chặn click nhảy trang nếu đang kéo
      container.addEventListener("click", (e) => {
        if (hasMoved) {
          e.stopPropagation();
          e.preventDefault();
          hasMoved = false;
        }
      }, true);

      container.style.cursor = "grab";

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
    if (!tooltip) return;
    tooltip.style.left = "";
    tooltip.style.right = "";
    tooltip.style.display = "flex";

    // Only adjust if protruding outside the entire browser viewport (to prevent page scrollbar)
    const tipRect = tooltip.getBoundingClientRect();
    const viewportWidth = window.innerWidth;
    if (tipRect.right > viewportWidth - 8) {
      tooltip.style.left = "auto";
      tooltip.style.right = "0";
    }
  };

  /**
   * Ẩn Tooltip Genre (Được gọi từ onmouseleave).
   * @param {HTMLElement} element - Phần tử .genre-tag-more.
   */
  window.hideGenreTooltip = function (element) {
    const tooltip = element.querySelector(".custom-genre-tooltip");
    if (tooltip) {
      tooltip.style.display = "none";
      tooltip.style.left = "";
      tooltip.style.right = "";
    }
  };

  /**
   * Khởi tạo và phát video trailer trên Hover Card.
   * @param {HTMLElement} hoverCard - Phần tử Hover Card.
   * @param {string} videoId - YouTube Video ID.
   */
  function playHoverVideo(hoverCard, videoId) {
    const playerId = hoverCard.querySelector(".hover-player")?.id;
    if (!playerId) return;

    if (hoverPlayerMap[playerId]) {
      if (hoverPlayerMap[playerId].fadeTimeout) {
        clearTimeout(hoverPlayerMap[playerId].fadeTimeout);
      }
      if (hoverPlayerMap[playerId].player && typeof hoverPlayerMap[playerId].player.destroy === "function") {
        hoverPlayerMap[playerId].player.destroy();
      }
      clearInterval(hoverPlayerMap[playerId].monitorInterval);
    }

    const playerContainer = hoverCard.querySelector(".hover-player-container");
    if (playerContainer) {
      playerContainer.style.transition = "none";
      playerContainer.style.opacity = "0";
      let playerElem = document.getElementById(playerId);
      if (!playerElem) {
        playerElem = document.createElement("div");
        playerElem.id = playerId;
        playerElem.className = "hover-player";
        playerContainer.appendChild(playerElem);
      }
    }

    const player = new YT.Player(playerId, {
      height: "100%",
      width: "100%",
      videoId: videoId,
      playerVars: {
        autoplay: 1,
        mute: 1,
        controls: 0,
        rel: 0,
        iv_load_policy: 3,
        modestbranding: 1,
        showinfo: 0,
        fs: 0,
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
      fadeTimeout: null,
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
          hoverPlayerData.container.style.transition = "opacity 0.3s ease-out";
          if (hoverPlayerData.fadeTimeout) {
            clearTimeout(hoverPlayerData.fadeTimeout);
          }
          hoverPlayerData.fadeTimeout = setTimeout(() => {
            if (hoverPlayerMap[playerId]) {
              hoverPlayerData.container.style.opacity = "1";
            }
          }, 850);
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
      if (data.fadeTimeout) {
        clearTimeout(data.fadeTimeout);
      }
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

    // Edge-aware alignment: prevent clipping at left and right boundaries
    const cardRect = card.getBoundingClientRect();
    const viewportWidth = window.innerWidth;
    const slider = card.closest(".movie-slider, .movies-scroll");
    const containerRect = slider ? slider.parentElement.getBoundingClientRect() : { left: 0, right: viewportWidth };

    const spaceLeft = Math.min(cardRect.left, cardRect.left - containerRect.left);

    // Edge-aware: align left edge at left boundary, align right edge at right boundary
    const spaceRight = viewportWidth - cardRect.right;

    if (spaceLeft < 60) {
      hoverCard.classList.add("edge-left");
      hoverCard.classList.remove("edge-right");
    } else if (spaceRight < 100) {
      // Card nằm sát mép phải viewport → mở hover card về bên trái để không bị khuất
      hoverCard.classList.add("edge-right");
      hoverCard.classList.remove("edge-left");
    } else {
      hoverCard.classList.remove("edge-left", "edge-right");
    }

    clearTimeout(hoverTimeout);
    hoverTimeout = setTimeout(() => {
      enhanceHoverCard(card);
    }, 120);
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
        hoverCard.classList.remove("edge-left", "edge-right");
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
    if (!button) return;
    const movieId = button.dataset.movieId || button.closest("[data-movie-id]")?.dataset.movieId;
    if (movieId) location.href = "/movie/detail/" + movieId;
  };

  // Delegated click handler: Click vùng không tương tác trên hover card hoặc movie card để vào Detail
  document.addEventListener("click", (e) => {
    if (e.target.closest("button, a, input, textarea, select, .hover-action-icon, .hover-volume-btn, .hover-play-btn, .genre-tag-more, .custom-genre-tooltip, .nav-btn")) {
      return;
    }
    const hoverCard = e.target.closest(".movie-hover-card");
    if (hoverCard && hoverCard.dataset.movieId) {
      location.href = `/movie/detail/${hoverCard.dataset.movieId}`;
      return;
    }
    const movieCard = e.target.closest(".movie-card");
    if (movieCard && movieCard.dataset.movieId) {
      location.href = `/movie/detail/${movieCard.dataset.movieId}`;
      return;
    }
  });

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
      initMiniCarouselInfiniteScroll();
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


/**
 * =========================================================================================
 * FFilm Main UI Script - CENTRALIZED CONTROLLER
 * =========================================================================================
 */
(function () {
  "use strict";

  // --- 1. GLOBAL HELPERS (Gán vào window để HTML gọi được) ---

  /**
   * Hiển thị Modal Xác nhận (Cinematic Style)
   */
  window.cineConfirm = function (msg, callback) {
    if (document.getElementById('cineModal')) document.getElementById('cineModal').remove();

    const html = `
    <div id="cineModal" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0, 0, 0, 0.9); z-index: 99999; display: flex; justify-content: center; align-items: center; backdrop-filter: blur(5px); animation: popIn 0.3s ease-out;">
        <div style="background: #1e1e1e; padding: 30px; border-radius: 12px; max-width: 400px; text-align: center; box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5); border: 1px solid #333;">
            <div style="font-size:3rem; color:#e50914; margin-bottom:15px;"><i class="fas fa-exclamation-circle"></i></div>
            <h3 style="color:#fff; margin-bottom:10px; font-family:'Segoe UI', sans-serif;">Xác nhận</h3>
            <p style="color:#ccc; margin-bottom:25px; line-height:1.5;">${msg}</p>
            <div style="display:flex; justify-content:center; gap:15px;">
                <button onclick="document.getElementById('cineModal').remove()" style="padding:10px 24px; background:#333; color:#fff; border:none; border-radius:8px; cursor:pointer; font-weight:600; transition:0.2s;">Hủy bỏ</button>
                <button id="cineConfirmBtn" style="padding:10px 24px; background:#e50914; color:#fff; border:none; border-radius:8px; font-weight:600; cursor:pointer; box-shadow: 0 4px 15px rgba(229,9,20,0.4); transition:0.2s;">Đồng ý</button>
            </div>
        </div>
    </div>
    <style>@keyframes popIn { from { transform: scale(0.8); opacity: 0; } to { transform: scale(1); opacity: 1; } }</style>
    `;
    document.body.insertAdjacentHTML('beforeend', html);

    document.getElementById('cineConfirmBtn').onclick = function () {
      if (callback) callback();
      document.getElementById('cineModal').remove();
    };
  };

  /**
   * Chuyển hướng đến Messenger với ID người dùng
   */
  window.openChat = function (userId) {
    window.location.href = `/messenger?uid=${userId}`;
  };

  /**
   * Xem Profile
   */
  window.viewProfile = function (userId) {
    window.location.href = `/social/profile/${userId}`;
  };

  // --- 2. SOCIAL ACTIONS (Kết bạn, Hủy kết bạn, Follow) ---

  // Gửi lời mời (Dùng cho Lobby & Profile)
  window.sendFriendRequest = function (targetId, btnElement) {
    // UI Loading
    const originalContent = btnElement.innerHTML;
    btnElement.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
    btnElement.disabled = true;

    // API: SocialController @PostMapping("/add-friend/{targetId}")
    fetch(`/social/add-friend/${targetId}`, { method: 'POST' })
      .then(res => {
        if (!res.ok) throw new Error("Lỗi server");
        return res.json();
      })
      .then(data => {
        // Cập nhật UI thành công
        btnElement.innerHTML = '<i class="fas fa-paper-plane"></i> Đã gửi';
        btnElement.classList.remove('btn-primary', 'btn-blue'); // Xóa class cũ
        btnElement.classList.add('btn-secondary', 'btn-dark'); // Thêm class xám
        btnElement.setAttribute('onclick', `window.cancelFriendRequest(${targetId}, this)`);
        btnElement.disabled = false;
        showToast("Đã gửi lời mời kết bạn!", "success");
      })
      .catch(err => {
        btnElement.innerHTML = originalContent;
        btnElement.disabled = false;
        showToast("Lỗi: Không thể gửi lời mời.", "error");
      });
  };

  // Hủy kết bạn / Hủy lời mời (Dùng chung)
  window.cancelFriendRequest = function (targetId, btnElement) {
    window.cineConfirm('Bạn muốn hủy lời mời / hủy kết bạn với người này?', function () {
      // UI Loading
      btnElement.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';

      // API: SocialController @PostMapping("/unfriend/{targetId}")
      fetch(`/social/unfriend/${targetId}`, { method: 'POST' })
        .then(res => {
          if (res.ok) {
            // Reset về nút "Thêm bạn bè"
            btnElement.innerHTML = '<i class="fas fa-user-plus"></i> Thêm bạn bè';
            btnElement.classList.remove('btn-secondary', 'btn-dark');
            btnElement.classList.add('btn-primary', 'btn-blue');
            btnElement.setAttribute('onclick', `window.sendFriendRequest(${targetId}, this)`);
            showToast("Đã hủy thành công.", "success");
            // Nếu đang ở trang Profile, có thể reload để cập nhật số liệu
            if (window.location.pathname.includes('/profile/')) location.reload();
          }
        });
    });
  };

  // Chấp nhận kết bạn (Thường dùng trong Notification dropdown)
  window.acceptFriendRequest = function (senderId, btnElement) {
    fetch(`/social/accept-friend/${senderId}`, { method: 'POST' })
      .then(res => {
        if (res.ok) {
          if (btnElement) {
            btnElement.innerHTML = '<i class="fas fa-check"></i> Bạn bè';
            btnElement.onclick = null;
          }
          showToast("Đã trở thành bạn bè!", "success");
          // Refresh trang nếu cần thiết
          if (window.location.pathname.includes('/profile/')) location.reload();
        }
      });
  };

  // Follow User
  window.followUser = function (targetId, btnElement) {
    fetch(`/social/api/follow/${targetId}`, { method: 'POST' })
      .then(res => {
        if (res.ok) {
          btnElement.innerHTML = '<i class="fas fa-check"></i> Đang theo dõi';
          btnElement.setAttribute('onclick', `window.unfollowUser(${targetId}, this)`);
          btnElement.classList.replace('btn-blue', 'btn-dark');
        }
      });
  };

  // Unfollow User
  window.unfollowUser = function (targetId, btnElement) {
    fetch(`/social/api/unfollow/${targetId}`, { method: 'POST' })
      .then(res => {
        if (res.ok) {
          btnElement.innerHTML = '<i class="fas fa-rss"></i> Theo dõi';
          btnElement.setAttribute('onclick', `window.followUser(${targetId}, this)`);
          btnElement.classList.replace('btn-dark', 'btn-blue');
        }
      });
  };

  // --- 3. TOAST NOTIFICATION ---
  function showToast(message, type = 'info') {
    let toast = document.getElementById('viproToast');
    if (!toast) {
      toast = document.createElement('div');
      toast.id = 'viproToast';
      toast.style.cssText = "position:fixed; top:80px; right:20px; background:#333; color:#fff; padding:12px 20px; border-radius:8px; z-index:99999; display:none; box-shadow:0 5px 15px rgba(0,0,0,0.5); border-left: 4px solid #e50914;";
      document.body.appendChild(toast);
    }
    toast.style.borderLeftColor = type === 'success' ? '#46d369' : '#e50914';
    toast.innerHTML = `<span>${message}</span>`;
    toast.style.display = 'block';
    setTimeout(() => { toast.style.display = 'none'; }, 3000);
  }

  // --- 4. GLOBAL SOCKET (Duy trì kết nối 1 lần) ---
  let globalStompClient = null;
  window.connectGlobalSocket = function () {
    if (globalStompClient && globalStompClient.connected) return;

    const socket = new SockJS('/ws');
    globalStompClient = Stomp.over(socket);
    globalStompClient.debug = null; // Tắt log console

    globalStompClient.connect({}, function () {
      console.log('✅ Global Socket Connected');

      // Lắng nghe thông báo
      globalStompClient.subscribe('/user/queue/notifications', function (msg) {
        const noti = JSON.parse(msg.body);
        // Gọi hàm update UI ở header (nếu có)
        if (typeof window.onNewNotificationReceived === 'function') {
          window.onNewNotificationReceived(noti);
        }
        showToast("🔔 " + noti.content);
      });
    }, function (err) {
      console.log('Socket error, reconnecting...', err);
      setTimeout(window.connectGlobalSocket, 5000);
    });
  };

  // --- 5. INIT ON LOAD ---
  document.addEventListener("DOMContentLoaded", () => {
    // Chỉ kết nối socket nếu user đã đăng nhập (kiểm tra có div notiContainer ở header ko)
    if (document.getElementById('notiContainer')) {
      window.connectGlobalSocket();
    }

    // Init Hover Cards (nếu có)
    if (typeof window.initHoverCards === 'function') window.initHoverCards();
  });

  // =======================================================
  // 6. TOP PAGE PROGRESS LOADER (YOUTUBE STYLE)
  // =======================================================
  (function initTopPageLoader() {
    let loader = document.getElementById("page-top-loader");
    if (!loader) {
      loader = document.createElement("div");
      loader.id = "page-top-loader";
      if (document.body) {
        document.body.prepend(loader);
      } else {
        document.addEventListener("DOMContentLoaded", () => document.body.prepend(loader), { once: true });
      }
    }

    let loaderTimeout = null;

    function startLoader() {
      if (!loader) loader = document.getElementById("page-top-loader");
      if (!loader) return;
      if (loaderTimeout) clearTimeout(loaderTimeout);
      loader.classList.remove("done");
      loader.classList.add("loading");
      loader.style.width = "25%";
      loaderTimeout = setTimeout(() => {
        loader.style.width = "75%";
      }, 120);
    }

    function finishLoader() {
      if (document.body) document.body.classList.remove("page-transitioning");
      if (!loader) loader = document.getElementById("page-top-loader");
      if (!loader) return;
      if (loaderTimeout) clearTimeout(loaderTimeout);
      if (loader.classList.contains("loading")) {
        loader.classList.add("done");
        setTimeout(() => {
          loader.classList.remove("loading", "done");
          loader.style.width = "0%";
        }, 400);
      }
    }

    // Expose helpers globally
    window.startPageLoader = startLoader;
    window.finishPageLoader = finishLoader;
    window.triggerPageTransition = function(url) {
      if (document.body) document.body.classList.add("page-transitioning");
      startLoader();
      if (url) {
        setTimeout(() => { window.location.href = url; }, 50);
      }
    };

    // Finish loader when page is ready
    if (document.readyState === "complete" || document.readyState === "interactive") {
      finishLoader();
    } else {
      window.addEventListener("DOMContentLoaded", finishLoader, { once: true });
    }
    window.addEventListener("load", finishLoader, { once: true });
    window.addEventListener("pageshow", finishLoader);

    // Intercept internal link clicks and card elements with data-href
    document.addEventListener("click", function (e) {
      const targetEl = e.target.closest("a, [data-href], [data-url]");
      if (!targetEl) return;

      let href = "";
      const isAnchor = targetEl.tagName.toLowerCase() === "a";
      if (isAnchor) {
        href = targetEl.getAttribute("href");
        if (
          !href ||
          href.startsWith("#") ||
          href.startsWith("javascript:") ||
          targetEl.target === "_blank" ||
          targetEl.hasAttribute("download") ||
          targetEl.dataset.noLoader !== undefined
        ) {
          return;
        }
      } else {
        // [data-href] or [data-url]
        href = targetEl.getAttribute("data-href") || targetEl.getAttribute("data-url");
        if (!href || href.startsWith("#") || href.startsWith("javascript:")) return;
      }

      // Only trigger for same-origin links
      try {
        const targetUrl = new URL(href, window.location.origin);
        if (targetUrl.origin === window.location.origin) {
          if (targetUrl.pathname === window.location.pathname && targetUrl.search === window.location.search && targetUrl.hash) {
            return; // In-page anchor jump
          }
          if (document.body) document.body.classList.add("page-transitioning");
          startLoader();

          if (!isAnchor && !targetEl.onclick && !e.defaultPrevented) {
            setTimeout(() => {
              window.location.href = targetUrl.href;
            }, 60);
          }
        }
      } catch (err) {
        // Ignore invalid URL
      }
    });

    // Form submission feedback
    document.addEventListener("submit", function(e) {
      const form = e.target;
      if (form && form.target !== "_blank") {
        if (document.body) document.body.classList.add("page-transitioning");
        startLoader();
      }
    });

    window.addEventListener("pagehide", () => {
      if (loader) loader.style.width = "95%";
    });

    // Fallback: auto-hide if navigation takes longer than 8s or gets cancelled
    window.addEventListener("beforeunload", () => {
      setTimeout(() => finishLoader(), 8000);
    });
  })();

})();