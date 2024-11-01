UPDATE
    article
SET
    id = split_part(id, '/', 1);
