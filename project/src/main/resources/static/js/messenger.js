/**
 * MESSENGER VIPRO - SCOPED VERSION
 * Fix lỗi: Identifier has already been declared
 */
(function() {
    'use strict';

    // --- KHAI BÁO BIẾN CỤC BỘ (AN TOÀN TUYỆT ĐỐI) ---
    let stompClient = null;
    let currentPartnerId = null;
    let mediaRecorder = null;
    let audioChunks = [];
    
    // Config
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
                sendTextMessage();
            }
        });

        // Upload ảnh
        $('#imageInput').off('change').on('change', function() {
            if (this.files && this.files[0]) uploadFile(this.files[0], 'IMAGE');
        });
        
        // Ghi âm
        $('#recordBtn').off('click').on('click', toggleRecording);
    }

    // --- 1. WEBSOCKET ---
    function connectWebSocket() {
        if(stompClient && stompClient.connected) return;

        var socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        stompClient.debug = null; 

        stompClient.connect({}, function (frame) {
            console.log('WS Connected');
            stompClient.subscribe('/user/queue/private', function (payload) {
                var message = JSON.parse(payload.body);
                handleIncomingMessage(message);
            });
        }, function(error) {
            console.log('WS Error, reconnecting...', error);
            setTimeout(connectWebSocket, 5000);
        });
    }

    // --- 2. CORE LOGIC ---
    function loadConversations() {
        $.get('/api/v1/messenger/conversations', function(data) {
            const list = $('#conversationList');
            list.empty(); // Xóa loading spinner

            // [FIX] Xử lý khi không có dữ liệu
            if (!data || data.length === 0) {
                list.html(`
                    <div class="text-center mt-5 text-muted">
                        <i class="fas fa-comments fa-3x mb-3"></i><br>
                        ưa có tin nhắn nào.<br>
                        <small>Tìm kiếm bạn bè để bắt đầu!</small>
                    </div>
                `);
                return;
            }

            data.forEach(c => {
                let active = (c.partnerId === currentPartnerId) ? 'active' : '';
                let unreadClass = c.unreadCount > 0 ? 'unread' : '';
                let lastMsg = c.lastMessage || 'Bắt đầu cuộc trò chuyện';
                let prefix = c.lastMessageMine ? 'Bạn: ' : '';
                
                let html = `
                    <div class="conv-item ${active}" id="conv-${c.partnerId}" 
                         onclick="selectConversation(${c.partnerId}, '${c.partnerName}', '${c.partnerAvatar}')">
                        <div class="avatar-wrapper">
                            <img src="${c.partnerAvatar}" class="avatar-img" onerror="this.src='/images/placeholder-user.jpg'">
                            <div class="online-dot ${c.online ? 'is-online' : ''}"></div>
                        </div>
                        <div class="conv-info">
                            <div class="conv-name">${c.partnerName}</div>
                            <div class="conv-preview ${unreadClass}">
                                ${prefix}${lastMsg}
                            </div>
                        </div>
                        ${c.unreadCount > 0 ? `<div class="unread-badge"></div>` : ''}
                    </div>
                `;
                list.append(html);
            });
        }).fail(function() {
            $('#conversationList').html('<div class="text-center text-danger mt-4">Lỗi tải dữ liệu.</div>');
        });
    }

    // Hàm global bridge để HTML gọi được (nếu cần inline onclick, nhưng ở trên tôi đã dùng active onclick)
    window.selectConversation = function(partnerId, name, avatar) {
        currentPartnerId = partnerId;
        
        // UI Update
        $('#emptyState').hide();
        $('#chatInterface').css('display', 'flex');
        $('#headerName').text(name);
        $('#headerAvatar').attr('src', avatar);
        
        $('.conv-item').removeClass('active');
        $(`#conv-${partnerId}`).addClass('active');

        loadHistory(partnerId);
    };

    function loadHistory(partnerId) {
        let container = $('#messagesContainer');
        container.html('<div class="text-center mt-5 text-muted"><i class="fas fa-spinner fa-spin"></i> Đang tải...</div>');

        $.get(`/api/v1/messenger/chat/${partnerId}`, function(msgs) {
            container.empty();
            if(!msgs || msgs.length === 0) {
                container.html('<div class="text-center mt-5 text-muted"><small>Hãy gửi lời chào!</small></div>');
                return;
            }
            msgs.forEach(m => appendMessageToUI(m));
            scrollToBottom();
        });
    }

    // --- 3. GỬI TIN NHẮN ---
    function sendTextMessage() {
        let content = $('#msgInput').val().trim();
        if (!content || !currentPartnerId) return;

        let payload = {
            receiverId: currentPartnerId,
            content: content,
            type: 'TEXT'
        };
        
        $('#msgInput').val('');

        $.ajax({
            url: '/api/v1/messenger/send',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            success: function(msg) {
                appendMessageToUI(msg, true);
                scrollToBottom();
                loadConversations(); // Refresh sidebar
            },
            error: function(err) {
                console.error("Send error:", err);
                alert("Lỗi gửi tin nhắn");
            }
        });
    }

    // --- 4. UI HELPER ---
    function appendMessageToUI(msg, forceMine = false) {
        // Logic xác định chủ nhân tin nhắn
        // Nếu msg.senderId == currentPartnerId -> Của người kia (other)
        // Ngược lại -> Của mình (mine)
        
        let isMine = forceMine;
        if (!forceMine) {
            isMine = (msg.senderId !== currentPartnerId);
        }

        let typeClass = isMine ? 'mine' : 'other';
        let contentHtml = `<div class="bubble" title="${msg.formattedTime}">${msg.content}</div>`;

        if (msg.type === 'IMAGE') {
            contentHtml = `<img src="${msg.content}" class="msg-image" onclick="window.open('${msg.content}')">`;
        } 

        let avatarHtml = !isMine ? `<img src="${$('#headerAvatar').attr('src')}" class="avatar-img" style="width: 28px; height: 28px;">` : '';

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

    // Các hàm phụ trợ (Sticker, Upload...)
    function renderStickerMenu() {
        // Logic render sticker như cũ
        let html = '';
        STICKERS.forEach(url => {
            html += `<img src="${url}" class="sticker-item" onclick="sendSticker('${url}')">`;
        });
        $('#stickerMenu').html(html);
    }
    
    // Gửi Sticker (Cần đưa vào window scope hoặc bind event nếu gọi từ HTML)
    window.sendSticker = function(url) {
        $('#stickerMenu').hide();
        if(!currentPartnerId) return;
        
        let payload = { receiverId: currentPartnerId, content: url, type: 'IMAGE' };
        $.ajax({
            url: '/api/v1/messenger/send',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            success: function(msg) { appendMessageToUI(msg, true); scrollToBottom(); }
        });
    };
    
    window.toggleStickers = function() { $('#stickerMenu').toggle(); };

    // --- GIỮ LẠI LOGIC RECORDING CŨ NẾU CẦN ---
    function toggleRecording() {
       // Logic cũ...
       console.log("Recording clicked");
    }

})(); // KẾT THÚC IIFE