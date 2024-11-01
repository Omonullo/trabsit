alter table article
    add column citizen_alias_ru          text,
    add column citizen_alias_uz_la       text,
    add column citizen_alias_uz_cy       text,
    add column citizen_selection_enabled boolean;

--;;

alter table offense
    add column citizen_article_id integer references article (id);
