alter table reward add column type varchar;

--;;

alter table reward drop column citizen_id;

--;;

alter table reward rename column queue_time to create_time;
