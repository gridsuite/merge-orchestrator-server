CREATE TABLE merge_orchestrator.merge_igm (
    process text,
    date timestamp,
    tso text,
    status text,
    networkUuid uuid,
    PRIMARY KEY (process, date, tso)
);

CREATE TABLE merge_orchestrator.merge (
    process text,
    date timestamp,
    status text,
    PRIMARY KEY (process, date)
);
