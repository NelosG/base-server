CREATE TABLE prl_user
(
    id                 bigint PRIMARY KEY,
    login              varchar(100),
    encrypted_password varchar(100),
    type               varchar(2)
);

CREATE SEQUENCE seq_prl_user MINVALUE 0;
ALTER TABLE prl_user
    OWNER TO nelos;
ALTER SEQUENCE seq_prl_user OWNER TO nelos;

CREATE TABLE prl_api_key
(
    id         bigint PRIMARY KEY,
    key_hash   varchar(64)  NOT NULL,
    key_prefix varchar(8)   NOT NULL,
    name       varchar(100) NOT NULL,
    active     boolean      NOT NULL DEFAULT TRUE,
    created_at timestamp    NOT NULL DEFAULT NOW()
);

CREATE SEQUENCE seq_prl_api_key MINVALUE 0;
ALTER TABLE prl_api_key
    OWNER TO nelos;
ALTER SEQUENCE seq_prl_api_key OWNER TO nelos;
