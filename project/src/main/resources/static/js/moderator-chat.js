'use strict';

// --- BIẾN TOÀN CỤC ---
const modName = document.querySelector('meta[name="_modName"]').content; // Lấy từ thẻ meta HTML
let stompClient = null;
let currentChatUser = null; // Email của user đang chat hiện tại

// --- DOM ELEMENTS ---
const userListUl = document.getElementById('userList');
const chatWindow = document.getElementById('mainChat');
const emptyState = document.getElementById('emptyState');
const msgContainer = document.getElementById('msgContainer');
const chatWithUserSpan = document.getElementById('chatWithUser');
const msgInput = document.getElementById('msgInput');

// --- 1. KẾT NỐI WEBSOCKET ---
function connect() {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);

    stompClient.connect({}, onConnected, onError);
}

function onConnected() {
    console.log("🟢 Connected as Moderator: " + modName);

    // A. Đăng ký Moderator Online
    stompClient.send("/app/chat.moderatorJoin", {}, JSON.stringify({ senderEmail: modName }));

    // B. Lắng nghe tin nhắn riêng (Được hệ thống chia bài)
    stompClient.subscribe('/topic/moderator/' + modName, onPrivateMessageReceived);

    // C. Lắng nghe hàng chờ chung (Khi chưa có Mod nào nhận khách)
    stompClient.subscribe('/topic/admin/queue', onQueueMessageReceived);

    // D. Load danh sách hội thoại cũ
    loadConversations();
}

function onError(error) {
    console.log('🔴 Error connecting to WebSocket:', error);
}

// --- 2. XỬ LÝ TIN NHẮN ĐẾN ---

// Tin nhắn riêng (User chat với Mod này hoặc Mod khác reply)
function onPrivateMessageReceived(payload) {
    const message = JSON.parse(payload.body);
    handleIncomingMessage(message);
}

// Tin nhắn hàng chờ (User mới chưa ai nhận)
function onQueueMessageReceived(payload) {
    const message = JSON.parse(payload.body);
    // Hiển thị badge "NEW" hoặc thêm vào danh sách
    handleIncomingMessage(message, true);
}

function handleIncomingMessage(message, isQueue = false) {
    // Xác định đối phương là ai (Nếu mình là người gửi -> đối phương là recipient, ngược lại là sender)
    let otherParty = (message.senderEmail === modName) ? message.recipientEmail : message.senderEmail;

    // Nếu tin nhắn đến từ WAITING_QUEUE, hiển thị tên người gửi gốc
    if (isQueue) {
        otherParty = message.senderEmail;
    }

    // 1. Cập nhật Sidebar (Đưa user lên đầu danh sách)
    updateSidebarUser(otherParty, message.content, isQueue);

    // 2. Nếu đang mở chat với user này -> Hiển thị tin nhắn lên màn hình
    if (currentChatUser === otherParty) {
        renderMessage(message);
        scrollToBottom();
    }
}

// --- 3. GỬI TIN NHẮN (REPLY) ---
function sendMsg() {
    const content = msgInput.value.trim();
    if (content && stompClient && currentChatUser) {
        const chatMessage = {
            senderEmail: modName,
            recipientEmail: currentChatUser,
            content: content,
            type: 'CHAT'
        };

        stompClient.send("/app/chat.replyToUser", {}, JSON.stringify(chatMessage));
        msgInput.value = '';

        // Render ngay lập tức phía mình (hoặc đợi server phản hồi cũng được, ở đây render luôn cho mượt)
        // Lưu ý: Controller của bạn có gửi lại tin nhắn cho Mod qua topic, nên có thể đợi onPrivateMessageReceived để tránh duplicate
        // Tuy nhiên, để UX tốt, ta thường render luôn. Nhưng vì Controller ĐÃ gửi lại, ta sẽ ĐỢI onPrivateMessageReceived
    }
}

// --- 4. UI & LOGIC HỖ TRỢ ---
// Trong file static/js/moderator-chat.js

