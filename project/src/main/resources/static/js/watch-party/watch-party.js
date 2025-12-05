// watch-party.js - Ultimate Version (Giphy Integrated)

// --- CẤU HÌNH ---
const GIPHY_API_KEY = '79jQGsmrNhvWRKAytBeikpRkve4u2m0K'; // <-- THAY KEY CỦA BẠN VÀO ĐÂY (Đăng ký tại developers.giphy.com)
// Nếu chưa có key, dùng tạm list backup này để test
const BACKUP_STICKERS = [
    "https://media.giphy.com/media/26BRv0ThflsHCqDrG/giphy.gif",
    "https://media.giphy.com/media/l0HlO3BJ8LxrZ4VRu/giphy.gif",
    "https://media.giphy.com/media/3o7TKSjRrfIPjeiVyM/giphy.gif",
    "https://media.giphy.com/media/l0HlI9qB6L8l756z6/giphy.gif",
    "https://media.giphy.com/media/3o6Zt481isNBF5POT6/giphy.gif",
    "https://media.giphy.com/media/3o7qDEq2bMbcbPRQ2c/giphy.gif"
];

var socket = new SockJS('/ws');
var stompClient = Stomp.over(socket);
stompClient.debug = null; 

var isSyncing = false; 
var isSidebarOpen = true;
var currentReply = null;

// --- PEERJS CONFIG (VIDEO CALL) ---
// Sử dụng server cloud miễn phí của PeerJS. Nếu lag thì cần dựng server riêng.
var myPeer = new Peer(undefined, {
    host: 'peerjs-server.herokuapp.com',
    secure: true,
    port: 443
});

var myStream;
var peers = {}; // Danh sách kết nối
var isSyncing = false;
var searchPage = 0; // Pagination cho search

// --- KẾT NỐI SOCKET ---
stompClient.connect({}, function (frame) {
    console.log('Connected');
    if (typeof joinStatus !== 'undefined' && joinStatus === 'WAITING') {
        stompClient.subscribe('/topic/party/' + roomId + '/approval/' + sessionId, function(msg) {
            if (msg.body === 'APPROVED') {
                document.getElementById('waitingScreen').remove();
                document.getElementById('noMovieState').style.display = 'block';
                initFullFeatures(); 
            }
        });
        return; 
    }
    initFullFeatures();
    
    // Tự động load Sticker từ Giphy
    loadGiphyStickers();
});

function initFullFeatures() {
    // 1. Chat & Reaction
    stompClient.subscribe('/topic/party/' + roomId + '/chat', function (payload) {
        var msg = JSON.parse(payload.body);
        if (msg.type === 'REACTION') showFloatingEmoji(msg.content);
        else handleIncomingMessage(msg);
    });

    // 2. Lịch sử
    stompClient.subscribe('/topic/party/' + roomId + '/history/' + sessionId, function (payload) {
        var history = JSON.parse(payload.body);
        history.forEach(drawMessage);
    });
    stompClient.send("/app/party/" + roomId + "/getHistory", {}, JSON.stringify({sessionId: sessionId}));

    // 3. Phim & Sync
    stompClient.subscribe('/topic/party/' + roomId + '/loadMovie', function (payload) {
        var movie = JSON.parse(payload.body);
        loadMovie(movie.url, movie.title);
    });

    stompClient.subscribe('/topic/party/' + roomId + '/sync', function (payload) {
        if (!isHost) {
            var action = JSON.parse(payload.body);
            if (action.sender !== username) handleVideoSync(action);
        }
    });
    
    stompClient.subscribe('/topic/party/' + roomId + '/kick/' + sessionId, function (msg) {
        alert("Bạn đã bị mời ra khỏi phòng!");
        window.location.href = "/";
    });

    // WebRTC: Lắng nghe user mới vào để gọi video
    stompClient.subscribe('/topic/party/' + roomId + '/user-connected', (payload) => {
        var userId = payload.body; // PeerID của user mới
        if(myStream) connectToNewUser(userId, myStream);
    });
}

// Subscribe waiting list updates (Host only)
if (isHost) {
    stompClient.subscribe('/topic/party/' + roomId + '/waitingUpdate', (payload) => {
        const waitingUsers = JSON.parse(payload.body);
        if (waitingUsers.length > 0) {
            document.getElementById('waitingNotif').style.display = 'block';
        } else {
            document.getElementById('waitingNotif').style.display = 'none';
        }
    });
}

