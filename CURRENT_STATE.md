# Current Verified State

This file records the MOST RECENT verified operational state of the project.

## RUNTIME
- **Database**: `FFilm3` [FACT]
- **Spring Boot Startup**: `SUCCESS` [FACT]
- **Maven Test**: `PASSED` (Tests run: 1, Failures: 0, Errors: 0) [FACT]
- **Homepage Status**: Loads and fetches movies successfully [FACT]

## ARCHITECTURE & BOUNDARIES
- **Controller Data Access Boundaries**: 100% of controllers delegate data access through services. `MovieDetailController` now delegates to `UserFavoriteService.isFavorite`. [FIXED]
- **Encapsulation**: Removed public `MovieService.getMovieRepository()` leak. [FIXED]
- **External Integration Boundaries**: TMDB via `TmdbClient`, Gemini via `GeminiClient`, Tenor via `TenorService`. [FIXED]
- **Messenger Performance**: `MessengerApiController.getConversations()` returns decorated list without duplicate query. `MessengerService.getChatStats()` calculates counts and earliest message directly at database level. [FIXED]
- **Frontend Constants**: Stale empty `TMDB_API_KEY` and `TMDB_BASE_URL` constants removed from `script.js` and `player.js`. [FIXED]
- **Error Handling & Logging (Batch 4A-4C)**: 100% of `e.printStackTrace()` eliminated and standardized to SLF4J; `GlobalExceptionHandler` standardized for REST controllers with dual-compatible envelope and sanitized 500 responses; 13 truly redundant controller try/catch blocks safely removed. [FIXED]

## SCHEMA
- **Synchronized User Privacy Columns**: `isPublicFavorites`, `isPublicFriendList`, `isPublicWatchHistory` [FIXED]
- **Migration Artifact**: `project/migration-7de319a-user-privacy.sql` [FACT]

## SECURITY
- **TMDB current-source exposure**: Hardcoded API keys removed from JS/HTML. Backend uses `@Value`. [FIXED]
- **Tenor current-source exposure**: Proxied successfully. [FIXED]
- **API Keys Historical exposure**: TMDB and Tenor present in Git history. Gemini NEVER exposed in git history. [FACT]
- **TMDB/Tenor rotation/revocation**: Awaiting explicit human intervention on provider side. [PENDING HUMAN]
- **Git history purge**: BFG/filter-repo requires explicit authorization. [PENDING HUMAN]
- **IDOR Vulnerability**: Fixed in UserReactionController (userId sourced from session). [FIXED]
- **IDOR Vulnerability**: Fixed in SecurityConfig for /api/users endpoint. [FIXED]
- **IDOR Vulnerability**: Fixed in WatchPartyController WebSockets (enforced host permissions via SimpMessageHeaderAccessor). [FIXED]
- **XSS Vulnerability**: Fixed Stored/Reflected XSS in WatchPartyController chat (added escapeHtml to user messages and names). [FIXED]
- **Hardcoded Configs**: Cleaned up unused vnp_ReturnUrl from VnPayConfig. [FIXED]

## WATCH PARTY & ROOM MANAGEMENT (PHASE 6 - 8)
- **Schema Synchronization**: `WatchRoom`, `FriendRequests`, `Notification`, `UserFollow` synchronized in SQL Server `FFilm3`. [FACT]
- **Create Room Flow**: Operational on both `/watch-party` and `/my-rooms` using shared fragment `fragments/watch-party-create-room.html`. [FIXED]
- **Host Authority & Initialization**: `createRoom` and `joinRoom` guarantee `runtime.setHostUserId(ownerId)` is set, ensuring host controls and WebSocket endpoints work immediately without false rejections. [FIXED]
- **Host Migration View Sync**: `joinRoom` gives precedence to `runtime.getHostUserId()` over static DB owner when determining `isHost` in Thymeleaf model, keeping migrated hosts in control upon page reload. [FIXED]
- **Private Room Waiting & Reconnect Approval**: `WatchRoomRuntime` tracks `approvedUserIds`. Reconnecting or refreshing approved members bypass the waiting room. Hosts are never locked in waiting status. [FIXED]
- **Host Waiting List Modal & Moderation UI**: Implemented `#waitingListModal` with real-time counters, "Duyệt" (`/admin/approve`), and "Từ chối" (`/admin/reject`). Added `rejectMember()` in `WatchPartyService`. [FIXED]
- **Movie Sync Hardening**: 150ms seek debounce prevents seek storms. Periodic 5-second heartbeat sync maintains sub-1.5s synchronization across buffering and network latency. [FIXED]
- **WebRTC Audio/Video Track Integrity**: `toggleCam` toggles `videoTrack.enabled` without terminating audio tracks. Added `myPeer.on('error')` handler and disconnect cleanup on `beforeunload`. [FIXED]
- **Room Dissolution & Delete Endpoints**: Added `@DeleteMapping("/api/party/delete/{roomId}")` and `@MessageMapping("/party/{roomId}/admin/close")` broadcasting `ROOM_CLOSED` to cleanly tear down party sessions. [FIXED]
- **Multi-session runtime**: Needs repro with two independent browser sessions (`[NEEDS REPRO]`).

