create table card_payment
(
    id                 uuid primary key,
    card               varchar(20),
    citizen_id         uuid references citizen (id),
    staff_id           uuid references staff (id),
    reward_count       integer,
    reward_ids         jsonb,

    amount             numeric,
    status             varchar,
    create_time        timestamp,
    pay_time           timestamp,
    failure_time       timestamp,
    failure_message    varchar,
    payment_result     jsonb,
    payment_log        text,
    number             serial not null,
    transaction_number text
)
