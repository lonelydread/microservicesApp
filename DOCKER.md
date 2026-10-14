# Запуск microservicesApp через Docker Compose

Нужен Docker Desktop с Linux containers и Compose v2. Dockerfile каждого приложения собирает его на Java 21 с общим модулем grpc-contract, если он нужен, и запускает от непривилегированного пользователя. Java и Maven на хосте не требуются.

Из корня проекта в PowerShell (если `.env` уже есть, используйте его):

```powershell
Copy-Item .env.example .env
docker compose up --build -d --wait --wait-timeout 240
docker compose ps
```

Пример содержит пароль БД и JWT-ключ для локальной разработки. Для другого окружения задайте собственные значения. Новый JWT-ключ можно создать:

```powershell
[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

Compose запускает три приложения, три отдельных PostgreSQL 17 и Kafka 4.1.1 в режиме KRaft. Liquibase применяет миграции при запуске приложений. Топик `orders.created` создаётся существующими KafkaConfig.

| Компонент | Адрес с хоста |
| --- | --- |
| Inventory REST | http://localhost:8080/api/products |
| Inventory gRPC | localhost:9090 |
| Order REST | http://localhost:8081 |
| Notification REST | http://localhost:8082/api/orders/all |
| PostgreSQL inventory: products | localhost:5432 |
| PostgreSQL order: order_service | localhost:5433 |
| PostgreSQL notification: notifications | localhost:5434 |
| Kafka | localhost:9092 |

Порты меняются в `.env`. Все опубликованные порты привязаны к `127.0.0.1`. Внутри Docker используются `inventory-service:9090`, `kafka:19092` и DNS-имена БД на порту `5432`.

Compose ждёт готовности PostgreSQL и Kafka перед запуском зависимых приложений. Order также ждёт открытых REST- и gRPC-портов Inventory. Healthcheck приложений проверяет TCP-порты, а не полный бизнес-сценарий.

```powershell
docker compose logs -f order-service inventory-service notification-service
docker compose down
```

Данные БД и Kafka сохраняются в именованных volumes после `down`. Изменение пароля в `.env` не меняет пароль существующей БД. Полное удаление данных локального стенда:

```powershell
docker compose down -v
```

Сборка отдельного образа из корня:

```powershell
docker build -f order-service/Dockerfile -t microservicesapp-order-service .
```

Проверка Compose без запуска контейнеров:

```powershell
docker compose --env-file .env.example config --quiet
```
