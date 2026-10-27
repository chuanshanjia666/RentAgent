package com.rentagent.service;

import com.rentagent.common.BizException;
import com.rentagent.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/** 文件上传服务（FR-05/08） */
@Service
public class FileService {

    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "webp");

    private final String uploadDir;

    public FileService(@Value("${rentagent.upload-dir}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public String getUploadDir() {
        return uploadDir;
    }

    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
        }
        String ext = extOf(file.getOriginalFilename());
        if (!ALLOWED.contains(ext)) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED.getCode(), "仅支持 jpg/png/webp 图片");
        }
        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String name = UUID.randomUUID() + "." + ext;
            file.transferTo(dir.resolve(name).toFile());
            return "/uploads/" + name;
        } catch (Exception e) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    private String extOf(String name) {
        if (name == null || !name.contains(".")) {
            return "";
        }
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase();
    }
}

