# grpc-contract

Общий Maven-модуль с protobuf-сообщениями и gRPC stubs для Inventory Service и Order Service.
Исходный контракт: `src/main/proto/inventory.proto`.

Генерация Java выполняется только в этом модуле. Результат — обычный библиотечный JAR,
без Spring Boot repackage. Версии protobuf, gRPC и генератора управляются тем же
Spring Boot parent, который используют сервисы; Spring Boot runtime в JAR не добавляется.

Сборка обоих gRPC-сервисов из корня репозитория:

```powershell
.\order-service\mvnw.cmd -f pom.xml -pl inventory-service,order-service -am -DskipTests compile
```

Установка контракта для независимой сборки или запуска сервисов:

```powershell
.\order-service\mvnw.cmd -f grpc-contract/pom.xml clean install
```

Зависимость в обоих сервисах:

```xml
<dependency>
    <groupId>ru.microservicesApp</groupId>
    <artifactId>grpc-contract</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

После изменения proto пересоберите контракт и зависимые сервисы. Сгенерированные
файлы в `target/generated-sources/protobuf` не редактируйте вручную.
Для независимых сборок на других машинах публикуйте обычный JAR и POM этого модуля
в используемый Maven-репозиторий.
