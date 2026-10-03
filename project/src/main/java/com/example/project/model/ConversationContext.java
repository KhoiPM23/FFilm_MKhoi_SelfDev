package com.example.project.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DTO luu tru ngu canh hoi thoai (Memory).
 * Nang cap Batch 1: Full StateSnapshot cho multi-level backtracking.
 */
public class ConversationContext implements Serializable {

    private static final long serialVersionUID = 2L;

    // ---- Inner class: Full State Snapshot ----
    public static class StateSnapshot implements Serializable {
        private static final long serialVersionUID = 1L;
        public List<Map<String, Object>> candidateMovies;
        public Map<String, Object> focusedMovie;
        public List<String> activeGenres;
        public String activeCountry;
        public List<String> excludedGenres;
        public List<String> excludedDirectors;
        public Float activeMinRating;
        public Integer activeMaxDuration;
        public Integer activeYearFrom;
        public Integer activeYearTo;
        public String activeActor;
        public String activeDirector;
        public String lastFocusedPerson;
        public String lastFocusedDirector;
        public StateSnapshot() {}
    }

    private String lastQuery;
    private String lastSubjectType;
    private Object lastSubjectId;
    private String lastQuestionAsked;

    private List<Integer> shownMovieIds = new ArrayList<>();
    private List<Integer> shownPersonIds = new ArrayList<>();

    private List<Map<String, Object>> lastCandidateMovies = new ArrayList<>();
    private Map<String, Object> lastFocusedMovie;
    private String lastFocusedPerson;

    private Map<String, Object> lastPageContext;
    private String lastActiveIntent;
    private List<String> lastActiveGenres = new ArrayList<>();
    private String lastActiveCountry;
    private Map<String, Object> lastBaseMovie;

    private List<String> excludedGenres = new ArrayList<>();
    private List<String> excludedDirectors = new ArrayList<>();
    private List<String> excludedActors = new ArrayList<>();
    private Float activeMinRating;
    private Integer activeMaxDuration;
    private Integer activeYearFrom;
    private Integer activeYearTo;
    private String activeActor;
    private String activeDirector;

    private String lastFocusedDirector;
    private List<String> lastMentionedPersons = new ArrayList<>();

    // Full State Snapshot Stack (max 6 levels)
    private List<StateSnapshot> snapshotStack = new ArrayList<>();

    // Legacy compat
    private List<Map<String, Object>> previousCandidateMovies = new ArrayList<>();
    private Map<String, Object> previousFocusedMovie;

    public ConversationContext() {}

    public ConversationContext(String lastQuery, String lastSubjectType, Object lastSubjectId, String lastQuestionAsked) {
        this.lastQuery = lastQuery;
        this.lastSubjectType = lastSubjectType;
        this.lastSubjectId = lastSubjectId;
        this.lastQuestionAsked = lastQuestionAsked;
    }

    /**
     * Luu snapshot TOAN BO trang thai hien tai vao stack.
     * Bao gom: candidates, focusedMovie, tat ca filters dang active.
     * Stack toi da 6 entries.
     */
    public void pushStateSnapshot() {
        if (this.lastCandidateMovies == null || this.lastCandidateMovies.isEmpty()) return;

        StateSnapshot snap = new StateSnapshot();
        snap.candidateMovies = new ArrayList<>(this.lastCandidateMovies);
        snap.focusedMovie = this.lastFocusedMovie != null ? new java.util.HashMap<>(this.lastFocusedMovie) : null;
        snap.activeGenres = this.lastActiveGenres != null ? new ArrayList<>(this.lastActiveGenres) : new ArrayList<>();
        snap.activeCountry = this.lastActiveCountry;
        snap.excludedGenres = this.excludedGenres != null ? new ArrayList<>(this.excludedGenres) : new ArrayList<>();
        snap.excludedDirectors = this.excludedDirectors != null ? new ArrayList<>(this.excludedDirectors) : new ArrayList<>();
        snap.activeMinRating = this.activeMinRating;
        snap.activeMaxDuration = this.activeMaxDuration;
        snap.activeYearFrom = this.activeYearFrom;
        snap.activeYearTo = this.activeYearTo;
        snap.activeActor = this.activeActor;
        snap.activeDirector = this.activeDirector;
        snap.lastFocusedPerson = this.lastFocusedPerson;
        snap.lastFocusedDirector = this.lastFocusedDirector;

        this.snapshotStack.add(snap);
        if (this.snapshotStack.size() > 6) this.snapshotStack.remove(0);

        this.previousCandidateMovies = snap.candidateMovies;
        if (snap.focusedMovie != null) this.previousFocusedMovie = snap.focusedMovie;
    }

