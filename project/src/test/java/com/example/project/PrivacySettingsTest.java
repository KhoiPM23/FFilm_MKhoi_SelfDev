package com.example.project;

import com.example.project.model.User;
import com.example.project.repository.FavoriteRepository;
import com.example.project.repository.FriendRequestRepository;
import com.example.project.repository.UserFollowRepository;
import com.example.project.repository.UserRepository;
import com.example.project.repository.WatchHistoryRepository;
import com.example.project.service.NotificationService;
import com.example.project.service.SocialService;
import com.example.project.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Kiểm tra chế độ hiển thị (privacy): lưu cờ đúng và SocialService chỉ truy vấn
 * dữ liệu riêng tư khi chủ hồ sơ cho phép (hoặc người xem là chính chủ).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrivacySettingsTest {

    @Mock private UserRepository userRepository;
    @Mock private UserFollowRepository followRepository;
    @Mock private WatchHistoryRepository historyRepository;
    @Mock private FriendRequestRepository friendRequestRepository;
    @Mock private NotificationService notificationService;
    @Mock private FavoriteRepository favoriteRepository;

    @InjectMocks private SocialService socialService;

    private User target(boolean friends, boolean favs, boolean history) {
        User u = new User();
        u.setUserID(7);
        u.setUserName("Target");
        u.setPublicFriendList(friends);
        u.setPublicFavorites(favs);
        u.setPublicWatchHistory(history);
        when(userRepository.findById(Integer.valueOf(7))).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void privateProfile_hidesFavoritesAndHistoryFromStranger() {
        User t = target(false, false, false);

        var profile = socialService.getUserProfile(99, 7);

        assertEquals("STRANGER", profile.getRelationStatus());
        assertTrue(profile.getFriends().isEmpty());
        verify(favoriteRepository, never()).findByUser(t);
        verify(historyRepository, never()).findByUserOrderByLastWatchedAtDesc(any(), any());
    }

    @Test
    void publicProfile_loadsFavoritesAndHistoryForStranger() {
        User t = target(true, true, true);

        socialService.getUserProfile(99, 7);

        verify(favoriteRepository).findByUser(t);
        verify(historyRepository).findByUserOrderByLastWatchedAtDesc(t, PageRequest.of(0, 10));
    }

    @Test
    void owner_alwaysSeesOwnPrivateData() {
        User t = target(false, false, false);

        var profile = socialService.getUserProfile(7, 7);

        assertEquals("ME", profile.getRelationStatus());
        verify(favoriteRepository).findByUser(t);
        verify(historyRepository).findByUserOrderByLastWatchedAtDesc(t, PageRequest.of(0, 10));
    }

    @Test
    void updatePrivacy_persistsAllThreeFlags() {
        UserService userService = new UserService(userRepository);
        User u = new User();
        u.setUserID(7);
        when(userRepository.findById(7)).thenReturn(Optional.of(u));

        userService.updatePrivacy(7, false, true, false);

        assertFalse(u.isPublicFriendList());
        assertTrue(u.isPublicFavorites());
        assertFalse(u.isPublicWatchHistory());
        verify(userRepository).save(u);
    }
}
