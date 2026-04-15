CREATE TABLE prl_assignment (
    id                  bigint       PRIMARY KEY,
    code                varchar(100) NOT NULL,
    name                varchar(255),
    description         varchar(2000),
    gitlab_project_path varchar(255) NOT NULL,
    test_repo_url       varchar(500) NOT NULL,
    test_repo_branch    varchar(100) DEFAULT 'main',
    memory_limit_mb     bigint,
    threads             int,
    wall_time_sec       int,
    cpu_time_sec        int,
    max_processes       int,
    active              boolean      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_prl_assignment_code UNIQUE (code)
);
CREATE SEQUENCE seq_prl_assignment MINVALUE 1 START WITH 1;
ALTER TABLE prl_assignment OWNER TO nelos;
ALTER SEQUENCE seq_prl_assignment OWNER TO nelos;

CREATE TABLE prl_student_group (
    id          bigint       PRIMARY KEY,
    name        varchar(100) NOT NULL,
    description varchar(500),
    CONSTRAINT uk_prl_student_group_name UNIQUE (name)
);
CREATE SEQUENCE seq_prl_student_group MINVALUE 1 START WITH 1;
ALTER TABLE prl_student_group OWNER TO nelos;
ALTER SEQUENCE seq_prl_student_group OWNER TO nelos;

CREATE TABLE prl_gitlab_user (
    user_id     bigint       NOT NULL PRIMARY KEY,
    gitlab_name varchar(100) NOT NULL,
    CONSTRAINT uk_prl_gitlab_user_gitlab_name UNIQUE (gitlab_name),
    CONSTRAINT fk_prl_gitlab_user_user        FOREIGN KEY (user_id) REFERENCES prl_user(id) ON DELETE CASCADE
);
-- user_id is the PK (1:1 mapping with prl_user); gitlab_name is unique so a single
-- GitLab account cannot be linked to more than one orchestrator user.
ALTER TABLE prl_gitlab_user OWNER TO nelos;

CREATE TABLE prl_student_group_member (
    group_id bigint NOT NULL,
    user_id  bigint NOT NULL,
    PRIMARY KEY (group_id, user_id),
    CONSTRAINT fk_prl_sgm_group FOREIGN KEY (group_id) REFERENCES prl_student_group(id) ON DELETE CASCADE,
    CONSTRAINT fk_prl_sgm_user  FOREIGN KEY (user_id)  REFERENCES prl_user(id)          ON DELETE CASCADE
);
-- The composite PRIMARY KEY (group_id, user_id) already covers `WHERE group_id = ?` lookups;
-- a separate index on user_id is still needed for the reverse direction.
CREATE INDEX idx_prl_sgm_user_id ON prl_student_group_member(user_id);
ALTER TABLE prl_student_group_member OWNER TO nelos;

CREATE TABLE prl_submission (
    id                bigint       NOT NULL PRIMARY KEY,
    assignment_id     bigint       NOT NULL,
    user_id           bigint       NOT NULL,
    job_id            bigint,
    mr_iid            bigint,
    source_branch     varchar(100),
    solution_repo_url varchar(500),
    commit_sha        varchar(64),
    status            varchar(2)   NOT NULL DEFAULT 'PN',
    created_at        timestamp    NOT NULL DEFAULT NOW(),
    completed_at      timestamp,
    result_summary    varchar(4000),
    CONSTRAINT fk_prl_submission_assignment FOREIGN KEY (assignment_id) REFERENCES prl_assignment(id),
    CONSTRAINT fk_prl_submission_user       FOREIGN KEY (user_id)       REFERENCES prl_user(id) ON DELETE CASCADE
);
CREATE SEQUENCE seq_prl_submission MINVALUE 1 START WITH 1;
CREATE INDEX idx_prl_submission_assignment_mr ON prl_submission(assignment_id, mr_iid);
CREATE INDEX idx_prl_submission_user_id       ON prl_submission(user_id);
-- Hot path: PipelineService.handleResult / handleProgress look up submission by job_id
-- on every runner callback and every progress event.
CREATE INDEX idx_prl_submission_job_id        ON prl_submission(job_id);
-- Hot path: StuckSubmissionCleanupJob runs every minute and filters by status.
CREATE INDEX idx_prl_submission_status        ON prl_submission(status);
-- Race guard: at most one PENDING/DISPATCHED submission per (assignment, MR). Two
-- concurrent CI submits for the same MR each insert PENDING; the second one fails
-- with a unique-violation, which the orchestrator (or the CI retry) can recover from.
-- NULL mr_iid is allowed multiple times by Postgres' NULL semantics.
CREATE UNIQUE INDEX uk_prl_submission_active_mr ON prl_submission(assignment_id, mr_iid)
    WHERE status IN ('PN', 'DS');
ALTER TABLE prl_submission OWNER TO nelos;
ALTER SEQUENCE seq_prl_submission OWNER TO nelos;

CREATE TABLE prl_submission_result (
    id            bigint    NOT NULL PRIMARY KEY,
    submission_id bigint    NOT NULL,
    log_text      text      NOT NULL,
    result_json   text      NOT NULL,
    created_at    timestamp NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_prl_submission_result_submission UNIQUE (submission_id),
    CONSTRAINT fk_prl_submission_result_submission FOREIGN KEY (submission_id) REFERENCES prl_submission(id) ON DELETE CASCADE
);
CREATE SEQUENCE seq_prl_submission_result MINVALUE 1 START WITH 1;
ALTER TABLE prl_submission_result OWNER TO nelos;
ALTER SEQUENCE seq_prl_submission_result OWNER TO nelos;

CREATE INDEX idx_prl_assignment_gitlab_path ON prl_assignment(gitlab_project_path);

CREATE TABLE prl_submission_log (
    id            bigint    NOT NULL PRIMARY KEY,
    submission_id bigint    NOT NULL,
    line          text      NOT NULL,
    created_at    timestamp NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_prl_submission_log_submission FOREIGN KEY (submission_id) REFERENCES prl_submission(id) ON DELETE CASCADE
);
CREATE SEQUENCE seq_prl_submission_log MINVALUE 1 START WITH 1;
CREATE INDEX idx_prl_submission_log_submission ON prl_submission_log(submission_id, id);
ALTER TABLE prl_submission_log OWNER TO nelos;
ALTER SEQUENCE seq_prl_submission_log OWNER TO nelos;
