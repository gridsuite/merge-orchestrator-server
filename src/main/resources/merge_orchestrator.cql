CREATE TABLE merge_orchestrator.merge_igm (
    processuuid uuid,
    date timestamp,
    tso text,
    status text,
    networkUuid uuid,
    caseUuid uuid,
    replacingDate timestamp,
    replacingBusinessProcess text,
    boundaries frozen<List<uuid>>,
    PRIMARY KEY (processuuid, date, tso)
);

CREATE TABLE merge_orchestrator.merge (
    processuuid uuid,
    date timestamp,
    status text,
    PRIMARY KEY (processuuid, date)
);

CREATE TABLE merge_orchestrator.configs (
    processuuid uuid,
    process text,
    businessProcess text,
    tsos frozen<List<text>>,
    runBalancesAdjustment boolean,
    PRIMARY KEY (processuuid)
);
