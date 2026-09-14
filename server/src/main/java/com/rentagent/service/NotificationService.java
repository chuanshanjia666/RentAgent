package com.rentagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rentagent.common.BizException;
import com.rentagent.entity.Notification;
import com.rentagent.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 站内通知（FR-21）：关键事件统一经此触达 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper mapper;

    public void send(Long userId, int type, String title, String content, String refType, Long refId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setRefType(refType);
        n.setRefId(refId);
        n.setIsRead(0);
        mapper.insert(n);
    }

    public Page<Notification> page(long uid, boolean onlyUnread, long page, long size) {
        return mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, uid)
                .eq(onlyUnread, Notification::getIsRead, 0)
                .orderByDesc(Notification::getId));
    }

    public long unreadCount(long uid) {
        return mapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, uid).eq(Notification::getIsRead, 0));
    }

    public void markRead(long uid, long id) {
        Notification n = mapper.selectById(id);
        if (n == null || n.getUserId() != uid) {
            throw new BizException(1007, "通知不存在");
        }
        n.setIsRead(1);
        mapper.updateById(n);
    }
}
