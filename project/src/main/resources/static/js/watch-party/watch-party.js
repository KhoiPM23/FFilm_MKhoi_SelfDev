// watch-party.js - Ultimate Version (Giphy Integrated)
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
var roomMembers = {};
var currentWaitingUsers = [];

// --- PEERJS CONFIG (VIDEO CALL) ---
var myPeer = new Peer(undefined, {
    host: 'peerjs-server.herokuapp.com',
    secure: true,
    port: 443
});

myPeer.on('error', function(err) {
    console.warn("PeerJS non-fatal error:", err.type || err);
});

var myStream;
var peers = {}; // Danh sách kết nối
var searchPage = 0; // Pagination cho search

// --- KẾT NỐI SOCKET ---
stompClient.connect({}, function (frame) {
    console.log('Connected to Watch Party WebSocket');
    
    // Gửi tín hiệu JOIN để server thêm vào members hoặc waitingList
    stompClient.send("/app/party/" + roomId + "/join", {}, JSON.stringify({sessionId: sessionId}));

    if (typeof joinStatus !== 'undefined' && joinStatus === 'WAITING') {
        stompClient.subscribe('/topic/party/' + roomId + '/approval/' + sessionId, function(msg) {
            if (msg.body === 'APPROVED') {
                var waitingScreen = document.getElementById('waitingScreen');
                if (waitingScreen) waitingScreen.remove();
                
                var noMovieState = document.getElementById('noMovieState');
                if (noMovieState && (!video || !video.src || video.style.display === 'none')) {
                    noMovieState.style.display = 'block';
                }
                
                initFullFeatures(); 
            } else if (msg.body === 'REJECTED') {
                alert("Yêu cầu vào phòng bị từ chối!");
                window.location.href = "/watch-party";
            }
        });
        return; 
    }
    initFullFeatures();
    
    // Tự động load Sticker từ Tenor
    loadTenorStickers();
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
        window.location.href = "/watch-party";
    });

    // 4. System Events (Join, Leave, Host Changed, Peer Registered, Room Closed)
    stompClient.subscribe('/topic/party/' + roomId + '/system', function (payload) {
        var msg = JSON.parse(payload.body);
        if (msg.type === 'MEMBER_JOINED') {
            roomMembers[msg.sessionId] = {
                sessionId: msg.sessionId,
                userName: msg.userName,
                userId: msg.userId,
                isHost: false
            };
            renderMembersList();
            drawSystemMessage(msg.userName + " đã tham gia phòng chiếu.");
        } else if (msg.type === 'MEMBER_LEFT') {
            delete roomMembers[msg.sessionId];
            renderMembersList();
            drawSystemMessage(msg.userName + " đã rời phòng chiếu.");
            // Cleanup PeerJS call and video
            if (msg.sessionId) {
                var leftPeerId = sessionToPeerId[msg.sessionId];
                if (leftPeerId) {
                    if (peers[leftPeerId]) {
                        try { peers[leftPeerId].close(); } catch(e) {}
                        delete peers[leftPeerId];
                    }
                    var videoEl = document.getElementById('video-' + leftPeerId);
                    if (videoEl && videoEl.parentNode) {
                        videoEl.parentNode.remove();
                    }
                    delete sessionToPeerId[msg.sessionId];
                }
            }
        } else if (msg.type === 'HOST_CHANGED') {
            drawSystemMessage("Chủ phòng mới: " + msg.newHostName);
            Object.values(roomMembers).forEach(m => m.isHost = false);
            if (roomMembers[msg.newHostSessionId]) {
                roomMembers[msg.newHostSessionId].isHost = true;
            }
            renderMembersList();
            if (msg.newHostUserId == userId || msg.newHostSessionId === sessionId || msg.newHostName === username) {
                alert("Chủ phòng đã rời đi. Bạn đã được chọn làm Chủ Phòng mới!");
                window.location.reload();
            } else {
                showFloatingBubble({sender: "System", content: "Chủ phòng mới: " + msg.newHostName, type: "CHAT"});
            }
        } else if (msg.type === 'ROOM_CLOSED') {
            alert(msg.message || "Chủ phòng đã giải tán phòng chiếu.");
            window.location.href = "/watch-party";
        } else if (msg.type === 'PEER_REGISTERED') {
            sessionToPeerId[msg.sessionId] = msg.peerId;
            if (msg.sessionId !== sessionId && myStream && msg.peerId) {
                setTimeout(() => connectToNewUser(msg.peerId, myStream), 1000);
            }
        }
    });

    // Nhận danh sách members hiện tại
    stompClient.subscribe('/topic/party/' + roomId + '/members/' + sessionId, function (payload) {
        var members = JSON.parse(payload.body);
        roomMembers = {};
        members.forEach(member => {
            roomMembers[member.sessionId] = member;
            if (member.sessionId !== sessionId && member.peerId) {
                sessionToPeerId[member.sessionId] = member.peerId;
            }
        });
        renderMembersList();
    });

    // Subscribe waiting list updates (Host only)
    if (isHost) {
        stompClient.subscribe('/topic/party/' + roomId + '/waitingUpdate', (payload) => {
            try {
                currentWaitingUsers = JSON.parse(payload.body) || [];
                updateWaitingNotifUI();
            } catch(e) {}
        });
        stompClient.send("/app/party/" + roomId + "/waitingList", {}, {});
    }
}

