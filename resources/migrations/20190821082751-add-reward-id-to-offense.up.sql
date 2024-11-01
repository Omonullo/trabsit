alter table offense add reward_id uuid references reward;

--;;

update offense
set reward_id = reward.id
from reward
where reward.offense_id = offense.id;

--;;

create unique index offense_reward_id_uindex on offense (reward_id);

--;;

alter table reward drop offense_id;

--;;

alter table reward add number integer;

--;;

update reward
set number = offense.number
from offense
where offense.reward_id = reward.id;

--;;

create unique index reward_number_uindex on reward (number);
