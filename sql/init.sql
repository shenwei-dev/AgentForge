

CREATE DATABASE IF NOT EXISTS agentforge
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE agentforge;

CREATE TABLE IF NOT EXISTS conversation
(
    id              VARCHAR(36)  NOT NULL COMMENT '会话ID(UUID)',
    title           VARCHAR(200) NOT NULL DEFAULT '新会话' COMMENT '会话标题',
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED',
    model_provider  VARCHAR(64)  NULL COMMENT '本次会话使用的模型提供方',
    last_message_at DATETIME(3)  NULL COMMENT '最后消息时间，用于列表排序',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否 1-是',
    PRIMARY KEY (id),
    KEY idx_conv_status_last (status, last_message_at DESC),
    KEY idx_conv_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '会话表';

CREATE TABLE IF NOT EXISTS chat_message
(
    id                VARCHAR(36)  NOT NULL COMMENT '消息ID(UUID)',
    conversation_id   VARCHAR(36)  NOT NULL COMMENT '会话ID',
    run_id            VARCHAR(64)  NULL COMMENT 'Agent 执行ID，后续阶段用于 Trace 关联',
    role              VARCHAR(32)  NOT NULL COMMENT 'USER/ASSISTANT/SYSTEM/TOOL',
    content           MEDIUMTEXT   NOT NULL COMMENT '消息正文',
    content_type      VARCHAR(32)  NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT/MARKDOWN/JSON/EVENT/SUMMARY',
    model_name        VARCHAR(64)  NULL COMMENT '生成该消息的模型',
    prompt_tokens     INT          NULL,
    completion_tokens INT          NULL,
    latency_ms        BIGINT       NULL COMMENT '模型调用耗时',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_msg_conv_created (conversation_id, created_at),
    KEY idx_msg_run (run_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '聊天消息表';

CREATE TABLE IF NOT EXISTS agent_config
(
    id              VARCHAR(64)  NOT NULL COMMENT 'Agent ID',
    agent_name      VARCHAR(64)  NOT NULL,
    role            VARCHAR(64)  NOT NULL COMMENT 'PLANNER/RESEARCHER/ANALYST/REVIEWER/WRITER',
    system_prompt   TEXT         NOT NULL,
    tools_json      JSON         NULL COMMENT '允许使用的工具名列表',
    model_provider  VARCHAR(64)  NULL COMMENT '为空表示跟随全局 active 模型',
    model_name      VARCHAR(64)  NULL,
    max_rounds      INT          NOT NULL DEFAULT 10 COMMENT 'Agent Loop 最大轮数',
    timeout_seconds INT          NOT NULL DEFAULT 120 COMMENT '单次执行超时(秒)',
    enabled         TINYINT      NOT NULL DEFAULT 1,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_name (agent_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Agent 角色配置表';

INSERT INTO agent_config (id, agent_name, role, system_prompt, tools_json, max_rounds, timeout_seconds)
VALUES ('planner', '规划 Agent', 'PLANNER', '你负责把复杂任务拆解为有依赖关系的可执行子任务，并输出结构化计划。', JSON_ARRAY(), 6, 120),
       ('researcher', '研究 Agent', 'RESEARCHER', '你负责收集资料、检索信息，输出事实性摘要并标注来源。', JSON_ARRAY('file_search', 'file_read', 'echo'), 10, 180),
       ('analyst', '分析 Agent', 'ANALYST', '你负责对给定资料做对比、统计和推理，给出结构化结论。', JSON_ARRAY('calculator', 'echo'), 10, 180),
       ('reviewer', '审核 Agent', 'REVIEWER', '你负责检查结论是否有事实错误、逻辑漏洞和遗漏，并给出修改建议。', JSON_ARRAY('file_read'), 6, 120),
       ('writer', '写作 Agent', 'WRITER', '你负责把多方结果整合为 Markdown 格式的最终报告。', JSON_ARRAY('echo'), 8, 180)
ON DUPLICATE KEY UPDATE id = id;

UPDATE agent_config SET tools_json = JSON_ARRAY() WHERE id = 'planner';
UPDATE agent_config SET tools_json = JSON_ARRAY('file_search', 'file_read', 'echo') WHERE id = 'researcher';
UPDATE agent_config SET tools_json = JSON_ARRAY('calculator', 'echo') WHERE id = 'analyst';
UPDATE agent_config SET tools_json = JSON_ARRAY('file_read') WHERE id = 'reviewer';
UPDATE agent_config SET tools_json = JSON_ARRAY('echo') WHERE id = 'writer';

CREATE TABLE IF NOT EXISTS workflow
(
    id              VARCHAR(36)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512) NULL,
    definition_json JSON         NULL COMMENT '工作流定义快照',
    version         INT          NOT NULL DEFAULT 1,
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/DISABLED',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_name_version (name, version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作流定义表';

CREATE TABLE IF NOT EXISTS workflow_node
(
    id           VARCHAR(36)  NOT NULL,
    workflow_id  VARCHAR(36)  NOT NULL,
    node_key     VARCHAR(64)  NOT NULL COMMENT '工作流内唯一节点标识',
    node_name    VARCHAR(128) NOT NULL,
    node_type    VARCHAR(32)  NOT NULL DEFAULT 'AGENT' COMMENT 'AGENT/TOOL/CONDITION/JOIN',
    agent_id     VARCHAR(64)  NULL,
    config_json  JSON         NULL,
    sort_no      INT          NOT NULL DEFAULT 0,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_node_workflow_key (workflow_id, node_key),
    KEY idx_node_workflow (workflow_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作流节点表';

CREATE TABLE IF NOT EXISTS workflow_edge
(
    id             VARCHAR(36)  NOT NULL,
    workflow_id    VARCHAR(36)  NOT NULL,
    from_node_key  VARCHAR(64)  NOT NULL COMMENT '前置节点',
    to_node_key    VARCHAR(64)  NOT NULL COMMENT '后置节点',
    condition_expr VARCHAR(512) NULL COMMENT '条件表达式，为空表示无条件依赖',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_edge (workflow_id, from_node_key, to_node_key),
    KEY idx_edge_to (workflow_id, to_node_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作流依赖关系表';

CREATE TABLE IF NOT EXISTS workflow_run
(
    id              VARCHAR(64)  NOT NULL COMMENT 'Workflow Run ID',
    conversation_id VARCHAR(36)  NULL,
    workflow_id     VARCHAR(36)  NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    input_text      TEXT         NULL,
    output_text     MEDIUMTEXT   NULL,
    error_message   TEXT         NULL,
    started_at      DATETIME(3)  NULL,
    finished_at     DATETIME(3)  NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_run_conv (conversation_id, created_at),
    KEY idx_run_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作流执行记录表';

CREATE TABLE IF NOT EXISTS node_run
(
    id              VARCHAR(64)  NOT NULL,
    run_id          VARCHAR(64)  NOT NULL,
    node_id         VARCHAR(36)  NOT NULL DEFAULT '',
    node_key        VARCHAR(64)  NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/READY/RUNNING/SUCCEEDED/FAILED/SKIPPED/WAITING_APPROVAL/CANCELLED',
    input_text      TEXT         NULL,
    output_text     MEDIUMTEXT   NULL,
    error_message   TEXT         NULL,
    retry_count     INT          NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(128) NULL COMMENT '幂等键，避免有副作用的工具被重复执行',
    started_at      DATETIME(3)  NULL,
    finished_at     DATETIME(3)  NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_node_run (run_id, node_key, retry_count),
    UNIQUE KEY uk_node_idem (idempotency_key),
    KEY idx_node_run_status (run_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '节点执行记录表';

CREATE TABLE IF NOT EXISTS tool_call
(
    id            VARCHAR(64)  NOT NULL COMMENT 'Tool Call ID',
    run_id        VARCHAR(64)  NULL,
    node_id       VARCHAR(36)  NULL,
    agent_id      VARCHAR(64)  NULL,
    tool_name     VARCHAR(64)  NOT NULL,
    input_json    JSON         NULL,
    output_json   JSON         NULL,
    status        VARCHAR(32)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCEEDED/FAILED/TIMEOUT/DENIED',
    error_message TEXT         NULL,
    duration_ms   BIGINT       NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_tool_run (run_id, created_at),
    KEY idx_tool_name (tool_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工具调用记录表';

CREATE TABLE IF NOT EXISTS execution_event
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    event_id        VARCHAR(64)  NOT NULL COMMENT '事件唯一ID，用于前端去重',
    run_id          VARCHAR(64)  NULL,
    conversation_id VARCHAR(36)  NULL,
    event_type      VARCHAR(64)  NOT NULL COMMENT 'run_started/plan_created/agent_started/...',
    agent_id        VARCHAR(64)  NULL,
    node_id         VARCHAR(36)  NULL,
    tool_call_id    VARCHAR(64)  NULL,
    level           VARCHAR(16)  NOT NULL DEFAULT 'INFO' COMMENT 'INFO/WARN/ERROR',
    message         VARCHAR(1024) NULL,
    payload_json    JSON         NULL COMMENT '事件附加数据，只允许动态结构',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_id (event_id),
    KEY idx_event_run (run_id, id),
    KEY idx_event_conv (conversation_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '执行事件表';

CREATE TABLE IF NOT EXISTS checkpoint
(
    id          VARCHAR(64)  NOT NULL,
    run_id      VARCHAR(64)  NOT NULL,
    node_id     VARCHAR(36)  NOT NULL DEFAULT '',
    state_type  VARCHAR(64)  NOT NULL COMMENT 'AGENT_LOOP/NODE/RUNTIME_CONTEXT',
    state_json  JSON         NOT NULL COMMENT '可恢复的执行状态快照',
    version     INT          NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_checkpoint_version (run_id, node_id, state_type, version),
    KEY idx_checkpoint_run (run_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '任务检查点表';

CREATE TABLE IF NOT EXISTS conversation_run
(
    conversation_id VARCHAR(36) NOT NULL,
    run_id          VARCHAR(64) NOT NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (conversation_id, run_id),
    UNIQUE KEY uk_conv_run_run (run_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '会话与执行记录关联表';
