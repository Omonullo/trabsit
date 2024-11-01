alter table area rename column name_uz to name_uz_cy;


--;;


alter table area
    add name_uz_la varchar;

--;;

alter table area rename column yname_uz to yname_uz_cy;

--;;

alter table area
    add yname_uz_la varchar;


--;;



alter table article rename column text_uz to text_uz_cy;


--;;


alter table article
    add text_uz_la varchar;


--;;



alter table district rename column name_uz to name_uz_cy;


--;;

alter table district
    add name_uz_la varchar;


--;;

alter table district rename column yname_uz to yname_uz_cy;


--;;

alter table district
    add yname_uz_la varchar;


--;;



alter table faq rename column category_uz to category_uz_cy;


--;;

alter table faq
    add category_uz_la varchar;


--;;


alter table faq rename column question_uz to question_uz_cy;


--;;

alter table faq
    add question_uz_la varchar;


--;;


alter table faq rename column answer_uz to answer_uz_cy;


--;;

alter table faq
    add answer_uz_la varchar;


--;;


alter table response rename column text_uz to text_uz_cy;


--;;

alter table response
    add text_uz_la varchar;
