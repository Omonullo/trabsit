create or replace view report_offense_count as
(
select report.*,
       (select count(*)
        from offense
        where offense.report_id = report.id
          and reject_time is not null
          and status = 'rejected'
       ) reject_count,
       (select count(*)
        from offense
        where offense.report_id = report.id
          and accept_time is not null
       ) accept_count,
       (select count(*)
        from offense
        where offense.report_id = report.id
       ) total_count
from report
    )
