-- AI 风险检测任务与救援派单分开保存。来源告警键保证重启及重复同步不重复建单。
CREATE TABLE IF NOT EXISTS risk_detection_task (
    id BIGSERIAL PRIMARY KEY,
    incident_key VARCHAR(64) NOT NULL UNIQUE,
    source_revision BIGINT NOT NULL,
    stream_id VARCHAR(255) NOT NULL,
    event_name TEXT NOT NULL,
    details TEXT,
    confidence DOUBLE PRECISION,
    occurred_at TIMESTAMP NOT NULL,
    source_device_id BIGINT,
    source_device_name TEXT NOT NULL,
    source_device_code TEXT NOT NULL,
    source_device_type VARCHAR(64),
    device_ip TEXT,
    selected_model TEXT,
    location TEXT,
    location_desc TEXT,
    snapshot_url TEXT,
    video_url TEXT,
    media_status VARCHAR(24) NOT NULL DEFAULT 'RECORDING',
    media_error TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSED', 'RECOGNITION_ERROR')),
    handled_by TEXT,
    handled_at TIMESTAMP,
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_risk_task_time ON risk_detection_task(occurred_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_risk_task_status ON risk_detection_task(status);

CREATE TABLE IF NOT EXISTS risk_detection_task_history (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES risk_detection_task(id),
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    operator_id BIGINT,
    operator_name TEXT NOT NULL,
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_risk_task_history ON risk_detection_task_history(task_id, id);
