create table vehicle
(
    id varchar(12) not null primary key
);

--;;

create table article
(
    id      varchar(12) not null primary key,
    text_uz text,
    text_ru text
);

--;;

create table response
(
    id      uuid   not null primary key,
    text_ru varchar,
    text_uz varchar,
    number  serial not null
);

--;;

create table faq
(
    id          uuid   not null primary key,
    number      serial not null,
    category_ru varchar,
    category_uz varchar,
    question_ru varchar,
    question_uz varchar,
    answer_ru   varchar,
    answer_uz   varchar
);

--;;

create table province
(
    id       uuid   not null primary key,
    number   serial not null,
    name_ru  varchar,
    name_uz  varchar,
    yname_ru varchar,
    yname_uz varchar
);

--;;

create table area
(
    id          uuid   not null primary key,
    number      serial not null,
    province_id uuid references province,
    name_ru     varchar,
    name_uz     varchar,
    yname_ru    varchar,
    yname_uz    varchar
);

--;;

create table district
(
    id       uuid   not null primary key,
    number   serial not null,
    area_id  uuid references area,
    name_ru  varchar,
    name_uz  varchar,
    yname_ru varchar,
    yname_uz varchar
);

--;;

create table citizen
(
    id           uuid   not null primary key,
    phone        varchar(12),
    first_name   varchar(32),
    last_name    varchar(32),
    middle_name  varchar(32),
    email        varchar(64),
    card_number  varchar(20),
    card_bank_id uuid,
    province_id  uuid references province,
    area_id      uuid references area,
    district_id  uuid references district,
    address      varchar,
    second_phone varchar(12),
    zipcode      varchar(6),
    create_time  timestamp,
    number       serial not null
);

--;;

create table staff
(
    id          uuid    not null primary key,
    number      serial  not null,
    username    varchar(16),
    password    varchar(98),
    first_name  varchar(32),
    last_name   varchar(32),
    middle_name varchar(32),
    role        varchar not null,
    active      boolean default true
);

--;;

create table report
(
    id              uuid   not null primary key,
    number          serial not null,
    citizen_id      uuid references citizen,
    inspector_id    uuid references staff,
    organization_id uuid,
    reward_type     varchar,
    locale          varchar(2),
    lat             numeric,
    lng             numeric,
    province_id     uuid references province,
    area_id         uuid references area,
    district_id     uuid references district,
    address         varchar,
    video           varchar(36),
    thumbnail       varchar(36),
    incident_time   timestamp,
    create_time     timestamp,
    start_time      timestamp,
    postpone_time   timestamp,
    finish_time     timestamp,
    status          varchar(12)
);

--;;

create table organization
(
    id           uuid not null primary key,
    citizen_id   uuid references citizen,
    name         varchar,
    inn          varchar,
    type         varchar,
    address      varchar,
    zipcode      varchar(6),
    province_id  uuid references province,
    area_id      uuid references area,
    district_id  uuid references district,
    bank_account varchar(20),
    bank_mfo     varchar
);

--;;

create table offense
(
    id             uuid   not null primary key,
    number         serial not null,
    report_id      uuid references report,
    vehicle_id     varchar(12) references vehicle,
    article_id     varchar(12) references article,
    testimony      varchar,
    response       varchar,
    create_time    timestamp,
    reject_time    timestamp,
    accept_time    timestamp,
    forward_time   timestamp,
    status         varchar(12),
    vehicle_img    varchar(36),
    vehicle_id_img varchar(36)
);

--;;

insert into province (id, name_ru, name_uz) values ('3f713bb6-2b77-4e81-8af0-f77cb476ee19', 'Хорезмская область', 'Xorazm viloyati');

--;;

insert into province (id, name_ru, name_uz) values ('199b9007-e140-417e-b6d0-b8bdd37485cc', 'Наманганская область', 'Namangan viloyati');

--;;

insert into province (id, name_ru, name_uz) values ('42e313bf-9f6f-4cc1-8a37-384f4422a28b', 'Сурхандарьинская область', 'Surxondaryo viloyati');

--;;

insert into province (id, name_ru, name_uz) values ('5cf7f839-4e13-41f2-9e98-b430ef4c6044', 'Андижанская область ', 'Andijon viloyati');

--;;

insert into province (id, name_ru, name_uz) values ('a8a07a02-a86f-4ad9-b7ba-d659495750b8', 'Сырдарьинская область', 'Sirdaryo viloyati');

--;;

insert into province (id, name_ru, name_uz) values ('11dda07a-4fac-4fdb-a1d4-f7ba9f6e0469', 'Каракалпакстан', 'Qoraqalpog‘iston');

--;;

insert into province (id, name_ru, name_uz) values ('67933e63-4da3-4aba-b67c-7939579de7a0', 'Ташкентская область', 'Toshkent viloyati');

--;;

insert into province (id, name_ru, name_uz) values ('37079756-472a-4045-be55-ecbdd7755761', 'Самаркандская область', 'Samarqand viloyati');

--;;

insert into province (id, name_ru, name_uz) values ('6ce339b0-588d-4c3c-8848-c6e3205deb9a', 'Кашкадарьинская область', 'Qashqadaryo viloyati');

--;;

insert into province (id, name_ru, name_uz) values ('c01ec4ce-88af-42d6-b591-68b28703150d', 'Ферганская область', 'Farg‘ona viloyati');

--;;

insert into province (id, name_ru, name_uz) values ('6d5fc88d-d7aa-47fb-b36c-bbc2a7ee2547', 'Джизакская область', 'Jizzax viloyati');

--;;

