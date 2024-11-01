update report set status = 'accepted' where status = 'reviewed';

--;;

alter table report add reject_time timestamp;

--;;

alter table report add accept_time timestamp;

--;;

update report set accept_time = review_time where review_time is not null;

--;;

alter table report drop review_time;

--;;

alter table report add start_time timestamp;

--;;

update report set start_time = accept_time where accept_time is not null;
