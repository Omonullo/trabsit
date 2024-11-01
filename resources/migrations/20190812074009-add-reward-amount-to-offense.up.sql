alter table offense add reward_amount numeric;

--;;

update offense set reward_amount = 10136 where fine_id is not null;
