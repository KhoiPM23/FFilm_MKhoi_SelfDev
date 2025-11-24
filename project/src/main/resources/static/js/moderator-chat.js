'use strict';

// --- BIẾN TOÀN CỤC ---
const modName = document.querySelector('meta[name="_modName"]').content;
let stompClient = null;
let currentChatUser = null;

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
    // stompClient.debug = null; 
    stompClient.connect({}, onConnected, onError);
}

function onConnected() {
    console.log("🟢 Connected as Moderator: " + modName);

    // Đăng ký
    stompClient.send("/app/chat.moderatorJoin", {}, JSON.stringify({ senderEmail: modName }));

    // Lắng nghe
    stompClient.subscribe('/topic/moderator/' + modName, onPrivateMessageReceived);
    stompClient.subscribe('/topic/admin/queue', onQueueMessageReceived);

    // Load data
    loadConversations();
}

function onError(error) { console.log('WebSocket Error:', error); }

// --- 2. XỬ LÝ TIN NHẮN ĐẾN ---

function onPrivateMessageReceived(payload) {
    const message = JSON.parse(payload.body);
    if (message.content === 'SEEN_ACK' && message.status === 'SEEN') {
        if (currentChatUser === message.senderEmail) {
            document.querySelectorAll('.msg-status').forEach(label => {
                if (label.innerText === 'Đã gửi') label.innerText = 'Đã xem';
            });
        }
        return;
    }

    handleIncomingMessage(message);
}

function onQueueMessageReceived(payload) {
    const message = JSON.parse(payload.body);
    handleIncomingMessage(message, true);
}

function handleIncomingMessage(message, isQueue = false) {
    let otherParty = (message.senderEmail === modName) ? message.recipientEmail : message.senderEmail;
    if (isQueue) otherParty = message.senderEmail;

    // Kiểm tra xem có đang chat với người này không
    const isNotCurrentChat = (currentChatUser !== otherParty);

    // CẬP NHẬT SIDEBAR (Tăng số đếm nếu không chat)
    updateSidebarUser(otherParty, message.content, isQueue || isNotCurrentChat);

    if (!isNotCurrentChat) {
        renderMessage(message);
        scrollToBottom();
        markAsSeen(otherParty);
    }
}

// --- 3. LOGIC SIDEBAR & ĐẾM SỐ ---

function updateSidebarUser(email, lastMessage, isUnread) {
    const existingItem = document.getElementById('user-row-' + email);

    if (existingItem) {
        // Update nội dung tin nhắn cuối
        existingItem.querySelector('.u-msg').textContent = lastMessage;

        // Đưa lên đầu danh sách
        userListUl.prepend(existingItem);

        // Xử lý số đếm (Counter)
        if (isUnread) {
            existingItem.classList.add('unread'); // Tô đậm text
            const badge = existingItem.querySelector('.unread-count');

            // Lấy số hiện tại + 1
            let currentCount = parseInt(badge.innerText) || 0;
            currentCount++;

            badge.innerText = currentCount;
            badge.classList.add('visible'); // Hiện badge lên
        }
    } else {
        createUserListItem(email, lastMessage, isUnread ? 1 : 0);
    }
}

function createUserListItem(email, lastMsg, initialCount) {
    const li = document.createElement('li');
    li.id = 'user-row-' + email;
    li.className = 'user-item';

    // Nếu có tin chưa đọc thì thêm class unread để tô đậm
    if (initialCount > 0) li.classList.add('unread');

    li.onclick = () => selectUser(email);

    // Class cho badge: nếu count > 0 thì thêm 'visible'
    let badgeClass = (initialCount > 0) ? 'unread-count visible' : 'unread-count';

    // Cấu trúc HTML mới: Chia Group Text và Badge riêng
    li.innerHTML = `
        <div class="user-info-group">
            <span class="u-email">${email}</span>
            <span class="u-msg">${lastMsg}</span>
        </div>
        <span class="${badgeClass}">${initialCount}</span>
    `;
    userListUl.prepend(li);
}

