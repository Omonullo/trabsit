drop table province CASCADE;


--;;


alter table area drop column province_id cascade;


--;;


alter table report drop column province_id cascade;


--;;


alter table organization drop column province_id cascade;


--;;


alter table citizen drop column province_id cascade;
