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

## 2026-09-25 - Batch 4B — Audit REST Exception Handling Strategy (Issue #3)
- **Issue**: #3 (https://github.com/KhoiPM23/FFilm_MKhoi_SelfDev/issues/3)
- **Branch**: refactor/batch-3-quick-wins
- **Status**: AUDIT COMPLETE — IMPLEMENTATION NOT STARTED (AUDIT ONLY)
- **Objective**:
  - Comprehensive audit of backend exception handling before any centralization.
  - Zero code modifications; zero try/catch removals; zero API response redesign in this turn.
- **GlobalExceptionHandler Inventory**:
  - Location: `project/src/main/java/com/example/project/exception/GlobalExceptionHandler.java`.
  - Annotation: `@RestControllerAdvice` (unbounded, applies across both `@RestController` and `@Controller` MVC view classes).
  - Existing Handlers:
    1. `MethodArgumentNotValidException`: Returns HTTP 400 Bad Request with `{"success": false, "message": "...", "errors": {...}}`.
    2. `RuntimeException`: Returns HTTP 400 Bad Request with `{"success": false, "message": ex.getMessage()}`.
  - Deficiencies Identified:
    - Broad `RuntimeException` catch collapses 403 Forbidden, 404 Not Found, and 500 Internal Server Errors all into HTTP 400.
    - Zero server logging: `GlobalExceptionHandler` lacks an SLF4J logger; unhandled runtime exceptions produce zero server logs.
    - Missing handlers: No handler for `Exception.class` (checked exceptions fall through to Spring default `/error`), `AccessDeniedException`, `EntityNotFoundException`, or `MaxUploadSizeExceededException`.
    - Information Disclosure: Returns raw `ex.getMessage()` for internal errors, potentially leaking database or path details.
- **Controller Try/Catch Inventory (72 Active Catch Blocks Across 35 Controllers)**:
  - **Category A — Truly Redundant (13 blocks)**:
    - `AdminSubscriptionPlanController` (L51, L64, L78): Standard 400 Bad Request with message map.
    - `ContentMovieController` (L70, L81, L91, L102): Standard 400 Bad Request with `{"success": false, "message": ...}` matching `GlobalExceptionHandler`.
    - `UserManageController` (L75, L85, L95): Administrative user CRUD endpoints returning 400 string body.
    - `SocialController` (L51, L77, L91): REST endpoints returning 400 string body.
  - **Category B — Contract-Specific (36 blocks — MUST NOT BE BLINDLY REMOVED)**:
    - `CommentController`: L156, L344 return HTTP 403 Forbidden on ownership violation; L40, L105, L161, L183, L238, L293, L347 return HTTP 500 Internal Server Error.
    - `AIAgentController`: L67 returns HTTP 500 with `{"success": false, "error": ...}` (consumed by `footer.html:1218` reading `data.error`).
    - `AISearchController`: L56 returns HTTP 500 with `{"success": false, "message": ...}`.
    - `ContentMovieController`: L41 returns HTTP 404 Not Found; L55-58 differentiates 400 vs 500 with `{"error": ...}`.
    - `AdminSubscriptionPlanController`: L38 returns HTTP 404 Not Found.
    - `FileUploadController`: L46, L78 return HTTP 500 with `{"error": ...}`.
    - `UserAuthenticationController`: L166 returns HTTP 400 with `{"error": ...}`; L130, L326 handle MVC view/redirect errors.
    - `MessengerApiController`: 10 catch blocks return HTTP 500 with `{"error": ...}` or plain string.
    - MVC Redirects/Views: `MoviePlayerController` (L81), `PaymentController` (L75, L116, L177), `SubscriptionController` (L43), `SocialController` (L36) redirect with flash attributes or render error templates.
  - **Category C — Recovery / Fallback Logic (20 blocks — MUST NOT BE CENTRALIZED)**:
    - `MovieApiController`: L43 (`liveSearchDb`), L127 (`getSimilarMovies`) return HTTP 200 with empty list `[]`; L140 (`getRecommendedMovies`) falls back to `loadRecommendedFallback`.
    - `DiscoverController` (L77, L96), `HomeController` (L65, L92), `SearchController` (L101, L106, L323, L328, L371, L394): Fallbacks to default carousels, hot movies, or empty pages.
    - `MessengerController` (L30): Fallback to empty userJson `"{}"`.
    - `MovieDetailController` (L103), `ProductionCompanyDetailController` (L102): Graceful template fallback.
  - **Category D — Suspicious / Protocol-Specific (3 blocks)**:
    - `ChatController` (L90, L200): STOMP/WebSocket message handling; HTTP `@RestControllerAdvice` does not intercept WebSocket STOMP exceptions.
    - `MessengerApiController` (L115): In-band WebSocket notification failure logged and ignored to prevent failing core message creation.