// --- WEBSOCKET & PEERJS LINK ---
myPeer.on('open', id => {
    // Gửi PeerID của mình lên Server để broadcast cho người khác biết
    // (Cần thêm endpoint này ở Controller nếu chưa có, hoặc dùng kênh chat 'JOIN' để gửi kèm PeerID)
    // Ở đây ta giả lập việc gửi PeerID qua kênh Chat hệ thống ẩn
});

// Nhận cuộc gọi
myPeer.on('call', call => {
    call.answer(myStream); // Trả lời bằng stream của mình
    const video = document.createElement('video');
    call.on('stream', userVideoStream => {
        addVideoStream(video, userVideoStream);
    });
});

function connectToNewUser(userId, stream) {
    const call = myPeer.call(userId, stream);
    const video = document.createElement('video');
    call.on('stream', userVideoStream => {
        addVideoStream(video, userVideoStream);
    });
    call.on('close', () => {
        video.remove();
    });
    peers[userId] = call;
}

function addVideoStream(video, stream) {
    video.srcObject = stream;
    video.addEventListener('loadedmetadata', () => { video.play(); });
    
    // Tạo khung hiển thị cam người khác
    const div = document.createElement('div');
    div.className = 'user-cam';
    div.appendChild(video);
    document.getElementById('videoGrid').appendChild(div);
}

// --- CAM/MIC CONTROLS ---
function toggleCam() {
    const btn = document.getElementById('btnCam');
    const container = document.getElementById('localCamContainer');
    
    if (btn.classList.contains('active')) {
        // Tắt Cam
        btn.classList.remove('active');
        btn.innerHTML = '<i class="fas fa-video-slash"></i>';
        if(myStream) {
            myStream.getTracks().forEach(track => track.stop());
            container.style.display = 'none';
        }
    } else {
        // Bật Cam
        navigator.mediaDevices.getUserMedia({ video: true, audio: true }).then(stream => {
            myStream = stream;
            const localVideo = document.getElementById('localVideo');
            localVideo.srcObject = stream;
            container.style.display = 'block';
            
            btn.classList.add('active');
            btn.innerHTML = '<i class="fas fa-video"></i>';
            
            // Tắt âm thanh local để không vang
            localVideo.muted = true; 
        });
    }
}

function toggleMic() {
    const btn = document.getElementById('btnMic');
    if(myStream) {
        const audioTrack = myStream.getAudioTracks()[0];
        if(audioTrack.enabled) {
            audioTrack.enabled = false;
            btn.classList.remove('active-mic');
            btn.innerHTML = '<i class="fas fa-microphone-slash"></i>';
        } else {
            audioTrack.enabled = true;
            btn.classList.add('active-mic');
            btn.innerHTML = '<i class="fas fa-microphone"></i>';
        }
    }
}

// --- GIPHY INTEGRATION (TỰ ĐỘNG FILL) ---
function loadGiphyStickers() {
    const container = document.getElementById('stickerList');
    container.innerHTML = '<div class="text-center w-100 text-muted"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';

    // Gọi API Giphy Trending Stickers
    fetch(`https://api.giphy.com/v1/stickers/trending?api_key=${GIPHY_API_KEY}&limit=20&rating=g`)
    .then(res => res.json())
    .then(data => {
        if (data.data && data.data.length > 0) {
            renderStickers(data.data.map(item => item.images.fixed_height_small.url));
        } else {
            renderStickers(BACKUP_STICKERS); // Fallback nếu lỗi key
        }
    })
    .catch(err => {
        console.warn("Giphy API Error (Dùng backup):", err);
        renderStickers(BACKUP_STICKERS);
    });
}

function renderStickers(urlList) {
    const container = document.getElementById('stickerList');
    let html = '';
    urlList.forEach(url => {
        html += `<img src="${url}" onclick="sendSticker('${url}')" 
                 class="sticker-item" 
                 style="width:70px; height:70px; cursor:pointer; object-fit:contain; margin:5px; transition:0.2s;">`;
    });
    container.innerHTML = html;
}

// --- LOGIC CHAT & UPLOAD ẢNH ---

function handleIncomingMessage(msg) {
    drawMessage(msg);
    if (!isSidebarOpen) showFloatingBubble(msg);
}

