// player.js - Video Player with HLS Support

const TMDB_API_KEY = 'eac03c4e09a0f5099128e38cb0e67a8f';
const TMDB_BASE_URL = 'https://api.themoviedb.org/3';

class VideoPlayer {
    constructor() {
        this.movieId = this.getMovieIdFromUrl();
        this.video = document.getElementById('videoPlayer');
        this.hls = null;
        this.isPlaying = false;
        this.currentTime = 0;
        this.duration = 0;
        this.hideControlsTimeout = null;
        
        this.init();
    }
    
    getMovieIdFromUrl() {
        const pathParts = window.location.pathname.split('/');
        return pathParts[pathParts.length - 1];
    }
    
    async init() {
        try {
            // Load movie info
            await this.loadMovieInfo();
            
            // Initialize player
            await this.initializePlayer();
            
            // Bind controls
            this.bindControls();
            
            // Auto-hide controls
            this.setupAutoHideControls();
            
            // Load watch progress
            this.loadWatchProgress();
            
        } catch (error) {
            console.error('Player initialization error:', error);
            this.showError('Không thể khởi tạo trình phát. Vui lòng thử lại.');
        }
    }
    
    async loadMovieInfo() {
        const url = `${TMDB_BASE_URL}/movie/${this.movieId}?api_key=${TMDB_API_KEY}&language=vi-VN`;
        const response = await fetch(url);
        const movie = await response.json();
        
        document.getElementById('playerTitle').textContent = movie.title;
        document.title = `${movie.title} - Đang xem | FFilm`;
    }
    
    async initializePlayer() {
        // Sample HLS manifest for testing
        // In production, this should come from your backend: /api/movies/{id}/stream
        const manifestUrl = 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8';
        
        const loadingOverlay = document.getElementById('loadingOverlay');
        loadingOverlay.classList.remove('hidden');
        
        if (Hls.isSupported()) {
            this.hls = new Hls({
                enableWorker: true,
                lowLatencyMode: false,
                backBufferLength: 90
            });
            
            this.hls.loadSource(manifestUrl);
            this.hls.attachMedia(this.video);
            
            this.hls.on(Hls.Events.MANIFEST_PARSED, () => {
                console.log('HLS manifest loaded, ready to play');
                loadingOverlay.classList.add('hidden');
                
                // Auto play
                const playPromise = this.video.play();
                if (playPromise !== undefined) {
                    playPromise.catch(error => {
                        console.log('Auto-play prevented:', error);
                    });
                }
            });
            
            this.hls.on(Hls.Events.ERROR, (event, data) => {
                console.error('HLS error:', data);
                if (data.fatal) {
                    switch (data.type) {
                        case Hls.ErrorTypes.NETWORK_ERROR:
                            this.showError('Lỗi kết nối mạng. Đang thử kết nối lại...');
                            this.hls.startLoad();
                            break;
                        case Hls.ErrorTypes.MEDIA_ERROR:
                            this.showError('Lỗi phương tiện. Đang khôi phục...');
                            this.hls.recoverMediaError();
                            break;
                        default:
                            this.showError('Lỗi nghiêm trọng. Không thể phát video.');
                            this.hls.destroy();
                            break;
                    }
                }
            });
            
            // Quality levels
            this.hls.on(Hls.Events.LEVEL_LOADED, () => {
                this.updateQualityOptions();
            });
            
        } else if (this.video.canPlayType('application/vnd.apple.mpegurl')) {
            // Native HLS support (Safari)
            this.video.src = manifestUrl;
            
            this.video.addEventListener('loadedmetadata', () => {
                loadingOverlay.classList.add('hidden');
                this.video.play();
            });
            
        } else {
            this.showError('Trình duyệt không hỗ trợ phát video HLS');
        }
    }
    
