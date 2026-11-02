# binance-price-service

Сервис раз в заданный интервал забирает среднюю цену пары с Binance (`/api/v3/avgPrice`), сохраняет в PostgreSQL и отдает через REST. По умолчанию пара BNBUSDT.

## Запуск

```
docker compose up --build
```

Приложение поднимется на localhost:8080. Если надо запустить его локально, а в докере оставить только базу:

```
docker compose up -d postgres
./mvnw spring-boot:run
```

## Переменные окружения

Можно задать через `.env` (пример в `.env.example`) или прямо в командной строке.

- `BINANCE_POLL_INTERVAL` - интервал опроса в формате ISO-8601, по умолчанию `PT30S`
- `BINANCE_SYMBOL` - торговая пара, по умолчанию `BNBUSDT`
- `BINANCE_BASE_URL` - адрес API, по умолчанию `https://api.binance.com`
- `BINANCE_CONNECT_TIMEOUT` и `BINANCE_READ_TIMEOUT` - таймауты запросов к Binance, `PT5S` и `PT10S`
- `TREND_FLAT_THRESHOLD_PERCENT` - при каком изменении цены в процентах тренд еще считается FLAT, по умолчанию `0`
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` - подключение к БД
- `SERVER_PORT` - порт приложения, по умолчанию `8080`
- `APP_PORT` - порт на хосте, используется только в docker compose

## API

Даты передаются в формате `2026-09-01T00:00:00Z`, границы периода включаются.

```
GET /api/v1/prices/latest
GET /api/v1/prices/average?from=...&to=...
GET /api/v1/prices?from=...&to=...&page=0&size=20
GET /api/v1/prices/trend?from=...&to=...
```

Например:

```
curl "localhost:8080/api/v1/prices/average?from=2026-09-01T00:00:00Z&to=2026-09-30T23:59:59Z"
```

Тренд считается по первой и последней цене за период, значения UPWARD, DOWNWARD и FLAT. Если за период нет данных, вернется 404, если `from` позже `to` или параметры некорректные, 400. Тело ошибки: `{time, status, message, path}`.

## Как это работает

Опрос сделан через `@Scheduled(fixedDelay)`, поэтому запросы не накладываются друг на друга. Если Binance не ответил или вернул ошибку, она пишется в лог, а следующий опрос идет по расписанию. При 429 и 418 читается заголовок `Retry-After`, и до конца паузы запросы к Binance не отправляются.

Binance отдает один и тот же снимок цены, пока не пройдет новая сделка, поэтому дубли отсекаются по паре `(symbol, closeTime)`: перед сохранением идет проверка, плюс на эту пару стоит уникальный индекс.

Цены везде `BigDecimal`, в БД `NUMERIC`.

## Тесты

```
./mvnw test
```

Для интеграционного теста нужен запущенный Docker, Testcontainers поднимает в нем PostgreSQL.