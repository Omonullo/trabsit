alter table report add column reward_phone varchar;

--;;

update report set reward_phone = citizen.phone
from citizen
where report.citizen_id = citizen.id;
