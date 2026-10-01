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

    // Safe HTML Escaper to prevent XSS and ReferenceErrors across Messenger
    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }
    if (typeof window.escapeHtml !== 'function') {
        window.escapeHtml = escapeHtml;
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
    window.emojiPickerState = { isOpen: false, picker: null };

    // Call State -> Extracted to messenger-calls.js
    let typingTimeout = null;
    let lastSeenMessageId = null;

    let messageQueue = [];
    let isProcessingQueue = false;

    if (window.currentUser) {
        if (typeof window.currentUser === 'string') {
            try {
                window.currentUser = JSON.parse(window.currentUser);
            } catch (e) {
                console.error('Failed to parse window.currentUser JSON string', e);
            }
        }
        if (typeof window.currentUser === 'object' && window.currentUser !== null) {
            if (!window.currentUser.userID && window.currentUser.id) window.currentUser.userID = window.currentUser.id;
            if (!window.currentUser.id && window.currentUser.userID) window.currentUser.id = window.currentUser.userID;
            if (!window.currentUser.name && window.currentUser.userName) window.currentUser.name = window.currentUser.userName;
            if (!window.currentUser.userName && window.currentUser.name) window.currentUser.userName = window.currentUser.name;
        }
    }
    const currentUser = (typeof window.currentUser === 'object' && window.currentUser !== null)
        ? window.currentUser
        : { userID: 0, id: 0, name: 'Me', userName: 'Me' };
    currentUser.userID = currentUser.userID || currentUser.id || 0;
    currentUser.id = currentUser.id || currentUser.userID || 0;
    currentUser.name = currentUser.name || currentUser.userName || 'Me';
    currentUser.userName = currentUser.userName || currentUser.name || 'Me';
    window.currentUser = currentUser;

    const notificationSound = new Audio('/sounds/message-notification.mp3');

    // Bridge shared state for modular scripts (e.g. messenger-calls.js, messenger-stickers.js)
    window.MessengerState = {
        get stompClient() { return stompClient; },
        get currentPartnerId() { return currentPartnerId; },
        get currentPartnerName() { return currentPartnerName; },
        get currentUser() { return currentUser; },
        sendApiRequest: function(payload, tempId) { return sendApiRequest(payload, tempId); },
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
        if (typeof window.initStickerSuggestions === 'function') {
            window.initStickerSuggestions();
        }
        
        // Close suggestions khi click outside
        $(document).on('click', function(e) {
            if (!$(e.target).closest('.sticker-suggestions, #msgInput').length) {
                if (typeof window.hideStickerSuggestions === 'function') {
                    window.hideStickerSuggestions();
                }
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

    // --- 1. WEBSOCKET & DEDUPLICATION ---
    let activeSubscriptions = [];
    let isConnectingSocket = false;
    let reconnectTimeout = null;
    const processedEvents = new Map();

    function isDuplicateEvent(key, ttlMs = 4000) {
        if (!key) return false;
        const now = Date.now();
        if (processedEvents.size > 200) {
            for (let [k, ts] of processedEvents) {
                if (now - ts > ttlMs) processedEvents.delete(k);
            }
        }
        if (processedEvents.has(key)) {
            const lastTs = processedEvents.get(key);
            if (now - lastTs < ttlMs) return true;
        }
        processedEvents.set(key, now);
        return false;
    }

    function connectWebSocket() {
        if (isConnectingSocket) return;
        isConnectingSocket = true;

        if (reconnectTimeout) {
            clearTimeout(reconnectTimeout);
            reconnectTimeout = null;
        }

        // Clean up previous client
        if (stompClient) {
            try {
                activeSubscriptions.forEach(sub => { try { sub.unsubscribe(); } catch (e) {} });
                activeSubscriptions = [];
                if (stompClient.connected) {
                    stompClient.disconnect();
                }
            } catch (e) {}
        }

        const socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        stompClient.debug = null;
        
        stompClient.connect({}, function(frame) {
            isConnectingSocket = false;
            activeSubscriptions = [];
            console.log('✅ WebSocket Connected:', frame);
            
            // 1. Subscribe đến private messages
            const subPrivateUser = stompClient.subscribe('/user/queue/private', function(payload) {
                try {
                    const msg = JSON.parse(payload.body);
                    handleSocketMessage(msg);
                } catch (e) {
                    console.error("Error parsing /user/queue/private frame", e);
                }
            });
            activeSubscriptions.push(subPrivateUser);

            if (currentUser.userID) {
                const subPrivateTopic = stompClient.subscribe(`/topic/user.${currentUser.userID}.private`, function(payload) {
                    try {
                        const msg = JSON.parse(payload.body);
                        handleSocketMessage(msg);
                    } catch (e) {
                        console.error("Error parsing /topic/user private frame", e);
                    }
                });
                activeSubscriptions.push(subPrivateTopic);
            }
            
            // 2. Subscribe đến typing notifications
            const subTypingUser = stompClient.subscribe('/user/queue/typing', function(payload) {
                try {
                    const data = JSON.parse(payload.body);
                    handleTypingNotification(data);
                } catch (e) {}
            });
            activeSubscriptions.push(subTypingUser);

            if (currentUser.userID) {
                const subTypingTopic = stompClient.subscribe(`/topic/user.${currentUser.userID}.typing`, function(payload) {
                    try {
                        const data = JSON.parse(payload.body);
                        handleTypingNotification(data);
                    } catch (e) {}
                });
                activeSubscriptions.push(subTypingTopic);
            }
            
            // 3. Subscribe đến seen notifications
            const subSeenUser = stompClient.subscribe('/user/queue/seen', function(payload) {
                try {
                    const data = JSON.parse(payload.body);
                    if (data.type === 'SEEN_ALL') {
                        handleSocketMessage(data);
                    } else {
                        updateSeenAvatar(data.messageId, data.seenBy);
                    }
                } catch (e) {}
            });
            activeSubscriptions.push(subSeenUser);

            if (currentUser.userID) {
                const subSeenTopic = stompClient.subscribe(`/topic/user.${currentUser.userID}.seen`, function(payload) {
                    try {
                        const data = JSON.parse(payload.body);
                        if (data.type === 'SEEN_ALL') {
                            handleSocketMessage(data);
                        } else {
                            updateSeenAvatar(data.messageId, data.seenBy);
                        }
                    } catch (e) {}
                });
                activeSubscriptions.push(subSeenTopic);
            }
            
            // 4. Subscribe đến online status updates (broadcast toàn hệ thống và private)
            const subOnlineTopic = stompClient.subscribe('/topic/online-status', function(payload) {
                try {
                    const data = JSON.parse(payload.body);
                    updateOnlineStatus(data.userId, data.isOnline, data.lastActive, data.lastActiveTimestamp);
                } catch (e) {}
            });
            activeSubscriptions.push(subOnlineTopic);

            const subOnlineUser = stompClient.subscribe('/user/queue/online-status', function(payload) {
                try {
                    const data = JSON.parse(payload.body);
                    updateOnlineStatus(data.userId, data.isOnline, data.lastActive, data.lastActiveTimestamp);
                } catch (e) {}
            });
            activeSubscriptions.push(subOnlineUser);

            // 5. Subscribe đến call notifications (delegated to messenger-calls.js)
            const subCallUser = stompClient.subscribe('/user/queue/call', function(payload) {
                try {
                    const data = JSON.parse(payload.body);
                    if (window.MessengerCalls) {
                        window.MessengerCalls.handleIncomingCall(data);
                    }
                } catch (e) {}
            });
            activeSubscriptions.push(subCallUser);

            if (currentUser.userID) {
                const subCallTopic = stompClient.subscribe(`/topic/user.${currentUser.userID}.call`, function(payload) {
                    try {
                        const data = JSON.parse(payload.body);
                        if (window.MessengerCalls) {
                            window.MessengerCalls.handleIncomingCall(data);
                        }
                    } catch (e) {}
                });
                activeSubscriptions.push(subCallTopic);
            }
            
            // Gửi ping để báo online
            stompClient.send('/app/online/ping', {}, JSON.stringify({
                userId: currentUser.userID
            }));
            
            // Thông báo kết nối thành công
            showToast("Đã kết nối thời gian thực", "success");
            
        }, function(error) {
            isConnectingSocket = false;
            console.error('WebSocket Error:', error);
            reconnectTimeout = setTimeout(connectWebSocket, 5000);
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

    function handleTypingNotification(data) {
        if (!data || !currentPartnerId) return;
        if (parseInt(data.senderId) === parseInt(currentPartnerId)) {
            if (data.type === 'TYPING' || data.typing === true) {
                showTypingIndicator(data.senderName || currentPartnerName);
            } else {
                hideTypingIndicator();
            }
        }
    }

    function handleSocketMessage(msg) {
        if (!msg) return;
        console.log("Socket message received:", msg);
        
        // 1. Xử lý Tín hiệu Gọi (delegated to messenger-calls.js)
        if (window.MessengerCalls && window.MessengerCalls.handleCallSocketMessage(msg)) {
            return;
        }

        // 2. Reaction events
        if (msg.type === 'REACTION' && msg.messageId) {
            const reactionKey = `REACTION:${msg.messageId}:${JSON.stringify(msg.reactions)}`;
            if (isDuplicateEvent(reactionKey)) return;
            updateMessageReactions(msg.messageId, msg.reactions);
            return;
        }

        // 3. Unsend events
        if (msg.type === 'UNSEND' && msg.messageId) {
            const unsendKey = `UNSEND:${msg.messageId}`;
            if (isDuplicateEvent(unsendKey)) return;
            const row = $(`#msg-${msg.messageId}`);
            if (row.length) {
                row.find('.bubble').addClass('deleted').text('Tin nhắn đã bị thu hồi');
                row.find('.msg-actions').remove();
            }
            return;
        }

        // 4. Pin events
        if (msg.type === 'PIN' && msg.messageId) {
            const pinKey = `PIN:${msg.messageId}:${msg.isPinned}`;
            if (isDuplicateEvent(pinKey)) return;
            const row = $(`#msg-${msg.messageId}`);
            if (row.length) {
                row.toggleClass('pinned', !!msg.isPinned);
            }
            if (typeof window.loadPinnedMessages === 'function') {
                window.loadPinnedMessages();
            }
            return;
        }

        // 5. Seen All events
        if (msg.type === 'SEEN_ALL') {
            const seenKey = `SEEN_ALL:${msg.seenBy}`;
            if (isDuplicateEvent(seenKey, 1000)) return;
            if (currentPartnerId && currentPartnerId == msg.seenBy) {
                $('.msg-row.mine .seen-avatar').remove();
                const lastMine = $('.msg-row.mine').last();
                if (lastMine.length) {
                    const partnerAvatar = $('#headerAvatar').attr('src') || '/images/placeholder-user.jpg';
                    lastMine.find('.msg-bubble-wrap').append(`<img src="${partnerAvatar}" class="seen-avatar" style="width:14px; height:14px; border-radius:50%; margin-top:2px; align-self:flex-end;" title="Đã xem">`);
                }
            }
            return;
        }

        // 6. Friend status update
        if (msg.type === 'FRIEND_STATUS') {
            const friendKey = `FRIEND_STATUS:${msg.partnerId}:${msg.relationStatus}`;
            if (isDuplicateEvent(friendKey, 2000)) return;
            if (currentPartnerId && currentPartnerId == msg.partnerId) {
                if (msg.relationStatus === 'FRIEND') {
                    isCurrentPartnerFriend = true;
                    $('#strangerBanner').remove();
                    $('#headerName span').remove();
                    $('#chatHeaderStatus').html(`<small class="text-success"><i class="fas fa-circle" style="font-size:8px;"></i> Đang hoạt động</small>`);
                    showToast('Đã trở thành bạn bè!', 'success');
                } else {
                    isCurrentPartnerFriend = false;
                    window.renderStrangerBanner(currentPartnerId, 'STRANGER');
                }
            }
            loadConversations();
            return;
        }

        // 7. Chat messages - deduplication check
        if (msg.id) {
            const msgKey = `MSG:${msg.id}`;
            if (isDuplicateEvent(msgKey, 3000)) return;
        }

        const myId = parseInt(currentUser.userID || currentUser.id || 0);
        const senderId = parseInt(msg.senderId);
        const partnerId = (senderId === myId) ? parseInt(msg.receiverId) : senderId;

        // Nếu đang xem chat này:
        if (currentPartnerId && currentPartnerId == partnerId) {
            // Deduplication: nếu tin nhắn đã có trong DOM thì bỏ qua
            if (msg.id && $(`#msg-${msg.id}`).length) {
                return;
            }
            if (msg.type === 'CALL_END') {
                let metaCallId = null;
                try {
                    const m = typeof msg.metadata === 'string' ? JSON.parse(msg.metadata) : msg.metadata;
                    if (m && m.callId) metaCallId = m.callId;
                } catch (e) {}
                if (metaCallId && $(`.msg-row[data-call-id="${metaCallId}"]`).length) {
                    return;
                }
            }
            // Nếu là tin nhắn của mình và có tin nhắn tạm thì cập nhật thay vì thêm mới
            if (senderId === myId) {
                let tempRow = $(`#messagesContainer .msg-row.mine[data-status="sending"]`).last();
                if (!tempRow.length) {
                    tempRow = $(`#messagesContainer .msg-row.mine[id^="msg-temp-"]`).last();
                }
                if (tempRow.length) {
                    const tempText = tempRow.find('.bubble').text().trim();
                    const tempImg = tempRow.find('.msg-content img, .bubble img');
                    const tempImgSrc = tempImg.length ? tempImg.attr('src') : null;
                    const isContentMatch = (tempText && tempText === (msg.content || '').trim());
                    const isMediaMatch = (tempImgSrc && msg.content && (tempImgSrc === msg.content || msg.content.includes(tempImgSrc)));
                    const isMediaOrStickerType = (msg.type === 'STICKER' || msg.type === 'GIF' || msg.type === 'IMAGE') && tempImg.length > 0;

                    if (isContentMatch || isMediaMatch || isMediaOrStickerType || (!tempText && !tempImgSrc)) {
                        tempRow.attr('id', `msg-${msg.id}`).attr('data-msg-id', msg.id).removeAttr('data-status');
                        tempRow.find('.msg-meta').text(formatSmartTimestamp(msg.timestamp || new Date()));
                        return;
                    }
                }
            }
            
            appendMessageToUI(msg, senderId === myId);
            
            if (senderId !== myId) {
                markAsRead(msg.id);
                // Phát âm thanh thông báo
                notificationSound.play().catch(() => {});
            }
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
        const partnerId = (parseInt(msg.senderId) === parseInt(currentUser.userID)) 
            ? parseInt(msg.receiverId) 
            : parseInt(msg.senderId);
            
        let convItem = $(`#conv-${partnerId}`);
        if (!convItem.length) {
            convItem = $(`.conv-item[data-partner-id="${partnerId}"]`);
        }
        
        if (convItem.length) {
            const isMine = (parseInt(msg.senderId) === parseInt(currentUser.userID));
            const prefix = isMine ? 'Bạn: ' : '';
            let preview = '';
            if (msg.type === 'TEXT') preview = prefix + msg.content;
            else if (msg.type === 'IMAGE') preview = prefix + 'Đã gửi 1 ảnh';
            else if (msg.type === 'STICKER') preview = prefix + 'Đã gửi 1 nhãn dán';
            else if (msg.type === 'GIF') preview = prefix + 'Đã gửi 1 GIF';
            else if (msg.type === 'AUDIO') preview = prefix + 'Đã gửi 1 tin nhắn thoại';
            else if (msg.type === 'CALL_END') {
                const isVideo = (msg.mediaUrl === 'VIDEO' || (msg.content && msg.content.toLowerCase().includes('video')));
                const callStatus = (msg.callStatus || '').toUpperCase();
                if (callStatus === 'MISSED') {
                    preview = isMine ? (isVideo ? 'Cuộc gọi video không phản hồi' : 'Cuộc gọi thoại không phản hồi')
                                     : (isVideo ? 'Cuộc gọi video nhỡ' : 'Cuộc gọi thoại nhỡ');
                } else if (callStatus === 'REJECTED') {
                    preview = isVideo ? 'Cuộc gọi video bị từ chối' : 'Cuộc gọi thoại bị từ chối';
                } else {
                    preview = prefix + (isVideo ? 'Cuộc gọi video' : 'Cuộc gọi thoại');
                }
            }
            else preview = prefix + 'Đã gửi 1 tệp';
            
            convItem.find('.conv-preview').text(preview);
            convItem.prependTo('#conversationList'); // Move to top
            
            if (!isMine && (!currentPartnerId || currentPartnerId !== partnerId)) {
                convItem.addClass('unread');
                let badge = convItem.find('.unread-badge');
                if (!badge.length) {
                    convItem.append('<div class="unread-badge">1</div>');
                } else {
                    let count = parseInt(badge.text()) || 0;
                    badge.text(count + 1);
                }
            }
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
                    const badgeText = (!c.online) ? formatRelativeTimeBadge(c.lastActiveTimestamp, c.lastActive) : null;
                    const badgeHtml = badgeText ? `<span class="last-active-badge">${badgeText}</span>` : '';
                    const dotHtml = `<div class="online-dot ${c.online ? 'is-online' : ''}" style="${c.online ? '' : 'display:none;'}"></div>`;

                    const item = $(`
                        <div class="conv-item ${active} ${unread} d-flex align-items-center p-2"
                            id="conv-${c.partnerId}" data-partner-id="${c.partnerId}"
                            style="cursor:pointer; border-bottom:1px solid #333;">

                            <div class="avatar-wrapper" style="position:relative; margin-right:10px;">
                                <img src="${avatar}" style="width:48px; height:48px; border-radius:50%; object-fit:cover;">
                                ${dotHtml}
                                ${badgeHtml}
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
                            <button type="button" class="conv-more-btn" title="Tùy chọn">
                                <i class="fas fa-ellipsis-h"></i>
                            </button>
                        </div>
                    `);

                    item.on('click', function(e) {
                        if ($(e.target).closest('.conv-more-btn, .conv-action-dropdown').length) return;
                        window.selectConversation(c.partnerId, c.partnerName, avatar, isFriendStr, c.online, c.lastActive, c.relationStatus, c.lastActiveTimestamp);
                    });

                    item.find('.conv-more-btn').on('click', function(e) {
                        e.stopPropagation();
                        e.preventDefault();
                        window.toggleConvMenu(e, c.partnerId);
                    });

                    list.append(item);
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
                    showToast('Đã hủy lời mời kết bạn', 'info');
                }
            });
    };

    window.acceptFriendRequest = function(partnerId, btnElement) {
        const id = partnerId || currentPartnerId;
        const btn = btnElement || document.querySelector('.btn-stranger-add');
        if (!id || !btn) return;

        btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
        fetch(`/social/accept-friend/${id}`, { method: 'POST' })
            .then(res => res.ok ? res.json() : Promise.reject())
            .then(() => {
                showToast('Đã trở thành bạn bè', 'success');
                isCurrentPartnerFriend = true;
                $('#strangerBanner').remove();
                loadConversations();
            })
            .catch(() => {
                showToast('Lỗi chấp nhận kết bạn', 'error');
            });
    };

    window.blockUser = function(partnerId) {
        const id = partnerId || currentPartnerId;
        if (!id) return;
        if (!confirm('Bạn có chắc chắn muốn chặn người dùng này không?')) return;
        $.post(`/api/v1/messenger/block/${id}`)
            .done(function() {
                showToast('Đã chặn người dùng thành công', 'success');
                window.renderStrangerBanner(id, 'STRANGER');
                if (typeof loadConversations === 'function') {
                    loadConversations();
                }
            })
            .fail(function() {
                showToast('Lỗi khi chặn người dùng', 'error');
            });
    };

    window.renderStrangerBanner = function(partnerId, status) {
        $('#strangerBanner').remove();
        if (status === 'FRIEND') return;

        let btnHtml = '';
        if (status === 'PENDING_SENT') {
            btnHtml = `<button class="btn-stranger-add btn-stranger-pending" onclick="window.cancelFriendRequest(${partnerId}, this)"><i class="fas fa-clock"></i> Đã gửi</button>`;
        } else if (status === 'PENDING_RECEIVED') {
            btnHtml = `<button class="btn-stranger-add" onclick="window.acceptFriendRequest(${partnerId}, this)"><i class="fas fa-check"></i> Chấp nhận</button>`;
        } else {
            btnHtml = `<button class="btn-stranger-add" onclick="window.sendFriendRequest(${partnerId}, this)"><i class="fas fa-user-plus"></i> Kết bạn</button>`;
        }

        const banner = `
            <div id="strangerBanner" class="stranger-alert-bar">
                <div class="stranger-content">
                    <i class="fas fa-user-shield"></i>
                    <span>Tin nhắn từ người lạ. Hãy cẩn thận khi chia sẻ thông tin.</span>
                </div>
                <div class="stranger-actions">
                    ${btnHtml}
                    <button class="btn-stranger-block" onclick="window.blockUser(${partnerId})">Chặn</button>
                </div>
            </div>
        `;
        $('#messagesContainer').before(banner);
    };

    // --- 3. SELECT AND LOAD THEME KHI CHỌN CONVERSATION ---
    window.selectConversation = function(partnerId, name, avatar, isFriend, isOnline, lastActive, relationStatus, lastActiveTimestamp) {
        currentPartnerId = parseInt(partnerId);
        currentPartnerName = name;
        isCurrentPartnerFriend = (String(isFriend) === 'true' || relationStatus === 'FRIEND');

        // Persist active conversation across refresh & update URL
        if (currentPartnerId) {
            sessionStorage.setItem('activeMessengerPartnerId', currentPartnerId);
            if (window.history && window.history.replaceState) {
                window.history.replaceState(null, '', '/messenger?uid=' + currentPartnerId);
            }
        }

        // UI Updates
        $('#emptyState').hide();
        $('#chatInterface').show();
        if (typeof window.closeInlineChatSearch === 'function') {
            window.closeInlineChatSearch();
        }
        updateInfoSidebar(name, avatar);

        // Only refresh media if info sidebar is already open by user choice
        if (!$('#chatInfoSidebar').hasClass('hidden')) {
            loadSharedMedia();
        }

        // Load theme và settings từ server
        $('#messagesContainer').css('background-image', '');
        $.get(`/api/v1/messenger/settings/${partnerId}`)
            .done(function(settings) {
                if (settings.themeColor && settings.themeColor !== '#0084ff') {
                    window.applyTheme(settings.themeColor);
                } else {
                    // Reset về mặc định
                    document.documentElement.style.setProperty('--msg-blue', '#0084ff');
                }
                if (settings.nickname) {
                    $('#headerName').text(settings.nickname);
                    $('#infoName').text(settings.nickname);
                }
                if (settings.customBackgroundUrl) {
                    $('#messagesContainer').css({
                        'background-image': `url('${settings.customBackgroundUrl}')`,
                        'background-size': 'cover',
                        'background-position': 'center'
                    });
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
            statusDiv.html(formatStatusText(isOnline, lastActiveTimestamp, lastActive));
        } else {
            statusDiv.empty();
        }

        // Cập nhật trạng thái trong info sidebar
        const infoStatus = $('.chat-info-sidebar .info-status');
        if (infoStatus.length) {
            if (String(isOnline) === 'true') {
                infoStatus.text('Đang hoạt động').css('color', '#31a24c');
            } else {
                const badge = formatRelativeTimeBadge(lastActiveTimestamp, lastActive);
                infoStatus.text(badge ? ('Hoạt động ' + badge + ' trước') : 'Không hoạt động').css('color', '#888');
            }
        }

        // [FIX] Banner Zalo (Vàng) - Chỉ hiện khi là người lạ
        if (isCurrentPartnerFriend || relationStatus === 'FRIEND') {
            $('#strangerBanner').remove();
        } else {
            window.renderStrangerBanner(partnerId, relationStatus || 'STRANGER');
            // Query real relation status from server to guarantee sync
            $.get(`/api/v1/messenger/relation/${partnerId}`).done(function(res) {
                if (res && res.relationStatus) {
                    if (res.relationStatus === 'FRIEND') {
                        isCurrentPartnerFriend = true;
                        $('#strangerBanner').remove();
                    } else {
                        window.renderStrangerBanner(partnerId, res.relationStatus);
                    }
                }
            });
        }

        // Active Sidebar & Load
        $('.conv-item').removeClass('active');
        if ($(`#conv-${partnerId}`).length === 0) {
            const isOnlineBool = String(isOnline) === 'true';
            const dotHtml = `<div class="online-dot ${isOnlineBool ? 'is-online' : ''}" style="${isOnlineBool ? '' : 'display:none;'}"></div>`;
            const newConvItem = $(`
                <div class="conv-item active d-flex align-items-center p-2"
                    id="conv-${partnerId}" data-partner-id="${partnerId}"
                    style="cursor:pointer; border-bottom:1px solid #333;">

                    <div class="avatar-wrapper" style="position:relative; margin-right:10px;">
                        <img src="${avatar}" style="width:48px; height:48px; border-radius:50%; object-fit:cover;">
                        ${dotHtml}
                    </div>

                    <div class="flex-grow-1" style="min-width:0;">
                        <div class="d-flex justify-content-between align-items-center">
                            <strong class="conv-name" style="color:#fff; font-size:0.95rem; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">
                                ${name}
                            </strong>
                            <small class="text-muted" style="font-size:0.75rem;">Mới</small>
                        </div>
                        <div class="conv-preview text-muted small text-truncate" style="color:#aaa;">
                            Bắt đầu cuộc trò chuyện mới
                        </div>
                    </div>

                    <button type="button" class="conv-more-btn" title="Tùy chọn">
                        <i class="fas fa-ellipsis-h"></i>
                    </button>
                </div>
            `);

            newConvItem.on('click', function(e) {
                if ($(e.target).closest('.conv-more-btn, .conv-action-dropdown').length) return;
                window.selectConversation(partnerId, name, avatar, isFriend, isOnline, lastActive, relationStatus, lastActiveTimestamp);
            });

            newConvItem.find('.conv-more-btn').on('click', function(e) {
                e.stopPropagation();
                e.preventDefault();
                window.toggleConvMenu(e, partnerId);
            });

            $('#conversationList .empty-conversations').remove();
            $('#conversationList').prepend(newConvItem);
        } else {
            $(`#conv-${partnerId}`).addClass('active');
        }
        loadChatHistory(partnerId);
        $('.messenger-container').addClass('show-chat');
    };

    let currentChatHistoryXhr = null;

    function loadChatHistory(partnerId) {
        if (currentChatHistoryXhr) {
            try { currentChatHistoryXhr.abort(); } catch (e) {}
            currentChatHistoryXhr = null;
        }

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

        currentChatHistoryXhr = $.get(`/api/v1/messenger/chat/${partnerId}`, function(msgs) {
            if (parseInt(partnerId) !== parseInt(currentPartnerId)) return;
            container.empty();
            
            // Nếu trống -> Hiện banner chào
            if(!msgs || msgs.length === 0) {
                let bannerText = isCurrentPartnerFriend ? 'Hãy gửi lời chào!' : 'Gửi lời chào để bắt đầu kết nối.';
                container.html(`<div class="text-center mt-5 text-muted"><small>${bannerText}</small></div>`);
                return;
            }
            msgs.forEach(m => appendMessageToUI(m));
            scrollToBottom();

            // Khởi tạo reaction system an toàn
            initReactionSystem();
        }).fail(function(jqXHR, textStatus) {
            if (textStatus === 'abort') return;
            if (parseInt(partnerId) !== parseInt(currentPartnerId)) return;
            container.html('<div class="text-center mt-5 text-danger"><small><i class="fas fa-exclamation-triangle mr-1"></i> Không thể tải tin nhắn. Vui lòng thử lại sau.</small></div>');
        });
    }

    // --- 4. RENDER UI (DÙNG CẤU TRÚC FILE CŨ CỦA BẠN) ---
    function appendMessageToUI(msg, forceMine = false) {
        if (!msg) return;
        const myId = parseInt(currentUser.userID);
        let isMine = forceMine || (msg.senderId != currentPartnerId);
        const typeClass = isMine ? 'mine' : 'other';
        const msgId = msg.id || ('temp-' + Date.now());
        
        // Deduplication check
        if (msg.id) {
            const existing = $(`#msg-${msg.id}`);
            if (existing.length) {
                if (msg.reactions) updateMessageReactions(msg.id, msg.reactions);
                return;
            }
            if (isMine) {
                const tempEl = $(`#messagesContainer .msg-row.mine[data-status="sending"]`).first();
                if (tempEl.length) {
                    tempEl.attr('id', `msg-${msg.id}`).attr('data-msg-id', msg.id).removeAttr('data-status');
                    return;
                }
            }
        }

        // Reply block
        let replyHtml = '';
        if (msg.replyTo) {
            const rName = (msg.replyTo.senderId === myId) ? 'Bạn' : currentPartnerName;
            let rContent = msg.replyTo.type === 'TEXT' ? msg.replyTo.content : '[Đính kèm]';
            if (rContent.length > 40) rContent = rContent.substring(0, 40) + '...';
            
            replyHtml = `
                <div class="reply-block" onclick="window.scrollToMessage('${msg.replyTo.id}')">
                    <div class="reply-name">${rName}</div>
                    <div>${rContent}</div>
                </div>
            `;
        }

        // Content
        let contentHtml = '';
        if (msg.isDeleted) {
            contentHtml = '<div class="bubble deleted" style="font-style:italic; opacity:0.6;">Tin nhắn đã bị thu hồi</div>';
        } else if (msg.type === 'CALL_END') {
            const isVideo = (msg.mediaUrl === 'VIDEO' || (msg.content && msg.content.toLowerCase().includes('video')));
            const callIconClass = isVideo ? 'fa-video' : 'fa-phone-alt';
            const durationSec = msg.callDuration || 0;
            let durationText = '';
            if (durationSec > 0) {
                const mins = Math.floor(durationSec / 60);
                const secs = durationSec % 60;
                durationText = `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
            }

            let statusClass = 'completed';
            let titleText = '';
            let metaText = '';

            const callStatus = (msg.callStatus || '').toUpperCase();
            if (callStatus === 'MISSED') {
                statusClass = 'missed';
                titleText = isMine
                    ? (isVideo ? 'Cuộc gọi video không phản hồi' : 'Cuộc gọi thoại không phản hồi')
                    : (isVideo ? 'Cuộc gọi video nhỡ' : 'Cuộc gọi thoại nhỡ');
                metaText = 'Nhỡ';
            } else if (callStatus === 'REJECTED') {
                statusClass = 'rejected';
                titleText = isMine
                    ? (isVideo ? 'Cuộc gọi video bị từ chối' : 'Cuộc gọi thoại bị từ chối')
                    : (isVideo ? 'Đã từ chối cuộc gọi video' : 'Đã từ chối cuộc gọi thoại');
                metaText = 'Đã từ chối';
            } else {
                statusClass = 'completed';
                titleText = isMine
                    ? (isVideo ? 'Cuộc gọi video đi' : 'Cuộc gọi thoại đi')
                    : (isVideo ? 'Cuộc gọi video đến' : 'Cuộc gọi thoại đến');
                metaText = durationText ? `Hoàn thành · ${durationText}` : 'Đã kết thúc';
            }

            const partnerToCall = isMine ? (msg.receiverId || currentPartnerId) : (msg.senderId || currentPartnerId);

            contentHtml = `
                <div class="msg-call-bubble">
                    <div class="msg-call-icon ${statusClass}">
                        <i class="fas ${callIconClass}"></i>
                    </div>
                    <div class="msg-call-info">
                        <div class="msg-call-title ${statusClass}">${titleText}</div>
                        <div class="msg-call-meta">
                            <span>${metaText}</span>
                            ${msg.formattedTime ? `<span>· ${msg.formattedTime}</span>` : ''}
                        </div>
                    </div>
                    <button class="msg-call-btn" onclick="window.callbackFromMessage(${partnerToCall}, ${isVideo})">
                        <i class="fas fa-phone-alt"></i> Gọi lại
                    </button>
                </div>
            `;
        } else if (msg.type === 'IMAGE' || msg.type === 'STICKER' || msg.type === 'GIF') {
            const imgClass = msg.type === 'STICKER' ? 'msg-sticker' : (msg.type === 'GIF' ? 'msg-gif' : 'msg-image');
            contentHtml = `<img src="${msg.content}" class="${imgClass}" onclick="window.open('${msg.content}')" style="max-width:240px; border-radius:10px; cursor:pointer;">`;
        } else if (msg.type === 'AUDIO') {
            contentHtml = renderAudioPlayer(msg.content, msg.id);
            setTimeout(() => {
                if (msg.id) {
                    window.initAudioDuration(`audio-player-${msg.id}`);
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

        // Reactions
        let reactionsHtml = '';
        if (msg.reactions && Object.keys(msg.reactions).length > 0) {
            reactionsHtml = '<div class="message-reactions">';
            Object.entries(msg.reactions).forEach(([emoji, count]) => {
                reactionsHtml += `
                    <div class="reaction-item" onclick="window.addReaction('${msgId}', '${emoji}')">
                        <span>${emoji}</span>
                        <span class="reaction-count">${count}</span>
                    </div>
                `;
            });
            reactionsHtml += '</div>';
        }

        // Action Buttons
        let actionButtons = '';
        if (msg.type === 'CALL_END') {
            actionButtons = '';
        } else if (isMine) {
            actionButtons = `
                <div class="action-btn" title="Chuyển tiếp" onclick="window.forwardMessage('${msgId}')">
                    <i class="fas fa-share"></i>
                </div>
                <div class="action-btn" title="Ghim" onclick="window.togglePinMessage('${msgId}')">
                    <i class="fas fa-thumbtack"></i>
                </div>
                <div class="action-btn" title="Trả lời" onclick="window.startReply('${msgId}', 'Bạn', '${(msg.content||'').replace(/'/g, "\\'").substring(0,50)}')">
                    <i class="fas fa-reply"></i>
                </div>
                <div class="action-btn action-react-btn" title="Thả cảm xúc" onclick="window.showReactionPicker(this, '${msgId}')">
                    <i class="far fa-smile"></i>
                </div>
                <div class="action-btn" title="Thu hồi" onclick="window.unsendMessage('${msgId}')">
                    <i class="fas fa-trash"></i>
                </div>
            `;
        } else {
            actionButtons = `
                <div class="action-btn" title="Chuyển tiếp" onclick="window.forwardMessage('${msgId}')">
                    <i class="fas fa-share"></i>
                </div>
                <div class="action-btn" title="Trả lời" onclick="window.startReply('${msgId}', '${currentPartnerName.replace(/'/g, "\\'")}', '${(msg.content||'').replace(/'/g, "\\'").substring(0,50)}')">
                    <i class="fas fa-reply"></i>
                </div>
                <div class="action-btn action-react-btn" title="Thả cảm xúc" onclick="window.showReactionPicker(this, '${msgId}')">
                    <i class="far fa-smile"></i>
                </div>
            `;
        }

        const actionsHtml = `
            <div class="msg-actions">
                ${actionButtons}
            </div>
        `;

        // Avatar
        let avatarHtml = !isMine ? `<img src="${$('#headerAvatar').attr('src')}" class="avatar-img" style="width: 28px; height: 28px;">` : '';

        let callIdAttr = '';
        if (msg.type === 'CALL_END') {
            let cid = null;
            if (msg.metadata) {
                try {
                    const m = typeof msg.metadata === 'string' ? JSON.parse(msg.metadata) : msg.metadata;
                    if (m && m.callId) cid = m.callId;
                } catch(e) {}
            }
            if (!cid && window.activeCallSessionId) cid = window.activeCallSessionId;
            if (cid) callIdAttr = `data-call-id="${cid}"`;
        }

        const statusAttr = msg.status ? `data-status="${msg.status}"` : '';
        const html = `
            <div class="msg-row ${typeClass}" id="msg-${msgId}" data-msg-id="${msgId}" ${callIdAttr} ${statusAttr}>
                ${avatarHtml}
                <div class="msg-content">${contentHtml}${reactionsHtml}</div>
                ${actionsHtml}
            </div>
        `;
        
        $('#messagesContainer').append(html);
        scrollToBottom();
    }

    window.appendMessageToUI = appendMessageToUI;

    window.callbackFromMessage = function(partnerId, isVideo) {
        if (partnerId && partnerId !== currentPartnerId && window.selectConversation) {
            window.selectConversation(partnerId);
            setTimeout(() => {
                if (isVideo && window.startVideoCall) window.startVideoCall();
                else if (window.startVoiceCall) window.startVoiceCall();
            }, 300);
        } else {
            if (isVideo && window.startVideoCall) window.startVideoCall();
            else if (window.startVoiceCall) window.startVoiceCall();
        }
    };

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
            uploadAndSend(pendingFile.file, pendingFile.type, content);
            return;
        }

        // Nếu không có file, mới kiểm tra text
        if (content && currentPartnerId) {
            const tempId = 'temp-' + Date.now();
            // Optimistic UI: Hiện tin nhắn ngay lập tức
            appendMessageToUI({
                id: tempId,
                senderId: currentUser.userID,
                content: content,
                type: 'TEXT',
                replyTo: replyToId ? { id: replyToId, senderId: currentPartnerId, content: $('#replyingBar').text(), type: 'TEXT' } : null,
                formattedTime: 'Đang gửi...',
                status: 'sending'
            }, true);

            // Gửi API kèm tempId
            sendApiRequest({ 
                receiverId: currentPartnerId, 
                content: content, 
                type: 'TEXT',
                replyToId: replyToId
            }, tempId);
            
            // Xóa ô nhập liệu
            $('#msgInput').val('').focus();
            window.cancelReply();
        }
    };

    window.sendSticker = function(url) {
        if (typeof window.sendChosenSticker === 'function') {
            window.sendChosenSticker(url, 'STICKER');
        } else {
            $('#stickerMenu').hide();
            if (!currentPartnerId) return;
            const tempId = 'temp-' + Date.now();
            appendMessageToUI({
                id: tempId,
                senderId: (currentUser ? (currentUser.userID || currentUser.id) : 0),
                content: url,
                type: 'STICKER',
                formattedTime: 'Đang gửi...',
                status: 'sending'
            }, true);
            sendApiRequest({ receiverId: currentPartnerId, content: url, type: 'STICKER' }, tempId);
        }
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


    // ============= FIX 8: ADVANCED SEARCH SYSTEM =============
    window.openAdvancedSearch = function() {
        $('.search-modal-overlay, .search-modal').remove();
        const modal = $('<div class="modal-overlay search-modal-overlay"></div>');
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

        modal.append(content);
        modal.on('click', function(e) {
            if ($(e.target).is(modal)) window.closeAdvancedSearch();
        });
        $('body').append(modal);
        
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

        $('.stats-modal-overlay, .stats-modal').remove();
        const modal = $('<div class="modal-overlay stats-modal-overlay"></div>');
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

        modal.append(content);
        modal.on('click', function(e) {
            if ($(e.target).is(modal)) window.closeStatsModal();
        });
        $('body').append(modal);
        
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
        $('.theme-modal-overlay, .theme-modal').remove();
        const modal = $('<div class="modal-overlay theme-modal-overlay"></div>');
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

        modal.append(content);
        modal.on('click', function(e) {
            if ($(e.target).is(modal)) window.closeThemePicker();
        });
        $('body').append(modal);
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
        $('.nickname-modal-overlay, .nickname-modal').remove();
        const modal = $('<div class="modal-overlay nickname-modal-overlay"></div>');
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

        modal.append(content);
        modal.on('click', function(e) {
            if ($(e.target).is(modal)) window.closeNicknameModal();
        });
        $('body').append(modal);
        
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

    function sendApiRequest(payload, tempId) {
        console.log("sendApiRequest payload:", payload);
        
        $.ajax({
            url: '/api/v1/messenger/send',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            success: function(msg) {
                console.log("sendApiRequest success:", msg);
                
                // Cập nhật tin nhắn tạm thành tin nhắn thật
                if (tempId && $(`#msg-${tempId}`).length) {
                    const tempEl = $(`#msg-${tempId}`);
                    if ($(`#msg-${msg.id}`).length) {
                        tempEl.remove();
                    } else {
                        tempEl.attr('id', `msg-${msg.id}`).attr('data-msg-id', msg.id).removeAttr('data-status');
                        tempEl.find('.msg-actions').html(`
                            <div class="action-btn" title="Chuyển tiếp" onclick="window.forwardMessage('${msg.id}')">
                                <i class="fas fa-share"></i>
                            </div>
                            <div class="action-btn" title="Ghim" onclick="window.togglePinMessage('${msg.id}')">
                                <i class="fas fa-thumbtack"></i>
                            </div>
                            <div class="action-btn" title="Trả lời" onclick="window.startReply('${msg.id}', 'Bạn', '${(msg.content||'').replace(/'/g, "\\'").substring(0,50)}')">
                                <i class="fas fa-reply"></i>
                            </div>
                            <div class="action-btn action-react-btn" title="Thả cảm xúc" onclick="window.showReactionPicker(this, '${msg.id}')">
                                <i class="far fa-smile"></i>
                            </div>
                            <div class="action-btn" title="Thu hồi" onclick="window.unsendMessage('${msg.id}')">
                                <i class="fas fa-trash"></i>
                            </div>
                        `);
                        tempEl.find('.msg-meta').text(formatSmartTimestamp(msg.timestamp || new Date()));
                    }
                } else if (!$(`#msg-${msg.id}`).length) {
                    appendMessageToUI(msg, true);
                }
                
                scrollToBottom();
                updateConversationPreview(msg);
            },
            error: function(e) { 
                console.error("Send Error", e); 
                if (tempId && $(`#msg-${tempId}`).length) {
                    $(`#msg-${tempId} .bubble`).addClass('error').append('<span style="color:#ff4d4d;font-size:11px;display:block;">❌ Gửi thất bại</span>');
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
        
        let content = '';
        let type = 'TEXT';
        if (messageElement.find('img.msg-sticker').length) {
            content = messageElement.find('img.msg-sticker').attr('src');
            type = 'STICKER';
        } else if (messageElement.find('img.msg-gif').length) {
            content = messageElement.find('img.msg-gif').attr('src');
            type = 'GIF';
        } else if (messageElement.find('img.msg-image').length) {
            content = messageElement.find('img.msg-image').attr('src');
            type = 'IMAGE';
        } else if (messageElement.find('audio source').length) {
            content = messageElement.find('audio source').attr('src');
            type = 'AUDIO';
        } else if (messageElement.find('.msg-file a').length) {
            content = messageElement.find('.msg-file a').attr('href');
            type = 'FILE';
        } else {
            const bubbleClone = messageElement.find('.bubble').clone();
            bubbleClone.children('.reply-block, .message-reactions').remove();
            content = bubbleClone.text().trim();
            type = 'TEXT';
        }
        
        selectedMessageToForward = {
            id: messageId,
            content: content,
            type: type,
            sender: currentUser.name
        };
        
        console.log("Selected message to forward:", selectedMessageToForward);
        
        // Show forward modal
        showForwardModal();
    };

    window.showForwardModal = function() {
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
        
        modal.append(content);
        $('body').append(modal);
        
        // Load conversation list for forwarding
        loadForwardRecipients();
        
        // Search functionality
        $('#forwardSearchInput').on('input', function() {
            filterForwardRecipients($(this).val());
        });
    }

    window.closeForwardModal = function() {
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

            // Delegated click on row toggles checkbox
            container.off('click', '.recipient-item').on('click', '.recipient-item', function(e) {
                if (!$(e.target).is('input[type="checkbox"]')) {
                    const cb = $(this).find('input[name="forwardTo"]');
                    cb.prop('checked', !cb.prop('checked')).trigger('change');
                }
            });
            
            // Enable/disable forward button based on selection
            container.off('change', 'input[name="forwardTo"]').on('change', 'input[name="forwardTo"]', function() {
                const hasSelection = container.find('input[name="forwardTo"]:checked').length > 0;
                $('.btn-forward').prop('disabled', !hasSelection);
            });
        });
    }

    let forwardSearchTimeout = null;
    function filterForwardRecipients(query) {
        if (!query || !query.trim()) {
            $('.recipient-item').show();
            $('#forwardDiscoveredSection').remove();
            return;
        }
        
        const q = query.toLowerCase().trim();
        $('.recipient-item').each(function() {
            if ($(this).closest('#forwardDiscoveredSection').length) return;
            const name = $(this).find('.recipient-name').text().toLowerCase();
            $(this).toggle(name.includes(q));
        });

        if (forwardSearchTimeout) clearTimeout(forwardSearchTimeout);
        forwardSearchTimeout = setTimeout(() => {
            $.get(`/api/v1/messenger/users?q=${encodeURIComponent(q)}`).done(function(users) {
                $('#forwardDiscoveredSection').remove();
                if (!users || !users.length) return;

                const existingIds = new Set();
                $('.recipient-item').each(function() {
                    existingIds.add(parseInt($(this).data('id')));
                });
                if (currentPartnerId) existingIds.add(parseInt(currentPartnerId));

                const newUsers = users.filter(u => !existingIds.has(u.id));
                if (newUsers.length > 0) {
                    let discHtml = '<div id="forwardDiscoveredSection" style="margin-top:10px; border-top:1px solid #333; padding-top:5px;"><div style="font-size:0.75rem; color:#888; padding:5px 10px; font-weight:600;">NGƯỜI DÙNG KHÁC</div>';
                    newUsers.forEach(u => {
                        discHtml += `
                            <div class="recipient-item" data-id="${u.id}">
                                <label class="recipient-select">
                                    <input type="checkbox" name="forwardTo" value="${u.id}">
                                    <span class="checkmark"></span>
                                </label>
                                <div class="recipient-info">
                                    <img src="${u.avatar}" class="recipient-avatar">
                                    <div class="recipient-details">
                                        <div class="recipient-name">${escapeHtml(u.name)}</div>
                                        <div class="recipient-last-message">${escapeHtml(u.email || 'Người dùng mới')}</div>
                                    </div>
                                </div>
                            </div>
                        `;
                    });
                    discHtml += '</div>';
                    let list = $('#forwardRecipients .recipients-list');
                    if (!list.length) {
                        $('#forwardRecipients').html('<div class="recipients-list"></div>');
                        list = $('#forwardRecipients .recipients-list');
                    }
                    list.append(discHtml);

                    $('input[name="forwardTo"]').off('change.btnUpdate').on('change.btnUpdate', function() {
                        const hasSelection = $('input[name="forwardTo"]:checked').length > 0;
                        $('.btn-forward').prop('disabled', !hasSelection);
                    });
                }
            });
        }, 250);
    }

    window.executeForward = function() {
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
                type: selectedMessageToForward.type || 'TEXT'
            };
            
            $.ajax({
                url: '/api/v1/messenger/send',
                type: 'POST',
                contentType: 'application/json',
                data: JSON.stringify(payload),
                complete: function() {
                    completed++;
                    if (completed === total) {
                        closeForwardModal();
                        showToast(`Đã chuyển tiếp tin nhắn đến ${total} người`, 'success');
                        loadConversations();
                    }
                }
            });
        });
    };

    window.showForwardSuccess = function() {
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

        const uploadUrl = (type === 'FILE') ? '/api/upload/file' : '/api/upload/image';

        $.ajax({
            url: uploadUrl, 
            type: 'POST',
            data: formData,
            processData: false,
            contentType: false,
            success: function(res) {
                $(`#${tempId}`).remove();
                if(res.url) {
                    sendApiRequest({ 
                        receiverId: currentPartnerId, 
                        content: res.url, 
                        type: type
                    });
                    
                    updateConversationPreview({
                        senderId: currentUser.userID,
                        receiverId: currentPartnerId,
                        content: (type === 'FILE' ? 'Đã gửi 1 tệp đính kèm' : 'Đã gửi 1 ảnh'),
                        type: type
                    });
                    
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
            
            $('#mediaPreview').show().css('display', 'flex');
            
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
            window.startRecording();
        } else {
            window.finishRecording();
        }
    };

    // --- FIX 6: AUDIO PLAYER ---
    function renderAudioPlayer(audioUrl, messageId = null) {
        const playerId = messageId ? `audio-player-${messageId}` : `audio-player-${Date.now()}`;
        
        return `
            <div class="msg-audio-player" id="${playerId}">
                <button class="audio-play-btn" onclick="window.toggleAudioPlay('${playerId}')">
                    <i class="fas fa-play"></i>
                </button>
                <div class="audio-waveform-container" onclick="window.seekAudio(event, '${playerId}')">
                    <div class="audio-waveform-bars">
                        <span style="height: 35%"></span>
                        <span style="height: 70%"></span>
                        <span style="height: 50%"></span>
                        <span style="height: 90%"></span>
                        <span style="height: 65%"></span>
                        <span style="height: 30%"></span>
                        <span style="height: 85%"></span>
                        <span style="height: 45%"></span>
                        <span style="height: 95%"></span>
                        <span style="height: 60%"></span>
                        <span style="height: 40%"></span>
                        <span style="height: 75%"></span>
                    </div>
                    <div class="audio-progress-bar">
                        <div class="audio-progress-fill" id="${playerId}-progress"></div>
                    </div>
                </div>
                <div class="audio-time-display">
                    <span id="${playerId}-current-time">0:00</span>
                    <span class="audio-divider">/</span>
                    <span id="${playerId}-duration">0:00</span>
                </div>
                <audio id="${playerId}-audio" preload="metadata"
                    onloadedmetadata="window.initAudioDuration('${playerId}')"
                    ontimeupdate="window.updateAudioProgress('${playerId}')"
                    onended="window.onAudioEnded('${playerId}')">
                    <source src="${audioUrl}" type="audio/webm">
                    <source src="${audioUrl}" type="audio/mpeg">
                    Trình duyệt không hỗ trợ audio.
                </audio>
                <a href="${audioUrl}" download class="audio-download-btn" title="Tải xuống">
                    <i class="fas fa-download"></i>
                </a>
            </div>
        `;
    }

    function formatAudioTime(seconds) {
        if (!seconds || isNaN(seconds) || seconds === Infinity) return "0:00";
        const mins = Math.floor(seconds / 60);
        const secs = Math.floor(seconds % 60);
        return `${mins}:${secs.toString().padStart(2, '0')}`;
    }

    window.initAudioDuration = function(playerId) {
        const audio = document.getElementById(playerId + '-audio');
        if (!audio) return;
        
        const setDuration = () => {
            let duration = audio.duration;
            if (!duration || isNaN(duration) || duration === Infinity) {
                // Fix Chromium WebM duration infinity bug
                audio.currentTime = 1e101;
                audio.ontimeupdate = function() {
                    this.ontimeupdate = null;
                    audio.currentTime = 0;
                    duration = audio.duration;
                    if (duration && !isNaN(duration) && duration !== Infinity) {
                        $(`#${playerId}-duration`).text(formatAudioTime(duration));
                    }
                };
            } else {
                $(`#${playerId}-duration`).text(formatAudioTime(duration));
            }
        };

        if (audio.readyState >= 1) {
            setDuration();
        } else {
            audio.addEventListener('loadedmetadata', setDuration, { once: true });
        }
    };
    window.initAudioPlayer = window.initAudioDuration;

    window.toggleAudioPlay = function(playerId) {
        const audio = document.getElementById(playerId + '-audio');
        const player = $(`#${playerId}`);
        const btn = player.find('.audio-play-btn');
        const btnIcon = btn.find('i');
        if (!audio) return;

        // Pause any other playing audio
        $('audio').each(function() {
            if (this !== audio && !this.paused) {
                this.pause();
                const otherId = this.id.replace('-audio', '');
                $(`#${otherId}`).removeClass('playing');
                $(`#${otherId} .audio-play-btn`).removeClass('playing').find('i').removeClass('fa-pause').addClass('fa-play');
            }
        });

        if (audio.paused) {
            audio.play();
            player.addClass('playing');
            btn.addClass('playing');
            btnIcon.removeClass('fa-play').addClass('fa-pause');
        } else {
            audio.pause();
            player.removeClass('playing');
            btn.removeClass('playing');
            btnIcon.removeClass('fa-pause').addClass('fa-play');
        }
    };

    window.updateAudioProgress = function(playerId) {
        const audio = document.getElementById(playerId + '-audio');
        if (!audio || !audio.duration || isNaN(audio.duration)) return;
        const progress = (audio.currentTime / audio.duration) * 100;
        $(`#${playerId}-progress`).css('width', progress + '%');
        const cur = Math.floor(audio.currentTime);
        const mins = Math.floor(cur / 60);
        const secs = cur % 60;
        $(`#${playerId}-current-time`).text(`${mins}:${secs.toString().padStart(2, '0')}`);
    };

    window.seekAudio = function(event, playerId) {
        const audio = document.getElementById(playerId + '-audio');
        if (!audio || !audio.duration) return;
        const container = $(`#${playerId} .audio-waveform-container`)[0];
        if (!container) return;
        const rect = container.getBoundingClientRect();
        const x = event.clientX - rect.left;
        const ratio = Math.max(0, Math.min(1, x / rect.width));
        audio.currentTime = audio.duration * ratio;
        window.updateAudioProgress(playerId);
    };

    window.onAudioEnded = function(playerId) {
        const player = $(`#${playerId}`);
        player.removeClass('playing');
        player.find('.audio-play-btn').removeClass('playing').find('i').removeClass('fa-pause').addClass('fa-play');
        $(`#${playerId}-progress`).css('width', '0%');
        $(`#${playerId}-current-time`).text('0:00');
    };


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
                const searchInput = document.getElementById('emojiSearchInput');
                if (searchInput) searchInput.focus();
            }, 100);
            
            window.emojiPickerState.isOpen = true;
            window.emojiPickerState.picker = pickerContainer;
        }

        // Close picker
        function closePicker() {
            pickerContainer.style.opacity = '0';
            pickerContainer.style.transform = 'translateY(10px)';
            setTimeout(() => {
                pickerContainer.style.display = 'none';
                window.emojiPickerState.isOpen = false;
                hideEmojiTooltip();
            }, 300);
        }

        // Event listeners
        const trigger = document.getElementById('emojiTrigger');
        if (trigger) {
            $(trigger).off('click.emojipicker').on('click.emojipicker', function(e) {
                e.stopPropagation();
                e.preventDefault();
                if (!window.emojiPickerState.isOpen) {
                    openPicker();
                } else {
                    closePicker();
                }
            });
        }
        
        const closeBtn = document.getElementById('closeEmojiPicker');
        if (closeBtn) closeBtn.addEventListener('click', closePicker);
        
        // Search input
        let searchTimeout;
        const searchInput = document.getElementById('emojiSearchInput');
        if (searchInput) {
            searchInput.addEventListener('input', function(e) {
                clearTimeout(searchTimeout);
                searchTimeout = setTimeout(() => {
                    searchEmojis(e.target.value);
                }, 200);
            });
        }
        
        // Category tabs
        pickerContainer.querySelectorAll('.emoji-category-btn').forEach(btn => {
            btn.addEventListener('click', function() {
                scrollToCategory(this.dataset.category);
            });
        });
        
        // Scroll event
        const emojiContent = pickerContainer.querySelector('.emoji-content');
        if (emojiContent) emojiContent.addEventListener('scroll', updateActiveCategoryOnScroll);
        
        // Click outside to close
        document.addEventListener('click', function(e) {
            if (pickerContainer && !pickerContainer.contains(e.target) && e.target !== trigger && window.emojiPickerState.isOpen) {
                closePicker();
            }
        });
        
        // ESC key to close
        document.addEventListener('keydown', function(e) {
            if (e.key === 'Escape' && window.emojiPickerState.isOpen) {
                closePicker();
            }
        });

        // CRITICAL FIX: Append to document.body and register globally
        document.body.appendChild(pickerContainer);
        window.emojiPickerState.picker = pickerContainer;
        window.emojiPickerState.openPicker = openPicker;
        window.emojiPickerState.closePicker = closePicker;
        
        console.log('✅ Premium Emoji Picker initialized and attached to body');
    }

    // Tạo hàm open/close riêng
    function openEmojiPicker() {
        if (!window.emojiPickerState.picker || !document.body.contains(window.emojiPickerState.picker)) {
            initEmojiPicker();
        }
        if (window.emojiPickerState.openPicker) {
            window.emojiPickerState.openPicker();
        } else {
            const picker = window.emojiPickerState.picker;
            if (picker) {
                picker.style.display = 'flex';
                setTimeout(() => {
                    picker.style.opacity = '1';
                    picker.style.transform = 'translateY(0)';
                }, 10);
                window.emojiPickerState.isOpen = true;
            }
        }
    }

    function closeEmojiPicker() {
        if (window.emojiPickerState.closePicker) {
            window.emojiPickerState.closePicker();
        } else {
            const picker = window.emojiPickerState.picker;
            if (!picker) return;
            picker.style.opacity = '0';
            picker.style.transform = 'translateY(10px)';
            setTimeout(() => {
                picker.style.display = 'none';
                window.emojiPickerState.isOpen = false;
            }, 300);
        }
    }

    window.openEmojiPicker = openEmojiPicker;
    window.closeEmojiPicker = closeEmojiPicker;

    // --- 1. LOGIC GHI ÂM (RECORDING) ---
    let audioDiscarded = false;

    // Bắt đầu ghi âm: Chuyển UI, Start MediaRecorder
    window.startRecording = function() {
        if (isRecording) return;
        audioDiscarded = false;
        
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            showToast('Trình duyệt không hỗ trợ ghi âm', 'error');
            return;
        }
        
        navigator.mediaDevices.getUserMedia({ audio: true })
            .then(stream => {
                try {
                    mediaRecorder = new MediaRecorder(stream, {
                        mimeType: 'audio/webm;codecs=opus'
                    });
                } catch (e) {
                    mediaRecorder = new MediaRecorder(stream);
                }
                
                audioChunks = [];
                
                mediaRecorder.ondataavailable = event => {
                    if (event.data && event.data.size > 0) {
                        audioChunks.push(event.data);
                    }
                };
                
                mediaRecorder.onstop = () => {
                    if (!audioDiscarded && audioChunks.length > 0 && currentPartnerId) {
                        const audioBlob = new Blob(audioChunks, { type: 'audio/webm' });
                        uploadAudioFile(audioBlob);
                    }
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
                if (recordingTimer) clearInterval(recordingTimer);
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
        audioDiscarded = true;
        
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
        audioDiscarded = false;
        
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

    // Reaction system
    window.initReactionSystem = function() {
        if (typeof window.loadMessageReactions === 'function') {
            $('.msg-row').each(function() {
                const msgId = $(this).data('msg-id');
                if (msgId) {
                    window.loadMessageReactions(msgId);
                }
            });
        }
    };

    window.showReactionPicker = function(button, directMsgId) {
        $('.reaction-picker').remove();
        
        const msgRow = $(button).closest('.msg-row');
        const msgId = directMsgId || msgRow.data('msg-id');
        if (!msgId) return;
        
        const picker = $(`
            <div class="reaction-picker active">
                <span class="reaction-emoji" onclick="window.addReaction(${msgId}, '👍')">👍</span>
                <span class="reaction-emoji" onclick="window.addReaction(${msgId}, '❤️')">❤️</span>
                <span class="reaction-emoji" onclick="window.addReaction(${msgId}, '😮')">😮</span>
                <span class="reaction-emoji" onclick="window.addReaction(${msgId}, '😢')">😢</span>
                <span class="reaction-emoji" onclick="window.addReaction(${msgId}, '😂')">😂</span>
                <span class="reaction-emoji" onclick="window.addReaction(${msgId}, '😠')">😠</span>
                <span class="reaction-emoji" onclick="window.showFullReactionPicker(${msgId})">
                    <i class="fas fa-plus"></i>
                </span>
            </div>
        `);
        
        msgRow.append(picker);
        
        setTimeout(() => {
            $(document).on('click.reaction', function(e) {
                if (!$(e.target).closest('.reaction-picker, .reaction-btn').length) {
                    $('.reaction-picker').remove();
                    $(document).off('click.reaction');
                }
            });
        }, 100);
    };

    window.showFullReactionPicker = function(messageId) {
        $('.reaction-picker, .full-reaction-modal-overlay').remove();
        
        const popularEmojis = [
            '👍', '❤️', '🔥', '😂', '😮', '😢', '👏', '🎉', '🍿', '💯',
            '🥰', '😍', '🤩', '🥺', '😡', '🤔', '😴', '🤡', '🥳', '🙏',
            '✨', '👀', '💀', '🤮', '🤝', '🚀', '💖', '💔', '⚡', '🌟',
            '💪', '🎯', '👌', '🤗', '😎', '💐'
        ];
        
        const overlay = $('<div class="full-reaction-modal-overlay"></div>');
        const modal = $(`
            <div class="full-reaction-modal">
                <div class="full-reaction-header">
                    <span><i class="far fa-smile me-2"></i>Thả cảm xúc</span>
                    <button class="full-reaction-close" onclick="$('.full-reaction-modal-overlay').remove()">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
                <div class="full-reaction-grid">
                    ${popularEmojis.map(emoji => `
                        <button class="full-reaction-item" onclick="window.addReaction(${messageId}, '${emoji}')">
                            ${emoji}
                        </button>
                    `).join('')}
                </div>
            </div>
        `);
        
        overlay.append(modal);
        $('body').append(overlay);
        
        overlay.on('click', function(e) {
            if ($(e.target).hasClass('full-reaction-modal-overlay')) {
                overlay.remove();
            }
        });
    };

    window.addReaction = function(messageId, emoji) {
        $.post('/api/v1/messenger/reaction', {
            messageId: messageId,
            emoji: emoji
        }).done(function(response) {
            if (response && response.reactions) {
                window.updateMessageReactions(messageId, response.reactions);
            }
            $('.reaction-picker, .full-reaction-modal-overlay').remove();
        }).fail(function(err) {
            console.error('Lỗi gửi reaction:', err);
        });
    };

    window.updateMessageReactions = function(messageId, reactions) {
        const msgRow = $(`#msg-${messageId}`);
        if (!msgRow.length) return;
        
        let reactionsHtml = '';
        if (reactions && Object.keys(reactions).length > 0) {
            reactionsHtml = '<div class="message-reactions">';
            Object.entries(reactions).forEach(([emoji, count]) => {
                reactionsHtml += `
                    <div class="reaction-item" onclick="window.addReaction(${messageId}, '${emoji}')" title="Nhấn để thả cảm xúc này">
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
                window.displayPinnedMessages(messages);
            });
    };

    window.displayPinnedMessages = function(messages) {
        const headerBar = $('#pinnedHeaderBar');
        const snippet = $('#pinnedHeaderSnippet');
        const countBadge = $('#pinnedHeaderCount');
        
        if (messages && messages.length > 0) {
            const latest = messages[messages.length - 1];
            const content = latest.content.length > 60 ? 
                latest.content.substring(0, 60) + '...' : latest.content;
            
            snippet.text(content);
            countBadge.text(messages.length);
            headerBar.slideDown(200);
        } else {
            headerBar.slideUp(200);
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
        let uid = urlParams.get('uid');
        if (!uid) {
            uid = sessionStorage.getItem('activeMessengerPartnerId');
        }
        if (!uid) return;
        
        const targetId = parseInt(uid);
        if (!targetId || isNaN(targetId)) return;
        
        // Tìm trong danh sách hội thoại hiện có
        const existing = (existingConversations || []).find(c => c.partnerId === targetId);
        
        if (existing) {
            window.selectConversation(
                existing.partnerId, 
                existing.partnerName, 
                existing.partnerAvatar, 
                existing.friend,
                existing.online,
                existing.lastActive,
                existing.relationStatus
            );
        } else {
            // Nếu chưa có hội thoại, gọi endpoint an toàn (tránh 403 Forbidden của /api/users)
            $.get(`/api/v1/messenger/user/${targetId}`).done(function(u) {
                const avatar = u.avatar || `https://ui-avatars.com/api/?name=${encodeURIComponent(u.userName || u.name)}`;
                window.selectConversation(
                    u.userID || u.id,
                    u.userName || u.name,
                    avatar,
                    u.isFriend,
                    u.isOnline,
                    u.lastActive,
                    u.isFriend ? 'FRIEND' : 'STRANGER'
                );
            }).fail(function(xhr) {
                console.warn('Could not load user info for messenger:', targetId, xhr.status);
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
    // --- UNSEND & INLINE CHAT SEARCH LOGIC ---
    window.unsendMessage = function(msgId) {
        if (!confirm("Thu hồi tin nhắn này?")) return;
        
        $.post(`/api/v1/messenger/unsend/${msgId}`)
            .done(function() {
                const bubble = $(`#msg-${msgId} .msg-content`);
                bubble.addClass('deleted').removeAttr('style').text('Tin nhắn đã bị thu hồi');
                $(`#msg-${msgId} .msg-actions`).remove();
                if (typeof showToast === 'function') showToast('Đã thu hồi tin nhắn', 'info');
            })
            .fail(function() {
                if (typeof showToast === 'function') showToast('Lỗi khi thu hồi tin nhắn', 'error');
            });
    };

    let inlineSearchResults = [];
    let inlineSearchIndex = -1;

    function removeHighlights() {
        $('#messagesContainer mark.search-matched-text').each(function() {
            const parent = this.parentNode;
            if (parent) {
                parent.replaceChild(document.createTextNode(this.textContent), this);
                parent.normalize();
            }
        });
        $('#messagesContainer .msg-row').removeClass('search-matched current-search-result');
    }

    function highlightSearchResults(query) {
        if (!query) return;
        const escaped = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        const regex = new RegExp(`(${escaped})`, 'gi');

        inlineSearchResults.forEach((res, idx) => {
            const row = res.el;
            row.addClass('search-matched');
            if (idx === inlineSearchIndex) row.addClass('current-search-result');

            const bubble = row.find('.bubble');
            if (!bubble.length) return;

            const textNodes = [];
            function collectTextNodes(node) {
                if (node.nodeType === 3 && node.nodeValue.trim()) {
                    textNodes.push(node);
                } else if (node.nodeType === 1 && !node.classList.contains('reply-block') && !node.classList.contains('message-reactions')) {
                    for (let child = node.firstChild; child; child = child.nextSibling) {
                        collectTextNodes(child);
                    }
                }
            }
            collectTextNodes(bubble[0]);

            textNodes.forEach(node => {
                const text = node.nodeValue;
                if (regex.test(text)) {
                    const fragment = document.createDocumentFragment();
                    let lastIdx = 0;
                    text.replace(regex, (match, p1, offset) => {
                        fragment.appendChild(document.createTextNode(text.substring(lastIdx, offset)));
                        const mark = document.createElement('mark');
                        mark.className = 'search-matched-text';
                        mark.textContent = match;
                        fragment.appendChild(mark);
                        lastIdx = offset + match.length;
                    });
                    fragment.appendChild(document.createTextNode(text.substring(lastIdx)));
                    if (node.parentNode) {
                        node.parentNode.replaceChild(fragment, node);
                    }
                }
            });
        });
    }

    window.openChatSearch = function() {
        $('#inlineChatSearch').slideDown(150);
        $('#inlineSearchInput').val('').focus();
        inlineSearchResults = [];
        inlineSearchIndex = -1;
        $('#inlineSearchCounter').text('0/0');
    };

    window.closeInlineChatSearch = function() {
        $('#inlineChatSearch').slideUp(150);
        removeHighlights();
        inlineSearchResults = [];
        inlineSearchIndex = -1;
    };

    window.handleInlineSearch = function(query) {
        query = (query || '').trim();
        removeHighlights();
        inlineSearchResults = [];
        inlineSearchIndex = -1;

        if (!query) {
            $('#inlineSearchCounter').text('0/0');
            return;
        }

        $('#messagesContainer .msg-row').each(function() {
            const row = $(this);
            const bubble = row.find('.bubble');
            if (!bubble.length) return;
            const text = bubble.text() || '';
            if (text.toLowerCase().includes(query.toLowerCase())) {
                const msgId = row.data('msg-id');
                if (msgId) {
                    inlineSearchResults.push({ id: msgId, el: row });
                }
            }
        });

        if (inlineSearchResults.length > 0) {
            inlineSearchIndex = 0;
            highlightSearchResults(query);
            $('#inlineSearchCounter').text(`1/${inlineSearchResults.length}`);
            window.scrollToMessage(inlineSearchResults[0].id);
        } else {
            $('#inlineSearchCounter').text('0/0');
        }
    };

    window.prevSearchResult = function() {
        if (inlineSearchResults.length === 0) return;
        $('#messagesContainer .msg-row').removeClass('current-search-result');
        inlineSearchIndex = (inlineSearchIndex - 1 + inlineSearchResults.length) % inlineSearchResults.length;
        $('#inlineSearchCounter').text(`${inlineSearchIndex + 1}/${inlineSearchResults.length}`);
        const res = inlineSearchResults[inlineSearchIndex];
        if (res && res.el) {
            res.el.addClass('current-search-result');
            window.scrollToMessage(res.id);
        }
    };

    window.nextSearchResult = function() {
        if (inlineSearchResults.length === 0) return;
        $('#messagesContainer .msg-row').removeClass('current-search-result');
        inlineSearchIndex = (inlineSearchIndex + 1) % inlineSearchResults.length;
        $('#inlineSearchCounter').text(`${inlineSearchIndex + 1}/${inlineSearchResults.length}`);
        const res = inlineSearchResults[inlineSearchIndex];
        if (res && res.el) {
            res.el.addClass('current-search-result');
            window.scrollToMessage(res.id);
        }
    };

    $(document).on('keydown', '#inlineSearchInput', function(e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            if (e.shiftKey) window.prevSearchResult();
            else window.nextSearchResult();
        } else if (e.key === 'Escape') {
            window.closeInlineChatSearch();
        }
    });

    window.toggleConvMenu = function(event, partnerId) {
        event.stopPropagation();
        event.preventDefault();
        const btn = $(event.currentTarget);
        const convItem = btn.closest('.conv-item');
        const isAlreadyOpen = convItem.hasClass('menu-open') && convItem.find('.conv-action-dropdown').length;

        $('.conv-action-dropdown').remove();
        $('.conv-item').removeClass('menu-open');

        if (isAlreadyOpen) return;

        convItem.addClass('menu-open');

        const dropdown = $(`
            <div class="conv-action-dropdown" onclick="event.stopPropagation();">
                <div class="dropdown-item" onclick="window.viewProfile(${partnerId})">
                    <i class="fas fa-user-circle"></i> Xem trang cá nhân
                </div>
                <div class="dropdown-item" onclick="window.toggleNotifications(${partnerId}); $('.conv-action-dropdown').remove(); $('.conv-item').removeClass('menu-open');">
                    <i class="fas fa-bell"></i> Bật/Tắt thông báo
                </div>
                <div class="dropdown-item text-danger" onclick="window.blockUser(${partnerId}); $('.conv-action-dropdown').remove(); $('.conv-item').removeClass('menu-open');">
                    <i class="fas fa-ban"></i> Chặn người dùng
                </div>
            </div>
        `);

        convItem.append(dropdown);

        setTimeout(() => {
            $(document).on('click.convMenu', function(e) {
                if (!$(e.target).closest('.conv-action-dropdown, .conv-more-btn').length) {
                    $('.conv-action-dropdown').remove();
                    $('.conv-item').removeClass('menu-open');
                    $(document).off('click.convMenu');
                }
            });
        }, 50);
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
            window.location.href = `/social/profile/${id}`;
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
    function formatRelativeTimeBadge(timestamp, lastActiveStr) {
        let ts = null;
        if (typeof timestamp === 'number' && !isNaN(timestamp)) {
            ts = timestamp;
        } else if (timestamp && !isNaN(Number(timestamp))) {
            ts = Number(timestamp);
        } else if (lastActiveStr) {
            const parsed = Date.parse(lastActiveStr);
            if (!isNaN(parsed)) ts = parsed;
        }

        if (ts) {
            const diffMs = Math.max(0, Date.now() - ts);
            const diffMins = Math.floor(diffMs / 60000);
            if (diffMins < 60) {
                return Math.max(1, diffMins) + 'p';
            }
            const diffHours = Math.floor(diffMins / 60);
            if (diffHours < 24) {
                return diffHours + 'h';
            }
            return null; // >= 24h: bubble completely disappears
        }

        if (lastActiveStr) {
            const str = String(lastActiveStr).trim();
            if (str === 'Vừa xong') return '1p';
            const mMatch = str.match(/(\d+)\s*(phút|m|min)/i);
            if (mMatch) return mMatch[1] + 'p';
            const hMatch = str.match(/(\d+)\s*(giờ|h|hour)/i);
            if (hMatch) {
                const hours = parseInt(hMatch[1]);
                return hours < 24 ? hours + 'h' : null;
            }
        }
        return null;
    }

    function formatStatusText(isOnline, timestamp, lastActiveStr) {
        if (String(isOnline) === 'true') {
            return `<small class="text-success"><i class="fas fa-circle" style="font-size:8px;"></i> Đang hoạt động</small>`;
        }
        let ts = null;
        if (typeof timestamp === 'number' && !isNaN(timestamp)) {
            ts = timestamp;
        } else if (timestamp && !isNaN(Number(timestamp))) {
            ts = Number(timestamp);
        } else if (lastActiveStr) {
            const parsed = Date.parse(lastActiveStr);
            if (!isNaN(parsed)) ts = parsed;
        }

        if (ts) {
            const diffMs = Math.max(0, Date.now() - ts);
            const diffMins = Math.floor(diffMs / 60000);
            if (diffMins < 1) return `<small class="text-muted">Hoạt động vừa xong</small>`;
            if (diffMins < 60) return `<small class="text-muted">Hoạt động ${diffMins} phút trước</small>`;
            const diffHours = Math.floor(diffMins / 60);
            if (diffHours < 24) return `<small class="text-muted">Hoạt động ${diffHours} giờ trước</small>`;
            return `<small class="text-muted">Không hoạt động</small>`;
        }

        if (lastActiveStr && typeof lastActiveStr === 'string' && lastActiveStr.trim().length > 0) {
            const s = lastActiveStr.trim();
            if (s.toLowerCase().includes('ngày') || s.toLowerCase() === 'không hoạt động') {
                return `<small class="text-muted">Không hoạt động</small>`;
            }
            if (s.toLowerCase().startsWith('hoạt động')) {
                return `<small class="text-muted">${escapeHtml(s)}</small>`;
            }
            return `<small class="text-muted">Hoạt động ${escapeHtml(s)}</small>`;
        }
        return `<small class="text-muted">Không hoạt động</small>`;
    }

    function updateOnlineStatus(partnerId, isOnline, lastActive, lastActiveTimestamp) {
        const pId = parseInt(partnerId);
        const conv = $(`#conv-${pId}`).length ? $(`#conv-${pId}`) : $(`.conv-item[data-partner-id="${pId}"]`);
        if (conv.length) {
            const avatarWrapper = conv.find('.avatar-wrapper');
            let dot = avatarWrapper.find('.online-dot');
            if (!dot.length) {
                avatarWrapper.append('<div class="online-dot"></div>');
                dot = avatarWrapper.find('.online-dot');
            }
            avatarWrapper.find('.last-active-badge').remove();

            if (String(isOnline) === 'true') {
                dot.addClass('is-online').show();
            } else {
                dot.removeClass('is-online').hide();
                const badgeText = formatRelativeTimeBadge(lastActiveTimestamp, lastActive);
                if (badgeText) {
                    avatarWrapper.append(`<span class="last-active-badge">${badgeText}</span>`);
                }
            }
        }
        
        // Cập nhật trong chat header nếu đang chat với người này (chỉ khi là bạn bè)
        if (currentPartnerId == pId && isCurrentPartnerFriend) {
            const statusDiv = $('#chatHeaderStatus');
            if (statusDiv.length) {
                statusDiv.html(formatStatusText(isOnline, lastActiveTimestamp, lastActive));
            }
            const infoStatus = $('.chat-info-sidebar .info-status');
            if (infoStatus.length) {
                if (String(isOnline) === 'true') {
                    infoStatus.text('Đang hoạt động').css('color', '#31a24c');
                } else {
                    const badge = formatRelativeTimeBadge(lastActiveTimestamp, lastActive);
                    infoStatus.text(badge ? ('Hoạt động ' + badge + ' trước') : 'Không hoạt động').css('color', '#888');
                }
            }
        }
    }

    // ============= BACKGROUND PICKER FUNCTION =============
    window.openBackgroundPicker = function() {
        if (!currentPartnerId) return;

        $('.background-modal-overlay, .background-modal').remove();
        const modal = $('<div class="modal-overlay background-modal-overlay"></div>');
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

        modal.append(content);
        modal.on('click', function(e) {
            if ($(e.target).is(modal)) window.closeBackgroundPicker();
        });
        $('body').append(modal);
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



    // --- FIX: SCROLL TO MESSAGE ---
    window.scrollToMessage = function(messageId) {
        const messageElement = $(`#msg-${messageId}`);
        if (messageElement.length) {
            const container = $('#messagesContainer');
            const containerTop = container.offset().top;
            const messageTop = messageElement.offset().top;
            const scrollTo = container.scrollTop() + (messageTop - containerTop) - 80;
            
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
    let leftSearchDebounce = null;
    window.filterConversations = function() {
        const query = $('#convSearchInput').val().toLowerCase().trim();
        $('#convDiscoveredUsers').remove();

        if (!query) {
            $('.conv-item').show();
            return;
        }

        $('.conv-item').each(function() {
            if ($(this).hasClass('discovered-user-item')) return;
            const nameElement = $(this).find('.conv-name');
            const name = nameElement.text().toLowerCase();
            const previewElement = $(this).find('.conv-preview');
            const preview = previewElement.text().toLowerCase();

            if (name.includes(query) || preview.includes(query)) {
                $(this).show();
            } else {
                $(this).hide();
            }
        });

        // Search new users from server
        if (leftSearchDebounce) clearTimeout(leftSearchDebounce);
        leftSearchDebounce = setTimeout(() => {
            $.get(`/api/v1/messenger/users?q=${encodeURIComponent(query)}`).done(function(users) {
                $('#convDiscoveredUsers').remove();
                if (!users || !users.length) return;

                const existingPartnerIds = new Set();
                $('.conv-item').each(function() {
                    const pId = $(this).data('partner-id');
                    if (pId) existingPartnerIds.add(parseInt(pId));
                });

                const newUsers = users.filter(u => !existingPartnerIds.has(u.id));
                if (newUsers.length > 0) {
                    const discWrapper = $(`
                        <div id="convDiscoveredUsers" style="border-top:1px solid #333; margin-top:5px; padding-top:5px;">
                            <div style="font-size:0.75rem; color:#888; padding:6px 12px; font-weight:600; text-transform:uppercase;">
                                <i class="fas fa-user-plus me-1 text-primary"></i> Người dùng mới
                            </div>
                        </div>
                    `);
                    newUsers.forEach(u => {
                        const discItem = $(`
                            <div class="conv-item d-flex align-items-center p-2 discovered-user-item"
                                 style="cursor:pointer; border-bottom:1px solid #222;">
                                <div class="avatar-wrapper" style="position:relative; margin-right:10px;">
                                    <img src="${u.avatar}" style="width:48px; height:48px; border-radius:50%; object-fit:cover;">
                                </div>
                                <div class="flex-grow-1" style="min-width:0;">
                                    <div class="d-flex justify-content-between align-items-center">
                                        <strong class="conv-name" style="color:#fff; font-size:0.95rem; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">
                                            ${u.name}
                                        </strong>
                                        <small class="text-primary" style="font-size:0.75rem;">Bắt đầu chat</small>
                                    </div>
                                    <div class="conv-preview text-muted small text-truncate" style="color:#888;">
                                        ${u.email || 'Nhấn để trò chuyện'}
                                    </div>
                                </div>
                            </div>
                        `);
                        discItem.on('click', function() {
                            window.selectConversation(u.id, u.name, u.avatar, 'false', 'false', '', 'STRANGER', null);
                        });
                        discWrapper.append(discItem);
                    });
                    $('#conversationList').append(discWrapper);
                }
            });
        }, 300);
    };
    
    // Cập nhật lại hàm updateInfoSidebar để reset trạng thái khi đổi chat
    const originalSelectConversation = window.selectConversation;
    window.selectConversation = function(id, name, avatar, isFriend, isOnline, lastActive, relationStatus, lastActiveTimestamp) {
        // Gọi hàm gốc
        originalSelectConversation(id, name, avatar, isFriend, isOnline, lastActive, relationStatus, lastActiveTimestamp);
        
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