alter table citizen
    add column creator_client_id uuid references oauth_client (id) on delete restrict;

--;;

alter table report
    add column creator_client_id uuid references oauth_client (id) on delete restrict;

--;;

alter table report
    add column creator_client_notified_at timestamp;

--;;

alter table offense
    add column creator_client_id uuid references oauth_client (id) on delete restrict;

--;;

alter table offense
    add column creator_client_notified_at timestamp;

--;;

alter table oauth_token alter column scope drop not null;

