package com.easypan.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.easypan.annotation.GlobalInterceptor;
import com.easypan.annotation.VerifyParam;
import com.easypan.config.AppConfig;
import com.easypan.convert.FileInfoConvert;
import com.easypan.convert.UserInfoConvert;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.SysSettingDto;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.enums.FileDelFlagEnums;
import com.easypan.entity.enums.FileFolderTypeEnums;
import com.easypan.entity.po.FileInfo;
import com.easypan.entity.po.UserInfo;
import com.easypan.entity.vo.FileInfoVO;
import com.easypan.entity.vo.UserInfoVO;
import com.easypan.page.PageResult;
import com.easypan.query.FileInfoQuery;
import com.easypan.query.UserInfoQuery;
import com.easypan.service.FileInfoService;
import com.easypan.service.UserInfoService;
import com.easypan.utils.RedisComponent;
import com.easypan.utils.Result;
import com.easypan.utils.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理员控制器
 *
 * @author Tao
 */
@RestController
@RequestMapping("admin")
public class AdminController extends BaseController {

    @Value("${demo.mode:false}")
    private boolean demoMode;

    @Resource
    private UserInfoService userInfoService;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private AppConfig appConfig;

    /**
     * 加载用户列表
     */
    @RequestMapping("loadUserList")
    @GlobalInterceptor(checkAdmin = true)
    public Result<PageResult<UserInfoVO>> loadUserList(HttpSession session, UserInfoQuery query) {
        if (demoMode) {
            return Result.ok(new PageResult<>(0L, 0, 0, 0, new ArrayList<>()));
        }
        PageResult<UserInfoVO> page = userInfoService.page(query);
        return Result.ok(page);
    }

    /**
     * 更新用户状态
     */
    @RequestMapping("updateUserStatus")
    @GlobalInterceptor(checkAdmin = true, checkParams = true)
    public Result<String> updateUserStatus(@VerifyParam(required = true) String userId,
                                           @VerifyParam(required = true) Integer status) {
        if (demoMode) {
            return Result.error("演示环境不支持此操作");
        }
        UserInfo userInfo = userInfoService.getById(userId);
        if (userInfo == null) {
            return Result.error("用户不存在");
        }
        userInfo.setStatus(status);
        userInfoService.updateById(userInfo);
        return Result.ok();
    }

    /**
     * 更新用户空间
     */
    @RequestMapping("updateUserSpace")
    @GlobalInterceptor(checkAdmin = true, checkParams = true)
    public Result<String> updateUserSpace(@VerifyParam(required = true) String userId,
                                          @VerifyParam(required = true) String changeSpace) {
        if (demoMode) {
            return Result.error("演示环境不支持此操作");
        }
        UserInfo userInfo = userInfoService.getById(userId);
        if (userInfo == null) {
            return Result.error("用户不存在");
        }
        Long newTotalSpace = Long.parseLong(changeSpace) * Constants.MB;
        userInfo.setTotalSpace(newTotalSpace);
        userInfoService.updateById(userInfo);
        redisComponent.saveUserUseSpace(userId, redisComponent.getUserUseSpace(userId));
        return Result.ok();
    }

    /**
     * 加载文件列表（管理员）
     */
    @RequestMapping("loadFileList")
    @GlobalInterceptor(checkAdmin = true)
    public Result<PageResult<FileInfoVO>> loadFileList(HttpSession session, FileInfoQuery query) {
        query.setDelFlag(FileDelFlagEnums.USING.getFlag());
        query.setOrderBy("last_update_time desc");
        if (StringUtils.isNotEmpty(query.getFileNameFuzzy())) {
            query.setFileNameFuzzy(query.getFileNameFuzzy());
        }
        PageResult<FileInfoVO> page = fileInfoService.page(query);
        if (page.getList() != null) {
            for (FileInfoVO vo : page.getList()) {
                if (StringUtils.isNotEmpty(vo.getUserId())) {
                    UserInfo userInfo = userInfoService.getById(vo.getUserId());
                    if (userInfo != null) {
                        vo.setNickName(userInfo.getNickName());
                    }
                }
            }
        }
        return Result.ok(page);
    }

