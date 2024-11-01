-- Delete foreign constraint for offense
alter table offense
    drop constraint offense_article_id_fkey;

--;;

-- Update article_id to be article.code
UPDATE
    offense
SET
    article_id = a.code
FROM (select id, code from article) as a
where article_id = a.id;

--;;


-- Swap article primary keys
alter table article drop constraint article_pkey;

--;;

alter table article
    add constraint article_pkey
        primary key (code);

--;;

-- Create foreign keys for offense
alter table offense alter column article_id type integer using article_id::integer;

--;;

alter table offense
    add constraint offense_article_code_fkey
        foreign key (article_id) references article
            on update cascade on delete restrict;


