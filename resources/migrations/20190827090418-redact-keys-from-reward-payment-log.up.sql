update reward
set payment_log = regexp_replace(payment_log, 'key=[^ ]+', 'key=[REDACTED]', 'g')
where payment_log is not null;
