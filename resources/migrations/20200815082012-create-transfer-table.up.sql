create unique index organization_citizen_id_bank_account_uindex
    on organization (citizen_id, bank_account);

--;;

create table transfer
(
    id           uuid primary key,
    bank_account text      not null,
    number       serial    not null,
    amount       numeric   not null,
    status       text      not null,
    create_time  timestamp not null,
    send_time    timestamp
);

--;;

insert into transfer (id, bank_account, amount, status, create_time, send_time)
select uuid_in(overlay(overlay(md5(random()::text || ':' || clock_timestamp()::text) placing '4' from 13) placing
                       to_hex(floor(random() * (11 - 8 + 1) + 8)::int)::text from 17)::cstring) id,
       reward.params ->> 'bank'                                                                 bank_account,
       sum(reward.amount)                                                                       amount,
       'sent'                                                                                   status,
       reward.pay_time                                                                          create_time,
       reward.pay_time                                                                          send_time
from reward
         left join offense o on reward.id = o.reward_id
         left join report r on o.report_id = r.id
where type = 'bank'
  and reward.status = 'paid'
group by reward.pay_time, reward.params
order by reward.pay_time;

--;;

alter table reward
    add transfer_id uuid references transfer;

--;;

with t as (select r.id reward_id, t.id transfer_id
           from reward r
                    left join transfer t on t.create_time = r.pay_time and t.bank_account = r.params ->> 'bank'
           where r.type = 'bank'
             and r.status = 'paid')
update reward
set transfer_id = t.transfer_id, payment_result = null, payment_log = null
from t
where id = reward_id;
