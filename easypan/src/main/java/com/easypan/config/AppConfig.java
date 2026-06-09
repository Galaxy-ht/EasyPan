package com.easypan.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.File;

/**
 * @author Tao
 * @since 2023/08/20
 */
@Configuration
@Data
public class AppConfig {

    @Value("${admin.emails}")
    public String adminEmails;

    @Value("${spring.mail.username}")
    private String sendUsername;

    @Value("${project.folder}")
    private String projectFolder;

    @Value("${mirror.storage.bak.path:./storage_bak}")
    private String storageBakPath;

    @PostConstruct
    public void init() {
        projectFolder = resolveToAbsolute(projectFolder);
        storageBakPath = resolveToAbsolute(storageBakPath);
    }

    private String resolveToAbsolute(String path) {
        File dir = new File(path);
        if (!dir.isAbsolute()) {
            return dir.getAbsolutePath();
        }
        return path;
    }
}
