CREATE TABLE prl_user
(
    id                 bigint PRIMARY KEY,
    login              varchar(100) NOT NULL,
    encrypted_password varchar(100) NOT NULL,
    display_name       varchar(255),
    type               varchar(2)   NOT NULL,
    properties         jsonb,
    CONSTRAINT uk_prl_user_login UNIQUE (login)
);

CREATE SEQUENCE seq_prl_user MINVALUE 1 START WITH 2;
ALTER TABLE prl_user
    OWNER TO nelos;
ALTER SEQUENCE seq_prl_user OWNER TO nelos;

-- Default admin user (login: admin, password: admin) - must change on first login.
INSERT INTO prl_user (id, login, encrypted_password, display_name, type, properties)
VALUES (1, 'admin', '$2b$12$LnJAepCeWBs2m1ywVGBVsuQGjnj6Ty7JctEiN89qhVMXNZrqI0n5m', 'Administrator', 'AD',
        '{
          "passwordChangeRequired": false
        }'::jsonb);

CREATE TABLE prl_api_key
(
    id         bigint PRIMARY KEY,
    key_hash   varchar(64)  NOT NULL,
    key_prefix varchar(8)   NOT NULL,
    name       varchar(100) NOT NULL,
    active     boolean      NOT NULL DEFAULT TRUE,
    created_at timestamp    NOT NULL DEFAULT NOW(),
    -- Hot path: ApiKeyAuthFilter calls validateKey() -> findByKeyHash() on every
    -- request to /api/register, /api/callback/*, /api/pipeline/*. UNIQUE because
    -- the SHA-256 hash deterministically identifies a single key.
    CONSTRAINT uk_prl_api_key_key_hash UNIQUE (key_hash)
);

CREATE SEQUENCE seq_prl_api_key MINVALUE 1 START WITH 1;
ALTER TABLE prl_api_key
    OWNER TO nelos;
ALTER SEQUENCE seq_prl_api_key OWNER TO nelos;