    bindControls() {
        // Play/Pause
        const playPauseBtn = document.getElementById('playPauseBtn');
        playPauseBtn.addEventListener('click', () => this.togglePlayPause());
        
        this.video.addEventListener('click', () => this.togglePlayPause());
        
        // Video events
        this.video.addEventListener('play', () => this.onPlay());
        this.video.addEventListener('pause', () => this.onPause());
        this.video.addEventListener('timeupdate', () => this.onTimeUpdate());
        this.video.addEventListener('loadedmetadata', () => this.onLoadedMetadata());
        this.video.addEventListener('ended', () => this.onEnded());
        
        // Progress bar
        const progressContainer = document.getElementById('progressContainer');
        progressContainer.addEventListener('click', (e) => this.seekTo(e));
        
        // Rewind/Forward
        document.getElementById('rewindBtn').addEventListener('click', () => {
            this.video.currentTime = Math.max(0, this.video.currentTime - 10);
        });
        
        document.getElementById('forwardBtn').addEventListener('click', () => {
            this.video.currentTime = Math.min(this.video.duration, this.video.currentTime + 10);
        });
        
        // Volume
        const volumeBtn = document.getElementById('volumeBtn');
        const volumeSlider = document.getElementById('volumeSlider');
        
        volumeBtn.addEventListener('click', () => this.toggleMute());
        volumeSlider.addEventListener('input', (e) => {
            this.video.volume = e.target.value / 100;
            this.updateVolumeIcon();
        });
        
        // Speed
        const speedSelect = document.getElementById('speedSelect');
        speedSelect.addEventListener('change', (e) => {
            this.video.playbackRate = parseFloat(e.target.value);
        });
        
        // Quality (HLS only)
        const qualitySelect = document.getElementById('qualitySelect');
        qualitySelect.addEventListener('change', (e) => {
            this.changeQuality(e.target.value);
        });
        
        // Picture-in-Picture
        const pipBtn = document.getElementById('pipBtn');
        if (document.pictureInPictureEnabled) {
            pipBtn.addEventListener('click', () => this.togglePiP());
        } else {
            pipBtn.style.display = 'none';
        }
        
        // Fullscreen
        const fullscreenBtn = document.getElementById('fullscreenBtn');
        fullscreenBtn.addEventListener('click', () => this.toggleFullscreen());
        
        // Keyboard shortcuts
        document.addEventListener('keydown', (e) => this.handleKeyboard(e));
        
        // Episode list toggle
        const episodeListBtn = document.getElementById('episodeListBtn');
        const episodeSidebar = document.getElementById('episodeSidebar');
        const closeEpisodes = document.getElementById('closeEpisodes');
        
        if (episodeListBtn) {
            episodeListBtn.addEventListener('click', () => {
                episodeSidebar.classList.toggle('active');
            });
        }
        
        if (closeEpisodes) {
            closeEpisodes.addEventListener('click', () => {
                episodeSidebar.classList.remove('active');
            });
        }
        
        // Retry button
        const retryBtn = document.getElementById('retryBtn');
        if (retryBtn) {
            retryBtn.addEventListener('click', () => {
                document.getElementById('errorOverlay').classList.remove('active');
                this.initializePlayer();
            });
        }
    }
    
    togglePlayPause() {
        if (this.video.paused) {
            this.video.play();
        } else {
            this.video.pause();
        }
    }
    
    onPlay() {
        this.isPlaying = true;
        const icon = document.querySelector('#playPauseBtn i');
        icon.className = 'fas fa-pause';
    }
    
    onPause() {
        this.isPlaying = false;
        const icon = document.querySelector('#playPauseBtn i');
        icon.className = 'fas fa-play';
    }
    
    onTimeUpdate() {
        this.currentTime = this.video.currentTime;
        
        // Update progress bar
        const progressBar = document.getElementById('progressBar');
        const percentage = (this.currentTime / this.video.duration) * 100;
        progressBar.style.width = `${percentage}%`;
        
        // Update time display
        document.getElementById('currentTime').textContent = this.formatTime(this.currentTime);
        
        // Save progress every 10 seconds
        if (Math.floor(this.currentTime) % 10 === 0) {
            this.saveWatchProgress();
        }
    }
    
