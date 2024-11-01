alter table oauth_client
    drop column webhook_login,
    drop column webhook_password;
