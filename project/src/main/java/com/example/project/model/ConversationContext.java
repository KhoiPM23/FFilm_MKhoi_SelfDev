package com.example.project.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

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

    public ConversationContext() {}

    public ConversationContext(String lastQuery, String lastSubjectType, Object lastSubjectId, String lastQuestionAsked) {
        this.lastQuery = lastQuery;
        this.lastSubjectType = lastSubjectType;
        this.lastSubjectId = lastSubjectId;
        this.lastQuestionAsked = lastQuestionAsked;
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

    public List<Integer> getShownPersonIds() { return shownPersonIds; }
    public void setShownPersonIds(List<Integer> shownPersonIds) { this.shownPersonIds = shownPersonIds; }
    public void addShownPersonId(Integer id) { this.shownPersonIds.add(id); }
    public void addShownPersonIds(List<Integer> ids) { this.shownPersonIds.addAll(ids); }
}