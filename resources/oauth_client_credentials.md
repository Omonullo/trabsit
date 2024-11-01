# videoJarima OAuth2 Authorization Server

Всё что написанно в этой документации просто реализация  [rfc6749](https://tools.ietf.org/html/rfc6749 "rfc6749") OAuth2 Framework.

Все изменения или отхождения от протокола обозначаются *italic*'ом  (в основном это дополнения)


Для того чтобы авторизироваться, требуется получить access_token, для этого нужно пройти через все шаги Auth flow

------------

##Error response:

При возникновении ошибкок в сервисах refresh_token и access_token сервер вернёт json объект:
с ключами:

* error - название ошибки
* error_description - удобочитаемое сообщение
* ***errors*** - json объект с полями содержащими ошибки


При возникновении ошибкок в сервисе Authorization request videoJarima перенаправит пользователя на указанный error_redirect_uri, с x-www-form-urlencoded параметрами указанными выше
[More](https://tools.ietf.org/html/rfc6749#section-4.1.2.1 "More")

------------


### Access token request [More](https://tools.ietf.org/html/rfc6749#section-4.1.3 "More")

В этом шаге, клиент(не пользователь) должен сделать POST запрос на
https://dyhxx.ejarima.uz/oauth/access_token
со следующими **x-www-form-urlencoded** параметрами:

#### Обязательны:
* **grant_type**  = "client_credentials"
* **client_id**
* **client_secret**


#####Пример:
```
curl --request POST \
  --url http://localhost:3000/oauth/access_token \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --cookie locale=ru \
  --data client_id=3eddf342-10df-4da4-90a4-485a5de68524 \
  --data grant_type=client_credentials \
  --data client_secret=3eddf342-10df-4da4-90a4-485a5de6 \
```


#####Возможные ошибки:
    invalid_client
        client_id- ["Не указан client_id"]
        client_secret- ["Неверные данные client_id или client_secret"]
        client_enabled- ["Ваш клиент отключен. Свяжитесь с администратором"]
    invalid_request
        code - ["Не указан code"]
        grant_type - ["Не указан grant_type"]
    invalid_grant
        code - ["Предоставленный код не верен или истек"]
        client_id - ["Предоставленный client_id не совпадает"]
        redirect_uri - ["Не указан redirect_uri. Эта ошибка не возникает если пользователь не отправляет redirect_uri", "Предоставленный redirect_uri не совпадает"]
    unsupported_grant_type
        grant_type - [ "Предоставленный grand_type не совпадает c сохранённым response_type"]


##### Note:
1. **redirect_uri** должен быть идентичным, если он был предоставлен в auth_request, иначе опционален
2. **code** должен быть равен тому, что был выдан в auth_response
3. Все предыдущие токены для этого пользователя с этим клиентом будут отозваны, поэтому убедитесь что вы указали правильный scope


------------


### Access token response [More](https://tools.ietf.org/html/rfc6749#section-4.1.4 "More")

Если всё указано верно, тогда сервер вернет такой ответ:

```json
{
    "access_token": "8090f987c86b425db800ceb795bfdb55",
    "refresh_token": "d6d2fa70a9b54913a51f6cdfd901cd11",
    "token_type": "bearer",
    "expires_in": 86400,
    "refresh_expires_in": 2592000,
    "scope": "send-report,read-user-private"
}
``` 

##### Note:
1. **access_token** обычно быстро истекает, используйте **refresh_token** чтобы обновить его
2. **refresh_token** так же имеет срок годности, когда он истекает, клиент обязан пройти auth flow заново
3. **expires_in** и **refresh_expires_in** даны в секундах 
4. **scope** может отличатся, если пользователь не разрешил определенный scope (на этом шаге scope отправляется *всегда, вне зависимости от того, был ли он изменён*)
5. **expires_in** это срок годности для access_token в секундах, если истек, используйте **refresh_token** чтобы обновить


------------


### Refresh token request [More](https://tools.ietf.org/html/rfc6749#section-6 "More")

Когда **access_token** истекает, требуется обновить его с помощью **refresh_token** полученном в Access token response. 

Чтобы обновить access token клиент должен сделать POST запрос на
https://dyhxx.ejarima.uz/oauth/refresh_token
со следующими **x-www-form-urlencoded** параметравми:


#### Обязательны:
* **grant_type**  = "refresh_token"
* **client_id**
* **client_secret**
* **refresh_token**


#### Опциональны:
* **scope** - чтобы обновить scope


#####Возможные ошибки:

    invalid_request
        grant_type - ["Не указан code"]
        refresh_token - ["Не указан grant_type"]
    invalid_grant
        refresh_token - ["Не найден refresh_token"]
        revoked - ["Доступ был отозван пользователем. Запросите доступ ещё раз"]
        refresh_expire_time - ["Истек срок годности refresh_token'а. Запросите доступ ещё раз"]
        client_enabled - ["Ваш клиент отключен. Свяжитесь с администратором"]
    unsupported_grant_type
        grant_type - ["Данный grant_type не поддерживается. Поддерживаются: refresh_token"]




##### Важно отметить:
1. **refresh_token** так же имеет срок годности, когда он истекает, клиент обязан пройти auth flow заново


------------


### Refresh token response
Ответ идентичен ответу в Access token response


##### Важно отметить:
1. scope должен быть под множеством выданного scope
2. **scope** должен быть под множеством scope выданного в access token response, или если не указан, будет учитываться как одинаковый.
    
    На пример: 
    
    Токен был выдан со scope=send-report,read-user-private
    Тогда, если обновить токен со scope=send-report, вы обновить scope вашего токена до send-report
   
3. Для каждого refresh token request, оба, и access, и refresh токены будут обновлены, старые будут отозваны. **refresh_expires_in** не изменится
4. Если клиент желает обновить scope до чего-то отличного от изначального, он должен пройти auth flow снова



