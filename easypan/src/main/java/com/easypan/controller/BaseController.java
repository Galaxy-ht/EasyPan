package com.easypan.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.easypan.config.AppConfig;
import com.easypan.convert.FileInfoConvert;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.DownloadFileDto;
import com.easypan.entity.dto.SessionShareDto;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.enums.FileCategoryEnums;
import com.easypan.entity.enums.FileFolderTypeEnums;
import com.easypan.entity.po.FileInfo;
import com.easypan.entity.vo.FileInfoVO;
import com.easypan.exception.ErrorCode;
import com.easypan.exception.FastException;
import com.easypan.query.FileInfoQuery;
import com.easypan.service.FileInfoService;
import com.easypan.utils.RedisComponent;
import com.easypan.utils.Result;
import com.easypan.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.net.URLEncoder;
import java.util.List;

/**
 * @author Tao
 * @since 2023/08/25
 */

public class BaseController {

    private static final Logger logger = LoggerFactory.getLogger(UserInfoController.class);

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_TYPE_VALUE = "application/json;charset=UTF-8";

    @Resource
    protected AppConfig appConfig;

    @Resource
    protected FileInfoService fileInfoService;

    @Resource
    protected RedisComponent redisComponent;

    /**
     * 输出文件
     *
     * @param HttpServletResponse response
     * @param String              filePath
     * @return void
     */
    protected void readFile(HttpServletResponse response, String filePath) {
        if (!StringUtils.pathIsOk(filePath)) {
            return;
        }
        OutputStream out = null;
        FileInputStream in = null;
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return;
            }
            in = new FileInputStream(file);
            byte[] byteData = new byte[1024];
            out = response.getOutputStream();
            int len = 0;
            while ((len = in.read(byteData)) != -1) {
                out.write(byteData, 0, len);
            }
            out.flush();
        } catch (Exception e) {
            logger.error("读取文件异常", e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    logger.error("IO异常", e);
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    logger.error("IO异常", e);
                }
            }
        }
    }

    /**
     * 解决输出默认头像失败问题
     *
     * @param HttpServletResponse response
     * @return void
     */
    protected void printNoDefaultImage(HttpServletResponse response) {
        response.setHeader(CONTENT_TYPE, CONTENT_TYPE_VALUE);
        response.setStatus(HttpStatus.OK.value());
        PrintWriter writer = null;
        try {
            writer = response.getWriter();
            writer.print("请在头像目录下放置默认头像default_avatar.jpg");
            writer.close();
        } catch (Exception e) {
            logger.error("输出无默认图失败", e);
        } finally {
            writer.close();
        }
    }

    protected SessionWebUserDto getUserInfoFromSession(HttpSession session) {
        SessionWebUserDto sessionWebUserDto = (SessionWebUserDto) session.getAttribute(Constants.SESSION_KEY);
        return sessionWebUserDto;
    }

    protected SessionShareDto getSessionShareFromSession(HttpSession session, String shareId) {
        SessionShareDto sessionShareDto = (SessionShareDto)
                session.getAttribute(Constants.SESSION_SHARE_KEY + shareId);
        return sessionShareDto;
    }

    protected void getImage(HttpServletResponse response, String imageFolder, String imageName) {
        if (!StringUtils.pathIsOk(imageFolder) || !StringUtils.pathIsOk(imageName)) {
            return;
        }

        String imageSuffix = StringUtils.getFileSuffix(imageName);
        String filePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + imageFolder + "/" + imageName;
        imageSuffix = imageSuffix.replace(".", "");
        String contentType = "image/" + imageSuffix;
        response.setContentType(contentType);
        response.setHeader("Cache-Control", "max-age=2592000");
        readFile(response, filePath);
    }

    protected void getFile(HttpServletResponse response, String fileId, String userId) {
        QueryWrapper<FileInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        String filePath = null;

        if (fileId.endsWith(".ts")) {
            String[] tsArray = fileId.split("_");
            String realFileId = tsArray[0];
            wrapper.eq("file_id", realFileId);
            FileInfo fileInfo = fileInfoService.getOne(wrapper);
            if (null == fileInfo) {
                return;
            }
            String fileName = fileInfo.getFilePath();
            fileName = StringUtils.getFileNameNoSuffix(fileName) + "/" + fileId;
            filePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + fileName;
            // 设置ts文件的Content-Type
            response.setContentType("video/mp2t");
        } else {
            wrapper.eq("file_id", fileId);
            FileInfo fileInfo = fileInfoService.getOne(wrapper);
            if (null == fileInfo) {
                return;
            }
            if (FileCategoryEnums.VIDEO.getCategory().equals(fileInfo.getFileCategory())) {
                String fileNameNoSuffix = StringUtils.getFileNameNoSuffix(fileInfo.getFilePath());
                filePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + fileNameNoSuffix + "/" + Constants.M3U8_NAME;
                // 设置m3u8文件的Content-Type
                response.setContentType("application/vnd.apple.mpegurl");
            } else {
                filePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + fileInfo.getFilePath();
            }

            File file = new File(filePath);
            if (!file.exists()) {
                return;
            }
        }
        readFile(response, filePath);
    }

    protected Result<List<FileInfoVO>> getFolderInfo(String path, String userId) {
        String[] pathArray = path.split("/");
        // Use CASE WHEN for H2/MySQL cross-compatibility (H2 doesn't support FIELD())
        StringBuilder orderBy = new StringBuilder("case file_id");
        for (int i = 0; i < pathArray.length; i++) {
            orderBy.append(" when '").append(pathArray[i]).append("' then ").append(i);
        }
        orderBy.append(" else ").append(pathArray.length).append(" end");
        FileInfoQuery query = new FileInfoQuery();
        query.setUserId(userId);
        query.setFolderType(FileFolderTypeEnums.FOLDER.getType());
        query.setFileIdArray(pathArray);
        query.setOrderBy(orderBy.toString());
        List<FileInfoVO> list = FileInfoConvert.INSTANCE.convertList(fileInfoService.list(query));

        return Result.ok(list);
    }

    protected Result<String> createDownloadUrl(String fileId, String userId) {
        QueryWrapper<FileInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        wrapper.eq("file_id", fileId);
        FileInfo fileInfo = fileInfoService.getOne(wrapper);
        if (fileInfo == null) {
            throw new FastException(ErrorCode.CODE_600);
        }
        // 不能下载文件夹
        if (FileFolderTypeEnums.FOLDER.getType().equals(fileInfo.getFolderType())) {
            throw new FastException(ErrorCode.CODE_600);
        }
        // fileId正常，得到50位随机code
        String code = StringUtils.getRandomString(Constants.LENGTH_50);
        DownloadFileDto downloadFileDto = new DownloadFileDto();
        downloadFileDto.setDownloadCode(code);
        downloadFileDto.setFilePath(fileInfo.getFilePath());
        downloadFileDto.setFileName(fileInfo.getFileName());

        redisComponent.saveDownloadCode(code, downloadFileDto);

        return Result.ok(code);
    }

    protected void download(HttpServletRequest request, HttpServletResponse response, String code) throws Exception {
        DownloadFileDto downloadFileDto = redisComponent.getDownloadCode(code);
        if (downloadFileDto == null) {
            return;
        }
        String filePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE
                + "/" + downloadFileDto.getFilePath();
        String fileName = downloadFileDto.getFileName();
        response.setContentType("application/x-msdownload; charset=UTF-8");
        if (request.getHeader("User-Agent").toLowerCase().indexOf("msie") > 0) {//IE浏览器
            fileName = URLEncoder.encode(fileName, "UTF-8");
        } else {
            fileName = new String(fileName.getBytes("UTF-8"), "ISO8859-1");
        }
        response.setHeader("Content-Disposition", "attachment;filename=\"" + fileName + "\"");
        readFile(response, filePath);
    }
}