insert into province (id, name_ru, name_uz) values ('1985a5ed-4c5d-45a8-8665-e6f61b371199', 'Навоийская область', 'Navoiy viloyati');

--;;

insert into province (id, name_ru, name_uz) values ('af96f22a-d5a3-4e52-b55b-e06bbca5f341', 'Бухарская область', 'Buxoro viloyati');

--;;

insert into area (id, name_ru, name_uz, province_id) select '3a5b968d-29e5-42b9-ac94-b8cb81d2fae2', 'Янгиарык', 'Yangiariq', id from province where name_ru = 'Хорезмская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '8d051c10-84d2-43a0-ab30-9566cddf94fe', 'Чуст', 'Chust', id from province where name_ru = 'Наманганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '11a31d93-e2d5-45d4-8467-098b0827e5aa', 'Учкурган', 'Uchqoʻrgʻon', id from province where name_ru = 'Наманганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '7d2ab12c-930c-4260-b080-1e825a144829', 'Шерабад', 'Sherobod', id from province where name_ru = 'Сурхандарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'e164ea7b-eb44-400b-94e8-bb233ee48aae', 'Асака', 'Asaka', id from province where name_ru = 'Андижанская область ';

--;;

insert into area (id, name_ru, name_uz, province_id) select '271610b5-20bd-4090-8059-d67b1dd0d840', 'Шават', 'Shovot', id from province where name_ru = 'Хорезмская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'f4140d67-a484-4709-9dc9-ae090beec38d', 'Янгиер', 'Yangiyer', id from province where name_ru = 'Сырдарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'c347c7c7-c749-45a7-8dc1-680ede7d4d13', 'Тахтакупыр', 'Taxtakoʻpir', id from province where name_ru = 'Каракалпакстан';

--;;

insert into area (id, name_ru, name_uz, province_id) select '815df64c-fb48-4577-8308-83540e39c06b', 'Пайтуг', 'Poytugʻ', id from province where name_ru = 'Андижанская область ';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'db0f5056-1f5f-4ac3-8e39-83880e93154e', 'Ходжикент', 'Xoʻjakent', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '95bd2ae5-b4c4-4494-adcd-31ad68b2a178', 'Лаиш', 'Loish', id from province where name_ru = 'Самаркандская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '15d2b9c8-7f5c-4abb-93ac-8c8e585b0523', 'Янги Миришкор', 'Mirishkor', id from province where name_ru = 'Кашкадарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'fc174b89-07f3-4046-8996-a19675ed4d63', 'Узун', 'Uzun', id from province where name_ru = 'Сурхандарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '6c07ac12-7540-4e44-be84-08669d6f0ca2', 'Балыкчи', 'Baliqchi', id from province where name_ru = 'Андижанская область ';

--;;

insert into area (id, name_ru, name_uz, province_id) select '324de1f1-50c8-4577-a03a-f023c293c4a8', 'Зафар', 'Zafar', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'f90ace0b-a5ef-41b5-9a79-5cc76f7865bc', 'Учкуприк', 'Uchkoʻpriq', id from province where name_ru = 'Ферганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'bf6882b3-c7d2-418a-a23b-67b079fad0a6', 'Келес', 'Keles', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'aa57aaf9-9a22-4f33-966c-6b1cdadc53a9', 'Хамза', 'Hamza', id from province where name_ru = 'Ферганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'fc4a5b67-48bf-4ef5-a762-4b81d4e64108', 'Янги Маргилан', 'Yangi Margʻilon', id from province where name_ru = 'Ферганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '9e7e65e1-3d51-463c-89c4-c1df954964ff', 'Эшангузар', 'Eshonguzar', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'f63f50e8-16dd-4905-aa20-bd604bdf10fc', 'Даштобод', 'Dashtobod', id from province where name_ru = 'Джизакская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '5cdbb473-9656-472f-84e1-3feb97255496', 'Нурата', 'Nurota', id from province where name_ru = 'Навоийская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '58a20ced-2ab0-4d1f-9e93-68f401308f16', 'Кумкурган', 'Qumqoʻrgʻon', id from province where name_ru = 'Сурхандарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '32e032b1-e630-4681-b62d-cc753237071e', 'Жондор', 'Jondor', id from province where name_ru = 'Бухарская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '4bab3202-ee80-4937-bff5-4b8a478597fd', 'Куйганъяр', 'Kuyganyor', id from province where name_ru = 'Андижанская область ';

--;;

insert into area (id, name_ru, name_uz, province_id) select '006e9894-7053-4dad-afbb-df176604dcde', 'Мангит', 'Mangʻit', id from province where name_ru = 'Каракалпакстан';

--;;

insert into area (id, name_ru, name_uz, province_id) select '5b39fbd0-ab6f-4c7d-9029-76309347366e', 'Алтынкуль', 'Oltinkoʻl', id from province where name_ru = 'Андижанская область ';

--;;