    /**
     * Khoi phuc snapshot gan nhat (pop).
     * Tra ve true neu co snapshot de pop.
     */
    public boolean popStateSnapshot() {
        if (!this.snapshotStack.isEmpty()) {
            StateSnapshot snap = this.snapshotStack.remove(this.snapshotStack.size() - 1);
            applySnapshot(snap);
            // Cap nhat previousCandidateMovies sau khi pop
            if (!this.snapshotStack.isEmpty()) {
                this.previousCandidateMovies = this.snapshotStack.get(this.snapshotStack.size() - 1).candidateMovies;
            } else {
                this.previousCandidateMovies = new ArrayList<>();
            }
            return true;
        }
        if (this.previousCandidateMovies != null && !this.previousCandidateMovies.isEmpty()) {
            this.lastCandidateMovies = new ArrayList<>(this.previousCandidateMovies);
            if (this.previousFocusedMovie != null) {
                this.lastFocusedMovie = new java.util.HashMap<>(this.previousFocusedMovie);
            }
            return true;
        }
        return false;
    }

    /**
     * Khoi phuc snapshot cu nhat trong stack (dung cho "quay lai danh sach ban dau").
     * Xoa toan bo stack sau khi pop.
     */
    public boolean popOldestSnapshot() {
        if (!this.snapshotStack.isEmpty()) {
            StateSnapshot oldest = this.snapshotStack.get(0);
            this.snapshotStack.clear();
            applySnapshot(oldest);
            return true;
        }
        return false;
    }

    /**
     * Xem snapshot cu nhat ma khong xoa (peek).
     */
    public StateSnapshot peekOldestSnapshot() {
        return !this.snapshotStack.isEmpty() ? this.snapshotStack.get(0) : null;
    }

    /**
     * Xem snapshot gan nhat ma khong xoa (peek).
     */
    public StateSnapshot peekLatestSnapshot() {
        return !this.snapshotStack.isEmpty() ? this.snapshotStack.get(this.snapshotStack.size() - 1) : null;
    }

    /** So luong levels backtracking con lai. */
    public int getSnapshotDepth() { return this.snapshotStack.size(); }

    private void applySnapshot(StateSnapshot snap) {
        this.lastCandidateMovies = snap.candidateMovies != null ? new ArrayList<>(snap.candidateMovies) : new ArrayList<>();
        this.lastFocusedMovie = snap.focusedMovie != null ? new java.util.HashMap<>(snap.focusedMovie) : null;
        this.lastActiveGenres = snap.activeGenres != null ? new ArrayList<>(snap.activeGenres) : new ArrayList<>();
        this.lastActiveCountry = snap.activeCountry;
        this.excludedGenres = snap.excludedGenres != null ? new ArrayList<>(snap.excludedGenres) : new ArrayList<>();
        this.excludedDirectors = snap.excludedDirectors != null ? new ArrayList<>(snap.excludedDirectors) : new ArrayList<>();
        this.activeMinRating = snap.activeMinRating;
        this.activeMaxDuration = snap.activeMaxDuration;
        this.activeYearFrom = snap.activeYearFrom;
        this.activeYearTo = snap.activeYearTo;
        this.activeActor = snap.activeActor;
        this.activeDirector = snap.activeDirector;
        if (snap.lastFocusedPerson != null) this.lastFocusedPerson = snap.lastFocusedPerson;
        if (snap.lastFocusedDirector != null) this.lastFocusedDirector = snap.lastFocusedDirector;
    }

    // Getters & Setters
    public String getLastQuery() { return lastQuery; }
    public void setLastQuery(String lastQuery) { this.lastQuery = lastQuery; }

    public String getLastSubjectType() { return lastSubjectType; }
    public void setLastSubjectType(String lastSubjectType) { this.lastSubjectType = lastSubjectType; }

    public Object getLastSubjectId() { return lastSubjectId; }
    public void setLastSubjectId(Object lastSubjectId) { this.lastSubjectId = lastSubjectId; }

    public String getLastQuestionAsked() { return lastQuestionAsked; }
    public void setLastQuestionAsked(String lastQuestionAsked) { this.lastQuestionAsked = lastQuestionAsked; }

    public List<Integer> getShownMovieIds() { return shownMovieIds; }
    public void setShownMovieIds(List<Integer> shownMovieIds) { this.shownMovieIds = shownMovieIds; }
    public void addShownMovieId(Integer id) { this.shownMovieIds.add(id); }
    public void addShownMovieIds(List<Integer> ids) { this.shownMovieIds.addAll(ids); }
    public void clearShownMovieIds() { this.shownMovieIds.clear(); }

    public List<Integer> getShownPersonIds() { return shownPersonIds; }
    public void setShownPersonIds(List<Integer> shownPersonIds) { this.shownPersonIds = shownPersonIds; }
    public void addShownPersonId(Integer id) { this.shownPersonIds.add(id); }
    public void addShownPersonIds(List<Integer> ids) { this.shownPersonIds.addAll(ids); }

    public List<Map<String, Object>> getLastCandidateMovies() { return lastCandidateMovies; }
    public void setLastCandidateMovies(List<Map<String, Object>> lastCandidateMovies) {
        this.lastCandidateMovies = lastCandidateMovies != null ? lastCandidateMovies : new ArrayList<>();
    }

