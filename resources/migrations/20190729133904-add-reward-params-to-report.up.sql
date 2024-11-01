alter table report add reward_params jsonb;

--;;

update report
set reward_params = json_build_object('phone', report.reward_phone)
where reward_phone is not null;

--;;

alter table report drop reward_phone;