insert into area (id, name_ru, name_uz, province_id) select '621c04d4-48c7-43a6-adc8-0a9c118d907e', 'Шахимардан', 'Shohimardon', id from province where name_ru = 'Ферганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '95b9d083-298b-4ec0-a497-a95a72763929', 'Багдад', 'Bogʻdod', id from province where name_ru = 'Ферганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '31f68569-7351-4012-ac7a-38e9295bdf34', 'Музрабад', 'Muzrobod', id from province where name_ru = 'Сурхандарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '9178776f-00fb-42bb-bb00-30e7a25bacbf', 'Салар', 'Salar', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '90ea2894-fdb4-42f6-8c48-e919d7f8c80e', 'Пайшанба', 'Payshanba', id from province where name_ru = 'Самаркандская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'adbaedf3-42dc-4b9f-b206-7dea3cc1e6e1', 'Тайлак', 'Tayloq', id from province where name_ru = 'Самаркандская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '113e4abd-749d-4597-8169-5255033e24d5', 'Риштан', 'Rishton', id from province where name_ru = 'Ферганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '6e5bb8dc-6750-4ee8-8c80-6d6a565a6769', 'Ташморе', 'Tashmore', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '96b0c566-794f-468b-a767-51cc84f4dc4f', 'Гиждуван', 'Gʻijduvon', id from province where name_ru = 'Бухарская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'd5c5cc98-df74-4b3c-ba35-ef182170ad17', 'Тамдыбулак', 'Tomdibuloq', id from province where name_ru = 'Навоийская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'c12e7050-462d-4801-bce0-bace2ccd03ea', 'Канлыкуль', 'Qanlikoʻl', id from province where name_ru = 'Каракалпакстан';

--;;

insert into area (id, name_ru, name_uz, province_id) select '1dcd6d8a-5899-40bb-8f63-d8baceba1725', 'Назарбек', 'Nazarbek', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'e66349a5-d00e-47a7-8fcb-b5429b1474a9', 'Кувасай', 'Quvasoy', id from province where name_ru = 'Ферганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'd781f325-def7-4c09-a7a5-2a863fae5d56', 'Ахунбабаев', 'Oxunboboyev', id from province where name_ru = 'Андижанская область ';

--;;

insert into area (id, name_ru, name_uz, province_id) select '3e13b50f-a18a-4f0a-93ca-1400b757dc57', 'Янгибазар', 'Yangibozor', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '4d9c904a-3fce-4fe8-b7ce-3fcb518e22fd', 'Голиблар', 'Gʻoliblar', id from province where name_ru = 'Джизакская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '826292f5-2d0f-4711-8a31-78b1838629d3', 'Cукок', 'Soʻqoq', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'b0634fad-3a67-4dda-87f7-df700a912b85', 'Янгиабад', 'Yangiobod', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'fd6ebd4a-6418-4ee5-bea4-8e5de6099f18', 'Булакбаши', 'Buloqboshi', id from province where name_ru = 'Андижанская область ';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'f9c9ae2d-b682-4a6d-b2af-2162ea8df3c7', 'Айдаркуль', 'Aydarkoʻl', id from province where name_ru = 'Джизакская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'b24bc2c4-eaa6-421f-b563-a8b085a37855', 'Челак', 'Chust', id from province where name_ru = 'Наманганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'ef8beaed-d734-48cd-b635-d2881e4a79e0', 'Турткуль', 'Toʻrtkoʻl', id from province where name_ru = 'Каракалпакстан';

--;;

insert into area (id, name_ru, name_uz, province_id) select '0bae416d-cbd2-4f86-9b37-96d51c225471', 'Караул', 'Qorovul', id from province where name_ru = 'Хорезмская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '0a88269b-f827-4848-9b7c-b88121e028b0', 'Гузар', 'Gʻuzor', id from province where name_ru = 'Кашкадарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '6114af88-44b0-4345-8db5-a1bc48c38315', 'Паркент', 'Parkent', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '1609718a-3d0f-4ea7-9652-13f38687d28b', 'Уртааул', 'Oʻrtaovul ', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '111fc9cf-c57a-4ae8-99b4-b5437097b738', 'Караулбазар', 'Qorovulbozor', id from province where name_ru = 'Бухарская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'bf0cfa70-82c0-476e-bc64-b749aaf33024', 'Бешкент', 'Beshkent', id from province where name_ru = 'Кашкадарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '923e6e73-4350-4cd8-a7a4-9300514460b1', 'Навруз', 'Navroʻz', id from province where name_ru = 'Сырдарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'e652f8e9-9444-47d3-a904-328b44c5d0c3', 'Карасу', 'Qorasuv', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'eedba3b3-e466-4908-8afb-86620fc3d03e', 'Ширин', 'Shirin', id from province where name_ru = 'Сырдарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'ed1e3997-a5de-46eb-9ca8-fdfacc5e8399', 'Чилек', 'Chelak', id from province where name_ru = 'Самаркандская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'cc6138ca-40e6-4baa-8f0a-c679c2bb9fe0', 'Ходжаабад', 'Xoʻjaobod', id from province where name_ru = 'Андижанская область ';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'df2fed87-c3f5-4fa9-ab0e-7bb847064f65', 'Кургантепа', 'Qoʻrgʻontepa', id from province where name_ru = 'Андижанская область ';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'c6f1c01a-7727-45f4-8da4-18e69c60d0ee', 'Нукус', 'Nukus', id from province where name_ru = 'Каракалпакстан';

--;;

