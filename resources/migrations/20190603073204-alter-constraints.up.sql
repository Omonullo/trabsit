alter table offense
    drop constraint offense_article_id_fkey;

--;;


alter table area drop constraint area_province_id_fkey;


--;;


alter table report drop constraint report_province_id_fkey;


--;;


alter table report drop constraint report_district_id_fkey;


--;;


alter table offense
    add constraint offense_article_id_fkey
        foreign key (article_id) references article
            on update cascade on delete restrict;

--;;


alter table report
    add constraint report_province_id_fkey
        foreign key (province_id) references province
            on update cascade on delete restrict;


--;;


alter table report
    add constraint report_district_id_fkey
        foreign key (district_id) references district
            on update cascade on delete restrict;


--;;


alter table area
    add constraint area_province_id_fkey
        foreign key (province_id) references province
            on update cascade on delete restrict;