    public Map<String, Object> getLastFocusedMovie() { return lastFocusedMovie; }
    public void setLastFocusedMovie(Map<String, Object> lastFocusedMovie) { this.lastFocusedMovie = lastFocusedMovie; }

    public String getLastFocusedPerson() { return lastFocusedPerson; }
    public void setLastFocusedPerson(String lastFocusedPerson) { this.lastFocusedPerson = lastFocusedPerson; }

    public Map<String, Object> getLastPageContext() { return lastPageContext; }
    public void setLastPageContext(Map<String, Object> lastPageContext) { this.lastPageContext = lastPageContext; }

    public String getLastActiveIntent() { return lastActiveIntent; }
    public void setLastActiveIntent(String lastActiveIntent) { this.lastActiveIntent = lastActiveIntent; }

    public List<String> getLastActiveGenres() { return lastActiveGenres; }
    public void setLastActiveGenres(List<String> lastActiveGenres) {
        this.lastActiveGenres = lastActiveGenres != null ? lastActiveGenres : new ArrayList<>();
    }

    public String getLastActiveCountry() { return lastActiveCountry; }
    public void setLastActiveCountry(String lastActiveCountry) { this.lastActiveCountry = lastActiveCountry; }

    public Map<String, Object> getLastBaseMovie() { return lastBaseMovie; }
    public void setLastBaseMovie(Map<String, Object> lastBaseMovie) { this.lastBaseMovie = lastBaseMovie; }

    public List<String> getExcludedGenres() { return excludedGenres; }
    public void setExcludedGenres(List<String> excludedGenres) {
        this.excludedGenres = excludedGenres != null ? excludedGenres : new ArrayList<>();
    }
    public void addExcludedGenre(String genre) {
        if (genre != null && !this.excludedGenres.contains(genre)) this.excludedGenres.add(genre);
    }

    public List<String> getExcludedDirectors() { return excludedDirectors; }
    public void setExcludedDirectors(List<String> excludedDirectors) {
        this.excludedDirectors = excludedDirectors != null ? excludedDirectors : new ArrayList<>();
    }
    public void addExcludedDirector(String director) {
        if (director != null && !this.excludedDirectors.contains(director)) this.excludedDirectors.add(director);
    }

    public List<String> getExcludedActors() { return excludedActors; }
    public void setExcludedActors(List<String> excludedActors) {
        this.excludedActors = excludedActors != null ? excludedActors : new ArrayList<>();
    }

    public Float getActiveMinRating() { return activeMinRating; }
    public void setActiveMinRating(Float activeMinRating) { this.activeMinRating = activeMinRating; }

    public Integer getActiveMaxDuration() { return activeMaxDuration; }
    public void setActiveMaxDuration(Integer activeMaxDuration) { this.activeMaxDuration = activeMaxDuration; }

    public Integer getActiveYearFrom() { return activeYearFrom; }
    public void setActiveYearFrom(Integer activeYearFrom) { this.activeYearFrom = activeYearFrom; }

    public Integer getActiveYearTo() { return activeYearTo; }
    public void setActiveYearTo(Integer activeYearTo) { this.activeYearTo = activeYearTo; }

    public String getActiveActor() { return activeActor; }
    public void setActiveActor(String activeActor) { this.activeActor = activeActor; }

    public String getActiveDirector() { return activeDirector; }
    public void setActiveDirector(String activeDirector) { this.activeDirector = activeDirector; }

    public String getLastFocusedDirector() { return lastFocusedDirector; }
    public void setLastFocusedDirector(String lastFocusedDirector) { this.lastFocusedDirector = lastFocusedDirector; }

    public List<String> getLastMentionedPersons() { return lastMentionedPersons; }
    public void setLastMentionedPersons(List<String> lastMentionedPersons) {
        this.lastMentionedPersons = lastMentionedPersons != null ? lastMentionedPersons : new ArrayList<>();
    }
    public void addMentionedPerson(String personName) {
        if (personName == null || personName.isBlank()) return;
        if (!this.lastMentionedPersons.contains(personName)) {
            this.lastMentionedPersons.add(personName);
            if (this.lastMentionedPersons.size() > 8) this.lastMentionedPersons.remove(0);
        }
    }

    public List<Map<String, Object>> getPreviousCandidateMovies() { return previousCandidateMovies; }
    public void setPreviousCandidateMovies(List<Map<String, Object>> previousCandidateMovies) {
        this.previousCandidateMovies = previousCandidateMovies != null ? previousCandidateMovies : new ArrayList<>();
    }

    public Map<String, Object> getPreviousFocusedMovie() { return previousFocusedMovie; }
    public void setPreviousFocusedMovie(Map<String, Object> previousFocusedMovie) {
        this.previousFocusedMovie = previousFocusedMovie;
    }

    public List<StateSnapshot> getSnapshotStack() { return snapshotStack; }
}
