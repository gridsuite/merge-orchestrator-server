CREATE TABLE merge_orchestrator.merge_igm (
    process text,
    date timestamp,
    tso text,
    status text,
    networkUuid uuid,
    caseUuid uuid,
    replacingDate timestamp,
    replacingBusinessProcess text,
    boundaries frozen<List<uuid>>,
    PRIMARY KEY (process, date, tso)
);

CREATE TABLE merge_orchestrator.merge (
    process text,
    date timestamp,
    status text,
    PRIMARY KEY (process, date)
);

CREATE TABLE merge_orchestrator.configs (
    process text,
    businessProcess text,
    tsos frozen<List<text>>,
    runBalancesAdjustment boolean,
    PRIMARY KEY (process)
);
