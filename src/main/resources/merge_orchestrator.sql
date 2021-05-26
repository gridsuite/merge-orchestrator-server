
    create table boundary (
       id varchar(255) not null,
        filename varchar(255),
        scenarioTime timestamp,
        primary key (id)
    );

    create table configs (
       processUuid uuid not null,
        businessProcess varchar(255),
        process varchar(255),
        runBalancesAdjustment boolean,
        useLastBoundarySet boolean,
        eqBoundary_id varchar(255),
        tpBoundary_id varchar(255),
        primary key (processUuid)
    );

    create table merge (
       date timestamp not null,
        processUuid uuid not null,
        status varchar(255),
        primary key (date, processUuid)
    );

    create table merge_igm (
       date timestamp not null,
        processUuid uuid not null,
        tso varchar(255) not null,
        caseUuid uuid,
        eqBoundary varchar(255),
        networkUuid uuid,
        replacingBusinessProcess varchar(255),
        replacingDate timestamp,
        status varchar(255),
        tpBoundary varchar(255),
        primary key (date, processUuid, tso)
    );

    create table ProcessConfigEntity_tsos (
       ProcessConfigEntity_processUuid uuid not null,
        tso varchar(255)
    );
create index processConfigEntity_tsos_idx on ProcessConfigEntity_tsos (ProcessConfigEntity_processUuid);

    alter table if exists configs 
       add constraint eqBoundary_id_fk 
       foreign key (eqBoundary_id) 
       references boundary;

    alter table if exists configs 
       add constraint tpBoundary_id_fk 
       foreign key (tpBoundary_id) 
       references boundary;

    alter table if exists ProcessConfigEntity_tsos 
       add constraint processConfigEntity_tsos_fk 
       foreign key (ProcessConfigEntity_processUuid) 
       references configs;
