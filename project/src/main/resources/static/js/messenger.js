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

    const currentUser = window.currentUser || { userID: 0, name: 'Me' };

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
        initPeerJS();
        initStickerMenu();
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
    // --- 3. SOCKET HANDLER (UPDATED) ---
    function connectWebSocket() {
        const socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        stompClient.debug = null;
        stompClient.connect({}, function() {
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

            stompClient.subscribe('/user/queue/private', function(payload) {
                const msg = JSON.parse(payload.body);
                handleSocketMessage(msg);
            });
        });
    }

    function handleSocketMessage(msg) {
        // 1. Xử lý Tín hiệu Gọi
        if (msg.type === 'CALL_REQ') {
            // Người khác gọi mình
            if (isRecording || currentCall) {
                // Đang bận -> Tự từ chối (Optional)
                return;
            }
            incomingCallData = { 
                peerId: msg.content, // PeerID của người gọi
                senderId: msg.senderId,
                senderName: msg.senderName || 'Người dùng FFilm', // Cần Backend trả về senderName trong MessageDto
                senderAvatar: msg.senderAvatar 
            };
            showIncomingCallModal(incomingCallData);
            return; // Không hiện tin nhắn chat
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

        // 2. Xử lý Chat thường (Text, Image, Audio)
        if (currentPartnerId && (msg.senderId == currentPartnerId || msg.senderId == currentUser.userID)) {
            appendMessageToUI(msg);
        }
        loadConversations();
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

    function handleIncomingMessage(message) {
        // Logic cũ: Nếu đang chat với người đó thì append
        if (currentPartnerId && (message.senderId == currentPartnerId || message.receiverId == currentPartnerId)) {
            appendMessageToUI(message); // Không forceMine để nó tự tính toán
            scrollToBottom();
        }
        loadConversations();
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
                    strangerBadge = `<span class="badge-stranger-icon" title="Người lạ">👤</span>`;
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
                        
                        ${c.unreadCount > 0 ? `<div class="badge bg-primary rounded-pill ms-2">${c.unreadCount}</div>` : ''}
                    </div>
                `);
            });
            checkUrlAndOpenChat(data);
        });
    }

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
        // [LOGIC CŨ] Xác định mine/other dựa trên so sánh với partnerId
        // Nếu người gửi KHÔNG PHẢI partner -> Thì là Mình. (Logic này hoạt động tốt cho chat 1-1)
        const myId = parseInt(currentUser.userID);
        const senderId = parseInt(msg.senderId);
        let isMine = forceMine;
        if (!forceMine) {
            // So sánh lỏng (==) để tránh lỗi string/int
            isMine = (msg.senderId != currentPartnerId);
        }

        const typeClass = isMine ? 'mine' : 'other';
        const msgId = msg.id || 'temp-' + Date.now();
        
        // Xử lý nội dung (Media)
        let contentHtml = '';
        // 1. Xử lý Ảnh & Sticker
        if (msg.type === 'IMAGE' || msg.type === 'STICKER') {
            const imgClass = msg.type === 'STICKER' ? 'sticker-img' : 'msg-image';
            contentHtml = `<img src="${msg.content}" class="${imgClass}" onclick="window.open('${msg.content}')" style="max-width:200px; border-radius:10px; cursor:pointer;">`;
        } 
        // 2. Xử lý Audio (Ghi âm)
        else if (msg.type === 'AUDIO') {
            contentHtml = `
                <audio controls style="height:32px; max-width:240px; margin-top:5px;">
                    <source src="${msg.content}" type="audio/webm">
                    <source src="${msg.content}" type="audio/mp3">
                    File ghi âm
                </audio>`;
        }
        // 3. [FIX] Xử lý File đính kèm (Hiện ra text link)
        else if (msg.type === 'FILE') {
            // Tách tên file từ URL (nếu có)
            const fileName = decodeURIComponent(msg.content.split('/').pop()); // ← THÊM decode
            const safeUrl = msg.content.replace(/ /g, '%20'); // ← Encode space
            contentHtml = `
                <div class="msg-file" style="display:flex; align-items:center; gap:10px; background:rgba(0,0,0,0.2); padding:5px 10px; border-radius:8px;">
                    <i class="fas fa-file-alt fa-2x"></i>
                    <div>
                        <div style="font-size:12px; font-weight:bold;">${fileName}</div>
                        <a href="${safeUrl}" target="_blank" style="color:#0084ff; font-size:11px; text-decoration:underline;">Tải xuống</a>
                    </div>
                </div>`;
        }
        // 4. Text thường
        else {
            contentHtml = `<div class="bubble" title="${msg.formattedTime || ''}">${msg.content}</div>`;
        }

        // // --- 1. Xử lý Nội dung (Đã xóa / Media) ---
        // if (msg.isDeleted) {
        //     contentHtml = 'Tin nhắn đã bị thu hồi';
        // } else if (msg.type === 'IMAGE' || msg.type === 'STICKER') {
        //      const cls = (msg.type === 'STICKER') ? 'sticker-img' : 'msg-image';
        //      contentHtml = `<img src="${msg.content}" class="${cls}" style="max-width:200px; border-radius:10px; cursor:pointer;" onclick="window.open('${msg.content}')">`;
        // } else if (msg.type === 'AUDIO') {
        //     contentHtml = `<audio controls style="height:30px;"><source src="${msg.content}" type="audio/webm"></audio>`;
        // } else {
        //     contentHtml = msg.content;
        // }

        // // --- 2. Xử lý Reply Block (Nếu tin này đang trả lời tin khác) ---
        // let replyHtml = '';
        // if (msg.replyTo) {
        //     const rName = (msg.replyTo.senderId === myId) ? 'Bạn' : currentPartnerName;
        //     let rContent = msg.replyTo.type === 'TEXT' ? msg.replyTo.content : '[Đính kèm]';
        //     replyHtml = `
        //         <div class="reply-block" onclick="scrollToMessage(${msg.replyTo.id})">
        //             <div class="reply-name">Đang trả lời ${rName}</div>
        //             <div class="text-truncate">${rContent}</div>
        //         </div>
        //     `;
        // }

        // --- 3. Xử lý Menu Actions (Hover) ---
        // Nút Unsend chỉ hiện cho tin của mình và chưa xóa
        const unsendBtn = (isMine && !msg.isDeleted) 
            ? `<div class="action-btn" onclick="window.unsendMessage(${msgId})" title="Thu hồi"><i class="fas fa-trash"></i></div>` 
            : '';
        
        const actionsHtml = `
            <div class="msg-actions">
                <div class="action-btn" onclick="window.startReply(${msgId}, '${isMine ? 'Bạn' : currentPartnerName}', '${msg.type === 'TEXT' ? msg.content.substring(0,20) : '[File]' }')" title="Trả lời"><i class="fas fa-reply"></i></div>
                ${unsendBtn}
            </div>
        `;

        // // Avatar
        // const avatarUrl = $('#headerAvatar').attr('src');
        // const avatarHtml = isMine ? '' : `<img src="${avatarUrl}" class="msg-avatar" style="width:28px; height:28px; border-radius:50%; margin-right:8px;">`;
        // const bubbleClass = msg.isDeleted ? 'msg-content deleted' : 'msg-content';
        // const bubbleStyle = msg.isDeleted ? '' : (isMine ? 'background:#0084ff; color:#fff;' : 'background:#3e4042; color:#eee;');

        // const html = `
        //     <div class="msg-row ${typeClass}" id="msg-${msgId}" style="display:flex; margin-bottom:10px; ${isMine ? 'justify-content:flex-end' : ''}">
        //         ${isMine ? actionsHtml : avatarHtml} 
                
        //         <div class="${bubbleClass}" style="padding:8px 12px; border-radius:15px; max-width:70%; ${bubbleStyle}">
        //             ${replyHtml}
        //             ${contentHtml}
        //         </div>
                
        //         ${!isMine ? actionsHtml : ''}
        //     </div>
        // `;
        // $('#messagesContainer').append(html);
        // scrollToBottom();

        // Avatar (Chỉ hiện cho 'other')
        let avatarHtml = !isMine ? `<img src="${$('#headerAvatar').attr('src')}" class="avatar-img" style="width: 28px; height: 28px;">` : '';

        // [CẤU TRÚC HTML CHUẨN CŨ]
        let html = `
            <div class="msg-row ${typeClass}" data-msg-id="${msg.id || Date.now()}">
            ${isMine ? actionsHtml : avatarHtml} 
                <div class="msg-content">${contentHtml}</div>

                ${!isMine ? actionsHtml : ''}
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
        $.ajax({
            url: '/api/v1/messenger/send',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            success: function(msg) {
                // appendMessageToUI(msg, true); // Force mine = true
                console.log("sendApiRequest success:", msg);
                scrollToBottom();
            },
            error: function(e) { console.error("Send Error", e); }
        });
    }

    // Upload (Fix URL)
    function uploadAndSend(file, type, caption) {
        const formData = new FormData();
        formData.append("file", file);

        // 1. Tạo Preview Base64 ngay lập tức (Optimistic UI)
        const reader = new FileReader();
        reader.onload = function(e) {
            const base64Url = e.target.result;
            // Hiện ngay tin nhắn ảnh với base64 (không sợ 404)
            const fakeMsg = { 
                senderId: currentUser.userID, 
                content: base64Url, // Dùng base64 để hiện ngay
                type: type,
                formattedTime: 'Đang gửi...'
            };
            // appendMessageToUI(fakeMsg, true);
            // scrollToBottom();
        };
        reader.readAsDataURL(file);

        // 2. Clear Input
        window.clearPreview();
        $('#msgInput').val('');

        // Thêm loading
        const tempId = 'up-' + Date.now();
        $('#messagesContainer').append(`<div id="${tempId}" class="text-center small text-muted">Đang tải lên...</div>`);
        scrollToBottom();

        // 3. Upload thật
        $.ajax({
            url: '/api/upload/image', 
            type: 'POST',
            data: formData,
            processData: false,
            contentType: false,
            success: function(res) {
                $(`#${tempId}`).remove(); // Xóa loading
                if(res.url) {
                    // Gửi tin nhắn chứa URL Server (để người kia xem được)
                    sendApiRequest({ 
                        receiverId: currentPartnerId, 
                        content: res.url, 
                        type: type // AUDIO, IMAGE, FILE
                    });

                    // 2. Hiện ngay lên UI của mình
                    appendMessageToUI({ 
                         senderId: currentUser.userID, 
                         content: res.url, 
                         type: type 
                    }, true);
                    
                    // Gửi caption nếu có
                    if(caption) {
                        sendApiRequest({ receiverId: currentPartnerId, content: caption, type: 'TEXT' });
                        appendMessageToUI({ senderId: currentUser.userID, content: caption, type: 'TEXT' }, true);
                    }

                    // [QUAN TRỌNG] Reset mọi thứ sau khi gửi xong
                    window.clearPreview(); 
                    $('#msgInput').val('');
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
        if (!window.STICKER_COLLECTIONS) return;
        const menu = $('#stickerMenu');
        
        if (menu.find('.sticker-collections').length === 0) {
            const html = `
                <div class="sticker-header">
                    <div class="sticker-tabs">
                        <button class="tab-btn active" onclick="window.switchStickerTab('stickers')">
                            <i class="fas fa-sticky-note"></i> Stickers
                        </button>
                        <button class="tab-btn" onclick="window.switchStickerTab('gifs')">
                            <i class="fas fa-film"></i> GIFs
                        </button>
                    </div>
                    <i class="fas fa-times close-sticker" onclick="window.toggleStickers()"></i>
                </div>
                
                <div class="sticker-search">
                    <input type="text" id="stickerSearchInput" 
                        placeholder="Tìm kiếm stickers..." 
                        onkeyup="window.searchStickers(this.value)">
                    <i class="fas fa-search"></i>
                </div>
                
                <div class="sticker-collections">
                    ${Object.entries(STICKER_COLLECTIONS).map(([id, collection]) => `
                        <button class="collection-btn ${id === 'popular' ? 'active' : ''}" 
                                onclick="window.switchStickerCollection('${id}', this)">
                            ${collection.name}
                        </button>
                    `).join('')}
                </div>
                
                ${recentStickers.length > 0 ? `
                <div class="recent-stickers">
                    <h4><i class="fas fa-history"></i> Gần đây</h4>
                    <div class="recent-stickers-grid"></div>
                </div>
                ` : ''}
                
                <div class="sticker-grid-container">
                    <div id="stickerGrid" class="sticker-grid"></div>
                </div>
            `;
            
            menu.html(html);
        }
    }

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

    // Initialize in document ready
    // $(document).ready(function() {
    //     initStickerMenu();
    //     setupStickerSuggestions();
    //     renderRecentStickers();
    // });

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
    let replyToId = null; // Biến lưu trạng thái đang reply

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
        if (pendingFile) {
            uploadAndSend(pendingFile.file, pendingFile.type, $('#msgInput').val().trim());
            return;
        }
        const input = $('#msgInput');
        const content = input.val().trim();
        if (!content || !currentPartnerId) return;

        // Payload có thêm replyToId
        const payload = { 
            receiverId: currentPartnerId, 
            content: content, 
            type: 'TEXT',
            replyToId: replyToId // [MỚI]
        };
        
        // Reset Input & Reply
        input.val('');
        window.cancelReply();

        sendApiRequest(payload);
        
        // Optimistic UI (Cần xử lý kỹ hơn cho Reply, tạm thời chờ server trả về để render đúng context)
        // Nếu muốn hiện ngay: cần truyền object replyTo vào appendMessageToUI
    };




    // --- 9. SIDEBAR INFO LOGIC ---

    // Toggle Sidebar
    window.toggleChatInfo = function() {
        const sidebar = $('#chatInfoSidebar');
        const btn = $('#btnToggleInfo');
        
        if (sidebar.hasClass('hidden')) {
            sidebar.removeClass('hidden');
            btn.addClass('active');
        } else {
            sidebar.addClass('hidden');
            btn.removeClass('active');
        }
    };

    // Update Info Sidebar khi chọn hội thoại
    function updateInfoSidebar(name, avatar) {
        $('#infoName').text(name);
        $('#infoAvatar').attr('src', avatar);
        // Có thể gọi thêm API lấy ảnh đã gửi để render vào .media-grid sau
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