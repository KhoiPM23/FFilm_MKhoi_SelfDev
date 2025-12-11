/**
 * MESSENGER VIPRO - HYBRID VERSION
 * UI: Chuẩn file cũ (Đẹp, đúng CSS)
 * Logic: Nâng cấp Realtime, Media, Stranger
 */
(function() {
    'use strict';

    // Fallback for global UI helpers in case script order changes during development.
    if (typeof window.showToast !== 'function') {
        window.showToast = function(message, type='info') {
            // Minimal non-blocking fallback: log to console so code that calls showToast doesn't throw.
            console.log('[showToast - fallback]', type, message);
        };
    }

    // --- KHAI BÁO BIẾN ---
    let stompClient = null;
    let currentPartnerId = null;
    let currentPartnerName = '';
    let isCurrentPartnerFriend = false; // Biến check trạng thái bạn bè
    
    // Media
    let mediaRecorder = null;
    let audioChunks = [];
    let isRecording = false;
    let recordingTimer = null;
    let recordingStartTime = 0;
    let pendingFile = null; // Lưu file đang chọn để preview
    let emojiPicker = null; // Instance của Emoji Button

    // Call State (PeerJS)
    let myPeer = null;
    let myPeerId = null;
    let currentCall = null;
    let localStream = null;
    let remoteStream = null;
    let callTimerInterval = null;
    let incomingCallData = null; // { peerId, senderId, senderName, senderAvatar }
    let typingTimeout = null;
    let lastSeenMessageId = null;

    let availableCameras = [];
    let currentCameraIndex = 0;

    let messageQueue = [];
    let isProcessingQueue = false;

    const currentUser = window.currentUser || { userID: 0, name: 'Me' };
    const notificationSound = new Audio('/sounds/message-notification.mp3');

    let searchResults = [];
    let currentSearchIndex = -1;

    let selectedMessageToForward = null;
    let forwardTimeout = null;

    // --- CẤU HÌNH STICKER NỘI BỘ ---
    let currentStickerCollection = 'popular';
    let recentStickers = JSON.parse(localStorage.getItem('recentStickers') || '[]');
    let suggestionTimeout = null;
    

    // Config Sticker
    const STICKERS = [
        "https://media.giphy.com/media/l0HlHFRbmaZtBRhXG/giphy.gif",
        "https://media.giphy.com/media/26BRv0ThflsHCqDrG/giphy.gif",
        "https://media.giphy.com/media/3o7TKSjRrfIPjeiVyM/giphy.gif",
        "https://media.giphy.com/media/l0HlI9qB6L8l756z6/giphy.gif"
    ];

    // --- KHỞI TẠO ---
    $(document).ready(function() {
        console.log("Messenger Init Start...");
        connectWebSocket();
        loadConversations();
        bindEvents();
        initStickerMenu();
        initPeerJS();        
        setupStickerSuggestions();
        renderRecentStickers();
        setTimeout(initEmojiPicker, 1000); // Delay xíu để thư viện load
    });

    function bindEvents() {
        // Gửi tin bằng Enter
        $('#msgInput').off('keypress').on('keypress', function(e) {
            if (e.which === 13 && !e.shiftKey) {
                e.preventDefault();
                window.sendTextMessage();
            }
        });

        // [FIX] Typing indicator
        $('#msgInput').off('input').on('input', function() {
            if (!currentPartnerId || !stompClient) return;
            
            clearTimeout(typingTimeout);
            
            stompClient.send('/app/typing', {}, JSON.stringify({
                receiverId: currentPartnerId,
                senderId: currentUser.userID
            }));
            
            typingTimeout = setTimeout(() => {
                stompClient.send('/app/stop-typing', {}, JSON.stringify({
                    receiverId: currentPartnerId
                }));
            }, 2000);
        });

        // Upload ảnh - CHỈ GÁN SỰ KIỆN 1 LẦN
        $('#imageInput').off('change').on('change', function() {
            if (this.files && this.files[0]) {
                window.handleFileSelect(this, 'IMAGE');
            }
        });
        
        // Upload file
        $('#fileInput').off('change').on('change', function() {
            if (this.files && this.files[0]) {
                window.handleFileSelect(this, 'FILE');
            }
        });
        
        // Ghi âm - SỬA: DÙNG NÚT ĐÚNG
        $('#micBtn').off('click').on('click', window.toggleRecording);
        
        // Sticker button với animation
        $('#stickerBtn').off('click').on('click', function() {
            $(this).addClass('active');
            setTimeout(() => $(this).removeClass('active'), 300);
            window.toggleStickers();
        });
        
        // Init sticker suggestions
        initStickerSuggestions();
        
        // Close suggestions khi click outside
        $(document).on('click', function(e) {
            if (!$(e.target).closest('.sticker-suggestions, #msgInput').length) {
                hideStickerSuggestions();
            }
        });
        
        // Nút gửi
        $('#sendBtn').off('click').on('click', window.sendTextMessage);

        // Search conversations
        $('#convSearchInput').off('input').on('input', function() {
            const query = $(this).val().toLowerCase();
            $('.conv-item').each(function() {
                const name = $(this).find('.conv-name').text().toLowerCase();
                $(this).toggle(name.includes(query));
            });
        });
        
        // Emoji
        initEmojiPicker();
    }

    // --- 1. PEERJS SETUP (WEB RTC) ---
    function initPeerJS() {
        if (!window.Peer) {
            console.error('PeerJS library not loaded');
            return;
        }
        
        myPeerId = `user_${currentUser.userID}_${Date.now()}`;
        
        myPeer = new Peer(myPeerId, {
            host: '0.peerjs.com',
            port: 443,
            path: '/',
            secure: true,
            config: {
                'iceServers': [
                    { urls: 'stun:stun.l.google.com:19302' },
                    { urls: 'stun:global.stun.twilio.com:3478' }
                ]
            },
            debug: 0
        });
        
        myPeer.on('open', (id) => {
            console.log('✅ PeerJS Connected. My ID:', id);
        });
        
        myPeer.on('call', (call) => {
            console.log('📞 Incoming call from:', call.peer);
            
            // Answer the call with user's media
            navigator.mediaDevices.getUserMedia({ video: true, audio: true })
                .then(stream => {
                    localStream = stream;
                    document.getElementById('localVideo').srcObject = stream;
                    
                    call.answer(stream);
                    currentCall = call;
                    
                    // Setup call handlers
                    setupCallHandlers(call);
                })
                .catch(err => {
                    console.error('Error accessing media:', err);
                    call.close();
                });
        });
        
        myPeer.on('error', (err) => {
            console.error('PeerJS Error:', err);
            showToast('Lỗi kết nối PeerJS: ' + err.type, 'error');
        });
    }

    // --- 2. LOGIC GỌI ĐIỆN (CALL LOGIC) ---

    // A. Người gọi (Caller)
    window.startVideoCall = function() {
        if (!currentPartnerId) {
            showToast('Vui lòng chọn người để gọi', 'error');
            return;
        }
        
        if (!myPeer || !myPeer.id) {
            showToast('Đang khởi tạo kết nối...', 'info');
            setTimeout(() => startVideoCall(), 1000);
            return;
        }
        
        // Get user media first
        navigator.mediaDevices.getUserMedia({ video: true, audio: true })
            .then(stream => {
                localStream = stream;
                document.getElementById('localVideo').srcObject = stream;
                
                // Show calling UI
                showCallModal(true);
                
                // Send call request via WebSocket
                const callData = {
                    type: 'CALL_REQ',
                    senderId: currentUser.userID,
                    senderName: currentUser.name,
                    senderAvatar: $('#headerAvatar').attr('src'),
                    receiverId: currentPartnerId,
                    peerId: myPeer.id,
                    callType: 'VIDEO',
                    timestamp: new Date().toISOString()
                };
                
                stompClient.send('/app/call', {}, JSON.stringify(callData));
                
                // Start call timeout (30 seconds)
                callTimeout = setTimeout(() => {
                    if (!currentCall) {
                        endCall();
                        showToast('Không có phản hồi từ người nhận', 'error');
                    }
                }, 30000);
                
            })
            .catch(err => {
                console.error('Error accessing media:', err);
                showToast('Không thể truy cập camera/microphone', 'error');
            });
    };

    window.startVoiceCall = function() {
        // Similar to startVideoCall but audio only
        if (!currentPartnerId) return;
        
        navigator.mediaDevices.getUserMedia({ video: false, audio: true })
            .then(stream => {
                localStream = stream;
                
                // Show voice call UI
                showCallModal(false);
                
                const callData = {
                    type: 'CALL_REQ',
                    senderId: currentUser.userID,
                    senderName: currentUser.name,
                    senderAvatar: $('#headerAvatar').attr('src'),
                    receiverId: currentPartnerId,
                    peerId: myPeer.id,
                    callType: 'AUDIO',
                    timestamp: new Date().toISOString()
                };
                
                stompClient.send('/app/call', {}, JSON.stringify(callData));
                
                callTimeout = setTimeout(() => {
                    if (!currentCall) endCall();
                }, 30000);
            })
            .catch(err => {
                console.error('Error accessing microphone:', err);
                showToast('Không thể truy cập microphone', 'error');
            });
    };

    function startCall(type) {
        if (!currentPartnerId || !myPeerId) return alert("Chưa kết nối máy chủ gọi.");
        
        // 1. Gửi tín hiệu yêu cầu gọi qua Socket
        // type: CALL_REQ, content: myPeerId
        const payload = {
            receiverId: currentPartnerId,
            content: myPeerId,
            type: 'CALL_REQ',
            senderId: currentUser.userID,
            senderName: currentUser.name,
            senderAvatar: $('#headerAvatar').attr('src') || null
        };
        console.log("CALL_REQ -> sending", payload);
        sendApiRequest(payload);
        
        // 2. Hiện UI đang gọi
        showCallModal(true, "Đang gọi...", null); // Local stream chưa có, sẽ bật sau khi bên kia bắt máy hoặc bật ngay tùy UX
        
        // UX: Bật camera mình trước để soi gương
        navigator.mediaDevices.getUserMedia({ video: type === 'VIDEO', audio: true })
            .then(stream => {
                localStream = stream;
                document.getElementById('localVideo').srcObject = stream;
            })
            .catch(err => console.error("Lỗi cam:", err));
    }

    // B. Người nhận (Callee) - Xử lý trong handleIncomingMessage
    
    // C. Xử lý chấp nhận/từ chối
    window.acceptCall = function() {
        $('#incomingCallModal').hide();
        
        if (!incomingCallData) return;
        
        const callType = incomingCallData.callType || 'VIDEO';
        
        navigator.mediaDevices.getUserMedia({ 
            video: callType === 'VIDEO', 
            audio: true 
        }).then(stream => {
            localStream = stream;
            document.getElementById('localVideo').srcObject = stream;
            
            // Show call UI
            showCallModal(callType === 'VIDEO');
            
            // Call the other peer
            const call = myPeer.call(incomingCallData.peerId, stream);
            currentCall = call;
            
            setupCallHandlers(call);
            
            // Send call accepted notification
            stompClient.send('/app/call-accepted', {}, JSON.stringify({
                receiverId: incomingCallData.senderId,
                peerId: myPeer.id
            }));
            
        }).catch(err => {
            console.error('Error accessing media:', err);
            showToast('Lỗi truy cập thiết bị', 'error');
            rejectCall();
        });
    };

    window.rejectCall = function() {
        $('#incomingCallModal').hide();
        
        if (incomingCallData) {
            // Gửi thông báo từ chối cuộc gọi
            stompClient.send('/app/call', {}, JSON.stringify({
                type: 'CALL_REJECT',
                receiverId: incomingCallData.senderId,
                senderId: currentUser.userID
            }));
        }
        
        // Dừng âm thanh chuông
        if (incomingCallData && incomingCallData.ringtone) {
            incomingCallData.ringtone.pause();
            incomingCallData.ringtone.currentTime = 0;
        }
        
        incomingCallData = null;
        showToast('Đã từ chối cuộc gọi', 'info');
    };

    window.endCall = function() {
        // Dừng timer
        if (callTimeout) clearTimeout(callTimeout);
        if (callTimerInterval) clearInterval(callTimerInterval);
        
        // Dừng local stream
        if (localStream) {
            localStream.getTracks().forEach(track => track.stop());
            localStream = null;
        }
        
        // Đóng call
        if (currentCall) {
            currentCall.close();
            currentCall = null;
        }
        
        // Gửi thông báo kết thúc cuộc gọi
        if (currentPartnerId) {
            stompClient.send('/app/call', {}, JSON.stringify({
                type: 'CALL_END',
                receiverId: currentPartnerId,
                senderId: currentUser.userID
            }));
        }
        
        // Ẩn modal
        $('#videoCallModal').hide();
        $('#incomingCallModal').hide();
        
        showToast('Đã kết thúc cuộc gọi', 'info');
    };

    function setupCallHandlers(call) {
        call.on('stream', (remoteStream) => {
            remoteStream = remoteStream;
            document.getElementById('remoteVideo').srcObject = remoteStream;
            
            // Update UI - hide avatar, show video
            $('.remote-info-overlay').fadeOut();
            
            // Start call timer
            startCallTimer();
        });
        
        call.on('close', () => {
            endCall();
        });
        
        call.on('error', (err) => {
            console.error('Call error:', err);
            endCall();
            showToast('Cuộc gọi bị lỗi', 'error');
        });
    }

    // --- 1. WEBSOCKET ---
    // --- FIX: WEBSOCKET CONNECTION IMPROVED ---
    function connectWebSocket() {
        const socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        stompClient.debug = null;
        
        const headers = {
            'X-User-Id': currentUser.userID,
            'X-User-Name': currentUser.name
        };
        
        stompClient.connect(headers, function(frame) {
            console.log('✅ WebSocket Connected:', frame);
            
            // Subscribe đến private messages
            stompClient.subscribe(`/user/${currentUser.name}/queue/private`, function(payload) {
                const msg = JSON.parse(payload.body);
                handleSocketMessage(msg);
            });
            
            // Subscribe đến typing notifications
            stompClient.subscribe(`/user/${currentUser.name}/queue/typing`, function(payload) {
                const data = JSON.parse(payload.body);
                if (data.senderId === currentPartnerId) {
                    if (data.type === 'TYPING') {
                        showTypingIndicator();
                    } else {
                        hideTypingIndicator();
                    }
                }
            });
            
            // Subscribe đến seen notifications
            stompClient.subscribe(`/user/${currentUser.name}/queue/seen`, function(payload) {
                const data = JSON.parse(payload.body);
                updateSeenAvatar(data.messageId);
            });
            
            // Subscribe đến online status
            stompClient.subscribe(`/user/${currentUser.name}/queue/online-status`, function(payload) {
                const data = JSON.parse(payload.body);
                updateOnlineStatus(data.userId, data.isOnline, data.lastActive);
            });

            // Lắng nghe cuộc gọi (PeerJS cũng cần socket để signaling ban đầu)
            myPeer.on('call', (call) => {
                // Trường hợp A gọi B -> B Accept -> B gọi A.
                // Lúc này A nhận được cuộc gọi từ B. A phải trả lời (answer)
                navigator.mediaDevices.getUserMedia({ video: true, audio: true })
                    .then(stream => {
                        localStream = stream;
                        document.getElementById('localVideo').srcObject = stream;
                        call.answer(stream); // Trả lời với stream của mình
                        handleCallStream(call);
                    });
            });
            
            // Thông báo kết nối thành công
            showToast("Đã kết nối thời gian thực", "success");
            
        }, function(error) {
            console.error('WebSocket Error:', error);
            setTimeout(connectWebSocket, 5000); // Reconnect sau 5s
        });
    }

    // --- FIX: TIMESTAMP THÔNG MINH ---
    function formatSmartTimestamp(timestamp) {
        if (!timestamp) return "";
        
        const now = new Date();
        const msgDate = new Date(timestamp);
        const diffMs = now - msgDate;
        const diffMins = Math.floor(diffMs / 60000);
        const diffHours = Math.floor(diffMs / 3600000);
        const diffDays = Math.floor(diffMs / 86400000);
        
        // Cùng ngày: chỉ hiện giờ
        if (diffDays === 0) {
            return msgDate.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
        }
        // Hôm qua
        else if (diffDays === 1) {
            return `Hôm qua ${msgDate.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}`;
        }
        // Trong tuần
        else if (diffDays < 7) {
            const days = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
            return `${days[msgDate.getDay()]} ${msgDate.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}`;
        }
        // Trong năm
        else if (msgDate.getFullYear() === now.getFullYear()) {
            return `${msgDate.getDate()}/${msgDate.getMonth() + 1} ${msgDate.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}`;
        }
        // Năm khác
        else {
            return `${msgDate.getDate()}/${msgDate.getMonth() + 1}/${msgDate.getFullYear()} ${msgDate.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}`;
        }
    }

    function handleSocketMessage(msg) {
        console.log("Socket message received:", msg);
        
        // 1. Xử lý Tín hiệu Gọi
        if (msg.type === 'CALL_REQ') {
            incomingCallData = { 
                peerId: msg.content,
                senderId: msg.senderId,
                senderName: msg.senderName || 'Người dùng',
                senderAvatar: msg.senderAvatar 
            };
            showIncomingCallModal(incomingCallData);
            return;
        }
        else if (msg.type === 'CALL_DENY') {
            alert("Người dùng bận hoặc từ chối cuộc gọi.");
            closeCallModal();
            return;
        }
        else if (msg.type === 'CALL_END') {
            closeCallModal();
            return;
        }

        // 2. Chat messages - LUÔN HIỆN NGAY KHI NHẬN
        const senderId = msg.senderId;
        const partnerId = (senderId === currentUser.userID) ? msg.receiverId : senderId;

        // Nếu đang xem chat này, append ngay
        if (currentPartnerId && currentPartnerId == partnerId) {
            appendMessageToUI(msg);
            if (senderId != currentUser.userID) {
                markAsRead(msg.id);
            }
            
            // Phát âm thanh thông báo
            notificationSound.play().catch(() => {});
        }

        // Cập nhật conversation list mà không reload
        updateConversationPreview(msg);
    }

    // --- FIX: SEEN REAL-TIME ---
    function markAsRead(messageId) {
        if (!stompClient || !stompClient.connected) return;
        
        stompClient.send('/app/mark-seen', {}, JSON.stringify({
            messageId: messageId,
            userId: currentUser.userID,
            partnerId: currentPartnerId
        }));
    }

    // --- 4. UI HELPERS ---
    function showIncomingCallModal(data) {
        $('#incomingAvatar').attr('src', data.senderAvatar);
        $('#incomingName').text(data.senderName);
        $('#incomingCallType').text(data.callType === 'VIDEO' ? 'Cuộc gọi video' : 'Cuộc gọi thoại');
        
        $('#incomingCallModal').show();
        
        // Play ringtone
        const ringtone = new Audio('/sounds/ringtone.mp3');
        ringtone.loop = true;
        ringtone.play().catch(() => {});
        
        // Store for later use
        incomingCallData = data;
        incomingCallData.ringtone = ringtone;
    }

    function showCallModal(isVideo) {
        const modal = $('#videoCallModal');
        const partnerAvatar = $('#headerAvatar').attr('src');
        const partnerName = currentPartnerName;
        
        // Set partner info
        $('#callPartnerName').text(partnerName);
        $('#callPartnerAvatar').html(`<img src="${partnerAvatar}" alt="${partnerName}">`);
        
        // Set background from avatar
        $('#callBackground').css('background-image', `url(${partnerAvatar})`);
        
        // Show modal
        modal.show();
        
        // Update status
        $('#callStatusText').text(isVideo ? 'Đang gọi...' : 'Đang gọi thoại...');
        $('#callDuration').text('00:00');
    }

    function closeCallModal() {
        $('#videoCallModal').hide();
        $('#incomingCallModal').hide();
        if (localStream) localStream.getTracks().forEach(t => t.stop());
        if (currentCall) currentCall.close();
        localStream = null;
        currentCall = null;
        stopCallTimer();
    }

    function startCallTimer() {
        let seconds = 0;
        
        if (callTimerInterval) clearInterval(callTimerInterval);
        
        callTimerInterval = setInterval(() => {
            seconds++;
            callDuration = seconds;
            
            const minutes = Math.floor(seconds / 60);
            const secs = seconds % 60;
            $('#callDuration').text(`${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`);
            
            // Update status text
            if (seconds < 5) {
                $('#callStatusText').text('Đang kết nối...');
            } else {
                $('#callStatusText').text('Đang trong cuộc gọi');
            }
        }, 1000);
    }

    function stopCallTimer() {
        clearInterval(callTimerInterval);
        $('#callDuration').text("00:00");
    }
    
    // Toggle Cam/Mic
    window.toggleCallMic = function() {
        if (localStream) {
            const audioTrack = localStream.getAudioTracks()[0];
            if (audioTrack) {
                audioTrack.enabled = !audioTrack.enabled;
                const btn = $('#btnToggleMic');
                btn.toggleClass('off');
                btn.find('i').toggleClass('fa-microphone fa-microphone-slash');
                btn.find('.control-label').text(audioTrack.enabled ? 'Tắt mic' : 'Bật mic');
            }
        }
    };

    window.toggleCallCam = function() {
        if (localStream) {
            const videoTrack = localStream.getVideoTracks()[0];
            if (videoTrack) {
                videoTrack.enabled = !videoTrack.enabled;
                const btn = $('#btnToggleCam');
                btn.toggleClass('off');
                btn.find('i').toggleClass('fa-video fa-video-slash');
                btn.find('.control-label').text(videoTrack.enabled ? 'Tắt camera' : 'Bật camera');
                
                // Show/hide local video
                $('#localVideo').toggle(videoTrack.enabled);
            }
        }
    };

    function switchCamera() {
        if (!localStream || availableCameras.length < 2) return;
        
        currentCameraIndex = (currentCameraIndex + 1) % availableCameras.length;
        const newCamera = availableCameras[currentCameraIndex];
        
        navigator.mediaDevices.getUserMedia({
            video: { deviceId: { exact: newCamera.deviceId } },
            audio: true
        }).then(newStream => {
            // Replace video track
            const newVideoTrack = newStream.getVideoTracks()[0];
            const oldVideoTrack = localStream.getVideoTracks()[0];
            
            oldVideoTrack.stop();
            localStream.removeTrack(oldVideoTrack);
            localStream.addTrack(newVideoTrack);
            
            // Update local video
            document.getElementById('localVideo').srcObject = localStream;
            
            // Update current call if exists
            if (currentCall && currentCall.peerConnection) {
                const sender = currentCall.peerConnection.getSenders().find(s => s.track.kind === 'video');
                if (sender) sender.replaceTrack(newVideoTrack);
            }
        }).catch(err => {
            console.error('Error switching camera:', err);
        });
    }

    function saveCallLog() {
        if (!currentPartnerId || callDuration < 3) return;
        
        const callLog = {
            partnerId: currentPartnerId,
            partnerName: currentPartnerName,
            type: incomingCallData ? 'INCOMING' : 'OUTGOING',
            duration: callDuration,
            timestamp: new Date().toISOString(),
            callType: incomingCallData ? incomingCallData.callType : 'VIDEO'
        };
        
        // Save to localStorage
        let callHistory = JSON.parse(localStorage.getItem('callHistory') || '[]');
        callHistory.unshift(callLog);
        if (callHistory.length > 50) callHistory = callHistory.slice(0, 50);
        localStorage.setItem('callHistory', JSON.stringify(callHistory));
        
        // Send to server
        $.post('/api/v1/messenger/call-log', callLog)
            .fail(err => console.error('Error saving call log:', err));
    }

    function handleIncomingMessage(msg) {
        if (currentPartnerId && (msg.senderId == currentPartnerId || msg.senderId == currentUser.userID)) {
            appendMessageToUI(msg);
            
            if (msg.senderId == currentPartnerId) {
                markAsRead(msg.id);
            }
        }
        
        // [FIX] CHỈ UPDATE CONVERSATION LIST, KHÔNG RELOAD CHAT
        updateConversationPreview(msg);
    }

    // ============= FIX 10: CALL HISTORY INTEGRATION =============
    window.openCallHistory = function() {
        const modal = $('<div class="call-history-modal-overlay"></div>');
        const content = $(`
            <div class="call-history-modal">
                <div class="call-history-header">
                    <h3><i class="fas fa-history"></i> Lịch sử cuộc gọi</h3>
                    <button class="close-call-history" onclick="closeCallHistory()">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
                <div class="call-history-tabs">
                    <button class="tab-btn active" data-type="ALL">Tất cả</button>
                    <button class="tab-btn" data-type="MISSED">Đã nhỡ</button>
                    <button class="tab-btn" data-type="VIDEO">Video</button>
                    <button class="tab-btn" data-type="AUDIO">Thoại</button>
                </div>
                <div class="call-history-list" id="callHistoryList">
                    <div class="loading-calls">
                        <i class="fas fa-spinner fa-spin"></i>
                        <p>Đang tải lịch sử...</p>
                    </div>
                </div>
            </div>
        `);
        
        $('body').append(modal).append(content);
        
        loadCallHistory('ALL');
        
        // Tab switching
        $('.tab-btn').click(function() {
            $('.tab-btn').removeClass('active');
            $(this).addClass('active');
            const type = $(this).data('type');
            loadCallHistory(type);
        });
    };

    window.closeCallHistory = function() {
        $('.call-history-modal-overlay, .call-history-modal').remove();
    };

    window.loadCallHistory = function(type) {
        const container = $('#callHistoryList');
        container.html('<div class="loading-calls"><i class="fas fa-spinner fa-spin"></i><p>Đang tải...</p></div>');
        
        $.get('/api/v1/messenger/call-history', {
            partnerId: currentPartnerId || undefined,
            days: 30
        })
        .done(function(logs) {
            displayCallHistory(logs, type);
        })
        .fail(function() {
            container.html('<div class="no-calls">Không thể tải lịch sử cuộc gọi</div>');
        });
    };

    window.displayCallHistory = function(logs, filterType) {
        const container = $('#callHistoryList');
        container.empty();
        
        let filteredLogs = logs;
        if (filterType !== 'ALL') {
            if (filterType === 'MISSED') {
                filteredLogs = logs.filter(log => log.callStatus === 'MISSED');
            } else if (filterType === 'VIDEO') {
                filteredLogs = logs.filter(log => log.video);
            } else if (filterType === 'AUDIO') {
                filteredLogs = logs.filter(log => !log.video);
            }
        }
        
        if (filteredLogs.length === 0) {
            container.html('<div class="no-calls">Không có cuộc gọi nào</div>');
            return;
        }
        
        filteredLogs.forEach(log => {
            const time = new Date(log.timestamp).toLocaleString('vi-VN');
            const duration = formatDuration(log.duration);
            const isOutgoing = log.callType === 'OUTGOING';
            const isMissed = log.callStatus === 'MISSED';
            const callIcon = log.video ? 'fa-video' : 'fa-phone';
            
            container.append(`
                <div class="call-history-item ${isMissed ? 'missed' : ''}">
                    <div class="call-icon">
                        <i class="fas ${callIcon} ${isOutgoing ? 'outgoing' : 'incoming'}"></i>
                    </div>
                    <div class="call-details">
                        <div class="call-partner">${log.partnerName}</div>
                        <div class="call-meta">
                            <span class="call-time">${time}</span>
                            <span class="call-duration">${duration}</span>
                        </div>
                    </div>
                    <div class="call-actions">
                        <button class="btn-call-action" onclick="redialCall(${log.partnerId}, ${log.video})">
                            <i class="fas fa-redo"></i>
                        </button>
                    </div>
                </div>
            `);
        });
    };

    window.formatDuration = function(seconds) {
        if (!seconds) return '--:--';
        const mins = Math.floor(seconds / 60);
        const secs = seconds % 60;
        return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    };

    window.redialCall = function(partnerId, isVideo) {
        // Logic redial - cần lấy thông tin partner từ partnerId
        if (isVideo) {
            window.startVideoCall();
        } else {
            window.startVoiceCall();
        }
        closeCallHistory();
    };

    // Thêm các CSS cần thiết
    const additionalCSS = `
    /* Pin Message Styles */
    .pin-indicator {
        position: absolute;
        top: -8px;
        right: -8px;
        background: #ff4757;
        color: white;
        width: 20px;
        height: 20px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 10px;
        z-index: 5;
        box-shadow: 0 2px 5px rgba(0,0,0,0.2);
    }

    .pinned-section {
        margin-top: 20px;
    }

    .section-title {
        display: flex;
        align-items: center;
        gap: 8px;
        color: #fff;
        font-size: 14px;
        font-weight: 600;
        margin-bottom: 10px;
        padding-bottom: 5px;
        border-bottom: 1px solid #333;
    }

    .pinned-messages-list {
        max-height: 200px;
        overflow-y: auto;
    }

    .pinned-message-item {
        background: rgba(255, 255, 255, 0.05);
        border-radius: 8px;
        padding: 10px;
        margin-bottom: 8px;
        cursor: pointer;
        transition: all 0.2s;
        border-left: 3px solid var(--msg-blue);
    }

    .pinned-message-item:hover {
        background: rgba(255, 255, 255, 0.1);
        transform: translateX(2px);
    }

    .pinned-content {
        color: #fff;
        font-size: 13px;
        margin-bottom: 5px;
    }

    .pinned-meta {
        display: flex;
        justify-content: space-between;
        align-items: center;
    }

    .pinned-time {
        color: #aaa;
        font-size: 11px;
    }

    .btn-unpin {
        background: rgba(255, 71, 87, 0.2);
        color: #ff4757;
        border: none;
        width: 24px;
        height: 24px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        font-size: 10px;
        transition: all 0.2s;
    }

    .btn-unpin:hover {
        background: rgba(255, 71, 87, 0.3);
        transform: scale(1.1);
    }

    /* Search Modal Styles */
    .search-modal {
        width: 800px;
        max-width: 95%;
    }

    .search-filters {
        padding: 20px;
        border-bottom: 1px solid #333;
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: 15px;
    }

    .filter-group label {
        display: block;
        color: #fff;
        font-size: 14px;
        margin-bottom: 8px;
        font-weight: 500;
    }

    #searchKeyword, #searchType, #searchSort {
        width: 100%;
        background: #3a3b3c;
        border: 1px solid #555;
        border-radius: 8px;
        padding: 10px;
        color: #fff;
        font-size: 14px;
    }

    .date-range {
        display: flex;
        align-items: center;
        gap: 10px;
    }

    #searchFromDate, #searchToDate {
        flex: 1;
        background: #3a3b3c;
        border: 1px solid #555;
        border-radius: 8px;
        padding: 10px;
        color: #fff;
        font-size: 14px;
    }

    .search-actions {
        padding: 15px 20px;
        border-bottom: 1px solid #333;
        display: flex;
        justify-content: space-between;
    }

    .btn-search-clear, .btn-search-execute {
        padding: 10px 20px;
        border-radius: 8px;
        border: none;
        cursor: pointer;
        font-weight: 600;
        display: flex;
        align-items: center;
        gap: 8px;
    }

    .btn-search-clear {
        background: #3a3b3c;
        color: #fff;
    }

    .btn-search-execute {
        background: #0084ff;
        color: white;
    }

    .search-results-container {
        padding: 20px;
        max-height: 400px;
        overflow-y: auto;
    }

    .results-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 15px;
        color: #fff;
    }

    .search-result-item {
        background: rgba(255, 255, 255, 0.05);
        border-radius: 8px;
        padding: 15px;
        margin-bottom: 10px;
        cursor: pointer;
        display: flex;
        gap: 12px;
        transition: all 0.2s;
    }

    .search-result-item:hover {
        background: rgba(255, 255, 255, 0.1);
    }

    .result-avatar img {
        width: 40px;
        height: 40px;
        border-radius: 50%;
    }

    .result-header {
        display: flex;
        justify-content: space-between;
        margin-bottom: 5px;
    }

    .result-sender {
        color: #fff;
        font-weight: 600;
    }

    .result-time {
        color: #aaa;
        font-size: 12px;
    }

    .result-text {
        color: #ccc;
        font-size: 14px;
        line-height: 1.4;
    }

    .highlight {
        background: #ffeb3b;
        color: #000;
        padding: 0 2px;
        border-radius: 2px;
        font-weight: bold;
    }

    .result-actions {
        margin-top: 10px;
        display: flex;
        gap: 10px;
    }

    .btn-result-action {
        background: rgba(0, 132, 255, 0.1);
        color: #0084ff;
        border: 1px solid rgba(0, 132, 255, 0.3);
        border-radius: 6px;
        padding: 5px 10px;
        font-size: 12px;
        cursor: pointer;
        display: flex;
        align-items: center;
        gap: 5px;
    }

    /* Stats Modal */
    .stats-modal {
        width: 600px;
    }

    .stats-summary {
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: 15px;
        padding: 20px;
    }

    .stat-card {
        background: linear-gradient(135deg, rgba(0, 132, 255, 0.1), rgba(0, 132, 255, 0.05));
        border-radius: 12px;
        padding: 20px;
        text-align: center;
        border: 1px solid rgba(0, 132, 255, 0.2);
    }

    .stat-value {
        color: var(--msg-blue);
        font-size: 32px;
        font-weight: 700;
        margin-bottom: 5px;
    }

    .stat-label {
        color: #aaa;
        font-size: 14px;
    }

    .stats-section {
        padding: 20px;
        border-top: 1px solid #333;
    }

    .stats-section h4 {
        color: #fff;
        margin: 0 0 15px 0;
        font-size: 16px;
    }

    .first-message {
        background: rgba(255, 255, 255, 0.05);
        border-radius: 8px;
        padding: 15px;
        border-left: 3px solid var(--msg-blue);
    }

    .first-sender {
        color: var(--msg-blue);
        font-weight: 600;
        margin-bottom: 5px;
    }

    .first-content {
        color: #ccc;
        font-style: italic;
        margin-bottom: 5px;
    }

    .first-date {
        color: #aaa;
        font-size: 12px;
    }

    .activity-chart {
        height: 200px;
        margin-top: 20px;
    }

    /* Call History */
    .call-history-modal {
        width: 500px;
    }

    .call-history-tabs {
        padding: 15px 20px;
        border-bottom: 1px solid #333;
        display: flex;
        gap: 10px;
    }

    .call-history-tabs .tab-btn {
        padding: 8px 16px;
        background: #3a3b3c;
        color: #aaa;
        border: none;
        border-radius: 20px;
        cursor: pointer;
        font-size: 13px;
        transition: all 0.2s;
    }

    .call-history-tabs .tab-btn.active {
        background: var(--msg-blue);
        color: white;
    }

    .call-history-list {
        padding: 20px;
        max-height: 400px;
        overflow-y: auto;
    }

    .call-history-item {
        display: flex;
        align-items: center;
        gap: 15px;
        padding: 12px;
        border-radius: 8px;
        background: rgba(255, 255, 255, 0.05);
        margin-bottom: 10px;
        transition: all 0.2s;
    }

    .call-history-item:hover {
        background: rgba(255, 255, 255, 0.1);
    }

    .call-history-item.missed {
        border-left: 3px solid #ff4757;
    }

    .call-icon {
        width: 40px;
        height: 40px;
        border-radius: 50%;
        background: rgba(0, 132, 255, 0.1);
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 16px;
    }

    .call-icon .outgoing {
        color: var(--msg-blue);
    }

    .call-icon .incoming {
        color: #2ed573;
    }

    .call-details {
        flex: 1;
    }

    .call-partner {
        color: #fff;
        font-weight: 600;
        margin-bottom: 3px;
    }

    .call-meta {
        display: flex;
        gap: 15px;
    }

    .call-time, .call-duration {
        color: #aaa;
        font-size: 12px;
    }

    .btn-call-action {
        background: rgba(0, 132, 255, 0.1);
        color: #0084ff;
        border: none;
        width: 32px;
        height: 32px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        transition: all 0.2s;
    }

    .btn-call-action:hover {
        background: rgba(0, 132, 255, 0.2);
        transform: scale(1.1);
    }

    .loading-calls, .no-calls {
        text-align: center;
        padding: 40px;
        color: #aaa;
    }
    `;

    // Thêm CSS vào document
    $(document).ready(function() {
        $('head').append(`<style>${additionalCSS}</style>`);
    });

    // [FIX] Update conversation list WITHOUT reload
    function updateConversationPreview(msg) {
        const partnerId = (msg.senderId == currentUser.userID) ? msg.receiverId : msg.senderId;
        const convItem = $(`.conv-item[onclick*="${partnerId}"]`);
        
        if (convItem.length) {
            const preview = msg.type === 'TEXT' ? msg.content : 'Đã gửi file';
            convItem.find('.conv-preview').text(preview);
            convItem.prependTo('#conversationList'); // Move to top
        } else {
            loadConversations(); // Only reload if new conversation
        }
    }

    // --- 2. CORE LOGIC: LOAD LIST ---
    // --- CẬP NHẬT: loadConversations (Truyền đủ tham số Online/Active) ---
    function loadConversations() {
        $.ajax({
            url: '/api/v1/messenger/conversations',
            method: 'GET',
            dataType: 'json',
            success: function(data) {
                const list = $('#conversationList');
                list.empty();
                if (!data || !Array.isArray(data)) return;

                data.forEach(c => {
                    const active = (c.partnerId == currentPartnerId) ? 'active' : '';
                    const unread = (c.unreadCount > 0) ? 'unread' : '';
                    const avatar = c.partnerAvatar || `https://ui-avatars.com/api/?name=${encodeURIComponent(c.partnerName)}`;

                    let strangerBadge = '';
                    if (c.friend === false) {
                        strangerBadge = `<span class="badge-stranger-icon" title="Người lạ">(Người lạ)</span>`;
                    }

                    const isFriendStr = c.friend ? 'true' : 'false';

                    list.append(`
                        <div class="conv-item ${active} ${unread} d-flex align-items-center p-2"
                            onclick="window.selectConversation(${c.partnerId}, '${c.partnerName.replace(/'/g, "\\'")}', '${avatar}', '${isFriendStr}')"
                            style="cursor:pointer; border-bottom:1px solid #333;">

                            <div class="avatar-wrapper" style="position:relative; margin-right:10px;">
                                <img src="${avatar}" style="width:48px; height:48px; border-radius:50%; object-fit:cover;">
                                ${c.online ? '<div class="online-dot"></div>' : ''}
                            </div>

                            <div class="flex-grow-1" style="min-width:0;">
                                <div class="d-flex justify-content-between align-items-center">
                                    <strong style="color:#fff; font-size:0.95rem; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">
                                        ${c.partnerName} ${strangerBadge}
                                    </strong>
                                    <small class="text-muted" style="font-size:0.75rem;">${c.timeAgo || ''}</small>
                                </div>
                                <div class="text-muted small text-truncate" style="color:#aaa;">
                                    ${c.lastMessageMine ? 'Bạn: ' : ''}${c.lastMessage || 'Hình ảnh'}
                                </div>
                            </div>

                            ${c.unreadCount > 0 ? `<div class="unread-badge">${c.unreadCount}</div>` : ''}
                        </div>
                    `);
                });

                checkUrlAndOpenChat(data);
            },
            error: function(xhr, status, err) {
                console.error('loadConversations() failed:', xhr.status, xhr.statusText, xhr.responseText);
                // Helpful toast for debugging
                if (typeof window.showToast === 'function') {
                    showToast('Lỗi tải danh sách hội thoại. Kiểm tra console/server logs.', 'error');
                } else {
                    console.error('[showToast missing] Lỗi tải danh sách hội thoại.');
                }
            }
        });
    }

    // --- FIX 11: STRANGER BANNER LOGIC ---
    window.sendFriendRequest = function(partnerId, btnElement) {
        const originalHtml = btnElement.innerHTML;
        btnElement.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
        btnElement.disabled = true;

        fetch(`/social/add-friend/${partnerId}`, { method: 'POST' })
            .then(res => res.ok ? res.json() : Promise.reject())
            .then(() => {
                btnElement.innerHTML = '<i class="fas fa-clock"></i> Đã gửi';
                btnElement.classList.add('btn-stranger-pending');
                btnElement.onclick = () => window.cancelFriendRequest(partnerId, btnElement);
                btnElement.disabled = false;
            })
            .catch(() => {
                btnElement.innerHTML = originalHtml;
                btnElement.disabled = false;
                alert('Lỗi gửi lời mời');
            });
    };

    window.cancelFriendRequest = function(partnerId, btnElement) {
        if (!confirm('Hủy lời mời kết bạn?')) return;
        
        btnElement.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
        
        fetch(`/social/unfriend/${partnerId}`, { method: 'POST' })
            .then(res => {
                if (res.ok) {
                    btnElement.innerHTML = '<i class="fas fa-user-plus"></i> Kết bạn';
                    btnElement.classList.remove('btn-stranger-pending');
                    btnElement.onclick = () => window.sendFriendRequest(partnerId, btnElement);
                }
            });
    };

    // --- 3. SELECT AND LOAD THEME KHI CHỌN CONVERSATION ---
    window.selectConversation = function(partnerId, name, avatar, isFriend, isOnline, lastActive) {
        currentPartnerId = parseInt(partnerId);
        currentPartnerName = name;
        isCurrentPartnerFriend = (String(isFriend) === 'true');

        // UI Updates
        $('#emptyState').hide();
        $('#chatInterface').show();
        updateInfoSidebar(name, avatar);

        // Load theme từ server
        $.get(`/api/v1/messenger/settings/${partnerId}`)
            .done(function(settings) {
                if (settings.themeColor && settings.themeColor !== '#0084ff') {
                    window.applyTheme(settings.themeColor);
                } else {
                    // Reset về mặc định
                    document.documentElement.style.setProperty('--msg-blue', '#0084ff');
                }
            })
            .fail(function() {
                // Fallback: load từ localStorage
                const savedTheme = localStorage.getItem(`theme_${partnerId}`);
                if (savedTheme) {
                    window.applyTheme(savedTheme);
                }
            });
        
        // [FIX] Header: Tên + Badge (nếu lạ)
        let headerHtml = `<h4 id="headerName" style="margin:0;">${name}`;
        if (!isCurrentPartnerFriend) {
            headerHtml += ` <span style="font-size:0.7rem; background:#444; color:#ccc; padding:2px 6px; border-radius:4px; vertical-align:middle; margin-left:5px;">Người lạ</span>`;
        }
        headerHtml += `</h4>`;
        
        // Render lại vùng info header
        $('.chat-user-info div').first().html(headerHtml + `<div id="chatHeaderStatus"></div>`); // Reset lại cấu trúc
        $('#headerAvatar').attr('src', avatar);

        // Status Line (Dòng dưới tên)
        const statusDiv = $('#chatHeaderStatus');
        if (isCurrentPartnerFriend) {
            // Nếu là bạn -> Hiện status hoạt động
            if (String(isOnline) === 'true') {
                statusDiv.html(`<small class="text-success"><i class="fas fa-circle" style="font-size:8px;"></i> Đang hoạt động</small>`);
            } else {
                statusDiv.html(`<small class="text-muted">${lastActive ? 'Hoạt động ' + lastActive : 'Không hoạt động'}</small>`);
            }
        } else {
             // Nếu là người lạ -> Không hiện status online, để trống cho gọn
             statusDiv.empty();
        }

        // [FIX] Banner Zalo (Vàng) - Chỉ hiện khi là người lạ
        $('#strangerBanner').remove();
        if (!isCurrentPartnerFriend) {
            const banner = `
                <div id="strangerBanner" class="stranger-alert-bar">
                    <div class="stranger-content">
                        <i class="fas fa-user-shield"></i>
                        <span>Tin nhắn từ người lạ. Hãy cẩn thận khi chia sẻ thông tin.</span>
                    </div>
                    <div class="stranger-actions">
                        <button class="btn-stranger-add" onclick="window.sendFriendRequest(${partnerId}, this)">Kết bạn</button>
                        <button class="btn-stranger-block" onclick="alert('Tính năng chặn đang phát triển')">Chặn</button>
                    </div>
                </div>
            `;
            $('#messagesContainer').before(banner);
        }

        // Active Sidebar & Load
        $('.conv-item').removeClass('active');
        $(`#conv-${partnerId}`).addClass('active');
        loadChatHistory(partnerId);
        $('.messenger-container').addClass('show-chat');
    };

    function loadChatHistory(partnerId) {
        let container = $('#messagesContainer');
        container.html('<div class="text-center mt-5 text-muted"><i class="fas fa-spinner fa-spin"></i> Đang tải...</div>');

        $.get(`/api/v1/messenger/chat/${partnerId}`, function(msgs) {
            container.empty();
            
            // Nếu trống -> Hiện banner chào
            if(!msgs || msgs.length === 0) {
                let bannerText = isCurrentPartnerFriend ? 'Hãy gửi lời chào!' : 'Gửi lời chào để bắt đầu kết nối.';
                container.html(`<div class="text-center mt-5 text-muted"><small>${bannerText}</small></div>`);
                return;
            }
            msgs.forEach(m => appendMessageToUI(m));
            scrollToBottom();
        });
    }

    // --- 4. RENDER UI (DÙNG CẤU TRÚC FILE CŨ CỦA BẠN) ---
    function appendMessageToUI(msg, forceMine = false) {
        const myId = parseInt(currentUser.userID);
        let isMine = forceMine || (msg.senderId != currentPartnerId);
        const typeClass = isMine ? 'mine' : 'other';
        const msgId = msg.id || 'temp-' + Date.now();
        
        // Reply block
        let replyHtml = '';
        if (msg.replyTo) {
            const rName = (msg.replyTo.senderId === myId) ? 'Bạn' : currentPartnerName;
            let rContent = msg.replyTo.type === 'TEXT' ? msg.replyTo.content : '[Đính kèm]';
            if (rContent.length > 40) rContent = rContent.substring(0, 40) + '...';
            
            replyHtml = `
                <div class="reply-block" onclick="scrollToMessage(${msg.replyTo.id})">
                    <div class="reply-name">${rName}</div>
                    <div>${rContent}</div>
                </div>
            `;
        }

        // Content
        let contentHtml = '';
        if (msg.isDeleted) {
            contentHtml = '<div class="bubble" style="font-style:italic; opacity:0.6;">Tin nhắn đã bị thu hồi</div>';
        } else if (msg.type === 'IMAGE' || msg.type === 'STICKER') {
            const imgClass = msg.type === 'STICKER' ? 'msg-sticker' : 'msg-image';
            contentHtml = `<img src="${msg.content}" class="${imgClass}" onclick="window.open('${msg.content}')" style="max-width:200px; border-radius:10px; cursor:pointer;">`;
        } else if (msg.type === 'AUDIO') {
            contentHtml = renderAudioPlayer(msg.content);
        } else if (msg.type === 'FILE') {
            const fileName = decodeURIComponent(msg.content.split('/').pop());
            contentHtml = `
                <div class="msg-file">
                    <i class="fas fa-file-alt fa-2x"></i>
                    <div>
                        <div style="font-size:12px; font-weight:bold;">${fileName}</div>
                        <a href="${msg.content}" download style="color:#0084ff; font-size:11px;">Tải xuống</a>
                    </div>
                </div>
            `;
        } else {
            contentHtml = `<div class="bubble">${replyHtml}${msg.content}</div>`;
        }

        // Around line 1585, find the action buttons section and update to:
        let actionButtons = '';
        if (isMine) {
            actionButtons = `
                <div class="action-btn" title="Chuyển tiếp" onclick="window.forwardMessage(${msgId})">
                    <i class="fas fa-share"></i>
                </div>
                <div class="action-btn" title="Ghim" onclick="window.togglePinMessage(${msgId})">
                    <i class="fas fa-thumbtack"></i>
                </div>
                <div class="action-btn" title="Trả lời" onclick="window.startReply(${msgId}, 'Bạn', '${msg.content?.substring(0,50) || '[File]'}')">
                    <i class="fas fa-reply"></i>
                </div>
                <div class="action-btn" title="Thu hồi" onclick="window.unsendMessage(${msgId})">
                    <i class="fas fa-trash"></i>
                </div>
            `;
        } else {
            actionButtons = `
                <div class="action-btn" title="Chuyển tiếp" onclick="window.forwardMessage(${msgId})">
                    <i class="fas fa-share"></i>
                </div>
                <div class="action-btn" title="Trả lời" onclick="window.startReply(${msgId}, '${currentPartnerName}', '${msg.content?.substring(0,50) || '[File]'}')">
                    <i class="fas fa-reply"></i>
                </div>
            `;
        }

        // Actions
        const unsendBtn = (isMine && !msg.isDeleted) 
            ? `<div class="action-btn" onclick="window.unsendMessage(${msgId})" title="Thu hồi"><i class="fas fa-trash"></i></div>` 
            : '';
        
        // const actionsHtml = `
        //     <div class="msg-actions">
        //         <div class="action-btn" onclick="window.startReply(${msgId}, '${isMine ? 'Bạn' : currentPartnerName}', '${msg.content?.substring(0,50) || '[File]'}')" title="Trả lời"><i class="fas fa-reply"></i></div>
        //         ${unsendBtn}
        //     </div>
        // `;

        const actionsHtml = `
            <div class="msg-actions">
                ${actionButtons}
            </div>
        `;

        // Avatar
        let avatarHtml = !isMine ? `<img src="${$('#headerAvatar').attr('src')}" class="avatar-img" style="width: 28px; height: 28px;">` : '';

        const html = `
            <div class="msg-row ${typeClass}" id="msg-${msgId}" data-msg-id="${msgId}">
                ${avatarHtml}
                <div class="msg-content">${contentHtml}</div>
                ${actionsHtml}
            </div>
        `;
        
        $('#messagesContainer').append(html);
        scrollToBottom();
    }

    function scrollToBottom() {
        let d = $('#messagesContainer');
        d.scrollTop(d[0].scrollHeight);
    }

    // --- 5. ACTIONS ---


    // --- FIX 3: REPLY LOGIC ---
    let replyToId = null;

    window.startReply = function(msgId, senderName, content) {
        replyToId = msgId;
        const previewText = content.length > 50 ? content.substring(0, 50) + '...' : content;
        
        $('#replyingBar').addClass('active').html(`
            <div>
                <div style="font-weight:bold; color:#0084ff;">Trả lời ${senderName}</div>
                <div style="color:#aaa; font-size:12px;">${previewText}</div>
            </div>
            <i class="fas fa-times" onclick="window.cancelReply()" style="cursor:pointer;"></i>
        `);
        $('#msgInput').focus();
    };

    window.cancelReply = function() {
        replyToId = null;
        $('#replyingBar').removeClass('active');
    };

    // Gán vào window để HTML gọi được
    window.sendTextMessage = function() {
        const content = $('#msgInput').val().trim();

        // [FIX QUAN TRỌNG] Kiểm tra xem có file đang chờ gửi không TRƯỚC
        if (pendingFile) {
            console.log("Đang gửi file...", pendingFile);
            // Gọi hàm upload kèm theo nội dung text (làm caption)
            uploadAndSend(pendingFile.file, pendingFile.type, content);
            return; // Dừng lại, không chạy logic gửi text phía dưới
        }

        // Nếu không có file, mới kiểm tra text
        if (content && currentPartnerId) {
            // Optimistic UI: Hiện tin nhắn ngay lập tức
            appendMessageToUI({
                senderId: currentUser.userID,
                content: content,
                type: 'TEXT',
                formattedTime: 'Đang gửi...'
            }, true);

            // Gửi API
            sendApiRequest({ receiverId: currentPartnerId, content: content, type: 'TEXT' });
            
            // Xóa ô nhập liệu
            $('#msgInput').val('').focus();
        }
    };

    window.sendSticker = function(url) {
        $('#stickerMenu').hide();
        if(!currentPartnerId) return;
        
        // Gửi type STICKER (nếu backend đã update) hoặc IMAGE
        let payload = { receiverId: currentPartnerId, content: url, type: 'STICKER' };
        sendApiRequest(payload);
    };

    // ============= FIX 7: PIN MESSAGE SYSTEM =============
    window.togglePinMessage = function(messageId) {
        if (!currentPartnerId || !messageId) return;
        
        $.post(`/api/v1/messenger/pin/${messageId}`)
            .done(function(response) {
                const msgElement = $(`#msg-${messageId}`);
                const pinIcon = msgElement.find('.pin-icon');
                
                if (response.pinned) {
                    if (!pinIcon.length) {
                        msgElement.find('.msg-content').append(`
                            <div class="pin-indicator" title="Đã ghim">
                                <i class="fas fa-thumbtack"></i>
                            </div>
                        `);
                    }
                    showToast('Đã ghim tin nhắn!', 'success');
                } else {
                    msgElement.find('.pin-indicator').remove();
                    showToast('Đã bỏ ghim tin nhắn!', 'info');
                }
                
                // Reload pinned messages trong sidebar
                if (!$('#chatInfoSidebar').hasClass('hidden')) {
                    loadPinnedMessages();
                }
            })
            .fail(function() {
                showToast('Lỗi thao tác ghim tin nhắn!', 'error');
            });
    };

    window.loadPinnedMessages = function() {
        if (!currentPartnerId) return;
        
        const container = $('#pinnedMessagesList');
        if (!container.length) {
            // Thêm section pinned messages vào sidebar
            $('.accordion-item:eq(1) .accordion-content').append(`
                <div class="pinned-section">
                    <div class="section-title">
                        <i class="fas fa-thumbtack"></i>
                        <span>Tin nhắn đã ghim</span>
                    </div>
                    <div class="pinned-messages-list" id="pinnedMessagesList">
                        <div class="loading-pinned">Đang tải...</div>
                    </div>
                </div>
            `);
        }
        
        $.get(`/api/v1/messenger/pinned/${currentPartnerId}`)
            .done(function(messages) {
                const list = $('#pinnedMessagesList');
                list.empty();
                
                if (messages.length === 0) {
                    list.html('<div class="no-pinned">Chưa có tin nhắn nào được ghim</div>');
                    return;
                }
                
                messages.forEach(msg => {
                    const shortContent = msg.content.length > 30 ? 
                        msg.content.substring(0, 30) + '...' : msg.content;
                    const time = new Date(msg.timestamp).toLocaleTimeString('vi-VN', {
                        hour: '2-digit',
                        minute: '2-digit'
                    });
                    
                    list.append(`
                        <div class="pinned-message-item" onclick="scrollToMessage(${msg.id})">
                            <div class="pinned-content">${shortContent}</div>
                            <div class="pinned-meta">
                                <span class="pinned-time">${time}</span>
                                <button class="btn-unpin" onclick="event.stopPropagation(); togglePinMessage(${msg.id})">
                                    <i class="fas fa-times"></i>
                                </button>
                            </div>
                        </div>
                    `);
                });
            })
            .fail(function() {
                $('#pinnedMessagesList').html('<div class="error-pinned">Lỗi tải tin đã ghim</div>');
            });
    };

    // ============= FIX 8: ADVANCED SEARCH SYSTEM =============
    window.openAdvancedSearch = function() {
        const modal = $('<div class="search-modal-overlay"></div>');
        const content = $(`
            <div class="search-modal">
                <div class="search-modal-header">
                    <h3><i class="fas fa-search"></i> Tìm kiếm nâng cao</h3>
                    <button class="close-search-modal" onclick="closeAdvancedSearch()">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
                <div class="search-filters">
                    <div class="filter-group">
                        <label>Từ khóa:</label>
                        <input type="text" id="searchKeyword" placeholder="Nhập từ cần tìm...">
                    </div>
                    <div class="filter-group">
                        <label>Loại tin nhắn:</label>
                        <select id="searchType">
                            <option value="ALL">Tất cả</option>
                            <option value="TEXT">Tin nhắn văn bản</option>
                            <option value="IMAGE">Hình ảnh</option>
                            <option value="FILE">File đính kèm</option>
                            <option value="AUDIO">Tin nhắn thoại</option>
                        </select>
                    </div>
                    <div class="filter-group">
                        <label>Khoảng thời gian:</label>
                        <div class="date-range">
                            <input type="date" id="searchFromDate">
                            <span>đến</span>
                            <input type="date" id="searchToDate">
                        </div>
                    </div>
                    <div class="filter-group">
                        <label>Sắp xếp:</label>
                        <select id="searchSort">
                            <option value="NEWEST">Mới nhất trước</option>
                            <option value="OLDEST">Cũ nhất trước</option>
                        </select>
                    </div>
                </div>
                <div class="search-actions">
                    <button class="btn-search-clear" onclick="clearSearchFilters()">
                        <i class="fas fa-eraser"></i> Xóa bộ lọc
                    </button>
                    <button class="btn-search-execute" onclick="executeAdvancedSearch()">
                        <i class="fas fa-search"></i> Tìm kiếm
                    </button>
                </div>
                <div class="search-results-container">
                    <div class="results-header">
                        <span id="resultsCount">0 kết quả</span>
                        <div class="results-actions">
                            <button class="btn-export-results" onclick="exportSearchResults()">
                                <i class="fas fa-download"></i> Xuất kết quả
                            </button>
                        </div>
                    </div>
                    <div class="search-results-list" id="searchResultsList">
                        <div class="no-results-placeholder">
                            <i class="fas fa-search"></i>
                            <p>Nhập từ khóa và nhấn "Tìm kiếm"</p>
                        </div>
                    </div>
                </div>
            </div>
        `);
        
        $('body').append(modal).append(content);
        
        // Set default dates
        const today = new Date().toISOString().split('T')[0];
        const weekAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
        $('#searchFromDate').val(weekAgo);
        $('#searchToDate').val(today);
    };

    window.closeAdvancedSearch = function() {
        $('.search-modal-overlay, .search-modal').remove();
    };

    window.clearSearchFilters = function() {
        $('#searchKeyword').val('');
        $('#searchType').val('ALL');
        $('#searchSort').val('NEWEST');
        
        const today = new Date().toISOString().split('T')[0];
        const weekAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
        $('#searchFromDate').val(weekAgo);
        $('#searchToDate').val(today);
    };

    window.executeAdvancedSearch = function() {
        if (!currentPartnerId) {
            showToast('Vui lòng chọn một cuộc trò chuyện!', 'error');
            return;
        }
        
        const keyword = $('#searchKeyword').val().trim();
        if (!keyword) {
            showToast('Vui lòng nhập từ khóa tìm kiếm!', 'warning');
            return;
        }
        
        const btn = $('.btn-search-execute');
        btn.prop('disabled', true).html('<i class="fas fa-spinner fa-spin"></i> Đang tìm...');
        
        $.get('/api/v1/messenger/search', {
            partnerId: currentPartnerId,
            query: keyword
        })
        .done(function(messages) {
            displaySearchResults(messages);
            $('#resultsCount').text(`${messages.length} kết quả`);
        })
        .fail(function() {
            showToast('Lỗi tìm kiếm!', 'error');
        })
        .always(function() {
            btn.prop('disabled', false).html('<i class="fas fa-search"></i> Tìm kiếm');
        });
    };

    window.displaySearchResults = function(messages) {
        const container = $('#searchResultsList');
        container.empty();
        
        if (messages.length === 0) {
            container.html(`
                <div class="no-results-found">
                    <i class="fas fa-search"></i>
                    <p>Không tìm thấy kết quả phù hợp</p>
                </div>
            `);
            return;
        }
        
        messages.forEach(msg => {
            const time = new Date(msg.timestamp).toLocaleString('vi-VN');
            const isMine = msg.senderId === currentUser.userID;
            const senderName = isMine ? 'Bạn' : currentPartnerName;
            
            container.append(`
                <div class="search-result-item" onclick="scrollToMessage(${msg.id})">
                    <div class="result-avatar">
                        <img src="${msg.senderAvatar}" alt="${senderName}">
                    </div>
                    <div class="result-content">
                        <div class="result-header">
                            <span class="result-sender">${senderName}</span>
                            <span class="result-time">${time}</span>
                        </div>
                        <div class="result-text">${highlightKeyword(msg.content, $('#searchKeyword').val())}</div>
                        <div class="result-actions">
                            <button class="btn-result-action" onclick="event.stopPropagation(); replyToMessage(${msg.id})">
                                <i class="fas fa-reply"></i> Trả lời
                            </button>
                            <button class="btn-result-action" onclick="event.stopPropagation(); togglePinMessage(${msg.id})">
                                <i class="fas fa-thumbtack"></i> Ghim
                            </button>
                        </div>
                    </div>
                </div>
            `);
        });
    };

    window.highlightKeyword = function(text, keyword) {
        if (!keyword) return text;
        const regex = new RegExp(`(${keyword})`, 'gi');
        return text.replace(regex, '<mark class="highlight">$1</mark>');
    };

    window.exportSearchResults = function() {
        // Logic export kết quả tìm kiếm (có thể export ra file txt)
        showToast('Tính năng xuất kết quả đang phát triển', 'info');
    };


    // ============= FIX 9: CHAT STATISTICS =============
    window.viewChatStats = function() {
        if (!currentPartnerId) return;
        
        const modal = $('<div class="stats-modal-overlay"></div>');
        const content = $(`
            <div class="stats-modal">
                <div class="stats-modal-header">
                    <h3><i class="fas fa-chart-bar"></i> Thống kê đoạn chat</h3>
                    <button class="close-stats-modal" onclick="closeStatsModal()">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
                <div class="stats-content" id="statsContent">
                    <div class="loading-stats">
                        <i class="fas fa-spinner fa-spin"></i>
                        <p>Đang tải thống kê...</p>
                    </div>
                </div>
            </div>
        `);
        
        $('body').append(modal).append(content);
        
        // Load stats
        $.get(`/api/v1/messenger/stats/${currentPartnerId}`)
            .done(function(stats) {
                displayChatStats(stats);
            })
            .fail(function() {
                $('#statsContent').html(`
                    <div class="stats-error">
                        <i class="fas fa-exclamation-triangle"></i>
                        <p>Không thể tải thống kê</p>
                    </div>
                `);
            });
    };

    window.closeStatsModal = function() {
        $('.stats-modal-overlay, .stats-modal').remove();
    };

    window.displayChatStats = function(stats) {
        const container = $('#statsContent');
        
        let html = `
            <div class="stats-summary">
                <div class="stat-card">
                    <div class="stat-value">${stats.totalMessages || 0}</div>
                    <div class="stat-label">Tổng tin nhắn</div>
                </div>
                <div class="stat-card">
                    <div class="stat-value">${stats.mediaCount || 0}</div>
                    <div class="stat-label">File phương tiện</div>
                </div>
            </div>
        `;
        
        if (stats.firstMessage) {
            const firstDate = new Date(stats.firstMessage.timestamp).toLocaleDateString('vi-VN');
            html += `
                <div class="stats-section">
                    <h4>Tin nhắn đầu tiên</h4>
                    <div class="first-message">
                        <div class="first-sender">${stats.firstMessage.sender}</div>
                        <div class="first-content">${stats.firstMessage.content}</div>
                        <div class="first-date">${firstDate}</div>
                    </div>
                </div>
            `;
        }
        
        // Thêm các phần thống kê khác nếu có
        html += `
            <div class="stats-section">
                <h4>Hoạt động gần đây</h4>
                <div class="activity-chart" id="activityChart">
                    <canvas id="chatActivityCanvas"></canvas>
                </div>
            </div>
        `;
        
        container.html(html);
        
        // Vẽ biểu đồ nếu có dữ liệu
        setTimeout(() => {
            if (window.Chart && $('#chatActivityCanvas').length) {
                renderActivityChart();
            }
        }, 100);
    };

    window.renderActivityChart = function() {
        // Demo chart - cần tích hợp với dữ liệu thực
        const ctx = document.getElementById('chatActivityCanvas').getContext('2d');
        new Chart(ctx, {
            type: 'line',
            data: {
                labels: ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'],
                datasets: [{
                    label: 'Số tin nhắn',
                    data: [12, 19, 8, 15, 22, 18, 25],
                    borderColor: 'rgb(75, 192, 192)',
                    backgroundColor: 'rgba(75, 192, 192, 0.2)',
                    tension: 0.4
                }]
            },
            options: {
                responsive: true,
                plugins: {
                    legend: {
                        position: 'top',
                    },
                    title: {
                        display: true,
                        text: 'Hoạt động chat trong tuần'
                    }
                }
            }
        });
    };


    // ============= FIX 1: THÊM LOGIC THEME DYNAMIC =============
    window.applyTheme = function(color) {
        if (!color) return;
        
        // Cập nhật CSS variables
        document.documentElement.style.setProperty('--msg-blue', color);
        
        // Tính toán các biến màu liên quan
        const lightColor = adjustBrightness(color, 40);
        const darkColor = adjustBrightness(color, -20);
        
        document.documentElement.style.setProperty('--msg-blue-light', lightColor);
        document.documentElement.style.setProperty('--msg-blue-dark', darkColor);
        
        // Lưu vào localStorage
        if (currentPartnerId) {
            localStorage.setItem(`theme_${currentPartnerId}`, color);
        }
    };

    function adjustBrightness(color, percent) {
        const num = parseInt(color.replace("#", ""), 16);
        const amt = Math.round(2.55 * percent);
        const R = (num >> 16) + amt;
        const G = (num >> 8 & 0x00FF) + amt;
        const B = (num & 0x0000FF) + amt;
        
        return "#" + (
            0x1000000 +
            (R < 255 ? R < 1 ? 0 : R : 255) * 0x10000 +
            (G < 255 ? G < 1 ? 0 : G : 255) * 0x100 +
            (B < 255 ? B < 1 ? 0 : B : 255)
        ).toString(16).slice(1);
    }

    // ============= FIX 2: THÊM MODAL THEME PICKER =============
    window.openThemePicker = function() {
        const modal = $('<div class="theme-modal-overlay"></div>');
        const content = $(`
            <div class="theme-modal">
                <div class="theme-modal-header">
                    <h3><i class="fas fa-palette"></i> Chọn chủ đề</h3>
                    <button class="close-theme-modal" onclick="closeThemePicker()">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
                <div class="theme-colors-grid">
                    <div class="color-option" data-color="#0084ff" style="background: #0084ff;" onclick="selectThemeColor(this)"></div>
                    <div class="color-option" data-color="#ff4757" style="background: #ff4757;" onclick="selectThemeColor(this)"></div>
                    <div class="color-option" data-color="#2ed573" style="background: #2ed573;" onclick="selectThemeColor(this)"></div>
                    <div class="color-option" data-color="#ffa502" style="background: #ffa502;" onclick="selectThemeColor(this)"></div>
                    <div class="color-option" data-color="#3742fa" style="background: #3742fa;" onclick="selectThemeColor(this)"></div>
                    <div class="color-option" data-color="#7158e2" style="background: #7158e2;" onclick="selectThemeColor(this)"></div>
                    <div class="color-option" data-color="#ff6b81" style="background: #ff6b81;" onclick="selectThemeColor(this)"></div>
                    <div class="color-option" data-color="#1e90ff" style="background: #1e90ff;" onclick="selectThemeColor(this)"></div>
                    <div class="color-option" data-color="#00d2d3" style="background: #00d2d3;" onclick="selectThemeColor(this)"></div>
                    <div class="color-option" data-color="#ff9ff3" style="background: #ff9ff3;" onclick="selectThemeColor(this)"></div>
                    <div class="color-option" data-color="#54a0ff" style="background: #54a0ff;" onclick="selectThemeColor(this)"></div>
                    <div class="color-option" data-color="#5f27cd" style="background: #5f27cd;" onclick="selectThemeColor(this)"></div>
                </div>
                <div class="theme-custom-section">
                    <h4>Màu tùy chỉnh</h4>
                    <div class="custom-color-input">
                        <input type="color" id="customColorPicker" value="#0084ff">
                        <input type="text" id="customColorHex" placeholder="#0084ff" maxlength="7">
                        <button onclick="applyCustomTheme()">Áp dụng</button>
                    </div>
                </div>
                <div class="theme-actions">
                    <button class="btn-theme-cancel" onclick="closeThemePicker()">Hủy</button>
                    <button class="btn-theme-apply" onclick="saveThemeToServer()">Lưu thay đổi</button>
                </div>
            </div>
        `);
        
        $('body').append(modal).append(content);
    };

    window.closeThemePicker = function() {
        $('.theme-modal-overlay, .theme-modal').remove();
    };

    window.selectThemeColor = function(element) {
        $('.color-option').removeClass('selected');
        $(element).addClass('selected');
        const color = $(element).data('color');
        $('#customColorPicker').val(color);
        $('#customColorHex').val(color);
        window.applyTheme(color);
    };

    window.applyCustomTheme = function() {
        let color = $('#customColorHex').val();
        if (!color.startsWith('#')) color = '#' + color;
        if (/^#[0-9A-F]{6}$/i.test(color)) {
            $('#customColorPicker').val(color);
            window.applyTheme(color);
        } else {
            alert('Mã màu không hợp lệ!');
        }
    };

    window.saveThemeToServer = function() {
        const color = $('#customColorHex').val();
        if (!currentPartnerId) {
            alert('Vui lòng chọn một cuộc trò chuyện!');
            return;
        }
        
        $.ajax({
            url: '/api/v1/messenger/settings/theme',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({
                partnerId: currentPartnerId,
                themeColor: color
            }),
            success: function() {
                showToast('Đã cập nhật chủ đề!', 'success');
                closeThemePicker();
            },
            error: function() {
                showToast('Lỗi cập nhật chủ đề!', 'error');
            }
        });
    };

    // ============= FIX 5: THÊM CSS CHO MODAL =============
    // Thêm vào messenger.css
    const themeAndNicknameCSS = `
    /* Theme Modal */
    .theme-modal-overlay, .nickname-modal-overlay {
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0, 0, 0, 0.8);
        z-index: 9998;
        display: flex;
        align-items: center;
        justify-content: center;
        backdrop-filter: blur(5px);
    }

    .theme-modal, .nickname-modal {
        background: #242526;
        border-radius: 16px;
        width: 450px;
        max-width: 90%;
        max-height: 80%;
        overflow-y: auto;
        box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
        animation: modalAppear 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    }

    @keyframes modalAppear {
        from {
            opacity: 0;
            transform: scale(0.9) translateY(20px);
        }
        to {
            opacity: 1;
            transform: scale(1) translateY(0);
        }
    }

    .theme-modal-header, .nickname-modal-header {
        padding: 20px;
        border-bottom: 1px solid #333;
        display: flex;
        justify-content: space-between;
        align-items: center;
    }

    .theme-modal-header h3, .nickname-modal-header h3 {
        margin: 0;
        color: #fff;
        font-size: 18px;
        display: flex;
        align-items: center;
        gap: 10px;
    }

    .close-theme-modal, .close-nickname-modal {
        background: none;
        border: none;
        color: #aaa;
        font-size: 20px;
        cursor: pointer;
        padding: 5px;
        border-radius: 50%;
        width: 36px;
        height: 36px;
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .close-theme-modal:hover, .close-nickname-modal:hover {
        background: rgba(255, 255, 255, 0.1);
        color: #fff;
    }

    .theme-colors-grid {
        padding: 20px;
        display: grid;
        grid-template-columns: repeat(6, 1fr);
        gap: 12px;
    }

    .color-option {
        width: 100%;
        aspect-ratio: 1;
        border-radius: 10px;
        cursor: pointer;
        border: 3px solid transparent;
        transition: all 0.2s;
        position: relative;
    }

    .color-option:hover {
        transform: scale(1.05);
        box-shadow: 0 5px 15px rgba(0, 0, 0, 0.3);
    }

    .color-option.selected {
        border-color: #fff;
        box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.3);
    }

    .color-option.selected::after {
        content: '✓';
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        color: #fff;
        font-size: 18px;
        font-weight: bold;
        text-shadow: 0 1px 3px rgba(0, 0, 0, 0.5);
    }

    .theme-custom-section {
        padding: 0 20px 20px;
        border-bottom: 1px solid #333;
    }

    .theme-custom-section h4 {
        color: #fff;
        margin: 0 0 15px 0;
        font-size: 16px;
    }

    .custom-color-input {
        display: flex;
        gap: 10px;
        align-items: center;
    }

    #customColorPicker {
        width: 50px;
        height: 50px;
        border: none;
        border-radius: 8px;
        cursor: pointer;
        background: none;
    }

    #customColorPicker::-webkit-color-swatch-wrapper {
        padding: 0;
    }

    #customColorPicker::-webkit-color-swatch {
        border: none;
        border-radius: 8px;
    }

    #customColorHex {
        flex: 1;
        background: #3a3b3c;
        border: 1px solid #555;
        border-radius: 8px;
        padding: 12px;
        color: #fff;
        font-size: 14px;
    }

    .custom-color-input button {
        background: #0084ff;
        color: white;
        border: none;
        border-radius: 8px;
        padding: 12px 20px;
        cursor: pointer;
        font-weight: 600;
        transition: all 0.2s;
    }

    .custom-color-input button:hover {
        background: #0073e6;
    }

    .theme-actions, .nickname-actions {
        padding: 20px;
        display: flex;
        justify-content: flex-end;
        gap: 10px;
    }

    .btn-theme-cancel, .btn-nickname-clear {
        background: #3a3b3c;
        color: #fff;
        border: none;
        border-radius: 8px;
        padding: 12px 24px;
        cursor: pointer;
        font-weight: 600;
    }

    .btn-theme-apply, .btn-nickname-save {
        background: #0084ff;
        color: white;
        border: none;
        border-radius: 8px;
        padding: 12px 24px;
        cursor: pointer;
        font-weight: 600;
    }

    .btn-theme-cancel:hover, .btn-nickname-clear:hover {
        background: #4e4f50;
    }

    .btn-theme-apply:hover, .btn-nickname-save:hover {
        background: #0073e6;
    }

    /* Nickname Modal */
    .nickname-input-section {
        padding: 20px;
        border-bottom: 1px solid #333;
    }

    .nickname-input-section p {
        color: #fff;
        margin: 0 0 15px 0;
    }

    #nicknameInput {
        width: 100%;
        background: #3a3b3c;
        border: 1px solid #555;
        border-radius: 8px;
        padding: 12px;
        color: #fff;
        font-size: 16px;
        margin-bottom: 10px;
    }

    .nickname-hint {
        color: #aaa;
        font-size: 12px;
    }

    .nickname-examples {
        padding: 20px;
        border-bottom: 1px solid #333;
    }

    .example-title {
        color: #aaa;
        font-size: 14px;
        margin-bottom: 10px;
    }

    .example-tags {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
    }

    .example-tag {
        background: rgba(0, 132, 255, 0.1);
        color: #0084ff;
        padding: 6px 12px;
        border-radius: 20px;
        font-size: 14px;
        cursor: pointer;
        transition: all 0.2s;
        border: 1px solid rgba(0, 132, 255, 0.3);
    }

    .example-tag:hover {
        background: rgba(0, 132, 255, 0.2);
        transform: translateY(-2px);
    }
    `;

    // Thêm CSS vào document
    $(document).ready(function() {
        $('head').append(`<style>${themeAndNicknameCSS}</style>`);
    });

    // ============= FIX 3: THÊM MODAL NICKNAME =============
    window.openNicknameModal = function() {
        const modal = $('<div class="nickname-modal-overlay"></div>');
        const content = $(`
            <div class="nickname-modal">
                <div class="nickname-modal-header">
                    <h3><i class="fas fa-font"></i> Đổi biệt danh</h3>
                    <button class="close-nickname-modal" onclick="closeNicknameModal()">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
                <div class="nickname-input-section">
                    <p>Biệt danh mới cho <strong>${currentPartnerName}</strong>:</p>
                    <input type="text" id="nicknameInput" placeholder="Nhập biệt danh..." maxlength="50">
                    <div class="nickname-hint">
                        <small>Biệt danh chỉ hiển thị với bạn</small>
                    </div>
                </div>
                <div class="nickname-examples">
                    <div class="example-title">Gợi ý:</div>
                    <div class="example-tags">
                        <span class="example-tag" onclick="fillNickname('Bạn thân')">Bạn thân</span>
                        <span class="example-tag" onclick="fillNickname('Đồng nghiệp')">Đồng nghiệp</span>
                        <span class="example-tag" onclick="fillNickname('Crush')">Crush</span>
                        <span class="example-tag" onclick="fillNickname('Sếp')">Sếp</span>
                        <span class="example-tag" onclick="fillNickname('Chị/Anh')">Chị/Anh</span>
                    </div>
                </div>
                <div class="nickname-actions">
                    <button class="btn-nickname-clear" onclick="clearNickname()">Xóa biệt danh</button>
                    <button class="btn-nickname-save" onclick="saveNickname()">Lưu</button>
                </div>
            </div>
        `);
        
        $('body').append(modal).append(content);
        
        // Load current nickname
        $.get(`/api/v1/messenger/settings/${currentPartnerId}`)
            .done(function(settings) {
                if (settings.nickname) {
                    $('#nicknameInput').val(settings.nickname);
                }
            });
    };

    window.closeNicknameModal = function() {
        $('.nickname-modal-overlay, .nickname-modal').remove();
    };

    window.fillNickname = function(nickname) {
        $('#nicknameInput').val(nickname);
    };

    window.clearNickname = function() {
        $('#nicknameInput').val('');
        saveNickname();
    };

    window.saveNickname = function() {
        const nickname = $('#nicknameInput').val().trim();
        
        $.ajax({
            url: '/api/v1/messenger/settings/nickname',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({
                partnerId: currentPartnerId,
                nickname: nickname
            }),
            success: function() {
                showToast(nickname ? 'Đã cập nhật biệt danh!' : 'Đã xóa biệt danh!', 'success');
                closeNicknameModal();
                
                // Update UI
                if (nickname) {
                    $('#infoName').text(nickname);
                    // Update trong conversation list nếu cần
                } else {
                    $('#infoName').text(currentPartnerName);
                }
            },
            error: function() {
                showToast('Lỗi cập nhật biệt danh!', 'error');
            }
        });
    };

    function sendApiRequest(payload) {
        console.log("sendApiRequest payload:", payload);
        
        // Optimistic UI cho TEXT
        // if (payload.type === 'TEXT' && payload.content.trim()) {
        //     const tempMsg = {
        //         id: 'temp-' + Date.now(),
        //         senderId: currentUser.userID,
        //         content: payload.content,
        //         type: 'TEXT',
        //         replyToId: payload.replyToId,
        //         formattedTime: 'Đang gửi...',
        //         status: 'SENDING'
        //     };
        //     appendMessageToUI(tempMsg, true);
        // }
        
        $.ajax({
            url: '/api/v1/messenger/send',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            success: function(msg) {
                console.log("sendApiRequest success:", msg);
                
                // Cập nhật tin nhắn tạm thành tin nhắn thật
                if (payload.type === 'TEXT') {
                    $(`#msg-temp-${msg.id}`).remove();
                    appendMessageToUI(msg, true);
                } else {
                    appendMessageToUI(msg, true);
                }
                
                scrollToBottom();
                // updateConversationPreview(msg);
                
                // KHÔNG gọi loadConversations() - tránh reload
            },
            error: function(e) { 
                console.error("Send Error", e); 
                // Xử lý lỗi cho tin nhắn tạm
                if (payload.type === 'TEXT') {
                    $(`#msg-temp-${msg.id} .bubble`).text('❌ Gửi thất bại').addClass('error');
                }
            }
        });
    }

    // --- FORWARD MESSAGE SYSTEM ---
    window.forwardMessage = function(messageId) {
        console.log("Forward clicked for:", messageId);
        const messageElement = $(`#msg-${messageId}`);
        if (!messageElement.length) {
            console.error("Message not found:", messageId);
            return;
        }
        
        selectedMessageToForward = {
            id: messageId,
            content: messageElement.find('.msg-content').text() || messageElement.find('.bubble').text(),
            type: messageElement.data('type') || 'TEXT',
            sender: currentUser.name
        };
        
        console.log("Selected message to forward:", selectedMessageToForward);
        
        // Show forward modal
        showForwardModal();
    };

    function showForwardModal() {
        if (!selectedMessageToForward) return;
        
        const modal = $('<div class="forward-modal-overlay"></div>');
        const content = $(`
            <div class="forward-modal">
                <div class="forward-header">
                    <h3><i class="fas fa-share"></i> Chuyển tiếp tin nhắn</h3>
                    <button class="close-forward" onclick="closeForwardModal()">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
                <div class="forward-preview">
                    <div class="preview-label">Tin nhắn sẽ chuyển tiếp:</div>
                    <div class="preview-content">
                        ${selectedMessageToForward.content.length > 100 ? 
                        selectedMessageToForward.content.substring(0, 100) + '...' : 
                        selectedMessageToForward.content}
                    </div>
                </div>
                <div class="forward-search">
                    <input type="text" id="forwardSearchInput" placeholder="Tìm người để chuyển tiếp...">
                    <i class="fas fa-search"></i>
                </div>
                <div class="forward-recipients" id="forwardRecipients">
                    <div class="loading-recipients">
                        <i class="fas fa-spinner fa-spin"></i>
                        <span>Đang tải danh sách...</span>
                    </div>
                </div>
                <div class="forward-actions">
                    <button class="btn-cancel" onclick="closeForwardModal()">Hủy</button>
                    <button class="btn-forward" onclick="executeForward()" disabled>
                        Chuyển tiếp
                    </button>
                </div>
            </div>
        `);
        
        $('body').append(modal).append(content);
        
        // Load conversation list for forwarding
        loadForwardRecipients();
        
        // Search functionality
        $('#forwardSearchInput').on('input', function() {
            filterForwardRecipients($(this).val());
        });
    }

    function closeForwardModal() {
        $('.forward-modal-overlay, .forward-modal').remove();
        selectedMessageToForward = null;
        
        if (forwardTimeout) {
            clearTimeout(forwardTimeout);
            forwardTimeout = null;
        }
    }

    function loadForwardRecipients() {
        $.get('/api/v1/messenger/conversations').done(function(conversations) {
            const container = $('#forwardRecipients');
            
            if (!conversations || conversations.length === 0) {
                container.html('<div class="no-conversations">Không có cuộc trò chuyện nào</div>');
                return;
            }
            
            let html = '<div class="recipients-list">';
            conversations.forEach(conv => {
                if (conv.partnerId === currentPartnerId) return; // Skip current chat
                
                html += `
                    <div class="recipient-item" data-id="${conv.partnerId}">
                        <label class="recipient-select">
                            <input type="checkbox" name="forwardTo" value="${conv.partnerId}">
                            <span class="checkmark"></span>
                        </label>
                        <div class="recipient-info">
                            <img src="${conv.partnerAvatar}" class="recipient-avatar">
                            <div class="recipient-details">
                                <div class="recipient-name">${conv.partnerName}</div>
                                <div class="recipient-last-message">${conv.lastMessage || 'Chưa có tin nhắn'}</div>
                            </div>
                        </div>
                    </div>
                `;
            });
            html += '</div>';
            
            container.html(html);
            
            // Enable/disable forward button based on selection
            $('input[name="forwardTo"]').on('change', function() {
                const hasSelection = $('input[name="forwardTo"]:checked').length > 0;
                $('.btn-forward').prop('disabled', !hasSelection);
            });
        });
    }

    function filterForwardRecipients(query) {
        if (!query) {
            $('.recipient-item').show();
            return;
        }
        
        query = query.toLowerCase();
        $('.recipient-item').each(function() {
            const name = $(this).find('.recipient-name').text().toLowerCase();
            $(this).toggle(name.includes(query));
        });
    }

    function executeForward() {
        const selectedRecipients = [];
        $('input[name="forwardTo"]:checked').each(function() {
            selectedRecipients.push($(this).val());
        });
        
        if (selectedRecipients.length === 0 || !selectedMessageToForward) return;
        
        const forwardBtn = $('.btn-forward');
        forwardBtn.prop('disabled', true);
        forwardBtn.html('<i class="fas fa-spinner fa-spin"></i> Đang chuyển tiếp...');
        
        // Send to each recipient
        let completed = 0;
        const total = selectedRecipients.length;
        
        selectedRecipients.forEach(recipientId => {
            const payload = {
                receiverId: parseInt(recipientId),
                content: selectedMessageToForward.content,
                type: 'TEXT',
                metadata: {
                    forwarded: true,
                    originalSender: selectedMessageToForward.sender,
                    originalMessageId: selectedMessageToForward.id
                }
            };
            
            $.ajax({
                url: '/api/v1/messenger/send',
                type: 'POST',
                contentType: 'application/json',
                data: JSON.stringify(payload),
                success: function() {
                    completed++;
                    
                    if (completed === total) {
                        // All forwards completed
                        showForwardSuccess();
                    }
                },
                error: function() {
                    completed++;
                    // Continue even if some fail
                }
            });
        });
        
        // Show undo option for 5 seconds
        let countdown = 5;
        forwardBtn.html(`Đã gửi (Hoàn tác ${countdown}s)`);
        forwardBtn.addClass('sent');
        
        forwardTimeout = setInterval(() => {
            countdown--;
            
            if (countdown > 0) {
                forwardBtn.html(`Đã gửi (Hoàn tác ${countdown}s)`);
            } else {
                clearInterval(forwardTimeout);
                closeForwardModal();
                showToast(`Đã chuyển tiếp tin nhắn đến ${selectedRecipients.length} người`, 'success');
            }
        }, 1000);
        
        // Allow undo
        forwardBtn.off('click').on('click', function() {
            if (countdown > 0) {
                clearInterval(forwardTimeout);
                showToast('Đã hủy chuyển tiếp', 'info');
                closeForwardModal();
            }
        });
    }

    function showForwardSuccess() {
        const forwardBtn = $('.btn-forward');
        forwardBtn.removeClass('sent');
        forwardBtn.html('<i class="fas fa-check"></i> Đã chuyển tiếp');
        forwardBtn.css('background', '#2ecc71');
    }
    // --- FIX: TYPING INDICATOR REAL-TIME ---
    function setupTypingIndicator() {
        $('#msgInput').off('input').on('input', function() {
            if (!currentPartnerId || !stompClient || !stompClient.connected) return;
            
            clearTimeout(typingTimeout);
            
            // Chỉ gửi typing nếu có nội dung
            if ($(this).val().trim().length > 0) {
                stompClient.send('/app/typing', {}, JSON.stringify({
                    receiverId: currentPartnerId,
                    senderId: currentUser.userID,
                    senderName: currentUser.name
                }));
            }
            
            typingTimeout = setTimeout(() => {
                stompClient.send('/app/stop-typing', {}, JSON.stringify({
                    receiverId: currentPartnerId
                }));
            }, 2000);
        });
    }

    // Upload (Fix URL)
    function uploadAndSend(file, type, caption) {
        const formData = new FormData();
        formData.append("file", file);

        window.clearPreview();
        $('#msgInput').val('');

        const tempId = 'up-' + Date.now();
        $('#messagesContainer').append(`<div id="${tempId}" class="text-center small text-muted">Đang tải lên...</div>`);
        scrollToBottom();

        $.ajax({
            url: '/api/upload/image', 
            type: 'POST',
            data: formData,
            processData: false,
            contentType: false,
            success: function(res) {
                $(`#${tempId}`).remove();
                if(res.url) {
                    // Send to server - let sendApiRequest handle UI append
                    sendApiRequest({ 
                        receiverId: currentPartnerId, 
                        content: res.url, 
                        type: type
                    });
                    
                    // Only append caption as separate message if needed
                    if(caption && caption.trim()) {
                        setTimeout(() => {
                            sendApiRequest({ receiverId: currentPartnerId, content: caption, type: 'TEXT' });
                        }, 200);
                    }

                    window.clearPreview();
                }
            },
            error: function(err) {
                console.error("Upload lỗi:", err);
                $(`#${tempId}`).html('<span class="text-danger">Lỗi tải lên</span>');
            }
        });
    }

    // Hàm này gọi từ onchange của input file trong HTML
    window.handleFileSelect = function(input, type) {
        if (input.files && input.files[0]) {
            const file = input.files[0];
            pendingFile = { file: file, type: type };
            
            $('#mediaPreview').show().css('display', 'flex'); // ← THÊM .css('display', 'flex')
            
            if (type === 'IMAGE') {
                const reader = new FileReader();
                reader.onload = function(e) {
                    $('#previewImg').attr('src', e.target.result).show();
                    $('#filePreviewIcon').hide();
                }
                reader.readAsDataURL(file);
            } else {
                $('#previewImg').hide();
                $('#filePreviewIcon').show().css('display', 'flex');
                $('#previewFileName').text(file.name);
            }
        }
    };

    window.clearPreview = function() {
        pendingFile = null;
        $('#imageInput').val('');
        $('#fileInput').val('');
        $('#mediaPreview').hide();
        $('#previewImg').attr('src', '');
    };

    // Expose necessary functions
    window.messengerInit = function() {
        console.log("Messenger initialized with all fixes");
    };

    // Timer Helper
    let timerInterval = null;
    function startTimer() {
        let sec = 0;
        $('#recordTimer').text("00:00");
        timerInterval = setInterval(() => {
            sec++;
            const m = Math.floor(sec / 60).toString().padStart(2, '0');
            const s = (sec % 60).toString().padStart(2, '0');
            $('#recordTimer').text(`${m}:${s}`);
        }, 1000);
    }
    function stopTimer() { clearInterval(timerInterval); }

    // Recording (Gán vào window)
    window.toggleRecording = function() {
        if (!isRecording) {
            // BẮT ĐẦU
            if (!navigator.mediaDevices) return alert("Lỗi Mic");
            
            navigator.mediaDevices.getUserMedia({ audio: true }).then(stream => {
                mediaRecorder = new MediaRecorder(stream);
                audioChunks = [];
                mediaRecorder.ondataavailable = e => audioChunks.push(e.data);
                
                mediaRecorder.start();
                isRecording = true;
                
                // UI: Ẩn input, Hiện recording (Dùng class .show của CSS mới)
                $('.input-actions').hide();
                $('.recording-ui').addClass('show').css('display', 'flex'); // Force flex

                if (recordingTimer) clearInterval(recordingTimer);

                // Timer
                let sec = 0;
                $('#recordTimer').text("00:00");
                recordingTimer = setInterval(() => {
                    sec++;
                    const m = Math.floor(sec/60).toString().padStart(2,'0');
                    const s = (sec%60).toString().padStart(2,'0');
                    $('#recordTimer').text(`${m}:${s}`);
                }, 1000);

                mediaRecorder.onstop = () => {
                    if (!currentPartnerId) return;
                    const blob = new Blob(audioChunks, { type: 'audio/webm' });

                    console.log('Recording stopped — uploading audio blob, size:', blob.size);
                    // Use the centralized helper that posts to /api/upload/audio and shows UI
                    uploadAudioFile(blob);

                    closeRecordingUI();
                };

            }).catch(err => alert("Cần quyền Mic"));
        }
    };

    // --- FIX 6: AUDIO PLAYER ---
    function renderAudioPlayer(audioUrl) {
        const playerId = 'audio-' + Date.now();
        return `
            <div class="msg-audio-player" id="${playerId}">
                <button class="audio-play-btn" onclick="toggleAudioPlay('${playerId}')">
                    <i class="fas fa-play"></i>
                </button>
                <div class="audio-progress-bar" onclick="seekAudio(event, '${playerId}')">
                    <div class="audio-progress-fill" id="${playerId}-progress"></div>
                </div>
                <span class="audio-time" id="${playerId}-time">0:00</span>
                <audio id="${playerId}-audio" preload="metadata">
                    <source src="${audioUrl}" type="audio/webm">
                    <source src="${audioUrl}" type="audio/mpeg">
                </audio>
                <a href="${audioUrl}" download class="audio-download-btn" title="Tải xuống">
                    <i class="fas fa-download"></i>
                </a>
            </div>
        `;
    }

    window.toggleAudioPlay = function(playerId) {
        const audio = document.getElementById(playerId + '-audio');
        const btnIcon = $(`#${playerId} .audio-play-btn i`);
        if (!audio) return;
        if (audio.paused) {
            audio.play();
            btnIcon.removeClass('fa-play').addClass('fa-pause');
        } else {
            audio.pause();
            btnIcon.removeClass('fa-pause').addClass('fa-play');
        }
    };

    window.updateAudioProgress = function(playerId) {
        const audio = document.getElementById(playerId + '-audio');
        if (!audio || !audio.duration) return;
        const progress = (audio.currentTime / audio.duration) * 100;
        $(`#${playerId}-progress`).css('width', progress + '%');
        const cur = Math.floor(audio.currentTime);
        const mins = Math.floor(cur/60);
        const secs = cur % 60;
        $(`#${playerId}-time`).text(`${mins}:${secs.toString().padStart(2,'0')}`);
    };

    window.seekAudio = function(event, playerId) {
        const audio = document.getElementById(playerId + '-audio');
        if (!audio) return;
        const rect = $(`#${playerId} .audio-progress-bar`)[0].getBoundingClientRect();
        const x = event.clientX - rect.left;
        const ratio = Math.max(0, Math.min(1, x / rect.width));
        audio.currentTime = audio.duration * ratio;
        updateAudioProgress(playerId);
    };

    window.onAudioEnded = function(playerId) {
        $(`#${playerId} .audio-play-btn i`).removeClass('fa-pause').addClass('fa-play');
        $(`#${playerId}-progress`).css('width', '0%');
    };

    // --- IN-CHAT SEARCH FEATURE ---
    window.openChatSearch = function() {
        const searchOverlay = $('<div class="chat-search-overlay"></div>');
        const searchModal = $(`
            <div class="chat-search-modal">
                <div class="search-modal-header">
                    <h3><i class="fas fa-search"></i> Tìm kiếm trong đoạn chat</h3>
                    <button class="close-search" onclick="closeChatSearch()">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
                <div class="search-input-container">
                    <input type="text" id="chatSearchInput" placeholder="Nhập từ khóa để tìm...">
                    <button onclick="performChatSearch()">
                        <i class="fas fa-search"></i>
                    </button>
                </div>
                <div class="search-results" id="chatSearchResults">
                    <div class="no-results">
                        <i class="fas fa-search"></i>
                        <p>Nhập từ khóa để tìm kiếm tin nhắn</p>
                    </div>
                </div>
                <div class="search-navigation" style="display: none;">
                    <button onclick="prevSearchResult()">
                        <i class="fas fa-chevron-up"></i> Trước
                    </button>
                    <span id="searchCounter">0/0</span>
                    <button onclick="nextSearchResult()">
                        Sau <i class="fas fa-chevron-down"></i>
                    </button>
                </div>
            </div>
        `);
        
        $('body').append(searchOverlay).append(searchModal);
        
        // Focus input
        setTimeout(() => $('#chatSearchInput').focus(), 100);
        
        // Enter key to search
        $('#chatSearchInput').on('keypress', function(e) {
            if (e.which === 13) performChatSearch();
        });
    };

    function closeChatSearch() {
        $('.chat-search-overlay, .chat-search-modal').remove();
        removeHighlights();
    }

    function performChatSearch() {
        const query = $('#chatSearchInput').val().trim();
        if (!query) return;
        
        searchResults = [];
        currentSearchIndex = -1;
        
        // Find messages containing query
        $('.msg-row').each(function() {
            const messageText = $(this).find('.bubble').text() || 
                            $(this).find('.msg-file .file-name').text() ||
                            '';
            
            if (messageText.toLowerCase().includes(query.toLowerCase())) {
                const messageId = $(this).data('msg-id');
                if (messageId) {
                    searchResults.push({
                        id: messageId,
                        element: $(this),
                        text: messageText
                    });
                }
            }
        });
        
        // Display results
        const resultsContainer = $('#chatSearchResults');
        const navigation = $('.search-navigation');
        
        if (searchResults.length === 0) {
            resultsContainer.html(`
                <div class="no-results">
                    <i class="fas fa-search"></i>
                    <p>Không tìm thấy kết quả cho "${query}"</p>
                </div>
            `);
            navigation.hide();
        } else {
            // Highlight search results
            removeHighlights();
            highlightSearchResults(query);
            
            // Show results list
            let resultsHtml = '<div class="results-list">';
            searchResults.forEach((result, index) => {
                const shortText = result.text.length > 60 ? 
                    result.text.substring(0, 60) + '...' : result.text;
                const date = result.element.find('.msg-timestamp').text();
                
                resultsHtml += `
                    <div class="search-result-item" onclick="goToSearchResult(${index})">
                        <div class="result-preview">
                            <span class="result-text">${highlightText(shortText, query)}</span>
                            <span class="result-date">${date}</span>
                        </div>
                        <i class="fas fa-chevron-right"></i>
                    </div>
                `;
            });
            resultsHtml += '</div>';
            
            resultsContainer.html(resultsHtml);
            navigation.show();
            updateSearchCounter();
            
            // Go to first result
            if (searchResults.length > 0) {
                goToSearchResult(0);
            }
        }
    }

    function highlightSearchResults(query) {
        searchResults.forEach(result => {
            const bubble = result.element.find('.bubble');
            const originalHtml = bubble.html();
            const highlightedHtml = highlightText(originalHtml, query);
            bubble.html(highlightedHtml);
            bubble.addClass('search-highlight');
        });
    }

    function highlightText(text, query) {
        if (!query) return text;
        
        const regex = new RegExp(`(${query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi');
        return text.replace(regex, '<mark class="search-highlight-mark">$1</mark>');
    }

    function removeHighlights() {
        $('.search-highlight-mark').each(function() {
            $(this).replaceWith($(this).text());
        });
        $('.bubble').removeClass('search-highlight');
    }

    function goToSearchResult(index) {
        if (index < 0 || index >= searchResults.length) return;
        
        currentSearchIndex = index;
        const result = searchResults[index];
        
        // Scroll to message
        scrollToMessage(result.id);
        
        // Highlight current result
        $('.search-result-item').removeClass('active');
        $(`.search-result-item:eq(${index})`).addClass('active');
        
        updateSearchCounter();
    }

    function prevSearchResult() {
        if (searchResults.length === 0) return;
        currentSearchIndex = (currentSearchIndex - 1 + searchResults.length) % searchResults.length;
        goToSearchResult(currentSearchIndex);
    }

    function nextSearchResult() {
        if (searchResults.length === 0) return;
        currentSearchIndex = (currentSearchIndex + 1) % searchResults.length;
        goToSearchResult(currentSearchIndex);
    }

    function updateSearchCounter() {
        $('#searchCounter').text(`${currentSearchIndex + 1}/${searchResults.length}`);
    }

    // Khởi tạo Emoji Picker (Thư viện đầy đủ)
    function initEmojiPicker() {
        const trigger = $('#emojiTrigger');
        const input = $('#msgInput');
        
        if (!trigger.length || !input.length) return;

        // Đợi Web Component load xong
        customElements.whenDefined('emoji-picker').then(() => {
            let pickerContainer = $('#emojiPickerContainer');
            if (!pickerContainer.length) {
                pickerContainer = $('<div id="emojiPickerContainer"></div>');
                pickerContainer.css({
                    position: 'fixed',
                    bottom: '100px',
                    right: '20px',
                    display: 'none',
                    zIndex: 9999
                });
                $('body').append(pickerContainer);
                pickerContainer.html('<emoji-picker></emoji-picker>');
            }

            trigger.off('click').on('click', (e) => {
                e.stopPropagation();
                pickerContainer.toggle();
            });

            pickerContainer.on('emoji-click', (e) => {
                input.val(input.val() + e.originalEvent.detail.unicode);
                input.focus();
            });
        });
    }

    // --- 1. LOGIC GHI ÂM (RECORDING) ---

    // Bắt đầu ghi âm: Chuyển UI, Start MediaRecorder
    window.startRecording = function() {
        if (isRecording) return;
        
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            showToast('Trình duyệt không hỗ trợ ghi âm', 'error');
            return;
        }
        
        navigator.mediaDevices.getUserMedia({ audio: true })
            .then(stream => {
                mediaRecorder = new MediaRecorder(stream, {
                    mimeType: 'audio/webm;codecs=opus'
                });
                
                audioChunks = [];
                
                mediaRecorder.ondataavailable = event => {
                    if (event.data.size > 0) {
                        audioChunks.push(event.data);
                    }
                };
                
                mediaRecorder.onstop = () => {
                    const audioBlob = new Blob(audioChunks, { type: 'audio/webm' });
                    uploadAudioFile(audioBlob);
                    
                    // Stop all tracks
                    stream.getTracks().forEach(track => track.stop());
                };
                
                // Start recording
                mediaRecorder.start();
                isRecording = true;
                recordingStartTime = Date.now();
                
                // Show recording UI
                $('#normalInputState').hide();
                $('#recordingState').show();
                
                // Start timer
                updateRecordingTimer();
                recordingTimer = setInterval(updateRecordingTimer, 1000);
                
            })
            .catch(err => {
                console.error('Lỗi truy cập microphone:', err);
                showToast('Không thể truy cập microphone. Vui lòng kiểm tra quyền.', 'error');
            });
    };

    // Hủy ghi âm: Dừng Recorder (không lưu), Reset UI
    window.cancelRecording = function() {
        if (!isRecording) return;
        
        // Stop recording
        if (mediaRecorder && mediaRecorder.state !== 'inactive') {
            mediaRecorder.stop();
        }
        
        resetRecordingUI();
        showToast('Đã hủy ghi âm', 'info');
    };

    // Hoàn tất & Gửi: Dừng Recorder -> Trigger onstop -> Upload
    window.finishRecording = function() {
        if (!isRecording) return;
        
        if (mediaRecorder && mediaRecorder.state !== 'inactive') {
            mediaRecorder.stop();
        }
        
        resetRecordingUI();
    };

    function closeRecordingUI() {
        isRecording = false;
        clearInterval(timerInterval);
        $('.recording-ui').removeClass('show').hide();
        $('.input-actions').show();
    }

    function resetRecordingUI() {
        isRecording = false;
        recordingStartTime = 0;
        
        // Clear timer
        if (recordingTimer) {
            clearInterval(recordingTimer);
            recordingTimer = null;
        }
        
        // Reset UI
        $('.recording-ui').removeClass('show').hide();
        $('.input-actions').show();
        $('#recordTimer').text('00:00');
    }

    function updateRecordingTimer() {
        if (!recordingStartTime) return;
        
        const elapsed = Date.now() - recordingStartTime;
        const seconds = Math.floor(elapsed / 1000);
        const minutes = Math.floor(seconds / 60);
        const remainingSeconds = seconds % 60;
        
        const timeString = `${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}`;
        $('#recordTimer').text(timeString);
        
        // Auto-stop after 5 minutes
        if (seconds >= 300) {
            finishRecording();
        }
    }

    function uploadAudioFile(audioBlob) {
        if (!currentPartnerId) {
            showToast('Vui lòng chọn người nhận', 'error');
            return;
        }
        
        // Create FormData
        const formData = new FormData();
        const fileName = `audio_${Date.now()}.webm`;
        formData.append('file', audioBlob, fileName);
        formData.append('type', 'AUDIO');
        
        // Show uploading indicator
        const tempId = 'audio-upload-' + Date.now();
        $('#messagesContainer').append(`
            <div id="${tempId}" class="msg-row mine">
                <div class="msg-content">
                    <div class="bubble uploading-audio">
                        <i class="fas fa-spinner fa-spin"></i>
                        <span>Đang tải lên...</span>
                    </div>
                </div>
            </div>
        `);
        scrollToBottom();
        
        // Upload to server
        $.ajax({
            url: '/api/upload/audio', // Cần tạo endpoint này
            type: 'POST',
            data: formData,
            processData: false,
            contentType: false,
            success: function(response) {
                $(`#${tempId}`).remove();
                
                if (response.url) {
                    // Send message with audio URL
                    const payload = {
                        receiverId: currentPartnerId,
                        content: response.url,
                        type: 'AUDIO',
                        metadata: {
                            duration: response.duration || 0,
                            size: response.size || 0
                        }
                    };
                    
                    sendApiRequest(payload);
                }
            },
            error: function(err) {
                console.error('Upload audio error:', err);
                $(`#${tempId}`).remove();
                
                const errorId = 'audio-error-' + Date.now();
                $('#messagesContainer').append(`
                    <div id="${errorId}" class="msg-row mine">
                        <div class="msg-content">
                            <div class="bubble error">
                                <i class="fas fa-exclamation-triangle"></i>
                                <span>Lỗi tải lên file âm thanh</span>
                            </div>
                        </div>
                    </div>
                `);
            }
        });
    }

    // Enhanced audio player
    function renderAudioPlayer(audioUrl) {
        const playerId = 'audio-player-' + Date.now();
        
        return `
            <div class="msg-audio-player" id="${playerId}">
                <button class="audio-play-btn" onclick="toggleAudioPlay('${playerId}')">
                    <i class="fas fa-play"></i>
                </button>
                <div class="audio-progress-container" onclick="seekAudio(event, '${playerId}')">
                    <div class="audio-progress-bar">
                        <div class="audio-progress-fill" id="${playerId}-progress"></div>
                    </div>
                    <div class="audio-time-display">
                        <span id="${playerId}-current-time">0:00</span>
                        <span id="${playerId}-duration">0:00</span>
                    </div>
                </div>
                <audio id="${playerId}-audio" preload="metadata"
                    onloadedmetadata="initAudioPlayer('${playerId}')"
                    ontimeupdate="updateAudioProgress('${playerId}')"
                    onended="onAudioEnded('${playerId}')">
                    <source src="${audioUrl}" type="audio/webm">
                    <source src="${audioUrl}" type="audio/mpeg">
                </audio>
                <a href="${audioUrl}" download class="audio-download-btn" title="Tải xuống">
                    <i class="fas fa-download"></i>
                </a>
            </div>
        `;
    }

    // --- FIX 9: TYPING INDICATOR ---
    function showTypingIndicator() {
        const indicator = $('#typingIndicator');
        if (!indicator.length) {
            const html = `
                <div id="typingIndicator" class="typing-indicator">
                    <img src="${$('#headerAvatar').attr('src')}" style="width:20px; height:20px; border-radius:50%;">
                    <div class="typing-dots">
                        <span class="typing-dot"></span>
                        <span class="typing-dot"></span>
                        <span class="typing-dot"></span>
                    </div>
                </div>
            `;
            $('#messagesContainer').append(html);
        } else {
            indicator.addClass('active');
        }
        
        scrollToBottom();
        notificationSound.play().catch(() => {});
    }

    function hideTypingIndicator() {
        $('#typingIndicator').removeClass('active');
    }

    // Send typing event via WebSocket
    $('#msgInput').on('input', function() {
        if (!currentPartnerId) return;
        
        clearTimeout(typingTimeout);
        
        if (stompClient && stompClient.connected) {
            stompClient.send('/app/typing', {}, JSON.stringify({
                receiverId: currentPartnerId,
                senderId: currentUser.userID
            }));
        }
        
        typingTimeout = setTimeout(() => {
            if (stompClient && stompClient.connected) {
                stompClient.send('/app/stop-typing', {}, JSON.stringify({
                    receiverId: currentPartnerId
                }));
            }
        }, 2000);
    });

    // --- FIX 10: SEEN AVATAR ---
    function updateSeenAvatar(messageId) {
        if (!messageId || lastSeenMessageId === messageId) return;
        
        // Remove old seen avatar
        $('.msg-seen-avatar').remove();
        
        // Add new seen avatar
        const msgRow = $(`#msg-${messageId}`);
        if (msgRow.length && msgRow.hasClass('mine')) {
            const avatar = $('#headerAvatar').attr('src');
            msgRow.find('.msg-content').append(`<img src="${avatar}" class="msg-seen-avatar">`);
            lastSeenMessageId = messageId;
        }
    }
    
    

    // --- 8. STICKER LOGIC (MESSENGER STYLE) ---

    // Toggle Sticker Menu (Messenger Style)
    window.toggleStickers = function() {
        const menu = $('#stickerMenu');
        const input = $('#msgInput');
        
        if (menu.hasClass('show')) {
            menu.removeClass('show').hide();
        } else {
            // Hide suggestions if open
            hideStickerSuggestions();
            
            // Position menu properly
            menu.css({
                bottom: '80px',
                left: '20px'
            });
            
            menu.addClass('show').css('display', 'flex');
            
            // Load stickers if not loaded
            if ($('#stickerGrid').is(':empty')) {
                renderStickerCollection(currentStickerCollection);
            }
            
            // Render recent stickers
            renderRecentStickers();
        }
    };

    // Render Sticker Collection
    function renderStickerCollection(collectionId) {
        const grid = $('#stickerGrid');
        const collection = window.STICKER_COLLECTIONS[collectionId];
        
        if (!collection) {
            grid.html('<div class="text-center p-4 text-muted">Không có sticker</div>');
            return;
        }
        
        let html = '';
        collection.items.forEach((sticker, index) => {
            html += `
                <div class="sticker-item" onclick="sendSticker('${sticker.url}', '${collectionId}', ${index})">
                    <img src="${sticker.url}" alt="Sticker" loading="lazy">
                </div>
            `;
        });
        
        grid.html(html);
        $('#recentStickersSection').toggle(recentStickers.length > 0);
    }

    // Render recent stickers
    function renderRecentStickers() {
        const recentStickers = window.getRecentStickers();
        const grid = $('#recentStickersGrid');
        
        if (recentStickers.length === 0) {
            $('#recentStickersSection').hide();
            return;
        }
        
        let html = '';
        recentStickers.forEach(sticker => {
            html += `
                <div class="sticker-item recent" onclick="sendTenorSticker('${sticker.id}', '${encodeURIComponent(JSON.stringify(sticker))}')">
                    <img src="${sticker.preview || sticker.url}" alt="Sticker">
                </div>
            `;
        });
        
        grid.html(html);
    }

    // Debounced search
    let searchTimeout;
    function searchStickersDebounced(query) {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            performStickerSearch(query);
        }, 300);
    }

    // Tìm kiếm stickers
    async function performStickerSearch(query) {
        const grid = $('#stickerGrid');
        
        if (!query || query.trim() === '') {
            // Quay lại category hiện tại
            const activeCategory = $('.tab-btn.active').data('category') || 'popular';
            loadStickerCategory(activeCategory);
            return;
        }
        
        grid.html('<div class="loading-stickers"><i class="fas fa-spinner fa-spin"></i><p>Đang tìm kiếm...</p></div>');
        
        const stickers = await window.searchTenorStickers(query);
        renderStickerGrid(stickers);
    }

    // Gửi sticker từ Tenor
    window.sendTenorSticker = function(stickerId, stickerData) {
        try {
            const sticker = JSON.parse(decodeURIComponent(stickerData));
            
            // Thêm vào recent
            window.addToRecentStickers(sticker);
            
            // Đóng menu
            $('#stickerMenu').hide();
            
            // Gửi qua API
            if (currentPartnerId) {
                const payload = {
                    receiverId: currentPartnerId,
                    content: sticker.url,
                    type: 'STICKER',
                    metadata: {
                        source: 'tenor',
                        stickerId: sticker.id,
                        width: sticker.width,
                        height: sticker.height
                    }
                };
                
                sendApiRequest(payload);
            }
        } catch (error) {
            console.error('Lỗi gửi sticker:', error);
        }
    };

    // Add to Recent Stickers
    function addToRecentStickers(stickerUrl) {
        // Remove if exists
        recentStickers = recentStickers.filter(s => s !== stickerUrl);
        
        // Add to beginning
        recentStickers.unshift(stickerUrl);
        
        // Keep only last 12
        recentStickers = recentStickers.slice(0, 12);
        
        // Save to localStorage
        localStorage.setItem('recentStickers', JSON.stringify(recentStickers));
        
        // Update UI
        renderRecentStickers();
    }

    function searchStickers(query) {
        const grid = $('#stickerGrid');
        
        if (!query.trim()) {
            renderStickerCollection(currentStickerCollection);
            return;
        }
        
        query = query.toLowerCase();
        let results = [];
        
        // Search in all collections
        Object.values(window.STICKER_COLLECTIONS).forEach(collection => {
            collection.items.forEach(sticker => {
                if (sticker.tags && sticker.tags.some(tag => tag.includes(query))) {
                    results.push(sticker);
                }
            });
        });
        
        if (results.length === 0) {
            grid.html('<div class="text-center p-4 text-muted">Không tìm thấy sticker phù hợp</div>');
            return;
        }
        
        let html = '';
        results.slice(0, 24).forEach((sticker, index) => {
            html += `
                <div class="sticker-item" onclick="sendSticker('${sticker.url}', 'search', ${index})">
                    <img src="${sticker.url}" alt="Sticker">
                </div>
            `;
        });
        
        grid.html(html);
    }

    // Switch Sticker Collection
    window.switchStickerCollection = function(collectionId, element) {
        currentStickerCollection = collectionId;
        
        // Update active state
        $('.collection-btn').removeClass('active');
        $(element).addClass('active');
        
        // Render collection
        renderStickerCollection(collectionId);
    };

    // Search Stickers
    window.searchStickers = function(query) {
        if (!query.trim()) {
            renderStickerCollection(currentStickerCollection);
            return;
        }
        
        const grid = $('#stickerGrid');
        grid.empty();
        
        query = query.toLowerCase();
        let foundStickers = [];
        
        // Search in all collections
        Object.values(STICKER_COLLECTIONS).forEach(collection => {
            collection.items.forEach(sticker => {
                // Search in tags
                const matches = sticker.tags.some(tag => tag.includes(query));
                if (matches) {
                    foundStickers.push(sticker);
                }
            });
        });
        
        if (foundStickers.length === 0) {
            grid.html('<div class="text-center text-muted p-4">Không tìm thấy sticker phù hợp</div>');
            return;
        }
        
        // Display found stickers
        foundStickers.forEach(sticker => {
            const item = $(`
                <div class="sticker-item" data-sticker-id="${sticker.id}" data-url="${sticker.url}">
                    <img src="${sticker.url}" alt="Sticker" style="width: 100%; height: 100%;">
                </div>
            `);
            
            item.on('click', function() {
                sendSticker(sticker.url);
                addToRecentStickers(sticker);
            });
            
            grid.append(item);
        });
    };

    // --- 9. STICKER SUGGESTIONS (ZALO STYLE) ---

    // messenger.js - FIX 4: Real-time Sticker Suggestions
    let suggestionDebounce;

    function initStickerSuggestions() {
        const msgInput = $('#msgInput');
        
        msgInput.on('input', async function() {
            const message = $(this).val().trim();
            
            clearTimeout(suggestionDebounce);
            
            if (message.length >= 2) {
                suggestionDebounce = setTimeout(async () => {
                    const suggestions = await window.getStickerSuggestions(message);
                    showStickerSuggestions(suggestions);
                }, 500);
            } else {
                hideStickerSuggestions();
            }
        });
    }

    function showStickerSuggestions(stickers) {
        if (!stickers || stickers.length === 0) {
            hideStickerSuggestions();
            return;
        }
        
        const container = $('#stickerSuggestions');
        const grid = $('#suggestionsGrid');
        
        grid.empty();
        
        stickers.slice(0, 12).forEach(sticker => {
            grid.append(`
                <div class="sticker-item" onclick="sendTenorSticker('${sticker.id}', '${encodeURIComponent(JSON.stringify(sticker))}')">
                    <img src="${sticker.preview || sticker.url}" alt="Sticker">
                </div>
            `);
        });
        
        container.css('display', 'block');
        setTimeout(() => container.css('opacity', 1), 10);
    }

    function hideStickerSuggestions() {
        $('#stickerSuggestions').css('opacity', 0);
        setTimeout(() => $('#stickerSuggestions').hide(), 300);
    }

    // Find Sticker Suggestions by Keywords
    function findStickerSuggestions(keywords) {
        const suggestions = new Set();
        const keywordList = keywords.toLowerCase().split(' ');
        
        keywordList.forEach(keyword => {
            if (STICKER_SUGGESTIONS[keyword]) {
                STICKER_SUGGESTIONS[keyword].forEach(stickerId => {
                    // Find sticker in all collections
                    Object.values(STICKER_COLLECTIONS).forEach(collection => {
                        const sticker = collection.items.find(s => s.id === stickerId);
                        if (sticker) {
                            suggestions.add(sticker);
                        }
                    });
                });
            }
            
            // Also search in tags
            Object.values(STICKER_COLLECTIONS).forEach(collection => {
                collection.items.forEach(sticker => {
                    if (sticker.tags.some(tag => tag.includes(keyword))) {
                        suggestions.add(sticker);
                    }
                });
            });
        });
        
        return Array.from(suggestions);
    }

    // Analyze message for sticker suggestions
    function analyzeMessageForStickers(message) {
        const words = message.toLowerCase().split(/\s+/);
        const stickerKeywords = [
            'cười', 'vui', 'buồn', 'khóc', 'yêu', 'tim', 'ok', 'like',
            'cảm ơn', 'hoan hô', 'wink', 'dễ thương', 'ngon', 'ngầu',
            'giận', 'tức', 'sợ', 'hoảng', 'ngượng', 'chó', 'mèo', 'cún',
            'thỏ', 'cáo', 'gấu', 'heo', 'hổ', 'ngựa', 'hamburger', 'bánh',
            'kem', 'kẹo', 'party', 'tiệc', 'quà', 'pháo hoa', 'noel',
            'halloween', 'ý tưởng', 'bom', 'ngủ', 'mồ hôi', 'cơ bắp',
            'khỏe', 'chóng mặt', 'nói', 'suy nghĩ', 'hôn', 'kim cương',
            'hoa', 'chạy', 'bóng đá', 'bóng rổ', 'tennis', 'bơi', 'golf'
        ];
        
        return words.filter(word => stickerKeywords.some(keyword => 
            keyword.includes(word) || word.includes(keyword)
        ));
    }

    // Initialize Sticker Menu HTML
    function initStickerMenu() {
        const menu = $('#stickerMenu');
        
        // HTML mới với design như Zalo
        menu.html(`
            <div class="sticker-header">
                <div class="sticker-tabs" id="stickerTabs">
                    ${Object.entries(window.TENOR_CATEGORIES).map(([id, cat]) => `
                        <button class="tab-btn ${id === 'popular' ? 'active' : ''}" 
                                data-category="${id}" 
                                onclick="switchStickerCategory('${id}', this)">
                            ${cat.name}
                        </button>
                    `).join('')}
                </div>
                <div class="sticker-header-actions">
                    <div class="sticker-search-box">
                        <input type="text" id="stickerSearchInput" placeholder="Tìm kiếm stickers..." 
                            onkeyup="searchStickersDebounced(this.value)">
                        <i class="fas fa-search"></i>
                    </div>
                    <i class="fas fa-times close-sticker" onclick="window.toggleStickers()"></i>
                </div>
            </div>
            
            <div class="sticker-content">
                <div class="sticker-grid" id="stickerGrid">
                    <div class="loading-stickers">
                        <i class="fas fa-spinner fa-spin"></i>
                        <p>Đang tải stickers...</p>
                    </div>
                </div>
                
                <div class="recent-stickers-section" id="recentStickersSection" style="display: none;">
                    <div class="section-title">
                        <i class="fas fa-history"></i>
                        <span>Gần đây</span>
                    </div>
                    <div class="recent-stickers-grid" id="recentStickersGrid"></div>
                </div>
            </div>
        `);
        
        // Load stickers phổ biến đầu tiên
        loadStickerCategory('popular');
        renderRecentStickers();
    }

    // Hàm load category mới
    async function loadStickerCategory(category) {
        const grid = $('#stickerGrid');
        grid.html('<div class="loading-stickers"><i class="fas fa-spinner fa-spin"></i><p>Đang tải...</p></div>');
        
        const stickers = await window.loadTenorStickers(category);
        renderStickerGrid(stickers);
        
        // Hiện recent section nếu có
        const recentStickers = window.getRecentStickers();
        if (recentStickers.length > 0) {
            $('#recentStickersSection').show();
            renderRecentStickers();
        }
    }

    // Render sticker grid
    function renderStickerGrid(stickers) {
        const grid = $('#stickerGrid');
        
        if (!stickers || stickers.length === 0) {
            grid.html('<div class="no-stickers"><i class="fas fa-image"></i><p>Không có stickers</p></div>');
            return;
        }
        
        let html = '';
        stickers.forEach(sticker => {
            html += `
                <div class="sticker-item" onclick="sendTenorSticker('${sticker.id}', '${encodeURIComponent(JSON.stringify(sticker))}')">
                    <img src="${sticker.preview || sticker.url}" 
                        data-src="${sticker.url}" 
                        alt="Sticker" 
                        loading="lazy"
                        class="sticker-gif">
                    <div class="sticker-hover">
                        <i class="fas fa-paper-plane"></i>
                    </div>
                </div>
            `;
        });
        
        grid.html(html);
        
        // Lazy load ảnh
        $('.sticker-gif').each(function() {
            const img = $(this);
            if (img.attr('data-src')) {
                img.attr('src', img.attr('data-src'));
                img.removeAttr('data-src');
            }
        });
    }

    function renderStickerCollection(collectionId) {
        const grid = $('#stickerGrid');
        const collection = window.STICKER_COLLECTIONS[collectionId];
        
        if (!collection) return;
        
        grid.empty();
        collection.items.forEach(sticker => {
            const item = $(`<img src="${sticker.url}" class="sticker-item" style="width:80px; height:80px; cursor:pointer; border-radius:4px; padding:5px; transition:0.2s;">`);
            item.on('click', function() {
                window.sendSticker(sticker.url);
            });
            grid.append(item);
        });
    }

    window.switchStickerCollection = function(id, btn) {
        $('.collection-btn').removeClass('active');
        $(btn).addClass('active');
        renderStickerCollection(id);
    };

    window.searchStickers = function(query) {
        if (!query.trim()) {
            renderStickerCollection('popular');
            return;
        }
        
        const grid = $('#stickerGrid');
        grid.empty();
        
        let found = [];
        Object.values(window.STICKER_COLLECTIONS).forEach(col => {
            col.items.forEach(s => {
                if (s.tags.some(tag => tag.includes(query.toLowerCase()))) {
                    found.push(s);
                }
            });
        });
        
        if (found.length === 0) {
            grid.html('<div class="text-center p-3">Không tìm thấy</div>');
            return;
        }
        
        found.forEach(s => {
            const item = $(`<img src="${s.url}" class="sticker-item" style="width:80px; height:80px; cursor:pointer;">`);
            item.on('click', () => window.sendSticker(s.url));
            grid.append(item);
        });
    };

    // Enhanced Message Input with Sticker Suggestions
    function setupStickerSuggestions() {
        const input = $('#msgInput');
        
        input.on('input', function() {
            const message = $(this).val().trim();
            
            if (suggestionTimeout) {
                clearTimeout(suggestionTimeout);
            }
            
            if (message.length >= 2) {
                suggestionTimeout = setTimeout(() => {
                    const keywords = analyzeMessageForStickers(message);
                    if (keywords.length > 0) {
                        showStickerSuggestions(keywords);
                    } else {
                        hideStickerSuggestions();
                    }
                }, 500);
            } else {
                hideStickerSuggestions();
            }
        });
        
        // Hide suggestions when clicking outside
        $(document).on('click', function(e) {
            if (!$(e.target).closest('#stickerSuggestions, #msgInput').length) {
                hideStickerSuggestions();
            }
        });
    }

    // Send Sticker Function (Updated)
    window.sendSticker = function(url, source, index) {
        if (!currentPartnerId) {
            showToast('Vui lòng chọn người nhận trước', 'error');
            return;
        }
        
        // Close sticker menu
        $('#stickerMenu').hide();
        
        // Add to recent
        addToRecentStickers(url);
        
        // Send via API
        const payload = {
            receiverId: currentPartnerId,
            content: url,
            type: 'STICKER',
            metadata: {
                source: source,
                index: index
            }
        };
        
        sendApiRequest(payload);
    };

    // --- 6. URL CHECK (NGƯỜI LẠ) ---
    // messenger.js - checkUrlAndOpenChat()
    function checkUrlAndOpenChat(existingConversations) {
        const urlParams = new URLSearchParams(window.location.search);
        const uid = urlParams.get('uid');
        if(!uid) return;
        
        const targetId = parseInt(uid);
        
        // Tìm trong danh sách hội thoại hiện có
        const existing = existingConversations.find(c => c.partnerId === targetId);
        
        if(existing) {
            window.selectConversation(
                existing.partnerId, 
                existing.partnerName, 
                existing.partnerAvatar, 
                existing.friend,
                existing.isOnline,
                existing.lastActive
            );
        } else {
            // Nếu chưa có hội thoại, tạo mới và load thông tin user
            $.get(`/api/users/${targetId}`).done(function(u) {
                const avatar = u.avatar || `https://ui-avatars.com/api/?name=${encodeURIComponent(u.userName)}`;
                window.selectConversation(u.userID, u.userName, avatar, false, false, null);
                
                // Tạo tin nhắn chào mừng tự động
                setTimeout(() => {
                    const welcomeMsg = {
                        id: 'welcome-' + Date.now(),
                        senderId: currentUser.userID,
                        content: `Xin chào! Tôi là ${currentUser.name}. Rất vui được kết nối với bạn!`,
                        type: 'TEXT',
                        formattedTime: 'Vừa xong'
                    };
                    appendMessageToUI(welcomeMsg, true);
                }, 1000);
            });
        }
    }

    // Events Listener
    // $(document).on('click', '.emoji-btn', function() {
    //     const input = $('#msgInput');
    //     input.val(input.val() + "😊");
    //     input.focus();
    // });

    // messenger.js - bindEvents()
    $('.search-wrapper input').on('input', function() {
        const query = $(this).val().toLowerCase();
        $('.conv-item').each(function() {
            const name = $(this).find('.conv-name').text().toLowerCase();
            $(this).toggle(name.includes(query));
        });
    });
    // --- REPLY & UNSEND LOGIC ---
    

    window.startReply = function(msgId, name, content) {
        replyToId = msgId;
        // Hiện thanh Replying Bar (Cần thêm HTML vào footer ở bước sau)
        $('#replyingBar').css('display', 'flex');
        $('#replyingBar').css('display', 'flex').html(`
            <span>Đang trả lời ${name}: ${content}</span>
            <i class="fas fa-times" onclick="window.cancelReply()" style="cursor:pointer;margin-left:auto;"></i>
        `);
        $('#msgInput').focus();
    };

    window.cancelReply = function() {
        replyToId = null;
        $('#replyingBar').hide();
    };

    window.unsendMessage = function(msgId) {
        if(!confirm("Thu hồi tin nhắn này?")) return;
        
        $.post(`/api/v1/messenger/unsend/${msgId}`, function() {
            // Update UI ngay lập tức
            const bubble = $(`#msg-${msgId} .msg-content`);
            bubble.addClass('deleted').removeAttr('style').text('Tin nhắn đã bị thu hồi');
            $(`#msg-${msgId} .msg-actions`).remove(); // Xóa menu action
        });
    };

    // [CẬP NHẬT HÀM GỬI TIN] Để kèm replyToId
    window.sendTextMessage = function() {
        const content = $('#msgInput').val().trim();

        if (pendingFile) {
            uploadAndSend(pendingFile.file, pendingFile.type, content);
            return;
        }

        if (content && currentPartnerId) {
            const payload = { 
                receiverId: currentPartnerId, 
                content: content, 
                type: 'TEXT',
                replyToId: replyToId
            };
            
            sendApiRequest(payload);
            $('#msgInput').val('').focus();
            window.cancelReply();
        }
    };




    // --- 9. SIDEBAR INFO LOGIC ---

    // Toggle Sidebar
    window.toggleChatInfo = function() {
        const sidebar = $('#chatInfoSidebar');
        const chatArea = $('.msg-chat-area');
        const btn = $('#btnToggleInfo');
        
        if (sidebar.hasClass('hidden')) {
            sidebar.removeClass('hidden');
            chatArea.addClass('info-open');
            btn.addClass('active');
            loadSharedMedia();
        } else {
            sidebar.addClass('hidden');
            chatArea.removeClass('info-open');
            btn.removeClass('active');
        }
    };

    // Update Info Sidebar khi chọn hội thoại
    function updateInfoSidebar(name, avatar) {
        $('#infoName').text(name);
        $('#infoAvatar').attr('src', avatar);
        
        // Thêm các nút chức năng mới
        $('.accordion-content:first').html(`
            <div class="info-action-btn" onclick="window.openThemePicker()">
                <i class="fas fa-palette" style="color: var(--msg-blue);"></i> Đổi chủ đề
            </div>
            <div class="info-action-btn" onclick="window.openNicknameModal()">
                <i class="fas fa-font"></i> Chỉnh sửa biệt danh
            </div>
            <div class="info-action-btn" onclick="window.openBackgroundPicker()">
                <i class="fas fa-image"></i> Đổi nền chat
            </div>
            <div class="info-action-btn" onclick="window.viewChatStats()">
                <i class="fas fa-chart-bar"></i> Thống kê đoạn chat
            </div>
        `);
    }

    // --- FIX 2: ONLINE STATUS UPDATE ---
    function updateOnlineStatus(partnerId, isOnline, lastActive) {
        // Cập nhật trong conversation list
        $(`.conv-item[onclick*="${partnerId}"] .online-dot`).toggle(isOnline);
        
        // Cập nhật trong chat header nếu đang chat với người này
        if (currentPartnerId == partnerId) {
            const statusDiv = $('#chatHeaderStatus');
            if (statusDiv.length) {
                if (isOnline) {
                    statusDiv.html(`<small class="text-success"><i class="fas fa-circle" style="font-size:8px;"></i> Đang hoạt động</small>`);
                } else {
                    const timeAgo = lastActive ? formatTimeAgo(lastActive) : 'Không hoạt động';
                    statusDiv.html(`<small class="text-muted">${timeAgo}</small>`);
                }
            }
        }
    }

    function formatTimeAgo(timestamp) {
        const now = new Date();
        const time = new Date(timestamp);
        const diffMs = now - time;
        const diffMins = Math.floor(diffMs / 60000);
        
        if (diffMins < 1) return 'Vừa xong';
        if (diffMins < 60) return `${diffMins} phút trước`;
        
        const diffHours = Math.floor(diffMins / 60);
        if (diffHours < 24) return `${diffHours} giờ trước`;
        
        const diffDays = Math.floor(diffHours / 24);
        return `${diffDays} ngày trước`;
    }

    // ============= BACKGROUND PICKER FUNCTION =============
    window.openBackgroundPicker = function() {
        if (!currentPartnerId) return;
        
        const modal = $('<div class="background-modal-overlay"></div>');
        const content = $(`
            <div class="background-modal">
                <div class="background-modal-header">
                    <h3><i class="fas fa-image"></i> Đổi nền chat</h3>
                    <button class="close-background-modal" onclick="window.closeBackgroundPicker()">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
                <div class="background-options">
                    <div class="background-colors">
                        <h4>Màu sắc</h4>
                        <div class="color-grid">
                            <div class="bg-option" style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);" onclick="window.applyBackground('linear-gradient(135deg, #667eea 0%, #764ba2 100%)')"></div>
                            <div class="bg-option" style="background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);" onclick="window.applyBackground('linear-gradient(135deg, #f093fb 0%, #f5576c 100%)')"></div>
                            <div class="bg-option" style="background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);" onclick="window.applyBackground('linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)')"></div>
                            <div class="bg-option" style="background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);" onclick="window.applyBackground('linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)')"></div>
                            <div class="bg-option" style="background: linear-gradient(135deg, #fa709a 0%, #fee140 100%);" onclick="window.applyBackground('linear-gradient(135deg, #fa709a 0%, #fee140 100%)')"></div>
                            <div class="bg-option" style="background: linear-gradient(135deg, #30cfd0 0%, #330867 100%);" onclick="window.applyBackground('linear-gradient(135deg, #30cfd0 0%, #330867 100%)')"></div>
                        </div>
                    </div>
                    
                    <div class="background-patterns">
                        <h4>Mẫu</h4>
                        <div class="pattern-grid">
                            <div class="bg-option" style="background-image: url('data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%22100%22 height=%22100%22><rect fill=%22%23f5f5f5%22 width=%22100%22 height=%22100%22/><circle cx=%2250%22 cy=%2250%22 r=%2220%22 fill=%22%23ddd%22/></svg>');" onclick="window.applyBackground('url(data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%22100%22 height=%22100%22><rect fill=%22%23f5f5f5%22 width=%22100%22 height=%22100%22/><circle cx=%2250%22 cy=%2250%22 r=%2220%22 fill=%22%23ddd%22/></svg>)')"></div>
                        </div>
                    </div>

                    <div class="background-default">
                        <h4>Mặc định</h4>
                        <button class="default-btn" onclick="window.applyBackground('default')">
                            <i class="fas fa-redo"></i> Khôi phục nền mặc định
                        </button>
                    </div>
                </div>
            </div>
        `);
        
        $('body').append(modal).append(content);
    };

    window.closeBackgroundPicker = function() {
        $('.background-modal-overlay, .background-modal').remove();
    };

    window.applyBackground = function(background) {
        // Save to localStorage
        localStorage.setItem(`chatBg_${currentPartnerId}`, background);
        
        // Apply to current chat
        if (background === 'default') {
            $('#messagesContainer').css('background', '');
        } else {
            $('#messagesContainer').css('background', background);
        }
        
        // Save to server (optional)
        $.post('/api/v1/messenger/settings/background', {
            partnerId: currentPartnerId,
            background: background
        });
        
        showToast('Đã cập nhật nền chat', 'success');
        window.closeBackgroundPicker();
    };


    // --- 9. SIDEBAR & SETTINGS LOGIC ---

    // Toggle Sidebar Info
    window.toggleChatInfo = function() {
        const sidebar = $('#chatInfoSidebar');
        const btn = $('#btnToggleInfo');
        
        if (sidebar.hasClass('hidden')) {
            sidebar.removeClass('hidden');
            btn.addClass('active');
            // Load media khi mở sidebar
            loadSharedMedia();
        } else {
            sidebar.addClass('hidden');
            btn.removeClass('active');
        }
    };

    // --- FIX: SCROLL TO MESSAGE ---
    window.scrollToMessage = function(messageId) {
        const messageElement = $(`#msg-${messageId}`);
        if (messageElement.length) {
            const container = $('#messagesContainer');
            const containerTop = container.offset().top;
            const messageTop = messageElement.offset().top;
            const scrollTo = messageTop - containerTop - 100;
            
            container.animate({
                scrollTop: scrollTo
            }, 500);
            
            // Highlight effect
            messageElement.addClass('highlighted');
            setTimeout(() => {
                messageElement.removeClass('highlighted');
            }, 2000);
        }
    };

    // Toggle Accordion Item
    window.toggleAccordion = function(header) {
        $(header).parent().toggleClass('active');
    };

    // Switch Tab Ảnh/File
    window.switchMediaTab = function(tab) {
        $('.media-tab').removeClass('active');
        if (tab === 'img') {
            $('.media-tab:first-child').addClass('active');
            $('#sharedImagesGrid').show();
            $('#sharedFilesList').hide();
        } else {
            $('.media-tab:last-child').addClass('active');
            $('#sharedImagesGrid').hide();
            $('#sharedFilesList').show();
        }
    };

    // Load Shared Media từ API
    function loadSharedMedia() {
        if (!currentPartnerId) return;
        
        const grid = $('#sharedImagesGrid');
        const fileList = $('#sharedFilesList');
        grid.html('<div class="text-center w-100 small text-muted">Đang tải...</div>');

        $.get(`/api/v1/messenger/media/${currentPartnerId}`, function(data) {
            grid.empty();
            fileList.empty();

            if (!data || data.length === 0) {
                grid.html('<div class="text-center w-100 small text-muted">Chưa có file nào</div>');
                return;
            }

            data.forEach(msg => {
                if (msg.type === 'IMAGE' || msg.type === 'STICKER') {
                    // Render Ảnh
                    grid.append(`<div class="media-thumb" style="background-image: url('${msg.content}')" onclick="window.open('${msg.content}')"></div>`);
                } else if (msg.type === 'FILE' || msg.type === 'AUDIO') {
                    // Render File
                    const name = msg.content.split('/').pop() || 'File đính kèm';
                    const icon = msg.type === 'AUDIO' ? 'fa-microphone' : 'fa-file-alt';
                    fileList.append(`
                        <div class="file-list-item">
                            <i class="fas ${icon} text-primary"></i>
                            <a href="${msg.content}" target="_blank" class="file-list-name text-white">${name}</a>
                        </div>
                    `);
                }
            });
        });
    }

    // --- 10. LIVE SEARCH CONVERSATIONS (Left Sidebar) ---
    window.filterConversations = function() {
        const query = $('#convSearchInput').val().toLowerCase();
        $('.conv-item').each(function() {
            const name = $(this).find('.conv-name').text().toLowerCase();
            if (name.includes(query)) {
                $(this).show();
            } else {
                $(this).hide();
            }
        });
    };
    
    // Cập nhật lại hàm updateInfoSidebar để reset trạng thái khi đổi chat
    const originalSelectConversation = window.selectConversation;
    window.selectConversation = function(id, name, avatar, isFriend, isOnline, lastActive) {
        // Gọi hàm gốc
        originalSelectConversation(id, name, avatar, isFriend, isOnline, lastActive);
        
        // Update Info bên phải
        $('#infoName').text(name);
        $('#infoAvatar').attr('src', avatar);
        
        // Nếu sidebar đang mở thì load lại media
        if (!$('#chatInfoSidebar').hasClass('hidden')) {
            loadSharedMedia();
        }
    };
})();