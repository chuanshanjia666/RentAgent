package com.rentagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 知识库切片表：RAG 语义检索单位；vector_ref 在向量检索升级后填充 */
@Data
@TableName("kb_chunk")
public class KbChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Integer seq;

    private String content;

    private Integer tokenCount;

    private String vectorRef;

    private LocalDateTime createdAt;
}
