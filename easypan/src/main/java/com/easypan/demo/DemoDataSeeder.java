package com.easypan.demo;

import com.easypan.config.AppConfig;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.enums.*;
import com.easypan.entity.po.FileInfo;
import com.easypan.entity.po.UserInfo;
import com.easypan.mappers.EmailCodeDao;
import com.easypan.mappers.FileInfoDao;
import com.easypan.mappers.FileShareDao;
import com.easypan.mappers.UserInfoDao;
import com.easypan.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

@Component
@Order(1)
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DemoDataSeeder.class);

    /**
     * 硬编码的管理员用户ID，与 storage_bak 中的文件路径前缀保持一致
     */
    private static final String ADMIN_USER_ID = "3915723277";

    @Value("${demo.mode:false}")
    private boolean demoMode;

    @Resource
    private AppConfig appConfig;

    @Value("${admin.emails}")
    private String adminEmails;

    @Resource
    private UserInfoDao userInfoDao;

    @Resource
    private FileInfoDao fileInfoDao;

    @Resource
    private EmailCodeDao emailCodeDao;

    @Resource
    private FileShareDao fileShareDao;

    @Override
    public void run(String... args) {
        if (!demoMode) {
            return;
        }
        logger.info("=== Demo Data Seeder Starting ===");

        clearExistingData();
        clearStorageDirectory();
        copyStorageFromBak();
        createAdminUser();
        insertFileMetadata();

        logger.info("=== Demo Data Seeder Complete ===");
    }

    /**
     * 清空所有业务数据表
     */
    private void clearExistingData() {
        fileInfoDao.delete(null);
        fileShareDao.delete(null);
        userInfoDao.delete(null);
        emailCodeDao.delete(null);
        logger.info("Cleared existing database data");
    }

    /**
     * 清空 demo 存储目录，为从 storage_bak 覆盖做准备
     */
    private void clearStorageDirectory() {
        String storagePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE;
        deleteDirectoryContents(storagePath);

        String tempPath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_TEMP;
        deleteDirectoryContents(tempPath);

        logger.info("Cleared storage directory");
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

    /**
     * 将 storage_bak 中的所有内容复制到 demo 存储目录
     */
    private void copyStorageFromBak() {
        Path sourcePath = Paths.get(appConfig.getStorageBakPath());
        Path targetPath = Paths.get(appConfig.getProjectFolder());

        if (!Files.exists(sourcePath)) {
            logger.warn("storage_bak not found at: {}, skipping file copy", appConfig.getStorageBakPath());
            return;
        }

        try {
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path relative = sourcePath.relativize(dir);
                    Path targetDir = targetPath.resolve(relative);
                    if (!Files.exists(targetDir)) {
                        Files.createDirectories(targetDir);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relative = sourcePath.relativize(file);
                    Path targetFile = targetPath.resolve(relative);
                    Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
            logger.info("Copied storage_bak from {} to {}", appConfig.getStorageBakPath(), appConfig.getProjectFolder());
        } catch (IOException e) {
            logger.error("Failed to copy storage_bak", e);
        }
    }

    /**
     * 创建管理员用户（使用硬编码的 user_id，与 storage_bak 文件路径匹配）
     */
    private void createAdminUser() {
        UserInfo admin = new UserInfo();
        admin.setUserId(ADMIN_USER_ID);
        admin.setEmail(adminEmails.split(",")[0].trim());
        admin.setNickName("Demo Admin");
        admin.setPassword(StringUtils.encodeByMd5("123456"));
        admin.setJoinTime(new Date());
        admin.setLastLoginTime(new Date());
        admin.setStatus(UserStatusEnum.ENABLE.getStatus());
        admin.setUseSpace(0L);
        admin.setTotalSpace(50L * Constants.MB);
        userInfoDao.insert(admin);
        logger.info("Admin user created: {}", adminEmails);
    }

    /**
     * 插入硬编码的文件元数据（镜像自本机 MySQL file_info 表）
     */
    private void insertFileMetadata() {
        List<FileInfo> files = buildFileMetadata();
        Date now = new Date();

        for (FileInfo file : files) {
            file.setUserId(ADMIN_USER_ID);
            file.setCreateTime(now);
            file.setLastUpdateTime(now);
            file.setStatus(FileStatusEnums.USING.getStatus());
            file.setDelFlag(FileDelFlagEnums.USING.getFlag());
            fileInfoDao.insert(file);
        }
        logger.info("Inserted {} file records", files.size());
    }

    /**
     * 构建硬编码的文件元数据列表
     * 数据来源：本机 MySQL easypan.file_info 表
     */
    private List<FileInfo> buildFileMetadata() {
        List<FileInfo> list = new ArrayList<>();

        // ── 顶层文件夹 ──────────────────────────────────
        list.add(folder("tSSUIYgxG2", "0", "工作文档"));
        list.add(folder("ljXTuuLRnQ", "0", "影音娱乐"));
        list.add(folder("CTmNIJbBzy", "0", "代码资源"));
        list.add(folder("StoxFXIaY7", "0", "压缩包"));

        // ── 工作文档 ──────────────────────────────────
        list.add(file("mq9xD5bw0A", "tSSUIYgxG2", "需求文档.docx", 37095L,
                "202606/3915723277mq9xD5bw0A.docx", null,
                FileCategoryEnums.DOC.getCategory(), FileTypeEnums.WORD.getType()));
        list.add(file("AAXonPgdPq", "tSSUIYgxG2", "技术方案.pdf", 274494L,
                "202606/3915723277AAXonPgdPq.pdf", null,
                FileCategoryEnums.DOC.getCategory(), FileTypeEnums.PDF.getType()));
        list.add(file("zBDGGjZxdk", "tSSUIYgxG2", "项目计划.xlsx", 5622L,
                "202606/3915723277zBDGGjZxdk.xlsx", null,
                FileCategoryEnums.DOC.getCategory(), FileTypeEnums.EXCEL.getType()));
        list.add(file("Dlb9C0z6Hb", "tSSUIYgxG2", "需求评审.txt", 367L,
                "202606/3915723277Dlb9C0z6Hb.txt", null,
                FileCategoryEnums.DOC.getCategory(), FileTypeEnums.TXT.getType()));

        // ── 影音娱乐 ──────────────────────────────────
        list.add(file("YEa13XOoMK", "ljXTuuLRnQ", "雪山云海_爱给网_aigei_com.mp4", 9388913L,
                "202606/3915723277YEa13XOoMK.mp4", "202606/3915723277YEa13XOoMK.png",
                FileCategoryEnums.VIDEO.getCategory(), FileTypeEnums.VIDEO.getType()));
        list.add(file("BP4EseUURf", "ljXTuuLRnQ", "19005_CamelGrunts_TE013801.mp3", 550660L,
                "202606/3915723277BP4EseUURf.mp3", null,
                FileCategoryEnums.MUSIC.getCategory(), FileTypeEnums.MUSIC.getType()));
        list.add(file("w5ueyLSzEQ", "ljXTuuLRnQ", "6844713ef46e60b40c39276d.png", 129996L,
                "202606/3915723277w5ueyLSzEQ.png", "202606/3915723277w5ueyLSzEQ_.png",
                FileCategoryEnums.IMAGE.getCategory(), FileTypeEnums.IMAGE.getType()));
        list.add(file("o3qoJIRMQL", "ljXTuuLRnQ", "default_avatar.jpg", 50523L,
                "202606/3915723277o3qoJIRMQL.jpg", "202606/3915723277o3qoJIRMQL_.jpg",
                FileCategoryEnums.IMAGE.getCategory(), FileTypeEnums.IMAGE.getType()));

        // ── 代码资源 ──────────────────────────────────
        list.add(file("F3guktPRQi", "CTmNIJbBzy", "App.java", 286L,
                "202606/3915723277F3guktPRQi.java", null,
                FileCategoryEnums.OTHERS.getCategory(), FileTypeEnums.PROGRAM.getType()));
        list.add(file("p0FDXSO4C6", "CTmNIJbBzy", "config.json", 242L,
                "202606/3915723277p0FDXSO4C6.json", null,
                FileCategoryEnums.OTHERS.getCategory(), FileTypeEnums.PROGRAM.getType()));
        list.add(file("HLogZyGmSi", "CTmNIJbBzy", "style.css", 495L,
                "202606/3915723277HLogZyGmSi.css", null,
                FileCategoryEnums.OTHERS.getCategory(), FileTypeEnums.PROGRAM.getType()));
        list.add(file("CErKAXLoQn", "CTmNIJbBzy", "index.html", 661L,
                "202606/3915723277CErKAXLoQn.html", null,
                FileCategoryEnums.OTHERS.getCategory(), FileTypeEnums.PROGRAM.getType()));

        // ── 压缩包 ──────────────────────────────────
        list.add(file("gZJCR6QBwx", "StoxFXIaY7", "网站备案指引.zip", 3070249L,
                "202606/3915723277gZJCR6QBwx.zip", null,
                FileCategoryEnums.OTHERS.getCategory(), FileTypeEnums.ZIP.getType()));

        // ── 根目录文件 ──────────────────────────────────
        list.add(file("l53VacQpVC", "0", "说明文档.txt", 1050L,
                "202606/3915723277l53VacQpVC.txt", null,
                FileCategoryEnums.DOC.getCategory(), FileTypeEnums.TXT.getType()));

        return list;
    }

    private FileInfo folder(String fileId, String filePid, String fileName) {
        FileInfo f = new FileInfo();
        f.setFileId(fileId);
        f.setFilePid(filePid);
        f.setFileName(fileName);
        f.setFolderType(FileFolderTypeEnums.FOLDER.getType());
        f.setFileCategory(FileCategoryEnums.OTHERS.getCategory());
        f.setFileType(FileTypeEnums.OTHERS.getType());
        f.setFileSize(0L);
        return f;
    }

    private FileInfo file(String fileId, String filePid, String fileName, Long fileSize,
                          String filePath, String fileCover, Integer fileCategory, Integer fileType) {
        FileInfo f = new FileInfo();
        f.setFileId(fileId);
        f.setFilePid(filePid);
        f.setFileName(fileName);
        f.setFileSize(fileSize);
        f.setFilePath(filePath);
        f.setFileCover(fileCover);
        f.setFolderType(FileFolderTypeEnums.FILE.getType());
        f.setFileCategory(fileCategory);
        f.setFileType(fileType);
        return f;
    }
}