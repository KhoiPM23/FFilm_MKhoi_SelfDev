package com.example.project.config;

import com.example.project.service.TmdbSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class MovieScheduler {

    @Autowired
    private TmdbSyncService tmdbSyncService;

    // Chạy lúc 03:00:00 sáng mỗi ngày
    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduleDailyMovieUpdate() {
        System.out.println("🕒 [SCHEDULER] Kích hoạt tác vụ cập nhật phim định kỳ...");
        tmdbSyncService.syncDailyUpdates();
    }
}