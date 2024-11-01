alter table area rename column name_uz_cy to  name_uz;

--;;

alter table area
    drop name_uz_la;

--;;

alter table area rename column yname_uz_cy to  yname_uz;

--;;

alter table area
    drop yname_uz_la;

--;;


alter table article rename column text_uz_cy to  text_uz;

--;;

alter table article
    drop text_uz_la;

--;;




alter table district rename column name_uz_cy to  name_uz;

--;;

alter table district
    drop name_uz_la;

--;;

alter table district rename column yname_uz_cy to  yname_uz;

--;;

alter table district
    drop yname_uz_la;

--;;


alter table faq rename column category_uz_cy to  category_uz;

--;;

alter table faq
    drop category_uz_la;

--;;


alter table faq rename column question_uz_cy to  question_uz;

--;;

alter table faq
    drop question_uz_la;

--;;


alter table faq rename column answer_uz_cy to  answer_uz;

--;;

alter table faq
    drop answer_uz_la;

--;;


alter table response rename column text_uz_cy to  text_uz;

--;;

alter table response
    drop text_uz_la;
