# Inventory gRPC

Order Service вызывает RPC inventory.v1.InventoryService/CheckAvailability в Inventory Service.

## Контракт и сборка

Единственный контракт находится в inventory-service/src/main/proto/inventory.proto.
Protobuf-плагин Order Service генерирует Java messages и stub из этого файла через sourceDirectories.
Для сборки Order Service нужны обе директории сервисов в текущей структуре репозитория.
Генерируемые файлы находятся в target/generated-sources/protobuf; вручную изменять их не нужно.

Из директории каждого сервиса:

    .\mvnw.cmd -DskipTests compile

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

    boolean available = inventoryGrpcClient.checkAvailability(productId, quantity);

true означает достаточный остаток; false — недостаточный.
Проверка не резервирует и не списывает товар.

Ошибки передаются как StatusRuntimeException:

- INVALID_ARGUMENT — неположительный productId или quantity.
- NOT_FOUND — товар отсутствует.
- INTERNAL — ошибка проверки на сервере.
- UNAVAILABLE — сервер недоступен.
- DEADLINE_EXCEEDED — истёк таймаут.

Сбой связи не преобразуется в false: вызывающий сервис может отдельно обработать недоступность Inventory.

## Проверка

Клиентские TCP-тесты из директории Order Service:

    .\mvnw.cmd '-Dtest=InventoryGrpcClientTests' test

InventoryGrpcServiceTests проверяют сервер через in-process transport и включают method validation ProductService.
Обычный запуск тестов Inventory сейчас блокируется несоответствием старых REST-тестов текущим классам;
gRPC-тесты были проверены отдельно.