alter table offense drop constraint offense_article_code_fkey;

--;;

alter table offense alter column article_id type varchar(12) using article_id::varchar(12);

--;;

UPDATE
    offense
SET
    article_id = a.id
FROM (select id, code from article) as a
where article_id::integer = a.code;

--;;


alter table article alter column code set default NULL;

--;;

alter table article drop constraint article_pkey;

--;;

alter table article
    add constraint article_pkey
        primary key (id);

--;;


alter table offense
    add constraint offense_article_id_fkey
        foreign key (article_id) references article
            on update cascade on delete restrict;
