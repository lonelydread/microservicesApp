# Inventory gRPC

Order Service вызывает RPC inventory.v1.InventoryService/CheckAvailability в Inventory Service.

## Контракт и сборка

Единственный контракт находится в `grpc-contract/src/main/proto/inventory.proto`.
Модуль `ru.microservicesApp:grpc-contract:0.0.1-SNAPSHOT` генерирует Java messages и gRPC stubs
и упаковывает их в обычный JAR. Inventory Service и Order Service подключают этот JAR
как Maven-зависимость и больше не запускают генерацию самостоятельно.
Java package сгенерированных классов сохранён, поэтому существующие импорты не меняются.
Генерируемые файлы находятся в `grpc-contract/target/generated-sources/protobuf`;
вручную изменять их не нужно.

product_id передаётся строкой с UUID. В Java идентификатор товара имеет тип UUID в Inventory, Order и Notification Service.
Переход с int64 на string меняет wire-контракт. Inventory и Order Service нужно пересобрать и обновить согласованно.

Из корня репозитория собрать оба gRPC-сервиса вместе с контрактом:

    .\order-service\mvnw.cmd -f pom.xml -pl inventory-service,order-service -am clean -DskipTests compile

Для отдельных сборок сервисов сначала установить контракт в локальный Maven-репозиторий:

    .\order-service\mvnw.cmd -f grpc-contract/pom.xml clean install

После этого из директории каждого сервиса:

    .\mvnw.cmd clean -DskipTests compile

При изменении proto нужно заново собрать контракт и зависимые сервисы.
Для сборки сервисов на другой машине JAR и POM контракта должны быть доступны
в её локальном или общем Maven-репозитории.

## Подключение

Inventory Service слушает gRPC-порт 9090 (GRPC_SERVER_PORT).
Order Service использует именованный канал inventory и по умолчанию подключается к localhost:9090.
Локальное соединение настроено без TLS.

Настройки Order Service:

- INVENTORY_GRPC_TARGET — адрес Inventory, например inventory-service:9090 при работе в контейнерах.
- INVENTORY_GRPC_TIMEOUT — deadline каждого вызова, по умолчанию 3s.
- HTTP-порт Order — 8081 (SERVER_PORT), чтобы не конфликтовать с HTTP-портом Inventory 8080.

Для запуска самих приложений также нужны их настройки БД и, для Order Service, JWT_SECRET_BASE64.

## Вызов

Внедри InventoryGrpcClient через конструктор сервиса:

    UUID productId = UUID.fromString("10000000-0000-4000-8000-000000000007");
    ProductAvailability availability = inventoryGrpcClient.checkAvailability(productId, quantity);

availability.available() означает достаточный остаток; price() и sale() содержат цену и процент скидки.
Проверка не резервирует и не списывает товар.

Ошибки передаются как StatusRuntimeException:

- INVALID_ARGUMENT — некорректный UUID или неположительный quantity.
- NOT_FOUND — товар отсутствует.
- INTERNAL — ошибка проверки на сервере.
- UNAVAILABLE — сервер недоступен.
- DEADLINE_EXCEEDED — истёк таймаут.

Сбой связи не преобразуется в false: вызывающий сервис может отдельно обработать недоступность Inventory.

## База данных

001-create-products.sql создаёт products.id сразу с типом UUID и значением по умолчанию gen_random_uuid().
Идентификаторы товаров можно получить через REST API. Миграция 002 добавляет поле sale.

## Проверка

Из Inventory Service:

    .\mvnw.cmd test

Из Order Service:

    .\mvnw.cmd '-Dtest=OrderControllerTests,InventoryGrpcClientTests,OrderProducerIntegrationTests,OrderMapperTests' test

InventoryGrpcServiceTests проверяют сервер через in-process transport, UUID, ошибки и валидацию DTO.
InventoryServiceApplicationTests проверяет операции с UUID через JPA на H2.
