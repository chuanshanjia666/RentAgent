package com.rentagent.controller;

import com.rentagent.common.BizException;
import com.rentagent.common.ErrorCode;
import com.rentagent.common.R;
import com.rentagent.security.RequireRole;
import com.rentagent.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 图片上传（FR-05/08） */
@Tag(name = "file", description = "文件上传")
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "webp");

    @Value("${rentagent.upload-dir}")
    private String uploadDir;

    @Operation(summary = "上传房源图片")
    @PostMapping("/upload")
    @RequireRole({2, 3})
    public R<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
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
            return R.ok(Map.of("url", "/uploads/" + name));
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
