/**
 * messenger-stickers.js - Sticker, Tenor GIF, and Emoji Management
 * Modularized from messenger.js for maintainability and performance.
 */
(function() {
    'use strict';

    let currentStickerCollection = 'popular';
    let recentStickers = JSON.parse(localStorage.getItem('recentStickers') || '[]');
    let suggestionTimeout = null;
    let searchTimeout = null;
    let emojiPicker = null;

    const STICKER_KEYWORDS = [
        'cười', 'vui', 'buồn', 'khóc', 'yêu', 'tim', 'ok', 'like',
        'cảm ơn', 'hoan hô', 'wink', 'dễ thương', 'ngon', 'ngầu',
        'giận', 'tức', 'sợ', 'hoảng', 'ngượng', 'chó', 'mèo', 'cún',
        'thỏ', 'cáo', 'gấu', 'heo', 'hổ', 'ngựa', 'hamburger', 'bánh',
        'kem', 'kẹo', 'party', 'tiệc', 'quà', 'pháo hoa', 'noel',
        'halloween', 'ý tưởng', 'bom', 'ngủ', 'mồ hôi', 'cơ bắp',
        'khỏe', 'chóng mặt', 'nói', 'suy nghĩ', 'hôn', 'kim cương',
        'hoa', 'chạy', 'bóng đá', 'bóng rổ', 'tennis', 'bơi', 'golf'
    ];

    function getPartnerId() {
        return window.MessengerState ? window.MessengerState.currentPartnerId : null;
    }

    function sendApi(payload) {
        if (window.MessengerState && typeof window.MessengerState.sendApiRequest === 'function') {
            return window.MessengerState.sendApiRequest(payload);
        }
        if (typeof window.sendApiRequest === 'function') {
            return window.sendApiRequest(payload);
        }
        console.warn('[MessengerStickers] sendApiRequest not available');
    }

    function showToast(msg, type) {
        if (typeof window.showToast === 'function') {
            window.showToast(msg, type);
        } else {
            console.log(`[Toast ${type}]`, msg);
        }
    }

    // Toggle Sticker Menu
    function toggleStickers() {
        const menu = $('#stickerMenu');
        if (!menu.length) return;

        if (menu.hasClass('show')) {
            menu.removeClass('show').hide();
        } else {
            hideStickerSuggestions();
            menu.css({
                bottom: '80px',
                left: '20px'
            });
            menu.addClass('show').css('display', 'flex');

            if ($('#stickerGrid').is(':empty')) {
                loadStickerCategory(currentStickerCollection);
            }
            renderRecentStickers();
        }
    }

    // Initialize Sticker Menu HTML
    function initStickerMenu() {
        const menu = $('#stickerMenu');
        if (!menu.length) return;

        const categories = window.TENOR_CATEGORIES || {
            popular: { name: 'Phổ biến' },
            emotions: { name: 'Cảm xúc' },
            animals: { name: 'Động vật' }
        };

        menu.html(`
            <div class="sticker-header">
                <div class="sticker-tabs" id="stickerTabs">
                    ${Object.entries(categories).map(([id, cat]) => `
                        <button class="tab-btn ${id === 'popular' ? 'active' : ''}" 
                                data-category="${id}" 
                                onclick="window.switchStickerCategory('${id}', this)">
                            ${cat.name}
                        </button>
                    `).join('')}
                </div>
                <div class="sticker-header-actions">
                    <div class="sticker-search-box">
                        <input type="text" id="stickerSearchInput" placeholder="Tìm kiếm stickers..." 
                            onkeyup="window.searchStickersDebounced(this.value)">
                        <i class="fas fa-search"></i>
                    </div>
                    <i class="fas fa-times close-sticker" onclick="window.toggleStickers()"></i>
                </div>
            </div>
            
            <div class="sticker-content">
                <div class="sticker-grid" id="stickerGrid">
                    <div class="loading-stickers">
                        <i class="fas fa-spinner fa-spin"></i>
                        <p>Đang tải stickers...</p>
                    </div>
                </div>
                
                <div class="recent-stickers-section" id="recentStickersSection" style="display: none;">
                    <div class="section-title">
                        <i class="fas fa-history"></i>
                        <span>Gần đây</span>
                    </div>
                    <div class="recent-stickers-grid" id="recentStickersGrid"></div>
                </div>
            </div>
        `);

        loadStickerCategory('popular');
        renderRecentStickers();
    }

    function getBuiltinStickers(category) {
        const BUILTIN_PACK = [
            { id: 'b1', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f600/512.gif', preview: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f600/512.gif', tags: ['cười', 'vui'] },
            { id: 'b2', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f602/512.gif', preview: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f602/512.gif', tags: ['cười', 'khóc'] },
            { id: 'b3', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f970/512.gif', preview: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f970/512.gif', tags: ['yêu', 'tim'] },
            { id: 'b4', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f60d/512.gif', preview: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f60d/512.gif', tags: ['yêu', 'thích'] },
            { id: 'b5', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f618/512.gif', preview: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f618/512.gif', tags: ['hôn', 'kiss'] },
            { id: 'b6', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f929/512.gif', preview: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f929/512.gif', tags: ['sao', 'mắt'] },
            { id: 'b7', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f973/512.gif', preview: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f973/512.gif', tags: ['tiệc', 'party'] },
            { id: 'b8', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f60e/512.gif', preview: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f60e/512.gif', tags: ['ngầu', 'cool'] },
            { id: 'b9', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f917/512.gif', preview: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f917/512.gif', tags: ['ôm', 'hug'] },
            { id: 'b10', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f44d/512.gif', preview: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f44d/512.gif', tags: ['ok', 'like'] },
            { id: 'b11', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f44f/512.gif', preview: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f44f/512.gif', tags: ['vỗ tay', 'hoan hô'] },
            { id: 'b12', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f62d/512.gif', preview: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f62d/512.gif', tags: ['khóc', 'buồn'] },
            { id: 'b13', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f621/512.gif', preview: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f621/512.gif', tags: ['giận', 'tức'] },
            { id: 'b14', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f631/512.gif', preview: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f631/512.gif', tags: ['hét', 'sợ'] },
            { id: 'b15', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f431/512.gif', preview: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f431/512.gif', tags: ['mèo', 'cat'] },
            { id: 'b16', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f436/512.gif', preview: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f436/512.gif', tags: ['chó', 'dog'] },
            { id: 'b17', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f389/512.gif', preview: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f389/512.gif', tags: ['chúc mừng', 'party'] },
            { id: 'b18', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f525/512.gif', preview: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f525/512.gif', tags: ['lửa', 'hot'] },
            { id: 'b19', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/2764_fe0f/512.gif', preview: 'https://fonts.gstatic.com/s/e/notoemoji/latest/2764_fe0f/512.gif', tags: ['tim', 'love'] },
            { id: 'b20', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f680/512.gif', preview: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f680/512.gif', tags: ['tên lửa', 'bay'] }
        ];

        if (category === 'emotions') {
            return BUILTIN_PACK.filter(s => s.tags.some(t => ['cười', 'khóc', 'buồn', 'giận', 'sợ', 'yêu'].includes(t)));
        } else if (category === 'animals') {
            return BUILTIN_PACK.filter(s => s.tags.some(t => ['mèo', 'chó'].includes(t)));
        }
        return BUILTIN_PACK;
    }

    async function loadStickerCategory(category) {
        const grid = $('#stickerGrid');
        if (!grid.length) return;
        grid.html('<div class="loading-stickers"><i class="fas fa-spinner fa-spin"></i><p>Đang tải...</p></div>');

        let stickers = [];
        try {
            if (typeof window.loadTenorStickers === 'function') {
                stickers = await window.loadTenorStickers(category);
            } else if (window.STICKER_COLLECTIONS && window.STICKER_COLLECTIONS[category]) {
                stickers = window.STICKER_COLLECTIONS[category].items || [];
            }
        } catch (e) {
            console.warn('[MessengerStickers] Failed to load remote stickers, using built-in:', e);
        }

        if (!stickers || stickers.length === 0) {
            stickers = getBuiltinStickers(category);
        }

        renderStickerGrid(stickers);
    }

    function renderStickerGrid(stickers) {
        const grid = $('#stickerGrid');
        if (!grid.length) return;

        if (!stickers || stickers.length === 0) {
            grid.html('<div class="text-center p-4 text-muted">Không tìm thấy sticker phù hợp</div>');
            return;
        }

        let html = '';
        stickers.forEach((sticker, index) => {
            const stickerDataStr = encodeURIComponent(JSON.stringify(sticker));
            const stickerUrl = sticker.url || sticker.preview;
            html += `
                <div class="sticker-item" onclick="window.sendTenorSticker('${sticker.id || index}', '${stickerDataStr}')">
                    <img src="${sticker.preview || stickerUrl}" 
                        data-src="${stickerUrl}" 
                        alt="Sticker" 
                        loading="lazy"
                        class="sticker-gif">
                    <div class="sticker-hover">
                        <i class="fas fa-paper-plane"></i>
                    </div>
                </div>
            `;
        });

        grid.html(html);

        grid.find('.sticker-gif').each(function() {
            const img = $(this);
            if (img.attr('data-src')) {
                img.attr('src', img.attr('data-src'));
                img.removeAttr('data-src');
            }
        });
    }

    function switchStickerCategory(categoryId, btnElement) {
        currentStickerCollection = categoryId;
        $('.tab-btn').removeClass('active');
        $(btnElement).addClass('active');
        loadStickerCategory(categoryId);
    }

    function searchStickersDebounced(query) {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            performStickerSearch(query);
        }, 300);
    }

    async function performStickerSearch(query) {
        const grid = $('#stickerGrid');
        if (!grid.length) return;

        if (!query || query.trim() === '') {
            const activeCategory = $('.tab-btn.active').data('category') || 'popular';
            loadStickerCategory(activeCategory);
            return;
        }

        grid.html('<div class="loading-stickers"><i class="fas fa-spinner fa-spin"></i><p>Đang tìm kiếm...</p></div>');

        let stickers = [];
        if (typeof window.searchTenorStickers === 'function') {
            stickers = await window.searchTenorStickers(query);
        }
        renderStickerGrid(stickers);
    }

    function sendTenorSticker(stickerId, stickerData) {
        try {
            const sticker = JSON.parse(decodeURIComponent(stickerData));
            addToRecentStickers(sticker);
            $('#stickerMenu').hide();

            const partnerId = getPartnerId();
            if (partnerId) {
                const payload = {
                    receiverId: partnerId,
                    content: sticker.url,
                    type: 'STICKER',
                    metadata: {
                        source: 'tenor',
                        stickerId: sticker.id,
                        width: sticker.width,
                        height: sticker.height
                    }
                };
                sendApi(payload);
            } else {
                showToast('Vui lòng chọn người nhận trước', 'error');
            }
        } catch (error) {
            console.error('[MessengerStickers] Error sending tenor sticker:', error);
        }
    }

    function sendSticker(url, source = 'local', index = 0) {
        const partnerId = getPartnerId();
        if (!partnerId) {
            showToast('Vui lòng chọn người nhận trước', 'error');
            return;
        }

        $('#stickerMenu').hide();
        addToRecentStickers({ url: url, preview: url });

        const payload = {
            receiverId: partnerId,
            content: url,
            type: 'STICKER',
            metadata: {
                source: source,
                index: index
            }
        };

        sendApi(payload);
    }

    function addToRecentStickers(sticker) {
        if (!sticker) return;
        const stickerUrl = typeof sticker === 'string' ? sticker : sticker.url;
        recentStickers = recentStickers.filter(s => (typeof s === 'string' ? s : s.url) !== stickerUrl);
        recentStickers.unshift(sticker);
        recentStickers = recentStickers.slice(0, 12);
        localStorage.setItem('recentStickers', JSON.stringify(recentStickers));
        renderRecentStickers();
    }

    function renderRecentStickers() {
        const grid = $('#recentStickersGrid');
        if (!grid.length) return;

        if (recentStickers.length === 0) {
            $('#recentStickersSection').hide();
            return;
        }

        let html = '';
        recentStickers.forEach(sticker => {
            const stickerDataStr = encodeURIComponent(JSON.stringify(sticker));
            const previewUrl = sticker.preview || sticker.url || sticker;
            html += `
                <div class="sticker-item recent" onclick="window.sendTenorSticker('${sticker.id || ''}', '${stickerDataStr}')">
                    <img src="${previewUrl}" alt="Sticker">
                </div>
            `;
        });

        grid.html(html);
        $('#recentStickersSection').show();
    }

    function setupStickerSuggestions() {
        const input = $('#msgInput');
        if (!input.length) return;

        input.on('input', function() {
            const message = $(this).val().trim();
            if (suggestionTimeout) clearTimeout(suggestionTimeout);

            if (message.length >= 2) {
                suggestionTimeout = setTimeout(() => {
                    const keywords = analyzeMessageForStickers(message);
                    if (keywords.length > 0) {
                        showStickerSuggestions(keywords);
                    } else {
                        hideStickerSuggestions();
                    }
                }, 500);
            } else {
                hideStickerSuggestions();
            }
        });

        $(document).on('click', function(e) {
            if (!$(e.target).closest('#stickerSuggestions, #msgInput').length) {
                hideStickerSuggestions();
            }
        });
    }

    function analyzeMessageForStickers(message) {
        const words = message.toLowerCase().split(/\s+/);
        return words.filter(word => STICKER_KEYWORDS.some(k => k.includes(word) || word.includes(k)));
    }

    async function showStickerSuggestions(keywords) {
        const container = $('#stickerSuggestions');
        const grid = $('#suggestionsGrid');
        if (!container.length || !grid.length) return;

        let suggestions = [];
        if (typeof window.getStickerSuggestions === 'function') {
            suggestions = await window.getStickerSuggestions(keywords.join(' '));
        }

        if (!suggestions || suggestions.length === 0) {
            hideStickerSuggestions();
            return;
        }

        grid.empty();
        suggestions.slice(0, 12).forEach(sticker => {
            const stickerDataStr = encodeURIComponent(JSON.stringify(sticker));
            grid.append(`
                <div class="sticker-item" onclick="window.sendTenorSticker('${sticker.id}', '${stickerDataStr}')">
                    <img src="${sticker.preview || sticker.url}" alt="Sticker">
                </div>
            `);
        });

        container.css('display', 'block');
        setTimeout(() => container.css('opacity', 1), 10);
    }

    function hideStickerSuggestions() {
        const container = $('#stickerSuggestions');
        if (container.length) {
            container.css('opacity', 0);
            setTimeout(() => container.hide(), 300);
        }
    }

    function initEmojiPicker() {
        console.log('[MessengerStickers] Initializing Emoji Picker...');
        const emojiBtn = document.getElementById('emojiBtn');
        if (emojiBtn && typeof EmojiButton !== 'undefined') {
            emojiPicker = new EmojiButton({
                position: 'top-end',
                theme: 'dark',
                autoHide: true
            });
            emojiPicker.on('emoji', selection => {
                const input = document.getElementById('msgInput');
                if (input) {
                    input.value += selection.emoji;
                    input.focus();
                }
            });
            emojiBtn.addEventListener('click', () => {
                emojiPicker.togglePicker(emojiBtn);
            });
        }
    }

    // Public API
    window.MessengerStickers = {
        init: function() {
            initStickerMenu();
            setupStickerSuggestions();
            renderRecentStickers();
            setTimeout(initEmojiPicker, 1000);
        },
        toggleStickers,
        switchStickerCategory,
        searchStickersDebounced,
        sendTenorSticker,
        sendSticker,
        hideStickerSuggestions
    };

    // Backward-compatible global wrappers for inline HTML event handlers
    window.toggleStickers = toggleStickers;
    window.switchStickerCategory = switchStickerCategory;
    window.searchStickersDebounced = searchStickersDebounced;
    window.sendTenorSticker = sendTenorSticker;
    window.sendSticker = sendSticker;
    window.hideStickerSuggestions = hideStickerSuggestions;
    window.addToRecentStickers = addToRecentStickers;
    window.getRecentStickers = function() { return recentStickers; };

})();
