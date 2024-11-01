drop trigger citizen_tsvector_before_insert_update on citizen;

--;;

create or replace function citizen_tsvector_trigger() returns trigger as
$$
begin
    new.document := setweight(to_tsvector(coalesce(new.first_name, '')), 'A')
                        || setweight(to_tsvector(coalesce(new.last_name, '')), 'A')
                        || setweight(to_tsvector(coalesce(new.middle_name, '')), 'B')
                        || setweight(to_tsvector(coalesce(new.phone, '')), 'C')
                        || setweight(to_tsvector(coalesce(right(new.phone, char_length(new.phone) - 3), '')), 'C')
                        || setweight(to_tsvector(coalesce(right(new.phone, char_length(new.phone) - 5), '')), 'C')
                        || setweight(to_tsvector(coalesce(new.email, '')), 'C')
        || setweight(to_tsvector(coalesce(new.address, '')), 'D');
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
set document = setweight(to_tsvector(coalesce(first_name, '')), 'A')
                   || setweight(to_tsvector(coalesce(last_name, '')), 'A')
                   || setweight(to_tsvector(coalesce(middle_name, '')), 'B')
                   || setweight(to_tsvector(coalesce(phone, '')), 'C')
                   || setweight(to_tsvector(coalesce(right(phone, char_length(phone) - 3), '')), 'C')
                   || setweight(to_tsvector(coalesce(right(phone, char_length(phone) - 5), '')), 'C')
                   || setweight(to_tsvector(coalesce(email, '')), 'C')
    || setweight(to_tsvector(coalesce(address, '')), 'D')
where true;
