alter table response
    add column priority integer default 0 not null;


--;;

update response
set priority = response_ranking.rank
from (select response.id as id, row_number() over (order by count(o.*) desc ) as rank
      from response
               left join offense o on response.id = o.response_id
      group by response.id) as response_ranking
where response.id = response_ranking.id
