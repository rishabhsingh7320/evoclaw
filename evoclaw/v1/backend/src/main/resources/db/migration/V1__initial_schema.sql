CREATE TABLE sessions (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id VARCHAR(255),
    metadata_json TEXT
);

CREATE TABLE messages (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    parent_message_id BIGINT,
    CONSTRAINT fk_messages_session
        FOREIGN KEY (session_id) REFERENCES sessions (id)
);

CREATE TABLE runs (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    root_message_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    config_json TEXT,
    CONSTRAINT fk_runs_session
        FOREIGN KEY (session_id) REFERENCES sessions (id),
    CONSTRAINT fk_runs_root_message
        FOREIGN KEY (root_message_id) REFERENCES messages (id)
);

CREATE TABLE steps (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL,
    step_index INT NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    input_message_id BIGINT,
    output_message_id BIGINT,
    state_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_steps_run
        FOREIGN KEY (run_id) REFERENCES runs (id),
    CONSTRAINT fk_steps_input_message
        FOREIGN KEY (input_message_id) REFERENCES messages (id),
    CONSTRAINT fk_steps_output_message
        FOREIGN KEY (output_message_id) REFERENCES messages (id)
);

CREATE TABLE tool_calls (
    id BIGSERIAL PRIMARY KEY,
    step_id BIGINT NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    arguments_json TEXT,
    result_json TEXT,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    CONSTRAINT fk_tool_calls_step
        FOREIGN KEY (step_id) REFERENCES steps (id)
);

CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL,
    step_id BIGINT,
    kind VARCHAR(64) NOT NULL,
    payload_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_events_run
        FOREIGN KEY (run_id) REFERENCES runs (id),
    CONSTRAINT fk_events_step
        FOREIGN KEY (step_id) REFERENCES steps (id)
);

CREATE TABLE agents (
    id VARCHAR(128) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    role VARCHAR(64),
    description TEXT,
    config_json TEXT
);