insert into area (id, name_ru, name_uz, province_id) select '0df17958-3644-43da-9081-05ed6d45619a', 'Большой Чимган', 'Katta Chimyon', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '5d323e57-2ada-4f01-8bee-6b34c91d3d74', 'Ахангаран', 'Ohangaron', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '2b61597a-670f-46d4-a3d9-e66ccb539670', 'Пайарык', 'Payariq', id from province where name_ru = 'Самаркандская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '4be5dfec-e563-4f78-9d63-865b231f6d3e', 'Чарвак', 'Chorvoq', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'c0c8a89f-fc6c-499e-9c44-1a4d80cf93e5', 'Каган', 'Kogon', id from province where name_ru = 'Бухарская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '51a8a846-0ce3-4513-add4-bf18eb273e50', 'Байсун', 'Boysun', id from province where name_ru = 'Сурхандарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '1a077a96-9baf-4783-8396-7c6f594f2fc8', 'Сайхун', 'Sayxun', id from province where name_ru = 'Сырдарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'ae94f2a3-568b-494a-aa93-a18e8e3db700', 'Вуадиль', 'Vodil', id from province where name_ru = 'Ферганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'e2ba12c6-5be5-4cc2-8e1a-e94eb653d4ef', 'Навбахор', 'Navbahor', id from province where name_ru = 'Ферганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'e8ca459d-f866-4170-8d17-56ebc78f9720', 'Зиадин', 'Ziadin', id from province where name_ru = 'Самаркандская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '061a4cc2-5795-4367-9005-f79c08d7af0f', 'Гулистан', 'Guliston', id from province where name_ru = 'Сырдарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '4c622bf9-ccd9-4801-9bf2-ffa4b7c3f2b7', 'Навои', 'Navoiy', id from province where name_ru = 'Навоийская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'e56883b2-fc2e-4db7-87fa-17685e86340b', 'Гузалкент', 'Goʻzalkent', id from province where name_ru = 'Самаркандская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '3a43be5d-2f43-47a2-9e06-5d76f4e93750', 'Ургенч', 'Urganch', id from province where name_ru = 'Хорезмская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '6f85579b-3ac6-4b53-b68e-8fd26774124d', 'Акташ', 'Oqtosh', id from province where name_ru = 'Самаркандская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'd309dc41-c7e1-4cea-bac8-6be852e60f5b', 'Бухара', 'Buxoro', id from province where name_ru = 'Бухарская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'd841794a-8390-4345-a321-c64e0cf18200', 'Багат', 'Bogʻot', id from province where name_ru = 'Хорезмская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '20ae1d29-b985-4c8c-b4e3-c7c9c394386a', 'Мархамат', 'Marhamat', id from province where name_ru = 'Андижанская область ';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'bdee84d0-17dc-4cf5-93e9-3b437c284d42', 'Камаши', 'Qamashi', id from province where name_ru = 'Кашкадарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '2aea891b-7ff4-4771-8c89-b96421e0ee48', 'Коксарай', 'Koʻksaroy', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '5bbf0b66-bc97-400d-b428-1e72985640d1', 'Чимбай', 'Chimboy', id from province where name_ru = 'Каракалпакстан';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'fa562aab-dfbc-4399-9f5c-28f92a857538', 'Гурлен', 'Gurlan', id from province where name_ru = 'Хорезмская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'a8e1b77b-d1f9-4eea-ba4b-b04bbf753702', 'Зангиата', 'Zangiota', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '1d93e6a1-5244-47bd-877d-298605d7d6f3', 'Ромитан', 'Romitan', id from province where name_ru = 'Бухарская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '0544bff6-3357-443b-b756-a58899e414f3', 'Бешарык', 'Beshariq', id from province where name_ru = 'Ферганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '7c8e9e1e-9f5f-4096-a896-5023453b17c7', 'Янгикишлак', 'Yangiqishloq', id from province where name_ru = 'Джизакская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '9755d831-91a6-4047-8107-9a81a1077954', 'Алат', 'Olot', id from province where name_ru = 'Бухарская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '66826421-7894-4867-ab6f-34dfb805a89e', 'Дустлик', 'Doʻstlik', id from province where name_ru = 'Джизакская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '12d76a24-35f6-4642-ad14-53d3b9b0f631', 'Ургут', 'Urgut', id from province where name_ru = 'Самаркандская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '12645651-fd13-4019-b81e-80d992ba536f', 'Яккабаг', 'Yakkabogʻ', id from province where name_ru = 'Кашкадарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '3e491b2f-e919-49e7-ac01-4f23e2dad908', 'Самарканд', 'Samarqand', id from province where name_ru = 'Самаркандская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '3b88c10e-aea6-4499-9b8d-32b11010bd3f', 'Андижан', 'Andijon', id from province where name_ru = 'Андижанская область ';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'c048e417-8051-4c3b-8f74-6156b671fecb', 'Булунгур', 'Bulungʻur', id from province where name_ru = 'Самаркандская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'bfb5b20d-b200-4136-ab5a-630324441b3d', 'Маргилан', 'Margʻilon', id from province where name_ru = 'Ферганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '2e2de6ed-dcd5-4181-9409-696c38cdde71', 'Лангар', 'Langar', id from province where name_ru = 'Ферганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '12896b55-7736-4b54-8a35-1fc93f8e8b6f', 'Тахиаташ', 'Taxiatosh', id from province where name_ru = 'Каракалпакстан';

--;;

insert into area (id, name_ru, name_uz, province_id) select '783ff7d1-50b5-4c65-9a54-ed954a1393af', 'Шурчи', 'Shoʻrchi', id from province where name_ru = 'Сурхандарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '0c8baeeb-0dbd-4996-9ff2-5575104058bd', 'Газалкент', 'Gʻazalkent', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '8c221b26-eb52-49c6-b87f-54bb463451f4', 'Мирабад', 'Mirobod', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'ffa97390-384d-44f8-9bc8-c1e91e8d01d1', 'Бешрабат', 'Beshrobot', id from province where name_ru = 'Навоийская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '30d715a2-0821-4300-9295-86d96c771a8f', 'Язъяван', 'Yozyovon', id from province where name_ru = 'Ферганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'b7390a2a-1a0c-40a8-83f1-3946ba7bc771', 'Янги-Нишан', 'Nishon', id from province where name_ru = 'Кашкадарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'd1e14b7d-417b-4f31-8a94-6595399f0a1a', 'Беруни', 'Beruniy', id from province where name_ru = 'Каракалпакстан';

