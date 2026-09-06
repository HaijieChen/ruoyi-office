-- U2: irreversible human-result marker and transaction generation.
-- Deploy before enabling initiator_withdraw_mode = 1. No process variables/backfill.
CREATE TABLE IF NOT EXISTS bpm_initiator_withdraw_state (
    tenant_id BIGINT NOT NULL,
    process_instance_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    human_result TINYINT NOT NULL DEFAULT 0,
    generation BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, process_instance_id)
) ENGINE=InnoDB COMMENT='发起人撤回的人工结果与并发代次（仅策略1）';