var sessionToPeerId = {}; // Map sessionId -> peerId

// --- WEBSOCKET & PEERJS LINK ---
myPeer.on('open', id => {
    console.log("My PeerJS ID is: " + id);
    if (stompClient && stompClient.connected) {
        stompClient.send("/app/party/" + roomId + "/webrtc/register", {}, JSON.stringify({peerId: id}));
    } else {
        var checkStomp = setInterval(() => {
            if (stompClient && stompClient.connected) {
                stompClient.send("/app/party/" + roomId + "/webrtc/register", {}, JSON.stringify({peerId: id}));
                clearInterval(checkStomp);
            }
        }, 500);
    }
});

// Nhận cuộc gọi
myPeer.on('call', call => {
    if (!myStream) {
        navigator.mediaDevices.getUserMedia({ video: false, audio: true }).then(stream => {
            myStream = stream;
            answerCall(call);
        }).catch(err => {
            console.error("Could not get media for answering call", err);
            call.answer();
            handleIncomingStream(call);
        });
    } else {
        answerCall(call);
    }
});

function answerCall(call) {
    call.answer(myStream);
    handleIncomingStream(call);
}

function handleIncomingStream(call) {
    const video = document.createElement('video');
    video.id = 'video-' + call.peer;
    call.on('stream', userVideoStream => {
        addVideoStream(video, userVideoStream);
    });
    call.on('close', () => {
        if (video.parentNode) video.parentNode.remove();
    });
    peers[call.peer] = call;
}

function connectToNewUser(userId, stream) {
    if (peers[userId]) return;
    const call = myPeer.call(userId, stream);
    const video = document.createElement('video');
    video.id = 'video-' + userId;
    call.on('stream', userVideoStream => {
        addVideoStream(video, userVideoStream);
    });
    call.on('close', () => {
        if (video.parentNode) video.parentNode.remove();
    });
    peers[userId] = call;
}

function addVideoStream(video, stream) {
    video.srcObject = stream;
    video.addEventListener('loadedmetadata', () => { video.play().catch(e=>{}); });
    
    if (!document.getElementById(video.id)) {
        const div = document.createElement('div');
        div.className = 'user-cam';
        div.appendChild(video);
        document.getElementById('videoGrid').appendChild(div);
    }
}

// --- CAM/MIC CONTROLS ---
function toggleCam() {
    const btn = document.getElementById('btnCam');
    const container = document.getElementById('localCamContainer');
    const localVideo = document.getElementById('localVideo');
    
    if (btn.classList.contains('active')) {
        // Tắt Cam (disable video track only, DO NOT destroy audio)
        btn.classList.remove('active');
        btn.innerHTML = '<i class="fas fa-video-slash"></i>';
        if (myStream) {
            myStream.getVideoTracks().forEach(track => { track.enabled = false; });
        }
        if (container) container.style.display = 'none';
    } else {
        // Bật Cam
        if (myStream && myStream.getVideoTracks().length > 0) {
            myStream.getVideoTracks().forEach(track => { track.enabled = true; });
            if (localVideo) {
                localVideo.srcObject = myStream;
                localVideo.muted = true;
            }
            if (container) container.style.display = 'block';
            btn.classList.add('active');
            btn.innerHTML = '<i class="fas fa-video"></i>';
        } else {
            navigator.mediaDevices.getUserMedia({ video: true, audio: true }).then(stream => {
                myStream = stream;
                if (localVideo) {
                    localVideo.srcObject = stream;
                    localVideo.muted = true;
                }
                if (container) container.style.display = 'block';
                btn.classList.add('active');
                btn.innerHTML = '<i class="fas fa-video"></i>';
                
                Object.keys(peers).forEach(peerId => {
                    connectToNewUser(peerId, myStream);
                });
            }).catch(err => {
                console.warn("Could not access camera:", err);
                alert("Không thể truy cập camera: " + err.message);
            });
        }
    }
}

