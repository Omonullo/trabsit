update citizen
set card_number = replace(card_number, ' ', '')
where card_number is not null;
