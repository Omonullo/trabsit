alter table oauth_client
    add column grant_type varchar(20) not null default 'code';

--;;

alter table oauth_client
    alter column grant_type drop default;

--;;

alter table oauth_token
    add column grant_type varchar(20) not null default 'code';

--;;

alter table oauth_token
    alter column grant_type drop default;

--;;

alter table oauth_token
    drop constraint oauth_token_check;

--;;

alter table oauth_token
    add constraint oauth_token_check
        check (((staff_id IS NOT NULL) or (citizen_id IS NOT NULL) and grant_type = 'code') or
               (grant_type = 'client_credentials'));
