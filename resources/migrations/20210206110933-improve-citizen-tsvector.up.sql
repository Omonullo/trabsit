drop trigger citizen_tsvector_before_insert_update on citizen;

--;;

create or replace function citizen_tsvector_trigger() returns trigger as
$$
begin
    new.document := setweight(
                            to_tsvector(trim(lower(regexp_replace(coalesce(new.first_name, ''), '[''\\`"]', '', 'g')))),
                            'A')
                        || setweight(
                            to_tsvector(trim(lower(regexp_replace(coalesce(new.last_name, ''), '[''\\`"]', '', 'g')))),
                            'A')
                        || setweight(to_tsvector(trim(lower(
                regexp_replace(coalesce(new.middle_name, ''), '[''\\`"]', '', 'g')))), 'B')
                        || setweight(
                            to_tsvector(trim(lower(regexp_replace(coalesce(new.phone, ''), '[''\\`"]', '', 'g')))), 'C')
                        ||
                    setweight(to_tsvector(trim(lower(
                            regexp_replace(coalesce(right(new.phone, char_length(new.phone) - 3), ''), '[''\\`"]', '',
                                           'g')))), 'C')
                        ||
                    setweight(to_tsvector(trim(
                            lower(regexp_replace(coalesce(right(new.phone, char_length(new.phone) - 5), ''), '[''\\`"]',
                                                 '', 'g')))), 'C')
                        ||
                    setweight(to_tsvector(trim(lower(regexp_replace(coalesce(new.email, ''), '[''\\`"]', '', 'g')))),
                              'C')
        || setweight(to_tsvector(trim(lower(regexp_replace(coalesce(new.address, ''), '[''\\`"]', '', 'g')))), 'D');
    return new;
end
$$ language plpgsql;

--;;

create trigger citizen_tsvector_before_insert_update
    before update or insert
    on citizen
    for each row
execute procedure citizen_tsvector_trigger();

--;;

update citizen
set document = setweight(to_tsvector(trim(lower(regexp_replace(coalesce(first_name, ''), '[''\\`"]', '', 'g')))), 'A')
                   ||
               setweight(to_tsvector(trim(lower(regexp_replace(coalesce(last_name, ''), '[''\\`"]', '', 'g')))), 'A')
                   || setweight(
                       to_tsvector(trim(lower(regexp_replace(coalesce(middle_name, ''), '[''\\`"]', '', 'g')))), 'B')
                   || setweight(to_tsvector(trim(lower(regexp_replace(coalesce(phone, ''), '[''\\`"]', '', 'g')))), 'C')
                   ||
               setweight(to_tsvector(trim(
                       lower(regexp_replace(coalesce(right(phone, char_length(phone) - 3), ''), '[''\\`"]', '', 'g')))),
                         'C')
                   ||
               setweight(to_tsvector(trim(lower(
                       regexp_replace(coalesce(right(phone, char_length(phone) - 5), ''), '[''\\`"]', '', 'g')))),
                         'C')
                   || setweight(to_tsvector(trim(lower(regexp_replace(coalesce(email, ''), '[''\\`"]', '', 'g')))), 'C')
    || setweight(to_tsvector(trim(lower(regexp_replace(coalesce(address, ''), '[''\\`"]', '', 'g')))), 'D')
where true;
