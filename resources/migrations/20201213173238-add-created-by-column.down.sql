alter table citizen
    drop column creator_client_id;

--;;

alter table report
    drop column creator_client_id;

--;;


alter table offense
    drop column creator_client_id;

--;;

alter table offense
    drop column creator_client_notified_at;

--;;

alter table oauth_token alter column scope set not null;

