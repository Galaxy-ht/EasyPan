package com.easypan.utils;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * @author Tao
 * @since 2023/09/01
 */

public class ScaleFilter {

    private static Logger logger = LoggerFactory.getLogger(ScaleFilter.class);


    public static void createCover4Video(File sourceFile, Integer width, File targetFile) {
        try {
            String cmd = "ffmpeg -i %s -y -vframes 1 -vf scale=%d:%d/a %s";
            String result = ProcessUtils.executeCommand(String.format(cmd, sourceFile.getAbsoluteFile(), width, width, targetFile.getAbsoluteFile()), false);
            if (result == null) {
                logger.warn("FFmpeg 不可用，跳过视频封面生成");
            }
        } catch (Exception e) {
            logger.error("生成视频封面失败", e);
        }
    }

    public static Boolean createThumbnailWidthFFmpeg(File file, int thumbnailWidth, File targetFile, Boolean delSource) {
        try {
            BufferedImage src = ImageIO.read(file);
            //thumbnailWidth 缩略图的宽度   thumbnailHeight 缩略图的高度
            int sorceW = src.getWidth();
            int sorceH = src.getHeight();
            //小于 指定高宽不压缩
            if (sorceW <= thumbnailWidth) {
                return false;
            }
            compressImage(file, thumbnailWidth, targetFile, delSource);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void compressImage(File sourceFile, Integer width, File targetFile, Boolean delSource) {
        try {
            String cmd = "ffmpeg -i %s -vf scale=%d:-1 %s -y";
            String result = ProcessUtils.executeCommand(String.format(cmd, sourceFile.getAbsoluteFile(), width, targetFile.getAbsoluteFile()), false);
            if (result == null) {
                logger.warn("FFmpeg 不可用，跳过图片压缩");
            }
            if (delSource) {
                FileUtils.forceDelete(sourceFile);
            }
        } catch (Exception e) {
            logger.error("压缩图片失败");
        }
    }

}
