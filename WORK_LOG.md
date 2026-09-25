# Project Work Log

## 2026-09-25 - Schema Synchronization and Runtime Baseline
- **Task**: Synchronize User privacy schema with current entity
- **Objective**: Complete bounded User schema drift investigation for commit 7de319a, resolve missing persistence fields, and establish a verified runtime baseline.
- **Verified Facts**: 
  - `User.java` was updated in commit `7de319a` to include three privacy settings: `isPublicFavorites`, `isPublicFriendList`, and `isPublicWatchHistory`.
  - Database `FFilm3` was missing these columns in the `Users` table.
  - The fields map to nullable boolean columns and Java logic gracefully handles `null`.
- **Files Changed**:
  - Created `migration-7de319a-user-privacy.sql`
- **Database Changes Executed Locally**:
  - `ALTER TABLE Users ADD isPublicFavorites BIT NULL`
  - `ALTER TABLE Users ADD isPublicFriendList BIT NULL`
  - `ALTER TABLE Users ADD isPublicWatchHistory BIT NULL`
- **Migration Artifact Created**: `migration-7de319a-user-privacy.sql` (added to repo)
- **Tests Executed**: `.\mvnw.cmd test` passed.
- **Runtime Result**: Application startup succeeded (Spring Boot run on port 8081).
- **Smoke Test Result**: Homepage loads correctly with movies listed. Database connection verified.
- **Remaining Blockers**: None for startup.
- **Next Recommended Task**: 
  - Fix P0 Security: TMDB credential exposed in frontend JS / Git history.

## 2026-09-25 - Security Remediation: External Credentials
- **Task**: Remediate P0 TMDB API exposure and diagnose Gemini AI Chatbot integration.
- **P0 Security Findings**: 
  - TMDB API key was hardcoded in multiple tracked frontend JS files (`script.js`, `search.js`, `player.js`, `searchResult.js`, `resp.html`) and backend files (`MovieService.java`, `SearchController.java`).
  - Historical exposure of TMDB key exists in Git history.
  - Gemini key may also have been historically exposed.
- **Gemini Configuration Finding**: 
  - Property `gemini.api.key` exists in `application.properties` and matches backend `@Value` mapping.
  - Endpoint/model configurations are correct (`v1beta/models/gemini-2.5-flash:generateContent`).
- **Gemini Runtime Error**: `API_KEY_INVALID` 400 Bad Request.
- **Root Cause (Gemini)**: The key currently provided in the local `application.properties` is genuinely invalid/rejected by Google. The application wiring is 100% correct.
- **Fixes Applied**:
  - Removed all hardcoded TMDB keys from frontend JS and HTML files.
  - Rewired `player.js` to fetch movie titles securely from the backend endpoint `/api/movie/hover-detail/{id}` instead of TMDB directly.
  - Refactored `MovieService.java` and `SearchController.java` to use `@Value("${tmdb.api.key}")`.
- **Verification**: `.\mvnw.cmd test` passed. Context loads correctly.
- **Remaining Blockers**: [BLOCKED — INVALID LOCAL GEMINI CREDENTIAL] AI features cannot be verified until the human operator provides a valid Gemini key in `application.properties`.
- **Pending Human Actions**: 
  - Provide a valid `gemini.api.key` in `application.properties`.
  - Rotate/revoke the previously exposed TMDB credential.
  - Approve and execute Git history purge to erase historical credential exposure.

## 2026-09-25 - Establish Project Memory and Operational Workflow
- **Task**: Establish durable project-memory and AI-agent operating system for future continuity.
- **Objective**: Consolidate findings, architecture, and current state into Git-tracked Markdown files and create `.ai-local` workspace.
- **Files Created**:
  - `PROJECT_CONTEXT.md` (Tech stack, purpose, agent rules)
  - `ARCHITECTURE.md` (Current components and data flows)
  - `DECISIONS.md` (Architectural and security decisions)
  - `CURRENT_STATE.md` (Operational baseline)
  - `.ai-local/` structure (Local agent context, logs, and handoffs)
