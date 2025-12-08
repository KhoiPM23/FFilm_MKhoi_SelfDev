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
        renderStickerMenu();
        bindEvents();
        initPeerJS();
        initEmojiPicker();
    });

    function bindEvents() {
        // Gửi tin bằng Enter
        $('#msgInput').off('keypress').on('keypress', function(e) {
            if (e.which === 13 && !e.shiftKey) {
                e.preventDefault();
                window.sendTextMessage();
            }
        });

        // Upload ảnh
        $('#imageInput').off('change').on('change', function() {
            if (this.files && this.files[0]) uploadFile(this.files[0], 'IMAGE');
        });
        
        // Ghi âm (Gán sự kiện click)
        $('#recordBtn').parent().off('click').on('click', window.toggleRecording);
        
        // Sticker Toggle
        $('.fa-sticky-note').parent().off('click').on('click', window.toggleStickers);
        
        // Nút gửi
        $('.fa-paper-plane').parent().off('click').on('click', window.sendTextMessage);
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

            if (!data || data.length === 0) {
                list.html(`<div class="text-center mt-5 text-muted"><small>Chưa có tin nhắn nào.</small></div>`);
                if(typeof checkUrlAndOpenChat === 'function') checkUrlAndOpenChat([]);
                return;
            }

            data.forEach(c => {
                const isActive = (c.partnerId == currentPartnerId) ? 'active' : '';
                const isUnread = c.unreadCount > 0 ? 'unread' : '';
                
                // Avatar Fallback
                let avatar = c.partnerAvatar;
                if (!avatar || avatar.includes('default')) {
                    avatar = `https://ui-avatars.com/api/?name=${encodeURIComponent(c.partnerName)}&background=random&color=fff`;
                }

                // [FIX] Convert dữ liệu an toàn để truyền vào onclick
                const isFriendStr = (c.friend === true) ? 'true' : 'false';
                const isOnlineStr = (c.isOnline === true) ? 'true' : 'false'; // [MỚI]
                const lastActiveStr = c.lastActive || ''; // [MỚI] (Nếu backend chưa có thì để rỗng)
                const safeName = c.partnerName.replace(/'/g, "\\'");

                const html = `
                    <div class="conv-item ${isActive} ${isUnread}" id="conv-${c.partnerId}" 
                         onclick="window.selectConversation(${c.partnerId}, '${safeName}', '${avatar}', '${isFriendStr}', '${isOnlineStr}', '${lastActiveStr}')">
                        
                        <div class="avatar-wrapper">
                            <img src="${avatar}" class="avatar-img" onerror="this.src='https://ui-avatars.com/api/?name=User&background=random'">
                            <div class="online-dot ${c.isOnline ? 'is-online' : ''}"></div>
                        </div>

                        <div class="conv-info">
                            <div class="conv-top-row">
                                <div class="conv-name">${c.partnerName}</div>
                                <span class="conv-time">${c.timeAgo || ''}</span>
                            </div>
                            <div class="conv-preview">
                                ${c.lastMessageMine ? 'Bạn: ' : ''}${c.lastMessage || 'Hình ảnh'}
                            </div>
                        </div>
                        ${c.unreadCount > 0 ? `<div class="unread-badge-dot"></div>` : ''}
                    </div>
                `;
                list.append(html);
            });

            if(typeof checkUrlAndOpenChat === 'function') checkUrlAndOpenChat(data);
        });
    }

    // --- 3. SELECT CONVERSATION ---
    window.selectConversation = function(partnerId, name, avatar, isFriend, isOnline, lastActive) {
        currentPartnerId = parseInt(partnerId);
        currentPartnerName = name;
        // Fix lỗi so sánh chuỗi "true"/"false"
        isCurrentPartnerFriend = (String(isFriend) === 'true');

        console.log("Check Friend:", name, isFriend, "->", isCurrentPartnerFriend);

        // UI Reset
        $('#emptyState').hide();
        $('#chatInterface').css('display', 'flex');
        
        // 1. Header Info
        $('#headerName').text(name);
        $('#headerAvatar').attr('src', avatar);

        // 2. Xử lý Trạng thái Online (Xanh lá / Phút trước)
        const statusDiv = $('#chatHeaderStatus');
        statusDiv.empty();

        // 3. Xử lý Banner Người Lạ (Zalo Style) - Nằm DƯỚI header, TRÊN message list
        $('#strangerBanner').remove(); // Xóa banner cũ nếu có
        if (!isCurrentPartnerFriend) {
            const bannerHtml = `
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
            // Chèn vào đầu khung chat
            $('#messagesContainer').before(bannerHtml);
        }

        else {
            // Ưu tiên 2: Nếu là bạn bè -> Hiện Status (Online hoặc Last Active)
            if (String(isOnline) === 'true') {
                statusDiv.html(`<small class="text-success fw-bold"><i class="fas fa-circle" style="font-size:8px;"></i> Đang hoạt động</small>`);
            } else {
                // Nếu có lastActive thì hiện, không thì hiện Offline
                const statusText = lastActive ? `Hoạt động ${lastActive}` : 'Không hoạt động';
                statusDiv.html(`<small class="text-muted">${statusText}</small>`);
            }
        }

        // Highlight Sidebar
        $('.conv-item').removeClass('active');
        $(`#conv-${partnerId}`).addClass('active');

        // Load History
        loadChatHistory(partnerId);
        
        // Mobile Support
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
        let isMine = forceMine;
        if (!forceMine) {
            // So sánh lỏng (==) để tránh lỗi string/int
            isMine = (msg.senderId != currentPartnerId);
        }

        let typeClass = isMine ? 'mine' : 'other';
        
        // Xử lý nội dung (Media)
        let contentHtml = '';
        if (msg.type === 'IMAGE' || msg.type === 'STICKER') {
            const imgClass = msg.type === 'STICKER' ? 'sticker-img' : 'msg-image';
            contentHtml = `<img src="${msg.content}" class="${imgClass}" onclick="window.open('${msg.content}')" style="max-width:200px; border-radius:10px; cursor:pointer;">`;
        } 
        else if (msg.type === 'AUDIO') {
            contentHtml = `<audio controls style="height:30px; max-width:220px;"><source src="${msg.content}" type="audio/webm"></audio>`;
        }
        else {
            contentHtml = `<div class="bubble" title="${msg.formattedTime || ''}">${msg.content}</div>`;
        }

        // Avatar (Chỉ hiện cho 'other')
        let avatarHtml = !isMine ? `<img src="${$('#headerAvatar').attr('src')}" class="avatar-img" style="width: 28px; height: 28px;">` : '';

        // [CẤU TRÚC HTML CHUẨN CŨ]
        let html = `
            <div class="msg-row ${typeClass}" data-msg-id="${msg.id || Date.now()}">
                ${avatarHtml}
                <div class="msg-content">${contentHtml}</div>
            </div>
        `;
        $('#messagesContainer').append(html);
    }

    function scrollToBottom() {
        let d = $('#messagesContainer');
        d.scrollTop(d[0].scrollHeight);
    }

    // --- 5. ACTIONS ---

    // Gán vào window để HTML gọi được
    window.sendTextMessage = function() {
        const content = $('#msgInput').val().trim();

        // Ưu tiên 1: Nếu có file đang chờ (Preview) -> Upload -> Gửi
        if (pendingFile) {
            uploadAndSend(pendingFile.file, pendingFile.type, content); // content là caption
            return;
        }

        // Ưu tiên 2: Gửi text thường
        if (content && currentPartnerId) {
            // Optimistic UI: Hiện ngay lập tức
            appendMessageToUI({
                senderId: currentUser.userID,
                content: content,
                type: 'TEXT',
                status: 'SENDING',
                formattedTime: 'Vừa xong'
            }, true);

            sendApiRequest({ receiverId: currentPartnerId, content: content, type: 'TEXT' });
            $('#msgInput').val('');
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
            appendMessageToUI(fakeMsg, true);
            scrollToBottom();
        };
        reader.readAsDataURL(file);

        // 2. Clear Input
        window.clearPreview();
        $('#msgInput').val('');

        // 3. Upload thật
        $.ajax({
            url: '/api/upload/image', 
            type: 'POST',
            data: formData,
            processData: false,
            contentType: false,
            success: function(res) {
                if(res.url) {
                    // Gửi tin nhắn chứa URL Server (để người kia xem được)
                    sendApiRequest({ 
                        receiverId: currentPartnerId, 
                        content: res.url, 
                        type: type 
                    });
                    
                    // Gửi caption nếu có
                    if(caption) {
                        sendApiRequest({ receiverId: currentPartnerId, content: caption, type: 'TEXT' });
                        appendMessageToUI({ senderId: currentUser.userID, content: caption, type: 'TEXT' }, true);
                    }
                }
            },
            error: function(e) { 
                console.error("Upload fail:", e);
                // Có thể thêm logic hiện icon lỗi tại tin nhắn vừa append
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

    // --- 2. CÁC HÀM KHÁC (Giữ nguyên hoặc cập nhật sự kiện input) ---
    
    // Khi gõ text -> Có thể ẩn nút Mic hiện nút Gửi (Logic Messenger)
    // Tạm thời ta để cả 2 nút như thiết kế HTML mới.
    
    // --- 1. STICKER TOGGLE (Fix tự bung) ---
    window.toggleStickers = function() {
        const menu = $('#stickerMenu');
        if (menu.hasClass('show')) {
            menu.removeClass('show').hide();
        } else {
            menu.addClass('show').css('display', 'flex');
        }
    };

    window.sendSticker = function(url) {
        $('#stickerMenu').hide();
        if(!currentPartnerId) return;
        sendApiRequest({ receiverId: currentPartnerId, content: url, type: 'IMAGE' }); // Dùng IMAGE tạm
    };

    function renderStickerMenu() {
        let html = '';
        STICKERS.forEach(url => html += `<img src="${url}" class="sticker-item" onclick="window.sendSticker('${url}')">`);
        $('#stickerMenu').html(html);
    }

    // Hàm chọn Emoji (Placeholder - Phase sau sẽ tích hợp thư viện)
    window.toggleEmojiPicker = function() {
        const input = $('#msgInput');
        const currentVal = input.val();
        input.val(currentVal + "😊"); // Tạm thời chèn hardcode, sau này gắn lib
        input.focus();
    };

    function initEmojiPicker() {
        if (typeof EmojiButton !== 'undefined') {
            emojiPicker = new EmojiButton({
                theme: 'dark',
                position: 'bottom-end', // ← ĐỔI POSITION
                emojiSize: '1.8em'
            });

            emojiPicker.on('emoji', selection => {
                $('#msgInput').val($('#msgInput').val() + selection.emoji).focus();
            });

            const trigger = document.querySelector('#emojiTrigger');
            if(trigger) {
                trigger.addEventListener('click', (e) => {
                    e.stopPropagation(); // ← THÊM DÒNG NÀY
                    emojiPicker.togglePicker(trigger);
                });
            }
        }
    }

    // --- 6. URL CHECK (NGƯỜI LẠ) ---
    function checkUrlAndOpenChat(existingConversations) {
        const urlParams = new URLSearchParams(window.location.search);
        const uid = urlParams.get('uid');
        if(!uid) return;
        
        const targetId = parseInt(uid);
        const existing = existingConversations.find(c => c.partnerId === targetId);

        if(existing) {
            $(`#conv-${targetId}`).click();
        } else {
            // Fetch info & Open Temp Chat
            $.get(`/api/users/${targetId}`).done(function(u) {
                const avatar = `https://ui-avatars.com/api/?name=${encodeURIComponent(u.userName)}&background=random&color=fff`;
                window.selectConversation(u.userId, u.userName, avatar, 'false');
            });
        }
    }

    // Events Listener
    // $(document).on('click', '.emoji-btn', function() {
    //     const input = $('#msgInput');
    //     input.val(input.val() + "😊");
    //     input.focus();
    // });

})();