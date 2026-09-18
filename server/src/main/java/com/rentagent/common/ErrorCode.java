package com.rentagent.common;

import lombok.Getter;

/** 业务错误码分段：1xxx 用户 / 2xxx 房源 / 3xxx 交易 / 4xxx AI / 5xxx 系统 */
@Getter
public enum ErrorCode {
    // 1xxx 用户与权限
    PARAM_INVALID(1000, "参数校验失败"),
    ACCOUNT_EXISTS(1001, "该手机号已注册，请直接登录"),
    /**
     * 1002 是"凭据与登录态"分段的公共码，具体语义由 message 区分（接口契约测试 UT-API-03 的约定）：
     * 登录失败 / 未登录 / 原密码不正确共用，前端不必为同类问题写三套分支。
     */
    LOGIN_FAILED(1002, "账号或密码错误"),
    NOT_LOGIN(1002, "请先登录"),
    WRONG_PASSWORD(1002, "原密码不正确"),
    ACCOUNT_LOCKED(1003, "密码错误次数过多，账号已锁定 10 分钟"),
    ACCOUNT_DISABLED(1004, "该账号已被禁用"),
    CAPTCHA_INVALID(1005, "验证码错误或已过期"),
    /** 1006 是"目标资源不存在"的公共码，message 说明具体对象（用户 / 举报等） */
    USER_NOT_FOUND(1006, "用户不存在"),
    REPORT_NOT_FOUND(1006, "举报不存在"),
    FORBIDDEN(1007, "无权限执行该操作"),
    REALNAME_REQUIRED(1008, "请先完成实名认证"),
    REALNAME_PENDING(1009, "实名认证审核中，请稍候"),

    // 2xxx 房源
    HOUSE_NOT_FOUND(2001, "房源不存在或已删除"),
    HOUSE_STATUS_INVALID(2002, "房源当前状态不允许该操作"),
    FILE_UPLOAD_FAILED(2003, "图片上传失败"),

    // 3xxx 交易
    APPOINTMENT_CONFLICT(3001, "该时段已被预约，请换个时间"),
    APPOINTMENT_STATUS_INVALID(3002, "预约当前状态不允许该操作"),
    CONTRACT_STATUS_INVALID(3003, "合同当前状态不允许该操作"),
    ORDER_NOT_FOUND(3004, "订单不存在"),
    REVIEW_NOT_ALLOWED(3005, "仅完成合同后可评价，且一单一评"),

    // 4xxx AI
    AI_UNAVAILABLE(4001, "智能助手繁忙，请稍后再试"),
    AI_SESSION_NOT_FOUND(4002, "会话不存在"),

    // 5xxx 系统
    SYSTEM_ERROR(5000, "系统繁忙，请稍后再试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
