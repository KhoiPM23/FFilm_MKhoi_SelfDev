/**
 * MESSENGER CALLS MODULE
 * Extracted from messenger.js — handles PeerJS/WebRTC calls, call UI, and call history.
 * Communicates with the core messenger via window.MessengerCalls and shared state.
 */
(function() {
    'use strict';

    // --- Call State ---
    let myPeer = null;
    let myPeerId = null;
    let currentCall = null;
    let localStream = null;
    let remoteStream = null;
    let callTimerInterval = null;
    let callTimeout = null;
    let callDuration = 0;
    let incomingCallData = null; // { peerId, senderId, senderName, senderAvatar, callType, callId }
    let currentCallId = null;
    let callInitiatorId = null;
    let currentCallType = 'VIDEO';

    let availableCameras = [];
    let currentCameraIndex = 0;
    let recordedCallIds = new Set();
    let isEndingCall = false;

    // Shared state accessors (set by core messenger.js)
    function getState() {
        return window.MessengerState || {};
    }

    function showToast(msg, type) {
        if (typeof window.showToast === 'function') {
            window.showToast(msg, type);
        } else {
            console.log('[Calls-Toast]', type, msg);
        }
    }

    // --- 1. PEERJS SETUP (WEB RTC) ---
    function initPeerJS() {
        if (!window.Peer) {
            console.error('PeerJS library not loaded');
            return;
        }

        const currentUser = getState().currentUser || window.currentUser || {};
        myPeerId = `user_${currentUser.userID}_${Date.now()}`;

        myPeer = new Peer(myPeerId, {
            host: '0.peerjs.com',
            port: 443,
            path: '/',
            secure: true,
            config: {
                'iceServers': [
                    { urls: 'stun:stun.l.google.com:19302' },
                    { urls: 'stun:global.stun.twilio.com:3478' }
                ]
            },
            debug: 0
        });

        myPeer.on('open', (id) => {
            console.log('✅ PeerJS Connected. My ID:', id);
        });

        myPeer.on('call', (call) => {
            console.log('📞 Incoming call from:', call.peer);
            if (callTimeout) { clearTimeout(callTimeout); callTimeout = null; }

            navigator.mediaDevices.getUserMedia({ video: true, audio: true })
                .then(stream => {
                    localStream = stream;
                    document.getElementById('localVideo').srcObject = stream;

                    call.answer(stream);
                    currentCall = call;
                    setupCallHandlers(call);
                })
                .catch(err => {
                    console.error('Error accessing media:', err);
                    call.close();
                });
        });

        myPeer.on('error', (err) => {
            console.error('PeerJS Error:', err);
            showToast('Lỗi kết nối PeerJS: ' + err.type, 'error');
        });
    }

    // --- 2. CALL LOGIC ---
    window.startVideoCall = function() {
        const state = getState();
        if (!state.currentPartnerId) {
            showToast('Vui lòng chọn người để gọi', 'error');
            return;
        }
        if (!myPeer || !myPeer.id) {
            showToast('Đang khởi tạo kết nối...', 'info');
            setTimeout(() => window.startVideoCall(), 1000);
            return;
        }

        currentCallType = 'VIDEO';
        callInitiatorId = state.currentUser ? state.currentUser.userID : null;
        currentCallId = 'call_' + (callInitiatorId || '0') + '_' + state.currentPartnerId + '_' + Date.now();
        window.activeCallSessionId = currentCallId;

        navigator.mediaDevices.getUserMedia({ video: true, audio: true })
            .then(stream => {
                localStream = stream;
                document.getElementById('localVideo').srcObject = stream;
                showCallModal(true);

                const callData = {
                    type: 'CALL_REQ',
                    callId: currentCallId,
                    senderId: state.currentUser.userID,
                    senderName: state.currentUser.name,
                    senderAvatar: $('#headerAvatar').attr('src'),
                    receiverId: state.currentPartnerId,
                    peerId: myPeer.id,
                    callType: 'VIDEO',
                    timestamp: new Date().toISOString()
                };

                state.stompClient.send('/app/call', {}, JSON.stringify(callData));

                callTimeout = setTimeout(() => {
                    if (!currentCall) {
                        recordCallRecord(state.currentPartnerId, 'VIDEO', 'MISSED', 0, currentCallId, callInitiatorId);
                        window.endCall();
                        showToast('Không có phản hồi từ người nhận', 'error');
                    }
                }, 30000);
            })
            .catch(err => {
                console.error('Error accessing media:', err);
                showToast('Không thể truy cập camera/microphone', 'error');
            });
    };

    window.startVoiceCall = function() {
        const state = getState();
        if (!state.currentPartnerId) {
            showToast('Vui lòng chọn người để gọi', 'error');
            return;
        }
        if (!myPeer || !myPeer.id) {
            showToast('Đang khởi tạo kết nối...', 'info');
            setTimeout(() => window.startVoiceCall(), 1000);
            return;
        }

        currentCallType = 'AUDIO';
        callInitiatorId = state.currentUser ? state.currentUser.userID : null;
        currentCallId = 'call_' + (callInitiatorId || '0') + '_' + state.currentPartnerId + '_' + Date.now();
        window.activeCallSessionId = currentCallId;

        navigator.mediaDevices.getUserMedia({ video: false, audio: true })
            .then(stream => {
                localStream = stream;
                showCallModal(false);

                const callData = {
                    type: 'CALL_REQ',
                    callId: currentCallId,
                    senderId: state.currentUser.userID,
                    senderName: state.currentUser.name,
                    senderAvatar: $('#headerAvatar').attr('src'),
                    receiverId: state.currentPartnerId,
                    peerId: myPeer.id,
                    callType: 'AUDIO',
                    timestamp: new Date().toISOString()
                };

                state.stompClient.send('/app/call', {}, JSON.stringify(callData));

                callTimeout = setTimeout(() => {
                    if (!currentCall) {
                        recordCallRecord(state.currentPartnerId, 'AUDIO', 'MISSED', 0, currentCallId, callInitiatorId);
                        window.endCall();
                    }
                }, 30000);
            })
            .catch(err => {
                console.error('Error accessing microphone:', err);
                showToast('Không thể truy cập microphone', 'error');
            });
    };

    window.acceptCall = function() {
        $('#incomingCallModal').hide();
        if (!incomingCallData) return;

        const state = getState();
        const callType = incomingCallData.callType || 'VIDEO';

        navigator.mediaDevices.getUserMedia({
            video: callType === 'VIDEO',
            audio: true
        }).then(stream => {
            localStream = stream;
            document.getElementById('localVideo').srcObject = stream;
            showCallModal(callType === 'VIDEO');

            const call = myPeer.call(incomingCallData.peerId, stream);
            currentCall = call;
            setupCallHandlers(call);

            state.stompClient.send('/app/call-accepted', {}, JSON.stringify({
                receiverId: incomingCallData.senderId,
                peerId: myPeer.id
            }));
        }).catch(err => {
            console.error('Error accessing media:', err);
            showToast('Lỗi truy cập thiết bị', 'error');
            window.rejectCall();
        });
    };

    window.rejectCall = function() {
        $('#incomingCallModal').hide();
        const state = getState();

        if (incomingCallData) {
            recordCallRecord(
                incomingCallData.senderId,
                incomingCallData.callType || 'VIDEO',
                'REJECTED',
                0,
                incomingCallData.callId,
                incomingCallData.senderId
            );

            state.stompClient.send('/app/call', {}, JSON.stringify({
                type: 'CALL_DENY',
                callId: incomingCallData.callId,
                receiverId: incomingCallData.senderId,
                senderId: state.currentUser ? state.currentUser.userID : null
            }));
        }

        if (incomingCallData && incomingCallData.ringtone) {
            incomingCallData.ringtone.pause();
            incomingCallData.ringtone.currentTime = 0;
        }
        incomingCallData = null;
        showToast('Đã từ chối cuộc gọi', 'info');
    };

    window.endCall = function() {
        if (isEndingCall) return;
        isEndingCall = true;
        setTimeout(() => { isEndingCall = false; }, 1000);

        const state = getState();

        if (callTimeout) { clearTimeout(callTimeout); callTimeout = null; }
        if (callTimerInterval) clearInterval(callTimerInterval);

        const duration = callDuration;
        const targetId = state.currentPartnerId || (incomingCallData ? incomingCallData.senderId : null);
        const thisCallId = currentCallId || window.activeCallSessionId || (incomingCallData ? incomingCallData.callId : null);
        const initiator = callInitiatorId || (incomingCallData ? incomingCallData.senderId : (state.currentUser ? state.currentUser.userID : null));
        const cType = currentCallType || (incomingCallData ? incomingCallData.callType : 'VIDEO');

        if (targetId && thisCallId) {
            const callStatus = duration > 0 ? 'COMPLETED' : 'MISSED';
            recordCallRecord(targetId, cType, callStatus, duration, thisCallId, initiator);
        }

        if (localStream) { localStream.getTracks().forEach(t => t.stop()); localStream = null; }
        if (remoteStream) { remoteStream.getTracks().forEach(t => t.stop()); remoteStream = null; }

        const localVideo = document.getElementById('localVideo');
        if (localVideo) localVideo.srcObject = null;
        const remoteVideo = document.getElementById('remoteVideo');
        if (remoteVideo) remoteVideo.srcObject = null;

        if (currentCall) { try { currentCall.close(); } catch (e) {} currentCall = null; }

        if (targetId && state.stompClient && state.stompClient.connected) {
            state.stompClient.send('/app/call', {}, JSON.stringify({
                type: 'CALL_END',
                callId: thisCallId,
                duration: duration,
                receiverId: targetId,
                senderId: state.currentUser ? state.currentUser.userID : null
            }));
        }

        if (incomingCallData && incomingCallData.ringtone) {
            incomingCallData.ringtone.pause();
            incomingCallData.ringtone.currentTime = 0;
        }
        incomingCallData = null;
        currentCallId = null;
        window.activeCallSessionId = null;
        callDuration = 0;

        $('#videoCallModal').hide();
        $('#incomingCallModal').hide();

        showToast('Đã kết thúc cuộc gọi', 'info');
    };

    function setupCallHandlers(call) {
        call.on('stream', (stream) => {
            if (callTimeout) { clearTimeout(callTimeout); callTimeout = null; }
            remoteStream = stream;
            document.getElementById('remoteVideo').srcObject = remoteStream;
            $('.remote-info-overlay').fadeOut();
            startCallTimer();
        });

        call.on('close', () => { window.endCall(); });

        call.on('error', (err) => {
            console.error('Call error:', err);
            window.endCall();
            showToast('Cuộc gọi bị lỗi', 'error');
        });
    }

    // --- 3. CALL UI HELPERS ---
    function showIncomingCallModal(data) {
        $('#incomingAvatar').attr('src', data.senderAvatar || '/images/placeholder-user.jpg');
        $('#incomingName').text(data.senderName);
        $('#incomingCallType').text(data.callType === 'VIDEO' ? 'Cuộc gọi video' : 'Cuộc gọi thoại');
        $('#incomingCallModal').show();

        const ringtone = new Audio('/sounds/ringtone.mp3');
        ringtone.loop = true;
        ringtone.play().catch(() => {});
        incomingCallData.ringtone = ringtone;
    }

    function showCallModal(isVideo) {
        const state = getState();
        const partnerAvatar = $('#headerAvatar').attr('src');
        const partnerName = state.currentPartnerName || '';

        $('#callPartnerName').text(partnerName);
        $('#callPartnerAvatar').html(`<img src="${partnerAvatar}" alt="${partnerName}">`);
        $('#callBackground').css('background-image', `url(${partnerAvatar})`);
        $('#videoCallModal').show();
        $('#callStatusText').text(isVideo ? 'Đang gọi...' : 'Đang gọi thoại...');
        $('#callDuration').text('00:00');
    }

    function closeCallModal() {
        $('#videoCallModal').hide();
        $('#incomingCallModal').hide();
        if (callTimeout) { clearTimeout(callTimeout); callTimeout = null; }
        if (callTimerInterval) { clearInterval(callTimerInterval); callTimerInterval = null; }
        if (localStream) { localStream.getTracks().forEach(t => t.stop()); localStream = null; }
        if (remoteStream) { remoteStream.getTracks().forEach(t => t.stop()); remoteStream = null; }

        const localVideo = document.getElementById('localVideo');
        if (localVideo) localVideo.srcObject = null;
        const remoteVideo = document.getElementById('remoteVideo');
        if (remoteVideo) remoteVideo.srcObject = null;

        if (currentCall) { try { currentCall.close(); } catch (e) {} currentCall = null; }
        if (incomingCallData && incomingCallData.ringtone) {
            try { incomingCallData.ringtone.pause(); incomingCallData.ringtone.currentTime = 0; } catch (e) {}
        }
        incomingCallData = null;
        stopCallTimer();
    }

    function startCallTimer() {
        let seconds = 0;
        if (callTimerInterval) clearInterval(callTimerInterval);

        callTimerInterval = setInterval(() => {
            seconds++;
            callDuration = seconds;
            const minutes = Math.floor(seconds / 60);
            const secs = seconds % 60;
            $('#callDuration').text(`${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`);

            if (seconds < 5) {
                $('#callStatusText').text('Đang kết nối...');
            } else {
                $('#callStatusText').text('Đang trong cuộc gọi');
            }
        }, 1000);
    }

    function stopCallTimer() {
        clearInterval(callTimerInterval);
        $('#callDuration').text("00:00");
    }

    // Toggle Mic/Cam
    window.toggleCallMic = function() {
        if (localStream) {
            const audioTrack = localStream.getAudioTracks()[0];
            if (audioTrack) {
                audioTrack.enabled = !audioTrack.enabled;
                const btn = $('#btnToggleMic');
                btn.toggleClass('off');
                btn.find('i').toggleClass('fa-microphone fa-microphone-slash');
                btn.find('.control-label').text(audioTrack.enabled ? 'Tắt mic' : 'Bật mic');
            }
        }
    };

    window.toggleCallCam = function() {
        if (localStream) {
            const videoTrack = localStream.getVideoTracks()[0];
            if (videoTrack) {
                videoTrack.enabled = !videoTrack.enabled;
                const btn = $('#btnToggleCam');
                btn.toggleClass('off');
                btn.find('i').toggleClass('fa-video fa-video-slash');
                btn.find('.control-label').text(videoTrack.enabled ? 'Tắt camera' : 'Bật camera');
                $('#localVideo').toggle(videoTrack.enabled);
            }
        }
    };

    function switchCamera() {
        if (!localStream || availableCameras.length < 2) return;

        currentCameraIndex = (currentCameraIndex + 1) % availableCameras.length;
        const newCamera = availableCameras[currentCameraIndex];

        navigator.mediaDevices.getUserMedia({
            video: { deviceId: { exact: newCamera.deviceId } },
            audio: true
        }).then(newStream => {
            const newVideoTrack = newStream.getVideoTracks()[0];
            const oldVideoTrack = localStream.getVideoTracks()[0];

            oldVideoTrack.stop();
            localStream.removeTrack(oldVideoTrack);
            localStream.addTrack(newVideoTrack);

            document.getElementById('localVideo').srcObject = localStream;

            if (currentCall && currentCall.peerConnection) {
                const sender = currentCall.peerConnection.getSenders().find(s => s.track.kind === 'video');
                if (sender) sender.replaceTrack(newVideoTrack);
            }
        }).catch(err => {
            console.error('Error switching camera:', err);
        });
    }

    function recordCallRecord(partnerId, callType, status, duration, callId, initiatorId) {
        if (!partnerId) return;
        const state = getState();
        const cid = callId || window.activeCallSessionId || (incomingCallData ? incomingCallData.callId : null);
        if (!cid) return;
        if (recordedCallIds.has(cid)) {
            return;
        }
        recordedCallIds.add(cid);

        if ($(`.msg-row[data-call-id="${cid}"]`).length) {
            return;
        }

        const payload = {
            partnerId: partnerId,
            initiatorId: initiatorId || (state.currentUser ? state.currentUser.userID : null),
            callType: callType || 'VIDEO',
            status: status || 'COMPLETED',
            duration: duration || 0,
            callId: cid
        };

        $.ajax({
            url: '/api/v1/messenger/call-record',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            success: function(messageDto) {
                if (messageDto && messageDto.id && window.appendMessageToUI) {
                    if (!$(`#msg-${messageDto.id}`).length && !$(`.msg-row[data-call-id="${cid}"]`).length) {
                        window.appendMessageToUI(messageDto);
                    }
                }
            },
            error: function(err) {
                console.warn('Call record save error:', err);
            }
        });
    }

    function saveCallLog() {
        const state = getState();
        if (!state.currentPartnerId || callDuration < 3) return;

        const callLog = {
            partnerId: state.currentPartnerId,
            partnerName: state.currentPartnerName,
            type: incomingCallData ? 'INCOMING' : 'OUTGOING',
            duration: callDuration,
            timestamp: new Date().toISOString(),
            callType: incomingCallData ? incomingCallData.callType : 'VIDEO'
        };

        let callHistory = JSON.parse(localStorage.getItem('callHistory') || '[]');
        callHistory.unshift(callLog);
        if (callHistory.length > 50) callHistory = callHistory.slice(0, 50);
        localStorage.setItem('callHistory', JSON.stringify(callHistory));

        $.post('/api/v1/messenger/call-log', callLog)
            .fail(err => console.error('Error saving call log:', err));
    }

    // --- 4. CALL HISTORY ---
    window.openCallHistory = function() {
        const state = getState();
        $('.call-history-modal-overlay, .call-history-modal').remove();
        const modal = $('<div class="modal-overlay call-history-modal-overlay"></div>');
        const content = $(`
            <div class="call-history-modal">
                <div class="call-history-header">
                    <h3><i class="fas fa-history"></i> Lịch sử cuộc gọi</h3>
                    <button class="close-call-history" onclick="closeCallHistory()">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
                <div class="call-history-tabs">
                    <button class="tab-btn active" data-type="ALL">Tất cả</button>
                    <button class="tab-btn" data-type="MISSED">Đã nhỡ</button>
                    <button class="tab-btn" data-type="VIDEO">Video</button>
                    <button class="tab-btn" data-type="AUDIO">Thoại</button>
                </div>
                <div class="call-history-list" id="callHistoryList">
                    <div class="loading-calls">
                        <i class="fas fa-spinner fa-spin"></i>
                        <p>Đang tải lịch sử...</p>
                    </div>
                </div>
            </div>
        `);

        modal.append(content);
        modal.on('click', function(e) {
            if ($(e.target).is(modal)) window.closeCallHistory();
        });
        $('body').append(modal);
        window.loadCallHistory('ALL');

        $('.tab-btn').click(function() {
            $('.tab-btn').removeClass('active');
            $(this).addClass('active');
            window.loadCallHistory($(this).data('type'));
        });
    };

    window.closeCallHistory = function() {
        $('.call-history-modal-overlay, .call-history-modal').remove();
    };

    window.loadCallHistory = function(type) {
        const state = getState();
        const container = $('#callHistoryList');
        container.html('<div class="loading-calls"><i class="fas fa-spinner fa-spin"></i><p>Đang tải...</p></div>');

        $.get('/api/v1/messenger/call-history', {
            partnerId: state.currentPartnerId || undefined,
            days: 30
        })
        .done(function(logs) { window.displayCallHistory(logs, type); })
        .fail(function() { container.html('<div class="no-calls">Không thể tải lịch sử cuộc gọi</div>'); });
    };

    window.displayCallHistory = function(logs, filterType) {
        const container = $('#callHistoryList');
        container.empty();

        let filteredLogs = logs;
        if (filterType !== 'ALL') {
            if (filterType === 'MISSED') filteredLogs = logs.filter(log => log.callStatus === 'MISSED');
            else if (filterType === 'VIDEO') filteredLogs = logs.filter(log => log.video);
            else if (filterType === 'AUDIO') filteredLogs = logs.filter(log => !log.video);
        }

        if (filteredLogs.length === 0) {
            container.html('<div class="no-calls">Không có cuộc gọi nào</div>');
            return;
        }

        filteredLogs.forEach(log => {
            const time = new Date(log.timestamp).toLocaleString('vi-VN');
            const duration = window.formatDuration(log.duration);
            const isOutgoing = log.callType === 'OUTGOING';
            const isMissed = log.callStatus === 'MISSED';
            const callIcon = log.video ? 'fa-video' : 'fa-phone';

            container.append(`
                <div class="call-history-item ${isMissed ? 'missed' : ''}">
                    <div class="call-icon">
                        <i class="fas ${callIcon} ${isOutgoing ? 'outgoing' : 'incoming'}"></i>
                    </div>
                    <div class="call-details">
                        <div class="call-partner">${log.partnerName}</div>
                        <div class="call-meta">
                            <span class="call-time">${time}</span>
                            <span class="call-duration">${duration}</span>
                        </div>
                    </div>
                    <div class="call-actions">
                        <button class="btn-call-action" onclick="redialCall(${log.partnerId}, ${log.video})">
                            <i class="fas fa-redo"></i>
                        </button>
                    </div>
                </div>
            `);
        });
    };

    window.formatDuration = function(seconds) {
        if (!seconds) return '--:--';
        const mins = Math.floor(seconds / 60);
        const secs = seconds % 60;
        return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    };

    window.redialCall = function(partnerId, isVideo) {
        if (isVideo) window.startVideoCall();
        else window.startVoiceCall();
        window.closeCallHistory();
    };

    // --- 5. INCOMING CALL EVENT HANDLER (delegated from core WebSocket) ---
    function handleIncomingCall(callData) {
        console.log('📞 Call event on /queue/call:', callData);
        if (!callData || !callData.type) return;

        if (callData.type === 'CALL_REQ') {
            currentCallId = callData.callId || ('call_' + callData.senderId + '_' + (getState().currentUser ? getState().currentUser.userID : '0') + '_' + Date.now());
            window.activeCallSessionId = currentCallId;
            currentCallType = callData.callType || 'VIDEO';
            callInitiatorId = callData.senderId;
            incomingCallData = {
                callId: currentCallId,
                peerId: callData.peerId,
                senderId: callData.senderId,
                senderName: callData.senderName || 'Người dùng',
                senderAvatar: callData.senderAvatar,
                callType: currentCallType
            };
            showIncomingCallModal(incomingCallData);
        } else if (callData.type === 'CALL_ACCEPT') {
            if (callTimeout) { clearTimeout(callTimeout); callTimeout = null; }
            $('#callStatusText').text('Đã chấp nhận, đang kết nối thiết bị...');
        } else if (callData.type === 'CALL_DENY' || callData.type === 'CALL_REJECT') {
            closeCallModal();
            showToast('Người nhận đã từ chối cuộc gọi', 'info');
        } else if (callData.type === 'CALL_END') {
            closeCallModal();
            showToast('Cuộc gọi đã kết thúc', 'info');
        }
    }

    // Handle CALL_REQ from socket messages (delegated by core handleSocketMessage)
    function handleCallSocketMessage(msg) {
        if (msg.type === 'CALL_REQ') {
            currentCallId = msg.callId || currentCallId;
            if (currentCallId) window.activeCallSessionId = currentCallId;
            incomingCallData = {
                callId: msg.callId || currentCallId,
                peerId: msg.content || msg.peerId,
                senderId: msg.senderId,
                senderName: msg.senderName || 'Người dùng',
                senderAvatar: msg.senderAvatar,
                callType: msg.callType || 'VIDEO'
            };
            showIncomingCallModal(incomingCallData);
            return true;
        }
        if (msg.type === 'CALL_DENY' || msg.type === 'CALL_REJECT') {
            closeCallModal();
            if (msg.id) return false;
            showToast('Người nhận đã từ chối cuộc gọi', 'info');
            return true;
        }
        if (msg.type === 'CALL_END') {
            closeCallModal();
            if (msg.id) {
                // Persisted conversation message DTO! Pass through to handleSocketMessage to render in chat
                return false;
            }
            showToast('Cuộc gọi đã kết thúc', 'info');
            return true;
        }
        return false; // Not a call message
    }

    // --- PUBLIC API ---
    window.MessengerCalls = {
        init: initPeerJS,
        handleIncomingCall: handleIncomingCall,
        handleCallSocketMessage: handleCallSocketMessage,
        closeCallModal: closeCallModal
    };
    window.closeCallModal = closeCallModal;
})();
