# Создание и сохранение заказа

1. Клиент отправляет `POST /api/order` в Order Service с JWT и списком `productId` / `quantity`.
2. Order Service объединяет позиции с одинаковым `productId`, суммируя количество, затем вызывает Inventory Service по gRPC для каждого товара.
3. `CheckAvailability` возвращает `available`, `price` и `sale` из одной записи товара. Десятичные значения передаются в protobuf строками, затем преобразуются в `BigDecimal`.
4. Если все товары доступны, Order Service рассчитывает сумму каждой позиции и публикует один `OrderCreatedEvent` в `orders.created`. Ключ сообщения — UUID заказа.
5. Notification Service читает событие и сохраняет каждую позицию отдельной строкой таблицы `orders`. Все позиции одного заказа сохраняются одной транзакцией.

Order Service использует `CreateOrderRequest` для входных данных, `OrderCreatedEvent` для Kafka и `OrderResponse` для HTTP-ответа. JPA-сущности заказа в этом сервисе нет; заказы сохраняет Notification Service.

`sale` — процент скидки от 0 до 100. `price` — цена единицы товара до скидки.
`totalPrice = price × quantity × (1 − sale / 100)`, округление только конечной суммы до двух знаков, `HALF_UP`.

Пример позиции Kafka-сообщения:

```json
{
  "productId": "10000000-0000-4000-8000-000000000007",
  "quantity": 2,
  "price": 25.50,
  "sale": 12.50,
  "totalPrice": 44.63
}
```

После подтверждения отправки в Kafka HTTP-ответ содержит `orderId`, `userId` текущего пользователя из JWT, список `items`, общую стоимость `totalPrice` и `createdAt`.
`totalPrice` заказа — сумма округлённых стоимостей позиций; все позиции относятся к одному `orderId`. В ответе и Kafka-событии каждый товар представлен одной позицией с суммарным количеством.
Идентификаторы товара, заказа и пользователя имеют тип UUID. В REST и Kafka они сериализуются строками; product_id в protobuf также имеет тип string.

Цена и скидка фиксируются при проверке наличия; Notification Service сохраняет полученные значения.
Поля цены и скидки из HTTP-запроса создания заказа не используются.

Таблица Notification Service: `id, order_id, product_id, quantity, price, sale, total_price, user_id`.
`id` — идентификатор строки, `order_id` — общий UUID заказа.
Повторная доставка заказа пропускается; уникальная пара `(order_id, product_id)` защищает таблицу от дубликатов.
После ошибки обработки сообщение повторяется с интервалом 1 секунда; offset подтверждается после успешного сохранения.
Некорректное сообщение также остаётся на повторной обработке и блокирует свою партицию до исправления.

Проверки данных выполняются в Order Service. В Notification Service повторная Jakarta-валидация Kafka-событий не подключена; при сохранении действуют ограничения таблицы `orders`.

## Чтение покупок из Notification Service

HTTP-порт по умолчанию — `8082`, настройка `SERVER_PORT`.

- `GET /api/orders/all` — все строки таблицы `orders`.
- `GET /api/orders/{order_id}` — все покупки одного заказа.
- `GET /api/orders/user/{user_id}` — все покупки пользователя во всех его заказах.

Поиск заказа и пользователя разделён маршрутами, поскольку оба идентификатора имеют тип UUID.
Каждый ответ — JSON-массив покупок с полями `id`, `orderId`, `productId`, `quantity`, `price`, `sale`, `totalPrice`, `userId`.
Покупки отсортированы по идентификатору строки `id`. Если записи не найдены, возвращается `200 OK` и `[]`.
Некорректный UUID в пути возвращает `400 Bad Request`.

## Запуск

Нужны PostgreSQL и Kafka. Базы данных должны быть созданы заранее.

- Inventory Service: `DB_URL` (по умолчанию `jdbc:postgresql://localhost:5432/products`), `DB_USERNAME`, `DB_PASSWORD`; gRPC-порт `9090`.
- Order Service: существующие настройки БД и JWT, `INVENTORY_GRPC_TARGET` (по умолчанию `localhost:9090`).
- Notification Service: `DB_URL` (по умолчанию `jdbc:postgresql://localhost:5432/notifications`), `DB_USERNAME`, `DB_PASSWORD`.
- Order и Notification Service: одинаковые `KAFKA_BOOTSTRAP_SERVERS` (по умолчанию `localhost:9092`) и `ORDER_CREATED_TOPIC` (по умолчанию `orders.created`).

Liquibase выполняет миграции Inventory и Notification Service при запуске.
Сначала обновите Inventory Service, затем Order и Notification Service: для создания заказа требуется gRPC-ответ с ценой и скидкой.
Старые Kafka-события без этих полей или с числовым productId не соответствуют новому контракту.

Первая миграция Inventory Service `001-create-products.sql` создаёт `products.id` сразу с типом UUID и значением по умолчанию `gen_random_uuid()`. Идентификаторы товаров доступны через `GET /api/products`.

Проверка наличия пока не резервирует и не уменьшает остаток товара.
