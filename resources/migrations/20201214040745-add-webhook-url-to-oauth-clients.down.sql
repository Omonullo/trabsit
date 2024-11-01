alter table oauth_client
    drop column report_status_webhook;

--;;

alter table oauth_client
    drop column offense_status_webhook;
