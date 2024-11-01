update report
set status = 'accepted'
where status = 'finished';

--;;

update report
set status = 'started'
where status = 'postponed';
