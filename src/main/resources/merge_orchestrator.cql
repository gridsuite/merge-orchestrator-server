CREATE TABLE merge_orchestrator.merges (
    process text,
    date timestamp,
    status text,
    networkUuid uuid,
    PRIMARY KEY (process, date)
);

CREATE TABLE merge_orchestrator.igms_qualities (
    caseUuid uuid,
    networkUuid uuid,
    date timestamp,
    valid boolean,
    PRIMARY KEY (caseUuid)
);

