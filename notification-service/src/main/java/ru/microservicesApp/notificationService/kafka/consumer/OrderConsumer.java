package ru.microservicesApp.notificationService.kafka.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.microservicesApp.notificationService.kafka.event.OrderCreatedEvent;
import ru.microservicesApp.notificationService.service.OrderService;

@Component
public class OrderConsumer {
    private final OrderService orderService;

    public OrderConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(topics = "${orders.kafka.topic:orders.created}")
    public void consume(OrderCreatedEvent event) {
        orderService.saveOrder(event);
    }
}
