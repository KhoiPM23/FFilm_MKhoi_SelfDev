/**
 * MESSENGER VIPRO - HYBRID VERSION
 * UI: Chuẩn file cũ (Đẹp, đúng CSS)
 * Logic: Nâng cấp Realtime, Media, Stranger
 */
(function() {
    'use strict';

    // --- KHAI BÁO BIẾN ---
    let stompClient = null;
    let currentPartnerId = null;
    let currentPartnerName = '';
    let isCurrentPartnerFriend = false; // Biến check trạng thái bạn bè
    
    // Media
    let mediaRecorder = null;
    let audioChunks = [];
    let isRecording = false;
    let recordTimerInterval = null;
    let recordStartTime = 0;
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
        
        // Sticker Toggle
        $('#stickerBtn').off('click').on('click', window.toggleStickers);
        
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
            stompClient.send('/app/call-rejected', {}, JSON.stringify({
                receiverId: incomingCallData.senderId,
                reason: 'USER_BUSY'
            }));
        }
        
        incomingCallData = null;
        showToast('Đã từ chối cuộc gọi', 'info');
    };

    window.endCall = function() {
        if (callTimeout) clearTimeout(callTimeout);
        
        // Stop local stream
        if (localStream) {
            localStream.getTracks().forEach(track => track.stop());
            localStream = null;
        }
        
        // Close current call
        if (currentCall) {
            currentCall.close();
            currentCall = null;
        }
        
        // Send end call notification
        if (currentPartnerId) {
            stompClient.send('/app/call-ended', {}, JSON.stringify({
                receiverId: currentPartnerId,
                duration: callDuration || 0
            }));
        }
        
        // Save call log
        saveCallLog();
        
        // Hide call modal
        $('#videoCallModal').hide();
        
        // Reset call variables
        callDuration = 0;
        if (callTimerInterval) {
            clearInterval(callTimerInterval);
            callTimerInterval = null;
        }
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
            stompClient.subscribe(`/user/${currentUser.userID}/queue/private`, function(payload) {
                const msg = JSON.parse(payload.body);
                handleSocketMessage(msg);
            });
            
            // Subscribe đến typing notifications
            stompClient.subscribe(`/user/${currentUser.userID}/queue/typing`, function(payload) {
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
            stompClient.subscribe(`/user/${currentUser.userID}/queue/seen`, function(payload) {
                const data = JSON.parse(payload.body);
                updateSeenAvatar(data.messageId);
            });
            
            // Subscribe đến online status
            stompClient.subscribe(`/user/${currentUser.userID}/queue/online-status`, function(payload) {
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
        // 1. Xử lý Tín hiệu Gọi
        if (msg.type === 'CALL_REQ') {
            incomingCallData = { 
                peerId: msg.content,
                senderId: msg.senderId,
                senderName: msg.senderName || 'Người dùng FFilm',
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

        // 2. Chat messages
        const senderId = msg.senderId;
        const receiverId = msg.receiverId;
        const partnerId = (senderId === currentUser.userID) ? receiverId : senderId;

        // Append to UI if viewing this conversation
        if (currentPartnerId && currentPartnerId === partnerId) {
            appendMessageToUI(msg);
            if (senderId !== currentUser.userID) {
                markAsRead(msg.id); // Mark as seen
            }
        }

        // Always update conversation list preview WITHOUT full reload
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
        $.get('/api/v1/messenger/conversations', function(data) {
            const list = $('#conversationList');
            list.empty();
            if(!data) return;

            data.forEach(c => {
                const active = (c.partnerId == currentPartnerId) ? 'active' : '';
                const unread = (c.unreadCount > 0) ? 'unread' : '';
                const avatar = c.partnerAvatar || `https://ui-avatars.com/api/?name=${c.partnerName}`;
                
                // [FIX] Tạo HTML Badge Người Lạ (Chỉ là icon/chữ nhỏ bên cạnh)
                // KHÔNG sửa c.partnerName
                let strangerBadge = '';
                if (c.friend === false) {
                    strangerBadge = `<span class="badge-stranger-icon" title="Người lạ">(Người lạ)</span>`;
                }

                const isFriendStr = c.friend ? 'true' : 'false';
                // Truyền tham số vào onclick
                
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

    // --- 3. SELECT CONVERSATION ---
    window.selectConversation = function(partnerId, name, avatar, isFriend, isOnline, lastActive) {
        currentPartnerId = parseInt(partnerId);
        currentPartnerName = name;
        isCurrentPartnerFriend = (String(isFriend) === 'true');

        // UI Updates
        $('#emptyState').hide();
        $('#chatInterface').show();
        updateInfoSidebar(name, avatar);
        
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

        // Actions
        const unsendBtn = (isMine && !msg.isDeleted) 
            ? `<div class="action-btn" onclick="window.unsendMessage(${msgId})" title="Thu hồi"><i class="fas fa-trash"></i></div>` 
            : '';
        
        const actionsHtml = `
            <div class="msg-actions">
                <div class="action-btn" onclick="window.startReply(${msgId}, '${isMine ? 'Bạn' : currentPartnerName}', '${msg.content?.substring(0,50) || '[File]'}')" title="Trả lời"><i class="fas fa-reply"></i></div>
                ${unsendBtn}
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
        const messageElement = $(`#msg-${messageId}`);
        if (!messageElement.length) return;
        
        selectedMessageToForward = {
            id: messageId,
            content: messageElement.find('.bubble').text(),
            type: messageElement.data('type') || 'TEXT',
            sender: currentUser.name
        };
        
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

                if (timerInterval) clearInterval(timerInterval);
                
                // Timer
                let sec = 0;
                $('#recordTimer').text("00:00");
                timerInterval = setInterval(() => {
                    sec++;
                    const m = Math.floor(sec/60).toString().padStart(2,'0');
                    const s = (sec%60).toString().padStart(2,'0');
                    $('#recordTimer').text(`${m}:${s}`);
                }, 1000);

                mediaRecorder.onstop = () => {
                    if (!currentPartnerId) return;
                    const blob = new Blob(audioChunks, { type: 'audio/webm' });
                    
                    // Upload ngay lập tức (giống logic ảnh)
                    const formData = new FormData();
                    formData.append("file", blob, "audio_" + Date.now() + ".webm");
                    
                    $.ajax({
                        url: '/api/upload/image', // Dùng chung endpoint
                        type: 'POST',
                        data: formData,
                        processData: false,
                        contentType: false,
                        success: function(res) {
                            if(res.url) {
                                sendApiRequest({ 
                                    receiverId: currentPartnerId, 
                                    content: res.url, 
                                    type: 'AUDIO' 
                                });
                            }
                        }
                    });
                    
                    closeRecordingUI();
                };

            }).catch(err => alert("Cần quyền Mic"));
        }
    };

    // --- FIX 6: AUDIO PLAYER ---
    function renderAudioPlayer(audioUrl) {
        const playerId = 'audio-' + Date.now();
        
        return `
            <div class="msg-audio-player" data-audio-id="${playerId}">
                <button class="audio-play-btn" onclick="window.toggleAudioPlay('${playerId}', '${audioUrl}')">
                    <i class="fas fa-play"></i>
                </button>
                <div class="audio-waveform" onclick="window.seekAudio(event, '${playerId}')">
                    <div class="audio-progress-bar">
                        <div class="audio-progress-fill" id="${playerId}-progress"></div>
                    </div>
                </div>
                <span class="audio-time" id="${playerId}-time">0:00</span>
                <audio id="${playerId}" src="${audioUrl}" onended="window.onAudioEnded('${playerId}')" ontimeupdate="window.updateAudioProgress('${playerId}')"></audio>
            </div>
        `;
    }

    window.toggleAudioPlay = function(playerId) {
        const audio = document.getElementById(playerId + '-audio');
        const btn = $(`#${playerId} .audio-play-btn i`);
        
        if (!audio) return;
        
        if (audio.paused) {
            audio.play();
            btn.removeClass('fa-play').addClass('fa-pause');
        } else {
            audio.pause();
            btn.removeClass('fa-pause').addClass('fa-play');
        }
    };

    window.updateAudioProgress = function(playerId) {
        const audio = document.getElementById(playerId + '-audio');
        if (!audio || !audio.duration) return;
        
        const progress = (audio.currentTime / audio.duration) * 100;
        $(`#${playerId}-progress`).css('width', progress + '%');
        
        const current = Math.floor(audio.currentTime);
        const minutes = Math.floor(current / 60);
        const seconds = current % 60;
        
        $(`#${playerId}-current-time`).text(`${minutes}:${seconds.toString().padStart(2, '0')}`);
    };


    window.updateAudioProgress = function(playerId) {
        const audio = document.getElementById(playerId);
        const progress = (audio.currentTime / audio.duration) * 100;
        $(`#${playerId}-progress`).css('width', progress + '%');
        
        const minutes = Math.floor(audio.currentTime / 60);
        const seconds = Math.floor(audio.currentTime % 60);
        $(`#${playerId}-time`).text(`${minutes}:${seconds.toString().padStart(2, '0')}`);
    };

    window.seekAudio = function(event, playerId) {
        const audio = document.getElementById(playerId + '-audio');
        if (!audio || !audio.duration) return;
        
        const bar = $(`#${playerId} .audio-progress-container`);
        const clickX = event.offsetX || event.originalEvent.layerX;
        const width = bar.width();
        const percent = clickX / width;
        
        audio.currentTime = percent * audio.duration;
    };

    window.onAudioEnded = function(playerId) {
        const btn = $(`#${playerId} .audio-play-btn i`);
        btn.removeClass('fa-pause').addClass('fa-play');
        
        // Reset progress
        $(`#${playerId}-progress`).css('width', '0%');
        $(`#${playerId}-current-time`).text('0:00');
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
    // --- INIT EMOJI PICKER (Native Web Component) ---
    // messenger.js - Thay function initEmojiPicker()
    function initEmojiPicker() {
        const trigger = $('#emojiTrigger');
        const input = $('#msgInput');
        
        if (!trigger.length || !input.length) return;

        // Dùng emoji-picker-element (Web Component hiện đại)
        let picker = document.querySelector('emoji-picker');
        if (!picker) {
            picker = document.createElement('emoji-picker');
            picker.style.cssText = 'position:absolute; bottom:80px; right:20px; display:none; z-index:9999;';
            document.body.appendChild(picker);
        }

        trigger.on('click', (e) => {
            e.stopPropagation();
            picker.style.display = picker.style.display === 'none' ? 'block' : 'none';
        });

        picker.addEventListener('emoji-click', (e) => {
            input.val(input.val() + e.detail.unicode);
            input.focus();
        });

        $(document).on('click', (e) => {
            if (!picker.contains(e.target) && !trigger.is(e.target)) {
                picker.style.display = 'none';
            }
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
        recordingStartTime = null;
        
        // Clear timer
        if (recordingTimer) {
            clearInterval(recordingTimer);
            recordingTimer = null;
        }
        
        // Reset UI
        $('#recordingState').hide();
        $('#normalInputState').show();
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

    // Render Recent Stickers
    function renderRecentStickers() {
        const grid = $('#recentStickersGrid');
        if (!grid.length) return;
        
        let html = '';
        recentStickers.slice(0, 8).forEach((sticker, index) => {
            html += `
                <div class="sticker-item" onclick="sendSticker('${sticker.url}', 'recent', ${index})">
                    <img src="${sticker.url}" alt="Sticker">
                </div>
            `;
        });
        
        grid.html(html || '<div class="text-muted small">Chưa có sticker gần đây</div>');
    }

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

    // Show/Hide Sticker Suggestions
    function showStickerSuggestions(keywords) {
        const suggestions = findStickerSuggestions(keywords);
        
        if (suggestions.length === 0) {
            hideStickerSuggestions();
            return;
        }
        
        const container = $('#stickerSuggestions');
        const grid = $('#suggestionsGrid');
        
        grid.empty();
        
        suggestions.slice(0, 12).forEach(sticker => {
            const item = $(`
                <img src="${sticker.url}" class="suggestion-sticker" 
                    data-url="${sticker.url}" 
                    title="${sticker.tags.join(', ')}">
            `);
            
            item.on('click', function() {
                sendSticker(sticker.url);
                addToRecentStickers(sticker);
                hideStickerSuggestions();
                $('#msgInput').val('').focus();
            });
            
            grid.append(item);
        });
        
        container.addClass('show').css('display', 'flex');
    }

    function hideStickerSuggestions() {
        $('#stickerSuggestions').removeClass('show').hide();
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
        
        // Tạo HTML cho sticker menu
        const collections = Object.entries(window.STICKER_COLLECTIONS || {});
        const collectionsHtml = collections.map(([id, col]) => `
            <button class="collection-btn ${id === 'popular' ? 'active' : ''}" 
                    onclick="switchStickerCollection('${id}', this)">
                ${col.name}
            </button>
        `).join('');
        
        menu.html(`
            <div class="sticker-header">
                <div class="sticker-collections">${collectionsHtml}</div>
                <i class="fas fa-times close-sticker" onclick="window.toggleStickers()"></i>
            </div>
            <div class="sticker-search">
                <input type="text" id="stickerSearchInput" placeholder="Tìm kiếm sticker...">
                <i class="fas fa-search"></i>
            </div>
            <div class="sticker-grid" id="stickerGrid">
                <div class="text-center text-muted p-4">
                    <i class="fas fa-spinner fa-spin"></i> Đang tải...
                </div>
            </div>
            <div class="recent-stickers" id="recentStickersSection" style="display: none;">
                <div class="sticker-section-title">Gần đây</div>
                <div class="recent-stickers-grid" id="recentStickersGrid"></div>
            </div>
        `);
        
        // Load stickers
        setTimeout(() => {
            renderStickerCollection('popular');
            renderRecentStickers();
            
            // Search functionality
            $('#stickerSearchInput').on('input', function() {
                searchStickers($(this).val());
            });
        }, 100);
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
        // Có thể gọi thêm API lấy ảnh đã gửi để render vào .media-grid sau
    }

    // --- FIX 2: ONLINE STATUS UPDATE ---
    function updateOnlineStatus(partnerId, isOnline, lastActive) {
        const statusDiv = $('#chatHeaderStatus');
        
        if (isCurrentPartnerFriend) {
            if (isOnline) {
                statusDiv.html(`<small class="online-status"><i class="fas fa-circle"></i> Đang hoạt động</small>`);
            } else {
                statusDiv.html(`<small class="offline-status">${lastActive || 'Không hoạt động'}</small>`);
            }
        } else {
            statusDiv.empty();
        }

        // Update conversation list
        const convItem = $(`.conv-item[data-partner-id="${partnerId}"]`);
        const onlineDot = convItem.find('.online-dot');
        const lastActiveBadge = convItem.find('.last-active-badge');
        
        if (isOnline) {
            onlineDot.addClass('is-online');
            lastActiveBadge.remove();
        } else {
            onlineDot.removeClass('is-online');
            if (lastActive && lastActive !== 'Không hoạt động') {
                if (lastActiveBadge.length) {
                    lastActiveBadge.text(lastActive);
                } else {
                    convItem.find('.avatar-wrapper').append(`<div class="last-active-badge">${lastActive}</div>`);
                }
            }
        }
    }


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