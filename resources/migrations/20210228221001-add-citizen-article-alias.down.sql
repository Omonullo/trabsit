alter table article
    drop column citizen_alias_ru,
    drop column citizen_alias_uz_la,
    drop column citizen_alias_uz_cy,
    drop column citizen_selection_enabled;

--;;

alter table offense
    drop column citizen_article_id;
