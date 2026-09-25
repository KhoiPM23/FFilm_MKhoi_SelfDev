// player.js - Complete Video Player Controller with Ad, Resume, Error Handling, and Shortcuts

document.addEventListener("DOMContentLoaded", () => {
  const playerWrapper = document.getElementById("player-wrapper");
  const mainVideo = document.getElementById("defaultVideoPlayer");

  if (!playerWrapper || !mainVideo) {
    return;
  }

  // DOM Elements
  const adVideo = document.getElementById("adVideoPlayer");
  const adContainer = document.getElementById("adPlayerContainer");
  const adTimerDisplay = document.getElementById("adTimer");
  const skipAdBtn = document.getElementById("skipAdBtn");
  const errorOverlay = document.getElementById("playerErrorOverlay");
  const retryBtn = document.getElementById("btnRetryPlayback");
  const toastEl = document.getElementById("playerToast");
  const toastIcon = document.getElementById("playerToastIcon");
  const toastText = document.getElementById("playerToastText");

  // State & Data Attributes
  const movieId = playerWrapper.dataset.movieId;
  const hasAd = playerWrapper.dataset.hasAd === "true";
  const adUrl = playerWrapper.dataset.adUrl || "";
  const startTime = parseFloat(playerWrapper.dataset.startTime || "0");

  let isHistoryRecorded = false;
  let lastRecordedTime = -1;
  let heartbeatInterval = null;
  let adInterval = null;
  let toastTimeout = null;

  // ----------------------------------------------------
  // 1. TOAST HELPER
  // ----------------------------------------------------
  function showToast(message, iconClass = "fa-info-circle", duration = 1200) {
    if (!toastEl || !toastIcon || !toastText) return;

    toastIcon.className = "fas " + iconClass;
    toastText.textContent = message;

    toastEl.style.display = "flex";
    toastEl.style.opacity = "1";

    if (toastTimeout) {
      clearTimeout(toastTimeout);
    }

    toastTimeout = setTimeout(() => {
      toastEl.style.opacity = "0";
      setTimeout(() => {
        if (toastEl.style.opacity === "0") {
          toastEl.style.display = "none";
        }
      }, 250);
    }, duration);
  }

  // ----------------------------------------------------
  // 2. TIME FORMATTER
  // ----------------------------------------------------
  function formatTime(seconds) {
    if (isNaN(seconds) || seconds < 0) return "0:00";
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = Math.floor(seconds % 60);
    if (h > 0) {
      return `${h}:${m.toString().padStart(2, "0")}:${s.toString().padStart(2, "0")}`;
    }
    return `${m}:${s.toString().padStart(2, "0")}`;
  }

  // ----------------------------------------------------
  // 3. FULLSCREEN TOGGLE
  // ----------------------------------------------------
  function toggleFullscreen() {
    if (!document.fullscreenElement) {
      if (playerWrapper.requestFullscreen) {
        playerWrapper.requestFullscreen().catch((err) => {
          console.warn("Fullscreen request error:", err);
        });
      }
    } else {
      if (document.exitFullscreen) {
        document.exitFullscreen().catch((err) => {
          console.warn("Exit fullscreen error:", err);
        });
      }
    }
  }

  // ----------------------------------------------------
  // 4. WATCH PROGRESS & HISTORY API
  // ----------------------------------------------------
  async function recordHistory() {
    if (!movieId || isHistoryRecorded) return;
    isHistoryRecorded = true;

    try {
      const response = await fetch(`/api/history/record/${movieId}`, {
        method: "POST",
        headers: { Accept: "application/json" },
      });
      if (response.ok) {
        console.log("Watch history recorded for movie:", movieId);
      }
    } catch (error) {
      console.warn("Watch history record failed:", error);
    }
  }

  function updateServerProgress(currentTime) {
    if (!movieId || currentTime == null || currentTime < 0) return;
    if (Math.abs(currentTime - lastRecordedTime) < 1) return;

    lastRecordedTime = currentTime;
    const url = `/api/history/update-progress?movieId=${encodeURIComponent(movieId)}&currentTime=${encodeURIComponent(currentTime.toFixed(1))}`;

    fetch(url, {
      method: "POST",
      keepalive: true,
    }).catch((err) => {
      console.warn("Update progress error:", err);
    });
  }

  // ----------------------------------------------------
  // 5. MAIN VIDEO LIFECYCLE
  // ----------------------------------------------------
  function startMainVideo() {
    if (adContainer) {
      adContainer.style.display = "none";
    }
    if (adVideo) {
      adVideo.pause();
      adVideo.muted = true;
    }
    if (adInterval) {
      clearInterval(adInterval);
      adInterval = null;
    }

    mainVideo.style.display = "block";
    mainVideo.controls = true;

    // Apply resume time if valid
    function applyResume() {
      if (startTime > 5 && mainVideo.currentTime < 5) {
        try {
          mainVideo.currentTime = startTime;
          showToast(`Tiếp tục xem từ ${formatTime(startTime)}`, "fa-history", 2500);
        } catch (e) {
          console.warn("Failed to set resume currentTime:", e);
        }
      }
    }

    if (mainVideo.readyState >= 1) {
      applyResume();
    } else {
      mainVideo.addEventListener("loadedmetadata", applyResume, { once: true });
    }

    // Unmute & Attempt Play
    mainVideo.muted = false;
    mainVideo.play().catch((err) => {
      console.log("Autoplay blocked by browser policy, awaiting user action:", err);
    });

    // Record initial history
    recordHistory();

    // Start progress heartbeat every 10 seconds
    if (!heartbeatInterval) {
      heartbeatInterval = setInterval(() => {
        if (!mainVideo.paused && movieId && mainVideo.currentTime > 0) {
          updateServerProgress(mainVideo.currentTime);
        }
      }, 10000);
    }
  }

  // ----------------------------------------------------
  // 6. AD PLAYER MANAGER
  // ----------------------------------------------------
  function initAdPlayer() {
    if (!hasAd || !adVideo || !adContainer || !adUrl) {
      startMainVideo();
      return;
    }

    const skipDelaySeconds = 5;

    // Pause main video while ad runs
    mainVideo.pause();
    mainVideo.muted = true;
    mainVideo.controls = false;

    // Play ad video
    adVideo.muted = false;
    adVideo.play().catch((err) => {
      console.warn("Ad autoplay blocked, falling back to main video:", err);
      startMainVideo();
    });

    // Countdown logic
    function updateCountdown() {
      const duration = adVideo.duration;
      const current = adVideo.currentTime;

      if (!isNaN(duration) && duration > 0) {
        const remaining = Math.max(0, Math.ceil(duration - current));
        if (adTimerDisplay) {
          adTimerDisplay.textContent = remaining;
        }
      }

      if (current >= skipDelaySeconds && skipAdBtn) {
        skipAdBtn.style.display = "block";
      }
    }

    adInterval = setInterval(updateCountdown, 500);

    adVideo.addEventListener("ended", startMainVideo, { once: true });
    adVideo.addEventListener("error", (e) => {
      console.warn("Ad video playback error, skipping to main video:", e);
      startMainVideo();
    }, { once: true });

    if (skipAdBtn) {
      skipAdBtn.addEventListener("click", () => {
        startMainVideo();
      });
    }
  }

  // ----------------------------------------------------
  // 7. ERROR HANDLING & FALLBACK
  // ----------------------------------------------------
  function handlePlaybackError(e) {
    console.error("Playback error detected on main video:", e);
    if (errorOverlay) {
      errorOverlay.style.display = "flex";
    }
  }

  mainVideo.addEventListener("error", handlePlaybackError);

  const mainSource = mainVideo.querySelector("source");
  if (mainSource) {
    mainSource.addEventListener("error", handlePlaybackError);
  }

  if (retryBtn) {
    retryBtn.addEventListener("click", () => {
      if (errorOverlay) {
        errorOverlay.style.display = "none";
      }
      showToast("Đang tải lại...", "fa-sync fa-spin", 1500);
      try {
        mainVideo.load();
        mainVideo.play().catch((err) => {
          console.warn("Retry playback failed:", err);
        });
      } catch (err) {
        console.error("Retry load error:", err);
      }
    });
  }

  // ----------------------------------------------------
  // 8. PROGRESS ON PAUSE & UNLOAD
  // ----------------------------------------------------
  mainVideo.addEventListener("pause", () => {
    if (movieId && mainVideo.currentTime > 0) {
      updateServerProgress(mainVideo.currentTime);
    }
  });

  const handleUnload = () => {
    if (movieId && mainVideo && !mainVideo.paused && mainVideo.currentTime > 0) {
      updateServerProgress(mainVideo.currentTime);
    }
  };

  window.addEventListener("beforeunload", handleUnload);
  window.addEventListener("pagehide", handleUnload);

  // ----------------------------------------------------
  // 9. DOUBLE CLICK FOR FULLSCREEN
  // ----------------------------------------------------
  mainVideo.addEventListener("dblclick", (e) => {
    e.preventDefault();
    toggleFullscreen();
  });

  // ----------------------------------------------------
  // 10. KEYBOARD SHORTCUTS
  // ----------------------------------------------------
  document.addEventListener("keydown", (e) => {
    const activeEl = document.activeElement;
    if (
      activeEl &&
      (activeEl.tagName === "INPUT" ||
        activeEl.tagName === "TEXTAREA" ||
        activeEl.isContentEditable)
    ) {
      return;
    }

    if (mainVideo.style.display === "none") {
      return; // Do not control main video while ad is playing
    }

    switch (e.code) {
      case "Space":
      case "KeyK":
        e.preventDefault();
        if (mainVideo.paused) {
          mainVideo.play().catch(() => {});
          showToast("Phát", "fa-play");
        } else {
          mainVideo.pause();
          showToast("Tạm dừng", "fa-pause");
        }
        break;

      case "ArrowLeft":
      case "KeyJ":
        e.preventDefault();
        mainVideo.currentTime = Math.max(0, mainVideo.currentTime - 10);
        showToast("-10 giây", "fa-backward");
        break;

      case "ArrowRight":
      case "KeyL":
        e.preventDefault();
        mainVideo.currentTime = Math.min(
          mainVideo.duration || 999999,
          mainVideo.currentTime + 10
        );
        showToast("+10 giây", "fa-forward");
        break;

      case "ArrowUp":
        e.preventDefault();
        mainVideo.volume = Math.min(1.0, parseFloat((mainVideo.volume + 0.1).toFixed(2)));
        if (mainVideo.muted) mainVideo.muted = false;
        showToast(`Âm lượng: ${Math.round(mainVideo.volume * 100)}%`, "fa-volume-up");
        break;

      case "ArrowDown":
        e.preventDefault();
        mainVideo.volume = Math.max(0.0, parseFloat((mainVideo.volume - 0.1).toFixed(2)));
        showToast(
          `Âm lượng: ${Math.round(mainVideo.volume * 100)}%`,
          mainVideo.volume === 0 ? "fa-volume-mute" : "fa-volume-down"
        );
        break;

      case "KeyM":
        e.preventDefault();
        mainVideo.muted = !mainVideo.muted;
        showToast(
          mainVideo.muted ? "Đã tắt tiếng" : "Đã bật tiếng",
          mainVideo.muted ? "fa-volume-mute" : "fa-volume-up"
        );
        break;

      case "KeyF":
        e.preventDefault();
        toggleFullscreen();
        break;
    }
  });

  // ----------------------------------------------------
  // 11. START PLAYBACK FLOW
  // ----------------------------------------------------
  if (hasAd) {
    initAdPlayer();
  } else {
    startMainVideo();
  }
});