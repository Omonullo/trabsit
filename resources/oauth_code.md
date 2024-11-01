# videoJarima OAuth2 Authorization Server

Всё что написано в этой документации реализация  [rfc6749](https://tools.ietf.org/html/rfc6749 "rfc6749") OAuth2 Framework.

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


##Authorization request: [More](https://tools.ietf.org/html/rfc6749#section-4.1.1 "More")
Клиент (например: TG_Bot) обязан перенаправить пользователя на эту ссылку:
https://dyhxx.ejarima.uz/oauth/authorize
с query params определенными ниже:

#### Обязательны:
* request_type = "code",
* client_id

#### Опциональны:
* redirect_uri
* scope
* state (0-32 characters)
* *login*
* *debug*


#####Пример:

```    
http://dyhxx.ejarima.uz/oauth/authorize?
    client_id=79df68ee-83b4-43be-babf-f1458d8e73ab&
    state=some_TG_BOT_redis_token&
    response_type=code&
    redirect_uri=https://telegram.org/&
    login=true
```

#####Возможные ошибки:

    invalid_request
        video - ["Отправьте видео", "Видео не может быть длиннее 120 секунд", "Недействительный формат видео"]
        client_id - ["Указан не существующий client_id"]
        response_type - ["Не указан response_type"]
        redirect_uri - ["redirect_uri должен быть одни из этих: "]
    unsupported_response_type
        response_type - ["Данный response_type не поддерживается. Поддерживаются: code"]
    invalid_scope
        scope - ["Предоставленный scope превышает разрешенный клиенту scope"]

Эти ошибки будут перенаправлены в качестве query-params на указанный вами error_redirect_uri


##### Важно отметить:
1. **redirect_uri** должен быть одним из определенных в админской панели
2. Если **redirect_uri** не указан, тогда будет использоваться первый uri указанный в админке
3. **scope** это лист значений разделенный запятыми.

    Например: **scope=send-report,read-user-private**
    
4. **redirect_uri** может отличатся только в query параметрах

    Например: http://google.com эквивалентно http://google.com?q=Search
    
5. **scope** должен быть подмножеством разрешенного scope в админской панели или не указан
6. Если **scope** не указан, будет использованно значение поумолчанию
7. ***login*** используется, чтобы заставить пользователя залогинится, даже если его сессия не истекла
8. ***debug*** используется, чтобы отключить redirect при ошибках
9. Если на данном шаге возникается ошибка, videoJarima перенаправляет пользователя на указанный redirect_uri(если такой имеется), с параметрами error, error_description, errors(более подробно ниже).


------------


##Authorization response: [More](https://tools.ietf.org/html/rfc6749#section-4.1.2 "More")

После того как пользователь даст разрешение на работу с выбранным scope, его браузер будет перенаправлен(HTTP 302) на определенный в админской панели **redirect_uri**

* **code** (32 characters string)
* **state** (0-32 characters string)

###### Важно отметить:
1. state не меняется на протижении всего auth flow, и предназначен для клиента

После того как клиент получил code, он должен перейти к **Access token request** чтобы получить access token.


------------


### Access token request [More](https://tools.ietf.org/html/rfc6749#section-4.1.3 "More")

В этом шаге, клиент должен сделать POST запрос на
https://dyhxx.ejarima.uz/oauth/access_token
со следующими **x-www-form-urlencoded** параметрами:

#### Обязательны:
* **grant_type**  = "code"
* **client_id**
* **client_secret**
* **code**

#### Опциональны:
* **redirect_uri**

#####Пример:
```shell
curl --location --request POST 'https://dyhxx.ejarima.uz/oauth/access_token' \
--header 'Content-Type: application/x-www-form-urlencoded' \
--data-urlencode 'code=0821dee56d074c9d9ff6d67c6428627c' \
--data-urlencode 'client_id=66261405-4b4c-4cc7-8510-917561f6a459' \
--data-urlencode 'grant_type=code' \
--data-urlencode 'redirect_uri=https://telegram.org/' \
--data-urlencode 'client_secret=2446bc5628194db79952fbd258c28535'
```


#####Возможные ошибки:
    invalid_client
        client_id- ["Не указан client_id"]
        client_secret- ["Не указан client_secret", "Неверные данные client_id или client_secret"]
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
    
    Например: 
    
    Токен был выдан со scope=send-report,read-user-private
    Тогда, если обновить токен со scope=send-report, вы обновить scope вашего токена до send-report
   
3. Для каждого refresh token request, оба, и access, и refresh токены будут обновлены, старые будут отозваны. **refresh_expires_in** не изменится
4. Если клиент желает обновить scope до чего-то отличного от изначального, он должен пройти auth flow снова



