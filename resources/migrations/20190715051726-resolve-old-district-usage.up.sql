update report
set district_id = 'f928ac6d-c754-45dc-ba55-9a4765370d28'
from (select report.id
      from report
               left join offense on offense.report_id = report.id
      where (offense.status = 'accepted' or offense.status = 'created')
        and report.district_id = 'cf9952d3-f117-46ea-907b-b271e6410174'
      group by report.id) as r
where r.id = report.id;
