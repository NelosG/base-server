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
