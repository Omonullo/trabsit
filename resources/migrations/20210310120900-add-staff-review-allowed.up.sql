alter table staff
    add column review_allowed boolean not null default false;

--;;

update staff
set review_allowed = true
where role = 'inspector'
