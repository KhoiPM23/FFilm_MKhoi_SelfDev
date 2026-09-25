# Project Context

## Overview
- **Project Name**: FFilm_MKhoi_SelfDev
- **Project Purpose**: Social Movie Discovery Platform (streaming, watch parties, live chat, personalized discovery)
- **Status**: Solo-project (verified via documentation constraints and project owner instructions)
- **Repository Location**: `D:\Personal_Projects\FFilm_MKhoi_SelfDev`

## Technology Stack
- **Backend**:
  - Java 17
  - Spring Boot 3.x (Spring MVC, Spring Data JPA, Spring Security)
  - Maven
  - WebSocket (STOMP) for Real-time Watch Parties and Chat
- **Database**:
  - SQL Server
  - Current Local Database: `FFilm3`
- **Frontend**:
  - HTML5 / CSS3 / Vanilla JS
  - Thymeleaf (Server-side rendering)
- **External Integrations**:
  - The Movie Database (TMDB) API
  - Google Gemini API (AI Search, AI Chatbot)
  - [PENDING] GIPHY API (Reported as undefined in frontend)
  - VnPay SDK (Payment Gateway)

## Source-of-Truth Hierarchy
When investigating or modifying the system, adhere to the following hierarchy:
1. Current source code / config structure (actual implementation)
2. Current database / runtime evidence (e.g., actual schema, runtime API responses)
3. Git history
4. Existing project documentation (`README.md`, `GUIDELINE_DEV_Id_Handle.md`)
5. `WORK_LOG.md`
6. Historical conversation assumptions

*Rule: Never turn an old assumption into a FACT without current evidence.*

## Agent Workflow Rules
1. **Audit before editing**: Always verify the current state of code and runtime.
2. **Current source beats historical assumptions**.
3. **Never invent evidence**: Do not hallucinate database names, table definitions, or API responses.
4. **Never expose secrets**: Keep all credentials out of Git history, logs, issues, and AI outputs.
5. **No destructive DB/Git actions** without explicit authorization.
6. **Keep focused tasks focused**: Do not refactor unrelated code.
7. **Verify actual runtime** instead of assuming compilation means success.
8. **Record important decisions** in `DECISIONS.md`.
9. **Leave a clean handoff** in `.ai-local/handoffs/README.md`.
10. **Commit scoped work** when explicitly authorized.
11. **Keep `.ai-local` local-only**.
