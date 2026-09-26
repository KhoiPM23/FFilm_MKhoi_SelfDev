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

    // Call State -> Extracted to messenger-calls.js
    let typingTimeout = null;
    let lastSeenMessageId = null;

    let messageQueue = [];
    let isProcessingQueue = false;

    const currentUser = window.currentUser || { userID: 0, name: 'Me' };
    const notificationSound = new Audio('/sounds/message-notification.mp3');

    // Bridge shared state for modular scripts (e.g. messenger-calls.js, messenger-stickers.js)
    window.MessengerState = {
        get stompClient() { return stompClient; },
        get currentPartnerId() { return currentPartnerId; },
        get currentPartnerName() { return currentPartnerName; },
        get currentUser() { return currentUser; },
        sendApiRequest: function(payload) { return sendApiRequest(payload); },
        showToast: function(msg, type) { return window.showToast(msg, type); }
    };

    let searchResults = [];
    let currentSearchIndex = -1;

    let selectedMessageToForward = null;
    let forwardTimeout = null;

    // --- KHỞI TẠO ---
    $(document).ready(function() {
        console.log("Messenger Init Start...");
        connectWebSocket();
        loadConversations();
        bindEvents();
        if (window.MessengerCalls) { window.MessengerCalls.init(); }
        if (window.MessengerStickers) { window.MessengerStickers.init(); }
    });

    function bindEvents() {
        // Gửi tin bằng Enter
        $('#msgInput').off('keypress').on('keypress', function(e) {
            if (e.which === 13 && !e.shiftKey) {
                e.preventDefault();
                window.sendTextMessage();
            }
        });

        // Thêm sự kiện cho search input
        $('#convSearchInput').off('input').on('input', window.filterConversations);

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

    // --- WebRTC / Call Logic extracted to messenger-calls.js ---

    // --- 1. WEBSOCKET ---
    function connectWebSocket() {
        const socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        stompClient.debug = null;
        
        stompClient.connect({}, function(frame) {
            console.log('✅ WebSocket Connected:', frame);
            
            // Subscribe đến private messages - DÙNG userId
            stompClient.subscribe(`/user/${currentUser.userID}/queue/private`, function(payload) {
                const msg = JSON.parse(payload.body);
                handleSocketMessage(msg);
            });
            
            // Subscribe đến typing notifications
            stompClient.subscribe(`/user/${currentUser.userID}/queue/typing`, function(payload) {
                const data = JSON.parse(payload.body);
                if (data.senderId === currentPartnerId) {
                    if (data.type === 'TYPING') {
                        showTypingIndicator(data.senderName);
                    } else {
                        hideTypingIndicator();
                    }
                }
            });
            
            // Subscribe đến seen notifications
            stompClient.subscribe(`/user/${currentUser.userID}/queue/seen`, function(payload) {
                const data = JSON.parse(payload.body);
                updateSeenAvatar(data.messageId, data.seenBy);
            });
            
            // Subscribe đến online status updates
            stompClient.subscribe(`/user/${currentUser.userID}/queue/online-status`, function(payload) {
                const data = JSON.parse(payload.body);
                updateOnlineStatus(data.userId, data.isOnline, data.lastActive);
            });

            // Subscribe đến call notifications (delegated to messenger-calls.js)
            stompClient.subscribe(`/user/${currentUser.userID}/queue/call`, function(payload) {
                const data = JSON.parse(payload.body);
                if (window.MessengerCalls) {
                    window.MessengerCalls.handleIncomingCall(data);
                }
            });
            
            // Gửi ping để báo online
            stompClient.send('/app/online/ping', {}, JSON.stringify({
                userId: currentUser.userID
            }));
            
            // Thông báo kết nối thành công
            showToast("Đã kết nối thời gian thực", "success");
            
        }, function(error) {
            console.error('WebSocket Error:', error);
            setTimeout(connectWebSocket, 5000);
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
        
        // 1. Xử lý Tín hiệu Gọi (delegated to messenger-calls.js)
        if (window.MessengerCalls && window.MessengerCalls.handleCallSocketMessage(msg)) {
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
    function renderConversationSkeletons(count = 6) {
        let html = '';
        for (let i = 0; i < count; i++) {
            html += `
                <div class="loading-skeleton">
                    <div class="skeleton-avatar"></div>
                    <div class="skeleton-text">
                        <div class="skeleton-line" style="width: ${65 + (i % 3) * 12}%;"></div>
                        <div class="skeleton-line short"></div>
                    </div>
                </div>
            `;
        }
        return html;
    }

    // --- CẬP NHẬT: loadConversations (Truyền đủ tham số Online/Active) ---
    function loadConversations() {
        const list = $('#conversationList');
        if (list.children('.conv-item').length === 0) {
            list.html(renderConversationSkeletons(6));
        }

        $.ajax({
            url: '/api/v1/messenger/conversations',
            method: 'GET',
            dataType: 'json',
            success: function(data) {
                list.empty();
                if (!data || !Array.isArray(data)) return;

                if (data.length === 0) {
                    list.html(`
                        <div class="empty-conversations text-center py-5 px-3 text-muted">
                            <i class="far fa-comments fa-3x mb-3" style="opacity: 0.4;"></i>
                            <p style="font-size: 0.9rem; margin-bottom: 12px; color: #aaa;">Chưa có cuộc trò chuyện nào</p>
                            <button class="btn btn-sm btn-primary" onclick="window.openNewChatModal()" style="border-radius: 20px; padding: 6px 16px;">
                                <i class="fas fa-edit mr-1"></i> Bắt đầu trò chuyện
                            </button>
                        </div>
                    `);
                    return;
                }

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
                                    <strong class="conv-name" style="color:#fff; font-size:0.95rem; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">
                                        ${c.partnerName} ${strangerBadge}
                                    </strong>
                                    <small class="text-muted" style="font-size:0.75rem;">${c.timeAgo || ''}</small>
                                </div>
                                <div class="conv-preview text-muted small text-truncate" style="color:#aaa;">
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
                list.html('<div class="text-center py-4 text-muted small"><i class="fas fa-exclamation-circle text-danger mr-1"></i> Không thể tải hội thoại. Vui lòng thử lại.</div>');
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
        const id = partnerId || currentPartnerId;
        const btn = btnElement || document.querySelector('.btn-stranger-add');
        if (!id || !btn) return;

        const originalHtml = btn.innerHTML;
        btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
        btn.disabled = true;

        fetch(`/social/add-friend/${id}`, { method: 'POST' })
            .then(res => res.ok ? res.json() : Promise.reject())
            .then(() => {
                btn.innerHTML = '<i class="fas fa-clock"></i> Đã gửi';
                btn.classList.add('btn-stranger-pending');
                btn.onclick = () => window.cancelFriendRequest(id, btn);
                btn.disabled = false;
            })
            .catch(() => {
                btn.innerHTML = originalHtml;
                btn.disabled = false;
                alert('Lỗi gửi lời mời');
            });
    };

    window.cancelFriendRequest = function(partnerId, btnElement) {
        const id = partnerId || currentPartnerId;
        const btn = btnElement || document.querySelector('.btn-stranger-add');
        if (!id || !btn) return;
        if (!confirm('Hủy lời mời kết bạn?')) return;
        
        btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
        
        fetch(`/social/unfriend/${id}`, { method: 'POST' })
            .then(res => {
                if (res.ok) {
                    btn.innerHTML = '<i class="fas fa-user-plus"></i> Kết bạn';
                    btn.classList.remove('btn-stranger-pending');
                    btn.onclick = () => window.sendFriendRequest(id, btn);
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
        container.html(`
            <div class="chat-skeleton-container">
                <div class="skeleton-bubble-row other"><div class="skeleton-bubble" style="width: 50%; height: 42px;"></div></div>
                <div class="skeleton-bubble-row mine"><div class="skeleton-bubble" style="width: 38%; height: 38px;"></div></div>
                <div class="skeleton-bubble-row other"><div class="skeleton-bubble" style="width: 65%; height: 56px;"></div></div>
                <div class="skeleton-bubble-row mine"><div class="skeleton-bubble" style="width: 48%; height: 42px;"></div></div>
                <div class="skeleton-bubble-row other"><div class="skeleton-bubble" style="width: 35%; height: 36px;"></div></div>
            </div>
        `);

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

            // FIX: Khởi tạo reaction system sau khi load tin nhắn
            initReactionSystem();
        }).fail(function() {
            container.html('<div class="text-center mt-5 text-danger"><small><i class="fas fa-exclamation-triangle mr-1"></i> Không thể tải tin nhắn. Vui lòng thử lại sau.</small></div>');
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
            contentHtml = renderAudioPlayer(msg.content, msg.id);
            // Auto-init sau khi append
            setTimeout(() => {
                if (msg.id) {
                    initAudioDuration(`audio-player-${msg.id}`);
                }
            }, 100);
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
                <div class="action-btn reaction-btn" title="Thả cảm xúc" onclick="showReactionPicker(this, ${msgId})">
                    <i class="far fa-smile"></i>
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
                <div class="action-btn reaction-btn" title="Thả cảm xúc" onclick="showReactionPicker(this, ${msgId})">
                    <i class="far fa-smile"></i>
                </div>
            `;
        }

        // Actions
        const unsendBtn = (isMine && !msg.isDeleted) 
            ? `<div class="action-btn" onclick="window.unsendMessage(${msgId})" title="Thu hồi"><i class="fas fa-trash"></i></div>` 
            : '';

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

    // FIX 1.2: Thêm hàm init audio player
    function initAudioPlayerForMessage(messageId, audioUrl) {
        const playerId = `audio-player-${messageId}`;
        const audioElement = document.getElementById(`${playerId}-audio`);
        
        if (audioElement) {
            audioElement.onloadedmetadata = function() {
                const duration = Math.floor(audioElement.duration);
                const mins = Math.floor(duration / 60);
                const secs = duration % 60;
                document.getElementById(`${playerId}-duration`).textContent = 
                    `${mins}:${secs.toString().padStart(2, '0')}`;
            };
            
            audioElement.onerror = function() {
                console.error('Lỗi tải audio:', audioUrl);
            };
        }
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

    window.closeModal = function() {
        $('.modal-overlay, .theme-modal, .nickname-modal, .background-modal, .stats-modal').remove();
    };

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
        
        // Xóa modal cũ nếu có
        $('.forward-modal-overlay, .forward-modal').remove();
        
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


function loadForwardRecipients() {
    
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

    function showTypingIndicator(senderName) {
        // Remove existing indicator
        $('#typingIndicator').remove();
        
        const indicator = $(`
            <div id="typingIndicator" class="typing-indicator">
                <div class="typing-dots">
                    <span class="typing-dot"></span>
                    <span class="typing-dot"></span>
                    <span class="typing-dot"></span>
                </div>
                <span style="margin-left: 8px; color: #aaa; font-size: 12px;">
                    ${senderName} đang soạn tin...
                </span>
            </div>
        `);
        
        $('#messagesContainer').append(indicator);
        scrollToBottom();
        
        // Auto hide after 5 seconds
        clearTimeout(typingTimeout);
        typingTimeout = setTimeout(hideTypingIndicator, 5000);
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
    function renderAudioPlayer(audioUrl, messageId = null) {
        const playerId = messageId ? `audio-player-${messageId}` : `audio-player-${Date.now()}`;
        
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
                        <span id="${playerId}-duration">--:--</span>
                    </div>
                </div>
                <audio id="${playerId}-audio" preload="metadata"
                    onloadedmetadata="initAudioDuration('${playerId}')"
                    ontimeupdate="updateAudioProgress('${playerId}')"
                    onended="onAudioEnded('${playerId}')">
                    <source src="${audioUrl}" type="audio/webm">
                    <source src="${audioUrl}" type="audio/mpeg">
                    Your browser does not support the audio element.
                </audio>
                <a href="${audioUrl}" download class="audio-download-btn" title="Tải xuống">
                    <i class="fas fa-download"></i>
                </a>
            </div>
        `;
    }

    // FIX 1.4: Thêm hàm initAudioDuration
    window.initAudioDuration = function(playerId) {
        const audio = document.getElementById(playerId + '-audio');
        if (!audio) return;
        
        // Đợi metadata load
        if (audio.readyState >= 1) {
            updateAudioDuration(playerId, audio.duration);
        } else {
            audio.addEventListener('loadedmetadata', () => {
                updateAudioDuration(playerId, audio.duration);
            });
            // Force load
            audio.load();
        }
    };

    function updateAudioDuration(playerId, duration) {
        if (!duration || isNaN(duration) || duration === Infinity) {
            console.warn('Invalid audio duration');
            return;
        }
        
        const mins = Math.floor(duration / 60);
        const secs = Math.floor(duration % 60);
        const durationElement = document.getElementById(playerId + '-duration');
        if (durationElement) {
            durationElement.textContent = `${mins}:${secs.toString().padStart(2, '0')}`;
        }
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
        
        const formData = new FormData();
        const fileName = `audio_${Date.now()}.webm`;
        formData.append('file', audioBlob, fileName);
        
        // Show uploading indicator
        const tempId = 'audio-upload-' + Date.now();
        $('#messagesContainer').append(`
            <div id="${tempId}" class="msg-row mine">
                <div class="msg-content">
                    <div class="bubble">
                        <i class="fas fa-spinner fa-spin"></i> Đang tải lên...
                    </div>
                </div>
            </div>
        `);
        scrollToBottom();
        
        $.ajax({
            url: '/api/upload/audio',
            type: 'POST',
            data: formData,
            processData: false,
            contentType: false,
            success: function(response) {
                $(`#${tempId}`).remove();
                
                if (response.url) {
                    // Send message với type AUDIO
                    const payload = {
                        receiverId: currentPartnerId,
                        content: response.url,
                        type: 'AUDIO',
                        metadata: {
                            size: response.size,
                            duration: 0
                        }
                    };
                    
                    // Optimistic UI
                    appendMessageToUI({
                        id: 'temp-audio',
                        senderId: currentUser.userID,
                        content: response.url,
                        type: 'AUDIO',
                        formattedTime: 'Đang gửi...'
                    }, true);
                    
                    // Send to server
                    sendApiRequest(payload);
                }
            },
            error: function(err) {
                console.error('Upload audio error:', err);
                $(`#${tempId}`).html('<span class="text-danger">Lỗi tải lên</span>');
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

    // Reaction system
    window.initReactionSystem = function() {
        // Thêm reaction button cho các message chưa có
        $('.msg-row').each(function() {
            if (!$(this).find('.reaction-btn').length) {
                const msgId = $(this).data('msg-id');
                if (msgId) {
                    $(this).append(`
                        <div class="reaction-btn" onclick="showReactionPicker(this, ${msgId})">
                            <i class="far fa-smile"></i>
                        </div>
                    `);
                }
            }
        });
        
        // Hiển thị existing reactions nếu có
        $('.msg-row').each(function() {
            const msgId = $(this).data('msg-id');
            if (msgId) {
                loadMessageReactions(msgId);
            }
        });
    };

    window.showReactionPicker = function(button) {
        // Remove any existing picker
        $('.reaction-picker').remove();
        
        const msgRow = $(button).closest('.msg-row');
        const msgId = msgRow.data('msg-id');
        
        const picker = $(`
            <div class="reaction-picker active">
                <span class="reaction-emoji" onclick="addReaction(${msgId}, '👍')">👍</span>
                <span class="reaction-emoji" onclick="addReaction(${msgId}, '❤️')">❤️</span>
                <span class="reaction-emoji" onclick="addReaction(${msgId}, '😮')">😮</span>
                <span class="reaction-emoji" onclick="addReaction(${msgId}, '😢')">😢</span>
                <span class="reaction-emoji" onclick="addReaction(${msgId}, '😂')">😂</span>
                <span class="reaction-emoji" onclick="addReaction(${msgId}, '😠')">😠</span>
                <span class="reaction-emoji" onclick="showFullReactionPicker(${msgId})">
                    <i class="fas fa-plus"></i>
                </span>
            </div>
        `);
        
        msgRow.append(picker);
        
        // Close picker when clicking outside
        setTimeout(() => {
            $(document).on('click.reaction', function(e) {
                if (!$(e.target).closest('.reaction-picker, .reaction-btn').length) {
                    $('.reaction-picker').remove();
                    $(document).off('click.reaction');
                }
            });
        }, 100);
    };

    window.addReaction = function(messageId, emoji) {
        $.post('/api/v1/messenger/reaction', {
            messageId: messageId,
            emoji: emoji
        }).done(function(response) {
            updateMessageReactions(messageId, response.reactions);
            $('.reaction-picker').remove();
        });
    };

    window.updateMessageReactions = function(messageId, reactions) {
        const msgRow = $(`#msg-${messageId}`);
        let reactionsHtml = '';
        
        if (reactions && Object.keys(reactions).length > 0) {
            reactionsHtml = '<div class="message-reactions">';
            Object.entries(reactions).forEach(([emoji, count]) => {
                reactionsHtml += `
                    <div class="reaction-item" onclick="showReactionDetails(${messageId}, '${emoji}')">
                        <span>${emoji}</span>
                        <span class="reaction-count">${count}</span>
                    </div>
                `;
            });
            reactionsHtml += '</div>';
        }
        
        msgRow.find('.message-reactions').remove();
        msgRow.find('.msg-content').append(reactionsHtml);
    };

    // Pinned messages system
    window.loadPinnedMessages = function() {
        if (!currentPartnerId) return;
        
        $.get(`/api/v1/messenger/pinned/${currentPartnerId}`)
            .done(function(messages) {
                displayPinnedMessages(messages);
            });
    };

    window.displayPinnedMessages = function(messages) {
        // Remove existing banner
        $('.pinned-banner').remove();
        
        if (messages && messages.length > 0) {
            const latest = messages[0];
            const content = latest.content.length > 50 ? 
                latest.content.substring(0, 50) + '...' : latest.content;
            
            const banner = $(`
                <div class="pinned-banner" onclick="openPinnedMessagesModal()">
                    <div class="pinned-content">
                        <i class="fas fa-thumbtack"></i>
                        <strong>Tin nhắn đã ghim:</strong> ${content}
                    </div>
                    <button class="btn-view-pinned">Xem tất cả (${messages.length})</button>
                </div>
            `);
            
            $('#messagesContainer').prepend(banner);
        }
    };

    window.openPinnedMessagesModal = function() {
        const modal = $(`
            <div class="modal-overlay">
                <div class="theme-modal pinned-modal">
                    <div class="theme-modal-header">
                        <h3><i class="fas fa-thumbtack"></i> Tin nhắn đã ghim</h3>
                        <button class="close-modal" onclick="closeModal()">
                            <i class="fas fa-times"></i>
                        </button>
                    </div>
                    <div class="pinned-list" id="pinnedList">
                        <div class="loading-pinned">
                            <i class="fas fa-spinner fa-spin"></i>
                            <p>Đang tải...</p>
                        </div>
                    </div>
                </div>
            </div>
        `);
        
        $('body').append(modal);
        
        $.get(`/api/v1/messenger/pinned/${currentPartnerId}`)
            .done(function(messages) {
                let html = '';
                if (messages.length === 0) {
                    html = '<p class="text-muted text-center">Chưa có tin nhắn nào được ghim</p>';
                } else {
                    messages.forEach(msg => {
                        const time = new Date(msg.timestamp).toLocaleTimeString('vi-VN', {
                            hour: '2-digit',
                            minute: '2-digit'
                        });
                        html += `
                            <div class="pinned-message-item" onclick="scrollToMessage(${msg.id})">
                                <div class="pinned-message-sender">
                                    ${msg.senderId === currentUser.userID ? 'Bạn' : currentPartnerName}
                                </div>
                                <div class="pinned-message-text">${msg.content}</div>
                                <div class="pinned-message-time">${time}</div>
                            </div>
                        `;
                    });
                }
                $('#pinnedList').html(html);
            });
    };

    // ============= NEW CHAT MODAL =============
    window.openNewChatModal = function() {
        $('#newChatModal').remove();

        const modal = $(`
            <div id="newChatModal" class="modal-overlay" style="display:flex;">
                <div class="theme-modal new-chat-modal" style="max-width: 440px; width: 90%; background: #242526; border-radius: 12px; overflow: hidden; box-shadow: 0 12px 28px rgba(0,0,0,0.5);">
                    <div class="theme-modal-header" style="padding: 16px 20px; border-bottom: 1px solid #3a3b3c; display: flex; justify-content: space-between; align-items: center;">
                        <h3 style="margin: 0; font-size: 1.1rem; color: #e4e6eb; display: flex; align-items: center; gap: 8px;">
                            <i class="fas fa-edit" style="color: #0084ff;"></i> Tin nhắn mới
                        </h3>
                        <button class="close-modal" onclick="$('#newChatModal').remove()" style="background: none; border: none; color: #b0b3b8; font-size: 1.2rem; cursor: pointer;">
                            <i class="fas fa-times"></i>
                        </button>
                    </div>
                    <div style="padding: 12px 16px; border-bottom: 1px solid #3a3b3c;">
                        <div class="search-wrapper" style="margin: 0; background: #3a3b3c; border-radius: 20px; padding: 6px 14px; display: flex; align-items: center;">
                            <i class="fas fa-search text-muted mr-2" style="font-size: 0.85rem;"></i>
                            <input type="text" id="newChatSearchInput" placeholder="Tìm người theo tên hoặc email..." 
                                style="background: none; border: none; outline: none; color: #fff; width: 100%; font-size: 0.9rem;">
                        </div>
                    </div>
                    <div id="newChatUsersList" style="max-height: 360px; overflow-y: auto; padding: 8px 0;">
                        <div class="text-center py-4 text-muted"><i class="fas fa-spinner fa-spin"></i> Đang tải...</div>
                    </div>
                </div>
            </div>
        `);

        $('body').append(modal);

        function loadUsers(query = '') {
            $.get(`/api/v1/messenger/users?q=${encodeURIComponent(query)}`)
                .done(function(users) {
                    const container = $('#newChatUsersList');
                    container.empty();

                    if (!users || users.length === 0) {
                        container.html('<div class="text-center py-4 text-muted"><small>Không tìm thấy người dùng phù hợp</small></div>');
                        return;
                    }

                    users.forEach(u => {
                        const row = $(`
                            <div class="new-chat-user-row d-flex align-items-center px-3 py-2" 
                                style="cursor: pointer; transition: background 0.2s; display: flex; align-items: center; padding: 8px 16px;"
                                onmouseover="this.style.background='#3a3b3c'" 
                                onmouseout="this.style.background='transparent'">
                                <img src="${u.avatar}" style="width: 40px; height: 40px; border-radius: 50%; object-fit: cover; margin-right: 12px;">
                                <div class="flex-grow-1" style="min-width: 0;">
                                    <div style="font-weight: 600; color: #e4e6eb; font-size: 0.95rem; text-overflow: ellipsis; overflow: hidden; white-space: nowrap;">
                                        ${u.name}
                                    </div>
                                    <small style="color: #b0b3b8; font-size: 0.8rem;">${u.email || ''}</small>
                                </div>
                                <i class="fas fa-paper-plane" style="color: #0084ff; font-size: 0.85rem; margin-left: auto;"></i>
                            </div>
                        `);

                        row.on('click', function() {
                            $('#newChatModal').remove();
                            window.selectConversation(u.id, u.name, u.avatar, 'false', 'false', '');
                        });

                        container.append(row);
                    });
                })
                .fail(function() {
                    $('#newChatUsersList').html('<div class="text-center py-4 text-danger"><small>Lỗi tải danh sách người dùng</small></div>');
                });
        }

        loadUsers();

        let searchTimer = null;
        $('#newChatSearchInput').on('input', function() {
            clearTimeout(searchTimer);
            const val = $(this).val().trim();
            searchTimer = setTimeout(() => loadUsers(val), 250);
        });

        setTimeout(() => $('#newChatSearchInput').focus(), 100);
    };

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
        $('#typingIndicator').remove();
    }

    // Send typing event via WebSocket
    $('#msgInput').on('input', function() {
        if (!currentPartnerId || !stompClient) return;
        
        clearTimeout(typingTimeout);
        
        // Send typing event
        stompClient.send('/app/typing', {}, JSON.stringify({
            receiverId: currentPartnerId,
            senderId: currentUser.userID,
            senderName: currentUser.userName,
            type: 'TYPING'
        }));
        
        // Stop typing after 2 seconds of inactivity
        typingTimeout = setTimeout(() => {
            stompClient.send('/app/stop-typing', {}, JSON.stringify({
                receiverId: currentPartnerId
            }));
        }, 2000);
    });

    // --- FIX 10: SEEN AVATAR ---
    function updateSeenAvatar(messageId, seenByUserId) {
        const messageElement = $(`#msg-${messageId}`);
        
        if (messageElement.length && seenByUserId === currentPartnerId) {
            // Add seen avatar
            const partnerAvatar = $('#headerAvatar').attr('src');
            messageElement.find('.msg-content').append(`
                <img src="${partnerAvatar}" class="msg-seen-avatar" 
                    title="Đã xem" style="width:16px; height:16px; border-radius:50%;">
            `);
        }
    }

    // FIX 7.6: Send seen events
    function sendSeenEvent(messageId) {
        if (!stompClient || !currentPartnerId) return;
        
        stompClient.send('/app/mark-seen', {}, JSON.stringify({
            messageId: messageId,
            userId: currentUser.userID,
            partnerId: currentPartnerId
        }));
    }
    
    // Call sendSeenEvent khi tin nhắn hiển thị trong viewport
    $(document).ready(function() {
        $('#messagesContainer').on('scroll', function() {
            // Implement logic để kiểm tra tin nhắn nào đang visible
            // và gọi sendSeenEvent cho tin nhắn cuối cùng
        });
    });

    // --- 8. STICKER & EMOJI LOGIC -> Extracted to messenger-stickers.js ---

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
        
        // FIX: Render đúng HTML cho accordion-content đầu tiên
        $('.accordion-item:first .accordion-content').html(`
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
        
        // FIX: Thêm các action buttons vào info-header-actions
        $('.info-header-actions').html(`
            <button class="info-action-btn" onclick="viewProfile(${currentPartnerId})" title="Xem trang cá nhân">
                <i class="fas fa-user-circle"></i>
            </button>
            <button class="info-action-btn" onclick="openChatSearch()" title="Tìm kiếm tin nhắn">
                <i class="fas fa-search"></i>
            </button>
            <button class="info-action-btn" onclick="toggleNotifications(${currentPartnerId})" title="Tắt thông báo">
                <i class="fas fa-bell"></i>
            </button>
            <button class="info-action-btn" onclick="openPinnedMessagesModal()" title="Tin nhắn đã ghim">
                <i class="fas fa-thumbtack"></i>
            </button>
            <button class="info-action-btn" onclick="openCallHistory()" title="Lịch sử cuộc gọi">
                <i class="fas fa-history"></i>
            </button>
        `);
    }

    window.viewProfile = function(partnerId) {
        const id = partnerId || currentPartnerId;
        if (id) {
            window.location.href = `/profile/${id}`;
        }
    };

    window.toggleNotifications = function(partnerId) {
        const id = partnerId || currentPartnerId;
        if (!id) return;
        $.ajax({
            url: '/api/v1/messenger/settings/notification',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({ partnerId: id }),
            success: function(res) {
                const enabled = res && res.enabled;
                if (enabled) {
                    showToast('Đã bật thông báo cuộc trò chuyện', 'success');
                    $('.info-header-actions .fa-bell-slash').removeClass('fa-bell-slash').addClass('fa-bell');
                } else {
                    showToast('Đã tắt thông báo cuộc trò chuyện', 'info');
                    $('.info-header-actions .fa-bell').removeClass('fa-bell').addClass('fa-bell-slash');
                }
            },
            error: function() {
                showToast('Không thể cập nhật cài đặt thông báo', 'error');
            }
        });
    };



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
        const chatArea = $('.msg-chat-area');
        const btn = $('#btnToggleInfo');
        
        if (sidebar.hasClass('hidden')) {
            sidebar.removeClass('hidden');
            chatArea.addClass('info-open');
            btn.addClass('active');
            // Load media khi mở sidebar
            loadSharedMedia();
        } else {
            sidebar.addClass('hidden');
            chatArea.removeClass('info-open');
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
        const query = $('#convSearchInput').val().toLowerCase().trim();
        
        $('.conv-item').each(function() {
            const nameElement = $(this).find('.conv-name');
            const name = nameElement.text().toLowerCase();
            
            // Also search in preview text
            const previewElement = $(this).find('.conv-preview');
            const preview = previewElement.text().toLowerCase();
            
            if (name.includes(query) || preview.includes(query)) {
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