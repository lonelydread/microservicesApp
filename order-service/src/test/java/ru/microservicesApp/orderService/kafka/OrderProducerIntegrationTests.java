package ru.microservicesApp.orderService.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tools.jackson.databind.json.JsonMapper;
import ru.microservicesApp.orderService.config.KafkaConfig;
import ru.microservicesApp.orderService.kafka.event.OrderCreatedEvent;
import ru.microservicesApp.orderService.kafka.event.OrderItemEvent;
import ru.microservicesApp.orderService.kafka.producer.OrderProducer;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(OrderProducerIntegrationTests.TestConfig.class)
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
@EmbeddedKafka(partitions = 1, topics = "orders.created", bootstrapServersProperty = "test.kafka.bootstrap")
@TestPropertySource(properties = {"spring.kafka.bootstrap-servers=${test.kafka.bootstrap}"})
@DirtiesContext
class OrderProducerIntegrationTests {
    @Configuration(proxyBeanMethods = false)
    @Import({SslAutoConfiguration.class, KafkaAutoConfiguration.class, KafkaConfig.class, OrderProducer.class})
    static class TestConfig {
    }

    @Autowired EmbeddedKafkaBroker broker;
    @Autowired OrderProducer producer;

    @Test
    void publishesJsonEventWithUuidKeyUsingApplicationConfiguration() throws Exception {
        var properties = KafkaTestUtils.consumerProps(broker, "order-publishing-tests", false);
        var factory = new DefaultKafkaConsumerFactory<String, String>(properties,
                new StringDeserializer(), new StringDeserializer());
        try (Consumer<String, String> consumer = factory.createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "orders.created");
            OrderCreatedEvent event = new OrderCreatedEvent(UUID.randomUUID(), UUID.randomUUID(),
                    List.of(new OrderItemEvent(UUID.randomUUID(), 2, new BigDecimal("99999999999999999.99"),
                            new BigDecimal("12.50"), new BigDecimal("174999999999999999.98"))), Instant.now());
            producer.send(event).get(10, TimeUnit.SECONDS);
            var record = KafkaTestUtils.getSingleRecord(consumer, "orders.created");
            var json = JsonMapper.builder().build().readTree(record.value());
            assertEquals(event.orderId().toString(), record.key());
            assertEquals(event.orderId().toString(), json.get("orderId").asText());
            assertEquals(event.userId().toString(), json.get("userId").asText());
            assertEquals(event.items().getFirst().productId().toString(), json.get("items").get(0).get("productId").asText());
            assertEquals(2, json.get("items").get(0).get("quantity").asInt());
            assertTrue(json.hasNonNull("createdAt"));
            var decoded = JsonMapper.builder().build().readValue(record.value(), OrderCreatedEvent.class);
            assertEquals(event.items(), decoded.items());
            assertNull(record.headers().lastHeader("__TypeId__"));
        }
    }
}
