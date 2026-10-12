package ru.microservicesApp.notificationService.kafka;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import jakarta.validation.Validator;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.json.JsonMapper;
import ru.microservicesApp.notificationService.kafka.event.OrderCreatedEvent;
import ru.microservicesApp.notificationService.kafka.event.OrderItemEvent;
import ru.microservicesApp.notificationService.repository.OrderRepository;
import ru.microservicesApp.notificationService.service.OrderService;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-order-flow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.kafka.consumer.group-id=notification-order-flow-tests",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer"
})
@EmbeddedKafka(partitions = 1, topics = "orders.created", bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class OrderConsumerIntegrationTests {
    private static final UUID FIRST_PRODUCT_ID = UUID.randomUUID();
    private static final UUID SECOND_PRODUCT_ID = UUID.randomUUID();
    private static final UUID THIRD_PRODUCT_ID = UUID.randomUUID();
    @Autowired OrderRepository repository;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired EmbeddedKafkaBroker broker;
    @Autowired Validator validator;
    @MockitoSpyBean OrderService orderService;

    @BeforeEach
    void clearOrders() {
        repository.deleteAll();
    }

    @Test
    void savesEveryPositionWithPricingAndIgnoresRedelivery() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BigDecimal maximumPrice = new BigDecimal("99999999999999999.99");
        BigDecimal maximumTotal = maximumPrice.multiply(BigDecimal.valueOf(Integer.MAX_VALUE));
        OrderCreatedEvent event = new OrderCreatedEvent(orderId, userId, List.of(
                new OrderItemEvent(FIRST_PRODUCT_ID, 2, new BigDecimal("25.50"), new BigDecimal("12.50"), new BigDecimal("44.63")),
                new OrderItemEvent(SECOND_PRODUCT_ID, Integer.MAX_VALUE, maximumPrice, BigDecimal.ZERO, maximumTotal)), Instant.now());
        send(event);
        long duplicateOffset = send(event);
        awaitCommitted(duplicateOffset);

        var rows = repository.findAll();
        assertEquals(2, rows.size());
        var first = rows.stream().filter(row -> FIRST_PRODUCT_ID.equals(row.getProductId())).findFirst().orElseThrow();
        assertNotNull(first.getId());
        assertEquals(orderId, first.getOrderId());
        assertEquals(userId, first.getUserId());
        assertEquals(2, first.getQuantity());
        assertEquals(new BigDecimal("25.50"), first.getPrice());
        assertEquals(new BigDecimal("12.50"), first.getSale());
        assertEquals(new BigDecimal("44.63"), first.getTotalPrice());
        var second = rows.stream().filter(row -> SECOND_PRODUCT_ID.equals(row.getProductId())).findFirst().orElseThrow();
        assertEquals(maximumPrice, second.getPrice());
        assertEquals(maximumTotal, second.getTotalPrice());
        verify(orderService, atLeast(2)).saveOrder(any());
    }

    @Test
    void retriesFailedPersistenceBeforeCommittingKafkaOffset() throws Exception {
        doThrow(new TransientDataAccessResourceException("Temporary database outage"))
                .doCallRealMethod().when(orderService).saveOrder(any());
        OrderCreatedEvent event = new OrderCreatedEvent(UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderItemEvent(THIRD_PRODUCT_ID, 1, new BigDecimal("25.50"), new BigDecimal("100.00"),
                        new BigDecimal("0.00"))), Instant.now());
        long offset = send(event);
        awaitCommitted(offset);
        assertEquals(1, repository.count());
        assertEquals(new BigDecimal("0.00"), repository.findAll().getFirst().getTotalPrice());
        verify(orderService, atLeast(2)).saveOrder(any());
    }

    @Test
    void rollsBackEveryPositionWhenOneDatabaseInsertFails() {
        OrderCreatedEvent event = new OrderCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), List.of(
                new OrderItemEvent(FIRST_PRODUCT_ID, 2, new BigDecimal("25.50"), BigDecimal.ZERO, new BigDecimal("51.00")),
                new OrderItemEvent(SECOND_PRODUCT_ID, 0, new BigDecimal("25.50"), BigDecimal.ZERO, BigDecimal.ZERO)), Instant.now());
        assertThrows(DataIntegrityViolationException.class, () -> orderService.saveOrder(event));
        assertEquals(0, repository.count());
    }

    @Test
    void validatesNestedIncomingPricingFields() {
        OrderCreatedEvent missing = new OrderCreatedEvent(null, null,
                List.of(new OrderItemEvent(null, null, null, null, null)), null);
        assertEquals(8, validator.validate(missing).size());
        OrderCreatedEvent outOfRange = new OrderCreatedEvent(UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderItemEvent(FIRST_PRODUCT_ID, 1, new BigDecimal("-1"),
                        new BigDecimal("100.01"), new BigDecimal("-1"))), Instant.now());
        assertEquals(3, validator.validate(outOfRange).size());
    }

    private long send(OrderCreatedEvent event) throws Exception {
        return kafka.send("orders.created", event.orderId().toString(),
                        JsonMapper.builder().build().writeValueAsString(event))
                .get(10, TimeUnit.SECONDS).getRecordMetadata().offset();
    }

    private void awaitCommitted(long offset) throws Exception {
        try (Admin admin = Admin.create(Map.of("bootstrap.servers", broker.getBrokersAsString()))) {
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                var offsets = admin.listConsumerGroupOffsets("notification-order-flow-tests")
                        .partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);
                var committed = offsets.get(new TopicPartition("orders.created", 0));
                assertNotNull(committed);
                assertTrue(committed.offset() > offset);
            });
        }
    }
}
