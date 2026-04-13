CREATE TABLE prl_job
(
    id              bigint PRIMARY KEY,
    start_date      timestamp,
    end_date        timestamp,
    total_count     int,
    processed_count int,
    status          varchar(2)
);
CREATE SEQUENCE seq_prl_job MINVALUE 1 START WITH 1;
ALTER TABLE prl_job
    OWNER TO nelos;
ALTER SEQUENCE seq_prl_job OWNER TO nelos;
