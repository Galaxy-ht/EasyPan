package com.easypan.cache;

import com.easypan.entity.dto.DownloadFileDto;
import com.easypan.entity.dto.SysSettingDto;
import com.easypan.entity.dto.UserSpaceDto;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

@Component
public class CaffeineCache {

    private Cache<String, SysSettingDto> sysSettingCache;
    private Cache<String, UserSpaceDto> userSpaceCache;
    private Cache<String, Long> fileTempSizeCache;
    private Cache<String, DownloadFileDto> downloadCodeCache;

    @PostConstruct
    public void init() {
        sysSettingCache = Caffeine.newBuilder()
                .maximumSize(1)
                .build();

        userSpaceCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.DAYS)
                .build();

        fileTempSizeCache = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(3, TimeUnit.HOURS)
                .build();

        downloadCodeCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    public SysSettingDto getSysSetting() {
        SysSettingDto dto = sysSettingCache.getIfPresent("sysSetting");
        if (dto == null) {
            dto = new SysSettingDto();
            sysSettingCache.put("sysSetting", dto);
        }
        return dto;
    }

    public void saveSysSetting(SysSettingDto dto) {
        sysSettingCache.put("sysSetting", dto);
    }

    public UserSpaceDto getUserSpace(String userId) {
        return userSpaceCache.getIfPresent(userId);
    }

    public void saveUserSpace(String userId, UserSpaceDto dto) {
        userSpaceCache.put(userId, dto);
    }

    public void invalidateUserSpace(String userId) {
        userSpaceCache.invalidate(userId);
    }

    public Long getFileTempSize(String key) {
        Long size = fileTempSizeCache.getIfPresent(key);
        return size == null ? 0L : size;
    }

    public void saveFileTempSize(String key, Long size) {
        fileTempSizeCache.put(key, size);
    }

    public void saveDownloadCode(String code, DownloadFileDto dto) {
        downloadCodeCache.put(code, dto);
    }

    public DownloadFileDto getDownloadCode(String code) {
        return downloadCodeCache.getIfPresent(code);
    }
}