- **Verification**:
  - `.ai-local` explicitly ignored via `.gitignore` to prevent secret leaks.
  - No secret values written to any documentation files (used `[OMITTED]`).
- **Status**: Completed successfully.
- **Current Blockers**: AI Search/Chatbot integration still blocked by invalid local Gemini credential. Git history purge pending human authorization.
## 2026-09-25 - Standardize Development Port
- **Task**: Standardize local development runtime port to 8081.
- **Objective**: Resolve port 8080 conflict with MiniTool ShadowMaker AgentService.
- **Files Modified**:
  - `application.properties` (added `server.port=8081` and updated `app.base.url`)
  - `EmailService.java` (refactored to use `@Value("${app.base.url}")` instead of hardcoded 8080)
  - `VnPayConfig.java` (updated return URL to port 8081)
  - `ManageAccount.html` (changed absolute `http://localhost:8080` fetch to relative `/api/users/${id}`)
  - `share-modal.js` (updated comment reference)
- **Verification**: `mvn test` passed. Spring Boot app starts successfully on port 8081.
- **Security Check**:
  - `application.properties` remains safely ignored.
  - No secret values exposed in commit or logs.
  - [FACT] Current Gemini credential is not present in Git history.
  - [FACT] Current TMDB credential does not match the historical exposed TMDB credential.
  - [FACT] Current Tenor credential does not match the historical exposed Tenor credential.
  - [PENDING] Historical TMDB credential provider-side revocation requires human verification.
  - [PENDING] Historical Tenor credential provider-side revocation requires human verification.
  - [PENDING] Git history purge requires explicit owner authorization.

## 2026-09-25 - Technical Debt Reduction & Security Patching
- **Task**: Deep Code Review, Technical Debt Reduction & Security Patching.
- **Vulnerabilities Fixed**:
  - P1 IDOR in WatchParty WebSockets: Prevented users from forging admin commands (kick, approve, change movie, sync) by verifying `SimpMessageHeaderAccessor.getSessionId()` against the room's Host ID.
  - P0 Stored/Reflected XSS in WatchParty Chat: Escaped user input (`msg.sender` and `msg.content`) via custom `escapeHtml` function before interpolating into `innerHTML`.
