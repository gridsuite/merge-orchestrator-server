
    create table configs (
       processUuid uuid not null,
        businessProcess varchar(255),
        process varchar(255),
        runBalancesAdjustment boolean,
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

    alter table if exists ProcessConfigEntity_tsos 
       add constraint processConfigEntity_tsos_fk 
       foreign key (ProcessConfigEntity_processUuid) 
       references configs;
