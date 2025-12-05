// messenger.js - Ultimate Version

var socket = new SockJS('/ws');
var stompClient = Stomp.over(socket);
stompClient.debug = null; // Tắt log

var selectedUser = null;
const GIPHY_API_KEY = 'YOUR_KEY_HERE'; // Thay key của bạn nếu có, hoặc dùng backup list bên dưới
const BACKUP_STICKERS = [
    "https://media.giphy.com/media/26BRv0ThflsHCqDrG/giphy.gif",
    "https://media.giphy.com/media/l0HlO3BJ8LxrZ4VRu/giphy.gif",
    "https://media.giphy.com/media/3o7TKSjRrfIPjeiVyM/giphy.gif",
    "https://media.giphy.com/media/l0HlI9qB6L8l756z6/giphy.gif"
];

// 1. KẾT NỐI SOCKET
stompClient.connect({}, function (frame) {
    console.log('Connected to Messenger');
    
    // Đăng ký kênh riêng tư (User Destination)
    stompClient.subscribe('/user/queue/private', function (payload) {
        var msg = JSON.parse(payload.body);
        
        // Nếu tin nhắn thuộc về hội thoại đang mở (Người gửi là họ HOẶC mình gửi cho họ)
        if (selectedUser && (msg.sender === selectedUser || msg.replyToId === selectedUser)) {
            renderMessage(msg);
        } else {
            // Notification đơn giản (Có thể nâng cấp lên Toast)
            playNotificationSound();
            // Highlight user trong sidebar
            var userItem = document.getElementById('user-' + msg.sender);
            if(userItem) userItem.style.background = '#3a3b3c'; 
        }
    });
});

// 2. CHỌN USER ĐỂ CHAT
function selectUser(username) {
    selectedUser = username;
    
    // UI Update
    document.getElementById('emptyState').classList.add('hidden');
    document.getElementById('activeChat').classList.remove('hidden');
    
    // Header Info
    document.getElementById('headerName').innerText = username;
    document.getElementById('headerAvatar').src = `https://ui-avatars.com/api/?name=${username}&background=random&color=fff`;
    
    // Active Sidebar
    document.querySelectorAll('.friend-item').forEach(el => el.classList.remove('active'));
    document.getElementById('user-' + username).classList.add('active');
    
    // Load History
    loadHistory(username);
}

// 3. LOAD LỊCH SỬ CHAT TỪ API
function loadHistory(username) {
    var area = document.getElementById('msgArea');
    area.innerHTML = '<div class="text-center text-muted mt-5"><i class="fas fa-spinner fa-spin"></i> Đang tải tin nhắn...</div>';
    
    fetch(`/api/messenger/history/${username}`)
        .then(res => res.json())
        .then(data => {
            area.innerHTML = '';
            if(data.length === 0) {
                area.innerHTML = '<div class="text-center text-muted mt-5">Hãy bắt đầu cuộc trò chuyện!</div>';
            } else {
                data.forEach(msg => {
                    // Convert Entity ChatMessage -> SocketMessage format để tái sử dụng hàm render
                    // (Lưu ý: Bạn cần đảm bảo logic mapping ở đây khớp với dữ liệu trả về)
                    var socketMsg = {
                        sender: msg.senderEmail,
                        content: msg.content,
                        timestamp: new Date(msg.timestamp).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}),
                        type: msg.content.startsWith('[IMAGE]') ? 'IMAGE' : (msg.content.startsWith('[STICKER]') ? 'STICKER' : 'CHAT'),
                        mediaUrl: msg.content.replace('[IMAGE]', '').replace('[STICKER]', '') // Hack nhẹ để lấy URL
                    };
                    renderMessage(socketMsg);
                });
            }
        })
        .catch(err => area.innerHTML = '<div class="text-center text-danger">Lỗi tải lịch sử</div>');
}

// 4. GỬI TIN NHẮN
function sendPrivateMsg() {
    var input = document.getElementById('msgInput');
    var content = input.value.trim();
    if (!content || !selectedUser) return;

    var msg = {
        sender: currentUser,
        content: content,
        type: 'CHAT',
        replyToId: selectedUser 
    };

    stompClient.send("/app/chat.sendPrivate", {}, JSON.stringify(msg));
    input.value = '';
}

