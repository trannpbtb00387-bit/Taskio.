package com.poly.config;

import java.nio.file.Path;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AvatarResourceConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String avatarDirectory = Path.of("uploads", "avatars").toAbsolutePath().normalize().toUri().toString();
        if (!avatarDirectory.endsWith("/")) avatarDirectory += "/";
        registry.addResourceHandler("/uploads/avatars/**").addResourceLocations(avatarDirectory);
    }
}
