package ru.microservicesApp.orderService.mapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import ru.microservicesApp.orderService.dto.order.OrderItemRequest;
import ru.microservicesApp.orderService.dto.order.OrderResponse;
import ru.microservicesApp.orderService.kafka.event.OrderCreatedEvent;
import ru.microservicesApp.orderService.kafka.event.OrderItemEvent;

@Component
public class OrderMapper {
    public OrderCreatedEvent toEvent(UUID orderId, UUID userId, List<OrderItemRequest> items, Instant createdAt) {
        return new OrderCreatedEvent(orderId, userId, items.stream()
                .map(item -> new OrderItemEvent(item.productId(), item.quantity())).toList(), createdAt);
    }

    public OrderResponse toResponse(OrderCreatedEvent event) {
        return new OrderResponse(event.orderId(), event.createdAt());
    }
}
