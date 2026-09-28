# Current Verified State

This file records the MOST RECENT verified operational state of the project.

## UI/UX & AI Experience Audit Phase
- ✅ **Batch 1 (Carousel + Hero UX):** HUMAN_ACCEPTED_WITH_CARRYOVER. Two issues carried into Batch 2.
- ⏳ **Batch 2 (Hover Card + Preview):** IN_PROGRESS. Two carry-over fixes implemented. Build verified. Browser verification blocked by quota limit at time of writing — HUMAN ACCEPTANCE PENDING.
- 📄 **Audit Report:** Available in [UI_UX_AI_AUDIT_REPORT.md](file:///C:/Users/Admin%20User/.gemini/antigravity-ide/brain/6b292f8a-ef49-4ace-a780-d369fd605ea7/UI_UX_AI_AUDIT_REPORT.md).

## BATCH ROADMAP STATE

```
Batch 1 — Core Carousel + Hero
Status: HUMAN_ACCEPTED_WITH_CARRYOVER

Accepted:
- Main carousel desktop = 6 cards
- Drag/horizontal interaction working
- Arrow UX working (show/hide, disable at ends)
- Hero active-card progress bar (belongs to and moves with active mini-card)
- Hero → Hot Movies overlap (Home + Genre)
- Genre stray progress bar under header removed
- Genre 450-movie grid card width normalized
- Video preview acceleration (hero: 1200ms, hover: 60ms trigger/200ms fade)
- No transient YouTube play/pause flash
- Left/right hover card edge protection working (.edge-left / .edge-right)
- Hover card soft rounded corners (iframe clip-path)

Carry-over into Batch 2:
- Hero progress intermittent runtime behavior → B2-CARRY-01
- Hover card top-edge clipping → B2-CARRY-02

Batch 2 — Hover Card + Preview
Status: COMPLETE / HUMAN_ACCEPTED

Completed early (done in Batch 1, accepted):
- Hover delay: [ALREADY_DONE_EARLY] 120ms debounce
- Video preview delay: [ALREADY_DONE_EARLY] 60ms trigger, 200ms fade-in
- Hero preview delay: [ALREADY_DONE_EARLY] 1200ms opacity reveal
- Edge positioning (left/right): [ALREADY_DONE_EARLY]
- Rounded media: [ALREADY_DONE_EARLY]
- Collapse bug / mouse area: [ALREADY_DONE_EARLY] stopHoverVideo on mouseleave
- Scale: [NO_CHANGE_RECOMMENDED] — no evidence of concrete UX problem
- YouTube transient controls: [ALREADY_DONE_EARLY] clip-path mask prevents flash

B2-CARRY-01 — Hero progress reliability:
- Root cause: setInterval callback was spawning duplicate RAF loops; infinite clones queried incompletely.
- Fix: Single RAF chain initialized on startAutoRotate; querySelectorAll for all active clones.
- Status: HUMAN_ACCEPTED [Manually verified OK]

B2-CARRY-02 — Hover card top-edge clipping:
- Root cause: 0px clearance between -50px top and 50px carousel padding-top during scale(0.8) -> scale(1).
- Fix: .movie-carousel padding-top increased to 65px; .movie-hover-card top to -42px; clip-path: inset removed.
- Status: HUMAN_ACCEPTED [Manually verified OK]

Remaining Batch 2 items: NONE

Batch 3 — Global UI Density + Loading
Status: AUDITED / NO_CHANGE_RECOMMENDED
- Speculative changes reverted: Google Fonts Inter removed (restored Segoe UI), .section-header margin restored to 20px, .section-title font-size restored to 1.8rem.
- Single-pass browser runtime audit:
  * Horizontal overflow on Home & Discover: 0px (scrollWidth === clientWidth).
  * Layout stability: Hero banner (5 mini cards), carousels (6 cards), Discover grid (5 columns) render cleanly with zero clipping, stable transitions, and proper alignment.
  * No P0/P1 layout shifts, broken transitions, or root overflow defects identified.
- Verdict: [NO_CHANGE_RECOMMENDED] — UI is clean, responsive, and stable.

Batch 4 — AI Search + Chatbot UI
Status: NOT_STARTED

Batch 5 — AI Backend Optimization
Status: NOT_STARTED
Note: Only after Batch 4 is reviewed.
```


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
- **PeerJS Server**: Updated from dead `peerjs-server.herokuapp.com` to official `0.peerjs.com`. Added null-safe initialization and guards around all `myPeer.on()` event registration. [FIXED]
- **PeerJS CDN in room.html**: Added missing PeerJS CDN script tag. Without it, `new Peer()` threw `ReferenceError`. [FIXED]
- **Missing /leave STOMP endpoint**: Added `@MessageMapping("/party/{roomId}/leave")` handler. Frontend `leaveRoom()` now properly triggers `handleDisconnect()` — no more ghost members on voluntary leave. [FIXED]
- **beforeunload cleanup**: Enhanced to destroy PeerJS instance and send `/leave` STOMP signal on page close. [FIXED]
- **Multi-session runtime**: Needs repro with two independent browser sessions (`[NEEDS REPRO]`).

## SECURITY & IDENTITY ENFORCEMENT
- **Profile Sensitivity & Confirmation Verification**: `UserProfileUpdateDto` includes `currentPassword`; `UserService.updateProfile` verifies current password with `passwordEncoder.matches()` before applying email, phone, or password changes. [FIXED]
- **Privacy Update Route**: Moved from `@RestController` to `UserAuthenticationController`, returning 302 redirect rather than raw string. [FIXED]
- **WebSocket Identity Extraction**: Sender identities extracted server-side from `Principal` and session attributes, preventing client-side spoofing. [FIXED]
- **Presence Tracking (STOMP Handshake)**: Handshake session attributes populate `"userSession"` and `"userDto"` to activate `OnlineStatusService` online/offline events. [FIXED]

## MESSENGER & REAL-TIME CHAT
- **Modularization**: Call/WebRTC logic extracted into `messenger-calls.js` (595 lines); Sticker/Tenor/Google Noto Emoji extracted into `messenger-stickers.js`. [FIXED]
- **State Bridge**: `window.MessengerState` connects satellite modules cleanly with the core orchestrator. [FIXED]
- **Database Schema**: SQL Server tables `messenger_messages`, `conversation_settings`, `call_logs` synchronized via migration. [FIXED]
- **Script Ordering**: `messenger.html` loads core `messenger.js` prior to satellite modules, and error handlers clean up skeletons. [FIXED]
- **Sticker Suggestion Functions**: `initStickerSuggestions`, `showStickerSuggestions`, `hideStickerSuggestions` restored to `messenger-stickers.js` (lost in extraction refactor). Exposed on `window.*`, guarded in `messenger.js` and `messenger.html`. [FIXED]
- **MessengerApiController compile error**: `getUserID().equals()` on primitive `int` → changed to `==` in unsend and reaction broadcast handlers. [FIXED]

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

## PHASE 13 EXECUTION
92. **Payment & Premium Playback**: Local simulation verified working via `/payment/simulate/{subId}`. Entitlement checks integrated in `MoviePlayerController`. Progress persistence and watch history correctly fetch `startTime`. Real VNPay blocked externally (`code 71`).
93. **Movie Detail Hero**: Layout stabilized.
94. **Rating Hover & Decoupling**: TMDB vs Community separation verified in `ReviewController`. Hover interaction validated.
95. **Unicode Encoding**: Enforced `sendStringParametersAsUnicode=true` in `application.properties` JDBC URL to resolve `thấy` -> `th?y` issue in DB storage.
96. **YouTube Hover Preview**: Verified that a `1200ms` opacity delay is structurally necessary to hide YouTube's native `< ▶ >` iframe controls while using the static `hover-card-image` as a seamless fallback.
97. **Genre Pagination**: Fixed bug in `DiscoverController` where pagination altered the Hero Banner. Banner and top recommendations are now statically fetched from page 0.
98. **Carousel UI**: Prevented double event binding with `dataset.initialized`. Trailer limit correctly enforced with CSS `repeat(3, 1fr)`.
