package com.example.project;

import com.example.project.model.ConversationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests cho ConversationContext — Batch 1 (Context Integrity).
 * Tests KHONG phu thuoc Spring context, chay nhanh.
 */
public class ConversationContextBatch1Test {

    private ConversationContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new ConversationContext();
    }

    // ===================== SNAPSHOT TESTS =====================

    @Test
    @DisplayName("SNAP-1: pushStateSnapshot luu candidates va filters")
    void testPushSnapshotSavesAllState() {
        ctx.setLastCandidateMovies(List.of(Map.of("title", "Inception", "id", 1)));
        ctx.setLastFocusedMovie(Map.of("title", "Inception", "id", 1));
        ctx.setLastActiveGenres(List.of("Hành động", "Khoa học viễn tưởng"));
        ctx.setLastActiveCountry("United States of America");
        ctx.setActiveMinRating(7.0f);

        ctx.pushStateSnapshot();

        assertThat(ctx.getSnapshotDepth()).isEqualTo(1);
    }

    @Test
    @DisplayName("SNAP-2: popStateSnapshot khoi phuc dung candidates va filters")
    void testPopSnapshotRestoresFullState() {
        ctx.setLastCandidateMovies(List.of(Map.of("title", "Inception", "id", 1)));
        ctx.setLastActiveGenres(List.of("Hành động"));
        ctx.setActiveMinRating(8.0f);
        ctx.pushStateSnapshot();

        // Thay doi state
        ctx.setLastCandidateMovies(List.of(Map.of("title", "The Dark Knight", "id", 2)));
        ctx.setLastActiveGenres(List.of("Kinh dị"));
        ctx.setActiveMinRating(9.0f);
        ctx.pushStateSnapshot();

        // Pop: nen khoi phuc The Dark Knight state (state moi nhat)
        boolean popped = ctx.popStateSnapshot();
        assertThat(popped).isTrue();
        assertThat(ctx.getLastCandidateMovies()).hasSize(1);
        assertThat(ctx.getLastCandidateMovies().get(0).get("title")).isEqualTo("The Dark Knight");
        assertThat(ctx.getLastActiveGenres()).contains("Kinh dị");
        assertThat(ctx.getActiveMinRating()).isEqualTo(9.0f);

        // Pop lan 2: nen khoi phuc Inception state
        popped = ctx.popStateSnapshot();
        assertThat(popped).isTrue();
        assertThat(ctx.getLastCandidateMovies()).hasSize(1);
        assertThat(ctx.getLastCandidateMovies().get(0).get("title")).isEqualTo("Inception");
        assertThat(ctx.getLastActiveGenres()).contains("Hành động");
        assertThat(ctx.getActiveMinRating()).isEqualTo(8.0f);
    }

    @Test
    @DisplayName("SNAP-3: popOldestSnapshot nhay thang ve snapshot cu nhat")
    void testPopOldestSnapshot() {
        // Push 3 snapshots
        ctx.setLastCandidateMovies(List.of(Map.of("title", "MovieA", "id", 1)));
        ctx.pushStateSnapshot();

        ctx.setLastCandidateMovies(List.of(Map.of("title", "MovieB", "id", 2)));
        ctx.pushStateSnapshot();

        ctx.setLastCandidateMovies(List.of(Map.of("title", "MovieC", "id", 3)));
        ctx.pushStateSnapshot();

        assertThat(ctx.getSnapshotDepth()).isEqualTo(3);

        // popOldest phai tra ve MovieA va clear stack
        boolean popped = ctx.popOldestSnapshot();
        assertThat(popped).isTrue();
        assertThat(ctx.getLastCandidateMovies().get(0).get("title")).isEqualTo("MovieA");
        assertThat(ctx.getSnapshotDepth()).isEqualTo(0); // Stack cleared
    }

    @Test
    @DisplayName("SNAP-4: Stack gioi han 6 entries, entry cu bi xoa")
    void testSnapshotStackCappedAt6() {
        for (int i = 1; i <= 8; i++) {
            ctx.setLastCandidateMovies(List.of(Map.of("title", "Movie" + i, "id", i)));
            ctx.pushStateSnapshot();
        }
        assertThat(ctx.getSnapshotDepth()).isEqualTo(6);
        // Phai giu 3-8 (moi nhat), khong co 1-2
        ConversationContext.StateSnapshot oldest = ctx.peekOldestSnapshot();
        assertThat(oldest).isNotNull();
        assertThat(oldest.candidateMovies.get(0).get("title")).isEqualTo("Movie3");
    }

    @Test
    @DisplayName("SNAP-5: Filters (excludedGenres, country, rating) duoc khoi phuc dung")
    void testSnapshotRestoresFilters() {
        ctx.setLastCandidateMovies(List.of(Map.of("title", "Test", "id", 1)));
        ctx.setExcludedGenres(new ArrayList<>(List.of("Kinh dị")));
        ctx.setExcludedDirectors(new ArrayList<>(List.of("Nolan")));
        ctx.setActiveMaxDuration(120);
        ctx.setActiveYearFrom(2010);
        ctx.setLastActiveCountry("South Korea");
        ctx.pushStateSnapshot();

        // Thay doi
        ctx.setExcludedGenres(new ArrayList<>());
        ctx.setActiveMaxDuration(null);
        ctx.setLastActiveCountry(null);

        ctx.popStateSnapshot();

        assertThat(ctx.getExcludedGenres()).contains("Kinh dị");
        assertThat(ctx.getExcludedDirectors()).contains("Nolan");
        assertThat(ctx.getActiveMaxDuration()).isEqualTo(120);
        assertThat(ctx.getActiveYearFrom()).isEqualTo(2010);
        assertThat(ctx.getLastActiveCountry()).isEqualTo("South Korea");
    }

    @Test
    @DisplayName("SNAP-6: Push rong (no candidates) khong tao snapshot")
    void testPushWithNoCandidatesDoesNotCreateSnapshot() {
        ctx.setLastCandidateMovies(new ArrayList<>()); // empty
        ctx.pushStateSnapshot();
        assertThat(ctx.getSnapshotDepth()).isEqualTo(0);
    }

    @Test
    @DisplayName("SNAP-7: Pop khi stack rong tra ve false")
    void testPopEmptyStackReturnsFalse() {
        boolean result = ctx.popStateSnapshot();
        assertThat(result).isFalse();
    }

    // ===================== lastMentionedPersons TESTS =====================

    @Test
    @DisplayName("PERSON-1: addMentionedPerson them vao list va gioi han 8")
    void testAddMentionedPersonCapAt8() {
        for (int i = 1; i <= 10; i++) {
            ctx.addMentionedPerson("Actor " + i);
        }
        assertThat(ctx.getLastMentionedPersons()).hasSize(8);
        // Phai giu Actor3-Actor10 (8 gan nhat)
        assertThat(ctx.getLastMentionedPersons().get(0)).isEqualTo("Actor 3");
        assertThat(ctx.getLastMentionedPersons().get(7)).isEqualTo("Actor 10");
    }

    @Test
    @DisplayName("PERSON-2: addMentionedPerson khong them trung")
    void testAddMentionedPersonNoDuplicate() {
        ctx.addMentionedPerson("Leonardo DiCaprio");
        ctx.addMentionedPerson("Leonardo DiCaprio");
        ctx.addMentionedPerson("Tom Hanks");
        assertThat(ctx.getLastMentionedPersons()).hasSize(2);
    }

    @Test
    @DisplayName("PERSON-3: addMentionedPerson bo qua null va blank")
    void testAddMentionedPersonSkipsNullBlank() {
        ctx.addMentionedPerson(null);
        ctx.addMentionedPerson("");
        ctx.addMentionedPerson("   ");
        assertThat(ctx.getLastMentionedPersons()).isEmpty();
    }

    @Test
    @DisplayName("PERSON-4: lastMentionedPersons duoc snapshot va khoi phuc")
    void testMentionedPersonsInSnapshot() {
        ctx.setLastCandidateMovies(List.of(Map.of("title", "Inception", "id", 1)));
        ctx.setLastFocusedPerson("Leonardo DiCaprio");
        ctx.addMentionedPerson("Leonardo DiCaprio");
        ctx.addMentionedPerson("Joseph Gordon-Levitt");
        ctx.pushStateSnapshot();

        ctx.setLastFocusedPerson("Tom Hanks");
        ctx.setLastMentionedPersons(new ArrayList<>(List.of("Tom Hanks")));

        ctx.popStateSnapshot();

        // focusedPerson phai duoc khoi phuc
        assertThat(ctx.getLastFocusedPerson()).isEqualTo("Leonardo DiCaprio");
    }

    // ===================== MULTI-LEVEL BACKTRACK CHAIN =====================

    @Test
    @DisplayName("BACKTRACK-1: 4-level chain A->B->C->D, quay lai tung buoc")
    void testMultiLevelBacktrackChain() {
        // State 0: PhimA
        ctx.setLastCandidateMovies(List.of(Map.of("title", "PhimA", "id", 1)));

        // Chuyen sang PhimB: snapshot PhimA
        ctx.pushStateSnapshot();
        ctx.setLastCandidateMovies(List.of(Map.of("title", "PhimB", "id", 2)));

        // Chuyen sang PhimC: snapshot PhimB
        ctx.pushStateSnapshot();
        ctx.setLastCandidateMovies(List.of(Map.of("title", "PhimC", "id", 3)));

        // Chuyen sang PhimD: snapshot PhimC
        ctx.pushStateSnapshot();
        ctx.setLastCandidateMovies(List.of(Map.of("title", "PhimD", "id", 4)));

        // Stack giu 3 snapshots: PhimA, PhimB, PhimC
        assertThat(ctx.getSnapshotDepth()).isEqualTo(3);

        // Quay lai 1 lan -> PhimC
        boolean popped = ctx.popStateSnapshot();
        assertThat(popped).isTrue();
        assertThat(ctx.getLastCandidateMovies().get(0).get("title")).isEqualTo("PhimC");

        // Quay lai lan 2 -> PhimB
        popped = ctx.popStateSnapshot();
        assertThat(popped).isTrue();
        assertThat(ctx.getLastCandidateMovies().get(0).get("title")).isEqualTo("PhimB");

        // Quay lai lan 3 -> PhimA
        popped = ctx.popStateSnapshot();
        assertThat(popped).isTrue();
        assertThat(ctx.getLastCandidateMovies().get(0).get("title")).isEqualTo("PhimA");

        // Stack gio rong
        assertThat(ctx.getSnapshotDepth()).isEqualTo(0);
    }
}
