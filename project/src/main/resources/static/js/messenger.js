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

        // Emoji trigger với animation
        $('#emojiTrigger').off('click').on('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            
            // Animation bounce
            $(this).css({
                transform: 'scale(0.8)',
                transition: 'transform 0.2s'
            });
            
            setTimeout(() => {
                $(this).css('transform', 'scale(1)');
            }, 200);
            
            // CHỈ toggle picker, không init lại
            if (window.emojiPickerState && window.emojiPickerState.isOpen) {
                closeEmojiPicker();
            } else {
                openEmojiPicker();
            }
        });
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

    /**
     * COMPLETE EMOJI DATABASE WITH VIETNAMESE SUPPORT
     * Full emoji list with English and Vietnamese keywords for search
     */

    window.EMOJI_CATEGORIES = [
    { id: 'smileys', name: 'Cảm xúc & Khuôn mặt', icon: '😀' },
    { id: 'people', name: 'Người & Cơ thể', icon: '👋' },
    { id: 'animals', name: 'Động vật & Thiên nhiên', icon: '🐶' },
    { id: 'food', name: 'Đồ ăn & Thức uống', icon: '🍎' },
    { id: 'activities', name: 'Hoạt động', icon: '⚽' },
    { id: 'travel', name: 'Du lịch & Địa điểm', icon: '🚗' },
    { id: 'objects', name: 'Đồ vật', icon: '💡' },
    { id: 'symbols', name: 'Biểu tượng', icon: '❤️' },
    { id: 'flags', name: 'Cờ', icon: '🏁' }
    ];

    window.EMOJI_DATA = [
    // ========== CẢM XÚC & KHUÔN MẶT (150+) ==========
    {"emoji":"😀","name":"grinning face","keywords":"grinning face,khuôn mặt cười lớn,cười,grinning,face,smile","category":"smileys"},
    {"emoji":"😃","name":"grinning face with big eyes","keywords":"grinning face with big eyes,khuôn mặt cười mắt to,cười,grinning,face,big eyes","category":"smileys"},
    {"emoji":"😄","name":"grinning face with smiling eyes","keywords":"grinning face with smiling eyes,khuôn mặt cười mắt cười,cười,grinning,face,smiling eyes","category":"smileys"},
    {"emoji":"😁","name":"beaming face with smiling eyes","keywords":"beaming face with smiling eyes,khuôn mặt rạng rỡ mắt cười,cười,beaming,face,smiling eyes","category":"smileys"},
    {"emoji":"😆","name":"grinning squinting face","keywords":"grinning squinting face,khuôn mặt cười nheo mắt,cười,grinning,squinting,face","category":"smileys"},
    {"emoji":"😅","name":"grinning face with sweat","keywords":"grinning face with sweat,khuôn mặt cười đổ mồ hôi,cười,mồ hôi,grinning,face,sweat","category":"smileys"},
    {"emoji":"🤣","name":"rolling on the floor laughing","keywords":"rolling on the floor laughing,cười lăn lộn,cười lớn,lăn lộn,laughing,floor","category":"smileys"},
    {"emoji":"😂","name":"face with tears of joy","keywords":"face with tears of joy,khuôn mặt nước mắt vui,cười khóc,tears,joy,face","category":"smileys"},
    {"emoji":"🙂","name":"slightly smiling face","keywords":"slightly smiling face,khuôn mặt hơi cười,mỉm cười,smiling,face","category":"smileys"},
    {"emoji":"🙃","name":"upside-down face","keywords":"upside-down face,khuôn mặt lộn ngược,ngược,lộn ngược,face","category":"smileys"},
    {"emoji":"😉","name":"winking face","keywords":"winking face,khuôn mặt nháy mắt,nháy mắt,wink,face","category":"smileys"},
    {"emoji":"😊","name":"smiling face with smiling eyes","keywords":"smiling face with smiling eyes,khuôn mặt cười mắt cười,cười,smiling,face","category":"smileys"},
    {"emoji":"😇","name":"smiling face with halo","keywords":"smiling face with halo,khuôn mặt cười có hào quang,thiên thần,halo,angel,face","category":"smileys"},
    {"emoji":"🥰","name":"smiling face with hearts","keywords":"smiling face with hearts,khuôn mặt cười với trái tim,yêu,thích,tim,hearts,face","category":"smileys"},
    {"emoji":"😍","name":"smiling face with heart-eyes","keywords":"smiling face with heart-eyes,khuôn mặt cười mắt tim,yêu,thích,tim,heart eyes,face","category":"smileys"},
    {"emoji":"🤩","name":"star-struck","keywords":"star-struck,ngôi sao,ấn tượng,star,struck,face","category":"smileys"},
    {"emoji":"😘","name":"face blowing a kiss","keywords":"face blowing a kiss,khuôn mặt thổi kiss,hôn,kiss,blowing,face","category":"smileys"},
    {"emoji":"😗","name":"kissing face","keywords":"kissing face,khuôn mặt hôn,hôn,kissing,face","category":"smileys"},
    {"emoji":"😚","name":"kissing face with closed eyes","keywords":"kissing face with closed eyes,khuôn mặt hôn nhắm mắt,hôn,kissing,closed eyes,face","category":"smileys"},
    {"emoji":"😙","name":"kissing face with smiling eyes","keywords":"kissing face with smiling eyes,khuôn mặt hôn mắt cười,hôn,kissing,smiling eyes,face","category":"smileys"},
    {"emoji":"😋","name":"face savoring food","keywords":"face savoring food,khuôn mặt thưởng thức đồ ăn,ngon,đồ ăn,food,savoring,face","category":"smileys"},
    {"emoji":"😛","name":"face with tongue","keywords":"face with tongue,khuôn mặt lè lưỡi,lè lưỡi,tongue,face","category":"smileys"},
    {"emoji":"😜","name":"winking face with tongue","keywords":"winking face with tongue,khuôn mặt nháy mắt lè lưỡi,nháy mắt,lè lưỡi,wink,tongue,face","category":"smileys"},
    {"emoji":"🤪","name":"zany face","keywords":"zany face,khuôn mặt điên,ngốc,điên,zany,face","category":"smileys"},
    {"emoji":"😝","name":"squinting face with tongue","keywords":"squinting face with tongue,khuôn mặt nheo mắt lè lưỡi,nheo mắt,lè lưỡi,squint,tongue,face","category":"smileys"},
    {"emoji":"🤑","name":"money-mouth face","keywords":"money-mouth face,khuôn mặt tiền,tiền,money,face","category":"smileys"},
    {"emoji":"🤗","name":"hugging face","keywords":"hugging face,khuôn mặt ôm,ôm,hug,face","category":"smileys"},
    {"emoji":"🤭","name":"face with hand over mouth","keywords":"face with hand over mouth,khuôn mặt tay che miệng,ngạc nhiên,hand,mouth,face","category":"smileys"},
    {"emoji":"🤫","name":"shushing face","keywords":"shushing face,khuôn mặt shhh,im lặng,shush,face","category":"smileys"},
    {"emoji":"🤔","name":"thinking face","keywords":"thinking face,khuôn mặt suy nghĩ,suy nghĩ,think,face","category":"smileys"},
    {"emoji":"🤐","name":"zipper-mouth face","keywords":"zipper-mouth face,khuôn mặt khóa kéo miệng,im lặng,zipper,mouth,face","category":"smileys"},
    {"emoji":"🤨","name":"face with raised eyebrow","keywords":"face with raised eyebrow,khuôn mặt nhướng mày,ngờ vực,eyebrow,face","category":"smileys"},
    {"emoji":"😐","name":"neutral face","keywords":"neutral face,khuôn mặt trung lập,trung lập,neutral,face","category":"smileys"},
    {"emoji":"😑","name":"expressionless face","keywords":"expressionless face,khuôn mặt vô cảm,vô cảm,expressionless,face","category":"smileys"},
    {"emoji":"😶","name":"face without mouth","keywords":"face without mouth,khuôn mặt không miệng,không miệng,no mouth,face","category":"smileys"},
    {"emoji":"😏","name":"smirking face","keywords":"smirking face,khuôn mặt cười tự mãn,tự mãn,smirk,face","category":"smileys"},
    {"emoji":"😒","name":"unamused face","keywords":"unamused face,khuôn mặt không vui,không vui,unamused,face","category":"smileys"},
    {"emoji":"🙄","name":"face with rolling eyes","keywords":"face with rolling eyes,khuôn mặt đảo mắt,đảo mắt,rolling eyes,face","category":"smileys"},
    {"emoji":"😬","name":"grimacing face","keywords":"grimacing face,khuôn mặt nhăn nhó,nhăn nhó,grimacing,face","category":"smileys"},
    {"emoji":"🤥","name":"lying face","keywords":"lying face,khuôn mặt nói dối,nói dối,lie,face","category":"smileys"},
    {"emoji":"😌","name":"relieved face","keywords":"relieved face,khuôn mặt nhẹ nhõm,nhẹ nhõm,relieved,face","category":"smileys"},
    {"emoji":"😔","name":"pensive face","keywords":"pensive face,khuôn mặt trầm tư,trầm tư,pensive,face","category":"smileys"},
    {"emoji":"😪","name":"sleepy face","keywords":"sleepy face,khuôn mặt buồn ngủ,buồn ngủ,sleepy,face","category":"smileys"},
    {"emoji":"🤤","name":"drooling face","keywords":"drooling face,khuôn mặt chảy nước miếng,đói,drool,face","category":"smileys"},
    {"emoji":"😴","name":"sleeping face","keywords":"sleeping face,khuôn mặt đang ngủ,ngủ,sleeping,face","category":"smileys"},
    {"emoji":"😷","name":"face with medical mask","keywords":"face with medical mask,khuôn mặt đeo khẩu trang,khẩu trang,mask,medical,face","category":"smileys"},
    {"emoji":"🤒","name":"face with thermometer","keywords":"face with thermometer,khuôn mặt nhiệt kế,ốm,thermometer,face","category":"smileys"},
    {"emoji":"🤕","name":"face with head-bandage","keywords":"face with head-bandage,khuôn mặt băng đầu,thương tích,bandage,face","category":"smileys"},
    {"emoji":"🤢","name":"nauseated face","keywords":"nauseated face,khuôn mặt buồn nôn,buồn nôn,nauseated,face","category":"smileys"},
    {"emoji":"🤮","name":"face vomiting","keywords":"face vomiting,khuôn mặt nôn,nôn,vomit,face","category":"smileys"},
    {"emoji":"🤧","name":"sneezing face","keywords":"sneezing face,khuôn mặt hắt xì,hắt xì,sneeze,face","category":"smileys"},
    {"emoji":"🥵","name":"hot face","keywords":"hot face,khuôn mặt nóng,nóng,hot,face","category":"smileys"},
    {"emoji":"🥶","name":"cold face","keywords":"cold face,khuôn mặt lạnh,lạnh,cold,face","category":"smileys"},
    {"emoji":"🥴","name":"woozy face","keywords":"woozy face,khuôn mặt chóng mặt,chóng mặt,woozy,face","category":"smileys"},
    {"emoji":"😵","name":"dizzy face","keywords":"dizzy face,khuôn mặt choáng,choáng,dizzy,face","category":"smileys"},
    {"emoji":"🤯","name":"exploding head","keywords":"exploding head,đầu nổ,ngạc nhiên,exploding,head","category":"smileys"},
    {"emoji":"🤠","name":"cowboy hat face","keywords":"cowboy hat face,khuôn mặt mũ cao bồi,cowboy,hat,face","category":"smileys"},
    {"emoji":"🥳","name":"partying face","keywords":"partying face,khuôn mặt tiệc tùng,tiệc,party,face","category":"smileys"},
    {"emoji":"😎","name":"smiling face with sunglasses","keywords":"smiling face with sunglasses,khuôn mặt cười đeo kính râm,ngầu,sunglasses,face","category":"smileys"},
    {"emoji":"🤓","name":"nerd face","keywords":"nerd face,khuôn mặt mọt sách,mọt sách,nerd,face","category":"smileys"},
    {"emoji":"🧐","name":"face with monocle","keywords":"face with monocle,khuôn mặt đeo kính một mắt,quan sát,monocle,face","category":"smileys"},
    {"emoji":"😕","name":"confused face","keywords":"confused face,khuôn mặt bối rối,bối rối,confused,face","category":"smileys"},
    {"emoji":"😟","name":"worried face","keywords":"worried face,khuôn mặt lo lắng,lo lắng,worried,face","category":"smileys"},
    {"emoji":"🙁","name":"slightly frowning face","keywords":"slightly frowning face,khuôn mặt hơi cau mày,cau mày,frown,face","category":"smileys"},
    {"emoji":"😮","name":"face with open mouth","keywords":"face with open mouth,khuôn mặt há miệng,ngạc nhiên,open mouth,face","category":"smileys"},
    {"emoji":"😯","name":"hushed face","keywords":"hushed face,khuôn mặt im lặng,im lặng,hushed,face","category":"smileys"},
    {"emoji":"😲","name":"astonished face","keywords":"astonished face,khuôn mặt kinh ngạc,kinh ngạc,astonished,face","category":"smileys"},
    {"emoji":"😳","name":"flushed face","keywords":"flushed face,khuôn mặt đỏ mặt,xấu hổ,flushed,face","category":"smileys"},
    {"emoji":"🥺","name":"pleading face","keywords":"pleading face,khuôn mặt van xin,van xin,pleading,face","category":"smileys"},
    {"emoji":"😦","name":"frowning face with open mouth","keywords":"frowning face with open mouth,khuôn mặt cau mày há miệng,cau mày,frown,open mouth,face","category":"smileys"},
    {"emoji":"😧","name":"anguished face","keywords":"anguished face,khuôn mặt đau khổ,đau khổ,anguished,face","category":"smileys"},
    {"emoji":"😨","name":"fearful face","keywords":"fearful face,khuôn mặt sợ hãi,sợ hãi,fearful,face","category":"smileys"},
    {"emoji":"😰","name":"anxious face with sweat","keywords":"anxious face with sweat,khuôn mặt lo âu đổ mồ hôi,lo âu,anxious,sweat,face","category":"smileys"},
    {"emoji":"😥","name":"sad but relieved face","keywords":"sad but relieved face,khuôn mặt buồn nhưng nhẹ nhõm,buồn,relieved,face","category":"smileys"},
    {"emoji":"😢","name":"crying face","keywords":"crying face,khuôn mặt khóc,khóc,crying,face","category":"smileys"},
    {"emoji":"😭","name":"loudly crying face","keywords":"loudly crying face,khuôn mặt khóc to,khóc to,crying,loud,face","category":"smileys"},
    {"emoji":"😱","name":"face screaming in fear","keywords":"face screaming in fear,khuôn mặt hét trong sợ hãi,hét,scream,fear,face","category":"smileys"},
    {"emoji":"😖","name":"confounded face","keywords":"confounded face,khuôn mặt bối rối,bối rối,confounded,face","category":"smileys"},
    {"emoji":"😣","name":"persevering face","keywords":"persevering face,khuôn mặt kiên trì,kiên trì,persevering,face","category":"smileys"},
    {"emoji":"😞","name":"disappointed face","keywords":"disappointed face,khuôn mặt thất vọng,thất vọng,disappointed,face","category":"smileys"},
    {"emoji":"😓","name":"downcast face with sweat","keywords":"downcast face with sweat,khuôn mặt buồn đổ mồ hôi,buồn,sweat,face","category":"smileys"},
    {"emoji":"😩","name":"weary face","keywords":"weary face,khuôn mặt mệt mỏi,mệt mỏi,weary,face","category":"smileys"},
    {"emoji":"😫","name":"tired face","keywords":"tired face,khuôn mặt mệt,mệt,tired,face","category":"smileys"},
    {"emoji":"🥱","name":"yawning face","keywords":"yawning face,khuôn mặt ngáp,ngáp,yawn,face","category":"smileys"},
    {"emoji":"😤","name":"face with steam from nose","keywords":"face with steam from nose,khuôn mặt bốc khói mũi,tức giận,steam,nose,face","category":"smileys"},
    {"emoji":"😡","name":"pouting face","keywords":"pouting face,khuôn mặt phụng phịu,phụng phịu,pout,face","category":"smileys"},
    {"emoji":"😠","name":"angry face","keywords":"angry face,khuôn mặt tức giận,tức giận,angry,face","category":"smileys"},
    {"emoji":"🤬","name":"face with symbols on mouth","keywords":"face with symbols on mouth,khuôn mặt biểu tượng trên miệng,chửi thề,symbols,mouth,face","category":"smileys"},
    {"emoji":"😈","name":"smiling face with horns","keywords":"smiling face with horns,khuôn mặt cười có sừng,quỷ,devil,horns,face","category":"smileys"},
    {"emoji":"👿","name":"angry face with horns","keywords":"angry face with horns,khuôn mặt giận có sừng,quỷ giận,devil,angry,horns,face","category":"smileys"},
    {"emoji":"💀","name":"skull","keywords":"skull,đầu lâu,chết,skull,bone","category":"smileys"},
    {"emoji":"☠️","name":"skull and crossbones","keywords":"skull and crossbones,đầu lâu xương chéo,nguy hiểm,skull,crossbones","category":"smileys"},
    {"emoji":"💩","name":"pile of poo","keywords":"pile of poo,cục phân,phân,poo,shit","category":"smileys"},
    {"emoji":"🤡","name":"clown face","keywords":"clown face,khuôn mặt chú hề,chú hề,clown,face","category":"smileys"},
    {"emoji":"👹","name":"ogre","keywords":"ogre,yêu tinh,ogre,monster","category":"smileys"},
    {"emoji":"👺","name":"goblin","keywords":"goblin,ma quỷ,goblin,monster","category":"smileys"},
    {"emoji":"👻","name":"ghost","keywords":"ghost,ma,ghost,spirit","category":"smileys"},
    {"emoji":"👽","name":"alien","keywords":"alien,người ngoài hành tinh,alien,space","category":"smileys"},
    {"emoji":"👾","name":"alien monster","keywords":"alien monster,quái vật ngoài hành tinh,alien,monster","category":"smileys"},
    {"emoji":"🤖","name":"robot","keywords":"robot,người máy,robot,android","category":"smileys"},
    {"emoji":"😺","name":"grinning cat","keywords":"grinning cat,mèo cười,cat,grinning","category":"smileys"},
    {"emoji":"😸","name":"grinning cat with smiling eyes","keywords":"grinning cat with smiling eyes,mèo cười mắt cười,cat,grinning,smiling eyes","category":"smileys"},
    {"emoji":"😹","name":"cat with tears of joy","keywords":"cat with tears of joy,mèo khóc vui,cat,tears,joy","category":"smileys"},
    {"emoji":"😻","name":"smiling cat with heart-eyes","keywords":"smiling cat with heart-eyes,mèo cười mắt tim,cat,smiling,heart eyes","category":"smileys"},
    {"emoji":"😼","name":"cat with wry smile","keywords":"cat with wry smile,mèo cười méo,cat,wry smile","category":"smileys"},
    {"emoji":"😽","name":"kissing cat","keywords":"kissing cat,mèo hôn,cat,kissing","category":"smileys"},
    {"emoji":"🙀","name":"weary cat","keywords":"weary cat,mèo mệt mỏi,cat,weary","category":"smileys"},
    {"emoji":"😿","name":"crying cat","keywords":"crying cat,mèo khóc,cat,crying","category":"smileys"},
    {"emoji":"😾","name":"pouting cat","keywords":"pouting cat,mèo phụng phịu,cat,pouting","category":"smileys"},
    {"emoji":"🙈","name":"see-no-evil monkey","keywords":"see-no-evil monkey,khỉ không thấy điều xấu,monkey,see no evil","category":"smileys"},
    {"emoji":"🙉","name":"hear-no-evil monkey","keywords":"hear-no-evil monkey,khỉ không nghe điều xấu,monkey,hear no evil","category":"smileys"},
    {"emoji":"🙊","name":"speak-no-evil monkey","keywords":"speak-no-evil monkey,khỉ không nói điều xấu,monkey,speak no evil","category":"smileys"},
    
    // ========== NGƯỜI & CƠ THỂ (150+) ==========
    {"emoji":"👋","name":"waving hand","keywords":"waving hand,vẫy tay,chào,waving,hand","category":"people"},
    {"emoji":"🤚","name":"raised back of hand","keywords":"raised back of hand,mặt sau bàn tay giơ lên,hand,back,raised","category":"people"},
    {"emoji":"🖐️","name":"hand with fingers splayed","keywords":"hand with fingers splayed,bàn tay xòe ngón,fingers,splayed,hand","category":"people"},
    {"emoji":"✋","name":"raised hand","keywords":"raised hand,tay giơ lên,stop,hand,raised","category":"people"},
    {"emoji":"🖖","name":"vulcan salute","keywords":"vulcan salute,kiểu chào Vulcan,star trek,vulcan,salute","category":"people"},
    {"emoji":"👌","name":"OK hand","keywords":"OK hand,tay OK,được,ok,hand","category":"people"},
    {"emoji":"🤌","name":"pinched fingers","keywords":"pinched fingers,ngón tay bóp,fingers,pinch,hand","category":"people"},
    {"emoji":"🤏","name":"pinching hand","keywords":"pinching hand,tay véo,nhỏ,pinch,hand","category":"people"},
    {"emoji":"✌️","name":"victory hand","keywords":"victory hand,tay chiến thắng,peace,victory,hand","category":"people"},
    {"emoji":"🤞","name":"crossed fingers","keywords":"crossed fingers,ngón tay bắt chéo,chúc may mắn,fingers,crossed","category":"people"},
    {"emoji":"🤟","name":"love-you gesture","keywords":"love-you gesture,cử chỉ yêu bạn,I love you,love,hand","category":"people"},
    {"emoji":"🤘","name":"sign of the horns","keywords":"sign of the horns,dấu hiệu sừng,rock,horns,hand","category":"people"},
    {"emoji":"🤙","name":"call me hand","keywords":"call me hand,tay gọi điện,call me,phone,hand","category":"people"},
    {"emoji":"👈","name":"backhand index pointing left","keywords":"backhand index pointing left,ngón trỏ trái,trái,left,point","category":"people"},
    {"emoji":"👉","name":"backhand index pointing right","keywords":"backhand index pointing right,ngón trỏ phải,phải,right,point","category":"people"},
    {"emoji":"👆","name":"backhand index pointing up","keywords":"backhand index pointing up,ngón trỏ lên,up,point","category":"people"},
    {"emoji":"🖕","name":"middle finger","keywords":"middle finger,ngón giữa,fuck,finger,middle","category":"people"},
    {"emoji":"👇","name":"backhand index pointing down","keywords":"backhand index pointing down,ngón trỏ xuống,down,point","category":"people"},
    {"emoji":"☝️","name":"index pointing up","keywords":"index pointing up,ngón trỏ chỉ lên,up,point,finger","category":"people"},
    {"emoji":"👍","name":"thumbs up","keywords":"thumbs up,giơ ngón cái,tốt,good,thumb","category":"people"},
    {"emoji":"👎","name":"thumbs down","keywords":"thumbs down,ngón cái xuống,xấu,bad,thumb","category":"people"},
    {"emoji":"✊","name":"raised fist","keywords":"raised fist,nắm đấm giơ lên,power,fist,raised","category":"people"},
    {"emoji":"👊","name":"oncoming fist","keywords":"oncoming fist,nắm đấm tới,punch,fist,oncoming","category":"people"},
    {"emoji":"🤛","name":"left-facing fist","keywords":"left-facing fist,nắm đấm trái,trái,left,fist","category":"people"},
    {"emoji":"🤜","name":"right-facing fist","keywords":"right-facing fist,nắm đấm phải,phải,right,fist","category":"people"},
    {"emoji":"👏","name":"clapping hands","keywords":"clapping hands,vỗ tay,hoan hô,clap,hands","category":"people"},
    {"emoji":"🙌","name":"raising hands","keywords":"raising hands,giơ hai tay,hooray,hands,raise","category":"people"},
    {"emoji":"👐","name":"open hands","keywords":"open hands,hái tay mở,open,hands","category":"people"},
    {"emoji":"🤲","name":"palms up together","keywords":"palms up together,lòng bàn tay hướng lên,pray,hands,palms","category":"people"},
    {"emoji":"🤝","name":"handshake","keywords":"handshake,bắt tay,deal,handshake,agreement","category":"people"},
    {"emoji":"🙏","name":"folded hands","keywords":"folded hands,chắp tay,cầu nguyện,pray,hands","category":"people"},
    {"emoji":"✍️","name":"writing hand","keywords":"writing hand,tay viết,write,hand","category":"people"},
    {"emoji":"💅","name":"nail polish","keywords":"nail polish,sơn móng tay,beauty,nail,polish","category":"people"},
    {"emoji":"🤳","name":"selfie","keywords":"selfie,tự sướng,selfie,camera","category":"people"},
    {"emoji":"💪","name":"flexed biceps","keywords":"flexed biceps,cơ bắp cuồn cuộn,strong,biceps,flex","category":"people"},
    {"emoji":"🦾","name":"mechanical arm","keywords":"mechanical arm,cánh tay cơ khí,robot,arm,mechanical","category":"people"},
    {"emoji":"🦿","name":"mechanical leg","keywords":"mechanical leg,chân cơ khí,robot,leg,mechanical","category":"people"},
    {"emoji":"🦵","name":"leg","keywords":"leg,chân,leg,limb","category":"people"},
    {"emoji":"🦶","name":"foot","keywords":"foot,bàn chân,foot","category":"people"},
    {"emoji":"👂","name":"ear","keywords":"ear,tai,hear,ear","category":"people"},
    {"emoji":"🦻","name":"ear with hearing aid","keywords":"ear with hearing aid,tai máy trợ thính,hearing aid,ear","category":"people"},
    {"emoji":"👃","name":"nose","keywords":"nose,mũi,smell,nose","category":"people"},
    {"emoji":"🧠","name":"brain","keywords":"brain,não,brain,intelligent","category":"people"},
    {"emoji":"🫀","name":"heart","keywords":"heart,trái tim,heart,love","category":"people"},
    {"emoji":"🫁","name":"lungs","keywords":"phổi,lungs,breathe","category":"people"},
    {"emoji":"🦷","name":"tooth","keywords":"tooth,răng,tooth,dental","category":"people"},
    {"emoji":"🦴","name":"bone","keywords":"bone,xương,bone,skeleton","category":"people"},
    {"emoji":"👀","name":"eyes","keywords":"eyes,đôi mắt,see,eyes","category":"people"},
    {"emoji":"👁️","name":"eye","keywords":"eye,mắt,see,eye","category":"people"},
    {"emoji":"👅","name":"tongue","keywords":"lưỡi,tongue,taste","category":"people"},
    {"emoji":"👄","name":"mouth","keywords":"miệng,mouth,kiss","category":"people"},
    {"emoji":"👶","name":"baby","keywords":"baby,em bé,baby,child","category":"people"},
    {"emoji":"🧒","name":"child","keywords":"child,trẻ em,child,kid","category":"people"},
    {"emoji":"👦","name":"boy","keywords":"boy,con trai,boy,child","category":"people"},
    {"emoji":"👧","name":"girl","keywords":"girl,con gái,girl,child","category":"people"},
    {"emoji":"🧑","name":"person","keywords":"person,người,person,adult","category":"people"},
    {"emoji":"👱","name":"person: blond hair","keywords":"person: blond hair,người tóc vàng,blond,hair","category":"people"},
    {"emoji":"👨","name":"man","keywords":"man,đàn ông,man,adult","category":"people"},
    {"emoji":"👩","name":"woman","keywords":"woman,phụ nữ,woman,adult","category":"people"},
    {"emoji":"🧓","name":"older person","keywords":"older person,người lớn tuổi,old,person","category":"people"},
    {"emoji":"👴","name":"old man","keywords":"old man,ông già,old,man","category":"people"},
    {"emoji":"👵","name":"old woman","keywords":"old woman,bà già,old,woman","category":"people"},
    {"emoji":"🙍","name":"person frowning","keywords":"person frowning,người cau mày,frown,person","category":"people"},
    {"emoji":"🙎","name":"person pouting","keywords":"person pouting,người phụng phịu,pout,person","category":"people"},
    {"emoji":"🙅","name":"person gesturing NO","keywords":"person gesturing NO,người ra hiệu KHÔNG,no,gesture","category":"people"},
    {"emoji":"🙆","name":"person gesturing OK","keywords":"person gesturing OK,người ra hiệu OK,ok,gesture","category":"people"},
    {"emoji":"💁","name":"person tipping hand","keywords":"person tipping hand,người nghiêng tay,information,hand","category":"people"},
    {"emoji":"🙋","name":"person raising hand","keywords":"person raising hand,người giơ tay,question,hand","category":"people"},
    {"emoji":"🧏","name":"deaf person","keywords":"deaf person,người điếc,deaf,person","category":"people"},
    {"emoji":"🙇","name":"person bowing","keywords":"person bowing,người cúi chào,bow,apology","category":"people"},
    {"emoji":"🤦","name":"person facepalming","keywords":"person facepalming,người đập tay lên mặt,facepalm,disbelief","category":"people"},
    {"emoji":"🤷","name":"person shrugging","keywords":"person shrugging,người nhún vai,shrug,indifferent","category":"people"},
    {"emoji":"👮","name":"police officer","keywords":"police officer,cảnh sát,police,cop","category":"people"},
    {"emoji":"🕵️","name":"detective","keywords":"detective,thám tử,detective,spy","category":"people"},
    {"emoji":"💂","name":"guard","keywords":"guard,lính gác,guard,security","category":"people"},
    {"emoji":"👷","name":"construction worker","keywords":"construction worker,công nhân xây dựng,construction,worker","category":"people"},
    {"emoji":"🤴","name":"prince","keywords":"prince,hoàng tử,prince,royal","category":"people"},
    {"emoji":"👸","name":"princess","keywords":"princess,công chúa,princess,royal","category":"people"},
    {"emoji":"👳","name":"person wearing turban","keywords":"person wearing turban,người đội khăn xếp,turban,person","category":"people"},
    {"emoji":"👲","name":"person with skullcap","keywords":"person with skullcap,người đội mũ tròn,skullcap,person","category":"people"},
    {"emoji":"🧕","name":"woman with headscarf","keywords":"woman with headscarf,phụ nữ đội khăn,headscarf,woman","category":"people"},
    {"emoji":"🤵","name":"person in tuxedo","keywords":"person in tuxedo,người mặc veston,tuxedo,formal","category":"people"},
    {"emoji":"👰","name":"person with veil","keywords":"person with veil,người che mạng cưới,veil,bride","category":"people"},
    {"emoji":"🤰","name":"pregnant woman","keywords":"pregnant woman,phụ nữ mang thai,pregnant,woman","category":"people"},
    {"emoji":"🤱","name":"breast-feeding","keywords":"breast-feeding,cho con bú,breastfeeding,nursing","category":"people"},
    {"emoji":"👼","name":"baby angel","keywords":"baby angel,thiên thần bé,angel,baby","category":"people"},
    {"emoji":"🎅","name":"Santa Claus","keywords":"Santa Claus,ông già Noel,santa,christmas","category":"people"},
    {"emoji":"🤶","name":"Mrs. Claus","keywords":"Mrs. Claus,bà già Noel,mrs claus,christmas","category":"people"},
    {"emoji":"🦸","name":"superhero","keywords":"superhero,siêu anh hùng,superhero,hero","category":"people"},
    {"emoji":"🦹","name":"supervillain","keywords":"supervillain,siêu phản diện,supervillain,villain","category":"people"},
    {"emoji":"🧙","name":"mage","keywords":"mage,phù thủy,mage,wizard","category":"people"},
    {"emoji":"🧚","name":"fairy","keywords":"fairy,tiên,fairy,mythical","category":"people"},
    {"emoji":"🧛","name":"vampire","keywords":"vampire,ma cà rồng,vampire,undead","category":"people"},
    {"emoji":"🧜","name":"merperson","keywords":"merperson,người cá,mermaid,merman","category":"people"},
    {"emoji":"🧝","name":"elf","keywords":"elf,yêu tinh,elf,fantasy","category":"people"},
    {"emoji":"🧞","name":"genie","keywords":"genie,thần đèn,genie,magic","category":"people"},
    {"emoji":"🧟","name":"zombie","keywords":"zombie,thây ma,zombie,undead","category":"people"},
    {"emoji":"💆","name":"person getting massage","keywords":"person getting massage,người được massage,massage,spa","category":"people"},
    {"emoji":"💇","name":"person getting haircut","keywords":"person getting haircut,người cắt tóc,haircut,beauty","category":"people"},
    {"emoji":"🚶","name":"person walking","keywords":"person walking,người đi bộ,walk,person","category":"people"},
    {"emoji":"🏃","name":"person running","keywords":"person running,người chạy,run,person","category":"people"},
    {"emoji":"💃","name":"woman dancing","keywords":"woman dancing,phụ nữ nhảy,dance,woman","category":"people"},
    {"emoji":"🕺","name":"man dancing","keywords":"man dancing,đàn ông nhảy,dance,man","category":"people"},
    {"emoji":"🕴️","name":"person in suit levitating","keywords":"person in suit levitating,người lơ lửng trong vest,levitate,business","category":"people"},
    {"emoji":"👯","name":"people with bunny ears","keywords":"people with bunny ears,người với tai thỏ,bunny,party","category":"people"},
    {"emoji":"🧖","name":"person in steamy room","keywords":"person in steamy room,người trong phòng xông hơi,sauna,steam","category":"people"},
    {"emoji":"🧗","name":"person climbing","keywords":"person climbing,người leo núi,climb,person","category":"people"},
    {"emoji":"🤺","name":"person fencing","keywords":"person fencing,người đấu kiếm,fencing,sword","category":"people"},
    {"emoji":"🏇","name":"horse racing","keywords":"horse racing,đua ngựa,horse,race","category":"people"},
    {"emoji":"⛷️","name":"skier","keywords":"skier,trượt tuyết,ski,winter","category":"people"},
    {"emoji":"🏂","name":"snowboarder","keywords":"snowboarder,trượt ván tuyết,snowboard,winter","category":"people"},
    {"emoji":"🏌️","name":"person golfing","keywords":"person golfing,người chơi golf,golf,sport","category":"people"},
    {"emoji":"🏄","name":"person surfing","keywords":"person surfing,người lướt sóng,surf,sea","category":"people"},
    {"emoji":"🚣","name":"person rowing boat","keywords":"person rowing boat,người chèo thuyền,row,boat","category":"people"},
    {"emoji":"🏊","name":"person swimming","keywords":"person swimming,người bơi,swim,water","category":"people"},
    {"emoji":"⛹️","name":"person bouncing ball","keywords":"person bouncing ball,người ném bóng,basketball,sport","category":"people"},
    {"emoji":"🏋️","name":"person lifting weights","keywords":"person lifting weights,người nâng tạ,weightlift,gym","category":"people"},
    {"emoji":"🚴","name":"person biking","keywords":"person biking,người đạp xe,bike,cycle","category":"people"},
    {"emoji":"🚵","name":"person mountain biking","keywords":"person mountain biking,người đạp xe leo núi,mountain bike","category":"people"},
    {"emoji":"🤸","name":"person cartwheeling","keywords":"person cartwheeling,người xoay người,cartwheel,gymnastics","category":"people"},
    {"emoji":"🤼","name":"people wrestling","keywords":"people wrestling,người vật,wrestle,sport","category":"people"},
    {"emoji":"🤽","name":"person playing water polo","keywords":"person playing water polo,người chơi bóng nước,water polo,sport","category":"people"},
    {"emoji":"🤾","name":"person playing handball","keywords":"person playing handball,người chơi bóng ném,handball,sport","category":"people"},
    {"emoji":"🤹","name":"person juggling","keywords":"person juggling,người tung hứng,juggle,skill","category":"people"},
    {"emoji":"🧘","name":"person in lotus position","keywords":"person in lotus position,người ngồi thiền,yoga,meditate","category":"people"},
    
    // ========== ĐỘNG VẬT & THIÊN NHIÊN (150+) ==========
    {"emoji":"🐶","name":"dog face","keywords":"dog face,mặt chó,dog,pet,animal","category":"animals"},
    {"emoji":"🐕","name":"dog","keywords":"dog,chó,dog,animal","category":"animals"},
    {"emoji":"🦮","name":"guide dog","keywords":"guide dog,chó dẫn đường,guide dog,blind","category":"animals"},
    {"emoji":"🐕‍🦺","name":"service dog","keywords":"service dog,chó dịch vụ,service dog,assistance","category":"animals"},
    {"emoji":"🐩","name":"poodle","keywords":"poodle,chó poodle,poodle,dog","category":"animals"},
    {"emoji":"🐺","name":"wolf","keywords":"wolf,sói,wolf,animal","category":"animals"},
    {"emoji":"🦊","name":"fox","keywords":"fox,cáo,fox,animal","category":"animals"},
    {"emoji":"🦝","name":"raccoon","keywords":"raccoon,gấu mèo,raccoon,animal","category":"animals"},
    {"emoji":"🐱","name":"cat face","keywords":"cat face,mặt mèo,cat,pet,animal","category":"animals"},
    {"emoji":"🐈","name":"cat","keywords":"cat,mèo,cat,animal","category":"animals"},
    {"emoji":"🦁","name":"lion","keywords":"lion,sư tử,lion,animal","category":"animals"},
    {"emoji":"🐯","name":"tiger face","keywords":"tiger face,mặt hổ,tiger,animal","category":"animals"},
    {"emoji":"🐅","name":"tiger","keywords":"tiger,hổ,tiger,animal","category":"animals"},
    {"emoji":"🐆","name":"leopard","keywords":"leopard,báo,leopard,animal","category":"animals"},
    {"emoji":"🐴","name":"horse face","keywords":"horse face,mặt ngựa,horse,animal","category":"animals"},
    {"emoji":"🐎","name":"horse","keywords":"horse,ngựa,horse,animal","category":"animals"},
    {"emoji":"🦄","name":"unicorn","keywords":"unicorn,ngựa một sừng,unicorn,fantasy","category":"animals"},
    {"emoji":"🦓","name":"zebra","keywords":"zebra,ngựa vằn,zebra,animal","category":"animals"},
    {"emoji":"🦌","name":"deer","keywords":"deer,hươu,deer,animal","category":"animals"},
    {"emoji":"🐮","name":"cow face","keywords":"cow face,mặt bò,cow,animal","category":"animals"},
    {"emoji":"🐂","name":"ox","keywords":"ox,bò đực,ox,animal","category":"animals"},
    {"emoji":"🐃","name":"water buffalo","keywords":"water buffalo,trâu nước,buffalo,animal","category":"animals"},
    {"emoji":"🐄","name":"cow","keywords":"cow,bò,cow,animal","category":"animals"},
    {"emoji":"🐷","name":"pig face","keywords":"pig face,mặt heo,pig,animal","category":"animals"},
    {"emoji":"🐖","name":"pig","keywords":"pig,heo,pig,animal","category":"animals"},
    {"emoji":"🐗","name":"boar","keywords":"boar,heo rừng,boar,animal","category":"animals"},
    {"emoji":"🐽","name":"pig nose","keywords":"pig nose,mũi heo,pig,nose","category":"animals"},
    {"emoji":"🐏","name":"ram","keywords":"ram,cừu đực,ram,animal","category":"animals"},
    {"emoji":"🐑","name":"ewe","keywords":"ewe,cừu cái,ewe,animal","category":"animals"},
    {"emoji":"🐐","name":"goat","keywords":"goat,dê,goat,animal","category":"animals"},
    {"emoji":"🐪","name":"camel","keywords":"camel,lạc đà,camel,animal","category":"animals"},
    {"emoji":"🐫","name":"two-hump camel","keywords":"two-hump camel,lạc đà hai bướu,camel,desert","category":"animals"},
    {"emoji":"🦙","name":"llama","keywords":"llama,lạc đà không bướu,llama,animal","category":"animals"},
    {"emoji":"🦒","name":"giraffe","keywords":"giraffe,hươu cao cổ,giraffe,animal","category":"animals"},
    {"emoji":"🐘","name":"elephant","keywords":"elephant,voi,elephant,animal","category":"animals"},
    {"emoji":"🦏","name":"rhinoceros","keywords":"rhinoceros,tê giác,rhino,animal","category":"animals"},
    {"emoji":"🦛","name":"hippopotamus","keywords":"hippopotamus,hà mã,hippo,animal","category":"animals"},
    {"emoji":"🐭","name":"mouse face","keywords":"mouse face,mặt chuột,mouse,animal","category":"animals"},
    {"emoji":"🐁","name":"mouse","keywords":"mouse,chuột,mouse,animal","category":"animals"},
    {"emoji":"🐀","name":"rat","keywords":"rat,chuột cống,rat,animal","category":"animals"},
    {"emoji":"🐹","name":"hamster","keywords":"hamster,chuột hamster,hamster,pet","category":"animals"},
    {"emoji":"🐰","name":"rabbit face","keywords":"rabbit face,mặt thỏ,rabbit,animal","category":"animals"},
    {"emoji":"🐇","name":"rabbit","keywords":"rabbit,thỏ,rabbit,animal","category":"animals"},
    {"emoji":"🐿️","name":"chipmunk","keywords":"chipmunk,sóc,chipmunk,animal","category":"animals"},
    {"emoji":"🦔","name":"hedgehog","keywords":"hedgehog,nhím,hedgehog,animal","category":"animals"},
    {"emoji":"🦇","name":"bat","keywords":"bat,dơi,bat,animal","category":"animals"},
    {"emoji":"🐻","name":"bear","keywords":"bear,gấu,bear,animal","category":"animals"},
    {"emoji":"🐨","name":"koala","keywords":"koala,gấu túi,koala,animal","category":"animals"},
    {"emoji":"🐼","name":"panda","keywords":"panda,gấu trúc,panda,animal","category":"animals"},
    {"emoji":"🦥","name":"sloth","keywords":"sloth,lười,sloth,animal","category":"animals"},
    {"emoji":"🦦","name":"otter","keywords":"otter,rái cá,otter,animal","category":"animals"},
    {"emoji":"🦨","name":"skunk","keywords":"skunk,chồn hôi,skunk,animal","category":"animals"},
    {"emoji":"🦘","name":"kangaroo","keywords":"kangaroo,chuột túi,kangaroo,animal","category":"animals"},
    {"emoji":"🦡","name":"badger","keywords":"badger,lửng,badger,animal","category":"animals"},
    {"emoji":"🐾","name":"paw prints","keywords":"paw prints,dấu chân,pet,paw,prints","category":"animals"},
    {"emoji":"🦃","name":"turkey","keywords":"turkey,gà tây,turkey,bird","category":"animals"},
    {"emoji":"🐔","name":"chicken","keywords":"chicken,gà,chicken,bird","category":"animals"},
    {"emoji":"🐓","name":"rooster","keywords":"rooster,gà trống,rooster,bird","category":"animals"},
    {"emoji":"🐣","name":"hatching chick","keywords":"hatching chick,gà con nở,chick,hatch","category":"animals"},
    {"emoji":"🐤","name":"baby chick","keywords":"baby chick,gà con,chick,baby","category":"animals"},
    {"emoji":"🐥","name":"front-facing baby chick","keywords":"front-facing baby chick,gà con nhìn thẳng,chick,baby","category":"animals"},
    {"emoji":"🐦","name":"bird","keywords":"bird,chim,bird,animal","category":"animals"},
    {"emoji":"🐧","name":"penguin","keywords":"penguin,chim cánh cụt,penguin,bird","category":"animals"},
    {"emoji":"🕊️","name":"dove","keywords":"dove,bồ câu,dove,peace","category":"animals"},
    {"emoji":"🦅","name":"eagle","keywords":"eagle,đại bàng,eagle,bird","category":"animals"},
    {"emoji":"🦆","name":"duck","keywords":"duck,vịt,duck,bird","category":"animals"},
    {"emoji":"🦢","name":"swan","keywords":"swan,thiên nga,swan,bird","category":"animals"},
    {"emoji":"🦉","name":"owl","keywords":"owl,cú mèo,owl,bird","category":"animals"},
    {"emoji":"🦤","name":"dodo","name":"dodo,dodo,bird,extinct","category":"animals"},
    {"emoji":"🦩","name":"flamingo","keywords":"flamingo,chim hồng hạc,flamingo,bird","category":"animals"},
    {"emoji":"🦚","name":"peacock","keywords":"peacock,công,peacock,bird","category":"animals"},
    {"emoji":"🦜","name":"parrot","keywords":"parrot,vẹt,parrot,bird","category":"animals"},
    {"emoji":"🐸","name":"frog","keywords":"frog,ếch,frog,animal","category":"animals"},
    {"emoji":"🐊","name":"crocodile","keywords":"crocodile,cá sấu,crocodile,animal","category":"animals"},
    {"emoji":"🐢","name":"turtle","keywords":"turtle,rùa,turtle,animal","category":"animals"},
    {"emoji":"🦎","name":"lizard","keywords":"lizard,thằn lằn,lizard,animal","category":"animals"},
    {"emoji":"🐍","name":"snake","keywords":"snake,rắn,snake,animal","category":"animals"},
    {"emoji":"🐲","name":"dragon face","keywords":"dragon face,mặt rồng,dragon,fantasy","category":"animals"},
    {"emoji":"🐉","name":"dragon","keywords":"dragon,rồng,dragon,fantasy","category":"animals"},
    {"emoji":"🦕","name":"sauropod","keywords":"sauropod,khủng long cổ dài,dinosaur,animal","category":"animals"},
    {"emoji":"🦖","name":"T-Rex","keywords":"T-Rex,khủng long bạo chúa,trex,dinosaur","category":"animals"},
    {"emoji":"🐳","name":"spouting whale","keywords":"spouting whale,cá voi phun nước,whale,sea","category":"animals"},
    {"emoji":"🐋","name":"whale","keywords":"whale,cá voi,whale,sea","category":"animals"},
    {"emoji":"🐬","name":"dolphin","keywords":"dolphin,cá heo,dolphin,sea","category":"animals"},
    {"emoji":"🦭","name":"seal","keywords":"seal,hải cẩu,seal,animal","category":"animals"},
    {"emoji":"🐟","name":"fish","keywords":"fish,cá,fish,sea","category":"animals"},
    {"emoji":"🐠","name":"tropical fish","keywords":"tropical fish,cá nhiệt đới,fish,sea","category":"animals"},
    {"emoji":"🐡","name":"blowfish","keywords":"blowfish,cá nóc,fish,sea","category":"animals"},
    {"emoji":"🦈","name":"shark","keywords":"shark,cá mập,shark,sea","category":"animals"},
    {"emoji":"🐙","name":"octopus","keywords":"octopus,bạch tuộc,octopus,sea","category":"animals"},
    {"emoji":"🐚","name":"spiral shell","keywords":"spiral shell,vỏ ốc,shell,sea","category":"animals"},
    {"emoji":"🪸","name":"coral","keywords":"coral,san hô,coral,sea","category":"animals"},
    {"emoji":"🪼","name":"jellyfish","keywords":"jellyfish,sứa,jellyfish,sea","category":"animals"},
    {"emoji":"🐌","name":"snail","keywords":"snail,ốc sên,snail,slow","category":"animals"},
    {"emoji":"🦋","name":"butterfly","keywords":"butterfly,bướm,butterfly,insect","category":"animals"},
    {"emoji":"🐛","name":"bug","keywords":"bug,bọ,bug,insect","category":"animals"},
    {"emoji":"🐜","name":"ant","keywords":"ant,kiến,ant,insect","category":"animals"},
    {"emoji":"🐝","name":"honeybee","keywords":"honeybee,ong mật,bee,insect","category":"animals"},
    {"emoji":"🪲","name":"beetle","keywords":"beetle,bọ cánh cứng,beetle,insect","category":"animals"},
    {"emoji":"🐞","name":"lady beetle","keywords":"lady beetle,bọ rùa,ladybug,insect","category":"animals"},
    {"emoji":"🦗","name":"cricket","keywords":"cricket,dế,cricket,insect","category":"animals"},
    {"emoji":"🪳","name":"cockroach","keywords":"cockroach,gián,cockroach,insect","category":"animals"},
    {"emoji":"🕷️","name":"spider","keywords":"spider,nhện,spider,insect","category":"animals"},
    {"emoji":"🕸️","name":"spider web","keywords":"spider web,mạng nhện,spider web,halloween","category":"animals"},
    {"emoji":"🦂","name":"scorpion","keywords":"scorpion,bọ cạp,scorpion,animal","category":"animals"},
    {"emoji":"🦟","name":"mosquito","keywords":"mosquito,muỗi,mosquito,insect","category":"animals"},
    {"emoji":"🦠","name":"microbe","keywords":"microbe,vi khuẩn,microbe,germ","category":"animals"},
    {"emoji":"💐","name":"bouquet","keywords":"bouquet,bó hoa,flowers,bouquet","category":"animals"},
    {"emoji":"🌸","name":"cherry blossom","keywords":"cherry blossom,hoa anh đào,flower,spring","category":"animals"},
    {"emoji":"💮","name":"white flower","keywords":"white flower,hoa trắng,flower,white","category":"animals"},
    {"emoji":"🏵️","name":"rosette","keywords":"rosette,hoa hồng cách điệu,flower,rosette","category":"animals"},
    {"emoji":"🌹","name":"rose","keywords":"rose,hoa hồng,rose,flower","category":"animals"},
    {"emoji":"🥀","name":"wilted flower","keywords":"wilted flower,hoa héo,flower,wilted","category":"animals"},
    {"emoji":"🌺","name":"hibiscus","keywords":"hibiscus,hoa dâm bụt,flower,hibiscus","category":"animals"},
    {"emoji":"🌻","name":"sunflower","keywords":"sunflower,hoa hướng dương,sunflower,flower","category":"animals"},
    {"emoji":"🌼","name":"blossom","keywords":"blossom,hoa nở,flower,blossom","category":"animals"},
    {"emoji":"🌷","name":"tulip","keywords":"tulip,hoa tulip,tulip,flower","category":"animals"},
    {"emoji":"🌱","name":"seedling","keywords":"seedling,cây non,plant,seedling","category":"animals"},
    {"emoji":"🪴","name":"potted plant","keywords":"potted plant,cây trong chậu,plant,potted","category":"animals"},
    {"emoji":"🌲","name":"evergreen tree","keywords":"evergreen tree,cây thường xanh,tree,evergreen","category":"animals"},
    {"emoji":"🌳","name":"deciduous tree","keywords":"deciduous tree,cây rụng lá,tree,deciduous","category":"animals"},
    {"emoji":"🌴","name":"palm tree","keywords":"palm tree,cây cọ,palm tree,beach","category":"animals"},
    {"emoji":"🌵","name":"cactus","keywords":"cactus,xương rồng,cactus,plant","category":"animals"},
    {"emoji":"🌾","name":"sheaf of rice","keywords":"sheaf of rice,bó lúa,rice,plant","category":"animals"},
    {"emoji":"🌿","name":"herb","keywords":"herb,thảo mộc,herb,plant","category":"animals"},
    {"emoji":"☘️","name":"shamrock","keywords":"shamrock,cỏ ba lá,shamrock,irish","category":"animals"},
    {"emoji":"🍀","name":"four leaf clover","keywords":"four leaf clover,cỏ bốn lá,clover,lucky","category":"animals"},
    {"emoji":"🍁","name":"maple leaf","keywords":"maple leaf,lá phong,maple leaf,canada","category":"animals"},
    {"emoji":"🍂","name":"fallen leaf","keywords":"fallen leaf,lá rụng,leaf,autumn","category":"animals"},
    {"emoji":"🍃","name":"leaf fluttering in wind","keywords":"leaf fluttering in wind,lá bay trong gió,leaf,wind","category":"animals"},
    {"emoji":"🍄","name":"mushroom","keywords":"mushroom,nấm,mushroom,fungi","category":"animals"},
    {"emoji":"🌰","name":"chestnut","keywords":"chestnut,hạt dẻ,chestnut,food","category":"animals"},
    
    // ========== ĐỒ ĂN & THỨC UỐNG (100+) ==========
    {"emoji":"🍇","name":"grapes","keywords":"grapes,nho,grapes,fruit","category":"food"},
    {"emoji":"🍈","name":"melon","keywords":"melon,dưa lưới,melon,fruit","category":"food"},
    {"emoji":"🍉","name":"watermelon","keywords":"watermelon,dưa hấu,watermelon,fruit","category":"food"},
    {"emoji":"🍊","name":"tangerine","keywords":"tangerine,quýt,tangerine,fruit","category":"food"},
    {"emoji":"🍋","name":"lemon","keywords":"lemon,chanh,lemon,fruit","category":"food"},
    {"emoji":"🍌","name":"banana","keywords":"banana,chuối,banana,fruit","category":"food"},
    {"emoji":"🍍","name":"pineapple","keywords":"pineapple,dứa,pineapple,fruit","category":"food"},
    {"emoji":"🥭","name":"mango","keywords":"mango,xoài,mango,fruit","category":"food"},
    {"emoji":"🍎","name":"red apple","keywords":"red apple,táo đỏ,apple,fruit","category":"food"},
    {"emoji":"🍏","name":"green apple","keywords":"green apple,táo xanh,apple,fruit","category":"food"},
    {"emoji":"🍐","name":"pear","keywords":"pear,lê,pear,fruit","category":"food"},
    {"emoji":"🍑","name":"peach","keywords":"peach,đào,peach,fruit","category":"food"},
    {"emoji":"🍒","name":"cherries","keywords":"cherries,anh đào,cherries,fruit","category":"food"},
    {"emoji":"🍓","name":"strawberry","keywords":"strawberry,dâu tây,strawberry,fruit","category":"food"},
    {"emoji":"🫐","name":"blueberries","keywords":"blueberries,việt quất,blueberries,fruit","category":"food"},
    {"emoji":"🥝","name":"kiwi fruit","keywords":"kiwi fruit,kiwi,kiwi,fruit","category":"food"},
    {"emoji":"🍅","name":"tomato","keywords":"tomato,cà chua,tomato,vegetable","category":"food"},
    {"emoji":"🫒","name":"olive","keywords":"olive,ô liu,olive,food","category":"food"},
    {"emoji":"🥥","name":"coconut","keywords":"coconut,dừa,coconut,fruit","category":"food"},
    {"emoji":"🥑","name":"avocado","keywords":"avocado,bơ,avocado,fruit","category":"food"},
    {"emoji":"🍆","name":"eggplant","keywords":"eggplant,cà tím,eggplant,vegetable","category":"food"},
    {"emoji":"🥔","name":"potato","keywords":"potato,khoai tây,potato,vegetable","category":"food"},
    {"emoji":"🥕","name":"carrot","keywords":"carrot,cà rốt,carrot,vegetable","category":"food"},
    {"emoji":"🌽","name":"ear of corn","keywords":"ear of corn,bắp ngô,corn,vegetable","category":"food"},
    {"emoji":"🌶️","name":"hot pepper","keywords":"hot pepper,ớt,pepper,spicy","category":"food"},
    {"emoji":"🫑","name":"bell pepper","keywords":"bell pepper,ớt chuông,pepper,vegetable","category":"food"},
    {"emoji":"🥒","name":"cucumber","keywords":"cucumber,dưa chuột,cucumber,vegetable","category":"food"},
    {"emoji":"🥬","name":"leafy green","keywords":"leafy green,rau xanh,vegetable,green","category":"food"},
    {"emoji":"🥦","name":"broccoli","keywords":"broccoli,bông cải xanh,broccoli,vegetable","category":"food"},
    {"emoji":"🧄","name":"garlic","keywords":"garlic,tỏi,garlic,food","category":"food"},
    {"emoji":"🧅","name":"onion","keywords":"onion,hành tây,onion,vegetable","category":"food"},
    {"emoji":"🍄","name":"mushroom","keywords":"mushroom,nấm,mushroom,food","category":"food"},
    {"emoji":"🥜","name":"peanuts","keywords":"peanuts,đậu phộng,peanuts,food","category":"food"},
    {"emoji":"🌰","name":"chestnut","keywords":"chestnut,hạt dẻ,chestnut,food","category":"food"},
    {"emoji":"🍞","name":"bread","keywords":"bread,bánh mì,bread,food","category":"food"},
    {"emoji":"🥐","name":"croissant","keywords":"croissant,bánh sừng bò,croissant,food","category":"food"},
    {"emoji":"🥖","name":"baguette bread","keywords":"baguette bread,bánh mì baguette,baguette,food","category":"food"},
    {"emoji":"🫓","name":"flatbread","keywords":"flatbread,bánh mì dẹt,flatbread,food","category":"food"},
    {"emoji":"🥨","name":"pretzel","keywords":"pretzel,bánh pretzel,pretzel,food","category":"food"},
    {"emoji":"🥯","name":"bagel","keywords":"bagel,bánh bagel,bagel,food","category":"food"},
    {"emoji":"🥞","name":"pancakes","keywords":"pancakes,bánh kếp,pancakes,food","category":"food"},
    {"emoji":"🧇","name":"waffle","keywords":"waffle,bánh waffle,waffle,food","category":"food"},
    {"emoji":"🧀","name":"cheese wedge","keywords":"cheese wedge,phô mai,cheese,food","category":"food"},
    {"emoji":"🍖","name":"meat on bone","keywords":"meat on bone,thịt trên xương,meat,food","category":"food"},
    {"emoji":"🍗","name":"poultry leg","keywords":"poultry leg,đùi gà,chicken,food","category":"food"},
    {"emoji":"🥩","name":"cut of meat","keywords":"cut of meat,miếng thịt,meat,food","category":"food"},
    {"emoji":"🥓","name":"bacon","keywords":"bacon,thịt xông khói,bacon,food","category":"food"},
    {"emoji":"🍔","name":"hamburger","keywords":"hamburger,hamburger,burger,food","category":"food"},
    {"emoji":"🍟","name":"french fries","keywords":"french fries,khoai tây chiên,fries,food","category":"food"},
    {"emoji":"🍕","name":"pizza","keywords":"pizza,pizza,pizza,food","category":"food"},
    {"emoji":"🌭","name":"hot dog","keywords":"hot dog,hot dog,hotdog,food","category":"food"},
    {"emoji":"🥪","name":"sandwich","keywords":"sandwich,bánh sandwich,sandwich,food","category":"food"},
    {"emoji":"🌮","name":"taco","keywords":"taco,taco,mexican,food","category":"food"},
    {"emoji":"🌯","name":"burrito","keywords":"burrito,burrito,mexican,food","category":"food"},
    {"emoji":"🫔","name":"tamale","keywords":"tamale,tamale,mexican,food","category":"food"},
    {"emoji":"🥙","name":"stuffed flatbread","keywords":"stuffed flatbread,bánh mì kẹp,flatbread,food","category":"food"},
    {"emoji":"🧆","name":"falafel","keywords":"falafel,falafel,middle eastern,food","category":"food"},
    {"emoji":"🥚","name":"egg","keywords":"egg,trứng,egg,food","category":"food"},
    {"emoji":"🍳","name":"cooking","keywords":"cooking,đang nấu ăn,cooking,food","category":"food"},
    {"emoji":"🥘","name":"shallow pan of food","keywords":"shallow pan of food,chảo thức ăn,pan,food","category":"food"},
    {"emoji":"🍲","name":"pot of food","keywords":"pot of food,nồi thức ăn,pot,food","category":"food"},
    {"emoji":"🫕","name":"fondue","keywords":"fondue,fondue,cheese,food","category":"food"},
    {"emoji":"🥣","name":"bowl with spoon","keywords":"bowl with spoon,tô với muỗng,bowl,food","category":"food"},
    {"emoji":"🥗","name":"green salad","keywords":"green salad,rau trộn,salad,food","category":"food"},
    {"emoji":"🍿","name":"popcorn","keywords":"popcorn,bỏng ngô,popcorn,snack","category":"food"},
    {"emoji":"🧈","name":"butter","keywords":"butter,bơ,butter,food","category":"food"},
    {"emoji":"🧂","name":"salt","keywords":"salt,muối,salt,seasoning","category":"food"},
    {"emoji":"🥫","name":"canned food","keywords":"canned food,đồ hộp,canned,food","category":"food"},
    {"emoji":"🍱","name":"bento box","keywords":"bento box,hộp bento,bento,japanese","category":"food"},
    {"emoji":"🍘","name":"rice cracker","keywords":"rice cracker,bánh gạo,cracker,food","category":"food"},
    {"emoji":"🍙","name":"rice ball","keywords":"rice ball,cơm nắm,rice ball,japanese","category":"food"},
    {"emoji":"🍚","name":"cooked rice","keywords":"cooked rice,cơm chín,rice,food","category":"food"},
    {"emoji":"🍛","name":"curry rice","keywords":"curry rice,cơm cà ri,curry,food","category":"food"},
    {"emoji":"🍜","name":"steaming bowl","keywords":"steaming bowl,tô mì nóng,bowl,noodles","category":"food"},
    {"emoji":"🍝","name":"spaghetti","keywords":"spaghetti,spaghetti,pasta,food","category":"food"},
    {"emoji":"🍠","name":"roasted sweet potato","keywords":"roasted sweet potato,khoai lang nướng,sweet potato,food","category":"food"},
    {"emoji":"🍢","name":"oden","keywords":"oden,oden,japanese,food","category":"food"},
    {"emoji":"🍣","name":"sushi","keywords":"sushi,sushi,japanese,food","category":"food"},
    {"emoji":"🍤","name":"fried shrimp","keywords":"fried shrimp,tôm chiên,shrimp,food","category":"food"},
    {"emoji":"🍥","name":"fish cake with swirl","keywords":"fish cake with swirl,bánh cá,kamaboko,japanese","category":"food"},
    {"emoji":"🥮","name":"moon cake","keywords":"moon cake,bánh trung thu,moon cake,chinese","category":"food"},
    {"emoji":"🍡","name":"dango","keywords":"dango,dango,japanese,food","category":"food"},
    {"emoji":"🥟","name":"dumpling","keywords":"dumpling,bánh bao,dumpling,food","category":"food"},
    {"emoji":"🥠","name":"fortune cookie","keywords":"fortune cookie,bánh quy may mắn,fortune cookie,chinese","category":"food"},
    {"emoji":"🥡","name":"takeout box","keywords":"takeout box,hộp mang về,takeout,food","category":"food"},
    {"emoji":"🦀","name":"crab","keywords":"crab,cua,crab,seafood","category":"food"},
    {"emoji":"🦞","name":"lobster","keywords":"lobster,tôm hùm,lobster,seafood","category":"food"},
    {"emoji":"🦐","name":"shrimp","keywords":"shrimp,tôm,shrimp,seafood","category":"food"},
    {"emoji":"🦑","name":"squid","keywords":"squid,mực,squid,seafood","category":"food"},
    {"emoji":"🦪","name":"oyster","keywords":"oyster,hàu,oyster,seafood","category":"food"},
    {"emoji":"🍦","name":"soft ice cream","keywords":"soft ice cream,kem mềm,ice cream,dessert","category":"food"},
    {"emoji":"🍧","name":"shaved ice","keywords":"shaved ice,đá bào,shaved ice,dessert","category":"food"},
    {"emoji":"🍨","name":"ice cream","keywords":"ice cream,kem,ice cream,dessert","category":"food"},
    {"emoji":"🍩","name":"doughnut","keywords":"doughnut,bánh donut,donut,dessert","category":"food"},
    {"emoji":"🍪","name":"cookie","keywords":"cookie,bánh quy,cookie,dessert","category":"food"},
    {"emoji":"🎂","name":"birthday cake","keywords":"birthday cake,bánh sinh nhật,birthday cake,dessert","category":"food"},
    {"emoji":"🍰","name":"shortcake","keywords":"shortcake,bánh gato,cake,dessert","category":"food"},
    {"emoji":"🧁","name":"cupcake","keywords":"cupcake,bánh cupcake,cupcake,dessert","category":"food"},
    {"emoji":"🥧","name":"pie","keywords":"pie,bánh pie,pie,dessert","category":"food"},
    {"emoji":"🍫","name":"chocolate bar","keywords":"chocolate bar,thanh sô cô la,chocolate,sweet","category":"food"},
    {"emoji":"🍬","name":"candy","keywords":"candy,kẹo,candy,sweet","category":"food"},
    {"emoji":"🍭","name":"lollipop","keywords":"lollipop,kẹo mút,lollipop,sweet","category":"food"},
    {"emoji":"🍮","name":"custard","keywords":"custard,bánh flan,custard,dessert","category":"food"},
    {"emoji":"🍯","name":"honey pot","keywords":"honey pot,hũ mật ong,honey,sweet","category":"food"},
    {"emoji":"🍼","name":"baby bottle","keywords":"baby bottle,bình sữa trẻ em,baby bottle,milk","category":"food"},
    {"emoji":"🥛","name":"glass of milk","keywords":"glass of milk,ly sữa,milk,drink","category":"food"},
    {"emoji":"☕","name":"hot beverage","keywords":"hot beverage,đồ uống nóng,coffee,tea","category":"food"},
    {"emoji":"🫖","name":"teapot","keywords":"teapot,ấm trà,teapot,tea","category":"food"},
    {"emoji":"🍵","name":"teacup without handle","keywords":"teacup without handle,tách trà không quai,tea,drink","category":"food"},
    {"emoji":"🍶","name":"sake","keywords":"sake,rượu sake,sake,japanese","category":"food"},
    {"emoji":"🍾","name":"bottle with popping cork","keywords":"bottle with popping cork,chai nút bật,champagne,celebration","category":"food"},
    {"emoji":"🍷","name":"wine glass","keywords":"wine glass,ly rượu vang,wine,drink","category":"food"},
    {"emoji":"🍸","name":"cocktail glass","keywords":"cocktail glass,ly cocktail,cocktail,drink","category":"food"},
    {"emoji":"🍹","name":"tropical drink","keywords":"tropical drink,đồ uống nhiệt đới,cocktail,drink","category":"food"},
    {"emoji":"🍺","name":"beer mug","keywords":"beer mug,cốc bia,beer,drink","category":"food"},
    {"emoji":"🍻","name":"clinking beer mugs","keywords":"clinking beer mugs,cốc bia chạm nhau,beer,cheers","category":"food"},
    {"emoji":"🥂","name":"clinking glasses","keywords":"clinking glasses,ly chạm nhau,cheers,celebration","category":"food"},
    {"emoji":"🥃","name":"tumbler glass","keywords":"tumbler glass,ly whisky,whisky,drink","category":"food"},
    {"emoji":"🥤","name":"cup with straw","keywords":"cup with straw,ly với ống hút,drink,straw","category":"food"},
    {"emoji":"🧋","name":"bubble tea","keywords":"bubble tea,trà sữa trân châu,bubble tea,drink","category":"food"},
    {"emoji":"🧃","name":"beverage box","keywords":"beverage box,hộp đồ uống,juice box,drink","category":"food"},
    {"emoji":"🧉","name":"mate","keywords":"mate,mate,drink,tea","category":"food"},
    {"emoji":"🧊","name":"ice","keywords":"ice,đá,ice,cold","category":"food"},
    {"emoji":"🥢","name":"chopsticks","keywords":"chopsticks,đũa,chopsticks,asian","category":"food"},
    {"emoji":"🍽️","name":"fork and knife with plate","keywords":"fork and knife with plate,dĩa và dao với đĩa,cutlery,dining","category":"food"},
    {"emoji":"🍴","name":"fork and knife","keywords":"fork and knife,dĩa và dao,cutlery,dining","category":"food"},
    {"emoji":"🥄","name":"spoon","keywords":"spoon,muỗng,spoon,cutlery","category":"food"},
    {"emoji":"🔪","name":"kitchen knife","keywords":"kitchen knife,dao bếp,knife,kitchen","category":"food"},
    {"emoji":"🏺","name":"amphora","keywords":"amphora,bình cổ,amphora,history","category":"food"},
    
    // ========== HOẠT ĐỘNG (80+) ==========
    {"emoji":"⚽","name":"soccer ball","keywords":"soccer ball,bóng đá,soccer,football,sport","category":"activities"},
    {"emoji":"🏀","name":"basketball","keywords":"basketball,bóng rổ,basketball,sport","category":"activities"},
    {"emoji":"🏈","name":"american football","keywords":"american football,bóng bầu dục Mỹ,football,sport","category":"activities"},
    {"emoji":"⚾","name":"baseball","keywords":"baseball,bóng chày,baseball,sport","category":"activities"},
    {"emoji":"🥎","name":"softball","keywords":"softball,bóng mềm,softball,sport","category":"activities"},
    {"emoji":"🏐","name":"volleyball","keywords":"volleyball,bóng chuyền,volleyball,sport","category":"activities"},
    {"emoji":"🏉","name":"rugby football","keywords":"rugby football,bóng bầu dục,rugby,sport","category":"activities"},
    {"emoji":"🥏","name":"flying disc","keywords":"flying disc,đĩa bay,frisbee,sport","category":"activities"},
    {"emoji":"🎱","name":"pool 8 ball","keywords":"pool 8 ball,bi-a số 8,pool,billiards","category":"activities"},
    {"emoji":"🔮","name":"crystal ball","keywords":"crystal ball,quả cầu pha lê,crystal ball,fortune","category":"activities"},
    {"emoji":"🧿","name":"nazar amulet","keywords":"nazar amulet,bùa mắt quỷ,nazar,amulet","category":"activities"},
    {"emoji":"🎮","name":"video game","keywords":"video game,máy chơi game,video game,gaming","category":"activities"},
    {"emoji":"🕹️","name":"joystick","keywords":"joystick,cần điều khiển,joystick,gaming","category":"activities"},
    {"emoji":"🎲","name":"game die","keywords":"game die,xúc xắc,dice,gaming","category":"activities"},
    {"emoji":"🧩","name":"puzzle piece","keywords":"puzzle piece,mảnh ghép,puzzle,game","category":"activities"},
    {"emoji":"🧸","name":"teddy bear","keywords":"teddy bear,gấu bông,teddy bear,toy","category":"activities"},
    {"emoji":"🪅","name":"piñata","keywords":"piñata,piñata,party,mexican","category":"activities"},
    {"emoji":"🪆","name":"nesting dolls","keywords":"nesting dolls,búp bê lồng nhau,matryoshka,russian","category":"activities"},
    {"emoji":"♠️","name":"spade suit","keywords":"spade suit,chất bích,spades,card","category":"activities"},
    {"emoji":"♥️","name":"heart suit","keywords":"heart suit,chất cơ,hearts,card","category":"activities"},
    {"emoji":"♦️","name":"diamond suit","keywords":"diamond suit,chất rô,diamonds,card","category":"activities"},
    {"emoji":"♣️","name":"club suit","keywords":"club suit,chất nhép,clubs,card","category":"activities"},
    {"emoji":"♟️","name":"chess pawn","keywords":"chess pawn,tốt cờ vua,chess pawn,chess","category":"activities"},
    {"emoji":"🃏","name":"joker","keywords":"joker,phăng teo,joker,card","category":"activities"},
    {"emoji":"🀄","name":"mahjong red dragon","keywords":"mahjong red dragon,con rồng đỏ mahjong,mahjong,game","category":"activities"},
    {"emoji":"🎴","name":"flower playing cards","keywords":"flower playing cards,bài hoa,flower cards,japanese","category":"activities"},
    {"emoji":"🎭","name":"performing arts","keywords":"performing arts,nghệ thuật biểu diễn,theater,performing arts","category":"activities"},
    {"emoji":"🖼️","name":"framed picture","keywords":"framed picture,bức tranh có khung,picture,art","category":"activities"},
    {"emoji":"🎨","name":"artist palette","keywords":"artist palette,bảng màu họa sĩ,palette,art","category":"activities"},
    {"emoji":"🧵","name":"thread","keywords":"thread,chỉ,thread,sewing","category":"activities"},
    {"emoji":"🪡","name":"sewing needle","keywords":"sewing needle,kim khâu,needle,sewing","category":"activities"},
    {"emoji":"🧶","name":"yarn","keywords":"yarn,cuộn len,yarn,knitting","category":"activities"},
    {"emoji":"🪢","name":"knot","keywords":"knot,nút thắt,knot,rope","category":"activities"},
    {"emoji":"👓","name":"glasses","keywords":"glasses,kính mắt,glasses,vision","category":"activities"},
    {"emoji":"🕶️","name":"sunglasses","keywords":"sunglasses,kính râm,sunglasses,sun","category":"activities"},
    {"emoji":"🥽","name":"goggles","keywords":"goggles,kính bảo hộ,goggles,protection","category":"activities"},
    {"emoji":"🥼","name":"lab coat","keywords":"lab coat,áo khoác phòng thí nghiệm,lab coat,science","category":"activities"},
    {"emoji":"🦺","name":"safety vest","keywords":"safety vest,áo vest an toàn,safety vest,construction","category":"activities"},
    {"emoji":"👔","name":"necktie","keywords":"necktie,cà vạt,tie,formal","category":"activities"},
    {"emoji":"👕","name":"t-shirt","keywords":"t-shirt,áo thun,tshirt,casual","category":"activities"},
    {"emoji":"👖","name":"jeans","keywords":"jeans,quần jeans,jeans,pants","category":"activities"},
    {"emoji":"🧣","name":"scarf","keywords":"scarf,khăn quàng cổ,scarf,winter","category":"activities"},
    {"emoji":"🧤","name":"gloves","keywords":"gloves,găng tay,gloves,winter","category":"activities"},
    {"emoji":"🧥","name":"coat","keywords":"coat,áo khoác,coat,winter","category":"activities"},
    {"emoji":"🧦","name":"socks","keywords":"socks,tất,socks,clothing","category":"activities"},
    {"emoji":"👗","name":"dress","keywords":"dress,váy,dress,clothing","category":"activities"},
    {"emoji":"👘","name":"kimono","keywords":"kimono,áo kimono,kimono,japanese","category":"activities"},
    {"emoji":"🥻","name":"sari","keywords":"sari,áo sari,sari,indian","category":"activities"},
    {"emoji":"🩱","name":"one-piece swimsuit","keywords":"one-piece swimsuit,đồ bơi một mảnh,swimsuit,beach","category":"activities"},
    {"emoji":"🩲","name":"briefs","keywords":"briefs,quần lót nam,briefs,underwear","category":"activities"},
    {"emoji":"🩳","name":"shorts","keywords":"shorts,quần đùi,shorts,clothing","category":"activities"},
    {"emoji":"👙","name":"bikini","keywords":"bikini,bikini,swimsuit,beach","category":"activities"},
    {"emoji":"👚","name":"woman's clothes","keywords":"woman's clothes,quần áo phụ nữ,women's clothes,clothing","category":"activities"},
    {"emoji":"👛","name":"purse","keywords":"purse,ví nhỏ,purse,bag","category":"activities"},
    {"emoji":"👜","name":"handbag","keywords":"handbag,túi xách,handbag,bag","category":"activities"},
    {"emoji":"👝","name":"clutch bag","keywords":"clutch bag,túi xách nhỏ,clutch bag,bag","category":"activities"},
    {"emoji":"🎒","name":"backpack","keywords":"backpack,ba lô,backpack,school","category":"activities"},
    {"emoji":"🩴","name":"thong sandal","keywords":"thong sandal,dép xỏ ngón,sandal,footwear","category":"activities"},
    {"emoji":"👞","name":"man's shoe","keywords":"man's shoe,giày nam,man's shoe,footwear","category":"activities"},
    {"emoji":"👟","name":"running shoe","keywords":"running shoe,giày chạy,running shoe,sport","category":"activities"},
    {"emoji":"🥾","name":"hiking boot","keywords":"hiking boot,giày leo núi,hiking boot,outdoor","category":"activities"},
    {"emoji":"🥿","name":"flat shoe","keywords":"flat shoe,giày bệt,flat shoe,footwear","category":"activities"},
    {"emoji":"👠","name":"high-heeled shoe","keywords":"high-heeled shoe,giày cao gót,high heels,footwear","category":"activities"},
    {"emoji":"👡","name":"woman's sandal","keywords":"woman's sandal,dép nữ,woman's sandal,footwear","category":"activities"},
    {"emoji":"🩰","name":"ballet shoes","keywords":"ballet shoes,giày ballet,ballet shoes,dance","category":"activities"},
    {"emoji":"👢","name":"woman's boot","keywords":"woman's boot,bốt nữ,woman's boot,footwear","category":"activities"},
    {"emoji":"👑","name":"crown","keywords":"crown,vương miện,crown,royal","category":"activities"},
    {"emoji":"👒","name":"woman's hat","keywords":"woman's hat,mũ nữ,woman's hat,accessory","category":"activities"},
    {"emoji":"🎩","name":"top hat","keywords":"top hat,mũ chóp cao,top hat,formal","category":"activities"},
    {"emoji":"🎓","name":"graduation cap","keywords":"graduation cap,mũ tốt nghiệp,graduation cap,school","category":"activities"},
    {"emoji":"🧢","name":"billed cap","keywords":"billed cap,mũ lưỡi trai,cap,casual","category":"activities"},
    {"emoji":"🪖","name":"military helmet","keywords":"military helmet,mũ bảo hiểm quân đội,military helmet,army","category":"activities"},
    {"emoji":"⛑️","name":"rescue worker's helmet","keywords":"rescue worker's helmet,mũ cứu hộ,rescue helmet,safety","category":"activities"},
    {"emoji":"📿","name":"prayer beads","keywords":"prayer beads,chuỗi hạt cầu nguyện,prayer beads,religion","category":"activities"},
    {"emoji":"💄","name":"lipstick","keywords":"lipstick,son môi,lipstick,makeup","category":"activities"},
    {"emoji":"💍","name":"ring","keywords":"ring,nhẫn,ring,jewelry","category":"activities"},
    {"emoji":"💎","name":"gem stone","keywords":"gem stone,đá quý,gem stone,jewelry","category":"activities"},
    
    // ========== DU LỊCH & ĐỊA ĐIỂM (80+) ==========
    {"emoji":"🚗","name":"automobile","keywords":"automobile,ô tô,car,vehicle","category":"travel"},
    {"emoji":"🚕","name":"taxi","keywords":"taxi,taxi,taxi,vehicle","category":"travel"},
    {"emoji":"🚙","name":"sport utility vehicle","keywords":"sport utility vehicle,xe SUV,suv,vehicle","category":"travel"},
    {"emoji":"🚌","name":"bus","keywords":"bus,xe buýt,bus,vehicle","category":"travel"},
    {"emoji":"🚎","name":"trolleybus","keywords":"trolleybus,xe buýt điện,trolleybus,vehicle","category":"travel"},
    {"emoji":"🏎️","name":"racing car","keywords":"racing car,xe đua,racing car,sport","category":"travel"},
    {"emoji":"🏍️","name":"motorcycle","keywords":"motorcycle,xe máy,motorcycle,vehicle","category":"travel"},
    {"emoji":"🛵","name":"motor scooter","keywords":"motor scooter,xe tay ga,scooter,vehicle","category":"travel"},
    {"emoji":"🛺","name":"auto rickshaw","keywords":"auto rickshaw,xe lam,rickshaw,vehicle","category":"travel"},
    {"emoji":"🚲","name":"bicycle","keywords":"bicycle,xe đạp,bicycle,vehicle","category":"travel"},
    {"emoji":"🛴","name":"kick scooter","keywords":"kick scooter,xe trượt scooter,scooter,vehicle","category":"travel"},
    {"emoji":"🚏","name":"bus stop","keywords":"bus stop,trạm xe buýt,bus stop,transport","category":"travel"},
    {"emoji":"🛣️","name":"motorway","keywords":"motorway,đường cao tốc,highway,road","category":"travel"},
    {"emoji":"🛤️","name":"railway track","keywords":"railway track,đường ray,train track,railway","category":"travel"},
    {"emoji":"⛽","name":"fuel pump","keywords":"fuel pump,cây xăng,fuel pump,gas","category":"travel"},
    {"emoji":"🚨","name":"police car light","keywords":"police car light,đèn cảnh sát,police light,emergency","category":"travel"},
    {"emoji":"🚥","name":"horizontal traffic light","keywords":"horizontal traffic light,đèn giao thông ngang,traffic light,road","category":"travel"},
    {"emoji":"🚦","name":"vertical traffic light","keywords":"vertical traffic light,đèn giao thông dọc,traffic light,road","category":"travel"},
    {"emoji":"🛑","name":"stop sign","keywords":"stop sign,biển dừng,stop sign,road","category":"travel"},
    {"emoji":"🚧","name":"construction","keywords":"construction,công trường xây dựng,construction,road","category":"travel"},
    {"emoji":"⚓","name":"anchor","keywords":"anchor,mỏ neo,anchor,ship","category":"travel"},
    {"emoji":"⛵","name":"sailboat","keywords":"sailboat,thuyền buồm,sailboat,boat","category":"travel"},
    {"emoji":"🛶","name":"canoe","keywords":"canoe,thuyền độc mộc,canoe,boat","category":"travel"},
    {"emoji":"🚤","name":"speedboat","keywords":"speedboat,thuyền máy,speedboat,boat","category":"travel"},
    {"emoji":"🛳️","name":"passenger ship","keywords":"passenger ship,tàu du lịch,passenger ship,cruise","category":"travel"},
    {"emoji":"⛴️","name":"ferry","keywords":"ferry,phà,ferry,boat","category":"travel"},
    {"emoji":"🛥️","name":"motor boat","keywords":"motor boat,thuyền máy,motor boat,boat","category":"travel"},
    {"emoji":"🚢","name":"ship","keywords":"ship,tàu thủy,ship,boat","category":"travel"},
    {"emoji":"✈️","name":"airplane","keywords":"airplane,máy bay,airplane,flight","category":"travel"},
    {"emoji":"🛩️","name":"small airplane","keywords":"small airplane,máy bay nhỏ,small airplane,flight","category":"travel"},
    {"emoji":"🛫","name":"airplane departure","keywords":"airplane departure,máy bay cất cánh,airplane departure,flight","category":"travel"},
    {"emoji":"🛬","name":"airplane arrival","keywords":"airplane arrival,máy bay hạ cánh,airplane arrival,flight","category":"travel"},
    {"emoji":"🪂","name":"parachute","keywords":"parachute,dù lượn,parachute,sky","category":"travel"},
    {"emoji":"💺","name":"seat","keywords":"seat,ghế ngồi,seat,chair","category":"travel"},
    {"emoji":"🚁","name":"helicopter","keywords":"helicopter,máy bay trực thăng,helicopter,flight","category":"travel"},
    {"emoji":"🚟","name":"suspension railway","keywords":"suspension railway,đường sắt treo,suspension railway,train","category":"travel"},
    {"emoji":"🚠","name":"mountain cableway","keywords":"mountain cableway,cáp treo núi,cable car,mountain","category":"travel"},
    {"emoji":"🚡","name":"aerial tramway","keywords":"aerial tramway,cáp treo trên không,aerial tramway,transport","category":"travel"},
    {"emoji":"🛰️","name":"satellite","keywords":"satellite,vệ tinh,satellite,space","category":"travel"},
    {"emoji":"🚀","name":"rocket","keywords":"rocket,tên lửa,rocket,space","category":"travel"},
    {"emoji":"🛸","name":"flying saucer","keywords":"flying saucer,đĩa bay,flying saucer,alien","category":"travel"},
    {"emoji":"🛎️","name":"bellhop bell","keywords":"bellhop bell,chuông khách sạn,bellhop bell,hotel","category":"travel"},
    {"emoji":"🧳","name":"luggage","keywords":"luggage,hành lý,luggage,travel","category":"travel"},
    {"emoji":"⌛","name":"hourglass done","keywords":"hourglass done,đồng hồ cát hết giờ,hourglass,time","category":"travel"},
    {"emoji":"⏳","name":"hourglass not done","keywords":"hourglass not done,đồng hồ cát chưa hết,hourglass,time","category":"travel"},
    {"emoji":"⌚","name":"watch","keywords":"watch,đồng hồ đeo tay,watch,time","category":"travel"},
    {"emoji":"⏰","name":"alarm clock","keywords":"alarm clock,đồng hồ báo thức,alarm clock,time","category":"travel"},
    {"emoji":"⏱️","name":"stopwatch","keywords":"stopwatch,đồng hồ bấm giờ,stopwatch,time","category":"travel"},
    {"emoji":"⏲️","name":"timer clock","keywords":"timer clock,đồng hồ hẹn giờ,timer clock,time","category":"travel"},
    {"emoji":"🕰️","name":"mantelpiece clock","keywords":"mantelpiece clock,đồng hồ để bàn,mantel clock,time","category":"travel"},
    {"emoji":"🕛","name":"twelve o'clock","keywords":"twelve o'clock,mười hai giờ,12 o'clock,time","category":"travel"},
    {"emoji":"🕧","name":"twelve-thirty","keywords":"twelve-thirty,mười hai giờ ba mươi,12:30,time","category":"travel"},
    {"emoji":"🕐","name":"one o'clock","keywords":"one o'clock,một giờ,1 o'clock,time","category":"travel"},
    {"emoji":"🕜","name":"one-thirty","keywords":"one-thirty,một giờ ba mươi,1:30,time","category":"travel"},
    {"emoji":"🕑","name":"two o'clock","keywords":"two o'clock,hai giờ,2 o'clock,time","category":"travel"},
    {"emoji":"🕝","name":"two-thirty","keywords":"two-thirty,hai giờ ba mươi,2:30,time","category":"travel"},
    {"emoji":"🕒","name":"three o'clock","keywords":"three o'clock,ba giờ,3 o'clock,time","category":"travel"},
    {"emoji":"🕞","name":"three-thirty","keywords":"three-thirty,ba giờ ba mươi,3:30,time","category":"travel"},
    {"emoji":"🕓","name":"four o'clock","keywords":"four o'clock,bốn giờ,4 o'clock,time","category":"travel"},
    {"emoji":"🕟","name":"four-thirty","keywords":"four-thirty,bốn giờ ba mươi,4:30,time","category":"travel"},
    {"emoji":"🕔","name":"five o'clock","keywords":"five o'clock,năm giờ,5 o'clock,time","category":"travel"},
    {"emoji":"🕠","name":"five-thirty","keywords":"five-thirty,năm giờ ba mươi,5:30,time","category":"travel"},
    {"emoji":"🕕","name":"six o'clock","keywords":"six o'clock,sáu giờ,6 o'clock,time","category":"travel"},
    {"emoji":"🕡","name":"six-thirty","keywords":"six-thirty,sáu giờ ba mươi,6:30,time","category":"travel"},
    {"emoji":"🕖","name":"seven o'clock","keywords":"seven o'clock,bảy giờ,7 o'clock,time","category":"travel"},
    {"emoji":"🕢","name":"seven-thirty","keywords":"seven-thirty,bảy giờ ba mươi,7:30,time","category":"travel"},
    {"emoji":"🕗","name":"eight o'clock","keywords":"eight o'clock,tám giờ,8 o'clock,time","category":"travel"},
    {"emoji":"🕣","name":"eight-thirty","keywords":"eight-thirty,tám giờ ba mươi,8:30,time","category":"travel"},
    {"emoji":"🕘","name":"nine o'clock","keywords":"nine o'clock,chín giờ,9 o'clock,time","category":"travel"},
    {"emoji":"🕤","name":"nine-thirty","keywords":"nine-thirty,chín giờ ba mươi,9:30,time","category":"travel"},
    {"emoji":"🕙","name":"ten o'clock","keywords":"ten o'clock,mười giờ,10 o'clock,time","category":"travel"},
    {"emoji":"🕥","name":"ten-thirty","keywords":"ten-thirty,mười giờ ba mươi,10:30,time","category":"travel"},
    {"emoji":"🕚","name":"eleven o'clock","keywords":"eleven o'clock,mười một giờ,11 o'clock,time","category":"travel"},
    {"emoji":"🕦","name":"eleven-thirty","keywords":"eleven-thirty,mười một giờ ba mươi,11:30,time","category":"travel"},
    {"emoji":"🌑","name":"new moon","keywords":"new moon,trăng non,new moon,moon","category":"travel"},
    {"emoji":"🌒","name":"waxing crescent moon","keywords":"waxing crescent moon,trăng lưỡi liềm đầu tháng,crescent moon,moon","category":"travel"},
    {"emoji":"🌓","name":"first quarter moon","keywords":"first quarter moon,trăng bán nguyệt đầu,first quarter moon,moon","category":"travel"},
    {"emoji":"🌔","name":"waxing gibbous moon","keywords":"waxing gibbous moon,trăng khuyết đầu tháng,gibbous moon,moon","category":"travel"},
    {"emoji":"🌕","name":"full moon","keywords":"full moon,trăng tròn,full moon,moon","category":"travel"},
    {"emoji":"🌖","name":"waning gibbous moon","keywords":"waning gibbous moon,trăng khuyết cuối tháng,gibbous moon,moon","category":"travel"},
    {"emoji":"🌗","name":"last quarter moon","keywords":"last quarter moon,trăng bán nguyệt cuối,last quarter moon,moon","category":"travel"},
    {"emoji":"🌘","name":"waning crescent moon","keywords":"waning crescent moon,trăng lưỡi liềm cuối,crescent moon,moon","category":"travel"},
    {"emoji":"🌙","name":"crescent moon","keywords":"crescent moon,trăng lưỡi liềm,crescent moon,moon","category":"travel"},
    {"emoji":"🌚","name":"new moon face","keywords":"new moon face,mặt trăng non,moon face,moon","category":"travel"},
    {"emoji":"🌛","name":"first quarter moon face","keywords":"first quarter moon face,mặt trăng bán nguyệt đầu,moon face,moon","category":"travel"},
    {"emoji":"🌜","name":"last quarter moon face","keywords":"last quarter moon face,mặt trăng bán nguyệt cuối,moon face,moon","category":"travel"},
    {"emoji":"🌡️","name":"thermometer","keywords":"thermometer,nhiệt kế,thermometer,temperature","category":"travel"},
    {"emoji":"☀️","name":"sun","keywords":"sun,mặt trời,sun,weather","category":"travel"},
    {"emoji":"🌝","name":"full moon face","keywords":"full moon face,mặt trăng tròn,moon face,moon","category":"travel"},
    {"emoji":"🌞","name":"sun with face","keywords":"sun with face,mặt trời có mặt,sun face,sun","category":"travel"},
    {"emoji":"🪐","name":"ringed planet","keywords":"ringed planet,hành tinh có vòng,ringed planet,space","category":"travel"},
    {"emoji":"⭐","name":"star","keywords":"star,ngôi sao,star,night","category":"travel"},
    {"emoji":"🌟","name":"glowing star","keywords":"glowing star,ngôi sao lấp lánh,glowing star,shiny","category":"travel"},
    {"emoji":"🌠","name":"shooting star","keywords":"shooting star,sao băng,shooting star,night","category":"travel"},
    {"emoji":"🌌","name":"milky way","keywords":"milky way,dải ngân hà,milky way,space","category":"travel"},
    {"emoji":"☁️","name":"cloud","keywords":"cloud,mây,cloud,weather","category":"travel"},
    {"emoji":"⛅","name":"sun behind cloud","keywords":"sun behind cloud,mặt trời sau mây,sun cloud,weather","category":"travel"},
    {"emoji":"⛈️","name":"cloud with lightning and rain","keywords":"cloud with lightning and rain,mây có sấm sét và mưa,storm,weather","category":"travel"},
    {"emoji":"🌤️","name":"sun behind small cloud","keywords":"sun behind small cloud,mặt trời sau mây nhỏ,sun cloud,weather","category":"travel"},
    {"emoji":"🌥️","name":"sun behind large cloud","keywords":"sun behind large cloud,mặt trời sau mây lớn,sun cloud,weather","category":"travel"},
    {"emoji":"🌦️","name":"sun behind rain cloud","keywords":"sun behind rain cloud,mặt trời sau mây mưa,sun rain,weather","category":"travel"},
    {"emoji":"🌧️","name":"cloud with rain","keywords":"cloud with rain,mây mưa,rain cloud,weather","category":"travel"},
    {"emoji":"🌨️","name":"cloud with snow","keywords":"cloud with snow,mây tuyết,snow cloud,weather","category":"travel"},
    {"emoji":"🌩️","name":"cloud with lightning","keywords":"cloud with lightning,mây sấm sét,lightning cloud,weather","category":"travel"},
    {"emoji":"🌪️","name":"tornado","keywords":"tornado,vòi rồng,tornado,storm","category":"travel"},
    {"emoji":"🌫️","name":"fog","keywords":"fog,sương mù,fog,weather","category":"travel"},
    {"emoji":"🌬️","name":"wind face","keywords":"wind face,mặt gió,wind face,weather","category":"travel"},
    {"emoji":"🌀","name":"cyclone","keywords":"cyclone,xoáy nước,cyclone,storm","category":"travel"},
    {"emoji":"🌈","name":"rainbow","keywords":"rainbow,cầu vồng,rainbow,weather","category":"travel"},
    {"emoji":"🌂","name":"closed umbrella","keywords":"closed umbrella,ô đóng,umbrella,rain","category":"travel"},
    {"emoji":"☂️","name":"umbrella","keywords":"umbrella,ô,umbrella,rain","category":"travel"},
    {"emoji":"☔","name":"umbrella with rain drops","keywords":"umbrella with rain drops,ô với giọt mưa,umbrella rain,weather","category":"travel"},
    {"emoji":"⛱️","name":"umbrella on ground","keywords":"umbrella on ground,ô trên mặt đất,beach umbrella,sun","category":"travel"},
    {"emoji":"⚡","name":"high voltage","keywords":"high voltage,điện cao thế,high voltage,electricity","category":"travel"},
    {"emoji":"❄️","name":"snowflake","keywords":"snowflake,bông tuyết,snowflake,winter","category":"travel"},
    {"emoji":"☃️","name":"snowman","keywords":"snowman,người tuyết,snowman,winter","category":"travel"},
    {"emoji":"⛄","name":"snowman without snow","keywords":"snowman without snow,người tuyết không tuyết,snowman,winter","category":"travel"},
    {"emoji":"☄️","name":"comet","keywords":"comet,sao chổi,comet,space","category":"travel"},
    {"emoji":"🔥","name":"fire","keywords":"fire,lửa,fire,hot","category":"travel"},
    {"emoji":"💧","name":"droplet","keywords":"droplet,giọt nước,droplet,water","category":"travel"},
    {"emoji":"🌊","name":"water wave","keywords":"water wave,sóng nước,wave,sea","category":"travel"},
    
    // ========== ĐỒ VẬT (100+) ==========
    {"emoji":"🏠","name":"house","keywords":"house,nhà,house,building","category":"objects"},
    {"emoji":"🏡","name":"house with garden","keywords":"house with garden,nhà có vườn,house garden,home","category":"objects"},
    {"emoji":"🏢","name":"office building","keywords":"office building,tòa nhà văn phòng,office building,work","category":"objects"},
    {"emoji":"🏣","name":"Japanese post office","keywords":"Japanese post office,bưu điện Nhật Bản,post office,japanese","category":"objects"},
    {"emoji":"🏤","name":"post office","keywords":"post office,bưu điện,post office,mail","category":"objects"},
    {"emoji":"🏥","name":"hospital","keywords":"hospital,bệnh viện,hospital,health","category":"objects"},
    {"emoji":"🏦","name":"bank","keywords":"bank,ngân hàng,bank,money","category":"objects"},
    {"emoji":"🏨","name":"hotel","keywords":"hotel,khách sạn,hotel,accommodation","category":"objects"},
    {"emoji":"🏩","name":"love hotel","keywords":"love hotel,khách sạn tình yêu,love hotel,japanese","category":"objects"},
    {"emoji":"🏪","name":"convenience store","keywords":"convenience store,cửa hàng tiện lợi,convenience store,shop","category":"objects"},
    {"emoji":"🏫","name":"school","keywords":"school,trường học,school,education","category":"objects"},
    {"emoji":"🏬","name":"department store","keywords":"department store,cửa hàng bách hóa,department store,shop","category":"objects"},
    {"emoji":"🏭","name":"factory","keywords":"factory,nhà máy,factory,industrial","category":"objects"},
    {"emoji":"🏯","name":"Japanese castle","keywords":"Japanese castle,lâu đài Nhật Bản,japanese castle,history","category":"objects"},
    {"emoji":"🏰","name":"castle","keywords":"castle,lâu đài,castle,history","category":"objects"},
    {"emoji":"💒","name":"wedding","keywords":"wedding,đám cưới,wedding,marriage","category":"objects"},
    {"emoji":"🗼","name":"Tokyo tower","keywords":"Tokyo tower,tháp Tokyo,tokyo tower,japan","category":"objects"},
    {"emoji":"🗽","name":"Statue of Liberty","keywords":"Statue of Liberty,tượng Nữ thần Tự do,statue of liberty,new york","category":"objects"},
    {"emoji":"⛪","name":"church","keywords":"church,nhà thờ,church,religion","category":"objects"},
    {"emoji":"🕌","name":"mosque","keywords":"mosque,nhà thờ Hồi giáo,mosque,islam","category":"objects"},
    {"emoji":"🛕","name":"hindu temple","keywords":"hindu temple,đền Hindu,hindu temple,india","category":"objects"},
    {"emoji":"🕍","name":"synagogue","keywords":"synagogue,giáo đường Do Thái,synagogue,jewish","category":"objects"},
    {"emoji":"⛩️","name":"shinto shrine","keywords":"shinto shrine,đền thờ Thần đạo,shinto shrine,japan","category":"objects"},
    {"emoji":"🕋","name":"kaaba","keywords":"kaaba,đền Kaaba,kaaba,islam","category":"objects"},
    {"emoji":"⛲","name":"fountain","keywords":"fountain,đài phun nước,fountain,water","category":"objects"},
    {"emoji":"⛺","name":"tent","keywords":"tent,lều,tent,camping","category":"objects"},
    {"emoji":"🌁","name":"foggy","keywords":"foggy,sương mù,foggy,weather","category":"objects"},
    {"emoji":"🌃","name":"night with stars","keywords":"night with stars,đêm với sao,night stars,sky","category":"objects"},
    {"emoji":"🏙️","name":"cityscape","keywords":"cityscape,phong cảnh thành phố,cityscape,urban","category":"objects"},
    {"emoji":"🌄","name":"sunrise over mountains","keywords":"sunrise over mountains,bình minh trên núi,sunrise mountains,morning","category":"objects"},
    {"emoji":"🌅","name":"sunrise","keywords":"sunrise,bình minh,sunrise,morning","category":"objects"},
    {"emoji":"🌆","name":"cityscape at dusk","keywords":"cityscape at dusk,thành phố lúc hoàng hôn,city dusk,evening","category":"objects"},
    {"emoji":"🌇","name":"sunset","keywords":"sunset,hoàng hôn,sunset,evening","category":"objects"},
    {"emoji":"🌉","name":"bridge at night","keywords":"bridge at night,cầu vào ban đêm,bridge night,city","category":"objects"},
    {"emoji":"♨️","name":"hot springs","keywords":"hot springs,suối nước nóng,hot springs,japan","category":"objects"},
    {"emoji":"🎠","name":"carousel horse","keywords":"carousel horse,ngựa carousel,carousel horse,amusement","category":"objects"},
    {"emoji":"🎡","name":"ferris wheel","keywords":"ferris wheel,vòng đu quay,ferris wheel,amusement","category":"objects"},
    {"emoji":"🎢","name":"roller coaster","keywords":"roller coaster,tàu lượn siêu tốc,roller coaster,amusement","category":"objects"},
    {"emoji":"💈","name":"barber pole","keywords":"barber pole,cột hiệu cắt tóc,barber pole,haircut","category":"objects"},
    {"emoji":"🎪","name":"circus tent","keywords":"circus tent,lều xiếc,circus tent,entertainment","category":"objects"},
    {"emoji":"🚂","name":"locomotive","keywords":"locomotive,đầu máy xe lửa,locomotive,train","category":"objects"},
    {"emoji":"🚃","name":"railway car","keywords":"railway car,toa xe lửa,railway car,train","category":"objects"},
    {"emoji":"🚄","name":"high-speed train","keywords":"high-speed train,tàu cao tốc,high speed train,train","category":"objects"},
    {"emoji":"🚅","name":"bullet train","keywords":"bullet train,tàu bullet,bullet train,train","category":"objects"},
    {"emoji":"🚆","name":"train","keywords":"train,tàu hỏa,train,transport","category":"objects"},
    {"emoji":"🚇","name":"metro","keywords":"metro,tàu điện ngầm,metro,subway","category":"objects"},
    {"emoji":"🚈","name":"light rail","keywords":"light rail,tàu điện nhẹ,light rail,train","category":"objects"},
    {"emoji":"🚉","name":"station","keywords":"station,nhà ga,station,train","category":"objects"},
    {"emoji":"🚊","name":"tram","keywords":"tram,tàu điện,tram,transport","category":"objects"},
    {"emoji":"🚝","name":"monorail","keywords":"monorail,tàu một ray,monorail,train","category":"objects"},
    {"emoji":"🚞","name":"mountain railway","keywords":"mountain railway,đường sắt núi,mountain railway,train","category":"objects"},
    {"emoji":"🚋","name":"tram car","keywords":"tram car,toa tàu điện,tram car,transport","category":"objects"},
    {"emoji":"🚌","name":"bus","keywords":"bus,xe buýt,bus,transport","category":"objects"},
    {"emoji":"🚍","name":"oncoming bus","keywords":"oncoming bus,xe buýt tới,oncoming bus,transport","category":"objects"},
    {"emoji":"🚎","name":"trolleybus","keywords":"trolleybus,xe buýt điện,trolleybus,transport","category":"objects"},
    {"emoji":"🚐","name":"minibus","keywords":"minibus,xe buýt nhỏ,minibus,transport","category":"objects"},
    {"emoji":"🚑","name":"ambulance","keywords":"ambulance,xe cứu thương,ambulance,emergency","category":"objects"},
    {"emoji":"🚒","name":"fire engine","keywords":"fire engine,xe cứu hỏa,fire engine,emergency","category":"objects"},
    {"emoji":"🚓","name":"police car","keywords":"police car,xe cảnh sát,police car,emergency","category":"objects"},
    {"emoji":"🚔","name":"oncoming police car","keywords":"oncoming police car,xe cảnh sát tới,oncoming police car,emergency","category":"objects"},
    {"emoji":"🚕","name":"taxi","keywords":"taxi,taxi,taxi,transport","category":"objects"},
    {"emoji":"🚖","name":"oncoming taxi","keywords":"oncoming taxi,taxi tới,oncoming taxi,transport","category":"objects"},
    {"emoji":"🚗","name":"automobile","keywords":"automobile,ô tô,car,transport","category":"objects"},
    {"emoji":"🚘","name":"oncoming automobile","keywords":"oncoming automobile,ô tô tới,oncoming car,transport","category":"objects"},
    {"emoji":"🚙","name":"sport utility vehicle","keywords":"sport utility vehicle,xe SUV,suv,transport","category":"objects"},
    {"emoji":"🛻","name":"pickup truck","keywords":"pickup truck,xe bán tải,pickup truck,vehicle","category":"objects"},
    {"emoji":"🚚","name":"delivery truck","keywords":"delivery truck,xe tải giao hàng,delivery truck,transport","category":"objects"},
    {"emoji":"🚛","name":"articulated lorry","keywords":"articulated lorry,xe tải đầu kéo,articulated lorry,transport","category":"objects"},
    {"emoji":"🚜","name":"tractor","keywords":"tractor,máy kéo,tractor,farming","category":"objects"},
    {"emoji":"🏎️","name":"racing car","keywords":"racing car,xe đua,racing car,sport","category":"objects"},
    {"emoji":"🏍️","name":"motorcycle","keywords":"motorcycle,xe máy,motorcycle,transport","category":"objects"},
    {"emoji":"🛵","name":"motor scooter","keywords":"motor scooter,xe tay ga,scooter,transport","category":"objects"},
    {"emoji":"🦽","name":"manual wheelchair","keywords":"manual wheelchair,xe lăn tay,manual wheelchair,disability","category":"objects"},
    {"emoji":"🦼","name":"motorized wheelchair","keywords":"motorized wheelchair,xe lăn điện,motorized wheelchair,disability","category":"objects"},
    {"emoji":"🛺","name":"auto rickshaw","keywords":"auto rickshaw,xe lam,auto rickshaw,transport","category":"objects"},
    {"emoji":"🚲","name":"bicycle","keywords":"bicycle,xe đạp,bicycle,transport","category":"objects"},
    {"emoji":"🛴","name":"kick scooter","keywords":"kick scooter,xe trượt scooter,kick scooter,transport","category":"objects"},





        // ========== HOẠT ĐỘNG (tiếp) ==========
    {"emoji":"🛹","name":"skateboard","keywords":"skateboard,ván trượt,skateboard,sport","category":"activities"},
    {"emoji":"🛼","name":"roller skate","keywords":"roller skate,giày trượt patin,roller skate,sport","category":"activities"},
    {"emoji":"🎯","name":"bullseye","keywords":"bullseye,trúng hồng tâm,bullseye,target","category":"activities"},
    {"emoji":"🎱","name":"pool 8 ball","keywords":"pool 8 ball,bi-a số 8,pool 8 ball,game","category":"activities"},
    {"emoji":"🎳","name":"bowling","keywords":"bowling,bowling,bowling,game","category":"activities"},
    {"emoji":"🎪","name":"circus tent","keywords":"circus tent,lều xiếc,circus tent,entertainment","category":"activities"},
    {"emoji":"🎬","name":"clapper board","keywords":"clapper board,bảng clapper,clapper board,movie","category":"activities"},
    {"emoji":"🎭","name":"performing arts","keywords":"performing arts,nghệ thuật biểu diễn,performing arts,theater","category":"activities"},
    {"emoji":"🎨","name":"artist palette","keywords":"artist palette,bảng màu họa sĩ,artist palette,art","category":"activities"},
    {"emoji":"🎰","name":"slot machine","keywords":"slot machine,máy đánh bạc,slot machine,casino","category":"activities"},
    {"emoji":"🎮","name":"video game","keywords":"video game,máy chơi game,video game,game","category":"activities"},
    {"emoji":"🎲","name":"game die","keywords":"game die,xúc xắc,game die,dice","category":"activities"},
    {"emoji":"🎴","name":"flower playing cards","keywords":"flower playing cards,bài hoa,flower playing cards,game","category":"activities"},
    {"emoji":"🎵","name":"musical note","keywords":"musical note,nốt nhạc,musical note,music","category":"activities"},
    {"emoji":"🎶","name":"musical notes","keywords":"musical notes,các nốt nhạc,musical notes,music","category":"activities"},
    {"emoji":"🎷","name":"saxophone","keywords":"saxophone,kèn saxophone,saxophone,instrument","category":"activities"},
    {"emoji":"🎸","name":"guitar","keywords":"guitar,đàn guitar,guitar,instrument","category":"activities"},
    {"emoji":"🎹","name":"musical keyboard","keywords":"musical keyboard,đàn keyboard,musical keyboard,instrument","category":"activities"},
    {"emoji":"🎺","name":"trumpet","keywords":"trumpet,kèn trumpet,trumpet,instrument","category":"activities"},
    {"emoji":"🎻","name":"violin","keywords":"violin,đàn violin,violin,instrument","category":"activities"},
    {"emoji":"🥁","name":"drum","keywords":"drum,trống,drum,instrument","category":"activities"},
    {"emoji":"🎤","name":"microphone","keywords":"microphone,microphone,microphone,music","category":"activities"},
    {"emoji":"🎧","name":"headphone","keywords":"headphone,tai nghe,headphone,music","category":"activities"},
    {"emoji":"🎼","name":"musical score","keywords":"musical score,bản nhạc,musical score,music","category":"activities"},
    {"emoji":"🎙️","name":"studio microphone","keywords":"studio microphone,micro phòng thu,studio microphone,music","category":"activities"},
    {"emoji":"🎚️","name":"level slider","keywords":"level slider,thanh trượt mức,level slider,audio","category":"activities"},
    {"emoji":"🎛️","name":"control knobs","keywords":"control knobs,nút điều khiển,control knobs,audio","category":"activities"},
    {"emoji":"📻","name":"radio","keywords":"radio,radio,radio,music","category":"activities"},

    // ========== ĐỒ VẬT (tiếp) ==========
    {"emoji":"💡","name":"light bulb","keywords":"light bulb,bóng đèn,light bulb,idea","category":"objects"},
    {"emoji":"🔦","name":"flashlight","keywords":"flashlight,đèn pin,flashlight,light","category":"objects"},
    {"emoji":"🏮","name":"red paper lantern","keywords":"red paper lantern,đèn lồng đỏ,red lantern,japanese","category":"objects"},
    {"emoji":"🪔","name":"diya lamp","keywords":"diya lamp,đèn diya,diya lamp,indian","category":"objects"},
    {"emoji":"📔","name":"notebook with decorative cover","keywords":"notebook with decorative cover,sổ tay bìa trang trí,notebook,book","category":"objects"},
    {"emoji":"📕","name":"closed book","keywords":"closed book,sách đóng,closed book,book","category":"objects"},
    {"emoji":"📖","name":"open book","keywords":"open book,sách mở,open book,book","category":"objects"},
    {"emoji":"📗","name":"green book","keywords":"green book,sách xanh,green book,book","category":"objects"},
    {"emoji":"📘","name":"blue book","keywords":"blue book,sách xanh dương,blue book,book","category":"objects"},
    {"emoji":"📙","name":"orange book","keywords":"orange book,sách cam,orange book,book","category":"objects"},
    {"emoji":"📚","name":"books","keywords":"books,nhiều sách,books,library","category":"objects"},
    {"emoji":"📓","name":"notebook","keywords":"notebook,sổ tay,notebook,book","category":"objects"},
    {"emoji":"📒","name":"ledger","keywords":"ledger,sổ cái,ledger,book","category":"objects"},
    {"emoji":"📃","name":"page with curl","keywords":"page with curl,trang cuộn góc,page curl,paper","category":"objects"},
    {"emoji":"📜","name":"scroll","keywords":"scroll,cuộn giấy,scroll,document","category":"objects"},
    {"emoji":"📄","name":"page facing up","keywords":"page facing up,trang hướng lên,page,paper","category":"objects"},
    {"emoji":"📰","name":"newspaper","keywords":"newspaper,báo,newspaper,news","category":"objects"},
    {"emoji":"🗞️","name":"rolled-up newspaper","keywords":"rolled-up newspaper,báo cuộn,rolled newspaper,news","category":"objects"},
    {"emoji":"📑","name":"bookmark tabs","keywords":"bookmark tabs,các tab đánh dấu,bookmark tabs,book","category":"objects"},
    {"emoji":"🔖","name":"bookmark","keywords":"bookmark,đánh dấu trang,bookmark,book","category":"objects"},
    {"emoji":"🏷️","name":"label","keywords":"label,nhãn,label,tag","category":"objects"},
    {"emoji":"💰","name":"money bag","keywords":"money bag,túi tiền,money bag,money","category":"objects"},
    {"emoji":"💴","name":"yen banknote","keywords":"yen banknote,tiền yen,yen banknote,money","category":"objects"},
    {"emoji":"💵","name":"dollar banknote","keywords":"dollar banknote,tiền đô la,dollar banknote,money","category":"objects"},
    {"emoji":"💶","name":"euro banknote","keywords":"euro banknote,tiền euro,euro banknote,money","category":"objects"},
    {"emoji":"💷","name":"pound banknote","keywords":"pound banknote,tiền bảng,pound banknote,money","category":"objects"},
    {"emoji":"💸","name":"money with wings","keywords":"money with wings,tiền có cánh,money with wings,money","category":"objects"},
    {"emoji":"💳","name":"credit card","keywords":"credit card,thẻ tín dụng,credit card,money","category":"objects"},
    {"emoji":"🧾","name":"receipt","keywords":"receipt,hóa đơn,receipt,money","category":"objects"},
    {"emoji":"💹","name":"chart increasing with yen","keywords":"chart increasing with yen,biểu đồ tăng với yen,chart increasing,money","category":"objects"},
    {"emoji":"💱","name":"currency exchange","keywords":"currency exchange,trao đổi tiền tệ,currency exchange,money","category":"objects"},
    {"emoji":"💲","name":"heavy dollar sign","keywords":"heavy dollar sign,dấu đô la đậm,heavy dollar sign,money","category":"objects"},
    {"emoji":"📧","name":"e-mail","keywords":"e-mail,email,e-mail,communication","category":"objects"},
    {"emoji":"📨","name":"incoming envelope","keywords":"incoming envelope,thư đến,incoming envelope,mail","category":"objects"},
    {"emoji":"📩","name":"envelope with arrow","keywords":"envelope with arrow,thư có mũi tên,envelope with arrow,mail","category":"objects"},
    {"emoji":"📤","name":"outbox tray","keywords":"outbox tray,khay thư đi,outbox tray,mail","category":"objects"},
    {"emoji":"📥","name":"inbox tray","keywords":"inbox tray,khay thư đến,inbox tray,mail","category":"objects"},
    {"emoji":"📦","name":"package","keywords":"package,gói hàng,package,mail","category":"objects"},
    {"emoji":"📫","name":"closed mailbox with raised flag","keywords":"closed mailbox with raised flag,hộp thư đóng cờ giương,closed mailbox,mail","category":"objects"},
    {"emoji":"📪","name":"closed mailbox with lowered flag","keywords":"closed mailbox with lowered flag,hộp thư đóng cờ hạ,closed mailbox,mail","category":"objects"},
    {"emoji":"📬","name":"open mailbox with raised flag","keywords":"open mailbox with raised flag,hộp thư mở cờ giương,open mailbox,mail","category":"objects"},
    {"emoji":"📭","name":"open mailbox with lowered flag","keywords":"open mailbox with lowered flag,hộp thư mở cờ hạ,open mailbox,mail","category":"objects"},
    {"emoji":"📮","name":"postbox","keywords":"postbox,thùng thư,postbox,mail","category":"objects"},
    {"emoji":"🗳️","name":"ballot box with ballot","keywords":"ballot box with ballot,hòm phiếu,ballot box,vote","category":"objects"},
    {"emoji":"✏️","name":"pencil","keywords":"pencil,bút chì,pencil,write","category":"objects"},
    {"emoji":"✒️","name":"black nib","keywords":"black nib,ngòi bút đen,black nib,write","category":"objects"},
    {"emoji":"🖋️","name":"fountain pen","keywords":"fountain pen,bút máy,fountain pen,write","category":"objects"},
    {"emoji":"🖊️","name":"pen","keywords":"pen,bút,pen,write","category":"objects"},
    {"emoji":"🖌️","name":"paintbrush","keywords":"paintbrush,cọ vẽ,paintbrush,art","category":"objects"},
    {"emoji":"🖍️","name":"crayon","keywords":"crayon,bút màu sáp,crayon,art","category":"objects"},
    {"emoji":"📝","name":"memo","keywords":"memo,ghi chú,memo,write","category":"objects"},
    {"emoji":"📁","name":"file folder","keywords":"file folder,thư mục,file folder,office","category":"objects"},
    {"emoji":"📂","name":"open file folder","keywords":"open file folder,thư mục mở,open file folder,office","category":"objects"},
    {"emoji":"🗂️","name":"card index dividers","keywords":"card index dividers,ngăn chỉ mục thẻ,card index dividers,office","category":"objects"},
    {"emoji":"📅","name":"calendar","keywords":"calendar,lịch,calendar,date","category":"objects"},
    {"emoji":"📆","name":"tear-off calendar","keywords":"tear-off calendar,lịch xé ngày,tear-off calendar,date","category":"objects"},
    {"emoji":"🗒️","name":"spiral notepad","keywords":"spiral notepad,pad xoắn ốc,spiral notepad,note","category":"objects"},
    {"emoji":"🗓️","name":"spiral calendar","keywords":"spiral calendar,lịch xoắn ốc,spiral calendar,date","category":"objects"},
    {"emoji":"📇","name":"card index","keywords":"card index,chỉ mục thẻ,card index,office","category":"objects"},
    {"emoji":"📈","name":"chart increasing","keywords":"chart increasing,biểu đồ tăng,chart increasing,graph","category":"objects"},
    {"emoji":"📉","name":"chart decreasing","keywords":"chart decreasing,biểu đồ giảm,chart decreasing,graph","category":"objects"},
    {"emoji":"📊","name":"bar chart","keywords":"bar chart,biểu đồ cột,bar chart,graph","category":"objects"},
    {"emoji":"📋","name":"clipboard","keywords":"clipboard,clipboard,clipboard,office","category":"objects"},
    {"emoji":"📌","name":"pushpin","keywords":"pushpin,đinh ghim,pushpin,office","category":"objects"},
    {"emoji":"📍","name":"round pushpin","keywords":"round pushpin,đinh ghim tròn,round pushpin,office","category":"objects"},
    {"emoji":"📎","name":"paperclip","keywords":"paperclip,kẹp giấy,paperclip,office","category":"objects"},
    {"emoji":"🖇️","name":"linked paperclips","keywords":"linked paperclips,kẹp giấy liên kết,linked paperclips,office","category":"objects"},
    {"emoji":"📏","name":"straight ruler","keywords":"straight ruler,thước thẳng,straight ruler,measure","category":"objects"},
    {"emoji":"📐","name":"triangular ruler","keywords":"triangular ruler,thước tam giác,triangular ruler,measure","category":"objects"},
    {"emoji":"✂️","name":"scissors","keywords":"scissors,cái kéo,scissors,cut","category":"objects"},
    {"emoji":"🗃️","name":"card file box","keywords":"card file box,hộp thẻ,card file box,office","category":"objects"},
    {"emoji":"🗄️","name":"file cabinet","keywords":"file cabinet,tủ hồ sơ,file cabinet,office","category":"objects"},
    {"emoji":"🗑️","name":"wastebasket","keywords":"wastebasket,thùng rác,wastebasket,trash","category":"objects"},
    {"emoji":"🔒","name":"locked","keywords":"locked,khóa,locked,security","category":"objects"},
    {"emoji":"🔓","name":"unlocked","keywords":"unlocked,mở khóa,unlocked,security","category":"objects"},
    {"emoji":"🔏","name":"locked with pen","keywords":"locked with pen,khóa với bút,locked with pen,security","category":"objects"},
    {"emoji":"🔐","name":"locked with key","keywords":"locked with key,khóa với chìa khóa,locked with key,security","category":"objects"},
    {"emoji":"🔑","name":"key","keywords":"key,chìa khóa,key,security","category":"objects"},
    {"emoji":"🗝️","name":"old key","keywords":"old key,chìa khóa cũ,old key,security","category":"objects"},
    {"emoji":"🔨","name":"hammer","keywords":"hammer,cái búa,hammer,tool","category":"objects"},
    {"emoji":"🪓","name":"axe","keywords":"axe,cái rìu,axe,tool","category":"objects"},
    {"emoji":"⛏️","name":"pick","keywords":"pick,cái cuốc chim,pick,tool","category":"objects"},
    {"emoji":"⚒️","name":"hammer and pick","keywords":"hammer and pick,búa và cuốc,hammer and pick,tool","category":"objects"},
    {"emoji":"🛠️","name":"hammer and wrench","keywords":"hammer and wrench,búa và cờ lê,hammer and wrench,tool","category":"objects"},
    {"emoji":"🗡️","name":"dagger","keywords":"dagger,dao găm,dagger,weapon","category":"objects"},
    {"emoji":"⚔️","name":"crossed swords","keywords":"crossed swords,kiếm chéo,crossed swords,weapon","category":"objects"},
    {"emoji":"🔫","name":"water pistol","keywords":"water pistol,súng nước,water pistol,toy","category":"objects"},
    {"emoji":"🛡️","name":"shield","keywords":"shield,khiên,shield,protection","category":"objects"},
    {"emoji":"🔧","name":"wrench","keywords":"wrench,cờ lê,wrench,tool","category":"objects"},
    {"emoji":"🔩","name":"nut and bolt","keywords":"nut and bolt,đai ốc và bu lông,nut and bolt,tool","category":"objects"},
    {"emoji":"⚙️","name":"gear","keywords":"gear,bánh răng,gear,tool","category":"objects"},
    {"emoji":"🗜️","name":"clamp","keywords":"clamp,cái kẹp,clamp,tool","category":"objects"},
    {"emoji":"⚖️","name":"balance scale","keywords":"balance scale,cân thăng bằng,balance scale,justice","category":"objects"},
    {"emoji":"🔗","name":"link","keywords":"link,liên kết,link,chain","category":"objects"},
    {"emoji":"⛓️","name":"chains","keywords":"chains,dây xích,chains,chain","category":"objects"},
    {"emoji":"🧰","name":"toolbox","keywords":"toolbox,hộp dụng cụ,toolbox,tool","category":"objects"},
    {"emoji":"🧲","name":"magnet","keywords":"magnet,nam châm,magnet,science","category":"objects"},
    {"emoji":"⚗️","name":"alembic","keywords":"alembic,bình chưng cất,alembic,science","category":"objects"},
    {"emoji":"🧪","name":"test tube","keywords":"test tube,ống nghiệm,test tube,science","category":"objects"},
    {"emoji":"🧫","name":"petri dish","keywords":"petri dish,đĩa petri,petri dish,science","category":"objects"},
    {"emoji":"🧬","name":"dna","keywords":"dna,DNA,dna,science","category":"objects"},
    {"emoji":"🔬","name":"microscope","keywords":"microscope,kính hiển vi,microscope,science","category":"objects"},
    {"emoji":"🔭","name":"telescope","keywords":"telescope,kính thiên văn,telescope,science","category":"objects"},
    {"emoji":"📡","name":"satellite antenna","keywords":"satellite antenna,anten vệ tinh,satellite antenna,communication","category":"objects"},
    {"emoji":"💉","name":"syringe","keywords":"syringe,ống tiêm,syringe,health","category":"objects"},
    {"emoji":"🩸","name":"drop of blood","keywords":"drop of blood,giọt máu,drop of blood,health","category":"objects"},
    {"emoji":"💊","name":"pill","keywords":"pill,viên thuốc,pill,health","category":"objects"},
    {"emoji":"🩹","name":"adhesive bandage","keywords":"adhesive bandage,băng dán,adhesive bandage,health","category":"objects"},
    {"emoji":"🩺","name":"stethoscope","keywords":"stethoscope,ống nghe,stethoscope,health","category":"objects"},
    {"emoji":"🚪","name":"door","keywords":"door,cửa ra vào,door,house","category":"objects"},
    {"emoji":"🛗","name":"elevator","keywords":"elevator,thang máy,elevator,building","category":"objects"},
    {"emoji":"🪞","name":"mirror","keywords":"mirror,gương,mirror,reflection","category":"objects"},
    {"emoji":"🪟","name":"window","keywords":"window,cửa sổ,window,house","category":"objects"},
    {"emoji":"🛏️","name":"bed","keywords":"bed,giường,bed,sleep","category":"objects"},
    {"emoji":"🛋️","name":"couch and lamp","keywords":"couch and lamp,ghế sofa và đèn,couch and lamp,furniture","category":"objects"},
    {"emoji":"🪑","name":"chair","keywords":"chair,ghế,chair,furniture","category":"objects"},
    {"emoji":"🚽","name":"toilet","keywords":"toilet,bồn cầu,toilet,bathroom","category":"objects"},
    {"emoji":"🪠","name":"plunger","keywords":"plunger,cái thông tắc,plunger,tool","category":"objects"},
    {"emoji":"🚿","name":"shower","keywords":"shower,vòi sen,shower,bathroom","category":"objects"},
    {"emoji":"🛁","name":"bathtub","keywords":"bathtub,bồn tắm,bathtub,bathroom","category":"objects"},
    {"emoji":"🪤","name":"mouse trap","keywords":"mouse trap,bẫy chuột,mouse trap,trap","category":"objects"},
    {"emoji":"🪒","name":"razor","keywords":"razor,dao cạo,razor,shave","category":"objects"},
    {"emoji":"🧴","name":"lotion bottle","keywords":"lotion bottle,chai kem dưỡng,lotion bottle,cosmetic","category":"objects"},
    {"emoji":"🧷","name":"safety pin","keywords":"safety pin,ghim an toàn,safety pin,clothing","category":"objects"},
    {"emoji":"🧹","name":"broom","keywords":"broom,chổi,broom,clean","category":"objects"},
    {"emoji":"🧺","name":"basket","keywords":"basket,cái giỏ,basket,container","category":"objects"},
    {"emoji":"🧻","name":"roll of paper","keywords":"roll of paper,cuộn giấy,roll of paper,toilet paper","category":"objects"},
    {"emoji":"🧼","name":"soap","keywords":"soap,xà phòng,soap,clean","category":"objects"},
    {"emoji":"🧽","name":"sponge","keywords":"sponge,miếng bọt biển,sponge,clean","category":"objects"},
    {"emoji":"🧯","name":"fire extinguisher","keywords":"fire extinguisher,bình chữa cháy,fire extinguisher,safety","category":"objects"},
    {"emoji":"🛒","name":"shopping cart","keywords":"shopping cart,giỏ hàng,shopping cart,shopping","category":"objects"},
    {"emoji":"🚬","name":"cigarette","keywords":"cigarette,thuốc lá,cigarette,smoking","category":"objects"},
    {"emoji":"⚰️","name":"coffin","keywords":"coffin,quan tài,coffin,death","category":"objects"},
    {"emoji":"🪦","name":"headstone","keywords":"headstone,bia mộ,headstone,death","category":"objects"},
    {"emoji":"⚱️","name":"funeral urn","keywords":"funeral urn,bình đựng tro cốt,funeral urn,death","category":"objects"},
    {"emoji":"🗿","name":"moai","name":"moai,tượng moai,moai,easter island","category":"objects"},
    {"emoji":"🪧","name":"placard","keywords":"placard,bảng biểu ngữ,placard,sign","category":"objects"},
    {"emoji":"🏧","name":"ATM sign","keywords":"ATM sign,biển ATM,ATM sign,money","category":"objects"},
    {"emoji":"🚮","name":"litter in bin sign","keywords":"litter in bin sign,biển vứt rác đúng nơi,litter bin sign,trash","category":"objects"},
    {"emoji":"🚰","name":"potable water","keywords":"potable water,nước uống được,potable water,water","category":"objects"},
    {"emoji":"♿","name":"wheelchair symbol","keywords":"wheelchair symbol,biểu tượng xe lăn,wheelchair symbol,accessibility","category":"objects"},
    {"emoji":"🚹","name":"men's room","keywords":"men's room,phòng nam,men's room,toilet","category":"objects"},
    {"emoji":"🚺","name":"women's room","keywords":"women's room,phòng nữ,women's room,toilet","category":"objects"},
    {"emoji":"🚻","name":"restroom","keywords":"restroom,nhà vệ sinh,restroom,toilet","category":"objects"},
    {"emoji":"🚼","name":"baby symbol","keywords":"baby symbol,biểu tượng em bé,baby symbol,baby","category":"objects"},
    {"emoji":"🚾","name":"water closet","keywords":"water closet,nhà vệ sinh,water closet,toilet","category":"objects"},
    {"emoji":"🛂","name":"passport control","keywords":"passport control,kiểm soát hộ chiếu,passport control,airport","category":"objects"},
    {"emoji":"🛃","name":"customs","keywords":"customs,hải quan,customs,airport","category":"objects"},
    {"emoji":"🛄","name":"baggage claim","keywords":"baggage claim,nhận hành lý,baggage claim,airport","category":"objects"},
    {"emoji":"🛅","name":"left luggage","keywords":"left luggage,gửi hành lý,left luggage,airport","category":"objects"},
    {"emoji":"⚠️","name":"warning","keywords":"warning,cảnh báo,warning,sign","category":"objects"},
    {"emoji":"🚸","name":"children crossing","keywords":"children crossing,trẻ em qua đường,children crossing,sign","category":"objects"},
    {"emoji":"⛔","name":"no entry","keywords":"no entry,cấm vào,no entry,sign","category":"objects"},
    {"emoji":"🚫","name":"prohibited","keywords":"prohibited,cấm,prohibited,sign","category":"objects"},
    {"emoji":"🚳","name":"no bicycles","keywords":"no bicycles,cấm xe đạp,no bicycles,sign","category":"objects"},
    {"emoji":"🚭","name":"no smoking","keywords":"no smoking,cấm hút thuốc,no smoking,sign","category":"objects"},
    {"emoji":"🚯","name":"no littering","keywords":"no littering,cấm xả rác,no littering,sign","category":"objects"},
    {"emoji":"🚱","name":"non-potable water","keywords":"non-potable water,nước không uống được,non-potable water,sign","category":"objects"},
    {"emoji":"🚷","name":"no pedestrians","keywords":"no pedestrians,cấm người đi bộ,no pedestrians,sign","category":"objects"},
    {"emoji":"📵","name":"no mobile phones","keywords":"no mobile phones,cấm điện thoại di động,no mobile phones,sign","category":"objects"},
    {"emoji":"🔞","name":"no one under eighteen","keywords":"no one under eighteen,cấm người dưới 18,no one under 18,sign","category":"objects"},
    {"emoji":"☢️","name":"radioactive","keywords":"radioactive,phóng xạ,radioactive,sign","category":"objects"},
    {"emoji":"☣️","name":"biohazard","keywords":"biohazard,nguy hiểm sinh học,biohazard,sign","category":"objects"},
    {"emoji":"⬆️","name":"up arrow","keywords":"up arrow,mũi tên lên,up arrow,direction","category":"objects"},
    {"emoji":"↗️","name":"up-right arrow","keywords":"up-right arrow,mũi tên lên-phải,up-right arrow,direction","category":"objects"},
    {"emoji":"➡️","name":"right arrow","keywords":"right arrow,mũi tên phải,right arrow,direction","category":"objects"},
    {"emoji":"↘️","name":"down-right arrow","keywords":"down-right arrow,mũi tên xuống-phải,down-right arrow,direction","category":"objects"},
    {"emoji":"⬇️","name":"down arrow","keywords":"down arrow,mũi tên xuống,down arrow,direction","category":"objects"},
    {"emoji":"↙️","name":"down-left arrow","keywords":"down-left arrow,mũi tên xuống-trái,down-left arrow,direction","category":"objects"},
    {"emoji":"⬅️","name":"left arrow","keywords":"left arrow,mũi tên trái,left arrow,direction","category":"objects"},
    {"emoji":"↖️","name":"up-left arrow","keywords":"up-left arrow,mũi tên lên-trái,up-left arrow,direction","category":"objects"},
    {"emoji":"↕️","name":"up-down arrow","keywords":"up-down arrow,mũi tên lên-xuống,up-down arrow,direction","category":"objects"},
    {"emoji":"↔️","name":"left-right arrow","keywords":"left-right arrow,mũi tên trái-phải,left-right arrow,direction","category":"objects"},
    {"emoji":"↩️","name":"right arrow curving left","keywords":"right arrow curving left,mũi tên phải cong trái,right arrow curving left,return","category":"objects"},
    {"emoji":"↪️","name":"left arrow curving right","keywords":"left arrow curving right,mũi tên trái cong phải,left arrow curving right,return","category":"objects"},
    {"emoji":"⤴️","name":"right arrow curving up","keywords":"right arrow curving up,mũi tên phải cong lên,right arrow curving up,direction","category":"objects"},
    {"emoji":"⤵️","name":"right arrow curving down","keywords":"right arrow curving down,mũi tên phải cong xuống,right arrow curving down,direction","category":"objects"},
    {"emoji":"🔃","name":"clockwise vertical arrows","keywords":"clockwise vertical arrows,mũi tên dọc theo chiều kim đồng hồ,clockwise arrows,refresh","category":"objects"},
    {"emoji":"🔄","name":"counterclockwise arrows button","keywords":"counterclockwise arrows button,nút mũi tên ngược chiều kim đồng hồ,counterclockwise arrows,refresh","category":"objects"},
    {"emoji":"🔙","name":"BACK arrow","keywords":"BACK arrow,mũi tên BACK,BACK arrow,return","category":"objects"},
    {"emoji":"🔚","name":"END arrow","keywords":"END arrow,mũi tên END,END arrow,end","category":"objects"},
    {"emoji":"🔛","name":"ON! arrow","keywords":"ON! arrow,mũi tên ON!,ON arrow,on","category":"objects"},
    {"emoji":"🔜","name":"SOON arrow","keywords":"SOON arrow,mũi tên SOON,SOON arrow,soon","category":"objects"},
    {"emoji":"🔝","name":"TOP arrow","keywords":"TOP arrow,mũi tên TOP,TOP arrow,top","category":"objects"},
    {"emoji":"🛐","name":"place of worship","keywords":"place of worship,nơi thờ cúng,place of worship,religion","category":"objects"},
    {"emoji":"⚛️","name":"atom symbol","keywords":"atom symbol,biểu tượng nguyên tử,atom symbol,science","category":"objects"},
    {"emoji":"🕉️","name":"om","keywords":"om,chữ om,om,religion","category":"objects"},
    {"emoji":"✡️","name":"star of David","keywords":"star of David,ngôi sao David,star of David,judaism","category":"objects"},
    {"emoji":"☸️","name":"wheel of dharma","keywords":"wheel of dharma,bánh xe dharma,wheel of dharma,buddhism","category":"objects"},
    {"emoji":"☯️","name":"yin yang","keywords":"yin yang,âm dương,yin yang,taoism","category":"objects"},
    {"emoji":"✝️","name":"latin cross","keywords":"latin cross,thập giá Latin,latin cross,christianity","category":"objects"},
    {"emoji":"☦️","name":"orthodox cross","keywords":"orthodox cross,thập giá Chính thống,orthodox cross,christianity","category":"objects"},
    {"emoji":"☪️","name":"star and crescent","keywords":"star and crescent,ngôi sao và trăng lưỡi liềm,star and crescent,islam","category":"objects"},
    {"emoji":"☮️","name":"peace symbol","keywords":"peace symbol,biểu tượng hòa bình,peace symbol,peace","category":"objects"},
    {"emoji":"🕎","name":"menorah","keywords":"menorah,đèn menorah,menorah,judaism","category":"objects"},
    {"emoji":"🔯","name":"dotted six-pointed star","keywords":"dotted six-pointed star,ngôi sao sáu cánh chấm,dotted star,fortune","category":"objects"},
    {"emoji":"♈","name":"Aries","keywords":"Aries,Bạch Dương,Aries,zodiac","category":"objects"},
    {"emoji":"♉","name":"Taurus","keywords":"Taurus,Kim Ngưu,Taurus,zodiac","category":"objects"},
    {"emoji":"♊","name":"Gemini","keywords":"Gemini,Song Tử,Gemini,zodiac","category":"objects"},
    {"emoji":"♋","name":"Cancer","keywords":"Cancer,Cự Giải,Cancer,zodiac","category":"objects"},
    {"emoji":"♌","name":"Leo","keywords":"Leo,Sư Tử,Leo,zodiac","category":"objects"},
    {"emoji":"♍","name":"Virgo","keywords":"Virgo,Xử Nữ,Virgo,zodiac","category":"objects"},
    {"emoji":"♎","name":"Libra","keywords":"Libra,Thiên Bình,Libra,zodiac","category":"objects"},
    {"emoji":"♏","name":"Scorpio","keywords":"Scorpio,Bọ Cạp,Scorpio,zodiac","category":"objects"},
    {"emoji":"♐","name":"Sagittarius","keywords":"Sagittarius,Nhân Mã,Sagittarius,zodiac","category":"objects"},
    {"emoji":"♑","name":"Capricorn","keywords":"Capricorn,Ma Kết,Capricorn,zodiac","category":"objects"},
    {"emoji":"♒","name":"Aquarius","keywords":"Aquarius,Bảo Bình,Aquarius,zodiac","category":"objects"},
    {"emoji":"♓","name":"Pisces","keywords":"Pisces,Song Ngư,Pisces,zodiac","category":"objects"},
    {"emoji":"⛎","name":"Ophiuchus","keywords":"Ophiuchus,Xà Phu,Ophiuchus,zodiac","category":"objects"},
    {"emoji":"🔀","name":"shuffle tracks button","keywords":"shuffle tracks button,nút xáo trộn bài,shuffle button,music","category":"objects"},
    {"emoji":"🔁","name":"repeat button","keywords":"repeat button,nút lặp lại,repeat button,music","category":"objects"},
    {"emoji":"🔂","name":"repeat single button","keywords":"repeat single button,nút lặp lại một bài,repeat single button,music","category":"objects"},
    {"emoji":"▶️","name":"play button","keywords":"play button,nút phát,play button,music","category":"objects"},
    {"emoji":"⏩","name":"fast-forward button","keywords":"fast-forward button,nút tua nhanh,fast-forward button,music","category":"objects"},
    {"emoji":"⏭️","name":"next track button","keywords":"next track button,nút bài tiếp theo,next track button,music","category":"objects"},
    {"emoji":"⏯️","name":"play or pause button","keywords":"play or pause button,nút phát hoặc tạm dừng,play pause button,music","category":"objects"},
    {"emoji":"◀️","name":"reverse button","keywords":"reverse button,nút đảo ngược,reverse button,music","category":"objects"},
    {"emoji":"⏪","name":"fast reverse button","keywords":"fast reverse button,nút tua ngược nhanh,fast reverse button,music","category":"objects"},
    {"emoji":"⏮️","name":"last track button","keywords":"last track button,nút bài trước,last track button,music","category":"objects"},
    {"emoji":"🔼","name":"upwards button","keywords":"upwards button,nút hướng lên,upwards button,direction","category":"objects"},
    {"emoji":"⏫","name":"fast up button","keywords":"fast up button,nút lên nhanh,fast up button,direction","category":"objects"},
    {"emoji":"🔽","name":"downwards button","keywords":"downwards button,nút hướng xuống,downwards button,direction","category":"objects"},
    {"emoji":"⏬","name":"fast down button","keywords":"fast down button,nút xuống nhanh,fast down button,direction","category":"objects"},
    {"emoji":"⏸️","name":"pause button","keywords":"pause button,nút tạm dừng,pause button,music","category":"objects"},
    {"emoji":"⏹️","name":"stop button","keywords":"stop button,nút dừng,stop button,music","category":"objects"},
    {"emoji":"⏺️","name":"record button","keywords":"record button,nút ghi,record button,music","category":"objects"},
    {"emoji":"⏏️","name":"eject button","keywords":"eject button,nút đẩy ra,eject button,music","category":"objects"},
    {"emoji":"🎦","name":"cinema","keywords":"cinema,rạp chiếu phim,cinema,movie","category":"objects"},
    {"emoji":"🔅","name":"dim button","keywords":"dim button,nút giảm sáng,dim button,brightness","category":"objects"},
    {"emoji":"🔆","name":"bright button","keywords":"bright button,nút tăng sáng,bright button,brightness","category":"objects"},
    {"emoji":"📶","name":"antenna bars","keywords":"antenna bars,thanh anten,antenna bars,signal","category":"objects"},
    {"emoji":"📳","name":"vibration mode","keywords":"vibration mode,chế độ rung,vibration mode,mobile","category":"objects"},
    {"emoji":"📴","name":"mobile phone off","keywords":"mobile phone off,tắt điện thoại di động,mobile phone off,mobile","category":"objects"},

    // ========== BIỂU TƯỢNG (150+) ==========
    {"emoji":"❤️","name":"red heart","keywords":"red heart,trái tim đỏ,red heart,love","category":"symbols"},
    {"emoji":"🧡","name":"orange heart","keywords":"orange heart,trái tim cam,orange heart,love","category":"symbols"},
    {"emoji":"💛","name":"yellow heart","keywords":"yellow heart,trái tim vàng,yellow heart,love","category":"symbols"},
    {"emoji":"💚","name":"green heart","keywords":"green heart,trái tim xanh lá,green heart,love","category":"symbols"},
    {"emoji":"💙","name":"blue heart","keywords":"blue heart,trái tim xanh dương,blue heart,love","category":"symbols"},
    {"emoji":"💜","name":"purple heart","keywords":"purple heart,trái tim tím,purple heart,love","category":"symbols"},
    {"emoji":"🖤","name":"black heart","keywords":"black heart,trái tim đen,black heart,dark","category":"symbols"},
    {"emoji":"🤍","name":"white heart","keywords":"white heart,trái tim trắng,white heart,love","category":"symbols"},
    {"emoji":"🤎","name":"brown heart","keywords":"brown heart,trái tim nâu,brown heart,love","category":"symbols"},
    {"emoji":"💔","name":"broken heart","keywords":"broken heart,trái tim tan vỡ,broken heart,sad","category":"symbols"},
    {"emoji":"❤️‍🔥","name":"heart on fire","keywords":"heart on fire,trái tim cháy,heart on fire,passion","category":"symbols"},
    {"emoji":"❤️‍🩹","name":"mending heart","keywords":"mending heart,trái tim hàn gắn,mending heart,heal","category":"symbols"},
    {"emoji":"💕","name":"two hearts","keywords":"two hearts,hai trái tim,two hearts,love","category":"symbols"},
    {"emoji":"💞","name":"revolving hearts","keywords":"revolving hearts,trái tim xoay,revolving hearts,love","category":"symbols"},
    {"emoji":"💓","name":"beating heart","keywords":"beating heart,trái tim đập,beating heart,love","category":"symbols"},
    {"emoji":"💗","name":"growing heart","keywords":"growing heart,trái tim lớn dần,growing heart,love","category":"symbols"},
    {"emoji":"💖","name":"sparkling heart","keywords":"sparkling heart,trái tim lấp lánh,sparkling heart,love","category":"symbols"},
    {"emoji":"💘","name":"heart with arrow","keywords":"heart with arrow,trái tim với mũi tên,heart with arrow,love","category":"symbols"},
    {"emoji":"💝","name":"heart with ribbon","keywords":"heart with ribbon,trái tim với ruy băng,heart with ribbon,love","category":"symbols"},
    {"emoji":"💟","name":"heart decoration","keywords":"heart decoration,trang trí trái tim,heart decoration,love","category":"symbols"},
    {"emoji":"☮️","name":"peace symbol","keywords":"peace symbol,biểu tượng hòa bình,peace symbol,peace","category":"symbols"},
    {"emoji":"✝️","name":"latin cross","keywords":"latin cross,thập giá Latin,latin cross,christian","category":"symbols"},
    {"emoji":"☪️","name":"star and crescent","keywords":"star and crescent,ngôi sao và trăng lưỡi liềm,star and crescent,islam","category":"symbols"},
    {"emoji":"🕉️","name":"om","keywords":"om,chữ om,om,hindu","category":"symbols"},
    {"emoji":"☸️","name":"wheel of dharma","keywords":"wheel of dharma,bánh xe dharma,wheel of dharma,buddhist","category":"symbols"},
    {"emoji":"✡️","name":"star of David","keywords":"star of David,ngôi sao David,star of David,jewish","category":"symbols"},
    {"emoji":"🔯","name":"dotted six-pointed star","keywords":"dotted six-pointed star,ngôi sao sáu cánh chấm,dotted star,fortune","category":"symbols"},
    {"emoji":"🪯","name":"khanda","keywords":"khanda,khanda,sikh","category":"symbols"},
    {"emoji":"♈","name":"Aries","keywords":"Aries,Bạch Dương,Aries,zodiac","category":"symbols"},
    {"emoji":"♉","name":"Taurus","keywords":"Taurus,Kim Ngưu,Taurus,zodiac","category":"symbols"},
    {"emoji":"♊","name":"Gemini","keywords":"Gemini,Song Tử,Gemini,zodiac","category":"symbols"},
    {"emoji":"♋","name":"Cancer","keywords":"Cancer,Cự Giải,Cancer,zodiac","category":"symbols"},
    {"emoji":"♌","name":"Leo","keywords":"Leo,Sư Tử,Leo,zodiac","category":"symbols"},
    {"emoji":"♍","name":"Virgo","keywords":"Virgo,Xử Nữ,Virgo,zodiac","category":"symbols"},
    {"emoji":"♎","name":"Libra","keywords":"Libra,Thiên Bình,Libra,zodiac","category":"symbols"},
    {"emoji":"♏","name":"Scorpio","keywords":"Scorpio,Bọ Cạp,Scorpio,zodiac","category":"symbols"},
    {"emoji":"♐","name":"Sagittarius","keywords":"Sagittarius,Nhân Mã,Sagittarius,zodiac","category":"symbols"},
    {"emoji":"♑","name":"Capricorn","keywords":"Capricorn,Ma Kết,Capricorn,zodiac","category":"symbols"},
    {"emoji":"♒","name":"Aquarius","keywords":"Aquarius,Bảo Bình,Aquarius,zodiac","category":"symbols"},
    {"emoji":"♓","name":"Pisces","keywords":"Pisces,Song Ngư,Pisces,zodiac","category":"symbols"},
    {"emoji":"⛎","name":"Ophiuchus","keywords":"Ophiuchus,Xà Phu,Ophiuchus,zodiac","category":"symbols"},
    {"emoji":"🆔","name":"ID button","keywords":"ID button,nút ID,ID button,identification","category":"symbols"},
    {"emoji":"⚛️","name":"atom symbol","keywords":"atom symbol,biểu tượng nguyên tử,atom symbol,science","category":"symbols"},
    {"emoji":"🉐","name":"Japanese bargain button","keywords":"Japanese bargain button,nút mặc cả Nhật,bargain button,japanese","category":"symbols"},
    {"emoji":"🈹","name":"Japanese discount button","keywords":"Japanese discount button,nút giảm giá Nhật,discount button,japanese","category":"symbols"},
    {"emoji":"🈚","name":"Japanese free of charge button","keywords":"Japanese free of charge button,nút miễn phí Nhật,free button,japanese","category":"symbols"},
    {"emoji":"🈲","name":"Japanese prohibited button","keywords":"Japanese prohibited button,nút cấm Nhật,prohibited button,japanese","category":"symbols"},
    {"emoji":"🉑","name":"Japanese acceptable button","keywords":"Japanese acceptable button,nút chấp nhận Nhật,acceptable button,japanese","category":"symbols"},
    {"emoji":"🈸","name":"Japanese application button","keywords":"Japanese application button,nút đơn đăng ký Nhật,application button,japanese","category":"symbols"},
    {"emoji":"🈴","name":"Japanese passing grade button","keywords":"Japanese passing grade button,nút đậu Nhật,passing grade button,japanese","category":"symbols"},
    {"emoji":"🈵","name":"Japanese no vacancy button","keywords":"Japanese no vacancy button,nút hết chỗ Nhật,no vacancy button,japanese","category":"symbols"},
    {"emoji":"㊗️","name":"Japanese congratulations button","keywords":"Japanese congratulations button,nút chúc mừng Nhật,congratulations button,japanese","category":"symbols"},
    {"emoji":"㊙️","name":"Japanese secret button","keywords":"Japanese secret button,nút bí mật Nhật,secret button,japanese","category":"symbols"},
    {"emoji":"🈺","name":"Japanese open for business button","keywords":"Japanese open for business button,nút mở cửa Nhật,open for business button,japanese","category":"symbols"},
    {"emoji":"🈶","name":"Japanese not free of charge button","keywords":"Japanese not free of charge button,nút có phí Nhật,not free button,japanese","category":"symbols"},
    {"emoji":"📴","name":"mobile phone off","keywords":"mobile phone off,tắt điện thoại di động,mobile phone off,phone","category":"symbols"},
    {"emoji":"📳","name":"vibration mode","keywords":"vibration mode,chế độ rung,vibration mode,phone","category":"symbols"},
    {"emoji":"🈸","name":"Japanese application button","keywords":"Japanese application button,nút đơn đăng ký Nhật,application button,japanese","category":"symbols"},
    {"emoji":"🈂️","name":"Japanese service charge button","keywords":"Japanese service charge button,nút phí dịch vụ Nhật,service charge button,japanese","category":"symbols"},
    {"emoji":"🛂","name":"passport control","keywords":"passport control,kiểm soát hộ chiếu,passport control,airport","category":"symbols"},
    {"emoji":"🛃","name":"customs","keywords":"customs,hải quan,customs,airport","category":"symbols"},
    {"emoji":"🛄","name":"baggage claim","keywords":"baggage claim,nhận hành lý,baggage claim,airport","category":"symbols"},
    {"emoji":"🛅","name":"left luggage","keywords":"left luggage,gửi hành lý,left luggage,airport","category":"symbols"},
    {"emoji":"🚹","name":"men's room","keywords":"men's room,phòng nam,men's room,toilet","category":"symbols"},
    {"emoji":"🚺","name":"women's room","keywords":"women's room,phòng nữ,women's room,toilet","category":"symbols"},
    {"emoji":"🚼","name":"baby symbol","keywords":"baby symbol,biểu tượng em bé,baby symbol,baby","category":"symbols"},
    {"emoji":"🚻","name":"restroom","keywords":"restroom,nhà vệ sinh,restroom,toilet","category":"symbols"},
    {"emoji":"🚮","name":"litter in bin sign","keywords":"litter in bin sign,biển vứt rác đúng nơi,litter bin sign,trash","category":"symbols"},
    {"emoji":"🎦","name":"cinema","keywords":"cinema,rạp chiếu phim,cinema,movie","category":"symbols"},
    {"emoji":"📶","name":"antenna bars","keywords":"antenna bars,thanh anten,antenna bars,signal","category":"symbols"},
    {"emoji":"🈁","name":"Japanese here button","keywords":"Japanese here button,nút ở đây Nhật,here button,japanese","category":"symbols"},
    {"emoji":"🔣","name":"input symbols","keywords":"input symbols,ký tự nhập,input symbols,symbol","category":"symbols"},
    {"emoji":"ℹ️","name":"information","keywords":"information,thông tin,information,info","category":"symbols"},
    {"emoji":"🔤","name":"input latin letters","keywords":"input latin letters,chữ Latin nhập,input latin letters,alphabet","category":"symbols"},
    {"emoji":"🔡","name":"input latin lowercase","keywords":"input latin lowercase,chữ thường Latin nhập,input latin lowercase,alphabet","category":"symbols"},
    {"emoji":"🔠","name":"input latin uppercase","keywords":"input latin uppercase,chữ hoa Latin nhập,input latin uppercase,alphabet","category":"symbols"},
    {"emoji":"🆖","name":"NG button","keywords":"NG button,nút NG,NG button,no good","category":"symbols"},
    {"emoji":"🆗","name":"OK button","keywords":"OK button,nút OK,OK button,okay","category":"symbols"},
    {"emoji":"🆙","name":"UP! button","keywords":"UP! button,nút UP!,UP button,up","category":"symbols"},
    {"emoji":"🆒","name":"COOL button","keywords":"COOL button,nút COOL,COOL button,cool","category":"symbols"},
    {"emoji":"🆕","name":"NEW button","keywords":"NEW button,nút NEW,NEW button,new","category":"symbols"},
    {"emoji":"🆓","name":"FREE button","keywords":"FREE button,nút FREE,FREE button,free","category":"symbols"},
    {"emoji":"0️⃣","name":"keycap 0","keywords":"keycap 0,phím số 0,keycap 0,number","category":"symbols"},
    {"emoji":"1️⃣","name":"keycap 1","keywords":"keycap 1,phím số 1,keycap 1,number","category":"symbols"},
    {"emoji":"2️⃣","name":"keycap 2","keywords":"keycap 2,phím số 2,keycap 2,number","category":"symbols"},
    {"emoji":"3️⃣","name":"keycap 3","keywords":"keycap 3,phím số 3,keycap 3,number","category":"symbols"},
    {"emoji":"4️⃣","name":"keycap 4","keywords":"keycap 4,phím số 4,keycap 4,number","category":"symbols"},
    {"emoji":"5️⃣","name":"keycap 5","keywords":"keycap 5,phím số 5,keycap 5,number","category":"symbols"},
    {"emoji":"6️⃣","name":"keycap 6","keywords":"keycap 6,phím số 6,keycap 6,number","category":"symbols"},
    {"emoji":"7️⃣","name":"keycap 7","keywords":"keycap 7,phím số 7,keycap 7,number","category":"symbols"},
    {"emoji":"8️⃣","name":"keycap 8","keywords":"keycap 8,phím số 8,keycap 8,number","category":"symbols"},
    {"emoji":"9️⃣","name":"keycap 9","keywords":"keycap 9,phím số 9,keycap 9,number","category":"symbols"},
    {"emoji":"🔟","name":"keycap 10","keywords":"keycap 10,phím số 10,keycap 10,number","category":"symbols"},
    {"emoji":"*️⃣","name":"keycap asterisk","keywords":"keycap asterisk,phím dấu sao,keycap asterisk,symbol","category":"symbols"},
    {"emoji":"#️⃣","name":"keycap number sign","keywords":"keycap number sign,phím dấu số,keycap number sign,symbol","category":"symbols"},
    {"emoji":"🔢","name":"input numbers","keywords":"input numbers,số nhập,input numbers,number","category":"symbols"},
    {"emoji":"⏏️","name":"eject button","keywords":"eject button,nút đẩy ra,eject button,music","category":"symbols"},
    {"emoji":"▶️","name":"play button","keywords":"play button,nút phát,play button,music","category":"symbols"},
    {"emoji":"⏸️","name":"pause button","keywords":"pause button,nút tạm dừng,pause button,music","category":"symbols"},
    {"emoji":"⏭️","name":"next track button","keywords":"next track button,nút bài tiếp theo,next track button,music","category":"symbols"},
    {"emoji":"⏹️","name":"stop button","keywords":"stop button,nút dừng,stop button,music","category":"symbols"},
    {"emoji":"⏺️","name":"record button","keywords":"record button,nút ghi,record button,music","category":"symbols"},
    {"emoji":"⏏️","name":"eject button","keywords":"eject button,nút đẩy ra,eject button,music","category":"symbols"},
    {"emoji":"🔀","name":"shuffle tracks button","keywords":"shuffle tracks button,nút xáo trộn bài,shuffle button,music","category":"symbols"},
    {"emoji":"🔁","name":"repeat button","keywords":"repeat button,nút lặp lại,repeat button,music","category":"symbols"},
    {"emoji":"🔂","name":"repeat single button","keywords":"repeat single button,nút lặp lại một bài,repeat single button,music","category":"symbols"},
    {"emoji":"◀️","name":"reverse button","keywords":"reverse button,nút đảo ngược,reverse button,music","category":"symbols"},
    {"emoji":"🔼","name":"upwards button","keywords":"upwards button,nút hướng lên,upwards button,direction","category":"symbols"},
    {"emoji":"🔽","name":"downwards button","keywords":"downwards button,nút hướng xuống,downwards button,direction","category":"symbols"},
    {"emoji":"⏩","name":"fast-forward button","keywords":"fast-forward button,nút tua nhanh,fast-forward button,music","category":"symbols"},
    {"emoji":"⏪","name":"fast reverse button","keywords":"fast reverse button,nút tua ngược nhanh,fast reverse button,music","category":"symbols"},
    {"emoji":"⏫","name":"fast up button","keywords":"fast up button,nút lên nhanh,fast up button,direction","category":"symbols"},
    {"emoji":"⏬","name":"fast down button","keywords":"fast down button,nút xuống nhanh,fast down button,direction","category":"symbols"},
    {"emoji":"🆚","name":"VS button","keywords":"VS button,nút VS,VS button,versus","category":"symbols"},
    {"emoji":"📳","name":"vibration mode","keywords":"vibration mode,chế độ rung,vibration mode,phone","category":"symbols"},
    {"emoji":"📴","name":"mobile phone off","keywords":"mobile phone off,tắt điện thoại di động,mobile phone off,phone","category":"symbols"},
    {"emoji":"🔈","name":"speaker low volume","keywords":"speaker low volume,loa âm lượng thấp,speaker low,volume","category":"symbols"},
    {"emoji":"🔇","name":"muted speaker","keywords":"muted speaker,loa tắt tiếng,muted speaker,volume","category":"symbols"},
    {"emoji":"🔉","name":"speaker medium volume","keywords":"speaker medium volume,loa âm lượng trung bình,speaker medium,volume","category":"symbols"},
    {"emoji":"🔊","name":"speaker high volume","keywords":"speaker high volume,loa âm lượng cao,speaker high,volume","category":"symbols"},
    {"emoji":"🔔","name":"bell","keywords":"bell,chuông,bell,sound","category":"symbols"},
    {"emoji":"🔕","name":"bell with slash","keywords":"bell with slash,chuông gạch chéo,bell with slash,silent","category":"symbols"},
    {"emoji":"📣","name":"megaphone","keywords":"megaphone,cái loa,megaphone,sound","category":"symbols"},
    {"emoji":"📢","name":"loudspeaker","keywords":"loudspeaker,loa phát thanh,loudspeaker,announcement","category":"symbols"},
    {"emoji":"🗨️","name":"left speech bubble","keywords":"left speech bubble,bong bóng thoại trái,left speech bubble,speech","category":"symbols"},
    {"emoji":"💬","name":"speech balloon","keywords":"speech balloon,bong bóng thoại,speech balloon,chat","category":"symbols"},
    {"emoji":"💭","name":"thought balloon","keywords":"thought balloon,bong bóng suy nghĩ,thought balloon,think","category":"symbols"},
    {"emoji":"🃏","name":"joker","keywords":"joker,phăng teo,joker,card","category":"symbols"},
    {"emoji":"🀄","name":"mahjong red dragon","keywords":"mahjong red dragon,con rồng đỏ mahjong,mahjong red dragon,game","category":"symbols"},
    {"emoji":"🎴","name":"flower playing cards","keywords":"flower playing cards,bài hoa,flower playing cards,game","category":"symbols"},
    {"emoji":"♠️","name":"spade suit","keywords":"spade suit,chất bích,spade suit,card","category":"symbols"},
    {"emoji":"♥️","name":"heart suit","keywords":"heart suit,chất cơ,heart suit,card","category":"symbols"},
    {"emoji":"♦️","name":"diamond suit","keywords":"diamond suit,chất rô,diamond suit,card","category":"symbols"},
    {"emoji":"♣️","name":"club suit","keywords":"club suit,chất nhép,club suit,card","category":"symbols"},
    {"emoji":"♟️","name":"chess pawn","keywords":"chess pawn,tốt cờ vua,chess pawn,chess","category":"symbols"},
    {"emoji":"🪀","name":"yo-yo","keywords":"yo-yo,yo-yo,yo-yo,toy","category":"symbols"},
    {"emoji":"🪁","name":"kite","keywords":"kite,diều,kite,toy","category":"symbols"},
    {"emoji":"🎀","name":"ribbon","keywords":"ribbon,ruy băng,ribbon,decoration","category":"symbols"},
    {"emoji":"🎁","name":"wrapped gift","keywords":"wrapped gift,quà được gói,wrapped gift,present","category":"symbols"},
    {"emoji":"🎗️","name":"reminder ribbon","keywords":"reminder ribbon,ruy băng nhắc nhở,reminder ribbon,awareness","category":"symbols"},
    {"emoji":"🎟️","name":"admission tickets","keywords":"admission tickets,vé vào cửa,admission tickets,ticket","category":"symbols"},
    {"emoji":"🎫","name":"ticket","keywords":"ticket,vé,ticket,entrance","category":"symbols"},
    {"emoji":"🎖️","name":"military medal","keywords":"military medal,huy chương quân sự,military medal,award","category":"symbols"},
    {"emoji":"🏆","name":"trophy","keywords":"trophy,cúp,trophy,award","category":"symbols"},
    {"emoji":"🏅","name":"sports medal","keywords":"sports medal,huy chương thể thao,sports medal,award","category":"symbols"},
    {"emoji":"🥇","name":"1st place medal","keywords":"1st place medal,huy chương vàng,1st place medal,award","category":"symbols"},
    {"emoji":"🥈","name":"2nd place medal","keywords":"2nd place medal,huy chương bạc,2nd place medal,award","category":"symbols"},
    {"emoji":"🥉","name":"3rd place medal","keywords":"3rd place medal,huy chương đồng,3rd place medal,award","category":"symbols"},
    {"emoji":"⚽","name":"soccer ball","keywords":"soccer ball,bóng đá,soccer ball,sport","category":"symbols"},
    {"emoji":"⚾","name":"baseball","keywords":"baseball,bóng chày,baseball,sport","category":"symbols"},
    {"emoji":"🥎","name":"softball","keywords":"softball,bóng mềm,softball,sport","category":"symbols"},
    {"emoji":"🏀","name":"basketball","keywords":"basketball,bóng rổ,basketball,sport","category":"symbols"},
    {"emoji":"🏐","name":"volleyball","keywords":"volleyball,bóng chuyền,volleyball,sport","category":"symbols"},
    {"emoji":"🏈","name":"american football","keywords":"american football,bóng bầu dục Mỹ,american football,sport","category":"symbols"},
    {"emoji":"🏉","name":"rugby football","keywords":"rugby football,bóng bầu dục,rugby football,sport","category":"symbols"},
    {"emoji":"🎾","name":"tennis","keywords":"tennis,quần vợt,tennis,sport","category":"symbols"},
    {"emoji":"🥏","name":"flying disc","keywords":"flying disc,đĩa bay,flying disc,sport","category":"symbols"},
    {"emoji":"🎳","name":"bowling","keywords":"bowling,bowling,bowling,game","category":"symbols"},
    {"emoji":"🏏","name":"cricket game","keywords":"cricket game,crickê,cricket game,sport","category":"symbols"},
    {"emoji":"🏑","name":"field hockey","keywords":"field hockey,khúc côn cầu trên cỏ,field hockey,sport","category":"symbols"},
    {"emoji":"🏒","name":"ice hockey","keywords":"ice hockey,khúc côn cầu trên băng,ice hockey,sport","category":"symbols"},
    {"emoji":"🥍","name":"lacrosse","keywords":"lacrosse,lacrosse,lacrosse,sport","category":"symbols"},
    {"emoji":"🏓","name":"ping pong","keywords":"ping pong,bóng bàn,ping pong,sport","category":"symbols"},
    {"emoji":"🏸","name":"badminton","keywords":"badminton,cầu lông,badminton,sport","category":"symbols"},
    {"emoji":"🥊","name":"boxing glove","keywords":"boxing glove,găng tay đấm bốc,boxing glove,sport","category":"symbols"},
    {"emoji":"🥋","name":"martial arts uniform","keywords":"martial arts uniform,đồ võ thuật,martial arts uniform,sport","category":"symbols"},
    {"emoji":"🥅","name":"goal net","keywords":"goal net,lưới khung thành,goal net,sport","category":"symbols"},
    {"emoji":"⛳","name":"flag in hole","keywords":"flag in hole,cờ trong lỗ,flag in hole,golf","category":"symbols"},
    {"emoji":"⛸️","name":"ice skate","keywords":"ice skate,giày trượt băng,ice skate,sport","category":"symbols"},
    {"emoji":"🎣","name":"fishing pole","keywords":"fishing pole,cần câu cá,fishing pole,fishing","category":"symbols"},
    {"emoji":"🤿","name":"diving mask","keywords":"diving mask,mặt nạ lặn,diving mask,diving","category":"symbols"},
    {"emoji":"🎽","name":"running shirt","keywords":"running shirt,áo chạy bộ,running shirt,sport","category":"symbols"},
    {"emoji":"🎿","name":"skis","keywords":"skis,ván trượt tuyết,skis,sport","category":"symbols"},
    {"emoji":"🛷","name":"sled","keywords":"sled,xe trượt tuyết,sled,sport","category":"symbols"},
    {"emoji":"🥌","name":"curling stone","keywords":"curling stone,đá curling,curling stone,sport","category":"symbols"},
    {"emoji":"🎯","name":"bullseye","keywords":"bullseye,trúng hồng tâm,bullseye,target","category":"symbols"},
    {"emoji":"🎱","name":"pool 8 ball","keywords":"pool 8 ball,bi-a số 8,pool 8 ball,game","category":"symbols"},
    {"emoji":"🔮","name":"crystal ball","keywords":"crystal ball,quả cầu pha lê,crystal ball,fortune","category":"symbols"},
    {"emoji":"🪄","name":"magic wand","keywords":"magic wand,đũa thần,magic wand,magic","category":"symbols"},
    {"emoji":"🎮","name":"video game","keywords":"video game,máy chơi game,video game,game","category":"symbols"},
    {"emoji":"🕹️","name":"joystick","keywords":"joystick,cần điều khiển,joystick,game","category":"symbols"},
    {"emoji":"🎰","name":"slot machine","keywords":"slot machine,máy đánh bạc,slot machine,casino","category":"symbols"},
    {"emoji":"🎲","name":"game die","keywords":"game die,xúc xắc,game die,dice","category":"symbols"},
    {"emoji":"🧩","name":"puzzle piece","keywords":"puzzle piece,mảnh ghép,puzzle piece,game","category":"symbols"},
    {"emoji":"🧸","name":"teddy bear","keywords":"teddy bear,gấu bông,teddy bear,toy","category":"symbols"},
    {"emoji":"🪅","name":"piñata","keywords":"piñata,piñata,piñata,party","category":"symbols"},
    {"emoji":"🪆","name":"nesting dolls","keywords":"nesting dolls,búp bê lồng nhau,nesting dolls,russian","category":"symbols"},
    {"emoji":"♠️","name":"spade suit","keywords":"spade suit,chất bích,spade suit,card","category":"symbols"},
    {"emoji":"♥️","name":"heart suit","keywords":"heart suit,chất cơ,heart suit,card","category":"symbols"},
    {"emoji":"♦️","name":"diamond suit","keywords":"diamond suit,chất rô,diamond suit,card","category":"symbols"},
    {"emoji":"♣️","name":"club suit","keywords":"club suit,chất nhép,club suit,card","category":"symbols"},
    {"emoji":"♟️","name":"chess pawn","keywords":"chess pawn,tốt cờ vua,chess pawn,chess","category":"symbols"},
    {"emoji":"🃏","name":"joker","keywords":"joker,phăng teo,joker,card","category":"symbols"},
    {"emoji":"🀄","name":"mahjong red dragon","keywords":"mahjong red dragon,con rồng đỏ mahjong,mahjong red dragon,game","category":"symbols"},
    {"emoji":"🎴","name":"flower playing cards","keywords":"flower playing cards,bài hoa,flower playing cards,game","category":"symbols"},
    {"emoji":"🎭","name":"performing arts","keywords":"performing arts,nghệ thuật biểu diễn,performing arts,theater","category":"symbols"},
    {"emoji":"🖼️","name":"framed picture","keywords":"framed picture,bức tranh có khung,framed picture,art","category":"symbols"},
    {"emoji":"🎨","name":"artist palette","keywords":"artist palette,bảng màu họa sĩ,artist palette,art","category":"symbols"},
    {"emoji":"🧵","name":"thread","keywords":"thread,chỉ khâu,thread,sewing","category":"symbols"},
    {"emoji":"🪡","name":"sewing needle","keywords":"sewing needle,kim khâu,sewing needle,sewing","category":"symbols"},
    {"emoji":"🧶","name":"yarn","keywords":"yarn,cuộn len,yarn,knitting","category":"symbols"},
    {"emoji":"🪢","name":"knot","keywords":"knot,nút thắt,knot,rope","category":"symbols"},
    {"emoji":"👓","name":"glasses","keywords":"glasses,kính mắt,glasses,vision","category":"symbols"},
    {"emoji":"🕶️","name":"sunglasses","keywords":"sunglasses,kính râm,sunglasses,sun","category":"symbols"},
    {"emoji":"🥽","name":"goggles","keywords":"goggles,kính bảo hộ,goggles,protection","category":"symbols"},
    {"emoji":"🥼","name":"lab coat","keywords":"lab coat,áo khoác phòng thí nghiệm,lab coat,science","category":"symbols"},
    {"emoji":"🦺","name":"safety vest","keywords":"safety vest,áo vest an toàn,safety vest,construction","category":"symbols"},
    {"emoji":"👔","name":"necktie","keywords":"necktie,cà vạt,necktie,formal","category":"symbols"},
    {"emoji":"👕","name":"t-shirt","keywords":"t-shirt,áo thun,t-shirt,casual","category":"symbols"},
    {"emoji":"👖","name":"jeans","keywords":"jeans,quần jeans,jeans,pants","category":"symbols"},
    {"emoji":"🧣","name":"scarf","keywords":"scarf,khăn quàng cổ,scarf,winter","category":"symbols"},
    {"emoji":"🧤","name":"gloves","keywords":"gloves,găng tay,gloves,winter","category":"symbols"},
    {"emoji":"🧥","name":"coat","keywords":"coat,áo khoác,coat,winter","category":"symbols"},
    {"emoji":"🧦","name":"socks","keywords":"socks,tất,socks,clothing","category":"symbols"},
    {"emoji":"👗","name":"dress","keywords":"dress,váy,dress,clothing","category":"symbols"},
    {"emoji":"👘","name":"kimono","keywords":"kimono,áo kimono,kimono,japanese","category":"symbols"},
    {"emoji":"🥻","name":"sari","keywords":"sari,áo sari,sari,indian","category":"symbols"},
    {"emoji":"🩱","name":"one-piece swimsuit","keywords":"one-piece swimsuit,đồ bơi một mảnh,one-piece swimsuit,swim","category":"symbols"},
    {"emoji":"🩲","name":"briefs","keywords":"briefs,quần lót nam,briefs,underwear","category":"symbols"},
    {"emoji":"🩳","name":"shorts","keywords":"shorts,quần đùi,shorts,clothing","category":"symbols"},
    {"emoji":"👙","name":"bikini","keywords":"bikini,bikini,bikini,swim","category":"symbols"},
    {"emoji":"👚","name":"woman's clothes","keywords":"woman's clothes,quần áo phụ nữ,woman's clothes,clothing","category":"symbols"},
    {"emoji":"👛","name":"purse","keywords":"purse,ví nhỏ,purse,bag","category":"symbols"},
    {"emoji":"👜","name":"handbag","keywords":"handbag,túi xách,handbag,bag","category":"symbols"},
    {"emoji":"👝","name":"clutch bag","keywords":"clutch bag,túi xách nhỏ,clutch bag,bag","category":"symbols"},
    {"emoji":"🎒","name":"backpack","keywords":"backpack,ba lô,backpack,school","category":"symbols"},
    {"emoji":"🩴","name":"thong sandal","keywords":"thong sandal,dép xỏ ngón,thong sandal,footwear","category":"symbols"},
    {"emoji":"👞","name":"man's shoe","keywords":"man's shoe,giày nam,man's shoe,footwear","category":"symbols"},
    {"emoji":"👟","name":"running shoe","keywords":"running shoe,giày chạy,running shoe,sport","category":"symbols"},
    {"emoji":"🥾","name":"hiking boot","keywords":"hiking boot,giày leo núi,hiking boot,outdoor","category":"symbols"},
    {"emoji":"🥿","name":"flat shoe","keywords":"flat shoe,giày bệt,flat shoe,footwear","category":"symbols"},
    {"emoji":"👠","name":"high-heeled shoe","keywords":"high-heeled shoe,giày cao gót,high-heeled shoe,footwear","category":"symbols"},
    {"emoji":"👡","name":"woman's sandal","keywords":"woman's sandal,dép nữ,woman's sandal,footwear","category":"symbols"},
    {"emoji":"🩰","name":"ballet shoes","keywords":"ballet shoes,giày ballet,ballet shoes,dance","category":"symbols"},
    {"emoji":"👢","name":"woman's boot","keywords":"woman's boot,bốt nữ,woman's boot,footwear","category":"symbols"},
    {"emoji":"👑","name":"crown","keywords":"crown,vương miện,crown,royal","category":"symbols"},
    {"emoji":"👒","name":"woman's hat","keywords":"woman's hat,mũ nữ,woman's hat,accessory","category":"symbols"},
    {"emoji":"🎩","name":"top hat","keywords":"top hat,mũ chóp cao,top hat,formal","category":"symbols"},
    {"emoji":"🎓","name":"graduation cap","keywords":"graduation cap,mũ tốt nghiệp,graduation cap,school","category":"symbols"},
    {"emoji":"🧢","name":"billed cap","keywords":"billed cap,mũ lưỡi trai,billed cap,casual","category":"symbols"},
    {"emoji":"🪖","name":"military helmet","keywords":"military helmet,mũ bảo hiểm quân đội,military helmet,army","category":"symbols"},
    {"emoji":"⛑️","name":"rescue worker's helmet","keywords":"rescue worker's helmet,mũ cứu hộ,rescue worker's helmet,safety","category":"symbols"},
    {"emoji":"📿","name":"prayer beads","keywords":"prayer beads,chuỗi hạt cầu nguyện,prayer beads,religion","category":"symbols"},
    {"emoji":"💄","name":"lipstick","keywords":"lipstick,son môi,lipstick,makeup","category":"symbols"},
    {"emoji":"💍","name":"ring","keywords":"ring,nhẫn,ring,jewelry","category":"symbols"},
    {"emoji":"💎","name":"gem stone","keywords":"gem stone,đá quý,gem stone,jewelry","category":"symbols"},

    // ========== CỜ (50+) ==========
    {"emoji":"🏁","name":"chequered flag","keywords":"chequered flag,cờ carô,chequered flag,finish","category":"flags"},
    {"emoji":"🚩","name":"triangular flag","keywords":"triangular flag,cờ tam giác,triangular flag,flag","category":"flags"},
    {"emoji":"🎌","name":"crossed flags","keywords":"crossed flags,cờ chéo,crossed flags,japan","category":"flags"},
    {"emoji":"🏴","name":"black flag","keywords":"black flag,cờ đen,black flag,flag","category":"flags"},
    {"emoji":"🏳️","name":"white flag","keywords":"white flag,cờ trắng,white flag,surrender","category":"flags"},
    {"emoji":"🏳️‍🌈","name":"rainbow flag","keywords":"rainbow flag,cờ cầu vồng,rainbow flag,pride","category":"flags"},
    {"emoji":"🏳️‍⚧️","name":"transgender flag","keywords":"transgender flag,cờ chuyển giới,transgender flag,pride","category":"flags"},
    {"emoji":"🏴‍☠️","name":"pirate flag","keywords":"pirate flag,cờ cướp biển,pirate flag,skull","category":"flags"},
    {"emoji":"🇦🇨","name":"flag: Ascension Island","keywords":"flag: Ascension Island,cờ Đảo Ascension,Ascension Island,flag","category":"flags"},
    {"emoji":"🇦🇩","name":"flag: Andorra","keywords":"flag: Andorra,cờ Andorra,Andorra,flag","category":"flags"},
    {"emoji":"🇦🇪","name":"flag: United Arab Emirates","keywords":"flag: United Arab Emirates,cờ Các Tiểu vương quốc Ả Rập Thống nhất,United Arab Emirates,flag","category":"flags"},
    {"emoji":"🇦🇫","name":"flag: Afghanistan","keywords":"flag: Afghanistan,cờ Afghanistan,Afghanistan,flag","category":"flags"},
    {"emoji":"🇦🇬","name":"flag: Antigua & Barbuda","keywords":"flag: Antigua & Barbuda,cờ Antigua và Barbuda,Antigua Barbuda,flag","category":"flags"},
    {"emoji":"🇦🇮","name":"flag: Anguilla","keywords":"flag: Anguilla,cờ Anguilla,Anguilla,flag","category":"flags"},
    {"emoji":"🇦🇱","name":"flag: Albania","keywords":"flag: Albania,cờ Albania,Albania,flag","category":"flags"},
    {"emoji":"🇦🇲","name":"flag: Armenia","keywords":"flag: Armenia,cờ Armenia,Armenia,flag","category":"flags"},
    {"emoji":"🇦🇴","name":"flag: Angola","keywords":"flag: Angola,cờ Angola,Angola,flag","category":"flags"},
    {"emoji":"🇦🇶","name":"flag: Antarctica","keywords":"flag: Antarctica,cờ Nam Cực,Antarctica,flag","category":"flags"},
    {"emoji":"🇦🇷","name":"flag: Argentina","keywords":"flag: Argentina,cờ Argentina,Argentina,flag","category":"flags"},
    {"emoji":"🇦🇸","name":"flag: American Samoa","keywords":"flag: American Samoa,cờ Samoa thuộc Mỹ,American Samoa,flag","category":"flags"},
    {"emoji":"🇦🇹","name":"flag: Austria","keywords":"flag: Austria,cờ Áo,Austria,flag","category":"flags"},
    {"emoji":"🇦🇺","name":"flag: Australia","keywords":"flag: Australia,cờ Australia,Australia,flag","category":"flags"},
    {"emoji":"🇦🇼","name":"flag: Aruba","keywords":"flag: Aruba,cờ Aruba,Aruba,flag","category":"flags"},
    {"emoji":"🇦🇽","name":"flag: Åland Islands","keywords":"flag: Åland Islands,cờ Quần đảo Åland,Åland Islands,flag","category":"flags"},
    {"emoji":"🇦🇿","name":"flag: Azerbaijan","keywords":"flag: Azerbaijan,cờ Azerbaijan,Azerbaijan,flag","category":"flags"},
    {"emoji":"🇧🇦","name":"flag: Bosnia & Herzegovina","keywords":"flag: Bosnia & Herzegovina,cờ Bosnia và Herzegovina,Bosnia Herzegovina,flag","category":"flags"},
    {"emoji":"🇧🇧","name":"flag: Barbados","keywords":"flag: Barbados,cờ Barbados,Barbados,flag","category":"flags"},
    {"emoji":"🇧🇩","name":"flag: Bangladesh","keywords":"flag: Bangladesh,cờ Bangladesh,Bangladesh,flag","category":"flags"},
    {"emoji":"🇧🇪","name":"flag: Belgium","keywords":"flag: Belgium,cờ Bỉ,Belgium,flag","category":"flags"},
    {"emoji":"🇧🇫","name":"flag: Burkina Faso","keywords":"flag: Burkina Faso,cờ Burkina Faso,Burkina Faso,flag","category":"flags"},
    {"emoji":"🇧🇬","name":"flag: Bulgaria","keywords":"flag: Bulgaria,cờ Bulgaria,Bulgaria,flag","category":"flags"},
    {"emoji":"🇧🇭","name":"flag: Bahrain","keywords":"flag: Bahrain,cờ Bahrain,Bahrain,flag","category":"flags"},
    {"emoji":"🇧🇮","name":"flag: Burundi","keywords":"flag: Burundi,cờ Burundi,Burundi,flag","category":"flags"},
    {"emoji":"🇧🇯","name":"flag: Benin","keywords":"flag: Benin,cờ Benin,Benin,flag","category":"flags"},
    {"emoji":"🇧🇱","name":"flag: St. Barthélemy","keywords":"flag: St. Barthélemy,cờ Saint Barthélemy,St. Barthélemy,flag","category":"flags"},
    {"emoji":"🇧🇲","name":"flag: Bermuda","keywords":"flag: Bermuda,cờ Bermuda,Bermuda,flag","category":"flags"},
    {"emoji":"🇧🇳","name":"flag: Brunei","keywords":"flag: Brunei,cờ Brunei,Brunei,flag","category":"flags"},
    {"emoji":"🇧🇴","name":"flag: Bolivia","keywords":"flag: Bolivia,cờ Bolivia,Bolivia,flag","category":"flags"},
    {"emoji":"🇧🇶","name":"flag: Caribbean Netherlands","keywords":"flag: Caribbean Netherlands,cờ Hà Lan Caribbean,Caribbean Netherlands,flag","category":"flags"},
    {"emoji":"🇧🇷","name":"flag: Brazil","keywords":"flag: Brazil,cờ Brazil,Brazil,flag","category":"flags"},
    {"emoji":"🇧🇸","name":"flag: Bahamas","keywords":"flag: Bahamas,cờ Bahamas,Bahamas,flag","category":"flags"},
    {"emoji":"🇧🇹","name":"flag: Bhutan","keywords":"flag: Bhutan,cờ Bhutan,Bhutan,flag","category":"flags"},
    {"emoji":"🇧🇻","name":"flag: Bouvet Island","keywords":"flag: Bouvet Island,cờ Đảo Bouvet,Bouvet Island,flag","category":"flags"},
    {"emoji":"🇧🇼","name":"flag: Botswana","keywords":"flag: Botswana,cờ Botswana,Botswana,flag","category":"flags"},
    {"emoji":"🇧🇾","name":"flag: Belarus","keywords":"flag: Belarus,cờ Belarus,Belarus,flag","category":"flags"},
    {"emoji":"🇧🇿","name":"flag: Belize","keywords":"flag: Belize,cờ Belize,Belize,flag","category":"flags"},
    {"emoji":"🇨🇦","name":"flag: Canada","keywords":"flag: Canada,cờ Canada,Canada,flag","category":"flags"},
    {"emoji":"🇨🇨","name":"flag: Cocos (Keeling) Islands","keywords":"flag: Cocos (Keeling) Islands,cờ Quần đảo Cocos (Keeling),Cocos Islands,flag","category":"flags"},
    {"emoji":"🇨🇩","name":"flag: Congo - Kinshasa","keywords":"flag: Congo - Kinshasa,cờ Congo - Kinshasa,Congo Kinshasa,flag","category":"flags"},
    {"emoji":"🇨🇫","name":"flag: Central African Republic","keywords":"flag: Central African Republic,cờ Cộng hòa Trung Phi,Central African Republic,flag","category":"flags"},
    {"emoji":"🇨🇬","name":"flag: Congo - Brazzaville","keywords":"flag: Congo - Brazzaville,cờ Congo - Brazzaville,Congo Brazzaville,flag","category":"flags"},
    {"emoji":"🇨🇭","name":"flag: Switzerland","keywords":"flag: Switzerland,cờ Thụy Sĩ,Switzerland,flag","category":"flags"},
    {"emoji":"🇨🇮","name":"flag: Côte d'Ivoire","keywords":"flag: Côte d'Ivoire,cờ Bờ Biển Ngà,Côte d'Ivoire,flag","category":"flags"},
    {"emoji":"🇨🇰","name":"flag: Cook Islands","keywords":"flag: Cook Islands,cờ Quần đảo Cook,Cook Islands,flag","category":"flags"},
    {"emoji":"🇨🇱","name":"flag: Chile","keywords":"flag: Chile,cờ Chile,Chile,flag","category":"flags"},
    {"emoji":"🇨🇲","name":"flag: Cameroon","keywords":"flag: Cameroon,cờ Cameroon,Cameroon,flag","category":"flags"},
    {"emoji":"🇨🇳","name":"flag: China","keywords":"flag: China,cờ Trung Quốc,China,flag","category":"flags"},
    {"emoji":"🇨🇴","name":"flag: Colombia","keywords":"flag: Colombia,cờ Colombia,Colombia,flag","category":"flags"},
    {"emoji":"🇨🇵","name":"flag: Clipperton Island","keywords":"flag: Clipperton Island,cờ Đảo Clipperton,Clipperton Island,flag","category":"flags"},
    {"emoji":"🇨🇷","name":"flag: Costa Rica","keywords":"flag: Costa Rica,cờ Costa Rica,Costa Rica,flag","category":"flags"},
    {"emoji":"🇨🇺","name":"flag: Cuba","keywords":"flag: Cuba,cờ Cuba,Cuba,flag","category":"flags"},
    {"emoji":"🇨🇻","name":"flag: Cape Verde","keywords":"flag: Cape Verde,cờ Cape Verde,Cape Verde,flag","category":"flags"},
    {"emoji":"🇨🇼","name":"flag: Curaçao","keywords":"flag: Curaçao,cờ Curaçao,Curaçao,flag","category":"flags"},
    {"emoji":"🇨🇽","name":"flag: Christmas Island","keywords":"flag: Christmas Island,cờ Đảo Giáng Sinh,Christmas Island,flag","category":"flags"},
    {"emoji":"🇨🇾","name":"flag: Cyprus","keywords":"flag: Cyprus,cờ Síp,Cyprus,flag","category":"flags"},
    {"emoji":"🇨🇿","name":"flag: Czechia","keywords":"flag: Czechia,cờ Séc,Czechia,flag","category":"flags"},
    {"emoji":"🇩🇪","name":"flag: Germany","keywords":"flag: Germany,cờ Đức,Germany,flag","category":"flags"},
    {"emoji":"🇩🇬","name":"flag: Diego Garcia","keywords":"flag: Diego Garcia,cờ Diego Garcia,Diego Garcia,flag","category":"flags"},
    {"emoji":"🇩🇯","name":"flag: Djibouti","keywords":"flag: Djibouti,cờ Djibouti,Djibouti,flag","category":"flags"},
    {"emoji":"🇩🇰","name":"flag: Denmark","keywords":"flag: Denmark,cờ Đan Mạch,Denmark,flag","category":"flags"},
    {"emoji":"🇩🇲","name":"flag: Dominica","keywords":"flag: Dominica,cờ Dominica,Dominica,flag","category":"flags"},
    {"emoji":"🇩🇴","name":"flag: Dominican Republic","keywords":"flag: Dominican Republic,cờ Cộng hòa Dominicana,Dominican Republic,flag","category":"flags"},
    {"emoji":"🇩🇿","name":"flag: Algeria","keywords":"flag: Algeria,cờ Algeria,Algeria,flag","category":"flags"},
    {"emoji":"🇪🇦","name":"flag: Ceuta & Melilla","keywords":"flag: Ceuta & Melilla,cờ Ceuta và Melilla,Ceuta Melilla,flag","category":"flags"},
    {"emoji":"🇪🇨","name":"flag: Ecuador","keywords":"flag: Ecuador,cờ Ecuador,Ecuador,flag","category":"flags"},
    {"emoji":"🇪🇪","name":"flag: Estonia","keywords":"flag: Estonia,cờ Estonia,Estonia,flag","category":"flags"},
    {"emoji":"🇪🇬","name":"flag: Egypt","keywords":"flag: Egypt,cờ Ai Cập,Egypt,flag","category":"flags"},
    {"emoji":"🇪🇭","name":"flag: Western Sahara","keywords":"flag: Western Sahara,cờ Tây Sahara,Western Sahara,flag","category":"flags"},
    {"emoji":"🇪🇷","name":"flag: Eritrea","keywords":"flag: Eritrea,cờ Eritrea,Eritrea,flag","category":"flags"},
    {"emoji":"🇪🇸","name":"flag: Spain","keywords":"flag: Spain,cờ Tây Ban Nha,Spain,flag","category":"flags"},
    {"emoji":"🇪🇹","name":"flag: Ethiopia","keywords":"flag: Ethiopia,cờ Ethiopia,Ethiopia,flag","category":"flags"},
    {"emoji":"🇪🇺","name":"flag: European Union","keywords":"flag: European Union,cờ Liên minh châu Âu,European Union,flag","category":"flags"},
    {"emoji":"🇫🇮","name":"flag: Finland","keywords":"flag: Finland,cờ Phần Lan,Finland,flag","category":"flags"},
    {"emoji":"🇫🇯","name":"flag: Fiji","keywords":"flag: Fiji,cờ Fiji,Fiji,flag","category":"flags"},
    {"emoji":"🇫🇰","name":"flag: Falkland Islands","keywords":"flag: Falkland Islands,cờ Quần đảo Falkland,Falkland Islands,flag","category":"flags"},
    {"emoji":"🇫🇲","name":"flag: Micronesia","keywords":"flag: Micronesia,cờ Micronesia,Micronesia,flag","category":"flags"},
    {"emoji":"🇫🇴","name":"flag: Faroe Islands","keywords":"flag: Faroe Islands,cờ Quần đảo Faroe,Faroe Islands,flag","category":"flags"},
    {"emoji":"🇫🇷","name":"flag: France","keywords":"flag: France,cờ Pháp,France,flag","category":"flags"},
    {"emoji":"🇬🇦","name":"flag: Gabon","keywords":"flag: Gabon,cờ Gabon,Gabon,flag","category":"flags"},
    {"emoji":"🇬🇧","name":"flag: United Kingdom","keywords":"flag: United Kingdom,cờ Vương quốc Anh,United Kingdom,flag","category":"flags"},
    {"emoji":"🇬🇩","name":"flag: Grenada","keywords":"flag: Grenada,cờ Grenada,Grenada,flag","category":"flags"},
    {"emoji":"🇬🇪","name":"flag: Georgia","keywords":"flag: Georgia,cờ Georgia,Georgia,flag","category":"flags"},
    {"emoji":"🇬🇫","name":"flag: French Guiana","keywords":"flag: French Guiana,cờ Guiana thuộc Pháp,French Guiana,flag","category":"flags"},
    {"emoji":"🇬🇬","name":"flag: Guernsey","keywords":"flag: Guernsey,cờ Guernsey,Guernsey,flag","category":"flags"},
    {"emoji":"🇬🇭","name":"flag: Ghana","keywords":"flag: Ghana,cờ Ghana,Ghana,flag","category":"flags"},
    {"emoji":"🇬🇮","name":"flag: Gibraltar","keywords":"flag: Gibraltar,cờ Gibraltar,Gibraltar,flag","category":"flags"},
    {"emoji":"🇬🇱","name":"flag: Greenland","keywords":"flag: Greenland,cờ Greenland,Greenland,flag","category":"flags"},
    {"emoji":"🇬🇲","name":"flag: Gambia","keywords":"flag: Gambia,cờ Gambia,Gambia,flag","category":"flags"},
    {"emoji":"🇬🇳","name":"flag: Guinea","keywords":"flag: Guinea,cờ Guinea,Guinea,flag","category":"flags"},
    {"emoji":"🇬🇵","name":"flag: Guadeloupe","keywords":"flag: Guadeloupe,cờ Guadeloupe,Guadeloupe,flag","category":"flags"},
    {"emoji":"🇬🇶","name":"flag: Equatorial Guinea","keywords":"flag: Equatorial Guinea,cờ Guinea Xích Đạo,Equatorial Guinea,flag","category":"flags"},
    {"emoji":"🇬🇷","name":"flag: Greece","keywords":"flag: Greece,cờ Hy Lạp,Greece,flag","category":"flags"},
    {"emoji":"🇬🇸","name":"flag: South Georgia & South Sandwich Islands","keywords":"flag: South Georgia & South Sandwich Islands,cờ Nam Georgia & Quần đảo Nam Sandwich,South Georgia,flag","category":"flags"},
    {"emoji":"🇬🇹","name":"flag: Guatemala","keywords":"flag: Guatemala,cờ Guatemala,Guatemala,flag","category":"flags"},
    {"emoji":"🇬🇺","name":"flag: Guam","keywords":"flag: Guam,cờ Guam,Guam,flag","category":"flags"},
    {"emoji":"🇬🇼","name":"flag: Guinea-Bissau","keywords":"flag: Guinea-Bissau,cờ Guinea-Bissau,Guinea-Bissau,flag","category":"flags"},
    {"emoji":"🇬🇾","name":"flag: Guyana","keywords":"flag: Guyana,cờ Guyana,Guyana,flag","category":"flags"},
    {"emoji":"🇭🇰","name":"flag: Hong Kong SAR China","keywords":"flag: Hong Kong SAR China,cờ Hong Kong Trung Quốc,Hong Kong,flag","category":"flags"},
    {"emoji":"🇭🇲","name":"flag: Heard & McDonald Islands","keywords":"flag: Heard & McDonald Islands,cờ Đảo Heard & McDonald,Heard McDonald Islands,flag","category":"flags"},
    {"emoji":"🇭🇳","name":"flag: Honduras","keywords":"flag: Honduras,cờ Honduras,Honduras,flag","category":"flags"},
    {"emoji":"🇭🇷","name":"flag: Croatia","keywords":"flag: Croatia,cờ Croatia,Croatia,flag","category":"flags"},
    {"emoji":"🇭🇹","name":"flag: Haiti","keywords":"flag: Haiti,cờ Haiti,Haiti,flag","category":"flags"},
    {"emoji":"🇭🇺","name":"flag: Hungary","keywords":"flag: Hungary,cờ Hungary,Hungary,flag","category":"flags"},
    {"emoji":"🇮🇨","name":"flag: Canary Islands","keywords":"flag: Canary Islands,cờ Quần đảo Canary,Canary Islands,flag","category":"flags"},
    {"emoji":"🇮🇩","name":"flag: Indonesia","keywords":"flag: Indonesia,cờ Indonesia,Indonesia,flag","category":"flags"},
    {"emoji":"🇮🇪","name":"flag: Ireland","keywords":"flag: Ireland,cờ Ireland,Ireland,flag","category":"flags"},
    {"emoji":"🇮🇱","name":"flag: Israel","keywords":"flag: Israel,cờ Israel,Israel,flag","category":"flags"},
    {"emoji":"🇮🇲","name":"flag: Isle of Man","keywords":"flag: Isle of Man,cờ Đảo Man,Isle of Man,flag","category":"flags"},
    {"emoji":"🇮🇳","name":"flag: India","keywords":"flag: India,cờ Ấn Độ,India,flag","category":"flags"},
    {"emoji":"🇮🇴","name":"flag: British Indian Ocean Territory","keywords":"flag: British Indian Ocean Territory,cờ Lãnh thổ Ấn Độ Dương thuộc Anh,British Indian Ocean Territory,flag","category":"flags"},
    {"emoji":"🇮🇶","name":"flag: Iraq","keywords":"flag: Iraq,cờ Iraq,Iraq,flag","category":"flags"}
    ];

    window.emojiPickerState = window.emojiPickerState || {
        isOpen: false,
        picker: null
    };

    // Khởi tạo Emoji Picker (Thư viện đầy đủ)
    function initEmojiPicker() {
        console.log('🎨 Initializing Premium Emoji Picker...');

        // Remove existing picker if any
        $('#instantEmojiPicker').remove();

        // Create premium picker container
        const pickerContainer = document.createElement('div');
        pickerContainer.id = 'instantEmojiPicker';
        pickerContainer.className = 'emoji-picker-premium';
        pickerContainer.style.cssText = `
            position: fixed;
            bottom: 90px;
            right: 20px;
            width: 380px;
            height: 460px;
            background: linear-gradient(135deg, #242526 0%, #1a1b1c 100%);
            border: 1px solid rgba(255, 255, 255, 0.1);
            border-radius: 20px;
            z-index: 10000;
            display: none;
            flex-direction: column;
            overflow: hidden;
            box-shadow: 
                0 25px 50px -12px rgba(0, 0, 0, 0.5),
                0 0 0 1px rgba(255, 255, 255, 0.05),
                inset 0 1px 0 rgba(255, 255, 255, 0.1);
            font-family: 'Segoe UI', -apple-system, BlinkMacSystemFont, sans-serif;
            backdrop-filter: blur(20px);
            animation: emojiSlideIn 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
            opacity: 0;
            transform: translateY(10px);
            transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
        `;

        // Premium HTML structure
        pickerContainer.innerHTML = `
            <div class="emoji-header" style="
                padding: 18px 20px 12px;
                border-bottom: 1px solid rgba(255, 255, 255, 0.08);
                background: rgba(36, 37, 38, 0.95);
                backdrop-filter: blur(10px);
                position: relative;
                overflow: hidden;
            ">
                <div class="header-top" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
                    <div style="display: flex; align-items: center; gap: 10px;">
                        <div style="
                            width: 36px;
                            height: 36px;
                            background: linear-gradient(135deg, #0084ff, #00c6ff);
                            border-radius: 10px;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            font-size: 18px;
                            color: white;
                            box-shadow: 0 4px 12px rgba(0, 132, 255, 0.3);
                        ">😊</div>
                        <div style="font-weight: 700; color: #fff; font-size: 16px; letter-spacing: 0.3px;">
                            Biểu tượng cảm xúc
                        </div>
                    </div>
                    <button id="closeEmojiPicker" style="
                        background: rgba(255, 255, 255, 0.08);
                        border: none;
                        color: #aaa;
                        font-size: 20px;
                        cursor: pointer;
                        padding: 8px;
                        border-radius: 50%;
                        width: 36px;
                        height: 36px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        transition: all 0.2s;
                    ">×</button>
                </div>
                
                <div class="search-container" style="position: relative;">
                    <input type="text" 
                        id="emojiSearchInput" 
                        placeholder="Tìm kiếm emoji..." 
                        style="
                                width: 100%;
                                background: rgba(58, 59, 60, 0.8);
                                border: 2px solid transparent;
                                border-radius: 12px;
                                padding: 12px 45px 12px 16px;
                                color: #fff;
                                font-size: 14px;
                                outline: none;
                                transition: all 0.3s;
                                box-sizing: border-box;
                                backdrop-filter: blur(10px);
                        "
                    >
                    <i class="fas fa-search" style="
                        position: absolute;
                        right: 16px;
                        top: 50%;
                        transform: translateY(-50%);
                        color: #8a8d91;
                        font-size: 14px;
                    "></i>
                </div>
                
                <!-- Shimmer effect -->
                <div class="header-shimmer" style="
                    position: absolute;
                    top: 0;
                    left: -100%;
                    width: 100%;
                    height: 100%;
                    background: linear-gradient(90deg, 
                        transparent 0%, 
                        rgba(255, 255, 255, 0.1) 50%, 
                        transparent 100%);
                    animation: shimmer 2s infinite;
                "></div>
            </div>
            
            <div class="emoji-category-tabs" style="
                display: flex;
                border-bottom: 1px solid rgba(255, 255, 255, 0.08);
                background: rgba(36, 37, 38, 0.95);
                padding: 0 12px;
                overflow-x: auto;
                scrollbar-width: none;
                -ms-overflow-style: none;
            ">
                ${window.EMOJI_CATEGORIES.map(cat => `
                    <button class="emoji-category-btn premium-tab" 
                            data-category="${cat.id}"
                            style="
                                padding: 14px 16px;
                                background: none;
                                border: none;
                                color: #8a8d91;
                                font-size: 24px;
                                cursor: pointer;
                                border-bottom: 3px solid transparent;
                                min-width: 50px;
                                transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
                                position: relative;
                                flex-shrink: 0;
                                display: flex;
                                flex-direction: column;
                                align-items: center;
                                gap: 4px;
                            "
                            title="${cat.name}">
                        <span style="font-size: 22px;">${cat.icon}</span>
                        <span style="
                            font-size: 10px;
                            font-weight: 600;
                            letter-spacing: 0.5px;
                            color: #8a8d91;
                            transition: all 0.3s;
                        ">${cat.name.substring(0, 8)}</span>
                    </button>
                `).join('')}
            </div>
            
            <div class="emoji-content" style="
                flex: 1;
                overflow-y: auto;
                padding: 16px;
                position: relative;
                background: rgba(36, 37, 38, 0.6);
            ">
                <div id="emojiSections" style="display: grid; gap: 24px;">
                    <!-- Emoji sections will be rendered here -->
                </div>
                
                <!-- Empty state -->
                <div id="emojiEmptyState" style="
                    display: none;
                    text-align: center;
                    padding: 60px 20px;
                    color: #8a8d91;
                ">
                    <div style="font-size: 48px; margin-bottom: 16px;">🔍</div>
                    <div style="font-weight: 600; margin-bottom: 8px; color: #fff;">Không tìm thấy emoji</div>
                    <div style="font-size: 13px;">Thử tìm kiếm với từ khóa khác</div>
                </div>
                
                <!-- Loading state -->
                <div id="emojiLoading" style="
                    display: none;
                    text-align: center;
                    padding: 60px 20px;
                    color: #8a8d91;
                ">
                    <div class="loading-spinner" style="
                        width: 40px;
                        height: 40px;
                        border: 3px solid rgba(0, 132, 255, 0.2);
                        border-top-color: #0084ff;
                        border-radius: 50%;
                        margin: 0 auto 20px;
                        animation: spin 1s linear infinite;
                    "></div>
                    <div>Đang tải emoji...</div>
                </div>
            </div>
            
            <!-- Recent emoji bar -->
            <div id="recentEmojiBar" style="
                padding: 12px 16px;
                border-top: 1px solid rgba(255, 255, 255, 0.08);
                background: rgba(36, 37, 38, 0.95);
                display: none;
            ">
                <div style="
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    margin-bottom: 10px;
                    color: #fff;
                    font-size: 13px;
                    font-weight: 600;
                ">
                    <span>🕒 Gần đây</span>
                    <button onclick="clearRecentEmojis()" style="
                        background: none;
                        border: none;
                        color: #8a8d91;
                        font-size: 12px;
                        cursor: pointer;
                        padding: 4px 8px;
                        border-radius: 6px;
                        transition: all 0.2s;
                    ">Xóa</button>
                </div>
                <div id="recentEmojiGrid" style="
                    display: grid;
                    grid-template-columns: repeat(10, 1fr);
                    gap: 6px;
                "></div>
            </div>
        `;

        document.body.appendChild(pickerContainer);
        
        // Add CSS animations
        const style = document.createElement('style');
        style.textContent = `
            @keyframes spin {
                to { transform: rotate(360deg); }
            }
            
            @keyframes slideIn {
                from { opacity: 0; transform: translateY(10px); }
                to { opacity: 1; transform: translateY(0); }
            }
            
            .premium-tab.active {
                color: #fff !important;
                border-bottom-color: #0084ff !important;
                background: rgba(0, 132, 255, 0.1) !important;
            }
            
            .premium-tab.active span {
                color: #0084ff !important;
            }
            
            .premium-tab:hover {
                color: #fff !important;
                transform: translateY(-2px);
            }
            
            .premium-tab:hover span {
                color: #fff !important;
            }
            
            /* Custom scrollbar */
            .emoji-content::-webkit-scrollbar {
                width: 6px;
            }
            
            .emoji-content::-webkit-scrollbar-track {
                background: rgba(255, 255, 255, 0.05);
                border-radius: 3px;
            }
            
            .emoji-content::-webkit-scrollbar-thumb {
                background: rgba(255, 255, 255, 0.2);
                border-radius: 3px;
                transition: background 0.3s;
            }
            
            .emoji-content::-webkit-scrollbar-thumb:hover {
                background: rgba(255, 255, 255, 0.3);
            }
            
            /* Hide scrollbar for category tabs */
            .emoji-category-tabs::-webkit-scrollbar {
                display: none;
            }
        `;
        document.head.appendChild(style);

        // State variables
        let isOpen = false;
        let currentCategory = 'smileys';
        let recentEmojis = JSON.parse(localStorage.getItem('recentEmojis') || '[]');

        // Function to render all emoji sections
        function renderAllEmojiSections() {
            const container = document.getElementById('emojiSections');
            const loading = document.getElementById('emojiLoading');
            
            loading.style.display = 'block';
            container.innerHTML = '';
            
            setTimeout(() => {
                window.EMOJI_CATEGORIES.forEach(cat => {
                    const emojis = window.EMOJI_DATA.filter(e => e.category === cat.id);
                    if (emojis.length === 0) return;
                    
                    const section = document.createElement('div');
                    section.className = 'emoji-section';
                    section.dataset.category = cat.id;
                    section.style.cssText = `
                        animation: slideIn 0.4s ease-out;
                        animation-fill-mode: both;
                        animation-delay: ${Math.random() * 0.2}s;
                    `;
                    
                    // Section title
                    const title = document.createElement('div');
                    title.className = 'section-title-premium';
                    title.innerHTML = `
                        <div style="
                            display: flex;
                            align-items: center;
                            gap: 10px;
                            color: #fff;
                            font-size: 14px;
                            font-weight: 600;
                            margin-bottom: 12px;
                            padding-bottom: 8px;
                            border-bottom: 1px solid rgba(255, 255, 255, 0.1);
                        ">
                            <span style="font-size: 18px;">${cat.icon}</span>
                            <span>${cat.name}</span>
                        </div>
                    `;
                    
                    // Emoji grid - FIXED: No horizontal scroll, perfect grid
                    const grid = document.createElement('div');
                    grid.className = 'emoji-grid-premium';
                    grid.style.cssText = `
                        display: grid;
                        grid-template-columns: repeat(8, 1fr);
                        gap: 6px;
                        margin-bottom: 20px;
                    `;
                    
                    emojis.forEach((emoji, index) => {
                        const btn = document.createElement('button');
                        btn.className = 'emoji-item-premium';
                        btn.dataset.emoji = emoji.emoji;
                        btn.dataset.name = emoji.name;
                        btn.innerHTML = emoji.emoji;
                        btn.style.cssText = `
                            width: 100%;
                            aspect-ratio: 1;
                            background: rgba(255, 255, 255, 0.05);
                            border: none;
                            font-size: 24px;
                            cursor: pointer;
                            border-radius: 12px;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
                            position: relative;
                            overflow: hidden;
                            animation: emojiPop 0.3s ease-out;
                            animation-fill-mode: both;
                            animation-delay: ${index * 0.01}s;
                        `;
                        btn.title = emoji.name;
                        
                        // Hover effect
                        btn.addEventListener('mouseenter', function() {
                            this.style.transform = 'scale(1.15) translateY(-3px)';
                            this.style.background = 'rgba(0, 132, 255, 0.15)';
                            this.style.boxShadow = '0 6px 20px rgba(0, 132, 255, 0.3)';
                            this.style.zIndex = '10';
                            
                            // Show tooltip
                            showEmojiTooltip(this, emoji.name);
                        });
                        
                        btn.addEventListener('mouseleave', function() {
                            this.style.transform = 'scale(1)';
                            this.style.background = 'rgba(255, 255, 255, 0.05)';
                            this.style.boxShadow = 'none';
                            this.style.zIndex = '1';
                            hideEmojiTooltip();
                        });
                        
                        // Click effect with ripple
                        btn.addEventListener('click', function(e) {
                            e.stopPropagation();
                            
                            // Ripple effect
                            const ripple = document.createElement('span');
                            ripple.className = 'emoji-ripple';
                            ripple.style.cssText = `
                                position: absolute;
                                border-radius: 50%;
                                background: rgba(0, 132, 255, 0.3);
                                transform: scale(0);
                                animation: ripple 0.6s linear;
                                width: 100%;
                                height: 100%;
                                top: 0;
                                left: 0;
                            `;
                            this.appendChild(ripple);
                            
                            // Selection animation
                            this.classList.add('emoji-selected');
                            setTimeout(() => {
                                this.classList.remove('emoji-selected');
                                ripple.remove();
                            }, 400);
                            
                            // Insert emoji
                            const input = document.getElementById('msgInput');
                            input.value += emoji.emoji;
                            input.focus();
                            
                            // Add to recent
                            addToRecentEmojis(emoji);
                            
                            // Close picker smoothly
                            setTimeout(() => {
                                closePicker();
                            }, 200);
                            
                            // Trigger input event
                            const event = new Event('input', { bubbles: true });
                            input.dispatchEvent(event);
                        });
                        
                        grid.appendChild(btn);
                    });
                    
                    section.appendChild(title);
                    section.appendChild(grid);
                    container.appendChild(section);
                });
                
                loading.style.display = 'none';
                updateRecentEmojiBar();
                
            }, 300);
        }

        // Show emoji tooltip
        function showEmojiTooltip(element, name) {
            let tooltip = document.getElementById('emojiTooltip');
            if (!tooltip) {
                tooltip = document.createElement('div');
                tooltip.id = 'emojiTooltip';
                tooltip.style.cssText = `
                    position: fixed;
                    background: rgba(0, 0, 0, 0.9);
                    color: white;
                    padding: 8px 12px;
                    border-radius: 8px;
                    font-size: 12px;
                    font-weight: 600;
                    z-index: 10001;
                    pointer-events: none;
                    opacity: 0;
                    transform: translateY(10px);
                    transition: all 0.2s;
                    backdrop-filter: blur(10px);
                    border: 1px solid rgba(255, 255, 255, 0.1);
                `;
                document.body.appendChild(tooltip);
            }
            
            const rect = element.getBoundingClientRect();
            tooltip.textContent = name;
            tooltip.style.left = `${rect.left + rect.width / 2}px`;
            tooltip.style.top = `${rect.top - 40}px`;
            tooltip.style.transform = 'translate(-50%, -10px)';
            tooltip.style.opacity = '1';
        }

        function hideEmojiTooltip() {
            const tooltip = document.getElementById('emojiTooltip');
            if (tooltip) {
                tooltip.style.opacity = '0';
                tooltip.style.transform = 'translate(-50%, 0px)';
            }
        }

        // Recent emojis functions
        function addToRecentEmojis(emoji) {
            recentEmojis = recentEmojis.filter(e => e.emoji !== emoji.emoji);
            recentEmojis.unshift(emoji);
            recentEmojis = recentEmojis.slice(0, 20);
            localStorage.setItem('recentEmojis', JSON.stringify(recentEmojis));
            updateRecentEmojiBar();
        }

        function updateRecentEmojiBar() {
            const bar = document.getElementById('recentEmojiBar');
            const grid = document.getElementById('recentEmojiGrid');
            
            if (recentEmojis.length > 0) {
                bar.style.display = 'block';
                grid.innerHTML = '';
                
                recentEmojis.slice(0, 10).forEach(emoji => {
                    const btn = document.createElement('button');
                    btn.innerHTML = emoji.emoji;
                    btn.style.cssText = `
                        width: 100%;
                        aspect-ratio: 1;
                        background: rgba(255, 255, 255, 0.05);
                        border: none;
                        font-size: 20px;
                        cursor: pointer;
                        border-radius: 8px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        transition: all 0.2s;
                    `;
                    btn.onclick = () => {
                        document.getElementById('msgInput').value += emoji.emoji;
                        closePicker();
                    };
                    grid.appendChild(btn);
                });
            } else {
                bar.style.display = 'none';
            }
        }

        // Clear recent emojis
        window.clearRecentEmojis = function() {
            recentEmojis = [];
            localStorage.removeItem('recentEmojis');
            updateRecentEmojiBar();
        };

        // Fixed search function
        function searchEmojis(query) {
            const sections = document.querySelectorAll('.emoji-section');
            const emptyState = document.getElementById('emojiEmptyState');
            let hasResults = false;
            
            if (!query.trim()) {
                sections.forEach(section => {
                    section.style.display = 'block';
                    section.querySelectorAll('.emoji-item-premium').forEach(item => {
                        item.style.display = 'flex';
                        item.style.animation = 'emojiPop 0.3s ease-out';
                    });
                });
                emptyState.style.display = 'none';
                return;
            }
            
            const searchTerm = query.toLowerCase();
            sections.forEach(section => {
                const emojiItems = section.querySelectorAll('.emoji-item-premium');
                let hasMatch = false;
                
                emojiItems.forEach(item => {
                    const emoji = item.dataset.emoji;
                    const name = item.dataset.name || '';
                    const emojiData = window.EMOJI_DATA.find(e => e.emoji === emoji);
                    
                    if (emojiData) {
                        const keywords = typeof emojiData.keywords === 'string' 
                            ? emojiData.keywords.split(',').map(k => k.trim().toLowerCase())
                            : [];
                        
                        const nameMatch = name.toLowerCase().includes(searchTerm);
                        const keywordMatch = keywords.some(kw => kw.includes(searchTerm));
                        
                        if (nameMatch || keywordMatch) {
                            item.style.display = 'flex';
                            item.style.animation = 'emojiPop 0.3s ease-out';
                            hasMatch = true;
                            hasResults = true;
                        } else {
                            item.style.display = 'none';
                        }
                    }
                });
                
                section.style.display = hasMatch ? 'block' : 'none';
            });
            
            emptyState.style.display = hasResults ? 'none' : 'block';
        }

        // Scroll to category
        function scrollToCategory(categoryId) {
            const section = document.querySelector(`.emoji-section[data-category="${categoryId}"]`);
            if (section) {
                const content = pickerContainer.querySelector('.emoji-content');
                content.scrollTop = section.offsetTop - 20;
            }
        }

        // Set active category tab
        function setActiveCategoryTab(categoryId) {
            const buttons = pickerContainer.querySelectorAll('.emoji-category-btn');
            buttons.forEach(btn => {
                if (btn.dataset.category === categoryId) {
                    btn.classList.add('active');
                } else {
                    btn.classList.remove('active');
                }
            });
        }

        // Update active category on scroll
        function updateActiveCategoryOnScroll() {
            const sections = pickerContainer.querySelectorAll('.emoji-section');
            const scrollTop = pickerContainer.querySelector('.emoji-content').scrollTop;
            
            let currentSection = null;
            sections.forEach(section => {
                if (section.offsetTop <= scrollTop + 100) {
                    currentSection = section;
                }
            });
            
            if (currentSection) {
                setActiveCategoryTab(currentSection.dataset.category);
            }
        }

        // Open picker
        function openPicker() {
            pickerContainer.style.display = 'flex';
            setTimeout(() => {
                pickerContainer.style.opacity = '1';
                pickerContainer.style.transform = 'translateY(0)';
            }, 10);
            
            renderAllEmojiSections();
            setActiveCategoryTab('smileys');
            
            setTimeout(() => {
                document.getElementById('emojiSearchInput').focus();
            }, 100);
            
            // isOpen = true;
            window.emojiPickerState.isOpen = true; // SỬA Ở ĐÂY
            window.emojiPickerState.picker = pickerContainer; // Lưu reference
        }

        // Close picker
        function closePicker() {
            pickerContainer.style.opacity = '0';
            pickerContainer.style.transform = 'translateY(10px)';
            setTimeout(() => {
                pickerContainer.style.display = 'none';
                // isOpen = false;
                window.emojiPickerState.isOpen = false;
                hideEmojiTooltip();
            }, 300);
        }

        // Event listeners
        const trigger = document.getElementById('emojiTrigger');
        
        trigger.addEventListener('click', function(e) {
            e.stopPropagation();
            e.preventDefault();
            
            if (!isOpen) {
                openPicker();
            } else {
                closePicker();
            }
        });
        
        document.getElementById('closeEmojiPicker').addEventListener('click', closePicker);
        
        // Search input
        let searchTimeout;
        document.getElementById('emojiSearchInput').addEventListener('input', function(e) {
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(() => {
                searchEmojis(e.target.value);
            }, 200);
        });
        
        // Category tabs
        pickerContainer.querySelectorAll('.emoji-category-btn').forEach(btn => {
            btn.addEventListener('click', function() {
                scrollToCategory(this.dataset.category);
            });
        });
        
        // Scroll event
        pickerContainer.querySelector('.emoji-content').addEventListener('scroll', updateActiveCategoryOnScroll);
        
        // Click outside to close
        document.addEventListener('click', function(e) {
            if (!pickerContainer.contains(e.target) && e.target !== trigger && isOpen) {
                closePicker();
            }
        });
        
        // ESC key to close
        document.addEventListener('keydown', function(e) {
            if (e.key === 'Escape' && isOpen) {
                closePicker();
            }
        });
        
        console.log('✅ Premium Emoji Picker initialized');
    }

    // Tạo hàm open/close riêng
    function openEmojiPicker() {
        if (!window.emojiPickerState.picker) {
            initEmojiPicker(); // Chỉ init nếu chưa có
        }
        
        const picker = window.emojiPickerState.picker;
        picker.style.display = 'flex';
        setTimeout(() => {
            picker.style.opacity = '1';
            picker.style.transform = 'translateY(0)';
        }, 10);
        
        window.emojiPickerState.isOpen = true;
    }

    function closeEmojiPicker() {
        if (!window.emojiPickerState.picker) return;
        
        const picker = window.emojiPickerState.picker;
        picker.style.opacity = '0';
        picker.style.transform = 'translateY(10px)';
        setTimeout(() => {
            picker.style.display = 'none';
            window.emojiPickerState.isOpen = false;
        }, 300);
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

    // Thêm vào cuối file messenger.js
    function addPremiumEffects() {
        // Thêm hiệu ứng "magnet" cho emoji khi di chuột gần
        document.addEventListener('mousemove', function(e) {
            if (!window.emojiPickerState || !window.emojiPickerState.isOpen) return;
            
            const emojiItems = document.querySelectorAll('.emoji-item-premium');
            emojiItems.forEach(item => {
                const rect = item.getBoundingClientRect();
                const centerX = rect.left + rect.width / 2;
                const centerY = rect.top + rect.height / 2;
                const distance = Math.sqrt(
                    Math.pow(e.clientX - centerX, 2) + 
                    Math.pow(e.clientY - centerY, 2)
                );
                
                if (distance < 100) {
                    const force = (100 - distance) / 100;
                    const angle = Math.atan2(
                        e.clientY - centerY,
                        e.clientX - centerX
                    );
                    
                    item.style.transform = `
                        translate(
                            ${Math.cos(angle) * force * 5}px,
                            ${Math.sin(angle) * force * 5}px
                        ) scale(${1 + force * 0.1})
                    `;
                } else {
                    item.style.transform = 'translate(0, 0) scale(1)';
                }
            });
        });
        
        // Thêm hiệu ứng "confetti" khi chọn emoji
        window.confettiEffect = function(x, y) {
            const confettiCount = 12;
            for (let i = 0; i < confettiCount; i++) {
                const confetti = document.createElement('div');
                confetti.innerHTML = ['🎉', '✨', '🌟', '💫', '🎊'][Math.floor(Math.random() * 5)];
                confetti.style.cssText = `
                    position: fixed;
                    left: ${x}px;
                    top: ${y}px;
                    font-size: 16px;
                    pointer-events: none;
                    z-index: 10002;
                    opacity: 0.9;
                    animation: confettiFall 1s ease-out forwards;
                `;
                
                document.body.appendChild(confetti);
                
                // Animation
                const angle = Math.random() * Math.PI * 2;
                const velocity = 2 + Math.random() * 3;
                const rotation = Math.random() * 720 - 360;
                
                let progress = 0;
                function animate() {
                    progress += 0.02;
                    if (progress > 1) {
                        confetti.remove();
                        return;
                    }
                    
                    const currentX = x + Math.cos(angle) * velocity * progress * 100;
                    const currentY = y + Math.sin(angle) * velocity * progress * 50 + progress * progress * 100;
                    
                    confetti.style.left = `${currentX}px`;
                    confetti.style.top = `${currentY}px`;
                    confetti.style.opacity = `${0.9 * (1 - progress)}`;
                    confetti.style.transform = `rotate(${rotation * progress}deg)`;
                    
                    requestAnimationFrame(animate);
                }
                animate();
            }
        };
        
        // Thêm CSS animation cho confetti
        const confettiStyle = document.createElement('style');
        confettiStyle.textContent = `
            @keyframes confettiFall {
                0% { transform: translate(0, 0) rotate(0deg); opacity: 1; }
                100% { transform: translate(var(--tx), var(--ty)) rotate(var(--r)); opacity: 0; }
            }
        `;
        document.head.appendChild(confettiStyle);
    }

    // Gọi hàm sau khi init
    setTimeout(addPremiumEffects, 1000);
})();