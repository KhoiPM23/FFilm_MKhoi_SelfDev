/**
 * messenger-stickers.js - Sticker and GIF Management
 * Supporting Stickers, GIFs, instant search, and smooth scrolling.
 */
(function() {
    'use strict';

    let currentMainTab = 'stickers'; // 'stickers' or 'gifs'
    let searchTimeout = null;
    let tenorCache = {
        stickers: [],
        gifs: []
    };

    const BUILTIN_STICKERS = [
        { id: 's1', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f600/512.gif', tags: ['cười', 'vui', 'smile', 'happy'] },
        { id: 's2', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f602/512.gif', tags: ['cười', 'khóc', 'lol', 'laugh'] },
        { id: 's3', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f970/512.gif', tags: ['yêu', 'tim', 'love', 'crush'] },
        { id: 's4', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f60d/512.gif', tags: ['thích', 'mê', 'heart', 'eyes'] },
        { id: 's5', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f618/512.gif', tags: ['hôn', 'kiss', 'thương'] },
        { id: 's6', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f929/512.gif', tags: ['sao', 'mắt', 'wow', 'star'] },
        { id: 's7', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f973/512.gif', tags: ['tiệc', 'party', 'chúc mừng'] },
        { id: 's8', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f60e/512.gif', tags: ['ngầu', 'cool', 'kính râm'] },
        { id: 's9', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f917/512.gif', tags: ['ôm', 'hug', 'thân thiện'] },
        { id: 's10', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f44d/512.gif', tags: ['ok', 'like', 'tuyệt', 'đồng ý'] },
        { id: 's11', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f44f/512.gif', tags: ['vỗ tay', 'hoan hô', 'clap', 'bravo'] },
        { id: 's12', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f62d/512.gif', tags: ['khóc', 'buồn', 'cry', 'sad'] },
        { id: 's13', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f621/512.gif', tags: ['giận', 'tức', 'angry', 'mad'] },
        { id: 's14', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f631/512.gif', tags: ['hét', 'sợ', 'scared', 'shock'] },
        { id: 's15', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f92f/512.gif', tags: ['bùng nổ', 'shock', 'mindblown'] },
        { id: 's16', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f97a/512.gif', tags: ['năn nỉ', 'pleading', 'dễ thương'] },
        { id: 's17', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f431/512.gif', tags: ['mèo', 'cat', 'meow'] },
        { id: 's18', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f436/512.gif', tags: ['chó', 'dog', 'cún'] },
        { id: 's19', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f389/512.gif', tags: ['pháo hoa', 'chúc mừng', 'celebrate'] },
        { id: 's20', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f525/512.gif', tags: ['lửa', 'hot', 'cháy', 'fire'] },
        { id: 's21', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/2764_fe0f/512.gif', tags: ['tim', 'đỏ', 'heart', 'love'] },
        { id: 's22', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f680/512.gif', tags: ['tên lửa', 'bay', 'rocket', 'speed'] },
        { id: 's23', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f923/512.gif', tags: ['lăn cười', 'rofl', 'cười lăn'] },
        { id: 's24', url: 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f914/512.gif', tags: ['suy nghĩ', 'thinking', 'hmm'] }
    ];

    const BUILTIN_GIFS = [
        { id: 'g1', url: 'https://media.giphy.com/media/artj92V8o75VPL7AeQ/giphy.gif', tags: ['yes', 'đồng ý', 'gật đầu', 'ok'] },
        { id: 'g2', url: 'https://media.giphy.com/media/3o7abKhOpu0NwenH3O/giphy.gif', tags: ['cười', 'vui', 'laugh', 'funny'] },
        { id: 'g3', url: 'https://media.giphy.com/media/26u4cqiYI30juCOGY/giphy.gif', tags: ['vỗ tay', 'applaud', 'bravo', 'clap'] },
        { id: 'g4', url: 'https://media.giphy.com/media/l0MYt5jPR6QX5pnqM/giphy.gif', tags: ['cheers', 'nâng ly', 'chúc mừng', 'leo'] },
        { id: 'g5', url: 'https://media.giphy.com/media/xT0xeJpnrWC4XWblEk/giphy.gif', tags: ['nhảy', 'dance', 'quẩy', 'party'] },
        { id: 'g6', url: 'https://media.giphy.com/media/l41lI4bYmcsPJX9Go/giphy.gif', tags: ['shock', 'bất ngờ', 'wow', 'mindblown'] },
        { id: 'g7', url: 'https://media.giphy.com/media/d2lcHJTG5Tscg/giphy.gif', tags: ['khóc', 'buồn', 'cry', 'sad'] },
        { id: 'g8', url: 'https://media.giphy.com/media/111ebonMs90YLu/giphy.gif', tags: ['like', 'thumbs up', 'tuyệt', 'ok'] },
        { id: 'g9', url: 'https://media.giphy.com/media/5GoVLqeAOo6PK/giphy.gif', tags: ['hào hứng', 'excited', 'yay'] },
        { id: 'g10', url: 'https://media.giphy.com/media/xT9IgG50Fb7Mi0prBC/giphy.gif', tags: ['chào', 'wave', 'hello', 'hi'] },
        { id: 'g11', url: 'https://media.giphy.com/media/OPU6wzx8JrHna/giphy.gif', tags: ['mếu', 'buồn', 'sad', 'crying'] },
        { id: 'g12', url: 'https://media.giphy.com/media/l3q2K5jinAlChoCLS/giphy.gif', tags: ['chớp mắt', 'blink', 'gì cơ'] },
        { id: 'g13', url: 'https://media.giphy.com/media/3o85xGocUH8RYoDKKs/giphy.gif', tags: ['facepalm', 'bó tay', 'chán'] },
        { id: 'g14', url: 'https://media.giphy.com/media/jpbnoe3UIa8TU8LM13/giphy.gif', tags: ['mèo quẩy', 'cat dance', 'party'] },
        { id: 'g15', url: 'https://media.giphy.com/media/26ufdipQqU2lhNA4g/giphy.gif', tags: ['popcorn', 'ăn bắp', 'xem kịch'] },
        { id: 'g16', url: 'https://media.giphy.com/media/l0MYEqEzwMWFCg8rm/giphy.gif', tags: ['thả tim', 'heart', 'love'] }
    ];

    function getPartnerId() {
        if (window.MessengerState && window.MessengerState.currentPartnerId) {
            return window.MessengerState.currentPartnerId;
        }
        return window.currentPartnerId || null;
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

    // Toggle Sticker Menu
    function toggleStickers() {
        const menu = $('#stickerMenu');
        if (!menu.length) return;

        if (menu.is(':visible')) {
            menu.hide();
        } else {
            menu.css({
                display: 'flex',
                bottom: '80px',
                left: '20px'
            });
            renderActiveTab();
            setTimeout(() => {
                $('#stickerSearchInput').focus();
            }, 50);
        }
    }

    function switchStickerMainTab(tab) {
        currentMainTab = tab;
        if (tab === 'stickers') {
            $('#tabStickersBtn').addClass('active');
            $('#tabGifsBtn').removeClass('active');
            $('#stickerSearchInput').attr('placeholder', 'Tìm kiếm stickers...');
        } else {
            $('#tabGifsBtn').addClass('active');
            $('#tabStickersBtn').removeClass('active');
            $('#stickerSearchInput').attr('placeholder', 'Tìm kiếm GIFs...');
        }
        $('#stickerSearchInput').val('');
        renderActiveTab();
    }

    function fetchTenorData(query = '') {
        const isGif = (currentMainTab === 'gifs');
        const endpoint = query 
            ? `/api/tenor/search?q=${encodeURIComponent(query)}&limit=24`
            : `/api/tenor/trending?limit=24`;
        
        return fetch(endpoint)
            .then(res => res.ok ? res.json() : Promise.reject())
            .then(data => {
                if (data && data.results && data.results.length > 0) {
                    return data.results.map(r => ({
                        id: r.id,
                        url: r.media_formats?.gif?.url || r.media_formats?.tinygif?.url,
                        tags: r.tags || []
                    })).filter(x => !!x.url);
                }
                return [];
            })
            .catch(() => []);
    }

    function renderActiveTab(filterQuery = '') {
        const grid = $('#stickerGrid');
        if (!grid.length) return;

        const query = (filterQuery || '').trim().toLowerCase();
        const isGif = (currentMainTab === 'gifs');
        let builtinItems = isGif ? BUILTIN_GIFS : BUILTIN_STICKERS;

        if (query) {
            builtinItems = builtinItems.filter(item => 
                item.tags.some(tag => tag.toLowerCase().includes(query))
            );
        }

        // Render builtin first
        renderItems(builtinItems, isGif);

        // Enhance with Tenor
        fetchTenorData(query).then(tenorItems => {
            if (tenorItems.length > 0) {
                const combined = [...tenorItems, ...builtinItems];
                renderItems(combined, isGif);
            }
        });
    }

    function renderItems(items, isGif) {
        const grid = $('#stickerGrid');
        if (!grid.length) return;

        if (items.length === 0) {
            grid.html(`<div style="grid-column: 1 / -1; text-align: center; color: #888; padding: 40px 10px;">
                <i class="fas fa-search" style="font-size: 24px; margin-bottom: 8px; opacity: 0.5;"></i>
                <p style="margin: 0; font-size: 13px;">Không tìm thấy ${isGif ? 'GIF' : 'sticker'} nào</p>
            </div>`);
            return;
        }

        let html = '';
        items.forEach((item) => {
            const mediaType = isGif ? 'GIF' : 'STICKER';
            html += `
                <div class="sticker-item" onclick="window.sendChosenSticker('${item.url}', '${mediaType}')">
                    <img src="${item.url}" alt="${mediaType}" loading="lazy" onerror="this.parentElement.remove()">
                    <div class="sticker-hover">
                        <i class="fas fa-paper-plane"></i>
                    </div>
                </div>
            `;
        });

        grid.html(html);
    }

    function searchStickersAndGifs(query) {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            renderActiveTab(query);
        }, 300);
    }

    function sendChosenSticker(url, type = 'STICKER') {
        const partnerId = getPartnerId();
        if (!partnerId) {
            if (typeof window.showToast === 'function') {
                window.showToast('Vui lòng chọn một cuộc trò chuyện trước', 'error');
            } else {
                alert('Vui lòng chọn người nhận');
            }
            return;
        }

        $('#stickerMenu').hide();

        const resolvedType = (type === 'GIF' || type === 'STICKER') ? type : 'STICKER';
        const tempId = 'temp-' + Date.now();

        // Optimistic UI append
        if (typeof window.appendMessageToUI === 'function') {
            window.appendMessageToUI({
                id: tempId,
                senderId: (window.currentUser ? window.currentUser.userID : 0),
                content: url,
                type: resolvedType,
                formattedTime: 'Đang gửi...'
            }, true);
        }

        const payload = {
            receiverId: partnerId,
            content: url,
            type: resolvedType
        };

        sendApi(payload);

        // Update preview in sidebar
        if (typeof window.updateConversationPreview === 'function') {
            window.updateConversationPreview({
                senderId: (window.currentUser ? window.currentUser.userID : 0),
                receiverId: partnerId,
                content: resolvedType === 'STICKER' ? 'Đã gửi 1 nhãn dán' : 'Đã gửi 1 GIF',
                type: resolvedType
            });
        }
    }

    let suggestionDebounce = null;

    function initStickerSuggestions() {
        const msgInput = $('#msgInput');
        if (!msgInput.length) return;

        msgInput.off('input.stickersug').on('input.stickersug', function() {
            const message = $(this).val().trim();
            clearTimeout(suggestionDebounce);

            if (message.length >= 2) {
                suggestionDebounce = setTimeout(async () => {
                    if (typeof window.getStickerSuggestions === 'function') {
                        try {
                            const suggestions = await window.getStickerSuggestions(message);
                            showStickerSuggestions(suggestions);
                        } catch (err) {
                            console.error('[Stickers] Error fetching suggestions:', err);
                            hideStickerSuggestions();
                        }
                    }
                }, 400);
            } else {
                hideStickerSuggestions();
            }
        });
    }

    function showStickerSuggestions(stickers) {
        if (!stickers || stickers.length === 0) {
            hideStickerSuggestions();
            return;
        }

        const container = $('#stickerSuggestions');
        const grid = $('#suggestionsGrid');
        if (!container.length || !grid.length) return;

        grid.empty();
        stickers.slice(0, 12).forEach(sticker => {
            const stickerData = encodeURIComponent(JSON.stringify(sticker));
            const imgUrl = sticker.preview || sticker.url;
            grid.append(`
                <div class="sticker-item" onclick="window.sendTenorSticker('${sticker.id}', '${stickerData}')">
                    <img src="${imgUrl}" alt="Sticker" loading="lazy">
                </div>
            `);
        });

        container.stop(true, true).css({ display: 'block' }).animate({ opacity: 1 }, 200);
    }

    function hideStickerSuggestions() {
        const container = $('#stickerSuggestions');
        if (!container.length) return;
        container.stop(true, true).animate({ opacity: 0 }, 150, function() {
            container.hide();
        });
    }

    // Public API
    window.MessengerStickers = {
        init: function() {
            renderActiveTab();
            initStickerSuggestions();
        },
        toggleStickers,
        switchStickerMainTab,
        searchStickersAndGifs,
        sendChosenSticker,
        initStickerSuggestions,
        hideStickerSuggestions
    };

    // Global bindings
    window.toggleStickers = toggleStickers;
    window.switchStickerMainTab = switchStickerMainTab;
    window.searchStickersAndGifs = searchStickersAndGifs;
    window.sendChosenSticker = sendChosenSticker;
    window.initStickerSuggestions = initStickerSuggestions;
    window.hideStickerSuggestions = hideStickerSuggestions;
    window.sendSticker = function(url) { sendChosenSticker(url, 'STICKER'); };
    window.sendTenorSticker = function(id, dataStr) {
        try {
            const data = JSON.parse(decodeURIComponent(dataStr));
            sendChosenSticker(data.url || data.preview, 'STICKER');
        } catch(e) {
            sendChosenSticker(dataStr, 'STICKER');
        }
    };

    $(document).ready(function() {
        renderActiveTab();
        initStickerSuggestions();
        
        // Close when clicking outside
        $(document).on('click', function(e) {
            if (!$(e.target).closest('#stickerMenu, #stickerBtn').length) {
                $('#stickerMenu').hide();
            }
            if (!$(e.target).closest('#stickerSuggestions, #msgInput').length) {
                hideStickerSuggestions();
            }
        });
    });

})();