function toggleMic() {
    const btn = document.getElementById('btnMic');
    if (myStream && myStream.getAudioTracks().length > 0) {
        const audioTrack = myStream.getAudioTracks()[0];
        audioTrack.enabled = !audioTrack.enabled;
        if (audioTrack.enabled) {
            btn.classList.add('active-mic');
            btn.innerHTML = '<i class="fas fa-microphone"></i>';
        } else {
            btn.classList.remove('active-mic');
            btn.innerHTML = '<i class="fas fa-microphone-slash"></i>';
        }
    } else {
        navigator.mediaDevices.getUserMedia({ audio: true }).then(stream => {
            if (myStream) {
                stream.getAudioTracks().forEach(t => myStream.addTrack(t));
            } else {
                myStream = stream;
            }
            btn.classList.add('active-mic');
            btn.innerHTML = '<i class="fas fa-microphone"></i>';
        }).catch(err => {
            console.warn("Could not access microphone:", err);
            alert("Không thể truy cập micro: " + err.message);
        });
    }
}

// --- TENOR INTEGRATION (TỰ ĐỘNG FILL) ---
function loadTenorStickers() {
    const container = document.getElementById('stickerList');
    container.innerHTML = '<div class="text-center w-100 text-muted"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';

    // Gọi API Tenor Trending Stickers qua backend proxy
    fetch('/api/tenor/trending?limit=20')
    .then(res => res.json())
    .then(data => {
        if (data.results && data.results.length > 0) {
            renderStickers(data.results.map(item => item.media_formats?.gif?.url || item.media_formats?.tinygif?.url).filter(url => url));
        } else {
            renderStickers(BACKUP_STICKERS); // Fallback
        }
    })
    .catch(err => {
        console.warn("Tenor API Error (Dùng backup):", err);
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

function escapeHtml(unsafe) {
    if (!unsafe) return "";
    return unsafe
         .toString()
         .replace(/&/g, "&amp;")
         .replace(/</g, "&lt;")
         .replace(/>/g, "&gt;")
         .replace(/"/g, "&quot;")
         .replace(/'/g, "&#039;");
}

function drawMessage(msg) {
    var chatBox = document.getElementById('chatBox');
    var isMine = msg.sender === username;
    
    // Xử lý XSS
    var safeSender = escapeHtml(msg.sender);
    var safeContent = escapeHtml(msg.content);
    var avatarChar = safeSender.charAt(0).toUpperCase();
    
    // Nội dung
    var contentHtml = '';
    if (msg.type === 'IMAGE' || msg.type === 'STICKER') {
        var safeUrl = msg.mediaUrl;
        if (safeUrl && (safeUrl.startsWith('http://') || safeUrl.startsWith('https://') || safeUrl.startsWith('/'))) {
            var escapedUrl = escapeHtml(safeUrl);
            if (msg.type === 'IMAGE') {
                contentHtml = `<img src="${escapedUrl}" onclick="viewImage(this.src)" style="max-width:200px; border-radius:10px; margin-top:5px; cursor:zoom-in;">`;
            } else {
                contentHtml = `<img src="${escapedUrl}" style="width:100px; height:auto; margin-top:5px;">`;
            }
        } else {
            contentHtml = `<div class="msg-bubble text-danger">[Hình ảnh không hợp lệ]</div>`;
        }
    } else {
        contentHtml = `<div class="msg-bubble">${safeContent}</div>`;
    }

    // Reply
    var replyHtml = '';
    if (msg.replyTo) {
        var safeReplySender = escapeHtml(msg.replyTo.sender);
        var safeReplyContent = escapeHtml(msg.replyTo.content);
        replyHtml = `
            <div class="msg-reply-quote" style="font-size:0.75rem; color:#aaa; margin-bottom:4px; padding-left:8px; border-left:3px solid #e50914; opacity:0.8;">
                <i class="fas fa-reply"></i> <b>${safeReplySender}</b>: ${msg.replyTo.type === 'IMAGE' ? 'Hình ảnh' : safeReplyContent}
            </div>
        `;
    }

    // Escape cho onclick handler
    var safeSenderForJS = safeSender.replace(/'/g, "\\'");
    var safeContentForJS = safeContent.replace(/'/g, "\\'");

    var html = `
        <div class="msg-container ${isMine ? 'mine' : 'other'} fade-in" id="msg-${msg.id}">
            <div class="avatar">${avatarChar}</div>
            <div style="max-width:100%; display:flex; flex-direction:column; ${isMine ? 'align-items:flex-end' : 'align-items:flex-start'}">
                ${replyHtml}
                ${contentHtml}
                <div class="msg-meta">
                    ${escapeHtml(msg.timestamp)} 
                    ${!isMine ? `<i class="fas fa-reply ms-2" onclick="startReply('${msg.id}', '${safeSenderForJS}', '${msg.type === 'IMAGE' ? '[Hình ảnh]' : safeContentForJS}')" style="cursor:pointer; opacity:0.6;"></i>` : ''}
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
    var safeContent = msg.type === 'IMAGE' ? '📷 [Hình ảnh]' : (msg.type === 'STICKER' ? '😊 [Sticker]' : escapeHtml(msg.content));
    var safeSender = escapeHtml(msg.sender);
    el.innerHTML = `
        <div class="avatar" style="width:25px;height:25px;font-size:0.7rem">${safeSender.charAt(0)}</div>
        <span>${safeContent}</span>
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

// VIDEO PLAYER & SYNCHRONIZATION
var video = document.getElementById('partyPlayer');
var seekDebounceTimer = null;

if (isHost && video) {
    video.addEventListener('play', function() {
        if (!isSyncing) sendSync('PLAY');
    });
    video.addEventListener('pause', function() {
        if (!isSyncing) sendSync('PAUSE');
    });
    video.addEventListener('seeked', function() {
        if (!isSyncing) {
            clearTimeout(seekDebounceTimer);
            seekDebounceTimer = setTimeout(function() {
                sendSync('SEEK');
            }, 150);
        }
    });

    // Periodic heartbeat every 5s while playing to keep all members tightly synced
    setInterval(function() {
        if (video && !video.paused && !isSyncing && stompClient && stompClient.connected) {
            sendSync('HEARTBEAT');
        }
    }, 5000);
}

function sendSync(type) {
    if (!stompClient || !stompClient.connected) return;
    stompClient.send("/app/party/" + roomId + "/sync", {}, JSON.stringify({
        type: type,
        currentTime: video.currentTime,
        sender: username,
        playbackStatus: video.paused ? 'PAUSE' : 'PLAY'
    }));
}

function handleVideoSync(action) {
    if (isHost || !video) return;
    isSyncing = true;
    
    var targetTime = typeof action.currentTime === 'number' ? action.currentTime : parseFloat(action.currentTime);
    
    if (action.type === 'PLAY') {
        if (!isNaN(targetTime) && Math.abs(video.currentTime - targetTime) > 1.5) {
            video.currentTime = targetTime;
        }
        video.play().catch(function(e) { console.warn("Autoplay blocked:", e); });
    } else if (action.type === 'PAUSE') {
        video.pause();
        if (!isNaN(targetTime) && Math.abs(video.currentTime - targetTime) > 0.5) {
            video.currentTime = targetTime;
        }
    } else if (action.type === 'SEEK' || action.type === 'SEEKED') {
        if (!isNaN(targetTime)) {
            video.currentTime = targetTime;
        }
        if (action.playbackStatus === 'PLAY') {
            video.play().catch(function(e) {});
        } else if (action.playbackStatus === 'PAUSE') {
            video.pause();
        }
    } else if (action.type === 'HEARTBEAT') {
        // Continuous smooth drift correction
        if (!isNaN(targetTime) && Math.abs(video.currentTime - targetTime) > 2.0) {
            video.currentTime = targetTime;
        }
        if (action.playbackStatus === 'PLAY' && video.paused) {
            video.play().catch(function(e) {});
        } else if (action.playbackStatus === 'PAUSE' && !video.paused) {
            video.pause();
        }
    }
    
    setTimeout(function() { isSyncing = false; }, 300);
}

function loadMovie(url, title) {
    var noMovieState = document.getElementById('noMovieState');
    if (noMovieState) noMovieState.style.display = 'none';
    var v = document.getElementById('partyPlayer');
    if (v) {
        v.style.display = 'block';
        v.src = url || "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"; 
        v.play().catch(function(e) { console.warn("Video play error:", e); });
    }
}

// --- SYSTEM MESSAGES & SIDEBAR TABS ---
function drawSystemMessage(text) {
    var chatBox = document.getElementById('chatBox');
    if (!chatBox) return;
    var div = document.createElement('div');
    div.style.cssText = "text-align:center; margin: 8px 0; font-size: 0.8rem; color: #888; font-style: italic;";
    div.innerHTML = `<span style="background: rgba(255,255,255,0.08); padding: 3px 12px; border-radius: 12px;"><i class="fas fa-info-circle me-1 text-danger"></i>${escapeHtml(text)}</span>`;
    chatBox.appendChild(div);
    chatBox.scrollTop = chatBox.scrollHeight;
}

window.switchSidebarTab = function(tabName) {
    var chatTab = document.getElementById('chatTabContent');
    var membersTab = document.getElementById('membersTabContent');
    var tabChatBtn = document.getElementById('tabChatBtn');
    var tabMembersBtn = document.getElementById('tabMembersBtn');
    
    if (tabName === 'chat') {
        if (chatTab) chatTab.style.display = 'flex';
        if (membersTab) membersTab.style.display = 'none';
        if (tabChatBtn) {
            tabChatBtn.classList.add('active');
            tabChatBtn.style.color = '#fff';
            tabChatBtn.style.borderBottom = '2px solid #e50914';
        }
        if (tabMembersBtn) {
            tabMembersBtn.classList.remove('active');
            tabMembersBtn.style.color = '#888';
            tabMembersBtn.style.borderBottom = '2px solid transparent';
        }
    } else {
        if (chatTab) chatTab.style.display = 'none';
        if (membersTab) membersTab.style.display = 'flex';
        if (tabMembersBtn) {
            tabMembersBtn.classList.add('active');
            tabMembersBtn.style.color = '#fff';
            tabMembersBtn.style.borderBottom = '2px solid #e50914';
        }
        if (tabChatBtn) {
            tabChatBtn.classList.remove('active');
            tabChatBtn.style.color = '#888';
            tabChatBtn.style.borderBottom = '2px solid transparent';
        }
    }
};

function renderMembersList() {
    var container = document.getElementById('membersContainer');
    var countBadge = document.getElementById('memberCountBadge');
    if (!container) return;
    
    var members = Object.values(roomMembers);
    if (countBadge) countBadge.innerText = members.length;
    
    if (members.length === 0) {
        container.innerHTML = '<div class="text-muted small text-center py-3">Không có thành viên.</div>';
        return;
    }
    
    var html = '';
    members.forEach(function(m) {
        var isThisMe = m.sessionId === sessionId || m.userName === username;
        var safeName = escapeHtml(m.userName || 'User');
        var avatarChar = safeName.charAt(0).toUpperCase();
        var isMemberHost = m.isHost || (m.userId && m.userId == userId && isHost);
        
        html += `
            <div style="display:flex; justify-content:space-between; align-items:center; background:#1c1c1c; padding:8px 12px; border-radius:8px; border:1px solid #2a2a2a;">
                <div style="display:flex; align-items:center; gap:10px;">
                    <div class="avatar" style="width:30px; height:30px; font-size:0.75rem;">${avatarChar}</div>
                    <div>
                        <div style="font-weight:600; font-size:0.9rem; color:#fff;">
                            ${safeName} ${isThisMe ? '<span style="font-size:0.75rem; color:#888;">(Bạn)</span>' : ''}
                        </div>
                        ${isMemberHost ? '<span style="font-size:0.75rem; color:#ffd700;"><i class="fas fa-crown"></i> Chủ phòng</span>' : '<span style="font-size:0.75rem; color:#888;">Thành viên</span>'}
                    </div>
                </div>
                ${(isHost && !isThisMe) ? `
                    <button onclick="kickUser('${escapeHtml(m.sessionId)}')" style="background:rgba(229,9,20,0.15); border:1px solid #e50914; color:#ff4d5a; padding:4px 8px; border-radius:4px; font-size:0.75rem; cursor:pointer;" title="Mời ra khỏi phòng">
                        <i class="fas fa-user-times"></i> Kick
                    </button>
                ` : ''}
            </div>
        `;
    });
    container.innerHTML = html;
}

// --- WAITING LIST & MODERATION (HOST) ---
function updateWaitingNotifUI() {
    var notif = document.getElementById('waitingNotif');
    var countSpan = document.getElementById('waitingCount');
    if (!notif) return;
    if (currentWaitingUsers && currentWaitingUsers.length > 0) {
        notif.style.display = 'block';
        if (countSpan) countSpan.innerText = currentWaitingUsers.length;
        renderWaitingList();
    } else {
        notif.style.display = 'none';
        closeWaitingListModal();
    }
}

window.showWaitingList = function() {
    var modal = document.getElementById('waitingListModal');
    if (modal) modal.style.display = 'flex';
    renderWaitingList();
};

window.closeWaitingListModal = function() {
    var modal = document.getElementById('waitingListModal');
    if (modal) modal.style.display = 'none';
};

function renderWaitingList() {
    var list = document.getElementById('waitingUsersList');
    if (!list) return;
    if (!currentWaitingUsers || currentWaitingUsers.length === 0) {
        list.innerHTML = '<div class="text-center text-muted py-4">Không có người nào đang chờ duyệt.</div>';
        return;
    }
    
    var html = '';
    currentWaitingUsers.forEach(function(u) {
        var safeName = escapeHtml(u.userName || 'Người dùng');
        var safeSessionId = escapeHtml(u.sessionId);
        html += `
            <div style="display:flex; justify-content:space-between; align-items:center; background:#222; padding:10px 14px; border-radius:8px; border:1px solid #333;">
                <div style="display:flex; align-items:center; gap:10px;">
                    <div class="avatar" style="width:32px; height:32px; font-size:0.75rem;">${safeName.charAt(0).toUpperCase()}</div>
                    <span style="font-weight:600;">${safeName}</span>
                </div>
                <div style="display:flex; gap:6px;">
                    <button onclick="approveUser('${safeSessionId}')" style="background:#4cd137; border:none; color:#fff; padding:5px 12px; border-radius:6px; font-size:0.8rem; cursor:pointer; font-weight:bold;">
                        <i class="fas fa-check"></i> Duyệt
                    </button>
                    <button onclick="rejectUser('${safeSessionId}')" style="background:#e50914; border:none; color:#fff; padding:5px 12px; border-radius:6px; font-size:0.8rem; cursor:pointer;">
                        <i class="fas fa-times"></i> Từ chối
                    </button>
                </div>
            </div>
        `;
    });
    list.innerHTML = html;
}

window.approveUser = function(targetSessionId) {
    if (stompClient && stompClient.connected) {
        stompClient.send("/app/party/" + roomId + "/admin/approve", {}, JSON.stringify({sessionId: targetSessionId}));
    }
};

window.rejectUser = function(targetSessionId) {
    if (stompClient && stompClient.connected) {
        stompClient.send("/app/party/" + roomId + "/admin/reject", {}, JSON.stringify({sessionId: targetSessionId}));
    }
};

window.kickUser = function(targetSessionId) {
    if (confirm("Bạn có chắc muốn mời người này ra khỏi phòng chiếu?")) {
        if (stompClient && stompClient.connected) {
            stompClient.send("/app/party/" + roomId + "/admin/kick", {}, JSON.stringify({sessionId: targetSessionId}));
        }
    }
};

window.closeRoom = function() {
    if (confirm("Bạn có chắc chắn muốn giải tán phòng chiếu này? Mọi người sẽ bị ngắt kết nối.")) {
        if (stompClient && stompClient.connected) {
            stompClient.send("/app/party/" + roomId + "/admin/close", {}, {});
        }
        setTimeout(() => { window.location.href = '/watch-party'; }, 400);
    }
};

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
    var mockHtml = `
        <div onclick="selectMovie(1, 'Big Buck Bunny (Demo)', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4')" 
             style="padding:10px; border-bottom:1px solid #333; cursor:pointer; color:white;">
            <b>🎬 Big Buck Bunny (Demo)</b><br><small>Test Video</small>
        </div>
    `;
    document.getElementById('searchResults').innerHTML = mockHtml;
}
function selectMovie(id, title, url) {
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

// Clean up WebRTC on page unload
window.addEventListener('beforeunload', function() {
    if (myStream) {
        myStream.getTracks().forEach(t => { try { t.stop(); } catch(e){} });
    }
    Object.values(peers).forEach(call => {
        try { call.close(); } catch(e){}
    });
});
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