--;;

insert into area (id, name_ru, name_uz, province_id) select '6bc71e16-307e-4f59-8329-c8ab87a1b14a', 'Усмат', 'Oʻsmat', id from province where name_ru = 'Джизакская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'aa5d440f-7a71-4d81-9b1f-554d4d8583c0', 'Кошкупыр', 'Qoʻshkoʻpir', id from province where name_ru = 'Хорезмская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '4e00c90c-2312-4a48-9c0f-72ed09b1aca1', 'Хаваст', 'Xovos', id from province where name_ru = 'Сырдарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '0762712b-1b18-43b0-a279-f1eedc271f06', 'Галлаарал', 'Gʻallaorol', id from province where name_ru = 'Джизакская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'fd552689-8636-4165-880a-e0d4fea96b63', 'Пахтакор', 'Paxtakor', id from province where name_ru = 'Джизакская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '14dbb051-4784-4438-a8da-2ffd89244df2', 'Янгирабат', 'Yangirobot', id from province where name_ru = 'Навоийская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '1f63956d-31e9-4f4b-92d0-6eae216311bf', 'Зафарабад', 'Zafarobod', id from province where name_ru = 'Джизакская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'e4e9f876-9b16-474a-a852-bb318f822722', 'Чартак', 'Chortoq', id from province where name_ru = 'Наманганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'c05018fa-6f7e-4463-b669-31397bf73c1d', 'Баландчакир', 'Balandchaqir', id from province where name_ru = 'Джизакская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'aa9e94c5-2934-4fbd-9196-c0b08e8621ee', 'Кунград', 'Qoʻngʻirot', id from province where name_ru = 'Каракалпакстан';

--;;

insert into area (id, name_ru, name_uz, province_id) select '73c5d24c-e669-40ec-8039-cc32f42269a1', 'Хазарасп', 'Xozarasp', id from province where name_ru = 'Хорезмская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'd11537cd-a462-4398-99f2-745d77454e08', 'Каттакурган', 'Kattaqoʻrgʻon', id from province where name_ru = 'Самаркандская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '5d02a4f6-f422-4065-b090-2bf4c7762c88', 'Карасу', 'Qorasuv', id from province where name_ru = 'Андижанская область ';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'ad07d8c1-2c3e-4acb-8495-a00bc8fd0ca7', 'Бахт', 'Baxt', id from province where name_ru = 'Сырдарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'fb02d106-ce6e-4ba4-9225-a1c000f5e26f', 'Шуманай', 'Shumanay', id from province where name_ru = 'Каракалпакстан';

--;;

insert into area (id, name_ru, name_uz, province_id) select '18f6d1ae-e52e-4f37-8e11-89e06eb6f73c', 'Сарык', 'Sariq', id from province where name_ru = 'Сурхандарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '16d99483-4bc9-4c83-bbb7-5ce499908a0b', 'Кушрабад', 'Qoʻshrobod', id from province where name_ru = 'Самаркандская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '086c0ab3-ec17-4c87-8e81-c6e01da13edd', 'Коканд', 'Qoʻqon', id from province where name_ru = 'Ферганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'cb9d7348-0750-4e52-9ed4-2c25612aa8e6', 'Теренозек', 'Terenozek', id from province where name_ru = 'Сырдарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '75d41dab-ebed-435c-9051-00eb1a4c81e2', 'Хаккулабад', 'Xaqqulobod', id from province where name_ru = 'Наманганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '30c95e9f-3c7e-4ce0-a79a-199067b4cc6c', 'Джаркурган', 'Jarqoʻrgʻon', id from province where name_ru = 'Сурхандарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '7f1ca3de-4ad7-4fff-a6bf-cdd0ff569d51', 'Алтыарык', 'Oltiariq', id from province where name_ru = 'Ферганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '0db809a6-ec31-4443-b644-09a67b830c1f', 'Акалтын', 'Oqoltin', id from province where name_ru = 'Андижанская область ';

--;;

insert into area (id, name_ru, name_uz, province_id) select '9e69e24b-e35f-4c7e-8c37-f25229e99894', 'Заамин', 'Zomin', id from province where name_ru = 'Джизакская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '2ed39626-1f7f-4241-97a0-5812c885fe8f', 'Учкудук', 'Uchquduq', id from province where name_ru = 'Навоийская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '18d16aa4-304a-479a-8395-bac5aa32921a', 'Кегейли', 'Kegeyli', id from province where name_ru = 'Каракалпакстан';

--;;

insert into area (id, name_ru, name_uz, province_id) select '44b5a89e-abab-41c0-a86a-8eed48c44805', 'Янгиюль', 'Yangiyoʻl', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '60d5b1fb-e33f-4f8b-8efc-19a09831a364', 'Бустан', 'Boʻston', id from province where name_ru = 'Каракалпакстан';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'f8215610-fd30-48e1-af0b-d1001c22329c', 'Джизак', 'Jizzax', id from province where name_ru = 'Джизакская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'c104eb08-d613-4aa0-939f-da5de3eb3cdf', 'Ангрен', 'Angren', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'aea378be-7ae0-4e5a-8b72-0043dce1a8c6', 'Вабкент', 'Vobkent', id from province where name_ru = 'Бухарская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '9e8dee70-732a-4079-8b5e-200bc2621204', 'Пахтаабад', 'Paxtaobod', id from province where name_ru = 'Андижанская область ';

--;;

