package com.easypan.demo;

import com.easypan.entity.constants.Constants;
import com.easypan.entity.enums.*;
import com.easypan.entity.po.FileInfo;
import com.easypan.entity.po.UserInfo;
import com.easypan.mappers.EmailCodeDao;
import com.easypan.mappers.FileInfoDao;
import com.easypan.mappers.FileShareDao;
import com.easypan.mappers.UserInfoDao;
import com.easypan.utils.ProcessUtils;
import com.easypan.utils.ScaleFilter;
import com.easypan.utils.StringUtils;
import com.easypan.exception.FastException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.*;
import java.nio.file.*;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
@Order(1)
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DemoDataSeeder.class);

    @Value("${demo.mode:false}")
    private boolean demoMode;

    @Value("${project.folder}")
    private String projectFolder;

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

    private String adminUserId;
    private String fileStoragePath;

    @Override
    public void run(String... args) {
        if (!demoMode) {
            return;
        }
        logger.info("=== Demo Data Seeder Starting ===");

        fileStoragePath = projectFolder + Constants.FILE_FOLDER_FILE;
        try {
            Files.createDirectories(Paths.get(fileStoragePath));
        } catch (IOException e) {
            logger.error("Failed to create storage directory", e);
        }

        clearExistingData();
        createAdminUser();
        createDemoStructure();

        logger.info("=== Demo Data Seeder Complete ===");
    }

    private void clearExistingData() {
        fileInfoDao.delete(null);
        fileShareDao.delete(null);
        userInfoDao.delete(null);
        emailCodeDao.delete(null);
        logger.info("Cleared existing data");
    }

    private void createAdminUser() {
        adminUserId = StringUtils.getRandomNumber(Constants.LENGTH_10);
        UserInfo admin = new UserInfo();
        admin.setUserId(adminUserId);
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

    private void createDemoStructure() {
        // 1. 工作文档
        String workFolderId = createFolder("0", "工作文档");
        String projFolderId = createFolder(workFolderId, "项目资料");
        createTextFile(projFolderId, "需求文档.docx", "本需求文档描述了 EasyPan 云盘系统的核心功能需求。\n\n1. 用户注册登录\n2. 文件上传下载\n3. 文件分享\n4. 回收站\n5. 在线预览\n\n详细需求请参考技术方案.pdf。\n");
        createTextFile(projFolderId, "技术方案.pdf", "EasyPan 技术方案\n\n架构：Spring Boot + Vue 3 + MyBatis-Plus\n数据库：MySQL / H2\n缓存：Redis / Caffeine\n存储：本地文件系统\n\n核心技术栈：\n- 后端：Spring Boot 2.6.1\n- 前端：Vue 3 + Element Plus\n- 视频转码：FFmpeg HLS 切片\n- 秒传：MD5 去重\n");
        createTextFile(projFolderId, "项目计划.xlsx", "阶段,任务,开始日期,结束日期,状态\n需求分析,用户调研,2024-01-01,2024-01-15,完成\n设计,系统架构设计,2024-01-16,2024-01-31,完成\n开发,核心功能开发,2024-02-01,2024-03-15,完成\n测试,集成测试,2024-03-16,2024-03-31,进行中\n");

        String meetingFolderId = createFolder(workFolderId, "会议记录");
        createTextFile(meetingFolderId, "周会纪要.txt", "【EasyPan 项目周会纪要】\n\n日期：2024年3月15日\n参会人：全员\n\n1. 上周进展\n   - 文件上传功能已完成\n   - 视频预览功能开发中\n   - 修复了3个线上bug\n\n2. 本周计划\n   - 完成分享功能\n   - 优化大文件上传体验\n   - 开始性能测试\n\n3. 风险点\n   - 大文件上传内存占用过高\n   - 视频转码耗时较长\n");
        createTextFile(meetingFolderId, "需求评审.txt", "【需求评审会议纪要】\n\n项目：EasyPan 网盘系统\n日期：2024年1月5日\n\n需求列表：\n1. 支持多文件同时上传\n2. 支持断点续传\n3. 支持文件秒传（MD5去重）\n4. 支持视频在线播放\n5. 支持图片在线预览\n6. 支持文件分享链接\n7. 回收站30天自动清理\n\n评审结论：需求合理，可以进入设计阶段。\n");

        createTextFile(workFolderId, "年度总结.pdf", "EasyPan 2024年度总结\n\n==========================================\n\n项目概述\n--------\nEasyPan 是新一代云存储平台，为用户提供安全、便捷的文件存储和分享服务。\n\n核心指标\n--------\n- 注册用户：10,000+\n- 存储文件：500,000+\n- 日均上传：2,000+\n- 系统可用性：99.9%\n\n技术亮点\n--------\n1. 秒传功能：基于MD5文件指纹，相同文件秒传\n2. 视频在线播放：HLS流媒体技术，支持拖拽播放\n3. 多格式预览：支持图片、PDF、Office、代码等格式在线预览\n4. 安全分享：支持提取码、有效期、分享次数限制\n\n==========================================\n");

        // 2. 影音娱乐
        String mediaFolderId = createFolder("0", "影音娱乐");
        String movieFolderId = createFolder(mediaFolderId, "电影");
        copyRealFile(movieFolderId, "城市风光延时摄影.mp4",
                "/Users/egon/Downloads/城市风光延时摄影_爱给网_aigei_com.mp4",
                FileTypeEnums.VIDEO);

        String musicFolderId = createFolder(mediaFolderId, "音乐");
        copyRealFile(musicFolderId, "CamelGrunts.mp3",
                "/Users/egon/Downloads/19005_CamelGrunts_TE013801.mp3",
                FileTypeEnums.MUSIC);
        createTextFile(musicFolderId, "钢琴曲.mp3", "[Demo: 钢琴曲占位文件 - 实际文件部署时复制]");

        String imageFolderId = createFolder(mediaFolderId, "图片");
        copyRealFile(imageFolderId, "风景照.jpg",
                "/Users/egon/Downloads/pexels-vladimirsrajber-34111428.jpg",
                FileTypeEnums.IMAGE);
        copyRealFile(imageFolderId, "城市夜景.png",
                "/Users/egon/Downloads/6844713ef46e60b40c39276d.png",
                FileTypeEnums.IMAGE);
        createTextFile(imageFolderId, "美食照片.webp", "[Demo: 美食照片占位文件 - 实际文件部署时复制]");

        // 3. 代码资源
        String codeFolderId = createFolder("0", "代码资源");
        createTextFile(codeFolderId, "App.java",
                "package com.example;\n\n" +
                "import org.springframework.boot.SpringApplication;\n" +
                "import org.springframework.boot.autoconfigure.SpringBootApplication;\n\n" +
                "@SpringBootApplication\n" +
                "public class App {\n" +
                "    public static void main(String[] args) {\n" +
                "        SpringApplication.run(App.class, args);\n" +
                "    }\n" +
                "}\n");
        createTextFile(codeFolderId, "config.json",
                "{\n" +
                "  \"server\": {\n" +
                "    \"port\": 7090,\n" +
                "    \"contextPath\": \"/api\"\n" +
                "  },\n" +
                "  \"database\": {\n" +
                "    \"type\": \"mysql\",\n" +
                "    \"host\": \"localhost\",\n" +
                "    \"port\": 3306,\n" +
                "    \"name\": \"easypan\"\n" +
                "  },\n" +
                "  \"upload\": {\n" +
                "    \"maxFileSize\": \"50MB\",\n" +
                "    \"chunkSize\": \"1MB\"\n" +
                "  }\n" +
                "}\n");
        createTextFile(codeFolderId, "style.css",
                "/* EasyPan 主题样式 */\n" +
                ":root {\n" +
                "  --primary-color: #409eff;\n" +
                "  --success-color: #67c23a;\n" +
                "  --warning-color: #e6a23c;\n" +
                "  --danger-color: #f56c6c;\n" +
                "  --bg-color: #f5f7fa;\n" +
                "}\n\n" +
                "body {\n" +
                "  margin: 0;\n" +
                "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n" +
                "  background-color: var(--bg-color);\n" +
                "}\n\n" +
                ".container {\n" +
                "  max-width: 1200px;\n" +
                "  margin: 0 auto;\n" +
                "  padding: 20px;\n" +
                "}\n\n" +
                ".file-list {\n" +
                "  display: grid;\n" +
                "  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));\n" +
                "  gap: 16px;\n" +
                "}\n");
        createTextFile(codeFolderId, "index.html",
                "<!DOCTYPE html>\n" +
                "<html lang=\"zh-CN\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>EasyPan - 云盘</title>\n" +
                "    <link rel=\"stylesheet\" href=\"style.css\">\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div id=\"app\">\n" +
                "        <header>\n" +
                "            <h1>EasyPan 云盘</h1>\n" +
                "            <nav>\n" +
                "                <a href=\"#home\">首页</a>\n" +
                "                <a href=\"#share\">分享</a>\n" +
                "                <a href=\"#recycle\">回收站</a>\n" +
                "            </nav>\n" +
                "        </header>\n" +
                "        <main class=\"container\">\n" +
                "            <div class=\"file-list\"></div>\n" +
                "        </main>\n" +
                "        <script src=\"app.js\"></script>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>\n");

        // 4. 压缩包
        String zipFolderId = createFolder("0", "压缩包");
        createZipFile(zipFolderId, "项目源码.zip");
        createZipFile(zipFolderId, "资料合集.rar");

        // 5. 根目录文件
        copyRealFile("0", "头像.png",
                "/Users/egon/Downloads/6844713ef46e60b40c39276d.png",
                FileTypeEnums.IMAGE);
        createTextFile("0", "说明文档.txt",
                "欢迎使用 EasyPan 演示系统！\n\n" +
                "==========================================\n" +
                "系统功能说明\n" +
                "==========================================\n\n" +
                "1. 文件上传\n" +
                "   点击右上角「上传」按钮，支持拖拽上传和分片上传。\n" +
                "   相同文件（MD5一致）将触发秒传功能。\n\n" +
                "2. 文件管理\n" +
                "   支持新建文件夹、重命名、移动、删除等操作。\n" +
                "   删除的文件会先进入回收站，可恢复或彻底删除。\n\n" +
                "3. 文件预览\n" +
                "   支持视频、音频、图片、PDF、Office文档、代码等格式在线预览。\n\n" +
                "4. 文件分享\n" +
                "   选中文件后点击分享，可设置有效期和提取码。\n" +
                "   分享链接可随时取消。\n\n" +
                "5. 回收站\n" +
                "   删除的文件保留在回收站，可恢复或彻底删除。\n\n" +
                "==========================================\n" +
                "演示环境说明\n" +
                "==========================================\n" +
                "- 管理员账号：admin@test.com / 123456\n" +
                "- 新注册用户空间：5MB\n" +
                "- 系统每天凌晨自动重置\n" +
                "- 设置功能仅供展示，不实际保存\n" +
                "==========================================\n");

        logger.info("Demo file structure created");
    }

    private String createFolder(String parentId, String folderName) {
        String folderId = StringUtils.getRandomNumber(Constants.LENGTH_10);
        FileInfo folder = new FileInfo();
        folder.setFileId(folderId);
        folder.setUserId(adminUserId);
        folder.setFilePid(parentId);
        folder.setFileName(folderName);
        folder.setFolderType(FileFolderTypeEnums.FOLDER.getType());
        folder.setCreateTime(new Date());
        folder.setLastUpdateTime(new Date());
        folder.setStatus(FileStatusEnums.USING.getStatus());
        folder.setDelFlag(FileDelFlagEnums.USING.getFlag());
        fileInfoDao.insert(folder);
        return folderId;
    }

    private void copyRealFile(String parentId, String fileName, String sourcePath, FileTypeEnums fileType) {
        String fileId = StringUtils.getRandomNumber(Constants.LENGTH_10);
        File sourceFile = new File(sourcePath);
        if (!sourceFile.exists()) {
            logger.warn("Source file not found: {}, skipping", sourcePath);
            return;
        }

        String suffix = fileName.substring(fileName.lastIndexOf("."));
        String storageFileName = adminUserId + fileId + suffix;
        Path targetPath = Paths.get(fileStoragePath, storageFileName);

        try {
            Files.copy(sourceFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.error("Failed to copy file: {}", sourcePath, e);
            return;
        }

        FileInfo file = new FileInfo();
        file.setFileId(fileId);
        file.setUserId(adminUserId);
        file.setFilePid(parentId);
        file.setFileName(fileName);
        file.setFileSize(sourceFile.length());
        file.setFilePath(storageFileName);
        file.setFolderType(FileFolderTypeEnums.FILE.getType());
        file.setFileCategory(fileType.getCategory().getCategory());
        file.setFileType(fileType.getType());
        file.setCreateTime(new Date());
        file.setLastUpdateTime(new Date());
        file.setStatus(FileStatusEnums.USING.getStatus());
        file.setDelFlag(FileDelFlagEnums.USING.getFlag());
        fileInfoDao.insert(file);
        logger.info("Created demo file: {}", fileName);

        // 视频文件：生成封面和 HLS 流
        if (fileType == FileTypeEnums.VIDEO) {
            processVideoFile(fileId, storageFileName, file);
        }
    }

    private void createTextFile(String parentId, String fileName, String content) {
        String fileId = StringUtils.getRandomNumber(Constants.LENGTH_10);
        String suffix = fileName.substring(fileName.lastIndexOf("."));
        String storageFileName = adminUserId + fileId + suffix;
        Path targetPath = Paths.get(fileStoragePath, storageFileName);

        try {
            Files.write(targetPath, content.getBytes("UTF-8"));
        } catch (IOException e) {
            logger.error("Failed to create text file: {}", fileName, e);
            return;
        }

        File file = targetPath.toFile();
        FileTypeEnums fileType = FileTypeEnums.getFileTypeBySuffix(suffix);

        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileId(fileId);
        fileInfo.setUserId(adminUserId);
        fileInfo.setFilePid(parentId);
        fileInfo.setFileName(fileName);
        fileInfo.setFileSize(file.length());
        fileInfo.setFilePath(storageFileName);
        fileInfo.setFolderType(FileFolderTypeEnums.FILE.getType());
        fileInfo.setFileCategory(fileType.getCategory().getCategory());
        fileInfo.setFileType(fileType.getType());
        fileInfo.setCreateTime(new Date());
        fileInfo.setLastUpdateTime(new Date());
        fileInfo.setStatus(FileStatusEnums.USING.getStatus());
        fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
        fileInfoDao.insert(fileInfo);
        logger.info("Created demo text file: {}", fileName);
    }

    private void createZipFile(String parentId, String fileName) {
        String fileId = StringUtils.getRandomNumber(Constants.LENGTH_10);
        String suffix = fileName.substring(fileName.lastIndexOf("."));
        String storageFileName = adminUserId + fileId + suffix;
        Path targetPath = Paths.get(fileStoragePath, storageFileName);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(targetPath.toFile()))) {
            // Add a README inside the zip
            ZipEntry entry = new ZipEntry("README.txt");
            zos.putNextEntry(entry);
            zos.write("This is a demo archive file for EasyPan demonstration.\n".getBytes("UTF-8"));
            zos.closeEntry();

            // Add a sample source file
            ZipEntry codeEntry = new ZipEntry("src/Sample.java");
            zos.putNextEntry(codeEntry);
            zos.write("public class Sample {\n    public static void main(String[] args) {\n        System.out.println(\"EasyPan Demo\");\n    }\n}\n".getBytes("UTF-8"));
            zos.closeEntry();
        } catch (IOException e) {
            logger.error("Failed to create zip file: {}", fileName, e);
            return;
        }

        File file = targetPath.toFile();
        FileTypeEnums fileType = FileTypeEnums.getFileTypeBySuffix(suffix);

        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileId(fileId);
        fileInfo.setUserId(adminUserId);
        fileInfo.setFilePid(parentId);
        fileInfo.setFileName(fileName);
        fileInfo.setFileSize(file.length());
        fileInfo.setFilePath(storageFileName);
        fileInfo.setFolderType(FileFolderTypeEnums.FILE.getType());
        fileInfo.setFileCategory(fileType.getCategory().getCategory());
        fileInfo.setFileType(fileType.getType());
        fileInfo.setCreateTime(new Date());
        fileInfo.setLastUpdateTime(new Date());
        fileInfo.setStatus(FileStatusEnums.USING.getStatus());
        fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
        fileInfoDao.insert(fileInfo);
        logger.info("Created demo zip file: {}", fileName);
    }

    private void processVideoFile(String fileId, String storageFileName, FileInfo fileInfo) {
        String filePath = fileStoragePath + storageFileName;
        File videoFile = new File(filePath);

        // 1. 生成视频封面缩略图
        String coverFileName = adminUserId + fileId + ".png";
        String coverPath = fileStoragePath + coverFileName;
        ScaleFilter.createCover4Video(videoFile, Constants.LENGTH_150, new File(coverPath));

        // 只有封面文件真正生成时才更新数据库
        File coverFile = new File(coverPath);
        if (coverFile.exists() && coverFile.length() > 0) {
            fileInfo.setFileCover(coverFileName);
            logger.info("Generated video cover: {}", coverFileName);
        } else {
            logger.warn("视频封面生成失败，跳过封面更新");
        }

        // 2. 生成 HLS 切片
        String baseName = storageFileName.substring(0, storageFileName.lastIndexOf("."));
        String tsFolderPath = fileStoragePath + baseName;
        File tsFolder = new File(tsFolderPath);
        if (!tsFolder.exists()) {
            tsFolder.mkdirs();
        }

        String m3u8Path = tsFolderPath + "/" + Constants.M3U8_NAME;

        // 方式1: 直接切片（-c copy，速度快，要求视频编码为H.264）
        final String CMD_CUT_TS_DIRECT = "ffmpeg -i %s -c copy -map 0 -f segment -segment_list %s -segment_time 30 %s/%s_%%04d.ts";
        // 方式2: 转码切片（兼容所有编码，速度较慢）
        final String CMD_CUT_TS_TRANSCODE = "ffmpeg -i %s -c:v libx264 -c:a aac -map 0 -f segment -segment_list %s -segment_time 30 %s/%s_%%04d.ts";

        try {
            // 先尝试直接切片
            String cmd = String.format(CMD_CUT_TS_DIRECT, filePath, m3u8Path, tsFolderPath, fileId);
            String result = ProcessUtils.executeCommand(cmd, false);

            // 检查 m3u8 是否生成成功，如果失败则使用转码方式
            File m3u8File = new File(m3u8Path);
            if (result == null || !m3u8File.exists() || m3u8File.length() == 0) {
                logger.info("直接切片失败或 FFmpeg 不可用，尝试转码切片");
                cmd = String.format(CMD_CUT_TS_TRANSCODE, filePath, m3u8Path, tsFolderPath, fileId);
                ProcessUtils.executeCommand(cmd, false);
            }

            if (m3u8File.exists() && m3u8File.length() > 0) {
                logger.info("Generated HLS stream for video: {}", storageFileName);
            } else {
                logger.warn("HLS 切片生成失败，视频播放可能不可用");
            }
        } catch (FastException e) {
            logger.warn("Video processing failed: {}", e.getMessage());
        }

        // 3. 更新文件信息（封面路径）
        fileInfoDao.updateById(fileInfo);
    }
}