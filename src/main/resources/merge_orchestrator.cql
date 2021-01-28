CREATE TYPE merge_orchestrator.tso (
    sourcingActor text,
    alternativeSourcingActor text,
);

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

CREATE TABLE merge_orchestrator.configs (
    process text,
    businessProcess text,
    tsos frozen<List<tso>>,
    runBalancesAdjustment boolean,
    PRIMARY KEY (process)
);
