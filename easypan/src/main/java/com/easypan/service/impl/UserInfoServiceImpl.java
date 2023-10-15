package com.easypan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.easypan.convert.UserInfoConvert;
import com.easypan.config.AppConfig;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.dto.UserSpaceDto;
import com.easypan.entity.enums.UserStatusEnum;
import com.easypan.entity.po.UserInfo;
import com.easypan.entity.vo.UserInfoVO;
import com.easypan.exception.FastException;
import com.easypan.mappers.FileInfoDao;
import com.easypan.mappers.UserInfoDao;
import com.easypan.page.PageResult;
import com.easypan.query.UserInfoQuery;
import com.easypan.service.EmailCodeService;
import com.easypan.service.UserInfoService;
import com.easypan.utils.RedisComponent;
import com.easypan.utils.StringUtils;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * 用户CRUD
 *
 * @author Tao MrHaoTao@gmail.com
 * @since 1.0.0 2023-08-16
 */
@Service
@AllArgsConstructor
public class UserInfoServiceImpl extends BaseServiceImpl<UserInfoDao, UserInfo> implements UserInfoService {

    @Resource
    private EmailCodeService emailCodeService;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private AppConfig appConfig;

    @Resource
    private FileInfoDao fileInfoDao;

    @Override
    public PageResult<UserInfoVO> page(UserInfoQuery query) {
        IPage<UserInfo> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(page.getTotal(), page.getSize(), page.getCurrent(),page.getPages(), UserInfoConvert.INSTANCE.convertList(page.getRecords()));
    }

    private QueryWrapper<UserInfo> getWrapper(UserInfoQuery query) {
        QueryWrapper<UserInfo> wrapper = new QueryWrapper<>();
        wrapper.like(StringUtils.isNotEmpty(query.getNickNameFuzzy()), "nick_name", query.getNickNameFuzzy());
        wrapper.eq(query.getStatus() != null, "status", query.getStatus());
        wrapper.orderByDesc("join_time");
        return wrapper;
    }

    @Override
    public void save(UserInfoVO vo) {
        UserInfo entity = UserInfoConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);
    }

    @Override
    public void update(UserInfoVO vo) {
        UserInfo entity = UserInfoConvert.INSTANCE.convert(vo);

        updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(String email, String nickName, String password, String emailCode) {
        UserInfo userInfoEmail = this.getOne(new QueryWrapper<UserInfo>().eq("email", email));
        if (null != userInfoEmail) {
            throw new FastException("用户邮箱已注册");
        }

        UserInfo userInfoNickName = this.getOne(new QueryWrapper<UserInfo>().eq("nick_name", nickName));
        if (null != userInfoNickName) {
            throw new FastException("用户名已存在");
        }

        //校验邮箱验证码
        emailCodeService.checkCode(email, emailCode);

        String userId = StringUtils.getRandomNumber(Constants.LENGTH_10);
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setEmail(email);
        userInfo.setNickName(nickName);
        userInfo.setPassword(StringUtils.encodeByMd5(password));
        userInfo.setJoinTime(new Date());
        userInfo.setStatus(UserStatusEnum.ENABLE.getStatus());
        userInfo.setUseSpace(0L);
        userInfo.setTotalSpace(redisComponent.getSysSettingDto().getUserInitUseSpace() * Constants.MB);
        this.save(userInfo);

    }

    @Override
    public SessionWebUserDto login(String email, String password) {

        UserInfo userInfo = this.getOne(new QueryWrapper<UserInfo>().eq("email", email));

        if (null == userInfo || !userInfo.getPassword().equals(password)) {
            throw new FastException("账号或密码错误");
        }

        if (UserStatusEnum.DISABLE.getStatus().equals(userInfo.getStatus())) {
            throw new FastException("账号已被禁用");
        }

        UserInfo updateInfo = new UserInfo();
        updateInfo.setLastLoginTime(new Date());
        this.updateById(updateInfo);

        SessionWebUserDto sessionWebUserDto = new SessionWebUserDto();
        sessionWebUserDto.setNickName(userInfo.getNickName());
        sessionWebUserDto.setUserId(userInfo.getUserId());
        sessionWebUserDto.setIsAdmin(ArrayUtils.contains(appConfig.getAdminEmails().split(","), email));
        //用户空间
        UserSpaceDto userSpaceDto = new UserSpaceDto();
        userSpaceDto.setUseSpace(fileInfoDao.selectUseSpace(userInfo.getUserId()));
        userSpaceDto.setTotalSpace(userInfo.getTotalSpace());
        redisComponent.saveUserUseSpace(userInfo.getUserId(), userSpaceDto);

        return sessionWebUserDto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPwd(String email, String password, String emailCode) {

        UserInfo userInfo = this.getOne(new QueryWrapper<UserInfo>().eq("email", email));

        if (null == userInfo) {
            throw new FastException("该账号尚未注册");
        }

        emailCodeService.checkCode(email, emailCode);

        userInfo.setPassword(StringUtils.encodeByMd5(password));
        this.updateById(userInfo);
    }

}