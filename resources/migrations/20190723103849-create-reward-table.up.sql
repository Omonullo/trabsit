create table reward
(
    id              uuid primary key,
    offense_id      uuid references offense,
    citizen_id      uuid references citizen,
    amount          numeric,
    params          jsonb,
    status          varchar,
    queue_time      timestamp,
    pay_time        timestamp,
    failure_time    timestamp,
    failure_message varchar,
    payment_result  jsonb,
    payment_log     text
)
