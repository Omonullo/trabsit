drop trigger citizen_tsvector_before_insert_update on citizen;

--;;

drop function citizen_tsvector_trigger;

--;;

alter table citizen
    drop column document;