- **REST API Contract Mismatches & Frontend Risks**:
  - Current backend produces 5 inconsistent error payloads:
    1. Standard envelope: `{"success": false, "message": "..."}`
    2. Error key map: `{"error": "..."}`
    3. Mixed envelope: `{"success": false, "error": "..."}`
    4. Message only: `{"message": "..."}`
    5. Plain text: raw string
  - Frontend Risk: `footer.html:1218` explicitly evaluates `(data.error || 'Unknown error')`. Blindly returning only `data.message` causes AI chat UI to display 'Unknown error'.
  - Frontend Risk: `comment-handler.js:201` checks `data.success` and `data.message`.
- **Security Findings**:
  - Exposing raw `ex.getMessage()` on unexpected 500 errors risks leaking database driver details, SQL syntax, or filesystem paths.
  - Global handler needs an SLF4J logger to record server-side stack traces while returning generic sanitized messages (`"Đã có lỗi xảy ra trên hệ thống"`) for 500 Internal Server Errors.
- **Proposed Implementation Plan for Issue #3**:
  1. Standardize `GlobalExceptionHandler` response envelope to include dual compatibility fields: `{"success": false, "message": msg, "error": msg}`.
  2. Add SLF4J logging (`log.error`) in `GlobalExceptionHandler` with stack trace.
  3. Differentiate HTTP statuses: 400 (Validation / IllegalArgument), 403 (AccessDenied), 404 (ResourceNotFound), 500 (Sanitized generic message for unexpected exceptions).
  4. Restrict advice targeting to `@RestController` to protect Thymeleaf MVC controllers.
- **Note on Code Changes**:
  - **NO CODE IMPLEMENTATION WAS PERFORMED IN THIS TURN.** All existing controllers and services remain 100% untouched.

## 2026-09-25 - Batch 4B — Implement REST Exception Handling Strategy (Issue #3)
- **Issue**: #3 (https://github.com/KhoiPM23/FFilm_MKhoi_SelfDev/issues/3)
- **Branch**: refactor/batch-3-quick-wins
- **Status**: COMPLETE
- **Objective**:
  - Implement standardized REST exception handling in `GlobalExceptionHandler.java` based on the completed Batch 4B audit.
- **Changes**:
  - `GlobalExceptionHandler.java`:
    - Scoped `@RestControllerAdvice` to `annotations = RestController.class` to protect Thymeleaf MVC `@Controller` views from intercepting REST error payloads.
    - Added SLF4J logger: `private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);`.
    - Standardized dual-compatible error response envelope: `{"success": false, "message": msg, "error": msg}` (with `errors` map preserved for validation exceptions).
    - Standardized HTTP semantics:
      - 400 Bad Request: `MethodArgumentNotValidException` (DTO validation errors) and `IllegalArgumentException` (invalid request arguments).
      - 403 Forbidden: `AccessDeniedException` (Spring Security access violations).
      - 404 Not Found: `EntityNotFoundException` (missing database/JPA resources).
      - 500 Internal Server Error: `Exception.class` (uncaught runtime/checked exceptions) with server-side SLF4J stack trace logging (`log.error("Unhandled REST exception", ex)`) and sanitized generic message (`"Đã có lỗi xảy ra trên hệ thống"`).
