alter table staff
    add column card varchar (20),
    add column card_bank_id uuid;

--;;

alter table reward add column staff_id uuid references staff(id);
