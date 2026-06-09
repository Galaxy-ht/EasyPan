package com.easypan.demo;

import com.easypan.config.AppConfig;
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

    @Resource
    private AppConfig appConfig;

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
            // 清空所有业务数据表
            fileInfoDao.delete(null);
            fileShareDao.delete(null);
            userInfoDao.delete(null);
            emailCodeDao.delete(null);
            logger.info("Cleared all database data");

            // 清空存储目录
            clearStorageDirectory();
            logger.info("Cleared all storage files");

            // 从 storage_bak 恢复文件
            demoDataSeeder.run();
            logger.info("=== Daily Demo Reset Complete ===");
        } catch (Exception e) {
            logger.error("Daily reset failed", e);
        }
    }

    private void clearStorageDirectory() {
        String storagePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE;
        deleteDirectoryContents(storagePath);

        String tempPath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_TEMP;
        deleteDirectoryContents(tempPath);
    }

    private void deleteDirectoryContents(String dirPath) {
        Path dir = Paths.get(dirPath);
        if (Files.exists(dir)) {
            try {
                Files.walk(dir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                logger.error("Failed to clear directory: {}", dirPath, e);
            }
        }
    }
}