function drawMessage(msg) {
    var chatBox = document.getElementById('chatBox');
    var isMine = msg.sender === username;
    var avatarChar = msg.sender.charAt(0).toUpperCase();
    
    // Nội dung
    var contentHtml = '';
    if (msg.type === 'IMAGE') {
        contentHtml = `<img src="${msg.mediaUrl}" onclick="viewImage(this.src)" style="max-width:200px; border-radius:10px; margin-top:5px; cursor:zoom-in;">`;
    } else if (msg.type === 'STICKER') {
        contentHtml = `<img src="${msg.mediaUrl}" style="width:100px; height:auto; margin-top:5px;">`;
    } else {
        contentHtml = `<div class="msg-bubble">${msg.content}</div>`;
    }

    // Reply
    var replyHtml = '';
    if (msg.replyTo) {
        replyHtml = `
            <div class="msg-reply-quote" style="font-size:0.75rem; color:#aaa; margin-bottom:4px; padding-left:8px; border-left:3px solid #e50914; opacity:0.8;">
                <i class="fas fa-reply"></i> <b>${msg.replyTo.sender}</b>: ${msg.replyTo.type === 'IMAGE' ? 'Hình ảnh' : msg.replyTo.content}
            </div>
        `;
    }

    var html = `
        <div class="msg-container ${isMine ? 'mine' : 'other'} fade-in" id="msg-${msg.id}">
            <div class="avatar">${avatarChar}</div>
            <div style="max-width:100%; display:flex; flex-direction:column; ${isMine ? 'align-items:flex-end' : 'align-items:flex-start'}">
                ${replyHtml}
                ${contentHtml}
                <div class="msg-meta">
                    ${msg.timestamp} 
                    ${!isMine ? `<i class="fas fa-reply ms-2" onclick="startReply('${msg.id}', '${msg.sender}', '${msg.type === 'IMAGE' ? '[Hình ảnh]' : msg.content}')" style="cursor:pointer; opacity:0.6;"></i>` : ''}
                </div>
            </div>
        </div>
    `;
    
    var div = document.createElement('div');
    div.innerHTML = html;
    chatBox.appendChild(div);
    chatBox.scrollTop = chatBox.scrollHeight;
}

function uploadImage() {
    var fileInput = document.getElementById('imageInput');
    var file = fileInput.files[0];
    if (!file) return;

    // Show loading giả lập
    var chatBox = document.getElementById('chatBox');
    var loadingDiv = document.createElement('div');
    loadingDiv.className = 'text-center text-muted small';
    loadingDiv.innerText = 'Đang tải ảnh lên...';
    chatBox.appendChild(loadingDiv);

    var formData = new FormData();
    formData.append("file", file);

    fetch('/api/upload/image', {
        method: 'POST',
        body: formData
    })
    .then(response => {
        if(!response.ok) throw new Error("Upload failed");
        return response.json();
    })
    .then(data => {
        loadingDiv.remove();
        if (data.url) {
            var msg = {
                sender: username,
                type: 'IMAGE',
                mediaUrl: data.url,
                replyTo: currentReply
            };
            stompClient.send("/app/party/" + roomId + "/chat", {}, JSON.stringify(msg));
            cancelReply();
        }
    })
    .catch(error => {
        loadingDiv.remove();
        alert("Lỗi upload ảnh: " + error);
    });
}

function sendSticker(url) {
    var msg = {
        sender: username,
        type: 'STICKER',
        mediaUrl: url,
        replyTo: currentReply
    };
    stompClient.send("/app/party/" + roomId + "/chat", {}, JSON.stringify(msg));
    cancelReply();
    
    // Đóng dropdown sau khi chọn
    var dropdownBtn = document.querySelector('.dropup button');
    if(dropdownBtn) dropdownBtn.click();
}

// --- CORE UTILS (SYNC, SEARCH...) ---

function startReply(msgId, sender, content) {
    currentReply = { id: msgId, sender: sender, content: content };
    document.getElementById('replyPreview').style.display = 'block';
    document.getElementById('replyTargetUser').innerText = sender;
    document.getElementById('replyContent').innerText = content;
    document.getElementById('msgInput').focus();
}

function cancelReply() {
    currentReply = null;
    document.getElementById('replyPreview').style.display = 'none';
}

function sendChat() {
    var input = document.getElementById('msgInput');
    var val = input.value.trim();
    if (!val) return;
    
    var msg = { 
        sender: username, 
        content: val, 
        type: 'CHAT',
        replyTo: currentReply 
    };
    stompClient.send("/app/party/" + roomId + "/chat", {}, JSON.stringify(msg));
    input.value = '';
    cancelReply();
}

