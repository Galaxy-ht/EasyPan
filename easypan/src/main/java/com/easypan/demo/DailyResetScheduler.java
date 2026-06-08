package com.easypan.demo;

import com.easypan.entity.constants.Constants;
import com.easypan.mappers.EmailCodeDao;
import com.easypan.mappers.FileInfoDao;
import com.easypan.mappers.FileShareDao;
import com.easypan.mappers.UserInfoDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;

@Component
@EnableScheduling
public class DailyResetScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DailyResetScheduler.class);

    @Value("${demo.mode:false}")
    private boolean demoMode;

    @Value("${project.folder}")
    private String projectFolder;

    @Resource
    private UserInfoDao userInfoDao;

    @Resource
    private FileInfoDao fileInfoDao;

    @Resource
    private EmailCodeDao emailCodeDao;

    @Resource
    private FileShareDao fileShareDao;

    @Resource
    private DemoDataSeeder demoDataSeeder;

    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyReset() {
        if (!demoMode) {
            return;
        }

        logger.info("=== Daily Demo Reset Starting ===");

        try {
            // Clear all database data
            // Clear all database data
            fileInfoDao.delete(null);
            fileShareDao.delete(null);
            userInfoDao.delete(null);
            emailCodeDao.delete(null);
            logger.info("Cleared all database data");

            // Clear storage files
            clearStorageDirectory();
            logger.info("Cleared all storage files");

            // Re-seed demo data
            demoDataSeeder.run();
            logger.info("=== Daily Demo Reset Complete ===");
        } catch (Exception e) {
            logger.error("Daily reset failed", e);
        }
    }

    private void clearStorageDirectory() {
        String storagePath = projectFolder + Constants.FILE_FOLDER_FILE;
        Path dir = Paths.get(storagePath);
        if (Files.exists(dir)) {
            try {
                Files.walk(dir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                logger.error("Failed to clear storage directory", e);
            }
        }

        String tempPath = projectFolder + Constants.FILE_FOLDER_TEMP;
        Path tempDir = Paths.get(tempPath);
        if (Files.exists(tempDir)) {
            try {
                Files.walk(tempDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                logger.error("Failed to clear temp directory", e);
            }
        }
    }
}