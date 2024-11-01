alter table offense
    add column creator_citizen_id uuid references citizen (id),
    add column creator_staff_id   uuid references staff (id);


--;;

update offense
set creator_citizen_id=report_offense.citizen_id
from (
         select report.citizen_id
         from report
                  join offense o on report.id = o.report_id
     ) as report_offense
where true;
