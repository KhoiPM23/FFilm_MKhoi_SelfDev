// ==================== AI Chat Widget JavaScript ====================

(function() {
    'use strict';

    // Prevent double initialization
    if (window.aiChatInitialized) return;
    window.aiChatInitialized = true;

    // DOM Elements
    let chatIcon, chatWindow, closeBtn, sendBtn, input, messagesContainer, typingIndicator, suggestionsContainer;

    // State
    let conversationId = generateUUID();
    let isSending = false;

    // Initialize on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    function init() {
        try {
            // Get DOM elements
            chatIcon = document.getElementById('aiChatIcon');
            chatWindow = document.getElementById('aiChatWindow');
            closeBtn = document.getElementById('aiCloseBtn');
            sendBtn = document.getElementById('aiSendBtn');
            input = document.getElementById('aiInput');
            messagesContainer = document.getElementById('aiMessages');
            typingIndicator = document.getElementById('aiTyping');
            suggestionsContainer = document.getElementById('aiSuggestions');

            if (!chatIcon || !chatWindow) {
                console.warn('AI Chat widget not found on this page');
                return;
            }

            // Attach event listeners
            chatIcon.addEventListener('click', openChat);
            closeBtn.addEventListener('click', closeChat);
            sendBtn.addEventListener('click', sendMessage);
            
            // Enter to send, Shift+Enter for new line
            input.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    sendMessage();
                }
            });

            // Auto-resize textarea
            input.addEventListener('input', autoResizeTextarea);

            // Attach suggestion chips
            attachSuggestionHandlers();

            console.log('✅ AI Chat widget initialized');

        } catch (error) {
            console.error('AI Chat init error:', error);
        }
    }

    // ==================== UI Functions ====================

    function openChat() {
        chatWindow.hidden = false;
        chatIcon.style.display = 'none';
        input.focus();
    }

    function closeChat() {
        chatWindow.hidden = true;
        chatIcon.style.display = 'flex';
    }

    function autoResizeTextarea() {
        input.style.height = 'auto';
        input.style.height = Math.min(input.scrollHeight, 120) + 'px';
    }

    function scrollToBottom() {
        if (messagesContainer) {
            messagesContainer.scrollTop = messagesContainer.scrollHeight;
        }
    }

    // ==================== Message Functions ====================

    async function sendMessage() {
        const message = input.value.trim();
        
        if (!message || isSending) return;

        // Add user message to UI
        addUserMessage(message);
        input.value = '';
        input.style.height = 'auto';

        // Hide suggestions after first message
        if (suggestionsContainer) {
            suggestionsContainer.style.display = 'none';
        }

        // Show typing indicator
        showTyping();

        isSending = true;
        sendBtn.disabled = true;

        try {
            console.log('🔵 Sending request to /api/ai-agent/chat');
            console.log('Message:', message);
            
            const response = await fetch('/api/ai-agent/chat', {
                method: 'POST',
                headers: { 
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                body: JSON.stringify({ 
                    message: message,
                    conversationId: conversationId
                })
            });

            console.log('Response status:', response.status);
            
            // Check if response is JSON
            const contentType = response.headers.get('content-type');
            if (!contentType || !contentType.includes('application/json')) {
                const text = await response.text();
                console.error('❌ Server returned non-JSON response:', text);
                throw new Error('Server trả về response không phải JSON. Status: ' + response.status);
            }

            const data = await response.json();
            console.log('Response data:', data);

            hideTyping();

            if (data.success) {
                addBotMessage(data.message);
            } else {
                addBotMessage('Xin lỗi, có lỗi xảy ra: ' + (data.error || 'Unknown error'));
            }

        } catch (error) {
            console.error('❌ Send message error:', error);
            hideTyping();
            
            if (error.message.includes('Failed to fetch')) {
                addBotMessage('⚠️ Không thể kết nối đến server. Kiểm tra xem server có đang chạy không?');
            } else if (error.message.includes('404') || error.message.includes('Not Found')) {
                addBotMessage('⚠️ Endpoint /api/ai-agent/chat không tồn tại. Kiểm tra lại Controller!');
            } else {
                addBotMessage('⚠️ Lỗi: ' + error.message);
            }
        } finally {
            isSending = false;
            sendBtn.disabled = false;
            input.focus();
        }
    }

    function addUserMessage(text) {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'ai-message user';
        messageDiv.innerHTML = `
            <div class="ai-message-avatar">
                <i class="fas fa-user"></i>
            </div>
            <div class="ai-message-content">
                <p>${escapeHtml(text)}</p>
            </div>
        `;
        messagesContainer.appendChild(messageDiv);
        scrollToBottom();
    }

    function addBotMessage(text) {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'ai-message bot';
        
        // Format text with line breaks and basic markdown
        const formattedText = formatBotMessage(text);
        
        messageDiv.innerHTML = `
            <div class="ai-message-avatar">
                <i class="fas fa-robot"></i>
            </div>
            <div class="ai-message-content">
                ${formattedText}
            </div>
        `;
        messagesContainer.appendChild(messageDiv);
        scrollToBottom();
    }

    function formatBotMessage(text) {
        if (!text) return '<p>...</p>';

        // Convert markdown-style formatting
        let formatted = text
            // Bold: **text** -> <strong>text</strong>
            .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
            // Italic: *text* -> <em>text</em>
            .replace(/\*(.*?)\*/g, '<em>$1</em>')
            // Line breaks
            .replace(/\n\n/g, '</p><p>')
            .replace(/\n/g, '<br>');

        // Wrap in paragraph if not already
        if (!formatted.startsWith('<p>')) {
            formatted = '<p>' + formatted + '</p>';
        }

        // Convert bullet points (- item or • item)
        formatted = formatted.replace(/<p>[-•]\s*(.*?)<\/p>/g, '<li>$1</li>');
        
        // Wrap consecutive <li> in <ul>
        formatted = formatted.replace(/(<li>.*?<\/li>)+/g, '<ul>$&</ul>');

        return formatted;
    }

    function showTyping() {
        if (typingIndicator) {
            typingIndicator.hidden = false;
            typingIndicator.style.display = 'flex'; // Ensure visible
            scrollToBottom();
        }
    }

    function hideTyping() {
        if (typingIndicator) {
            typingIndicator.hidden = true;
            typingIndicator.style.display = 'none'; // Ensure hidden
        }
    }

    // ==================== Suggestion Chips ====================

    function attachSuggestionHandlers() {
        const chips = document.querySelectorAll('.suggestion-chip');
        chips.forEach(chip => {
            chip.addEventListener('click', function() {
                const question = this.dataset.question;
                if (question) {
                    input.value = question;
                    sendMessage();
                }
            });
        });
    }

    // ==================== Utilities ====================

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function generateUUID() {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
            const r = Math.random() * 16 | 0;
            const v = c === 'x' ? r : (r & 0x3 | 0x8);
            return v.toString(16);
        });
    }

    // ==================== Export for debugging ====================
    window.aiChat = {
        open: openChat,
        close: closeChat,
        send: sendMessage,
        conversationId: () => conversationId
    };

})();