// 5. RENDER TIN NHẮN (Hỗ trợ Text, Image, Sticker)
function renderMessage(msg) {
    var area = document.getElementById('msgArea');
    var isMine = msg.sender === currentUser;
    
    // Xử lý nội dung
    var contentHtml = '';
    if (msg.type === 'IMAGE' || (msg.content && msg.content.startsWith('[IMAGE]'))) {
        var url = msg.mediaUrl || msg.content.replace('[IMAGE]', '');
        contentHtml = `<img src="${url}" style="max-width:200px; border-radius:10px; cursor:pointer;" onclick="window.open(this.src)">`;
    } else if (msg.type === 'STICKER' || (msg.content && msg.content.startsWith('[STICKER]'))) {
        var url = msg.mediaUrl || msg.content.replace('[STICKER]', '');
        contentHtml = `<img src="${url}" style="width:100px;">`;
    } else {
        contentHtml = msg.content;
    }

    var html = `
        <div class="msg-row ${isMine ? 'mine' : 'other'}">
            ${!isMine ? `<img src="https://ui-avatars.com/api/?name=${msg.sender}&background=random&color=fff" class="avatar" style="width:30px;height:30px;">` : ''}
            <div style="display:flex; flex-direction:column; ${isMine ? 'align-items:flex-end' : ''}">
                <div class="msg-bubble" style="${(msg.type==='IMAGE' || msg.type==='STICKER') ? 'background:transparent;padding:0;' : ''}">
                    ${contentHtml}
                </div>
                <div class="msg-time">${msg.timestamp || 'Just now'}</div>
            </div>
        </div>
    `;
    
    var div = document.createElement('div');
    div.innerHTML = html;
    area.appendChild(div.firstElementChild);
    area.scrollTop = area.scrollHeight;
}

// 6. UPLOAD ẢNH
function uploadImage() {
    var file = document.getElementById('imageInput').files[0];
    if (!file || !selectedUser) return;

    var formData = new FormData();
    formData.append("file", file);

    fetch('/api/upload/image', { method: 'POST', body: formData })
        .then(res => res.json())
        .then(data => {
            if (data.url) {
                // Gửi message đặc biệt
                var msg = {
                    sender: currentUser,
                    type: 'IMAGE',
                    mediaUrl: data.url,
                    replyToId: selectedUser,
                    content: '[IMAGE]' + data.url // Hack để lưu vào DB text
                };
                stompClient.send("/app/chat.sendPrivate", {}, JSON.stringify(msg));
            }
        });
}

// 7. GIPHY STICKER
function toggleStickers() {
    var menu = document.getElementById('stickerMenu');
    if (menu.style.display === 'block') {
        menu.style.display = 'none';
        return;
    }
    
    // Load stickers nếu chưa có
    if (menu.innerHTML.trim() === '') {
        var html = '<div class="d-flex flex-wrap gap-2 justify-content-center">';
        BACKUP_STICKERS.forEach(url => {
            html += `<img src="${url}" onclick="sendSticker('${url}')" style="width:80px; height:80px; cursor:pointer; object-fit:contain;">`;
        });
        html += '</div>';
        menu.innerHTML = html;
    }
    menu.style.display = 'block';
}

function sendSticker(url) {
    var msg = {
        sender: currentUser,
        type: 'STICKER',
        mediaUrl: url,
        replyToId: selectedUser,
        content: '[STICKER]' + url
    };
    stompClient.send("/app/chat.sendPrivate", {}, JSON.stringify(msg));
    document.getElementById('stickerMenu').style.display = 'none';
}

function handleEnter(e) { if(e.key === 'Enter') sendPrivateMsg(); }
function playNotificationSound() { /* Code play sound here if needed */ }

// [BLOCK CODE JS] Hàm tạo HTML tin nhắn chuẩn Facebook Vipro
function createMessageHTML(msg, isMine) {
    // Check avatar
    const avatar = isMine ? currentUserAvatar : currentChatAvatar;
    const alignClass = isMine ? 'mine' : 'other';
    const bubbleClass = isMine ? 'mine' : 'other';

    return `
    <div class="msg-row ${alignClass}" id="msg-${msg.id}">
        ${!isMine ? `<img src="${avatar}" class="msg-avatar">` : ''}
        
        <div class="msg-content-wrapper">
            ${msg.replyTo ? `
            <div class="reply-context">
                <div class="reply-line"></div>
                <small>Trả lời: ${msg.replyTo.content}</small>
            </div>` : ''}

            <div class="msg-bubble ${bubbleClass}">
                ${msg.type === 'IMAGE' ? `<img src="${msg.content}" class="msg-image">` : 
                  msg.type === 'VOICE' ? `<audio controls src="${msg.content}"></audio>` : 
                  msg.content}
            </div>

            <div class="reaction-display" id="react-${msg.id}">
                ${msg.reactions && msg.reactions.length > 0 ? renderReactions(msg.reactions) : ''}
            </div>
        </div>

        <div class="msg-actions">
            <button onclick="showReactionMenu('${msg.id}')" title="Thả cảm xúc"><i class="far fa-smile"></i></button>
            <button onclick="replyToMessage('${msg.id}', '${msg.content}')" title="Trả lời"><i class="fas fa-reply"></i></button>
            <button onclick="forwardMessage('${msg.id}')" title="Chuyển tiếp"><i class="fas fa-share"></i></button>
        </div>
    </div>
    `;
}