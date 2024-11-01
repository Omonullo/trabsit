with target as (
    select offense.id as offense_id, response.text_ru as extra_response
    from offense
    left join response on offense.response_id = response.id
    where reject_time is not null and offense.response_id is not null)
update offense
set extra_response = target.extra_response
from target
where id = target.offense_id;

--;;

alter table offense drop response_id;

--;;

alter table offense rename column extra_response TO response;
