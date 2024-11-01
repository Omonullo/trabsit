alter table oauth_client
    alter column allowed_scope set not null;

--;;

alter table oauth_client
    alter column default_scope set not null;

--;;

alter table oauth_client
    alter column redirect_uri set not null;

--;;

alter table oauth_client
    alter column error_redirect_uri set not null;

