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

    @Value("${mirror.storage.bak.path:./storage_bak}")
    private String storageBakPath;

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
        String storagePath = projectFolder + Constants.FILE_FOLDER_FILE;
        deleteDirectoryContents(storagePath);

        String tempPath = projectFolder + Constants.FILE_FOLDER_TEMP;
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