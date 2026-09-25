# Decisions

| Decision | Reason | Evidence / Date | Status |
|---|---|---|---|
| **Use SQL Server (Local target `FFilm3`)** | Matches the local owner's environment setup and avoids destructive reset of existing data. | Local configuration & baseline verification (2026-09-25) | Active |
| **No `ddl-auto=update` in production/schema syncing** | Avoids unpredictable schema rewrites. Additive changes should be handled via explicit SQL migrations. | Baseline verification & schema drift resolution (2026-09-25) | Active |
| **User Privacy Schema Synchronization** | The `User` entity received `isPublicFavorites`, `isPublicFriendList`, and `isPublicWatchHistory` fields in commit `7de319a`. These must map to `BIT NULL` in the DB safely. | Explicit additive migration script `migration-7de319a-user-privacy.sql` (2026-09-25) | Active |
| **No Frontend Exposure of Secrets** | Hardcoded TMDB keys in JS files introduce P0 security risks. All requests requiring external secrets must be proxied through the Backend. | TMDB current-source exposure remediation (2026-09-25) | Active |
| **Local Internal IDs for Relational Tables** | `tmdbId` is an external reference and not a primary key in `Movie`. All satellite tables (Favorites, History, Reactions) MUST join using the internal `movieID`. | `GUIDELINE_DEV_Id_Handle.md` | Active |
| **Credential Rotation & History Purge Requires Authorization** | Erasing Git history or revoking active credentials is a destructive action that requires human oversight to avoid crippling dependent services. | Task constraint (2026-09-25) | Active |
| **Do not refactor unrelated code** | Focus must be kept strictly on the objective at hand to avoid introducing unintentional side effects. | Project workflow rules (2026-09-25) | Active |
