-- =====================================================================
-- RentAgent 业务库建表脚本（MySQL 8 / InnoDB / utf8mb4）
-- 与《数据库设计简介》v1.0 第四章数据字典一致，共 18 张表
-- =====================================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username    VARCHAR(50)  NOT NULL COMMENT '账号',
  password    VARCHAR(100) NOT NULL COMMENT 'BCrypt密文',
  phone       VARCHAR(20)  NULL COMMENT '手机号',
  email       VARCHAR(100) NULL,
  nickname    VARCHAR(50)  NULL,
  avatar_url  VARCHAR(255) NULL,
  role        TINYINT      NOT NULL DEFAULT 1 COMMENT '1租客 2房东 3管理员',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1正常',
  deleted     TINYINT      NOT NULL DEFAULT 0,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  UNIQUE KEY uk_phone (phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

CREATE TABLE IF NOT EXISTS realname_auth (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id        BIGINT UNSIGNED NOT NULL COMMENT '认证人',
  real_name      VARCHAR(50)  NOT NULL,
  id_card_no_enc VARCHAR(255) NOT NULL COMMENT '身份证号AES密文',
  id_card_hash   CHAR(64)     NOT NULL COMMENT '身份证号SHA-256(比对用)',
  status         TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审核 1通过 2驳回',
  reject_reason  VARCHAR(200) NULL,
  audit_by       BIGINT UNSIGNED NULL,
  audit_time     DATETIME     NULL,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_created (user_id, created_at),
  CONSTRAINT fk_realname_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '实名认证表';

CREATE TABLE IF NOT EXISTS notification (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id    BIGINT UNSIGNED NOT NULL COMMENT '接收人',
  type       TINYINT     NOT NULL COMMENT '1预约 2审核 3签约 4账单 5举报 9系统',
  title      VARCHAR(100) NOT NULL,
  content    VARCHAR(500) NOT NULL,
  ref_type   VARCHAR(30)  NULL,
  ref_id     BIGINT UNSIGNED NULL,
  is_read    TINYINT     NOT NULL DEFAULT 0,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_read (user_id, is_read, created_at),
  CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内通知表';

CREATE TABLE IF NOT EXISTS house (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  landlord_id   BIGINT UNSIGNED NOT NULL,
  title         VARCHAR(100) NOT NULL,
  community     VARCHAR(100) NOT NULL COMMENT '小区',
  city          VARCHAR(50)  NOT NULL,
  district      VARCHAR(50)  NOT NULL COMMENT '行政区',
  address       VARCHAR(200) NOT NULL,
  layout        VARCHAR(20)  NOT NULL COMMENT '户型 如2室1厅',
  area          DECIMAL(6,2) NOT NULL,
  orientation   VARCHAR(10)  NULL,
  floor_desc    VARCHAR(20)  NULL,
  rent          DECIMAL(10,2) NOT NULL COMMENT '月租金',
  deposit_type  VARCHAR(20)  NOT NULL COMMENT '押付方式',
  facilities    JSON         NULL COMMENT '设施清单',
  description   VARCHAR(2000) NULL,
  cover_url     VARCHAR(255) NULL,
  lng           DECIMAL(10,7) NOT NULL,
  lat           DECIMAL(10,7) NOT NULL,
  view_count    INT UNSIGNED NOT NULL DEFAULT 0,
  avg_score     DECIMAL(3,1) NULL,
  status        TINYINT     NOT NULL DEFAULT 0 COMMENT '0待审核 1已通过 2已驳回 3已上架 4已下架 5已出租',
  reject_reason VARCHAR(200) NULL,
  deleted       TINYINT     NOT NULL DEFAULT 0,
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_status_district_rent (status, district, rent),
  KEY idx_status_layout (status, layout),
  KEY idx_landlord (landlord_id),
  KEY idx_community (community),
  KEY idx_lnglat (lng, lat),
  CONSTRAINT fk_house_landlord FOREIGN KEY (landlord_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '房源表';

CREATE TABLE IF NOT EXISTS house_image (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  house_id   BIGINT UNSIGNED NOT NULL,
  url        VARCHAR(255) NOT NULL,
  sort       TINYINT     NOT NULL DEFAULT 0,
  is_cover   TINYINT     NOT NULL DEFAULT 0,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_house (house_id),
  CONSTRAINT fk_image_house FOREIGN KEY (house_id) REFERENCES house (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '房源图片表';

CREATE TABLE IF NOT EXISTS favorite (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id    BIGINT UNSIGNED NOT NULL,
  house_id   BIGINT UNSIGNED NOT NULL,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_house (user_id, house_id),
  CONSTRAINT fk_fav_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
  CONSTRAINT fk_fav_house FOREIGN KEY (house_id) REFERENCES house (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '房源收藏表';

CREATE TABLE IF NOT EXISTS viewing_appointment (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  house_id         BIGINT UNSIGNED NOT NULL,
  tenant_id        BIGINT UNSIGNED NOT NULL,
  landlord_id      BIGINT UNSIGNED NOT NULL COMMENT '查询冗余列,源于房源',
  appointment_time DATETIME    NOT NULL COMMENT '预约时段(起点)',
  status           TINYINT     NOT NULL DEFAULT 0 COMMENT '0待确认 1已确认 2已拒绝 3已完成 4已取消',
  reject_reason    VARCHAR(200) NULL,
  remark           VARCHAR(200) NULL,
  created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_house_time (house_id, appointment_time),
  KEY idx_tenant (tenant_id),
  KEY idx_landlord (landlord_id),
  CONSTRAINT fk_appt_house FOREIGN KEY (house_id) REFERENCES house (id),
  CONSTRAINT fk_appt_tenant FOREIGN KEY (tenant_id) REFERENCES sys_user (id),
  CONSTRAINT fk_appt_landlord FOREIGN KEY (landlord_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '看房预约表';

CREATE TABLE IF NOT EXISTS contract (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  house_id           BIGINT UNSIGNED NOT NULL,
  tenant_id          BIGINT UNSIGNED NOT NULL,
  landlord_id        BIGINT UNSIGNED NOT NULL,
  clauses            JSON NOT NULL COMMENT '模板渲染后的条款集合',
  risk_flags         JSON NULL COMMENT 'AI解读风险条款标注',
  start_date         DATE NOT NULL,
  end_date           DATE NOT NULL,
  monthly_rent       DECIMAL(10,2) NOT NULL,
  deposit            DECIMAL(10,2) NOT NULL,
  status             TINYINT NOT NULL DEFAULT 0 COMMENT '0待租客确认 1待房东确认 2已生效 3已退租 4已作废',
  signed_tenant_at   DATETIME NULL,
  signed_landlord_at DATETIME NULL,
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_tenant_status (tenant_id, status),
  KEY idx_landlord_status (landlord_id, status),
  CONSTRAINT fk_contract_house FOREIGN KEY (house_id) REFERENCES house (id),
  CONSTRAINT fk_contract_tenant FOREIGN KEY (tenant_id) REFERENCES sys_user (id),
  CONSTRAINT fk_contract_landlord FOREIGN KEY (landlord_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '电子合同表';

CREATE TABLE IF NOT EXISTS lease_order (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  contract_id  BIGINT UNSIGNED NOT NULL,
  house_id     BIGINT UNSIGNED NOT NULL,
  tenant_id    BIGINT UNSIGNED NOT NULL,
  landlord_id  BIGINT UNSIGNED NOT NULL,
  start_date   DATE NOT NULL,
  end_date     DATE NOT NULL,
  monthly_rent DECIMAL(10,2) NOT NULL,
  deposit      DECIMAL(10,2) NOT NULL,
  status       TINYINT NOT NULL DEFAULT 0 COMMENT '0在租 1已退租 2已到期',
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_contract (contract_id),
  KEY idx_tenant (tenant_id),
  KEY idx_landlord (landlord_id),
  CONSTRAINT fk_order_contract FOREIGN KEY (contract_id) REFERENCES contract (id),
  CONSTRAINT fk_order_house FOREIGN KEY (house_id) REFERENCES house (id),
  CONSTRAINT fk_order_tenant FOREIGN KEY (tenant_id) REFERENCES sys_user (id),
  CONSTRAINT fk_order_landlord FOREIGN KEY (landlord_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租赁订单表';

CREATE TABLE IF NOT EXISTS rent_bill (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  lease_order_id BIGINT UNSIGNED NOT NULL,
  period_no      INT    NOT NULL COMMENT '第几期',
  due_date       DATE   NOT NULL,
  amount         DECIMAL(10,2) NOT NULL,
  status         TINYINT NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已逾期',
  paid_at        DATETIME NULL,
  remark         VARCHAR(200) NULL,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_period (lease_order_id, period_no),
  CONSTRAINT fk_bill_order FOREIGN KEY (lease_order_id) REFERENCES lease_order (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租金账单表';

CREATE TABLE IF NOT EXISTS review (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  lease_order_id BIGINT UNSIGNED NOT NULL COMMENT '一单一评',
  house_id       BIGINT UNSIGNED NOT NULL,
  landlord_id    BIGINT UNSIGNED NOT NULL,
  tenant_id      BIGINT UNSIGNED NOT NULL,
  house_score    TINYINT NOT NULL COMMENT '1~5',
  landlord_score TINYINT NOT NULL COMMENT '1~5',
  content        VARCHAR(500) NULL,
  status         TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1已隐藏',
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order (lease_order_id),
  KEY idx_house_status (house_id, status, created_at),
  CONSTRAINT fk_review_order FOREIGN KEY (lease_order_id) REFERENCES lease_order (id),
  CONSTRAINT fk_review_house FOREIGN KEY (house_id) REFERENCES house (id),
  CONSTRAINT fk_review_landlord FOREIGN KEY (landlord_id) REFERENCES sys_user (id),
  CONSTRAINT fk_review_tenant FOREIGN KEY (tenant_id) REFERENCES sys_user (id),
  CONSTRAINT ck_house_score CHECK (house_score BETWEEN 1 AND 5),
  CONSTRAINT ck_landlord_score CHECK (landlord_score BETWEEN 1 AND 5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '评价表';

CREATE TABLE IF NOT EXISTS report (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  reporter_id   BIGINT UNSIGNED NOT NULL,
  target_type   TINYINT    NOT NULL COMMENT '1房源 2评价 3用户',
  target_id     BIGINT UNSIGNED NOT NULL COMMENT '多态关联,业务层校验',
  reason        VARCHAR(200) NOT NULL,
  status        TINYINT    NOT NULL DEFAULT 0 COMMENT '0待处理 1已处理',
  handle_by     BIGINT UNSIGNED NULL,
  handle_remark VARCHAR(200) NULL,
  handled_at    DATETIME   NULL,
  created_at    DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_status (status, created_at),
  CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '举报表';

CREATE TABLE IF NOT EXISTS audit_log (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  operator_id BIGINT UNSIGNED NOT NULL,
  action      VARCHAR(50) NOT NULL COMMENT 'HOUSE_AUDIT/USER_BAN/REPORT_HANDLE/SIGN...',
  target_type VARCHAR(30) NOT NULL,
  target_id   BIGINT UNSIGNED NOT NULL,
  detail      JSON NULL,
  ip          VARCHAR(45) NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_operator (operator_id),
  KEY idx_target (target_type, target_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审计日志表';

CREATE TABLE IF NOT EXISTS ai_chat_session (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id         BIGINT UNSIGNED NOT NULL COMMENT '逻辑关联',
  scene           TINYINT    NOT NULL COMMENT '1找房助手 2智能客服 3合同解读',
  title           VARCHAR(100) NULL,
  context_summary VARCHAR(500) NULL COMMENT '长对话记忆摘要',
  is_transferred  TINYINT    NOT NULL DEFAULT 0 COMMENT '客服未命中转人工标记',
  created_at      DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI会话表';

CREATE TABLE IF NOT EXISTS ai_chat_message (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  session_id  BIGINT UNSIGNED NOT NULL,
  role        TINYINT NOT NULL COMMENT '1user 2assistant 3tool',
  content     MEDIUMTEXT NULL,
  tool_name   VARCHAR(50) NULL,
  tool_args   JSON NULL,
  tool_result JSON NULL,
  citations   JSON NULL COMMENT 'RAG来源引用',
  token_count INT NULL,
  latency_ms  INT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_session (session_id, id),
  CONSTRAINT fk_msg_session FOREIGN KEY (session_id) REFERENCES ai_chat_session (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI对话消息表';

CREATE TABLE IF NOT EXISTS ai_analysis (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id        BIGINT UNSIGNED NOT NULL COMMENT '触发人,逻辑关联',
  type           TINYINT NOT NULL COMMENT '1定价建议 2虚假房源检测 3合同解读 4房源信息识别',
  target_type    VARCHAR(30) NOT NULL,
  target_id      BIGINT UNSIGNED NOT NULL,
  input_snapshot JSON NULL,
  result         JSON NOT NULL,
  model          VARCHAR(50) NULL,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_target (target_type, target_id),
  KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI分析结果表';

CREATE TABLE IF NOT EXISTS kb_document (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  title       VARCHAR(100) NOT NULL,
  category    TINYINT NOT NULL COMMENT '1平台规则 2租赁政策FAQ 3合同模板说明',
  source_url  VARCHAR(255) NULL,
  content     MEDIUMTEXT NOT NULL,
  status      TINYINT NOT NULL DEFAULT 0 COMMENT '0待入库 1已生效 2失效',
  chunk_count INT     NOT NULL DEFAULT 0,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_category_status (category, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识库文档表';

CREATE TABLE IF NOT EXISTS kb_chunk (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  document_id  BIGINT UNSIGNED NOT NULL,
  seq          INT    NOT NULL,
  content      TEXT   NOT NULL COMMENT '切片正文(embedding输入)',
  token_count  INT    NULL,
  vector_ref   VARCHAR(64) NULL COMMENT '向量库引用id(升级向量检索后填充)',
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_doc_seq (document_id, seq),
  CONSTRAINT fk_chunk_document FOREIGN KEY (document_id) REFERENCES kb_document (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识库切片表';
