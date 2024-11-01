 
# Документация

 
## Аутентификация
Все отправляемые запросы должны иметь в хедерах Authorization ключ, где после слова "Bearer" идет непосредственно сам токен
####Пример:
```
Authorization: Bearer YkfezVmgKvt5nNxsLUCDyt4SrF4mKb
```


В случае неудачной аутентификации в ответе вернется HTTP 403
```json
{
  "error": "Доступ запрещен"
}
```


### Уведомление о завершении постановления за нарушение ПДД


#### Запрос
```bash
curl -X POST \
     http://dyhxx.ejarima.uz/offense/notify \
    -H 'Authorization: Bearer YkfezVmgKvt5nNxsLUCDyt4SrF4mKb' \
    -H 'Content-Type: application/json' \
    -d '{
            "pId": "7e3cca6531e94e2782e8626b4ad2bab0",
            "pSeryNumber": "10",
            "pDate":"24.12.2018",
            "pStatus":208
        }'
```

#### Ответ
В ответ вернется объект следующего вида. 
```json
{
    "AnswereId": 1,
    "AnswereMessage": "Ok",
    "AnswereComment": "Comment"
}
```

Значения всех полей можно найти в технической инструкции
