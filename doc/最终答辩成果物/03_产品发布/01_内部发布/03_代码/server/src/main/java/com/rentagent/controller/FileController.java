package com.rentagent.controller;

import com.rentagent.common.R;
import com.rentagent.security.RequireRole;
import com.rentagent.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** 图片上传（FR-05/08） */
@Tag(name = "file", description = "文件上传")
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "上传房源图片")
    @PostMapping("/upload")
    @RequireRole({2, 3})
    public R<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String url = fileService.uploadImage(file);
        return R.ok(Map.of("url", url));
    }
}
