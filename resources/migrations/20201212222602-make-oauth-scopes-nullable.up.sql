alter table oauth_client
    alter column allowed_scope drop not null;

--;;

alter table oauth_client
    alter column default_scope drop not null;

--;;

alter table oauth_client
    alter column redirect_uri drop not null;

--;;

alter table oauth_client
    alter column error_redirect_uri drop not null;

