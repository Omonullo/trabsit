alter table staff add column area_id uuid references area;

--;;

alter table staff add column district_id uuid references district;
