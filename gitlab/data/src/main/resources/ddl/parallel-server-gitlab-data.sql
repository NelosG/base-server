CREATE TABLE prl_gitlab_user
(
    user_id     bigint       NOT NULL PRIMARY KEY,
    gitlab_name varchar(100) NOT NULL,
    CONSTRAINT uk_prl_gitlab_user_gitlab_name UNIQUE (gitlab_name),
    CONSTRAINT fk_prl_gitlab_user_user FOREIGN KEY (user_id) REFERENCES prl_user (id) ON DELETE CASCADE
);
-- user_id is the PK (1:1 mapping with prl_user); gitlab_name is unique so a single
-- GitLab account cannot be linked to more than one orchestrator user.
ALTER TABLE prl_gitlab_user
    OWNER TO nelos;
