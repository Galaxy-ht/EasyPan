package com.easypan.utils;

import com.easypan.cache.CaffeineCache;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.DownloadFileDto;
import com.easypan.entity.dto.SysSettingDto;
import com.easypan.entity.dto.UserSpaceDto;
import com.easypan.mappers.FileInfoDao;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class RedisComponent {

    @Resource
    private CaffeineCache caffeineCache;

    @Resource
    private FileInfoDao fileInfoDao;

    public SysSettingDto getSysSettingDto() {
        return caffeineCache.getSysSetting();
    }

    public void saveUserUseSpace(String userId, UserSpaceDto userSpaceDto) {
        caffeineCache.saveUserSpace(userId, userSpaceDto);
    }

    public UserSpaceDto getUserUseSpace(String userId) {
        UserSpaceDto userSpaceDto = caffeineCache.getUserSpace(userId);
        if (userSpaceDto == null) {
            userSpaceDto = new UserSpaceDto();
            userSpaceDto.setUseSpace(fileInfoDao.selectUseSpace(userId));
            userSpaceDto.setTotalSpace(getSysSettingDto().getUserInitUseSpace() * Constants.MB);
            saveUserUseSpace(userId, userSpaceDto);
        }
        return userSpaceDto;
    }

    public Long getFileTempSize(String userId, String fileId) {
        return caffeineCache.getFileTempSize(Constants.REDIS_KEY_USER_FILE_TEMP_SIZE + userId + fileId);
    }

    public void saveFileTempSize(String userId, String fileId, Long fileSize) {
        Long currentSize = getFileTempSize(userId, fileId);
        caffeineCache.saveFileTempSize(Constants.REDIS_KEY_USER_FILE_TEMP_SIZE + userId + fileId, currentSize + fileSize);
    }

    public void saveDownloadCode(String code, DownloadFileDto downloadFileDto) {
        caffeineCache.saveDownloadCode(code, downloadFileDto);
    }

    public void saveSysSettingDto(SysSettingDto sysSettingDto) {
        caffeineCache.saveSysSetting(sysSettingDto);
    }

    public DownloadFileDto getDownloadCode(String code) {
        return caffeineCache.getDownloadCode(code);
    }
}