create table oauth_client
(
    id                 uuid           not null primary key,
    secret             varchar(98)    not null,
    name               varchar,
    allowed_scope      varchar(100)[] not null check ( cardinality(allowed_scope) > 0 ),
    default_scope      varchar(100)[] not null check ( cardinality(default_scope) > 0 ),
    redirect_uri       varchar(255)[] not null check ( cardinality(redirect_uri) > 0 ),
    error_redirect_uri varchar(255)   not null,
    url                varchar(255),
    logo               varchar(255),
    enabled            bool      default true,
    create_time        timestamp default now()
);
