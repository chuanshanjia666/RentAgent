package com.rentagent.controller;

import com.rentagent.common.R;
import com.rentagent.dto.PageVO;
import com.rentagent.entity.Notification;
import com.rentagent.security.UserContext;
import com.rentagent.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 站内消息（FR-21） */
@Tag(name = "notification", description = "站内消息")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "通知列表")
    @GetMapping
    public R<PageVO<Notification>> list(@RequestParam(defaultValue = "false") boolean onlyUnread,
                                        @RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "10") long size) {
        return R.ok(PageVO.of(notificationService.page(UserContext.userId(), onlyUnread, page, size)));
    }

    @Operation(summary = "未读数（前端轮询角标）")
    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        return R.ok(notificationService.unreadCount(UserContext.userId()));
    }

    @Operation(summary = "标记已读")
    @PatchMapping("/{id}/read")
    public R<Void> read(@PathVariable long id) {
        notificationService.markRead(UserContext.userId(), id);
        return R.ok();
    }
}