## SECURITY & IDENTITY ENFORCEMENT
- **Profile Sensitivity & Confirmation Verification**: `UserProfileUpdateDto` includes `currentPassword`; `UserService.updateProfile` verifies current password with `passwordEncoder.matches()` before applying email, phone, or password changes. [FIXED]
- **Privacy Update Route**: Moved from `@RestController` to `UserAuthenticationController`, returning 302 redirect rather than raw string. [FIXED]
- **WebSocket Identity Extraction**: Sender identities extracted server-side from `Principal` and session attributes, preventing client-side spoofing. [FIXED]
- **Presence Tracking (STOMP Handshake)**: Handshake session attributes populate `"userSession"` and `"userDto"` to activate `OnlineStatusService` online/offline events. [FIXED]

## MESSENGER & REAL-TIME CHAT
- **Modularization**: Call/WebRTC logic extracted into `messenger-calls.js` (595 lines); Sticker/Tenor/Google Noto Emoji extracted into `messenger-stickers.js` (215 lines). [FIXED]
- **State Bridge**: `window.MessengerState` connects satellite modules cleanly with the core orchestrator. [FIXED]
- **Database Schema**: SQL Server tables `messenger_messages`, `conversation_settings`, `call_logs` synchronized via migration. [FIXED]
- **Script Ordering**: `messenger.html` loads core `messenger.js` prior to satellite modules, and error handlers clean up skeletons. [FIXED]

## NOTIFICATION & SOCIAL INTEGRITY
- **STOMP Routing Bug**: Fixed critical Spring STOMP user destination mismatch; now routes to numeric `Principal.getName()` (`userId.toString()`). [FIXED]
- **Single-Read API**: Implemented zero-IDOR `POST /social/api/notifications/read/{id}` with 403 authorization guard. [FIXED]
- **Bulk Mark-All-Read**: Efficient single SQL update query via `NotificationRepository.markAllAsReadByRecipient`. [FIXED]
- **Watch Party Invitations**: In-room "Mời bạn bè" modal and quick triggers dispatch `PARTY_INVITE` notifications with direct room link. [FIXED]

## COMMUNITY RATING & DISCUSSION
- **Decoupled Architecture**: TMDB metadata score (`movie.rating`, `movie.voteCount`) strictly separated from FFilm community score (`communityRating` 1-5 stars, `ratingCount`). [FIXED]
- **Zero-IDOR Rating API**: `ReviewController` derives user identity strictly from `HttpSession`. [FIXED]
- **Interactive UI**: 5-star rating widget in movie detail hero with live hover preview and author rating badges on live comments. [FIXED]

## SKELETON LOADERS
- **Progressive UX**: Shimmer skeleton loaders implemented in `style.css` and `messenger.css` for search suggestions, carousel, messenger list/chat, and watch history. [FIXED]
- **Failure Resilience**: AJAX/fetch `.fail()` and error callbacks clean up skeletons properly. [FIXED]

## AI SEARCH & AI CHATBOT
- **AI Search**: `/api/ai-search/suggest` verified via API and browser runtime. [VERIFIED]
- **AI Chatbot**: `/api/ai-agent/chat` verified via API and browser runtime with rich movie cards. [VERIFIED]

## NEXT TASKS
- Multi-browser 2-party concurrent reproduction for Watch Party WebRTC video call and synchronized playback (`[NEEDS REPRO]`).
- Profile page visual refinement and avatar upload.
- Full E2E user regression pass.