function handleEnter(e) { if(e.key === 'Enter') sendChat(); }

function showFloatingBubble(msg) {
    var floatArea = document.getElementById('floatArea');
    var el = document.createElement('div');
    el.className = 'float-msg';
    var content = msg.type === 'IMAGE' ? '📷 [Hình ảnh]' : (msg.type === 'STICKER' ? '😊 [Sticker]' : msg.content);
    el.innerHTML = `
        <div class="avatar" style="width:25px;height:25px;font-size:0.7rem">${msg.sender.charAt(0)}</div>
        <span>${content}</span>
    `;
    floatArea.appendChild(el);
    setTimeout(() => el.remove(), 7000);
}

function sendReaction(emoji) {
    var msg = { sender: username, content: emoji, type: 'REACTION' };
    stompClient.send("/app/party/" + roomId + "/chat", {}, JSON.stringify(msg));
}

function showFloatingEmoji(emoji) {
    var container = document.getElementById('emojiContainer');
    var el = document.createElement('div');
    el.className = 'fly-emoji';
    el.innerText = emoji;
    el.style.right = Math.random() * 80 + 'px';
    container.appendChild(el);
    setTimeout(() => el.remove(), 2000);
}

// VIDEO PLAYER
var video = document.getElementById('partyPlayer');
if (isHost) {
    ['play', 'pause', 'seeked'].forEach(event => {
        video.addEventListener(event, () => {
            if(!isSyncing) sendSync(event.toUpperCase());
        });
    });
}
function sendSync(type) {
    stompClient.send("/app/party/" + roomId + "/sync", {}, JSON.stringify({ type: type, currentTime: video.currentTime, sender: username }));
}
function handleVideoSync(action) {
    isSyncing = true;
    if (Math.abs(video.currentTime - action.currentTime) > 2) video.currentTime = action.currentTime;
    if (action.type === 'PLAY') video.play().catch(e=>{});
    else if (action.type === 'PAUSE') video.pause();
    setTimeout(() => isSyncing = false, 500);
}
function loadMovie(url, title) {
    document.getElementById('noMovieState').style.display = 'none';
    var v = document.getElementById('partyPlayer');
    v.style.display = 'block';
    v.src = url || "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"; 
    v.play();
}
function toggleSidebar() {
    var sidebar = document.getElementById('sidebar');
    var icon = document.getElementById('toggleIcon');
    isSidebarOpen = !isSidebarOpen;
    if (isSidebarOpen) {
        sidebar.classList.remove('collapsed');
        icon.className = 'fas fa-chevron-right';
    } else {
        sidebar.classList.add('collapsed');
        icon.className = 'fas fa-chevron-left';
    }
}
function toggleBtn(btn) { btn.classList.toggle('active'); }
function openSearchModal() { document.getElementById('searchModal').style.display = 'block'; }
function closeSearchModal() { document.getElementById('searchModal').style.display = 'none'; }
function performSearch() {
    var query = document.getElementById('searchInput').value;
    // Mock Search (Bạn thay bằng API thật sau)
    var mockHtml = `
        <div onclick="selectMovie(1, 'Big Buck Bunny (Demo)', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4')" 
             style="padding:10px; border-bottom:1px solid #333; cursor:pointer; color:white;">
            <b>🎬 Big Buck Bunny (Demo)</b><br><small>Test Video</small>
        </div>
    `;
    document.getElementById('searchResults').innerHTML = mockHtml;
}
function selectMovie(id, title, url) {
    // [FIX] Lấy poster từ backend hoặc dùng placeholder
    fetch(`/api/movie/${id}/info`)
        .then(res => res.json())
        .then(data => {
            const poster = data.poster || '/images/placeholder.jpg';
            stompClient.send("/app/party/" + roomId + "/changeMovie", {}, 
                JSON.stringify({ id: id, title: title, url: url, poster: poster }));
            closeSearchModal();
        })
        .catch(() => {
            stompClient.send("/app/party/" + roomId + "/changeMovie", {}, 
                JSON.stringify({ id: id, title: title, url: url, poster: '/images/placeholder.jpg' }));
            closeSearchModal();
        });
}
// View Full Image
window.viewImage = function(src) {
    var w = window.open("", "_blank");
    w.document.write(`<img src="${src}" style="width:100%">`);
}