insert into area (id, name_ru, name_uz, province_id) select '5900bbda-8c3f-4ab3-98a9-61bebce9eb8b', 'Кизирик', 'Qiziriq', id from province where name_ru = 'Сурхандарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '480ae213-fcab-457e-877f-2656517d6aa5', 'Учкызыл', 'Uchqizil', id from province where name_ru = 'Сурхандарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '93a427bf-b6b9-4595-9f59-8d7be3b5981f', 'Ташбулак', 'Toshbuloq', id from province where name_ru = 'Наманганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '13db554e-4737-4ac8-acfa-064af08e18ee', 'Учтепа', 'Uchtepa', id from province where name_ru = 'Джизакская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '82ccdacc-914d-44f7-b30b-ff413bb87587', 'Касан', 'Koson', id from province where name_ru = 'Кашкадарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'ce72aa48-c7a5-4757-a2e2-15242e674f21', 'Дурмень', 'Durmen', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '17c0dd92-96a5-4bbd-a96a-dc205964472c', 'Касби', 'Kasbi', id from province where name_ru = 'Кашкадарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'ce60a8ec-5d83-42b4-9e3b-915073e13376', 'Денау', 'Denov', id from province where name_ru = 'Сурхандарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '6572ad11-bb2e-4428-8452-4bdc5d2536a6', 'Яйпан', 'Yaypan', id from province where name_ru = 'Ферганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'e7a3f4cc-9c1f-49ae-86c7-92b16bfcac47', 'Джумашуй', 'Jomashoʻy', id from province where name_ru = 'Наманганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '08b639f3-6390-479d-899f-ff1b7761efe2', 'Карлук', 'Qorlik', id from province where name_ru = 'Сурхандарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'd3e87639-e290-445d-9bbb-0131b2b2d462', 'Красногорск', 'Krasnogorsk', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '63997d8e-d91e-44f5-a9c3-88fb23344e4a', 'Дустабад', 'Doʻstobod', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '878e2d77-3c77-468b-8b8c-a50d00da90d5', 'Нурафшан (Тойтепа)', 'Toʻytepa', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '76cd3687-4780-4655-9b34-4a53f4f3607b', 'Хива', 'Xiva', id from province where name_ru = 'Хорезмская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '1f3c15be-eafe-4dcc-82f8-3f7cc8607993', 'Дарбанд', 'Darband', id from province where name_ru = 'Самаркандская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '83c650b9-b792-448d-a864-0f8fe8a0640c', 'Караузяк', 'Qoraoʻzak', id from province where name_ru = 'Каракалпакстан';

--;;

insert into area (id, name_ru, name_uz, province_id) select '47eafacd-b880-4138-9a99-7da80b63469f', 'Аккурган', 'Oqqoʻrgʻon', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '9e917961-c58f-42db-8a60-5e831bd6b981', 'Пап', 'Pop', id from province where name_ru = 'Наманганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'f6ccaa52-1546-4ac2-82af-43ccc379b7b5', 'Кызылтепа', 'Qiziltepa', id from province where name_ru = 'Навоийская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'eefd05eb-a45d-4fdc-b004-6becb87840e0', 'Пскент', 'Pskent', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '77b734dc-cc2c-4032-b65f-34409687a0da', 'Ангор', 'Angor', id from province where name_ru = 'Сурхандарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'e60f53e5-9adc-46be-9721-04bfcb8d3917', 'Дехканабад', 'Dehqonobod', id from province where name_ru = 'Кашкадарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '977c249b-c9a4-4fdc-ad98-26a028456d02', 'Сардоба', 'Sardoba', id from province where name_ru = 'Сырдарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '0f426ad5-9c9c-4ff4-b324-fa7f1b1b64cc', 'Cырдарья', 'Sirdaryo', id from province where name_ru = 'Сырдарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'ac5b8aea-cfd0-4c95-9bdb-fe4cb2bf7926', 'Ташкент', 'Toshkent', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '9b5dc0cd-3298-48a6-b1c0-24abdd72eaab', 'Каракуль', 'Qorakoʻl', id from province where name_ru = 'Бухарская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'e88e7519-2c48-429e-ae35-31b7eed5cd79', 'Джамбай', 'Jomboy', id from province where name_ru = 'Самаркандская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '021ea888-b10f-45b3-80d5-a801ea325627', 'Термез', 'Termiz', id from province where name_ru = 'Сурхандарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'c0fda7f4-377c-4dbd-ae5a-46273a5108dc', 'Джума', 'Juma', id from province where name_ru = 'Самаркандская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'e1f15f6e-35c5-444b-a040-1e2716d3a9a4', 'Акмангит', 'Oqmangʻit', id from province where name_ru = 'Каракалпакстан';

--;;

insert into area (id, name_ru, name_uz, province_id) select '2b2ce4a9-d4cd-40dd-bc5d-f19fa1070427', 'Шахрихан', 'Shahrixon', id from province where name_ru = 'Андижанская область ';

--;;

