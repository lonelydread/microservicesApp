package ru.microservicesApp.orderService.kafka.event;

public record OrderItemEvent(long productId, int quantity) {
}
