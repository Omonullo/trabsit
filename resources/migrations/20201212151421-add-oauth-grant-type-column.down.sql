alter table oauth_token
    drop constraint oauth_token_check;

--;;

alter table oauth_token
    add constraint oauth_token_check
        check ((staff_id IS NULL) <> (citizen_id IS NULL));

--;;

alter table oauth_client
    drop column grant_type;

--;;

alter table oauth_token
    drop column grant_type;
