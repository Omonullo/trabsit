# videoJarima OAuth2 Resource Server

Этот документ объясняет все шаги обязательные для создания заявки в videoJarima

------------

Чтобы авторизовать client, требуется получить access_token, для этого пройдите Authorization Flow объяснённом oauth файле.

Как только access_token получен, отправляёте его в каждом запросе для определнного пользователя как указано [здесь](https://tools.ietf.org/html/rfc6750#section-2.1 "здесь")

------------

Для того чтобы создать заявку необходимо:

 * получить информацию о пользователе и доступных регионах через сервис **get-report-form**
 * загрузить видео через сервис **upload-video**
 * отправить сформированную заявку через сервис **create-report**

###Формат ошибок: [Read more](https://www.oauth.com/oauth2-servers/server-side-apps/possible-errors/ "Read more")

При возникновиении ошибок сервер вернет такой ответ:
```json
{
   "error": "Название ошибки",
   "error_description": {"поле с ошибкой": ["описание ошибки 1", "описание ошибки 2"]} 
}
``` 


Далее, все ошибки в этой документации будут описываться в виде:

    error - HTTP_code
        error_description_key ["описание ошибки 1", "описание ошибки 2"]    



В query можно указать **locale**, чтобы получить ошибки на определенном языке
Возможны: **ru, uz_cy, uz_la** 


------------


### Ошибки при авторизации
    
    invalid_token - HTTP(401)
        token_client_id - ["Клиент не найден"]
        token_revoked - ["Доступ был отозван пользователем. Запросите доступ ещё раз"]
        token_refresh_expire_time - ["Истек срок годности refresh_token'а. Запросите доступ ещё раз"]
        token_access_expire_time - ["Истек срок годности access_token'а. Обновите с помощью refresh_token'а"]
    
    invalid_client - HTTP(403)
        client_enabled - ["Ваш клиент отключен. Свяжитесь с администратором"]
    
    insufficient_scope - HTTP(403)
        client_allowed_scope - ["Данный ресурс требует scope=scope1,scope2. Вашему клиенту доступен только scope=scope1"]  
        token_scope - ["Данный ресурс требует scope=scope1,scope2. Данный access_token имеет только scope=scope1"]  
    

##### Пример ответа с ошибкой:

```json
{
    "error": "invalid_token",
    "error_description": {
        "token_client_id": [
            "Клиент не найден"
        ]
    }
}
```


##### Важно отметить:
1. scope ресурса должен быть подмноженством scope разрешенному в админке
2. scope ресурса должен быть подмноженством scope для выданного токена

------------

## Сервис upload-video
Этот сервис позволяет асинхронно загружать видео файл на videoJarima server.

    Метод: POST
    URL: http://localhost:3000/api/oauth/report/video
    scope: send-report
 
#### Обязательные поля:
* video - binary file

##### Пример запроса:

```bash
curl --location --request POST 'http://localhost:3000/api/oauth/report/video' \
--header 'Authorization: Bearer 9c19dbf844134df3a06791656e7614c1' \
--header 'Content-Type: multipart/form-data'\
--form 'video=@/C:/Users/Davron/Desktop/IMG_8728.mp4'
```

##### Пример успешного ответа:

```json

{
    "download-url": "download-video-url",
    "url": "video-url",
    "content-type": "video/mp4",
    "id": "guid.mp4"
}
```

#####Возможные ошибки:

    invalid_request - HTTP(400)
        video - ["Отправьте видео", "Видео не может быть длиннее 120 секунд", "Недействительный формат видео"]


##### Пример ответа с ошибкой:

```json
{
    "error": "invalid_token",
    "error_description": {
        "token_client_id": [
            "Клиент не найден"
        ]
    }
}
```



#####Важно отметить:
1. **video** должно быть не длиннее 2 минут


------------


## Сервис get-report-form:
Этот сервис даёт все необходимые данные, для заполнения заявки

    Метод: GET
    URL: http://localhost:3000/api/oauth/report/form
    required-scope: send-report, read-card-phone, read-organization
    optional-scope: read-card-phone, read-organization



##### Пример запроса:

```bash
curl --request GET 'http://localhost:3000/api/oauth/report/form?locale=ru' \
--header 'Authorization: Bearer 9c19dbf844134df3a06791656e7614c1'
```

##### Пример ответа:

```json
{
   "funds":{
      "Благотворительный фонд 1": "URL link",
      "Благотворительный фонд 2":  "URL link"
   },
   "areas":[
      {
         "id":"811a6aa2-2446-4abc-b91a-7c8b61310721",
         "name":"Название города/области",
         "districts":[
            {
               "name":"Название района",
               "yname":"Яндекс название/null",
               "area_id":"811a6aa2-2446-4abc-b91a-7c8b61310721",
               "id":"923cf971-60ba-4e00-8104-b4a9fa2cc3be"
            }
         ]
      }
   ],
   "profile":{
      "card":"8600000000000000",
      "phone":"998000000000"
   },
   "reward-types":{
      "phone":{
         "name":"Пополнение мобильного счета",
         "description":"Доступны операторы: Beeline, Ucell, UzMobile, UMS, Perfectum Mobile. Юридические номера телефонов не принимаются. Операттор HUMANS не принимается."
      },
      "fund":{
         "name":"Благотворительное пожертвование",
         "description":"Вознаграждение будет отправлено на счета выбранного благотворительного фонда. Выбор пожертвовать будет показано нарушителю.",
         "unavailable":true
      },
      "card":{
         "name":"Перевод на карту",
         "description":"Перевод денег на выбранную вами Uzcard карту. Срок действия карты не должен заканчиваться раньше двух месяцев."
      },
      "bank":{
         "name":"Банковский перевод",
         "description":"Банковский денежный перевод на расчетный счет выбранного юридического лица."
      }
   },
   "organizations":[
      {
         "name":"123",
         "bank_account":"12345123451234512345"
      }
   ],
  "offense-types": [
    {
      "id": 1,
      "name_ru": "Не соблюдение правил разметки",
      "name_uz_cy": "Йўл чизиқларга бўйсунмаслик",
      "name_uz_la": "Yo'l chiziqlarga bo'yunmaslik",
      "show_details": true
    },
    {
      "id": 2,
      "name_ru": "Нарушение правил остановки",
      "name_uz_cy": "Тўхташ ва тўхтаб туриш қоидабузарлиги",
      "name_uz_la": "To'xtash va to'xtab turish qoidabuzarligi",
      "show_details": false
    }
  ]
}
``` 

##### Важно отметить:
1. Если в scope вашего токена нет "read-organization",
тогда массив **organizations** будет отсутствовать
2. Если в scope вашего токена нет "read-card-phone",
  тогда объект **profile** будет отсутствовать.

------------

## Сервис create-report

Данный сервис создаёт заявку

    Метод: POST
    URL: http://localhost:3000/api/oauth/reports
    required-scope: send-report

 
Всю необходимую информацию можно получить с помощью сервиса **get-report-form**
 
 
##### Описание полей:

    {
        address: "string, адрес нарушения"
        area_id: "id области (см. сервис get-report-form)"
        district_id: "id района (см. сервис create-form)"
        lng: "долгота",
        lat: "широта",
        incident_time: "время нарушения, HH:mm",
        incident_date: "дата нарушения, DD.MM.YYYY",
        video_id: "id видео полученный от сервиса upload-video",
        with_extra_video: "boolean, учитывать ли extra video",
        extra_video_id: "string, id видео полученный от сервиса upload-video",
        extra_video_type: "string, тип extra_video: 'remake', 'sequel', 'rear'",
        offenses: {объеденённые offense объекты, максимум 15 штук (см. ниже)"}}
        reward_params: {объект reward_params (см. ниже)}
    }
    
объект offense:

    { 
        "уникальный id":{
            "vehicle_id": "string, номер машины"
            "testimony": "string, описание нарушения. Не обязательно."
            "type_id": "id типа нарушения (см. сервис get-report-form)"
        }
    }

объект reward_params, должен содержать только один ключ:
    
    {
        phone: "string, номер телефона, формата 998\d{9}"
        card: "string, Uzcard карта формата 8600\d{12}"
        bank: "string, формата \d{20}, расчетный счёт Юрлица указанного в базе" 
        fund: "string, название фонда"
    }


##### Пример запроса:

```bash
curl --location --request POST 'http://localhost:3000/api/oauth/reports?locale=ru' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer addbc81a2d3e4715a316a739ce769c59' \
--data-raw '{
   "offenses":{
      "0":{
         "vehicle_id":"D 123123",
         "testimony":"TEST",
         "type_id": 2
      },
      "1":{
         "vehicle_id":"D 999999",
         "testimony":"TEST"
         "type_id": 1
      }
   },
   "video_id":"012b018a3f2d4c369b406211a3212377.mp4",
   "lat":41.283463,
   "lng":69.339789,
   "address":"TEST ADDRESS",
   "incident_date":"17.03.2020",
   "incident_time":"0:10",
   "area_id":"811a6aa2-2446-4abc-b91a-7c8b61310721",
   "district_id":"b464be85-d850-428f-86b8-3e752a3912dc",
   "with_extra_video":true,
   "extra_video_id":"9823e02fc9e94d5b851c5fb39bda89cd.mp4",
   "extra_video_type":"rear",
   "reward_params":{
      "card":"8600 4904 9927 2235"
   }
}'
```

##### Пример успешного ответа:

```json
{
    "video": {
        "download-url": "url",
        "url": "url",
        "content-type": "video/mp4"
    },
    "address": "TEST ADDRESS",
    "extra_video": {
        "download-url": "url",
        "url": "url",
        "content-type": "video/mp4"
    },
    "number": 6,
    "locale": null,
    "incident_time": "2020-03-17T00:10:00",
    "district_id": "b464be85-d850-428f-86b8-3e752a3912dc",
    "extra_video_type": "Задняя камера",
    "thumbnail": "img url",
    "status": "created",
    "area_id": "811a6aa2-2446-4abc-b91a-7c8b61310721",
    "id": "ec006549-4809-4b2e-98a5-28e979f44e8f",
    "create_time": "2020-03-19T06:54:49.745",
    "review_time": null,
    "reward_params": {
        "card": "8600490499272235"
    },
    "lat": 41.283463,
    "lng": 69.339789
}
```


##### Ошибки при отправке формы
    
    invalid_request - HTTP(400)
        reward_params - ["Выберите вознаграждение", "Вознаграждение неверно"]
            phone - ["*Номер телефона неверен"]
            card - ["Карта введена неверно", "Карты банка КДБ не поддерживаются", "Сервис проверки карты не доступен"]
            bank - ["Рассчетный счет неверный"]
            fund - ["Данного фонда не существует"]
        address - ["Укажите адрес"]
        area_id - ["Указана неправильная область", "Указанная область недействительна"]
        district_id - ["Указан неправильный район", "Указанный район недействителен"]
        lng - ["Укажите долготу", "Долгода неверна"]
        lat - ["Укажите широту", "Широта неверна"]
        incident_time - ["Укажите время"]
        incident_date - ["Дата указана неверно", "Нарушение должно быть не старше трех дней"]
        video_id - ["Укажите видео", "Указанного видео не существует. Повторите отправку"]
        extra_video_id - ["Укажите тип видео", "Укажите видео", "Указанного видео не существует. Повторите отправку"]
        offenses - ["Укажите нарушения", "Вы можете добавить до 15 нарушений"]
             vehicle_id - ["Укажите номер машины"]
             type_id - ["Укажите тип нарушения"]



##### Пример ответа с ошибкой:

```json
{
   "error":"invalid_request",
   "error_description":{
      "reward_params":{
         "card":[
            "Укажите номер карты"
         ]
      },
      "offenses":{
         "0":{
            "testimony":[
               "Укажите описание нарушения"
            ]
         }
      },
      "video_id":[
         "Указанного видео не существует. Повторите отправку"
      ],
      "extra_video_id":[
         "Указанного видео не существует. Повторите отправку"
      ]
   }
}
```
