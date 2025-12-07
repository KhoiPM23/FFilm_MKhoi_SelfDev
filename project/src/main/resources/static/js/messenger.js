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

    // --- 1. WEBSOCKET ---
    function connectWebSocket() {
        if(stompClient && stompClient.connected) return;

        var socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        stompClient.debug = null; 

        stompClient.connect({}, function (frame) {
            console.log('✅ WS Connected');
            stompClient.subscribe('/user/queue/private', function (payload) {
                var message = JSON.parse(payload.body);
                handleIncomingMessage(message);
            });
        }, function(error) {
            console.log('WS Error, reconnecting...', error);
            setTimeout(connectWebSocket, 5000);
        });
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
    function loadConversations() {
        $.get('/api/v1/messenger/conversations', function(data) {
            const list = $('#conversationList');
            list.empty();

            if (!data || data.length === 0) {
                list.html(`<div class="text-center mt-5 text-muted"><small>Chưa có tin nhắn nào.</small></div>`);
                if (typeof checkUrlAndOpenChat === 'function') checkUrlAndOpenChat([]);
                return;
            }

            data.forEach(c => {
                let activeClass = (c.partnerId == currentPartnerId) ? 'active' : '';
                let unreadClass = c.unreadCount > 0 ? 'unread' : '';
                let senderPrefix = c.lastMessageMine ? '<span class="prefix">Bạn: </span>' : '';
                let lastMsg = c.lastMessage || 'Hình ảnh/File';
                
                // Avatar fallback
                let avatarUrl = c.partnerAvatar;
                if(!avatarUrl || avatarUrl.includes('default')) {
                    avatarUrl = `https://ui-avatars.com/api/?name=${encodeURIComponent(c.partnerName)}&background=random&color=fff`;
                }

                // [FIX] Xử lý tham số an toàn cho onclick
                const safeName = c.partnerName.replace(/'/g, "\\'");
                const isFriendStr = (c.friend === true) ? 'true' : 'false';

                let html = `
                    <div class="conv-item ${activeClass} ${unreadClass}" id="conv-${c.partnerId}" 
                         onclick="window.selectConversation(${c.partnerId}, '${safeName}', '${avatarUrl}', ${isFriendStr})">
                        
                        <div class="avatar-wrapper">
                            <img src="${avatarUrl}" class="avatar-img">
                            <div class="online-dot ${c.online ? 'is-online' : ''}"></div>
                        </div>

                        <div class="conv-info">
                            <div class="conv-top-row">
                                <div class="conv-name">${c.partnerName}</div>
                                <span class="conv-time">${c.timeAgo || ''}</span>
                            </div>
                            <div class="conv-preview">
                                ${senderPrefix}${lastMsg}
                            </div>
                        </div>
                        ${c.unreadCount > 0 ? `<div class="unread-badge-dot"></div>` : ''}
                    </div>
                `;
                list.append(html);
            });
            
            // Check URL để mở chat người lạ (nếu có uid)
            checkUrlAndOpenChat(data);
        });
    }

    // --- 3. SELECT CONVERSATION ---
    window.selectConversation = function(partnerId, name, avatar, isFriend) {
        currentPartnerId = parseInt(partnerId);
        currentPartnerName = name;
        isCurrentPartnerFriend = (String(isFriend) === 'true'); // Convert string -> boolean

        // UI Update
        $('#emptyState').hide();
        $('#chatInterface').css('display', 'flex');
        
        $('#headerName').text(name);
        $('#headerAvatar').attr('src', avatar);
        
        $('.conv-item').removeClass('active');
        $(`#conv-${partnerId}`).addClass('active');

        // Logic Header Status (Người lạ/Bạn bè)
        const statusDiv = $('#chatHeaderStatus');
        if(statusDiv.length) {
            statusDiv.empty();
            if(!isCurrentPartnerFriend) {
                statusDiv.html(`
                    <span class="badge" style="background:#444; color:#ccc; margin-right:5px; font-size:11px;">Người lạ</span>
                    <button class="btn btn-sm btn-primary" onclick="window.sendFriendRequest(${partnerId}, this)" style="padding:2px 8px; font-size:11px;">Kết bạn</button>
                `);
            } else {
                statusDiv.html(`<small class="text-success">Đang hoạt động</small>`);
            }
        }

        loadChatHistory(partnerId);
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
            <div class="msg-row ${typeClass}">
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
        let content = $('#msgInput').val().trim();
        if (!content || !currentPartnerId) return;

        let payload = {
            receiverId: currentPartnerId,
            content: content,
            type: 'TEXT'
        };
        
        $('#msgInput').val('');
        sendApiRequest(payload);
    };

    window.sendSticker = function(url) {
        $('#stickerMenu').hide();
        if(!currentPartnerId) return;
        
        // Gửi type STICKER (nếu backend đã update) hoặc IMAGE
        let payload = { receiverId: currentPartnerId, content: url, type: 'STICKER' };
        sendApiRequest(payload);
    };

    function sendApiRequest(payload) {
        $.ajax({
            url: '/api/v1/messenger/send',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            success: function(msg) {
                appendMessageToUI(msg, true); // Force mine = true
                scrollToBottom();
            },
            error: function(e) { console.error("Send Error", e); }
        });
    }

    // Upload (Fix URL)
    function uploadFile(file, type) {
        if (!currentPartnerId) return alert("Chọn đoạn chat trước.");

        const formData = new FormData();
        formData.append("file", file);

        $('#messagesContainer').append(`<div id="uploading" class="text-center small text-muted">Đang gửi...</div>`);
        scrollToBottom();

        $.ajax({
            url: '/api/upload/image', // [FIX] URL đúng
            type: 'POST',
            data: formData,
            processData: false,
            contentType: false,
            success: function(res) {
                $('#uploading').remove();
                if(res.url) {
                    sendApiRequest({
                        receiverId: currentPartnerId,
                        content: res.url,
                        type: type // AUDIO hoặc IMAGE
                    });
                }
            },
            error: function() { $('#uploading').html('Lỗi upload'); }
        });
    }

    // Recording (Gán vào window)
    window.toggleRecording = function() {
        const btn = $('#recordBtn');
        if (!isRecording) {
            if (!navigator.mediaDevices) return alert("Trình duyệt không hỗ trợ");
            navigator.mediaDevices.getUserMedia({ audio: true }).then(stream => {
                mediaRecorder = new MediaRecorder(stream);
                mediaRecorder.start();
                isRecording = true;
                audioChunks = [];
                btn.addClass('fa-beat text-danger');
                $('#msgInput').attr('placeholder', 'Đang ghi âm...').prop('disabled', true);
                
                mediaRecorder.ondataavailable = e => audioChunks.push(e.data);
                mediaRecorder.onstop = () => {
                    const blob = new Blob(audioChunks, { type: 'audio/webm' });
                    uploadFile(blob, 'AUDIO');
                };
            }).catch(() => alert("Cần quyền Mic"));
        } else {
            if (mediaRecorder) mediaRecorder.stop();
            isRecording = false;
            btn.removeClass('fa-beat text-danger');
            $('#msgInput').attr('placeholder', 'Nhập tin nhắn...').prop('disabled', false).focus();
        }
    };

    window.toggleStickers = function() { $('#stickerMenu').toggle(); };

    function renderStickerMenu() {
        let html = '';
        STICKERS.forEach(url => {
            html += `<img src="${url}" class="sticker-item" onclick="window.sendSticker('${url}')">`;
        });
        $('#stickerMenu').html(html);
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

})();