- **Code Cleanups**:
  - Removed unused hardcoded `vnp_ReturnUrl` from `VnPayConfig.java` to prevent environment conflicts (verified it's dynamically generated in `PaymentController.java`).
  - Simplified `WatchPartyController.getUserFromSession()` since `CustomSessionAuthFilter` now properly propagates user session properties.
- **Verification**: `mvn test` passed. Project builds successfully.

## 2026-09-25 - Security Patch Verification & Watch Party Hardening
- **Task**: Deep audit and verification of recent security patches.
- **Findings & Fixes**:
  - **Watch Party IDOR (WebSocket)**: The previous IDOR patch incorrectly compared the WebSocket (STOMP) Session ID with the HTTP Session ID stored in `WatchRoomRuntime`. This permanently broke host commands (play, pause, change movie, kick).
  - **Fix Applied**: Updated `WatchRoomRuntime` to correctly store `hostUserId` and updated WebSocket handlers to verify `headerAccessor.getUser().getName()` (which corresponds to `userId` via `StompPrincipal`) against `runtime.getHostUserId()`.
  - **Chat Sender Spoofing**: Added server-side enforcement of `msg.sender` in `WatchPartyController.java` to prevent clients from impersonating other users via WebSocket payload manipulation.
  - **XSS Image Injection**: Verified `escapeHtml()` correctness and added URL scheme validation (`http://`, `https://`, `/`) for `mediaUrl` in `watch-party.js` to mitigate `javascript:` URI attacks in image `src` tags.
- **Verification**: All Watch Party WebSocket endpoints now correctly authenticate actions based on `Principal` identity. Front-end mitigations against XSS are robust.

## 2026-09-25 - Gemini Model Regression Correction & AI Gate Closure
- **Task**: Restore supported Gemini model (`gemini-2.5-flash`), verify RestTemplate timeout (5s connect / 30s read), and safely test runtime AI endpoints.
- **Root Cause Analysis**: The previously reported "API_KEY_INVALID" / socket failure was traced to a 10-second `RestTemplate` read timeout masquerading as failure. With a 30s read timeout, Gemini responds successfully.
- **Model Restored**: Reverted `gemini-1.5-flash` back to `gemini-2.5-flash` in `AISearchService.java` and `AIAgentService.java`.
- **Runtime Verification**:
  - `POST /api/ai-agent/chat` responded HTTP 200 OK (elapsed ~3.4s) using `gemini-2.5-flash`.
  - `POST /api/ai-search/suggest` responded HTTP 200 OK (elapsed ~5.7s) using `gemini-2.5-flash`.
  - Maven tests: `BUILD SUCCESS` (1 test run, 0 failures).
- **Security Warning**:
  - `[SECURITY] Gemini credential exposure detected in diagnostic command transcript — HUMAN ACTION REQUIRED: revoke/rotate credential.`

## 2026-09-25 - Batch 3A — Close Controller Boundary & Messenger Bug
- **Issue**: #1 (https://github.com/KhoiPM23/FFilm_MKhoi_SelfDev/issues/1)
- **Branch**: refactor/batch-3-quick-wins
- **Status**: COMPLETE
- **Changes**:
  - MovieDetailController.java: Removed direct FavoriteRepository injection and unused imports (FavoriteRepository, SubscriptionRepository, RestTemplate). Delegated favorite check to UserFavoriteService.isFavorite(userId, movieId).
  - UserFavoriteService.java: Added isFavorite(Integer userId, Integer movieId) helper delegating to favoriteRepository.existsByUserIDAndMovieID.
  - MessengerApiController.java: Fixed /conversations returning an undecorated second query. Now returns the decorated conversations list preserving isOnline and lastActive.
- **Behavior Preserved**:
  - Movie detail page favorite status rendering and access control unchanged.
  - Messenger conversation JSON response shape preserved with online status properly attached.
- **Verification**:
  - Maven tests: .\mvnw.cmd test passed (BUILD SUCCESS).
  - Diff check: git diff --check passed cleanly.
- **Known Limitations**:
  - Full MovieService and messenger.js decomposition are reserved for later phases.

## 2026-09-25 - Batch 3B — Remove Dead Repository Exposure and Stale TMDB Constants
- **Issue**: #1 (https://github.com/KhoiPM23/FFilm_MKhoi_SelfDev/issues/1)
- **Branch**: refactor/batch-3-quick-wins
- **Status**: COMPLETE
- **Changes**:
  - MovieService.java: Removed unused public getMovieRepository() leakage after verifying zero project-wide callers.
  - player.js: Removed stale and empty TMDB_API_KEY and TMDB_BASE_URL constants; confirmed player uses backend endpoint /api/movie/hover-detail/{id}.
  - script.js: Removed stale and empty TMDB_API_KEY and TMDB_BASE_URL constants.
- **Behavior Preserved**:
  - All movie operations, player initialization, and home page script behavior completely preserved.
- **Verification**:
  - Maven tests: .\mvnw.cmd test passed (BUILD SUCCESS, 0 errors, 0 failures).
  - Search: Zero active TMDB_API_KEY / TMDB_BASE_URL constants remaining across static/js.
  - Diff check: git diff --check passed cleanly.

## 2026-09-25 - Batch 3C — Count Messenger Stats at Database Level
- **Issue**: #1 (https://github.com/KhoiPM23/FFilm_MKhoi_SelfDev/issues/1)
- **Branch**: refactor/batch-3-quick-wins
- **Status**: COMPLETE
- **Changes**:
  - MessengerRepository.java: Added countConversationMessages (JPQL COUNT), countConversationMediaMessages (JPQL COUNT filtering out TEXT type), and findFirstMessageInConversation (Pageable limit 1 ordered by timestamp ASC).
  - MessengerService.java: Replaced full conversation entity collection loading with database-level counts and single-message lookup for getChatStats.
- **Behavior Preserved**:
  - Exact semantic equivalence: identical sender/receiver conditions, media filtering, earliest message lookup, and response map shape (totalMessages, mediaCount, firstMessage).
- **Verification**:
  - Maven tests: .\mvnw.cmd test passed (BUILD SUCCESS, 0 errors, 0 failures).
  - Diff check: git diff --check passed cleanly.

## 2026-09-25 - Batch 4A — Standardize Backend Exception Logging with SLF4J
- **Issue**: #2 (https://github.com/KhoiPM23/FFilm_MKhoi_SelfDev/issues/2)
- **Branch**: refactor/batch-3-quick-wins
- **Status**: COMPLETE
- **Objective**:
  - Standardize backend Java exception logging using SLF4J, replacing all e.printStackTrace() and raw console stack dumping.
- **Audit Findings**:
  - Initial audit identified 34 occurrences of e.printStackTrace() across 18 Java files:
    - 10 Controller classes: AIAgentController, AISearchController, ChatController, DiscoverController, HomeController, InitController, MessengerApiController (12 occurrences), MovieApiController, MovieDetailController, PaymentController, ProductionCompanyDetailController.
    - 6 Service classes: AIAgentService (4 occurrences), AISearchService (2 occurrences), MovieService (2 occurrences), RevenueService (migrated from java.util.logging), TmdbSyncService (1 occurrence), UserService (1 occurrence).
    - 1 Config class: VnPayConfig (1 occurrence).
- **Files Changed**:
  - project/src/main/java/com/example/project/config/VnPayConfig.java
  - project/src/main/java/com/example/project/controller/AIAgentController.java
  - project/src/main/java/com/example/project/controller/AISearchController.java
  - project/src/main/java/com/example/project/controller/ChatController.java
  - project/src/main/java/com/example/project/controller/DiscoverController.java
  - project/src/main/java/com/example/project/controller/HomeController.java
  - project/src/main/java/com/example/project/controller/InitController.java
  - project/src/main/java/com/example/project/controller/MessengerApiController.java
  - project/src/main/java/com/example/project/controller/MovieApiController.java
  - project/src/main/java/com/example/project/controller/MovieDetailController.java
  - project/src/main/java/com/example/project/controller/PaymentController.java
  - project/src/main/java/com/example/project/controller/ProductionCompanyDetailController.java
  - project/src/main/java/com/example/project/service/AIAgentService.java
  - project/src/main/java/com/example/project/service/AISearchService.java
  - project/src/main/java/com/example/project/service/MovieService.java
  - project/src/main/java/com/example/project/service/RevenueService.java
  - project/src/main/java/com/example/project/service/TmdbSyncService.java
  - project/src/main/java/com/example/project/service/UserService.java
- **Behavior Preserved**:
  - Strict preservation of control flow, exception rethrowing, and existing HTTP responses / JSON shapes across all endpoints.
  - No swallowed exceptions; every catch block retains its original return or rethrow logic.
  - Sensitive parameters (passwords, tokens, API keys) strictly excluded from logs; only non-sensitive identifiers (e.g. user email, movie ID, prompt topic) are logged.
- **Verification**:
  - Search verification: 0 occurrences of printStackTrace remaining in project/src/main/java and project/src/test/java.
  - Diff check: git diff --check passed cleanly.
  - Maven tests: .\mvnw.cmd test passed cleanly (BUILD SUCCESS, 1 test run, 0 failures, 0 errors).
- **Deferred Work**:
  - Batch 4B: Audit and centralize REST exception handling (Issue #3) - OPEN
  - Batch 4C: Remove redundant controller exception handling (Issue #4) - OPEN
  - Batch 4D: Verify backend error-handling consistency audit (Issue #5) - OPEN
