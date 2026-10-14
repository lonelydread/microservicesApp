package ru.microservicesApp.orderService.kafka.producer;

import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.microservicesApp.orderService.kafka.event.OrderCreatedEvent;

@Component
public class OrderProducer {
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    private final String topic;

    public OrderProducer(KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate,
                         @Value("${orders.kafka.topic:orders.created}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public CompletableFuture<Void> send(OrderCreatedEvent event) {
        try {
            return kafkaTemplate.send(topic, event.orderId().toString(), event).handle((result, exception) -> {
                if (exception != null) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Unable to publish order", exception);
                }
                return null;
            });
        } catch (org.apache.kafka.common.KafkaException | org.springframework.kafka.KafkaException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to publish order", exception);
        }
    }
}