    onLoadedMetadata() {
        this.duration = this.video.duration;
        document.getElementById('duration').textContent = this.formatTime(this.duration);
    }
    
    onEnded() {
        console.log('Video ended');
        this.saveWatchProgress();
        // TODO: Show next episode or related movies
    }
    
    seekTo(event) {
        const progressContainer = event.currentTarget;
        const rect = progressContainer.getBoundingClientRect();
        const pos = (event.clientX - rect.left) / rect.width;
        this.video.currentTime = pos * this.video.duration;
    }
    
    toggleMute() {
        this.video.muted = !this.video.muted;
        this.updateVolumeIcon();
        
        const volumeSlider = document.getElementById('volumeSlider');
        volumeSlider.value = this.video.muted ? 0 : this.video.volume * 100;
    }
    
    updateVolumeIcon() {
        const icon = document.querySelector('#volumeBtn i');
        const volume = this.video.muted ? 0 : this.video.volume;
        
        if (volume === 0) {
            icon.className = 'fas fa-volume-mute';
        } else if (volume < 0.5) {
            icon.className = 'fas fa-volume-down';
        } else {
            icon.className = 'fas fa-volume-up';
        }
    }
    
    updateQualityOptions() {
        if (!this.hls) return;
        
        const qualitySelect = document.getElementById('qualitySelect');
        const levels = this.hls.levels;
        
        qualitySelect.innerHTML = '<option value="-1">Tự động</option>';
        
        levels.forEach((level, index) => {
            const option = document.createElement('option');
            option.value = index;
            option.textContent = `${level.height}p`;
            qualitySelect.appendChild(option);
        });
    }
    
    changeQuality(levelIndex) {
        if (!this.hls) return;
        
        const index = parseInt(levelIndex);
        if (index === -1) {
            this.hls.currentLevel = -1; // Auto
        } else {
            this.hls.currentLevel = index;
        }
    }
    
    async togglePiP() {
        try {
            if (document.pictureInPictureElement) {
                await document.exitPictureInPicture();
            } else {
                await this.video.requestPictureInPicture();
            }
        } catch (error) {
            console.error('PiP error:', error);
        }
    }
    
    toggleFullscreen() {
        const playerWrapper = document.getElementById('playerWrapper');
        
        if (!document.fullscreenElement) {
            playerWrapper.requestFullscreen().catch(err => {
                console.error('Fullscreen error:', err);
            });
        } else {
            document.exitFullscreen();
        }
        
        // Update icon
        const icon = document.querySelector('#fullscreenBtn i');
        icon.className = document.fullscreenElement ? 'fas fa-compress' : 'fas fa-expand';
    }
    
    handleKeyboard(event) {
        // Prevent if typing in input
        if (event.target.tagName === 'INPUT') return;
        
        switch(event.key) {
            case ' ':
                event.preventDefault();
                this.togglePlayPause();
                break;
            case 'ArrowLeft':
                this.video.currentTime -= 5;
                break;
            case 'ArrowRight':
                this.video.currentTime += 5;
                break;
            case 'ArrowUp':
                event.preventDefault();
                this.video.volume = Math.min(1, this.video.volume + 0.1);
                document.getElementById('volumeSlider').value = this.video.volume * 100;
                this.updateVolumeIcon();
                break;
            case 'ArrowDown':
                event.preventDefault();
                this.video.volume = Math.max(0, this.video.volume - 0.1);
                document.getElementById('volumeSlider').value = this.video.volume * 100;
                this.updateVolumeIcon();
                break;
            case 'f':
                this.toggleFullscreen();
                break;
            case 'm':
                this.toggleMute();
                break;
        }
    }
    
