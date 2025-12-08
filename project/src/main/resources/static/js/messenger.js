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

    // --- 1. WEBSOCKET ---
    function connectWebSocket() {
        if(stompClient && stompClient.connected) return;

        var socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        stompClient.debug = null; 

        stompClient.connect({}, function (frame) {
            console.log('✅ WS Connected');
            stompClient.subscribe('/user/queue/private', function(payload) {
                const msg = JSON.parse(payload.body);
                
                // [FIX] Kiểm tra đúng người đang chat
                if(currentPartnerId && 
                (msg.senderId == currentPartnerId || msg.receiverId == currentPartnerId)) {
                    
                    // [FIX] Không append nếu đã có (tránh double)
                    const existingMsg = $(`#messagesContainer .msg-row[data-msg-id="${msg.id}"]`);
                    if(existingMsg.length === 0) {
                        appendMessageToUI(msg);
                        scrollToBottom();
                    }
                }
                
                // Luôn reload sidebar
                loadConversations();
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
        $.ajax({
            url: '/api/v1/messenger/send',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            success: function(msg) {
                // appendMessageToUI(msg, true); // Force mine = true
                scrollToBottom();
            },
            error: function(e) { console.error("Send Error", e); }
        });
    }

    // Upload (Fix URL)
    function uploadAndSend(file, type, caption) {
        const formData = new FormData();
        formData.append("file", file);

        // UI Loading giả
        const tempId = 'up-' + Date.now();
        $('#messagesContainer').append(`<div id="${tempId}" class="text-center small text-muted">Đang tải lên...</div>`);
        scrollToBottom();
        
        // Xóa preview ngay cho gọn
        window.clearPreview();
        $('#msgInput').val(''); 

        $.ajax({
            url: '/api/upload/image', // Đảm bảo Backend Controller map đúng URL này
            type: 'POST',
            data: formData,
            processData: false,
            contentType: false,
            success: function(res) {
                $(`#${tempId}`).remove();
                if(res.url) {
                    // 1. Gửi tin nhắn chứa URL file/ảnh
                    // Backend cần hỗ trợ Enum: IMAGE hoặc FILE hoặc AUDIO
                    sendApiRequest({ 
                        receiverId: currentPartnerId, 
                        content: res.url, 
                        type: type 
                    });
                    
                    // Hiện ngay (Optimistic)
                    appendMessageToUI({
                         senderId: currentUser.userID, 
                         content: res.url, 
                         type: type 
                    }, true);

                    // 2. Nếu có caption (text đi kèm) -> Gửi tiếp 1 tin text
                    if(caption) {
                        sendApiRequest({ receiverId: currentPartnerId, content: caption, type: 'TEXT' });
                        appendMessageToUI({ senderId: currentUser.userID, content: caption, type: 'TEXT' }, true);
                    }
                }
            },
            error: function(err) {
                console.error("Upload failed", err);
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
    let timerInterval;
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
        clearInterval(recordTimerInterval);
        
        // Chuyển lại UI thường
        $('#recordingState').hide();
        $('#normalInputState').show();
        $('#msgInput').focus();
        
        // Tắt stream mic (để tắt đèn đỏ trên tab trình duyệt)
        if(mediaRecorder && mediaRecorder.stream) {
            mediaRecorder.stream.getTracks().forEach(track => track.stop());
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