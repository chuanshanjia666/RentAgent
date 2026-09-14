package com.rentagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 实名认证表：身份证号 AES 密文 + SHA-256 哈希（NFR-04）；status 0待审核 1通过 2驳回 */
@Data
@TableName("realname_auth")
public class RealnameAuth {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String realName;

    private String idCardNoEnc;

    private String idCardHash;

    private Integer status;

    private String rejectReason;

    private Long auditBy;

    private LocalDateTime auditTime;

    private LocalDateTime createdAt;
}