- **Behavior Preserved**:
  - Zero controller try/catch removals in this batch (reserved for Batch 4C / Issue #4).
  - Full backward compatibility for frontend clients reading `data.message` (`comment-handler.js`, `search.js`), `data.error` (`footer.html` AI chat), and `data.success`.
  - Thymeleaf MVC controllers continue handling view redirects, flash attributes, and error templates without interference from REST advice.
- **Verification**:
  - Maven tests: `.\mvnw.cmd test` passed cleanly (`BUILD SUCCESS`, 1/1 tests passed, 0 failures, 0 errors).
  - Git diff check: `git diff --check` passed cleanly (zero whitespace or syntax issues).
  - Code changes: Strictly confined to `GlobalExceptionHandler.java`.
- **Security & Regression Result**:
  - Sanitized 500 response prevents database schema, SQL errors, or stack trace leakage to clients.
  - Authentication, authorization, IDOR, ownership, and WebSocket security controls remain fully intact.

## 2026-09-25 - Batch 4C — Remove Truly Redundant Controller Exception Handling (Issue #4)
- **Issue**: #4 (https://github.com/KhoiPM23/FFilm_MKhoi_SelfDev/issues/4)
- **Branch**: refactor/batch-3-quick-wins
- **Status**: COMPLETE

### Completed in Batch 4C
- **13 Category A Truly Redundant Catch Blocks Removed Across 4 Controllers**:
  1. `AdminSubscriptionPlanController.java`:
     - `createPlan`: Removed try/catch. DTO validation errors handled by `GlobalExceptionHandler` (`MethodArgumentNotValidException` -> 400).
     - `updatePlan`: Removed try/catch. Response typed to `ResponseEntity<SubscriptionPlan>`.
     - `deactivatePlan`: Removed try/catch. Response typed to `ResponseEntity<Void>`.
     - Cleaned up unused `java.util.Map` import.
  2. `ContentMovieController.java`:
     - `createMovie`: Removed try/catch. Preserved `@Valid @RequestBody` -> handled by `GlobalExceptionHandler`.
     - `updateMovie`: Removed try/catch. Response typed to `ResponseEntity<Movie>`.
     - `deleteMovie`: Removed try/catch. Response typed to `ResponseEntity<Void>`.
     - `syncMoviesByIds`: Removed try/catch. Clean delegation to `movieService.syncTmdbIds`.
  3. `UserManageController.java`:
     - `createUser`: Removed try/catch. `IllegalArgumentException` ("Email đã được sử dụng") handled by `GlobalExceptionHandler` -> 400 Bad Request.
     - `updateUser`: Removed try/catch. Response typed to `ResponseEntity<UserManageDTO>`.
     - `deleteUser`: Removed try/catch. `IllegalArgumentException` ("User not found") handled by `GlobalExceptionHandler` -> 400 Bad Request.
  4. `SocialController.java`:
     - `followUser`: Removed redundant try/catch.
     - `sendFriendRequest`: Removed redundant try/catch.
     - `acceptFriend`: Removed redundant try/catch.
- **Behavior Preserved**:
  - Category B (contract-specific 403, 404, 500, Thymeleaf redirects and views), Category C (recovery/fallback), and Category D (WebSocket) strictly preserved and untouched.
  - Zero changes to frontend code, database, security filters, or services.
- **Verification**:
  - Maven tests: `.\mvnw.cmd test` passed cleanly (`BUILD SUCCESS`, 1/1 tests passed, 0 failures, 0 errors).
  - Diff check: `git diff --check` passed with 0 errors.

### Still Pending (Explicitly Deferred)
- **Batch 4D**:
  - Final backend error-handling consistency audit (Issue #5).
- **Phase 5 — Product Stabilization & Modernization (NOT started in this batch)**:
  - Watch Party/Messenger realtime stabilization
  - WebSocket/STOMP lifecycle, reconnect, and state synchronization
  - WebRTC audit
  - YouTube movie preview / autoplay / native play-overlay bug
  - UI/UX regression audit
  - Browser console / network error audit
  - Frontend performance audit
  - `messenger.js` monolith (~7,000+ lines)
  - JS/CSS modularization
  - Stale/dead frontend code cleanup
  - Modernization evaluation & selective library adoption

## 2026-09-25 - Phase 5A — WebRTC Call Signaling Lifecycle & Media Cleanup
- **Branch**:
efactor/batch-3-quick-wins
- **Status**: IMPLEMENTED & VERIFIED
- **Objective**:
  - Resolve the WebRTC call signaling defect and media stream leak upon call rejection.
  - Establish canonical call protocol alignment across frontend and backend.
  - Enforce immediate camera/microphone hardware track release on callee decline.
- **Root Causes Fixed**:
  1. **String / Semantic Mismatch**: Callee emitted CALL_REJECT, while backend domain model and client expected CALL_DENY.
  2. **Relay Crash on Null Values**: WebSocketController.handleCall used Map.of() which threw NullPointerException on CALL_DENY and CALL_END due to null peerId.
  3. **Transport Channel Multiplexing**: /queue/call receiver in messenger.js lacked message type branching and treated all payloads as new incoming calls.
  4. **Stale Hardware Capture**: When rejection signaling was dropped, caller's camera and mic remained active for 30s until fallback timeout.
- **Changes Made**:
  1. project/src/main/java/com/example/project/controller/WebSocketController.java:
     - Replaced unsafe Map.of() with null-safe HashMap, safely preserving optional peerId, callType, senderName, and senderAvatar.
     - Derived senderId from authenticated Principal (falling back to payload if unauthenticated) to uphold security context.
     - Added safe integer parsing for IDs.
  2. project/src/main/resources/static/js/messenger.js:
     - Standardized window.rejectCall on canonical CALL_DENY matching MessengerMessage.MessageType.CALL_DENY.
     - Updated handleIncomingCall on /queue/call to multiplex by msg.type (CALL_REQ, CALL_DENY / CALL_REJECT, CALL_END).
     - Hardened closeCallModal() to immediately cancel callTimeout, stop all localStream tracks, stop ringtone, close peer connection, and reset state.
     - Updated handleSocketMessage to use non-blocking toast instead of blocking lert().
     - Preserved 30-second unanswered timeout as safety fallback.
- **Verification**:
  - Automated tests: .\mvnw.cmd test passed cleanly (BUILD SUCCESS, 1/1 tests passed, 0 failures, 0 errors).
  - Protocol search: Verified 0 non-standard outgoing events; canonical CALL_DENY and CALL_END verified.
  - Formatting & diff check: git diff --check passed with 0 errors.
  - Working tree: Clean.

## 2026-09-25 - Phase 5B-1: YouTube Preview Stabilization
- **Task**: Fix ghost YouTube hover preview appearing after rapid unhover.
- **Action**: Managed fadeTimeout within hoverPlayerMap and cleared it in stopHoverVideo and playHoverVideo inside script.js to prevent delayed CSS opacity transitions.
- **Result**: Fixed in commit 68f1f24. Hover card previews now correctly abort.

## 2026-09-25 - Phase 5B-2: Backend Exception Leak Hardening
- **Task**: Prevent internal exception details from leaking in 500 error HTTP responses.
- **Action**: Edited FileUploadController, ContentMovieController, CommentController, and AIAgentController to replace e.getMessage() with generic error messages when returning HTTP 500 responses.
- **Result**: Checked in under commit fix(security): prevent exception details leak in 500 responses.

## 2026-09-25 - Phase 5B-3: STOMP Broadcast Consistency
- **Task**: Prevent chat message broadcast when database save fails.
- **Action**: In ChatController.java (sendPrivateMessage), moved the messagingTemplate.convertAndSendToUser calls inside the try-block so they only execute after chatMessageService.saveChatMessage succeeds.
- **Result**: Checked in under commit fix(chat): ensure STOMP message broadcast only if database persist succeeds.

## 2026-09-25 - Phase 5B-4: WebRTC Security Regression Verification
- **Task**: Verify and fix senderId spoofing vulnerabilities in WebSocketController.
- **Action**: Modified handleTyping, handleStopTyping, handleMarkSeen, and handleCall in WebSocketController to always derive the user ID from the authenticated Principal instead of trusting the client payload.
- **Result**: Checked in under commit fix(security): strictly extract senderId from Principal in WebSocket payloads to prevent spoofing.

## 2026-09-25 - Phase 5B-5: Final Product Regression Audit
- **Task**: Perform a final regression audit after all Phase 5B fixes.
- **Action**: Compiled the project and ran all backend unit/integration tests with dynamic agent loading enabled.
- **Result**: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. Build Success. No regressions found.

## 2026-09-26 - Phase 6 / P6-A.1: Watch Party & My Rooms HTTP 500 Root Cause
- **Task**: Investigate runtime HTTP 500 errors on `/watch-party` and `/my-rooms`.
- **Root Cause**: SQLServerException: Invalid object name 'WatchRoom'. Missing tables in `FFilm3` database.
- **Result**: Documented root cause and required schema changes.

## 2026-09-26 - Phase 6 / P6-A.2: Watch Party Database Schema Synchronization
- **Task**: Synchronize missing Watch Party tables with SQL Server database `FFilm3`.
- **Action**: Executed `migration-p6a2-watch-party-schema.sql` creating `WatchRoom`, `FriendRequests`, `Notification`, and `UserFollow`.
- **Result**: Commit `66a576f`. Endpoints `/watch-party` and `/my-rooms` return HTTP 200.

## 2026-09-26 - Phase 6 / P6-A.3: Restore Watch Party Room Creation Flow
- **Task**: Fix frontend blockers preventing room creation from `/watch-party` and `/my-rooms` using the existing backend contract.
- **Root Cause Blocker A (`/watch-party`)**: Button `+ Tạo Phòng Ngay` called `openCreateModal()`, which was undeclared in `lobby.html`. Also `closeCreateModal()`, `togglePassword()`, and `joinRoom()` were missing.
- **Root Cause Blocker B (`/my-rooms`)**: Modal "Khởi Tạo" button triggered `submitCreateRoom()`, which attempted to call non-existent JSON endpoint `POST /api/party/create`, lacked required `accessType` parameter, and "Vào Phòng" link pointed to 404 `/watch-party/{id}` instead of `/watch-party/room/{id}`.
- **Fixes Applied**:
  - `lobby.html`: Implemented `openCreateModal`, `closeCreateModal`, `togglePassword`, `joinRoom`, and safe window click listener.
  - `my-rooms.html`: Integrated modal form directly with `POST /watch-party/create` (`application/x-www-form-urlencoded`), dynamically computed `accessType` ('PRIVATE' if password provided, else 'PUBLIC'), and rewired room enter link to `/watch-party/room/{id}`.
- **Automated Tests**: Maven `.\mvnw.cmd test` passed (`BUILD SUCCESS`, 1 test, 0 failures, 0 errors).
- **Runtime Verification**:
  - Flow A: `/watch-party` -> "+ Tạo Phòng Ngay" opens modal without errors -> created room `Phong Cine VIP 1` (id=1) -> navigated to `/watch-party/room/1`.
  - Flow B: `/my-rooms` lists `Phong Cine VIP 1` -> "+ Tạo Phòng Mới" opens modal -> created room `Phong My Room 2` (id=2) -> navigated to `/watch-party/room/2`.
  - Flow C: `/my-rooms` lists both rooms -> "Vào Phòng" on `Phong My Room 2` navigates cleanly to `/watch-party/room/2`.
  - SQL Server persistence verified: 2 rows in `WatchRoom` table.
- **Future AI Validation Backlog**:
  - [PENDING] Future AI Search runtime validation.
  - [PENDING] Future AI Chatbot runtime validation.

## 2026-09-26 - Phase 6 / P6-A.5: Core Watch Party Room Lifecycle & Migration
- **Task**: P6-A.5 Core Room Lifecycle / Membership / Host Migration / Private Join.
- **Problem**: Disconnected members remained as "ghosts", host disconnect broke room controls, private room approvals were untracked, and Create Room UI was duplicated.
- **Implementation**:
  - Wired `SessionDisconnectEvent` to fetch `httpSessionId` and execute automatic disconnect cleanup and deterministic host migration in `WatchPartyService`.
  - Created `/party/{roomId}/join` STOMP endpoint for robust runtime membership and waiting list handling.
  - Consolidated `/watch-party` and `/my-rooms` modal creation UI into a single Thymeleaf fragment `fragments/watch-party-create-room.html`.
- **Files Changed**:
  - `WebSocketConfig.java`
  - `WebSocketEventListener.java`
  - `WatchPartyController.java`
  - `WatchPartyService.java`
  - `watch-party.js`
  - `lobby.html`
  - `my-rooms.html`
  - `watch-party-create-room.html`
- **Security Considerations**:
  - Host authorization heavily enforced server-side.
  - Client payload spoofing prevented by relying strictly on `Principal` and `httpSessionId` extracted securely during the STOMP handshake.
  - Guest cannot invoke host-only approvals.
- **Automated Verification**:
  - Source architecture, security boundaries, and shared fragment verified successfully.
  - Maven tests: `BUILD SUCCESS` (1 test, 0 failures, 0 errors).
- **Runtime Limitation**:
  - Multi-session browser E2E unavailable in current automated environment. (`[NEEDS REPRO]` for A/B public-room join, real-time participant updates, member removal, host migration UI, and private approval UI).
- **Pending Decisions**:
  - Host auto-reclaim after reconnect: `[PENDING DECISION]`.
  - Private room rejection capability: `[PENDING]`.
- **Next Step**: P6-A.6 WebRTC Signaling & Call Lifecycle.

## 2026-09-26 - Phase 6 / Parallel Workstreams A, B, D (P6-A.6, P6-A.7, P6-A.9)
- **Task**: Implement WebRTC signaling, harden movie sync, modernize Room UI.
- **Problem**: Missing STOMP signaling for WebRTC peer IDs, late joiners desync on movie time, Room UI lacked participant camera space.
- **Implementation**:
  - Conducted Broad Discovery on all 11 workstreams, created `.ai-local/FINDINGS.md`.
  - Added STOMP `/webrtc/register` endpoint in `WatchPartyController` to register PeerJS IDs in `WatchRoomRuntime`.
  - Wired `PEER_REGISTERED` and `/members` STOMP channels in `watch-party.js` to automatically call new peers and clean up disconnected peers.
  - Added `currentPlaybackTime` and `playbackStatus` to `WatchRoomRuntime`. Server intercepts `/sync` to store state, and extrapolates time upon `getHistory` for late joiners.
  - Revamped `room.html` layout, moving chat to right sidebar, movie to main center, and added a horizontal scrollable `participant-strip` at the bottom for WebRTC video feeds.
- **Files Changed**:
  - `RoomMember.java`
  - `WatchPartyController.java`
  - `WatchPartyService.java`
  - `watch-party.js`
  - `room.html`
- **Security Considerations**:
  - WebRTC signaling relies on server-authenticated STOMP sessions, preventing arbitrary PeerID injection.
- **Automated Verification**:
  - Maven tests: `BUILD SUCCESS` (1 test, 0 failures, 0 errors).
- **Runtime Limitation**:
  - Multi-session browser E2E unavailable in current automated environment (`[NEEDS REPRO]` for actual video call rendering and precise movie sync delta).
- **Next Step**: Messenger Modularization & Deep Audit (Workstream F) / P6-A.11.

## 2026-09-26 - Phase 7: WebRTC Stream Leaks, Profile Security & Navigation Fixes
- **Task**: Resolve Messenger WebRTC media stream leaks, enforce current-password verification on sensitive profile mutations, fix REST view redirect bug, and correct route typos.
- **Problems Fixed**:
  - `messenger.js`: `remoteStream` variable shadowing prevented track stoppage on hangup/rejection, leaving audio/video tracks active and leaking DOM video elements. Fixed global variable declarations (`callTimeout`, `callDuration`).
  - `UserProfileUpdateDto.java` & `UserService.java`: `currentPassword` was missing from DTO; `updateProfile()` failed to verify the existing password via `passwordEncoder.matches()`, allowing bypass of client confirmation. Added verification gate.
  - `UserManageController.java` vs `UserAuthenticationController.java`: `/update-privacy` was defined in a `@RestController`, returning raw redirect string literal rather than issuing HTTP 302. Moved to `UserAuthenticationController`.
  - `header.html` & `RecommenedMovieController.java`: Fixed spelling typo `/recommnended` -> `/recommended`.
- **Commits**: `503027a`, `de98dee`, `5f606f1`. Pushed to `origin/main`.
- **Verification**: `.\mvnw.cmd test` passed (`BUILD SUCCESS`).

## 2026-09-26 - Phase 8: Watch Party Full Deep Execution (Lifecycle, Host Authority, Movie Sync, Moderation)
- **Task**: Watch Party Deep Audit & Fixes (Workstream E).
- **Problems Fixed**:
  - **Host Authority Null on Room Creation**: `createRoom` instantiated `WatchRoomRuntime` without calling `setHostUserId(ownerId)`. Because `isHost()` checked `runtime.getHostUserId()`, newly created rooms permanently rejected all host WebSocket actions. Fixed by setting `runtime.setHostUserId(ownerId)` and ensuring fallback in `joinRoom`.
  - **Host Migration View Desync**: When a host disconnected, `handleDisconnect` migrated host identity in RAM (`room.setHostUserId()`), but HTTP GET `/watch-party/room/{id}` evaluated host via `dbRoom.getOwner() == user.getId()`. On page reload, the migrated host was stripped of host UI privileges. Fixed by prioritizing `runtime.getHostUserId()`.
  - **Private Room Lockout & Refresh Loop**: `requestJoin` checked `"PRIVATE".equals(accessType)` without checking `isHost`, placing hosts in their own waiting list! Furthermore, approved guests who refreshed were forced back into `WAITING`. Fixed by adding `approvedUserIds` set to `WatchRoomRuntime` and respecting host/approved status.
  - **Dead Waiting List UI & Missing Reject Endpoint**: `showWaitingList()` had placeholder alert `Danh sách chờ đang được phát triển`. Implemented full Host Waiting List Modal (`#waitingListModal`) with real-time counters, "Duyệt" (`/admin/approve`), and "Từ chối" (`/admin/reject`). Added `rejectMember()` in `WatchPartyService`.
  - **Movie Sync Drift & Scrubbing Race Condition**: Host scrubbing fired rapid seek events; added 150ms seek debounce and periodic 5s heartbeat sync during active playback. Member drift correction smoothly synchronizes any client trailing by > 1.5s.
  - **WebRTC Camera Toggle Audio Destruction**: In `toggleCam()`, `myStream.getTracks().forEach(track => track.stop())` destroyed audio tracks permanently. Fixed by toggling `videoTrack.enabled` without killing audio tracks. Added `myPeer.on('error')` exception handling.
  - **Missing Delete Room & Dissolution Endpoints**: Added `@DeleteMapping("/api/party/delete/{roomId}")` for `my-rooms.html`, `@PostMapping("/api/party/close/{roomId}")`, and `@MessageMapping("/party/{roomId}/admin/close")` broadcasting `ROOM_CLOSED` to cleanly disperse rooms.
  - **Sidebar Tab System & Participant Roster**: Added "Trò chuyện" (Chat) and "Thành viên" (Members) tabs to `room.html`, displaying participant list with host badge and Kick controls.
  - **Lobby Actions**: Added `openChat(userId)` (routing to `/messenger?uid=`) and `viewProfile(userId)` (routing to `/social/profile/`).
  - **WebSocket Session Attribute Alignment**: Added `"userSession"` to `WebSocketConfig` and fallback check in `WebSocketEventListener` to restore `OnlineStatusService` presence tracking.
- **Commit**: `73f5327`. Pushed to `origin/main`.
- **Verification**: `.\mvnw.cmd test` passed (`BUILD SUCCESS`, 1 test, 0 failures, 0 errors). Working tree clean, `HEAD == origin/main`.
