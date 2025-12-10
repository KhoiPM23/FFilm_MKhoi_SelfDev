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

    let messageQueue = [];
    let isProcessingQueue = false;

    const currentUser = window.currentUser || { userID: 0, name: 'Me' };
    const notificationSound = new Audio('/sounds/message-notification.mp3');

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
        // Tạo PeerID ngẫu nhiên hoặc dựa trên UserID (nhưng PeerJS yêu cầu unique string)
        // Ta dùng UserID + timestamp để đảm bảo unique mỗi lần F5
        myPeerId = `user_${currentUser.userID}_${Date.now()}`;
        
        myPeer = new Peer(myPeerId, {
            // debug: 3, // Bật nếu cần debug
        });

        myPeer.on('open', (id) => {
            console.log('✅ PeerJS Connected. My ID:', id);
        });

        // Xử lý khi có người gọi đến (PeerJS signal)
        myPeer.on('call', (call) => {
            // Đây là bước 2 của luồng nhận cuộc gọi. 
            // Bước 1 là nhận Socket Message CALL_REQ để hiện popup.
            // Khi người dùng bấm "Trả lời", ta sẽ answer call này.
            
            // Lưu tạm call instance để xử lý sau khi user bấm Accept
            // Tuy nhiên, logic chuẩn: A gọi B -> B nhận Socket -> B Accept -> B gửi Socket Accept -> A gọi Peer -> B nhận Peer Call -> B answer.
            // Nên ở đây ta cứ answer nếu đã có trạng thái "Accepting".
            
            // Cách đơn giản nhất cho người dùng: 
            // A gửi Socket "Tao gọi mày nè, PeerID tao là X" -> B hiện Popup.
            // B bấm Nghe -> B gọi lại A (hoặc B chờ A gọi?).
            
            // CHUẨN:
            // 1. A gửi Socket CALL_REQ kèm A_PeerID.
            // 2. B nhận. Bấm Nghe.
            // 3. B lấy A_PeerID gọi A.
            
            // Hoặc:
            // 1. A gửi Socket.
            // 2. B nhận. Bấm Nghe.
            // 3. B gửi Socket CALL_ACCEPT kèm B_PeerID.
            // 4. A nhận. A gọi B.
            
            // Ta dùng cách: A gọi B (Peer) ngay lập tức? Không, phải chờ B online.
            // Chọn cách: A gửi Socket CALL_REQ (kèm PeerID).
            // B nhận -> Popup -> Bấm Nghe -> B gọi lại cho A qua PeerJS.
        });
        
        // Handle call error
        myPeer.on('error', (err) => console.error('Peer Error:', err));
    }

    // --- 2. LOGIC GỌI ĐIỆN (CALL LOGIC) ---

    // A. Người gọi (Caller)
    window.startVideoCall = function() {
        startCall('VIDEO');
    };

    window.startVoiceCall = function() {
        startCall('AUDIO');
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
        document.getElementById("incomingCallRingtone")?.pause(); // Tắt nhạc chuông nếu có

        if (!incomingCallData) return;

        // 1. Bật Camera/Mic của mình
        navigator.mediaDevices.getUserMedia({ video: true, audio: true })
            .then(stream => {
                localStream = stream;
                // Hiện UI Gọi
                showCallModal(true, "Đang kết nối...", stream);

                // 2. Gọi lại cho người kia bằng PeerID của họ (đã nhận từ Socket)
                const call = myPeer.call(incomingCallData.peerId, stream);
                handleCallStream(call);
            })
            .catch(err => {
                alert("Không thể truy cập Camera/Mic: " + err.message);
                rejectCall();
            });
    };

    window.rejectCall = function() {
        $('#incomingCallModal').hide();
        // Gửi tín hiệu từ chối
        if (incomingCallData) {
            sendApiRequest({
                receiverId: incomingCallData.senderId,
                content: "BUSY",
                type: 'CALL_DENY'
            });
        }
        incomingCallData = null;
    };

    window.endCall = function() {
        // Tắt stream
        if (localStream) localStream.getTracks().forEach(track => track.stop());
        if (currentCall) currentCall.close();
        
        // Gửi tín hiệu kết thúc
        if (currentPartnerId) {
            sendApiRequest({ receiverId: currentPartnerId, content: "END", type: 'CALL_END' });
        }
        
        closeCallModal();
    };

    // D. Helper xử lý Stream PeerJS
    function handleCallStream(call) {
        currentCall = call;
        
        // Khi nhận stream từ đối phương
        call.on('stream', (userVideoStream) => {
            remoteStream = userVideoStream;
            document.getElementById('remoteVideo').srcObject = userVideoStream;
            $('#callStatusText').text("Đang trong cuộc gọi");
            startCallTimer();
            $('.call-avatar-container').hide();
        });

        call.on('close', () => {
            endCall(); // Đóng UI khi kết thúc
        });
        
        call.on('error', (e) => {
            console.error(e);
            alert("Lỗi kết nối cuộc gọi");
            endCall();
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
        $('#incomingName').text(data.senderName);
        $('#incomingAvatar').attr('src', data.senderAvatar || '/images/placeholder-user.jpg');
        $('#incomingCallModal').show().css('display', 'flex'); // Flex để căn giữa
        // Play sound if needed
    }

    function showCallModal(isVideo, status, localStream) {
        $('#videoCallModal').show().css('display', 'flex');
        $('#callStatusText').text(status);
        if (localStream) {
            document.getElementById('localVideo').srcObject = localStream;
        }
        // Set avatar partner
        $('#callPartnerAvatar').attr('src', $('#headerAvatar').attr('src'));
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
        let sec = 0;
        clearInterval(callTimerInterval);
        callTimerInterval = setInterval(() => {
            sec++;
            let m = Math.floor(sec / 60).toString().padStart(2, '0');
            let s = (sec % 60).toString().padStart(2, '0');
            $('#callDuration').text(`${m}:${s}`);
        }, 1000);
    }
    function stopCallTimer() {
        clearInterval(callTimerInterval);
        $('#callDuration').text("00:00");
    }
    
    // Toggle Cam/Mic
    window.toggleCallMic = function() {
        if(localStream) {
            const track = localStream.getAudioTracks()[0];
            track.enabled = !track.enabled;
            $('#btnToggleMic').toggleClass('off');
        }
    }
    window.toggleCallCam = function() {
        if(localStream) {
            const track = localStream.getVideoTracks()[0];
            track.enabled = !track.enabled;
            $('#btnToggleCam').toggleClass('off');
        }
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

    window.toggleAudioPlay = function(playerId, audioUrl) {
        const audio = document.getElementById(playerId);
        const btn = $(`[data-audio-id="${playerId}"] .audio-play-btn i`);
        
        if (audio.paused) {
            audio.play();
            btn.removeClass('fa-play').addClass('fa-pause');
        } else {
            audio.pause();
            btn.removeClass('fa-pause').addClass('fa-play');
        }
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
        const audio = document.getElementById(playerId);
        const bar = event.currentTarget;
        const clickX = event.offsetX;
        const width = bar.offsetWidth;
        const seekTime = (clickX / width) * audio.duration;
        audio.currentTime = seekTime;
    };

    window.onAudioEnded = function(playerId) {
        const btn = $(`[data-audio-id="${playerId}"] .audio-play-btn i`);
        btn.removeClass('fa-pause').addClass('fa-play');
        $(`#${playerId}-progress`).css('width', '0%');
        const audio = document.getElementById(playerId);
        $(`#${playerId}-time`).text('0:00');
    };

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
        if (!navigator.mediaDevices) return alert("Trình duyệt không hỗ trợ ghi âm");
        
        navigator.mediaDevices.getUserMedia({ audio: true }).then(stream => {
            // 1. Setup Recorder
            mediaRecorder = new MediaRecorder(stream);
            audioChunks = [];
            mediaRecorder.ondataavailable = e => audioChunks.push(e.data);
            mediaRecorder.start();
            isRecording = true;

            // 2. Chuyển đổi UI
            $('#normalInputState').hide();
            $('#recordingState').css('display', 'flex'); // Hiện thanh ghi âm
            
            // 3. Chạy đồng hồ đếm giờ
            recordStartTime = Date.now();
            $('#recordTimer').text("00:00");
            recordTimerInterval = setInterval(() => {
                const diff = Math.floor((Date.now() - recordStartTime) / 1000);
                const mm = Math.floor(diff / 60).toString().padStart(2, '0');
                const ss = (diff % 60).toString().padStart(2, '0');
                $('#recordTimer').text(`${mm}:${ss}`);
            }, 1000);

        }).catch(err => {
            console.error(err);
            alert("Không thể truy cập Microphone. Vui lòng kiểm tra quyền.");
        });
    };

    // Hủy ghi âm: Dừng Recorder (không lưu), Reset UI
    window.cancelRecording = function() {
        if(mediaRecorder) {
            mediaRecorder.onstop = null; // Hủy sự kiện gửi
            mediaRecorder.stop();
        }
        closeRecordingUI();
    };

    // Hoàn tất & Gửi: Dừng Recorder -> Trigger onstop -> Upload
    window.finishRecording = function() {
        if(mediaRecorder) mediaRecorder.stop(); // Trigger onstop -> Gửi
    };

    function closeRecordingUI() {
        isRecording = false;
        clearInterval(timerInterval);
        $('.recording-ui').removeClass('show').hide();
        $('.input-actions').show();
    }

    function resetRecordingUI() {
        isRecording = false;
        // Xóa interval ngay lập tức
        if (timerInterval) {
            clearInterval(timerInterval);
            timerInterval = null;
        }
        $('#recordTimer').text("00:00"); // Reset text
        
        $('#recordingState').removeClass('show').hide();
        $('#normalInputState').show();
        
        if(mediaRecorder && mediaRecorder.stream) {
            mediaRecorder.stream.getTracks().forEach(t => t.stop());
        }
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
        const collection = STICKER_COLLECTIONS[collectionId];
        
        if (!collection) return;
        
        grid.empty();
        
        collection.items.forEach(sticker => {
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
    }

    // Render Recent Stickers
    function renderRecentStickers() {
        const recentGrid = $('.recent-stickers-grid');
        if (!recentGrid.length) return;
        
        recentGrid.empty();
        
        recentStickers.slice(0, 8).forEach(sticker => {
            const item = $(`
                <div class="sticker-item" data-sticker-id="${sticker.id}" data-url="${sticker.url}">
                    <img src="${sticker.url}" alt="Sticker" style="width: 100%; height: 100%;">
                </div>
            `);
            
            item.on('click', function() {
                sendSticker(sticker.url);
                addToRecentStickers(sticker);
            });
            
            recentGrid.append(item);
        });
    }

    // Add to Recent Stickers
    function addToRecentStickers(sticker) {
        // Remove if already exists
        recentStickers = recentStickers.filter(s => s.id !== sticker.id);
        
        // Add to beginning
        recentStickers.unshift(sticker);
        
        // Keep only last 20
        recentStickers = recentStickers.slice(0, 20);
        
        // Save to localStorage
        localStorage.setItem('recentStickers', JSON.stringify(recentStickers));
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
        if (!window.STICKER_COLLECTIONS) {
            console.error("STICKER_COLLECTIONS not loaded");
            return;
        }
        
        const menu = $('#stickerMenu');
        if (menu.find('.sticker-collections').length > 0) return; // Already init

        const collectionsHtml = Object.entries(window.STICKER_COLLECTIONS)
            .map(([id, col]) => `<button class="collection-btn ${id === 'popular' ? 'active' : ''}" onclick="window.switchStickerCollection('${id}', this)">${col.name}</button>`)
            .join('');

        menu.html(`
            <div class="sticker-header">
                <div class="sticker-tabs">
                    <button class="tab-btn active">Stickers</button>
                </div>
                <i class="fas fa-times close-sticker" onclick="window.toggleStickers()"></i>
            </div>
            <div class="sticker-search">
                <input type="text" placeholder="Tìm kiếm..." onkeyup="window.searchStickers(this.value)">
                <i class="fas fa-search"></i>
            </div>
            <div class="sticker-collections">${collectionsHtml}</div>
            <div class="sticker-grid" id="stickerGrid"></div>
        `);

        renderStickerCollection('popular');
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
    window.sendSticker = function(url, type = 'STICKER') {
        if (!currentPartnerId) return;
        
        // Close menus
        $('#stickerMenu').removeClass('show').hide();
        hideStickerSuggestions();
        
        // Send to server
        sendApiRequest({ 
            receiverId: currentPartnerId, 
            content: url, 
            type: type 
        });
        
        // Show immediately on UI
        const fakeMsg = { 
            senderId: currentUser.userID, 
            content: url, 
            type: type,
            formattedTime: 'Vừa xong'
        };
        appendMessageToUI(fakeMsg, true);
    };

    // --- 6. URL CHECK (NGƯỜI LẠ) ---
    // messenger.js - checkUrlAndOpenChat()
    function checkUrlAndOpenChat(existingConversations) {
        const urlParams = new URLSearchParams(window.location.search);
        const uid = urlParams.get('uid');
        if(!uid) return;
        
        const targetId = parseInt(uid);
        const existing = existingConversations.find(c => c.partnerId === targetId);

        if(existing) {
            window.selectConversation(existing.partnerId, existing.partnerName, existing.partnerAvatar, existing.friend);
        } else {
            $.get(`/api/users/${targetId}`).done(function(u) {
                const avatar = `https://ui-avatars.com/api/?name=${encodeURIComponent(u.userName)}`;
                window.selectConversation(u.userID, u.userName, avatar, false);
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