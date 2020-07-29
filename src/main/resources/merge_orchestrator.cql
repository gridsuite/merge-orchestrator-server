CREATE TABLE merge_orchestrator.merges (
    process text,
    date timestamp,
    status text,
    networkUuid uuid,
    PRIMARY KEY (process, date)
);

