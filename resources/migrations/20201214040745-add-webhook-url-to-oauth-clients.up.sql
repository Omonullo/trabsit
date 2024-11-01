alter table oauth_client
    add column report_status_webhook text;

--;;

alter table oauth_client
    add column offense_status_webhook text;
