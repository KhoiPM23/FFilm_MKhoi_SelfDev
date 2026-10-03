package com.example.project.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DTO lưu trữ ngữ cảnh hội thoại (Memory)
 * Đã nâng cấp cho Phase 5 (Hỗ trợ phân trang/loại trừ)
 */
public class ConversationContext implements Serializable {
    
    private static final long serialVersionUID = 1L;

    private String lastQuery;         
    private String lastSubjectType;   // "Movie", "Person", "Genre"
    private Object lastSubjectId;     // ID hoặc Tên
    private String lastQuestionAsked; // "ask_director_movies", "ask_more"
    
    // Phase 5: Danh sách ID đã hiển thị (để tránh lặp lại khi "xem thêm")
    private List<Integer> shownMovieIds = new ArrayList<>();
    private List<Integer> shownPersonIds = new ArrayList<>();

    // Phase 14: Danh sách phim gợi ý gần nhất & thực thể đang thảo luận để hỗ trợ multi-turn follow-up
    private List<Map<String, Object>> lastCandidateMovies = new ArrayList<>();
    private Map<String, Object> lastFocusedMovie;
    private String lastFocusedPerson;

    // Phase 15: Proactive Page Context & Conversational Narrowing
    private Map<String, Object> lastPageContext;
    private String lastActiveIntent;
    private List<String> lastActiveGenres = new ArrayList<>();
    private String lastActiveCountry;
    private Map<String, Object> lastBaseMovie;

    // Advanced Movie Intelligence: Multi-Constraint & Negative Preferences
    private List<String> excludedGenres = new ArrayList<>();
    private List<String> excludedDirectors = new ArrayList<>();
    private List<String> excludedActors = new ArrayList<>();
    private Float activeMinRating;
    private Integer activeMaxDuration;
    private Integer activeYearFrom;
    private Integer activeYearTo;
    private String activeActor;
    private String activeDirector;

    // Movie ↔ Person Graph Context
    private String lastFocusedDirector;
    private List<String> lastMentionedPersons = new ArrayList<>();

    // Backtracking & History State Snapshot
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
     * Lưu snapshot trạng thái hiện tại trước khi switch sang chủ đề/danh sách mới
     */
    public void pushStateSnapshot() {
        if (this.lastCandidateMovies != null && !this.lastCandidateMovies.isEmpty()) {
            this.previousCandidateMovies = new ArrayList<>(this.lastCandidateMovies);
        }
        if (this.lastFocusedMovie != null) {
            this.previousFocusedMovie = new java.util.HashMap<>(this.lastFocusedMovie);
        }
    }

    /**
     * Khôi phục snapshot danh sách trước đó khi user yêu cầu "quay lại"
     */
    public boolean popStateSnapshot() {
        if (this.previousCandidateMovies != null && !this.previousCandidateMovies.isEmpty()) {
            this.lastCandidateMovies = new ArrayList<>(this.previousCandidateMovies);
            if (this.previousFocusedMovie != null) {
                this.lastFocusedMovie = new java.util.HashMap<>(this.previousFocusedMovie);
            }
            return true;
        }
        return false;
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

    public List<Map<String, Object>> getPreviousCandidateMovies() { return previousCandidateMovies; }
    public void setPreviousCandidateMovies(List<Map<String, Object>> previousCandidateMovies) {
        this.previousCandidateMovies = previousCandidateMovies != null ? previousCandidateMovies : new ArrayList<>();
    }

    public Map<String, Object> getPreviousFocusedMovie() { return previousFocusedMovie; }
    public void setPreviousFocusedMovie(Map<String, Object> previousFocusedMovie) {
        this.previousFocusedMovie = previousFocusedMovie;
    }
}