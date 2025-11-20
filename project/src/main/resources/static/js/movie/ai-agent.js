// ==================== AI Chat Widget JavaScript (Final Optimized) ====================

(function() {
    'use strict';

    if (window.aiChatInitialized) return;
    window.aiChatInitialized = true;

    // DOM Elements
    let chatIcon, chatWindow, closeBtn, sendBtn, input, messagesContainer, typingIndicator, suggestionsContainer;
    let conversationId = generateUUID();
    let isSending = false;

    // --- INJECT CSS STYLE TRỰC TIẾP (Không cần file css ngoài) ---
    const aiStyle = document.createElement('style');
    aiStyle.innerHTML = `
        /* AI Movie Carousel Style */
        .ai-movie-scroll {
            display: flex;
            gap: 12px;
            overflow-x: auto;
            padding: 10px 4px 15px 4px;
            scrollbar-width: thin;
            scroll-behavior: smooth;
            width: 100%;
        }
        .ai-movie-scroll::-webkit-scrollbar { height: 4px; }
        .ai-movie-scroll::-webkit-scrollbar-thumb { background: #e50914; border-radius: 2px; }
        
        .ai-movie-card {
            flex: 0 0 130px;
            width: 130px;
            background: #1f1f1f;
            border-radius: 8px;
            overflow: hidden;
            cursor: pointer;
            box-shadow: 0 4px 8px rgba(0,0,0,0.4);
            transition: transform 0.2s, border-color 0.2s;
            border: 1px solid #333;
            position: relative;
        }
        .ai-movie-card:hover {
            transform: translateY(-5px);
            border-color: #e50914;
        }
        .ai-card-poster {
            width: 100%;
            height: 190px;
            object-fit: cover;
        }
        .ai-card-rating {
            position: absolute;
            top: 6px; right: 6px;
            background: rgba(0,0,0,0.8);
            color: #ffd700;
            font-size: 0.7rem;
            padding: 2px 6px;
            border-radius: 4px;
            font-weight: bold;
        }
        .ai-card-info {
            padding: 8px;
        }
        .ai-card-title {
            font-size: 0.85rem;
            font-weight: 600;
            color: #fff;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            margin-bottom: 4px;
        }
        .ai-card-year {
            font-size: 0.75rem;
            color: #aaa;
        }
    `;
    document.head.appendChild(aiStyle);

    // Initialize
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    function init() {
        chatIcon = document.getElementById('aiChatIcon');
        chatWindow = document.getElementById('aiChatWindow');
        closeBtn = document.getElementById('aiCloseBtn');
        sendBtn = document.getElementById('aiSendBtn');
        input = document.getElementById('aiInput');
        messagesContainer = document.getElementById('aiMessages');
        typingIndicator = document.getElementById('aiTyping');
        suggestionsContainer = document.getElementById('aiSuggestions');

        if (!chatIcon) return;

        // --- [LỚP BẢO MẬT 1] LOGIN CHECK ---
        chatIcon.addEventListener('click', () => {
            // Kiểm tra biến global từ header.html
            if (typeof window.isUserLoggedIn !== 'undefined' && !window.isUserLoggedIn) {
                // Chuyển hướng login nếu chưa đăng nhập
                window.location.href = '/login';
                return;
            }
            openChat();
        });

        closeBtn.addEventListener('click', closeChat);
        sendBtn.addEventListener('click', sendMessage);
        
        input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });

        input.addEventListener('input', autoResizeTextarea);
        attachSuggestionHandlers();

        // Load History nếu đã đăng nhập
        if (typeof window.isUserLoggedIn !== 'undefined' && window.isUserLoggedIn) {
            loadChatHistory();
        }
    }

    async function loadChatHistory() {
        try {
            const res = await fetch('/api/ai-agent/history');
            if (res.ok) {
                const history = await res.json();
                // Nếu có history, xóa nội dung mặc định (welcome)
                if (history.length > 0) messagesContainer.innerHTML = '';
                
                history.forEach(msg => {
                    if (msg.role === 'USER') addUserMessage(msg.message);
                    else addBotMessage(msg.message, msg.movies);
                });
                scrollToBottom();
            }
        } catch (e) { console.error("Lỗi load history", e); }
    }

    async function sendMessage() {
        const message = input.value.trim();
        if (!message || isSending) return;

        addUserMessage(message);
        input.value = '';
        input.style.height = 'auto';
        if (suggestionsContainer) suggestionsContainer.style.display = 'none';

        showTyping();
        isSending = true;
        sendBtn.disabled = true;

        try {
            const response = await fetch('/api/ai-agent/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message: message, conversationId: conversationId })
            });

            const data = await response.json();
            hideTyping();

            if (data.success) {
                addBotMessage(data.message, data.movies);
            } else {
                addBotMessage('Xin lỗi, có lỗi xảy ra: ' + (data.error || 'Unknown error'));
            }

        } catch (error) {
            hideTyping();
            addBotMessage('⚠️ Không thể kết nối đến server.');
        } finally {
            isSending = false;
            sendBtn.disabled = false;
            input.focus();
        }
    }

    // --- RENDER FUNCTIONS ---

    function addUserMessage(text) {
        const div = document.createElement('div');
        div.className = 'ai-message user';
        div.innerHTML = `<div class="ai-message-content"><p>${escapeHtml(text)}</p></div>`;
        messagesContainer.appendChild(div);
        scrollToBottom();
    }

    // Hàm hiển thị tin nhắn Bot kèm Movie Cards (nếu có)
    function addBotMessage(text, movies) {
        const div = document.createElement('div');
        div.className = 'ai-message bot';
        
        let html = `
            <div class="ai-message-avatar"><i class="fas fa-robot"></i></div>
            <div class="ai-message-content" style="width: 100%; max-width: 85%;">
                ${formatBotMessage(text)}
        `;

        // Nếu có phim -> Render Carousel vào trong message content luôn
        if (movies && Array.isArray(movies) && movies.length > 0) {
            html += renderMovieSuggestions(movies);
        }

        html += `</div>`; // End ai-message-content

        div.innerHTML = html;
        messagesContainer.appendChild(div);
        scrollToBottom();
    }

    // Tạo HTML chuỗi thẻ phim (Reuse Style đã inject)
    function renderMovieSuggestions(movies) {
        let html = `<div class="ai-movie-scroll">`;
        
        movies.forEach(m => {
            const poster = m.poster || '/images/placeholder.jpg';
            const title = escapeHtml(m.title || 'Unknown');
            const year = m.year || '';
            const rating = m.rating || 'N/A';
            
            html += `
                <div class="ai-movie-card" onclick="window.location.href='/movie/detail/${m.id}'">
                    <div style="position: relative;">
                        <img class="ai-card-poster" src="${poster}" alt="${title}" onerror="this.src='/images/placeholder.jpg'">
                        <div class="ai-card-rating">⭐ ${rating}</div>
                    </div>
                    <div class="ai-card-info">
                        <div class="ai-card-title" title="${title}">${title}</div>
                        <div class="ai-card-year">${year}</div>
                    </div>
                </div>
            `;
        });
        
        html += `</div>`;
        return html;
    }

    function formatBotMessage(text) {
        if (!text) return '';
        return text.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
                   .replace(/\n/g, '<br>');
    }

    // UI Utilities
    function openChat() {
        chatWindow.hidden = false;
        chatIcon.style.display = 'none';
        input.focus();
        scrollToBottom();
    }
    function closeChat() {
        chatWindow.hidden = true;
        chatIcon.style.display = 'flex';
    }
    function showTyping() { if(typingIndicator) { typingIndicator.hidden = false; typingIndicator.style.display = 'flex'; scrollToBottom(); } }
    function hideTyping() { if(typingIndicator) typingIndicator.hidden = true; }
    function autoResizeTextarea() { input.style.height = 'auto'; input.style.height = Math.min(input.scrollHeight, 120) + 'px'; }
    function scrollToBottom() { if (messagesContainer) messagesContainer.scrollTop = messagesContainer.scrollHeight; }
    function escapeHtml(text) { const div = document.createElement('div'); div.textContent = text; return div.innerHTML; }
    function attachSuggestionHandlers() {
        document.querySelectorAll('.suggestion-chip').forEach(chip => {
            chip.addEventListener('click', function() { input.value = this.dataset.question; sendMessage(); });
        });
    }
    function generateUUID() {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
            const r = Math.random() * 16 | 0; return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
        });
    }

    window.aiChat = { open: openChat, close: closeChat, send: sendMessage };

})();