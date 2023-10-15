package com.easypan.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

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
}
