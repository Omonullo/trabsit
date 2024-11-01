create table offense_type
(
    id   serial primary key,
    name_ru varchar(255) not null,
    name_uz_cy varchar(255),
    name_uz_la varchar(255)
);

--;;

alter table offense
    add type_id integer references offense_type(id) on delete set null;
