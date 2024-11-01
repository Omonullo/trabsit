alter table report add reward_phone varchar;

--;;

update report
set reward_phone = reward_params->>'phone'
where reward_params->>'phone' is not null;

--;;


alter table report drop reward_params;
