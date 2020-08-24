CREATE TABLE IF NOT EXISTS merge_orchestrator.merges (
    process text,
    date timestamp,
    status text,
    networkUuid uuid,
    PRIMARY KEY (process, date)
);

CREATE TABLE IF NOT EXISTS merge_orchestrator.boundaries (
    id text,
    boundary blob,
    filename text,
    PRIMARY KEY (id)
);
