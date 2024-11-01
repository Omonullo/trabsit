alter table offense add response_id uuid references response;

--;;

with target as (
    select offense.id as offense_id, response.id as response_id
    from offense
    left join response on offense.response in (response.text_ru, response.text_uz_cy, response.text_uz_la)
    where reject_time is not null and response.id is not null)
update offense
set response_id = target.response_id,response    = null
from target
where id = target.offense_id;

--;;

alter table offense rename column response TO extra_response;
