CREATE TABLE prl_runner_config
(
    id         bigint       NOT NULL PRIMARY KEY,
    name       varchar(100) NOT NULL,
    enabled    boolean      NOT NULL DEFAULT FALSE,
    priority   int          NOT NULL DEFAULT 0,
    settings   jsonb,
    CONSTRAINT uk_prl_runner_config_name UNIQUE (name)
);
CREATE SEQUENCE seq_prl_runner_config MINVALUE 1 START WITH 1;
ALTER TABLE prl_runner_config
    OWNER TO nelos;
ALTER SEQUENCE seq_prl_runner_config OWNER TO nelos;
