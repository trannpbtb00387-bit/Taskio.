package com.poly.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.poly.exception.BusinessException;

@Service
public class AvatarStorageService {
    private static final long MAX_SIZE = 2 * 1024 * 1024;
    private final Path uploadDirectory = Path.of("uploads", "avatars").toAbsolutePath().normalize();

    public String store(MultipartFile image) {
        if (image == null || image.isEmpty()) return null;
        if (image.getSize() > MAX_SIZE) throw new BusinessException("Ảnh đại diện tối đa 2 MB.");

        String extension = switch (String.valueOf(image.getContentType()).toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new BusinessException("Chỉ chấp nhận ảnh JPG, PNG hoặc WEBP.");
        };

        String filename = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(uploadDirectory);
            Path destination = uploadDirectory.resolve(filename).normalize();
            if (!destination.getParent().equals(uploadDirectory)) throw new BusinessException("Đường dẫn ảnh không hợp lệ.");
            image.transferTo(destination);
            return "/uploads/avatars/" + filename;
        } catch (IOException ex) {
            throw new BusinessException("Không thể lưu ảnh đại diện. Vui lòng thử lại.");
        }
    }

    public void delete(String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl) || !avatarUrl.startsWith("/uploads/avatars/")) return;
        String filename = avatarUrl.substring("/uploads/avatars/".length());
        Path target = uploadDirectory.resolve(filename).normalize();
        if (!target.getParent().equals(uploadDirectory)) return;
        try { Files.deleteIfExists(target); } catch (IOException ignored) { }
    }
}