insert into area (id, name_ru, name_uz, province_id) select '93ee1694-2cc7-4e04-8e2d-28e4b50e71de', 'Гюлабад', 'Gulobod', id from province where name_ru = 'Самаркандская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'dd69b583-df50-40c5-859e-1eef16607581', 'Чалыш', 'Cholish', id from province where name_ru = 'Хорезмская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '66eceae2-7ce6-4f67-8095-b48eadf97984', 'Бандихон', 'Bandixon', id from province where name_ru = 'Сурхандарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '4c8a97cb-0f5d-4121-821e-117972f35c87', 'Муглан', 'Mugʻlon', id from province where name_ru = 'Кашкадарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'f564a42c-1093-418c-bc35-1f8b020107f1', 'Алмалык', 'Olmaliq', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '39e59a2f-edf6-4fcc-b979-34fce915016c', 'Шафиркан', 'Shofirkon', id from province where name_ru = 'Бухарская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'df078c3a-a7d6-401a-b20f-3d025e9aa8f0', 'Бука', 'Boʻka', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '1924f801-4fad-4dcc-9881-7a5032a6b7e8', 'Касансай', 'Kosonsoy', id from province where name_ru = 'Наманганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'b0b98f2e-e5c3-4465-b3f7-e5b96b9a4646', 'Янгиёр', 'Yangiyer', id from province where name_ru = 'Сырдарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '1634573a-cf73-4de7-974b-a7fad21463d0', 'Кибрай', 'Qibray', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'd66ced34-3226-4eec-a11c-a6d8202c214f', 'Наманган', 'Namangan', id from province where name_ru = 'Наманганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '508d7a70-22a8-4247-b591-454c6cb97a8c', 'Янгикурган', 'Yangiqoʻrgʻon ', id from province where name_ru = 'Ферганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'f66e897d-6d5a-45cb-a8e8-94a153e95228', 'Гульбахор', 'Gulbahor', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '63d889df-f1ae-415a-b887-10324ddb117f', 'Чиракчи', 'Chiroqchi', id from province where name_ru = 'Кашкадарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '079579d7-66bc-4710-b365-635b443089f5', 'Карашина', 'Karashina', id from province where name_ru = 'Кашкадарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '6b1eb9a2-92fc-415e-8683-c853d8b6c235', 'Зарбдар', 'Zarbdor', id from province where name_ru = 'Джизакская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '13cab9ae-95c5-4555-be61-2106178c9dc5', 'Ханабад', 'Xonobod', id from province where name_ru = 'Андижанская область ';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'd4751ca2-677d-418d-bf80-413f8b8ecc4f', 'Туракурган', 'Toʻraqoʻrgʻon', id from province where name_ru = 'Наманганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '61e1ad4a-a0d8-4354-8712-4c306f5375a1', 'Гагарин', 'Gagarin', id from province where name_ru = 'Джизакская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '28d6dd12-c49e-4575-b5ed-1742ce5fd824', 'Мубарек', 'Muborak', id from province where name_ru = 'Кашкадарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '78c0b05c-24e4-4339-9928-5dc7fda347bc', 'Газли', 'Gazli', id from province where name_ru = 'Бухарская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '7fc09e39-ac46-4c46-b9ca-476b24fb024a', 'Туркестан', 'Turkiston', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '92dc99ef-3c47-4abc-9576-fd0e311b8637', 'Боз', 'Boʻz', id from province where name_ru = 'Андижанская область ';

--;;

insert into area (id, name_ru, name_uz, province_id) select '38ab64c1-9979-4240-96b7-efda99b4c4bf', 'Карши', 'Qarshi', id from province where name_ru = 'Кашкадарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'eea84254-38df-4774-86e9-6e9eab979823', 'Кармана', 'Karmana', id from province where name_ru = 'Навоийская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'def18362-2b36-4e42-acb9-75fbd638fdc7', 'Чирчик', 'Chirchiq', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'b35f42f1-8cfe-4669-841d-fac045349611', 'Искандар', 'Iskandar', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '7c0861c0-b68d-4f39-ae81-513826075b1a', 'Муйнак', 'Moʻynoq', id from province where name_ru = 'Каракалпакстан';

--;;

insert into area (id, name_ru, name_uz, province_id) select '0f90fe12-0e03-4fe1-a395-aee3d75b6483', 'Питнак', 'Pitnak', id from province where name_ru = 'Хорезмская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'd0d0b6e0-b0c4-4bbf-ad3d-f5b2217c9420', 'Галаасия', 'Galaosiyo', id from province where name_ru = 'Бухарская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '701b6835-1596-40b4-8b36-66d475a5bfe9', 'Шахрисабз', 'Shahrisabz', id from province where name_ru = 'Кашкадарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'e9f32b92-cb22-4309-957a-4178fdeea208', 'Нурабад', 'Nurobod', id from province where name_ru = 'Самаркандская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '8ce7c827-2714-424d-a9b9-dc4ca6e3916d', 'Чиназ', 'Chinoz', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '7931ed00-5278-41f0-a7f8-2153a2b2c655', 'Сариасия', 'Sariosiyo', id from province where name_ru = 'Сурхандарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'a819c3ac-d560-440e-abb3-a0eac2d0e4a1', 'Канимех', 'Konimex', id from province where name_ru = 'Навоийская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '13cab78f-180a-413c-a942-3642e707a931', 'Раван', 'Ravon', id from province where name_ru = 'Ферганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'f253ce91-834c-45e5-a9c7-e64c5759e785', 'Ханка', 'Xonqa', id from province where name_ru = 'Хорезмская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '8dd07102-7529-4003-a7a5-05aa6cded372', 'Шаргунь', 'Shargʻun', id from province where name_ru = 'Сурхандарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'da316eb9-9f88-4cf0-91ab-e04a3ebf27f8', 'Баяут', 'Boyovut', id from province where name_ru = 'Сырдарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'f448df16-da1c-44c5-a10c-d3d1e30aa45c', 'Марджанбулак', 'Marjonbuloq', id from province where name_ru = 'Джизакская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '84b09091-6791-4758-8604-c84180930065', 'Ходжейли', 'Xoʻjayli', id from province where name_ru = 'Каракалпакстан';

--;;

