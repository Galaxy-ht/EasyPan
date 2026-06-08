package com.easypan.controller;

import com.easypan.annotation.GlobalInterceptor;
import com.easypan.annotation.VerifyParam;
import com.easypan.config.AppConfig;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.CreateImageCode;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.dto.UserSpaceDto;
import com.easypan.entity.enums.VerifyRegexEnum;
import com.easypan.entity.po.UserInfo;
import com.easypan.exception.FastException;
import com.easypan.service.UserInfoService;
import com.easypan.utils.RedisComponent;
import com.easypan.utils.Result;
import com.easypan.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;

/**
 * 用户CRUD
 *
 * @author Tao MrHaoTao@gmail.com
 * @since 1.0.0 2023-08-16
 */
@RestController
@RequestMapping("userInfo")
//@Tag(name="用户CRUD")
@RequiredArgsConstructor
public class UserInfoController extends BaseController {

    private final UserInfoService userInfoService;

    private static final Logger logger = LoggerFactory.getLogger(UserInfoController.class);

    @Value("${demo.mode:false}")
    private boolean demoMode;

    @Resource
    private AppConfig appConfig;

    @Resource
    private RedisComponent redisComponent;

    /**
     * 根据请求类型返回验证码并存入session
     *
     * @param HttpServletResponse response
     * @param HttpSession         session
     * @param Integer             type 0:登录注册  1:邮箱验证码发送  默认0
     * @return void
     */
    @RequestMapping("/checkCode")
    public void checkCode(HttpServletResponse response, HttpSession session, Integer type) throws IOException {
        CreateImageCode vCode = new CreateImageCode(130, 38, 5, 10);
        response.setHeader("pragma", "no-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);
        response.setContentType("image/jpeg");
        String code = vCode.getCode();
        if (type == null || type == 0) {
            session.setAttribute(Constants.CHECK_CODE_KEY, code);
        } else {
            session.setAttribute(Constants.CHECK_CODE_KEY_EMAIL, code);
        }
        vCode.write(response.getOutputStream());
    }

    @RequestMapping("/register")
    @GlobalInterceptor(checkParams = true, checkLogin = false)
    public Result<String> register(HttpSession session,
                                   @VerifyParam(required = true, regex = VerifyRegexEnum.EMAIL, max = 150) String email,
                                   @VerifyParam(required = true) String nickName,
                                   @VerifyParam(required = true, regex = VerifyRegexEnum.PASSWORD, min = 8) String password,
                                   @VerifyParam(required = true) String checkCode,
                                   @VerifyParam(required = true) String emailCode) {
        try {
            if (!checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY))) {
                throw new FastException("图片验证码不正确");
            }
            userInfoService.register(email, nickName, password, emailCode);
            return Result.ok("邮件发送成功");
        } finally {
            session.removeAttribute(Constants.CHECK_CODE_KEY);
        }
    }

    @RequestMapping("/login")
    @GlobalInterceptor(checkParams = true, checkLogin = false)
    public Result<SessionWebUserDto> login(HttpSession session,
                                           @VerifyParam(required = true) String email,
                                           @VerifyParam(required = true) String password,
                                           @VerifyParam(required = true) String checkCode) {
        try {
            if (!demoMode && !checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY))) {
                throw new FastException("图片验证码不正确");
            }
            SessionWebUserDto sessionWebUserDto = userInfoService.login(email, password);
            session.setAttribute(Constants.SESSION_KEY, sessionWebUserDto);
            return Result.ok(sessionWebUserDto);
        } finally {
            session.removeAttribute(Constants.CHECK_CODE_KEY);
        }
    }


    @RequestMapping("/resetPwd")
    @GlobalInterceptor(checkParams = true, checkLogin = false)
    public Result<String> resetPwd(HttpSession session,
                                   @VerifyParam(required = true, regex = VerifyRegexEnum.EMAIL, max = 150) String email,
                                   @VerifyParam(required = true, regex = VerifyRegexEnum.PASSWORD, min = 8, max = 10) String password,
                                   @VerifyParam(required = true) String checkCode,
                                   @VerifyParam(required = true) String emailCode) {
        try {
            if (!checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY))) {
                throw new FastException("图片验证码不正确");
            }
            userInfoService.resetPwd(email, password, emailCode);
            return Result.ok("密码修改成功");
        } finally {
            session.removeAttribute(Constants.CHECK_CODE_KEY);
        }
    }

    @RequestMapping("/updatePassword")
    @GlobalInterceptor(checkParams = true)
    public Result<Object> updatePassword(HttpSession session,
                                         @VerifyParam(required = true, regex = VerifyRegexEnum.PASSWORD, min = 8, max = 18) String password) {
        SessionWebUserDto sessionWebUserDto = getUserInfoFromSession(session);
        UserInfo userInfo = userInfoService.getById(sessionWebUserDto.getUserId());
        userInfo.setPassword(StringUtils.encodeByMd5(password));
        userInfoService.updateById(userInfo);
        return Result.ok(null);
    }

    @RequestMapping("/getUserInfo")
    @GlobalInterceptor(checkParams = true)
    public Result<SessionWebUserDto> getUserInfo(HttpSession session) {
        SessionWebUserDto sessionWebUserDto = getUserInfoFromSession(session);

        return Result.ok(sessionWebUserDto);
    }

    @RequestMapping("/getUseSpace")
    @GlobalInterceptor(checkParams = true)
    public Result<UserSpaceDto> getUseSpace(HttpSession session) {
        SessionWebUserDto sessionWebUserDto = getUserInfoFromSession(session);

        UserSpaceDto userUseSpace = redisComponent.getUserUseSpace(sessionWebUserDto.getUserId());
        return Result.ok(userUseSpace);
    }

    @RequestMapping("/logout")
    public Result<Object> logout(HttpSession session) {
        session.invalidate();
        return Result.ok(null);
    }


    /**
     * 获取用户头像
     *
     * @param HttpServletResponse response
     * @param HttpSession         session
     * @param String              userId
     * @return void
     */
    @RequestMapping("/getAvatar/{userId}")
    @GlobalInterceptor(checkParams = true, checkLogin = false)
    public void getAvatar(HttpServletResponse response, HttpSession session, @VerifyParam(required = true) @PathVariable("userId") String userId) {
        String avatarFolderName = Constants.FILE_FOLDER_FILE + Constants.FILE_FOLDER_AVATAR_NAME;
        File folder = new File(appConfig.getProjectFolder() + avatarFolderName);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        String avatarPath = appConfig.getProjectFolder() + avatarFolderName + userId + Constants.AVATAR_SUFFIX;
        File file = new File(avatarPath);
        if (!file.exists()) {
            if (!new File(appConfig.getProjectFolder() + avatarFolderName + Constants.AVATAR_DEFAULT).exists()) {
                printNoDefaultImage(response);
            }
            avatarPath = appConfig.getProjectFolder() + avatarFolderName + Constants.AVATAR_DEFAULT;
        }

        response.setContentType("image/jpg");
        readFile(response, avatarPath);
    }

    @RequestMapping("/updateUserAvatar")
    @GlobalInterceptor
    public Result<Object> updateUserAvatar(HttpSession session, MultipartFile avatar) {

        SessionWebUserDto webUserDto = getUserInfoFromSession(session);
        // 得到头像文件夹
        String baseFolder = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE;
        File targetFileFolder = new File(baseFolder + Constants.FILE_FOLDER_AVATAR_NAME);
        // 如果不存在就创建
        if (!targetFileFolder.exists()) {
            targetFileFolder.mkdirs();
        }
        // 得到新头像绝对路径
        File targetFile = new File(targetFileFolder.getPath() + "/" + webUserDto.getUserId() + Constants.AVATAR_SUFFIX);
        try {
            // 输出
            avatar.transferTo(targetFile);
        } catch (Exception e) {
            logger.error("上传头像失败", e);
        }

        // 同时将数据库中qq头像设为空
        UserInfo userInfo = userInfoService.getById(webUserDto.getUserId());
        userInfo.setQqAvatar("");
        userInfoService.updateById(userInfo);
        webUserDto.setAvatar(null);
        //更新session
        session.setAttribute(Constants.SESSION_KEY, webUserDto);
        return Result.ok(null);
    }

}