function showWaitingList() {
    const users = [...document.querySelectorAll('#waitingNotif')]; // Mock - cần gọi API thực
    alert("Danh sách chờ đang được phát triển");
}
// [THAY THẾ] Hàm debounceRoomSearch và performRoomSearch cũ bằng logic mới này

let roomSearchTimeout;
function debounceRoomSearch() {
    clearTimeout(roomSearchTimeout);
    roomSearchTimeout = setTimeout(performRoomSearch, 500);
}

function performRoomSearch() {
    const query = document.getElementById('searchInput').value.trim();
    const container = document.getElementById('searchResults');
    
    if (query.length < 2) {
        container.innerHTML = '<div style="text-align:center; color:#666; grid-column: 1/-1;">Nhập ít nhất 2 ký tự...</div>';
        return;
    }
    
    container.innerHTML = '<div style="text-align:center; color:#fff; grid-column: 1/-1;"><i class="fas fa-spinner fa-spin fa-2x"></i></div>';

    fetch(`/api/movie/search-db?query=${encodeURIComponent(query)}`)
        .then(res => res.json())
        .then(results => {
            if (!results || results.length === 0) {
                container.innerHTML = '<div style="text-align:center; color:#888; grid-column: 1/-1;">Không tìm thấy phim nào.</div>';
                return;
            }

            // Render Movie Cards (Reuse style from Homepage)
            const html = results.map(movie => createRoomMovieCard(movie)).join('');
            container.innerHTML = html;
        })
        .catch(err => {
            console.error(err);
            container.innerHTML = '<div style="text-align:center; color:red; grid-column: 1/-1;">Lỗi tìm kiếm.</div>';
        });
}

// Hàm tạo HTML Card tương thích với Hover Card CSS
function createRoomMovieCard(movie) {
    const safeTitle = (movie.title || '').replace(/"/g, '&quot;');
    const poster = movie.poster || '/images/placeholder.jpg';
    const backdrop = movie.backdrop || poster; // Fallback
    const year = (movie.year || '').substring(0, 4);
    const rating = movie.rating || 'N/A';
    const overview = (movie.overview || 'Chưa có mô tả').substring(0, 100) + '...';
    
    // Nút Play ở đây sẽ gọi Socket thay vì chuyển trang
    // Nút Reaction ở đây sẽ mở emoji picker (nếu cần)

    return `
    <div class="movie-card" style="animation: fadeIn 0.3s ease;">
        <div class="movie-poster">
            <img src="${poster}" alt="${safeTitle}" onerror="this.src='/images/placeholder.jpg'">
        </div>
        <div class="movie-info">
            <h3>${safeTitle}</h3>
            <p class="movie-rating">⭐ ${rating} • ${year}</p>
        </div>

        <div class="movie-hover-card">
            <div class="hover-card-media">
                <img class="hover-card-image" src="${backdrop}" onerror="this.src='/images/placeholder.jpg'">
                <div class="hover-player-container"></div>
            </div>
            <div class="hover-card-content">
                <div class="hover-card-actions">
                    <button class="hover-play-btn" onclick="selectMovie(${movie.id}, '${safeTitle}', '${movie.url || ''}')">
                        <i class="fas fa-play"></i> Chiếu Ngay
                    </button>
                    <button class="hover-action-icon" onclick="sendReactionInRoom('❤️')"><i class="far fa-heart"></i></button>
                    <button class="hover-action-icon" onclick="sendReactionInRoom('😂')"><i class="far fa-laugh"></i></button>
                </div>
                
                <h3 class="hover-card-title">${safeTitle}</h3>
                <div class="hover-card-meta">
                    <span class="meta-rating">⭐ ${rating}</span>
                    <span class="meta-year">${year}</span>
                    <span class="meta-quality">HD</span>
                </div>
                
                <div class="hover-card-genres">
                    <span class="genre-tag">Hành động</span>
                    <span class="genre-tag">Viễn tưởng</span>
                </div>

                <p class="hover-card-description" style="font-size:0.8rem; color:#bbb;">
                    ${overview}
                </p>
            </div>
        </div>
    </div>
    `;
}

// Helper gửi reaction nhanh từ hover card
function sendReactionInRoom(emoji) {
    stompClient.send("/app/party/" + roomId + "/chat", {}, JSON.stringify({
        sender: username,
        type: 'REACTION',
        content: emoji
    }));
}