    setupAutoHideControls() {
        const playerWrapper = document.getElementById('playerWrapper');
        
        const resetTimeout = () => {
            clearTimeout(this.hideControlsTimeout);
            playerWrapper.classList.remove('hide-controls');
            
            if (this.isPlaying) {
                this.hideControlsTimeout = setTimeout(() => {
                    playerWrapper.classList.add('hide-controls');
                }, 3000);
            }
        };
        
        playerWrapper.addEventListener('mousemove', resetTimeout);
        playerWrapper.addEventListener('touchstart', resetTimeout);
        
        this.video.addEventListener('play', resetTimeout);
        this.video.addEventListener('pause', () => {
            clearTimeout(this.hideControlsTimeout);
            playerWrapper.classList.remove('hide-controls');
        });
    }
    
    formatTime(seconds) {
        if (isNaN(seconds)) return '0:00';
        
        const h = Math.floor(seconds / 3600);
        const m = Math.floor((seconds % 3600) / 60);
        const s = Math.floor(seconds % 60);
        
        if (h > 0) {
            return `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
        }
        return `${m}:${s.toString().padStart(2, '0')}`;
    }
    
    saveWatchProgress() {
        const progress = {
            movieId: this.movieId,
            currentTime: this.currentTime,
            duration: this.duration,
            timestamp: Date.now()
        };
        
        localStorage.setItem(`progress_${this.movieId}`, JSON.stringify(progress));
    }
    
    loadWatchProgress() {
        const savedProgress = localStorage.getItem(`progress_${this.movieId}`);
        
        if (savedProgress) {
            const progress = JSON.parse(savedProgress);
            
            // If watched more than 10% and less than 90%, offer to resume
            const percentage = (progress.currentTime / progress.duration) * 100;
            
            if (percentage > 10 && percentage < 90) {
                const resume = confirm(`Tiếp tục xem từ ${this.formatTime(progress.currentTime)}?`);
                if (resume) {
                    this.video.currentTime = progress.currentTime;
                }
            }
        }
    }
    
    showError(message) {
        const errorOverlay = document.getElementById('errorOverlay');
        const errorMessage = document.getElementById('errorMessage');
        
        errorMessage.textContent = message;
        errorOverlay.classList.add('active');
    }
}

// Initialize when DOM ready
document.addEventListener('DOMContentLoaded', () => {
    new VideoPlayer();
});

// Thêm code này vào file player.js của bạn

document.addEventListener('DOMContentLoaded', () => {
    
    // Tìm thẻ div cha chứa data-movie-id
    const playerWrapper = document.getElementById('player-wrapper');
    
    if (playerWrapper) {
        // Lấy movieId từ data attribute
        const movieId = playerWrapper.dataset.movieId;
        let hasRecorded = false; // Cờ để đảm bảo chỉ gọi 1 lần

        const recordHistory = async () => {
            // Nếu đã gọi rồi hoặc không có movieId thì không làm gì cả
            if (hasRecorded || !movieId) return; 

            console.log(`Recording history for movie ID: ${movieId}`);
            hasRecorded = true; // Đánh dấu là đã gọi
            
            try {
                const response = await fetch(`/api/history/record/${movieId}`, {
                    method: 'POST',
                    headers: {
                        'Accept': 'application/json'
                        // Không cần CSRF token vì bạn đã tắt nó trong SecurityConfig
                    }
                });

                if (response.ok) {
                    console.log('Watch history recorded successfully.');
                } else if (response.status === 401) {
                    // Lỗi 401 (Unauthorized) nghĩa là user chưa đăng nhập
                    console.log('User not logged in. Skipping history record.');
                } 
                else {
                    // Các lỗi khác (500, 404...)
                    console.error('Failed to record watch history.');
                }
            } catch (error) {
                // Lỗi mạng, không thể kết nối
                console.error('Error calling record history API:', error);
            }
        };

        // === LOGIC GỌI HÀM ===
        // Chúng ta gọi thẳng hàm khi trang player được tải.
        // Điều này giả định rằng nếu user đã vào trang player, 
        // họ có ý định xem (cách an toàn nhất cho <iframe>)
        if(movieId) {
             recordHistory();
        }
    }
});