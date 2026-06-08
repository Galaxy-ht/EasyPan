package com.easypan.controller;

import com.easypan.annotation.GlobalInterceptor;
import com.easypan.annotation.VerifyParam;
import com.easypan.entity.FileShare;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.vo.FileShareVO;
import com.easypan.page.PageResult;
import com.easypan.query.FileShareQuery;
import com.easypan.service.FileShareService;
import com.easypan.utils.Result;
import com.easypan.utils.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.Date;

/**
 * @author Tao
 * @since 2023/10/14
 */

@RestController
@RequestMapping("/share")
public class FileShareController extends BaseController {

    @Resource
    private FileShareService fileShareService;


    @RequestMapping("/loadShareList")
    @GlobalInterceptor(checkParams = true)
    public Result<PageResult<FileShareVO>> loadShareList(HttpSession session, FileShareQuery query) {
        query.setOrderBy("share_time desc");
        SessionWebUserDto userDto = getUserInfoFromSession(session);
        query.setUserId(userDto.getUserId());
        query.setQueryFileName(true);
        PageResult<FileShareVO> resultVO = fileShareService.page(query);
        return Result.ok(resultVO);
    }

    @RequestMapping("/shareFile")
    @GlobalInterceptor(checkParams = true)
    public Result<FileShare> shareFile(HttpSession session,
                                       @VerifyParam(required = true) String fileId,
                                       @VerifyParam(required = true) Integer validType,
                                       Integer codeType,
                                       String code) {
        SessionWebUserDto userDto = getUserInfoFromSession(session);
        FileShare share = new FileShare();
        Date curDate = new Date();
        long time = curDate.getTime();
        long expireTime = 0L;
        if (validType == 0) {
            expireTime = time + Constants.REDIS_KEY_EXPIRES_DAY * 1000L;
        } else if (validType == 1) {
            expireTime = time + Constants.REDIS_KEY_EXPIRES_DAY * 7 * 1000L;
        } else if (validType == 2) {
            expireTime = time + Constants.REDIS_KEY_EXPIRES_DAY * 30 * 1000L;
        }
        if (validType == 3) {
            share.setExpireTime(null);
        } else {
            share.setExpireTime(new Date(expireTime));
        }
        share.setFileId(fileId);
        share.setValidType(validType);
        share.setCode(code);
        share.setUserId(userDto.getUserId());
        if (codeType == 1) {
            share.setCode(StringUtils.getRandomString(Constants.LENGTH_5));
        }
        share.setShareTime(curDate);
        fileShareService.save(share);
        return Result.ok(share);
    }

    @RequestMapping("/cancelShare")
    @GlobalInterceptor(checkParams = true)
    public Result<String> cancelShare(HttpSession session,
                                      @VerifyParam(required = true) String shareIds) {
        SessionWebUserDto userDto = getUserInfoFromSession(session);
        fileShareService.deleteFileShareBatch(shareIds.split(","), userDto.getUserId());
        return Result.ok();
    }
}
