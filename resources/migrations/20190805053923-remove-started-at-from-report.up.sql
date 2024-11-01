alter table report drop start_time;

--;;

alter table report add review_time timestamp;

--;;

update report set review_time = coalesce(reject_time, accept_time) where reject_time is not null or accept_time is not null;

--;;

alter table report drop accept_time;

--;;

alter table report drop reject_time;

--;;

update report set status = 'created' where status = 'started';

--;;

update report set status = 'reviewed' where status = 'accepted' or status = 'rejected';
