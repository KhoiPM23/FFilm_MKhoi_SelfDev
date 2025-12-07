/**
 * MESSENGER VIPRO - ULTIMATE EDITION
 * Đầy đủ tính năng: Real-time, Chat người lạ, Ghi âm, Sticker, Upload ảnh.
 */
(function() {
    'use strict';

    // --- 1. KHAI BÁO BIẾN CỤC BỘ (STATE MANAGEMENT) ---
    let stompClient = null;
    let currentPartnerId = null;
    let currentPartnerName = '';
    let isCurrentPartnerFriend = false;
    
    // Biến cho Ghi âm
    let mediaRecorder = null;
    let audioChunks = [];
    let isRecording = false;

    // Lấy thông tin user hiện tại (được inject từ messenger.html)
    const currentUser = window.currentUser || { userID: 0, name: 'Me' };

    // Config Sticker
    const STICKERS = [
        "https://media.giphy.com/media/l0HlHFRbmaZtBRhXG/giphy.gif",
        "https://media.giphy.com/media/26BRv0ThflsHCqDrG/giphy.gif",
        "https://media.giphy.com/media/3o7TKSjRrfIPjeiVyM/giphy.gif",
        "https://media.giphy.com/media/l0HlI9qB6L8l756z6/giphy.gif",
        "https://media.giphy.com/media/3o6Zt481isNas9aEqs/giphy.gif",
        "https://media.giphy.com/media/l41lFw057lAJcYt0Y/giphy.gif"
    ];

    // --- 2. KHỞI TẠO (INITIALIZATION) ---
    $(document).ready(function() {
        console.log("Messenger System Starting...");
        
        // 1. Kết nối Socket
        connectWebSocket();
        
        // 2. Load danh sách chat
        loadConversations();
        
        // 3. Render Menu Sticker
        renderStickerMenu();
        
        // 4. Gắn sự kiện (Events)
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

        // Nút Gửi (Click)
        $('.fa-paper-plane').parent().off('click').on('click', sendTextMessage);

        // Upload ảnh (Input hidden)
        $('#imageInput').off('change').on('change', function() {
            if (this.files && this.files[0]) {
                uploadFile(this.files[0], 'IMAGE');
            }
        });
        
        // Ghi âm (Toggle)
        $('#recordBtn').parent().off('click').on('click', toggleRecording);
    }

    // --- 3. XỬ LÝ SOCKET (REAL-TIME ENGINE) ---
    function connectWebSocket() {
        if(stompClient && stompClient.connected) {
            console.log("Socket already connected.");
            return;
        }

        const socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        stompClient.debug = null; // Tắt log spam console

        stompClient.connect({}, function (frame) {
            console.log('✅ Messenger Socket Connected');
            
            // Subscribe kênh tin nhắn riêng tư
            // Topic: /user/queue/private (Server gửi về user đích danh)
            stompClient.subscribe('/user/queue/private', function (payload) {
                const message = JSON.parse(payload.body);
                handleIncomingMessage(message);
            });

        }, function(error) {
            console.error('Socket Error, reconnecting in 5s...', error);
            setTimeout(connectWebSocket, 5000);
        });
    }

    function handleIncomingMessage(message) {
        // 1. Nếu đang chat với đúng người gửi hoặc mình gửi (sync đa thiết bị)
        if (currentPartnerId && (message.senderId === currentPartnerId || message.senderId === currentUser.userID)) {
            appendMessageToUI(message);
            scrollToBottom();
            // TODO: Gửi signal "Đã xem" nếu cần
        }
        
        // 2. Cập nhật Sidebar (đẩy tin mới lên đầu)
        loadConversations();
    }

    // --- 4. LOGIC CHAT & GIAO DIỆN (CORE UI) ---

    // Hàm load danh sách bên trái
    function loadConversations() {
        console.log("Loading conversations...");
        $.get('/api/v1/messenger/conversations', function(data) {
            const list = $('#conversationList');
            list.empty();

            // Nếu không có dữ liệu, vẫn phải check URL (trường hợp chat người lạ lần đầu)
            if (!data || data.length === 0) {
                list.html(`<div class="text-center mt-5 text-muted"><small>Chưa có tin nhắn nào.</small></div>`);
                if (typeof checkUrlAndOpenChat === 'function') checkUrlAndOpenChat([]);
                return;
            }

            data.forEach(c => {
                const isActive = (c.partnerId === currentPartnerId) ? 'active' : '';
                const isUnread = c.unreadCount > 0 ? 'unread' : '';
                const senderPrefix = c.lastMessageMine ? 'Bạn: ' : '';
                const avatarUrl = c.partnerAvatar || `https://ui-avatars.com/api/?name=${c.partnerName}`;
                
                // [FIX] Đảm bảo biến friend không bị undefined
                const isFriendSafe = (c.friend === true); 

                const html = `
                    <div class="conv-item ${isActive} ${isUnread}" id="conv-${c.partnerId}" 
                         onclick="window.selectConversation(${c.partnerId}, '${c.partnerName}', '${avatarUrl}', ${isFriendSafe})">
                        
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
                                ${senderPrefix}${c.lastMessage || 'Hình ảnh/File'}
                            </div>
                        </div>
                        
                        ${c.unreadCount > 0 ? `<div class="unread-badge-dot"></div>` : ''}
                    </div>
                `;
                list.append(html);
            });

            // [FIX QUAN TRỌNG] Gọi hàm kiểm tra URL để mở chat người lạ sau khi list đã render
            if (typeof checkUrlAndOpenChat === 'function') {
                checkUrlAndOpenChat(data);
            }

        }).fail(function(xhr, status, error) {
            console.error("Lỗi tải hội thoại:", status, error);
            $('#conversationList').html(`<div class="text-center text-danger mt-4">Lỗi tải dữ liệu</div>`);
        });
    }

    // Hàm chọn hội thoại (Expose ra window để HTML gọi onclick)
    window.selectConversation = function(partnerId, name, avatar, isFriend) {
        currentPartnerId = partnerId;
        currentPartnerName = name;
        isCurrentPartnerFriend = isFriend;

        // 1. Update UI Header
        $('.msg-right-header .user-info h4').text(name);
        $('.msg-right-header .user-info img').attr('src', avatar);
        
        // 2. Xử lý Badge Người Lạ / Online
        const statusContainer = $('#chatHeaderStatus'); // Cần ID này ở messenger.html
        if (statusContainer.length) {
            if (!isFriend) {
                statusContainer.html(`<span class="badge badge-warning" style="background:#e50914; color:#fff; padding:3px 8px; border-radius:10px; font-size:0.75rem;">Người lạ</span>`);
            } else {
                statusContainer.html(`<span class="text-success" style="font-size:0.8rem;"><i class="fas fa-circle" style="font-size:0.6rem;"></i> Đang hoạt động</span>`);
            }
        }

        // 3. Highlight Sidebar
        $('.conv-item').removeClass('active');
        $(`#conv-${partnerId}`).addClass('active');

        // 4. Load Lịch sử Chat
        loadChatHistory(partnerId, name, isFriend);
        
        // 5. Mobile responsive: Hiển thị khung chat
        $('.messenger-container').addClass('show-chat');
    };

    function loadChatHistory(partnerId, name, isFriend) {
        const container = $('#messagesContainer');
        container.html('<div class="text-center mt-5"><i class="fas fa-spinner fa-spin text-muted"></i></div>');

        $.get(`/api/v1/messenger/chat/${partnerId}`, function(messages) {
            container.empty();

            // A. Banner Người Lạ (Nếu chưa kết bạn)
            if (!isFriend) {
                const strangerBanner = `
                    <div class="stranger-banner text-center mb-4 p-3" style="background: rgba(255,255,255,0.05); border-radius: 8px;">
                        <img src="https://ui-avatars.com/api/?name=${name}&background=random" style="width:50px; height:50px; border-radius:50%; margin-bottom:10px;">
                        <p class="text-muted mb-2" style="font-size: 0.9rem;">Bạn và <strong>${name}</strong> chưa là bạn bè trên FFilm.</p>
                        <button class="btn btn-sm btn-outline-danger" onclick="window.sendFriendRequest(${partnerId}, this)">
                            <i class="fas fa-user-plus"></i> Gửi lời mời kết bạn
                        </button>
                    </div>
                `;
                container.append(strangerBanner);
            }

            // B. Render tin nhắn
            if (!messages || messages.length === 0) {
                if(isFriend) {
                    container.append(`<div class="text-center mt-5 text-muted"><small>Hãy gửi lời chào tới ${name} 👋</small></div>`);
                }
            } else {
                messages.forEach(msg => appendMessageToUI(msg));
            }

            scrollToBottom();
        });
    }

    // --- 5. RENDER TIN NHẮN (UI RENDERING) ---
    function appendMessageToUI(msg) {
        const isMine = (msg.senderId === currentUser.userID);
        const typeClass = isMine ? 'mine' : 'other';
        
        // Avatar người khác
        const partnerAvatarUrl = $('.msg-right-header .user-info img').attr('src') || '/images/default-avatar.jpg';
        const avatarHtml = !isMine ? `<img src="${partnerAvatarUrl}" class="msg-avatar">` : '';

        // Xử lý nội dung theo loại tin nhắn
        let contentHtml = '';
        
        if (msg.type === 'IMAGE' || msg.type === 'STICKER') {
            const imgClass = msg.type === 'STICKER' ? 'sticker-img' : 'chat-image';
            contentHtml = `<img src="${msg.content}" class="${imgClass}" onclick="window.open('${msg.content}', '_blank')">`;
        } 
        else if (msg.type === 'AUDIO' || msg.type === 'VOICE') {
            contentHtml = `
                <audio controls controlsList="nodownload" style="height: 30px; max-width: 200px;">
                    <source src="${msg.content}" type="audio/webm">
                    Your browser does not support the audio element.
                </audio>
            `;
        } 
        else {
            // TEXT mặc định
            contentHtml = msg.content; // Cần escape HTML nếu muốn bảo mật XSS chặt chẽ
        }

        // HTML tin nhắn hoàn chỉnh
        const html = `
            <div class="msg-bubble ${typeClass}">
                ${avatarHtml}
                <div class="msg-text">
                    ${contentHtml}
                    <div class="msg-time">${msg.formattedTime || 'Vừa xong'}</div>
                </div>
            </div>
        `;
        
        $('#messagesContainer').append(html);
    }

    function scrollToBottom() {
        const d = $('#messagesContainer');
        d.scrollTop(d[0].scrollHeight);
    }

    // --- 6. CÁC CHỨC NĂNG GỬI (SEND ACTIONS) ---
    
    // 6.1 Gửi Text
    function sendTextMessage() {
        const input = $('#msgInput');
        const content = input.val().trim();
        if (!content || !currentPartnerId) return;

        const payload = {
            receiverId: currentPartnerId,
            content: content,
            type: 'TEXT'
        };

        sendApiRequest(payload);
        input.val('');
    }

    // 6.2 Gửi Sticker (Global function)
    window.sendSticker = function(url) {
        $('#stickerMenu').hide();
        if (!currentPartnerId) return;
        
        const payload = {
            receiverId: currentPartnerId,
            content: url,
            type: 'STICKER' // Hoặc IMAGE tùy backend
        };
        sendApiRequest(payload);
    };

    // 6.3 Core Send API
    function sendApiRequest(payload) {
        $.ajax({
            url: '/api/v1/messenger/send',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            success: function(msg) {
                // UI được cập nhật qua Socket, nhưng để mượt thì append luôn (nếu socket chậm)
                // appendMessageToUI(msg); // Tùy chọn: Bật lên nếu muốn instant feedback
                scrollToBottom();
            },
            error: function(e) {
                console.error("Send Failed", e);
                alert("Không thể gửi tin nhắn. Vui lòng kiểm tra kết nối.");
            }
        });
    }

    // --- 7. UPLOAD FILE & GHI ÂM (FILE HANDLING) ---

    // 7.1 Upload File (Ảnh/Audio)
    function uploadFile(file, type) {
        if (!currentPartnerId) return alert("Vui lòng chọn cuộc trò chuyện trước.");

        const formData = new FormData();
        formData.append("file", file);
        formData.append("receiverId", currentPartnerId);
        formData.append("type", type); // 'IMAGE' hoặc 'AUDIO'

        // UI Loading
        const loadingId = 'loading-' + Date.now();
        $('#messagesContainer').append(`<div id="${loadingId}" class="text-center text-muted small mt-2">Đang gửi file...</div>`);
        scrollToBottom();

        $.ajax({
            url: '/api/v1/messenger/upload', // Endpoint backend xử lý upload
            type: 'POST',
            data: formData,
            processData: false,
            contentType: false,
            success: function(response) {
                $(`#${loadingId}`).remove();
                // Response trả về đối tượng Message -> Append hoặc đợi Socket
            },
            error: function() {
                $(`#${loadingId}`).html('<span class="text-danger">Lỗi gửi file!</span>');
            }
        });
    }

    // 7.2 Logic Ghi âm (Record Audio)
    function toggleRecording() {
        const btn = $('#recordBtn');
        
        if (!isRecording) {
            // BẮT ĐẦU GHI
            if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
                alert("Trình duyệt của bạn không hỗ trợ ghi âm.");
                return;
            }

            navigator.mediaDevices.getUserMedia({ audio: true })
                .then(stream => {
                    mediaRecorder = new MediaRecorder(stream);
                    mediaRecorder.start();
                    isRecording = true;
                    audioChunks = [];

                    // UI Effect
                    btn.removeClass('fa-microphone').addClass('fa-stop-circle text-danger').addClass('fa-beat');
                    $('#msgInput').attr('placeholder', 'Đang ghi âm...').prop('disabled', true);

                    mediaRecorder.ondataavailable = event => {
                        audioChunks.push(event.data);
                    };

                    mediaRecorder.onstop = () => {
                        const audioBlob = new Blob(audioChunks, { type: 'audio/webm' });
                        // Gửi file ngay khi dừng
                        uploadFile(audioBlob, 'AUDIO');
                    };
                })
                .catch(err => {
                    console.error("Mic Access Error:", err);
                    alert("Không thể truy cập Microphone.");
                });

        } else {
            // DỪNG GHI
            if (mediaRecorder) {
                mediaRecorder.stop();
            }
            isRecording = false;
            
            // Reset UI
            btn.removeClass('fa-stop-circle text-danger fa-beat').addClass('fa-microphone');
            $('#msgInput').attr('placeholder', 'Nhập tin nhắn...').prop('disabled', false).focus();
        }
    }

    // --- 8. HELPER FUNCTIONS ---
    window.toggleStickers = function() {
        $('#stickerMenu').toggle();
    };

    function renderStickerMenu() {
        let html = '';
        STICKERS.forEach(url => {
            html += `<img src="${url}" class="sticker-item" onclick="window.sendSticker('${url}')">`;
        });
        $('#stickerMenu').html(html);
    }


    // ============================================================
    // [MỚI] CÁC HÀM HỖ TRỢ CHAT NGƯỜI LẠ (STRANGER CHAT)
    // ============================================================

    /**
     * Kiểm tra URL param ?uid=... và mở chat nếu cần
     */
    function checkUrlAndOpenChat(existingConversations) {
        const urlParams = new URLSearchParams(window.location.search);
        const targetUid = urlParams.get('uid');

        if (!targetUid) return; // Không có yêu cầu chat

        const targetIdInt = parseInt(targetUid);
        
        // Trường hợp 1: Người này ĐÃ CÓ trong danh sách chat cũ
        // existingConversations là mảng data trả về từ API /conversations
        if (existingConversations && existingConversations.length > 0) {
            const existing = existingConversations.find(c => c.partnerId === targetIdInt);
            if (existing) {
                console.log("Đã có hội thoại, mở ngay:", existing);
                // Giả lập click vào item đó để mở chat
                // Lưu ý: Đảm bảo ID trong HTML render ở loadConversations là #conv-{id}
                const item = document.getElementById(`conv-${targetIdInt}`);
                if(item) item.click();
                return;
            }
        }

        // Trường hợp 2: Người lạ (Chưa có trong list) -> Gọi API lấy thông tin để tạo box tạm
        console.log("Người lạ, đang lấy thông tin...");
        $.get(`/api/users/${targetIdInt}`)
            .done(function(userDto) {
                // Tạo data giả lập cho item sidebar
                const tempItem = {
                    partnerId: userDto.userId,
                    partnerName: userDto.userName,
                    partnerAvatar: `https://ui-avatars.com/api/?name=${encodeURIComponent(userDto.userName)}&background=random`,
                    lastMessage: "Bắt đầu cuộc trò chuyện mới",
                    friend: false // Đánh dấu là chưa kết bạn
                };
                
                // Render item này lên đầu sidebar
                prependSidebarItem(tempItem);
                
                // Tự động mở chat với người này (false = chưa là bạn)
                window.selectConversation(tempItem.partnerId, tempItem.partnerName, tempItem.partnerAvatar, false);
            })
            .fail(function() {
                console.error("Không tìm thấy user ID:", targetIdInt);
            });
    }

    /**
     * Chèn item tạm thời vào đầu danh sách chat
     */
    function prependSidebarItem(c) {
        const list = $('#conversationList');
        // Xóa thông báo "Chưa có tin nhắn" nếu có
        if (list.find('.text-muted').length > 0) list.empty();

        const html = `
            <div class="conv-item active" id="conv-${c.partnerId}" 
                 onclick="window.selectConversation(${c.partnerId}, '${c.partnerName}', '${c.partnerAvatar}', ${c.friend})">
                
                <div class="avatar-wrapper">
                    <img src="${c.partnerAvatar}" class="avatar-img">
                </div>

                <div class="conv-info">
                    <div class="conv-top-row">
                        <div class="conv-name">${c.partnerName}</div>
                        <span class="conv-time">Mới</span>
                    </div>
                    <div class="conv-preview">
                        <span class="text-primary">Bắt đầu trò chuyện ngay</span>
                    </div>
                </div>
            </div>
        `;
        list.prepend(html);
    }

})(); // END IIFE