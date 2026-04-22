CREATE TABLE prl_node
(
    id                 bigint       NOT NULL PRIMARY KEY,
    node_id            varchar(100) NOT NULL,
    capabilities       jsonb,
    transports         jsonb,
    resource_providers jsonb,
    registered_at      timestamp    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_prl_node_node_id UNIQUE (node_id)
);
CREATE SEQUENCE seq_prl_node MINVALUE 1 START WITH 1;
ALTER TABLE prl_node
    OWNER TO nelos;
ALTER SEQUENCE seq_prl_node OWNER TO nelos;
