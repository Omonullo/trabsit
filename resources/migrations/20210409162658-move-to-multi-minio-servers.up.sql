drop view report_offense_count;

--;;

alter table report alter column thumbnail type varchar(100);

--;;

alter table report alter column video type varchar(100);

--;;

alter table report alter column extra_video type varchar(100);

--;;

alter table offense alter column vehicle_img type varchar(100);

--;;

alter table offense alter column vehicle_id_img type varchar(100);

--;;

alter table offense alter column extra_img type varchar(100);

--;;

update report
set thumbnail = concat('jarima/', report.thumbnail);

--;;

update report
set video = concat('jarima/', video)
where video is not null;

--;;

update report
set extra_video = concat('jarima/', extra_video)
where extra_video is not null;

--;;

update offense
set vehicle_img = concat('jarima/', vehicle_img)
where vehicle_img is not null;

--;;

update offense
set vehicle_id_img = concat('jarima/', vehicle_id_img)
where vehicle_id_img is not null;

--;;

update offense
set extra_img = concat('jarima/', extra_img)
where extra_img is not null;