function loadConversations() {
    fetch('/api/chat/conversations')
        .then(response => {
            // [FIX] Kiểm tra xem request có thành công không
            if (!response.ok) {
                throw new Error('Lỗi Server: ' + response.status);
            }
            return response.json();
        })
        .then(messages => {
            userListUl.innerHTML = ''; // Clear list cũ
            const uniqueUsers = new Set();

            // [FIX] Kiểm tra chắc chắn messages là mảng mới chạy forEach
            if (Array.isArray(messages)) {
                messages.forEach(msg => {
                    let user = (msg.senderEmail === modName) ? msg.recipientEmail : msg.senderEmail;
                    if (msg.recipientEmail === 'WAITING_QUEUE') user = msg.senderEmail;

                    if (!uniqueUsers.has(user)) {
                        uniqueUsers.add(user);
                        createUserListItem(user, msg.content, false);
                    }
                });
            }
        })
        .catch(error => {
            console.error("🔴 Lỗi tải danh sách chat:", error);
            // Có thể hiển thị thông báo lỗi nhỏ lên giao diện nếu muốn
        });
}
// Tạo hoặc cập nhật user trong sidebar
function updateSidebarUser(email, lastMessage, isNew) {
    // Tìm xem user đã có trong list chưa
    const existingItem = document.getElementById('user-row-' + email);

    if (existingItem) {
        // Update nội dung và đưa lên đầu
        existingItem.querySelector('.u-msg').textContent = lastMessage;
        userListUl.prepend(existingItem); // Move to top
        if (currentChatUser !== email) {
            existingItem.classList.add('unread'); // Thêm class để báo tin mới (CSS tự thêm)
        }
    } else {
        // Tạo mới
        createUserListItem(email, lastMessage, isNew);
    }
}

function createUserListItem(email, lastMsg, isNew) {
    const li = document.createElement('li');
    li.id = 'user-row-' + email;
    li.className = 'user-item';
    li.onclick = () => selectUser(email);

    let badgeHtml = '';
    if (isNew) {
        badgeHtml = `<span class="status-badge badge-new">NEW</span>`;
    }

    li.innerHTML = `
        ${badgeHtml}
        <span class="u-email">${email}</span>
        <span class="u-msg">${lastMsg}</span>
    `;

    // Insert vào đầu danh sách
    userListUl.prepend(li);
}

// Chọn User để chat
function selectUser(email) {
    currentChatUser = email;

    // UI Updates
    document.querySelectorAll('.user-item').forEach(el => el.classList.remove('active'));
    document.getElementById('user-row-' + email)?.classList.add('active');

    chatWithUserSpan.textContent = email;
    emptyState.style.display = 'none';
    chatWindow.style.display = 'flex';
    msgContainer.innerHTML = ''; // Xóa tin nhắn cũ

    // Load lịch sử chat
    fetch('/api/chat/history/' + email)
        .then(response => response.json())
        .then(messages => {
            messages.forEach(renderMessage);
            scrollToBottom();
        });
}

// Render 1 tin nhắn ra màn hình
function renderMessage(message) {
    const div = document.createElement('div');
    // Kiểm tra xem tin nhắn là "Gửi đi" (Sent) hay "Nhận về" (Received)
    // Nếu người gửi là Mod hiện tại -> Sent. Ngược lại -> Received
    const isSent = (message.senderEmail === modName);

    div.className = `message-row ${isSent ? 'sent' : 'received'}`;

    div.innerHTML = `
        <div class="message-bubble">
            ${message.content}
        </div>
        <div style="font-size:10px; color:#555; margin-top:2px; text-align: ${isSent ? 'right' : 'left'}">
            ${new Date(message.timestamp).toLocaleTimeString()}
        </div>
    `;
    msgContainer.appendChild(div);
    scrollToBottom();
}

function scrollToBottom() {
    msgContainer.scrollTop = msgContainer.scrollHeight;
}

function finishChat() {
    if (confirm("Kết thúc phiên chat này?")) {
        // Logic: Xóa khỏi list hoặc đổi trạng thái
        // Hiện tại Controller chưa có API finish, ta chỉ clear UI
        currentChatUser = null;
        chatWindow.style.display = 'none';
        emptyState.style.display = 'block';
        loadConversations(); // Reload lại list
    }
}

// --- KHỞI CHẠY ---
connect();