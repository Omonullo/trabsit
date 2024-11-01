alter table reward drop number;

--;;

alter table reward add offense_id uuid references offense;

--;;

update reward
set offense_id = offense.id
from offense
where offense.reward_id = reward.id;

--;;

create unique index reward_offense_id_uindex on reward (offense_id);

--;;

alter table offense drop reward_id;
