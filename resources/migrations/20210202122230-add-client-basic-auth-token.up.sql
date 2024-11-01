alter table oauth_client
    add column webhook_login    text,
    add column webhook_password text;