    /**
     * 删除文件（管理员）
     */
    @RequestMapping("delFile")
    @GlobalInterceptor(checkAdmin = true, checkParams = true)
    public Result<String> delFile(@VerifyParam(required = true) String fileIdAndUserIds) {
        if (demoMode) {
            return Result.error("演示环境不支持此操作");
        }
        String[] idArray = fileIdAndUserIds.split(",");
        for (String idPair : idArray) {
            String[] parts = idPair.split("_");
            if (parts.length == 2) {
                String userId = parts[0];
                String fileId = parts[1];
                fileInfoService.removeFile2RecycleBatch(userId, fileId);
            }
        }
        return Result.ok();
    }

    /**
     * 获取文件夹信息（管理员）
     */
    @RequestMapping("getFolderInfo")
    @GlobalInterceptor(checkAdmin = true, checkParams = true)
    public Result<List<FileInfoVO>> getFolderInfo(@VerifyParam(required = true) String path) {
        String[] pathArray = path.split("/");
        String orderBy = "field(file_id,\"" + StringUtils.join(pathArray, "\",\"") + "\")";
        FileInfoQuery query = new FileInfoQuery();
        query.setFolderType(FileFolderTypeEnums.FOLDER.getType());
        query.setFileIdArray(pathArray);
        query.setOrderBy(orderBy);
        List<FileInfoVO> list = FileInfoConvert.INSTANCE.convertList(fileInfoService.list(query));
        return Result.ok(list);
    }

    /**
     * 获取文件（管理员预览）
     */
    @RequestMapping("getFile/{userId}/{fileId}")
    @GlobalInterceptor(checkAdmin = true, checkParams = true)
    public void getFile(HttpServletResponse response,
                        @PathVariable("userId") String userId,
                        @PathVariable("fileId") String fileId) {
        super.getFile(response, fileId, userId);
    }

    /**
     * 获取视频信息（管理员预览）
     */
    @RequestMapping("ts/getVideoInfo/{userId}/{fileId}")
    @GlobalInterceptor(checkAdmin = true, checkParams = true)
    public void getVideoInfo(HttpServletResponse response,
                             @PathVariable("userId") String userId,
                             @PathVariable("fileId") String fileId) {
        super.getFile(response, fileId, userId);
    }

    /**
     * 创建下载链接（管理员）
     */
    @RequestMapping("createDownloadUrl/{userId}/{fileId}")
    @GlobalInterceptor(checkAdmin = true, checkParams = true)
    public Result<String> createDownloadUrl(@PathVariable("userId") String userId,
                                            @PathVariable("fileId") String fileId) {
        return super.createDownloadUrl(fileId, userId);
    }

    /**
     * 下载文件（管理员）
     */
    @RequestMapping("/download/{code}")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public void download(HttpServletRequest request,
                         HttpServletResponse response,
                         @PathVariable("code") @VerifyParam(required = true) String code) throws Exception {
        super.download(request, response, code);
    }

    /**
     * 获取系统设置
     */
    @RequestMapping("getSysSettings")
    @GlobalInterceptor(checkAdmin = true)
    public Result<SysSettingDto> getSysSettings() {
        SysSettingDto sysSettingDto = redisComponent.getSysSettingDto();
        return Result.ok(sysSettingDto);
    }

    /**
     * 保存系统设置
     */
    @RequestMapping("saveSysSettings")
    @GlobalInterceptor(checkAdmin = true, checkParams = true)
    public Result<String> saveSysSettings(SysSettingDto sysSettingDto) {
        if (demoMode) {
            return Result.error("演示环境不支持此操作");
        }
        redisComponent.saveSysSettingDto(sysSettingDto);
        return Result.ok();
    }
}