// --- 4. CHỌN USER (RESET SỐ & BÁO ĐÃ XEM) ---

function selectUser(email) {
    currentChatUser = email;

    // UI Updates
    document.querySelectorAll('.user-item').forEach(el => el.classList.remove('active'));

    const currentItem = document.getElementById('user-row-' + email);
    if (currentItem) {
        currentItem.classList.add('active');

        // [RESET SỐ ĐẾM]
        currentItem.classList.remove('unread');
        const badge = currentItem.querySelector('.unread-count');
        badge.innerText = '0';
        badge.classList.remove('visible');

        chatWithUserSpan.textContent = email;
        emptyState.style.display = 'none';
        chatWindow.style.display = 'flex';
        msgContainer.innerHTML = '';

        // Load lịch sử
        fetch('/api/chat/history/' + email)
            .then(res => res.json())
            .then(messages => {
                messages.forEach(renderMessage);
                scrollToBottom();
                markAsSeen(email);
            });
    }
}

function markAsSeen(senderEmail) {
    fetch('/api/chat/seen/' + senderEmail, { method: 'PUT' })
        .then(res => {
            if (res.ok) console.log("Marked as seen for: " + senderEmail);
        });
}

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

    }
}

function loadConversations() {
    fetch('/api/chat/conversations')
        .then(res => res.ok ? res.json() : [])
        .then(messages => {
            userListUl.innerHTML = '';
            const uniqueUsers = new Set();

            if (Array.isArray(messages)) {
                messages.forEach(msg => {
                    // Xác định User là ai
                    let user = (msg.senderEmail === modName) ? msg.recipientEmail : msg.senderEmail;
                    if (msg.recipientEmail === 'WAITING_QUEUE') user = msg.senderEmail;

                    if (!uniqueUsers.has(user)) {
                        uniqueUsers.add(user);

                        // [FIX] GỌI API LẤY SỐ ĐẾM TỪ SERVER
                        fetch('/api/chat/unread/' + user)
                            .then(r => r.json())
                            .then(count => {
                                // Truyền số count thật vào hàm tạo giao diện
                                createUserListItem(user, msg.content, count);
                            })
                            .catch(e => {
                                // Nếu lỗi mạng thì hiện 0
                                createUserListItem(user, msg.content, 0);
                            });
                    }
                });
            }
        });
}

function renderMessage(message) {
    const div = document.createElement('div');

    const isSent = (message.senderEmail !== currentChatUser);

    div.className = `message-row ${isSent ? 'sent' : 'received'}`;

    let statusHtml = '';
    let senderNameHtml = '';

    if (isSent) {
        if (message.senderEmail === modName) {
            const statusText = (message.status === 'SEEN') ? 'Đã xem' : 'Đã gửi';
            statusHtml = `<div class="msg-status" style="font-size:10px; color:#888; text-align:right; font-style:italic;">${statusText}</div>`;
        }
        else {
            senderNameHtml = `<div style="font-size:10px; color:#ccc; margin-bottom:2px; text-align:right;">Gửi bởi: <b>${message.senderEmail}</b></div>`;
        }
    }
    div.innerHTML = `
        ${senderNameHtml}
        <div class="message-bubble">
            ${message.content}
        </div>
        <div style="font-size:10px; color:#555; margin-top:2px; text-align: ${isSent ? 'right' : 'left'}">
            ${new Date(message.timestamp).toLocaleTimeString()}
        </div>
        ${statusHtml}
    `;
    msgContainer.appendChild(div);
}

function scrollToBottom() {
    msgContainer.scrollTop = msgContainer.scrollHeight;
}

function finishChat() {
    if (confirm("Kết thúc phiên chat này?")) {
        currentChatUser = null;
        chatWindow.style.display = 'none';
        emptyState.style.display = 'block';
        loadConversations();
    }
}

connect();