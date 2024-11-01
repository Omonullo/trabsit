drop trigger staff_tsvector_before_insert_update on staff;

--;;

drop function staff_tsvector_trigger;

--;;

alter table staff
    drop column document;