insert into area (id, name_ru, name_uz, province_id) select '2ce56e41-3724-4637-bde0-5275fa7e9593', 'Талимарджан	', 'Talimarjon', id from province where name_ru = 'Кашкадарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '60dfc577-2709-403a-8c5d-7405c008ba66', 'Халкабад', 'Xalqobod', id from province where name_ru = 'Сурхандарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '599c655b-95d9-4e91-8665-2915efa50b6c', 'Сырдарья', 'Sirdaryo', id from province where name_ru = 'Сырдарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'bc4aac55-b91c-4567-9468-b482f060372b', 'Зарафшан', 'Zarafshon', id from province where name_ru = 'Навоийская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select 'af3fb016-ff08-4346-a8ad-c27ce7eb0ed6', 'Дангара', 'Dangʻara', id from province where name_ru = 'Ферганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '1d84195a-05a6-45c5-b428-b2a431d817ad', 'Бекабад', 'Bekobod', id from province where name_ru = 'Ташкентская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '09847189-03ae-4596-bfc1-d856837bf820', 'Иштыхан', 'Ishtixon', id from province where name_ru = 'Самаркандская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '802f1954-a54a-4154-b88f-0189825ad369', 'Ташлак', 'Toshloq', id from province where name_ru = 'Ферганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '4eaaf0b8-884b-41fb-94e6-802d5bac29af', 'Кува', 'Quva', id from province where name_ru = 'Ферганская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '06438320-8755-419a-85d4-76362793d119', 'Китаб', 'Kitob', id from province where name_ru = 'Кашкадарьинская область';

--;;

insert into area (id, name_ru, name_uz, province_id) select '85dd2a27-8f47-4845-9de9-8d990434991e', 'Фергана', 'Farg‘ona', id from province where name_ru = 'Ферганская область';

--;;

insert into district (id, name_ru, name_uz, area_id) values('714c89a3-11d1-4c55-8685-f6078a0e048d', 'Яккасарайский район', 'Yakkasaroy tumani', 'ac5b8aea-cfd0-4c95-9bdb-fe4cb2bf7926');

--;;

insert into district (id, name_ru, name_uz, area_id) values('56b837c5-c019-4c3b-88a3-ed7fb0b35445', 'Мирабадский район', 'Mirobod tumani', 'ac5b8aea-cfd0-4c95-9bdb-fe4cb2bf7926');

--;;

insert into district (id, name_ru, name_uz, area_id) values('572923fa-53c2-44d9-b38f-bbf4de61000d', 'Юнусабадский район', 'Yunusobod tumani', 'ac5b8aea-cfd0-4c95-9bdb-fe4cb2bf7926');

--;;

insert into district (id, name_ru, name_uz, area_id) values('64078aa3-235b-43ad-8ca0-f30a0e00c94f', 'Яшнабадский район', 'Yashnobod tumani', 'ac5b8aea-cfd0-4c95-9bdb-fe4cb2bf7926');

--;;

insert into district (id, name_ru, name_uz, area_id) values('62fbd217-d271-4810-b2de-e971c1e5fcf9', 'Сергелийский район', 'Sirg‘ali tumani', 'ac5b8aea-cfd0-4c95-9bdb-fe4cb2bf7926');

--;;

insert into district (id, name_ru, name_uz, area_id) values('6bc3c3ec-76c9-4488-a0ed-30820cb3a3d0', 'Бектемирский район', 'Bektemir tumani', 'ac5b8aea-cfd0-4c95-9bdb-fe4cb2bf7926');

--;;

insert into district (id, name_ru, name_uz, area_id) values('386e3689-bc60-4eb2-866b-041ed7445472', 'Алмазарский район', 'Shayxontohur tumani', 'ac5b8aea-cfd0-4c95-9bdb-fe4cb2bf7926');

--;;

insert into district (id, name_ru, name_uz, area_id) values('d5a3e6a6-1349-4856-9e55-5fd415ef3a59', 'Шайхантахурский район', 'Shayxontohur tumani', 'ac5b8aea-cfd0-4c95-9bdb-fe4cb2bf7926');

--;;

insert into district (id, name_ru, name_uz, area_id) values('cb27af3b-62eb-49be-a0bb-abfc8ab6565b', 'Учтепинский район', 'Uchtepa tumani', 'ac5b8aea-cfd0-4c95-9bdb-fe4cb2bf7926');

--;;

insert into district (id, name_ru, name_uz, area_id) values('f6a040e3-22d2-4064-bd32-624533023d49', 'Чиланзарский район', 'Chilonzor tumani', 'ac5b8aea-cfd0-4c95-9bdb-fe4cb2bf7926');

--;;

insert into district (id, name_ru, name_uz, area_id) values('52d0c925-b11d-4ab8-890c-a9b053a029f3', 'Мирзо-Улугбекский район', 'Mirzo-Ulug‘bek tumani', 'ac5b8aea-cfd0-4c95-9bdb-fe4cb2bf7926');

--;;

insert into staff (id, username, password, first_name, role) values ('4e0e53ca-3372-4169-b4ea-872f12b33540', 'root', 'bcrypt+sha512$63863ef94f4f0c3cf25229bd2c8794e1$12$13d690e26abc8aae8a698480a225bfd292fef179a54d74c7', 'Admin', 'admin');

--;;

insert into staff (id, username, password, first_name, role) values ('7ce2e98b-278e-4a55-bef4-5a165a360282', 'inspector', 'bcrypt+sha512$eb7f82c2554bfae029fff54d45843fde$12$0d58c2beb6a688ab50e53fea53b2969e9fe66fc28eb622f4', 'Inspector', 'inspector');
