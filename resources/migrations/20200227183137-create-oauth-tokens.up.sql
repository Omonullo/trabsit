create table oauth_token
(
    id                  uuid                                           not null primary key,
    citizen_id          uuid references citizen,
    staff_id            uuid references staff,
    client_id           uuid references oauth_client on update cascade not null,
    scope               varchar(255)[]                                 not null,
    access_token        varchar(32)                                    not null,
    refresh_token       varchar(32)                                    not null,
    create_time         timestamp                                      not null,
    refresh_time        timestamp                                      not null,
    access_expire_time  timestamp                                      not null,
    refresh_expire_time timestamp                                      not null,
    revoked             boolean default false                          not null,
    CHECK ((staff_id IS NULL) != (citizen_id IS NULL))
);
