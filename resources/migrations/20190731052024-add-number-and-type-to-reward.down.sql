alter table reward drop column type;

--;;

alter table reward add column citizen_id uuid references citizen;

--;;

alter table reward rename column create_time to queue_time;
