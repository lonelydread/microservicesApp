package ru.microservicesApp.orderService.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import ru.microservicesApp.orderService.dto.inventory.ProductAvailability;
import ru.microservicesApp.orderService.dto.order.OrderItemRequest;
import ru.microservicesApp.orderService.dto.order.OrderItemResponse;
import ru.microservicesApp.orderService.dto.order.OrderResponse;
import ru.microservicesApp.orderService.kafka.event.OrderCreatedEvent;
import ru.microservicesApp.orderService.kafka.event.OrderItemEvent;

@Component
public class OrderMapper {
    public OrderItemEvent toItemEvent(OrderItemRequest item, ProductAvailability availability) {
        BigDecimal discountMultiplier = BigDecimal.ONE.subtract(availability.sale().movePointLeft(2));
        BigDecimal totalPrice = availability.price().multiply(BigDecimal.valueOf(item.quantity()))
                .multiply(discountMultiplier).setScale(2, RoundingMode.HALF_UP);
        return new OrderItemEvent(item.productId(), item.quantity(), availability.price(), availability.sale(), totalPrice);
    }

    public OrderCreatedEvent toEvent(UUID orderId, UUID userId, List<OrderItemEvent> items, Instant createdAt) {
        return new OrderCreatedEvent(orderId, userId, List.copyOf(items), createdAt);
    }

    public OrderResponse toResponse(OrderCreatedEvent event) {
        List<OrderItemResponse> items = event.items().stream()
                .map(item -> new OrderItemResponse(item.productId(), item.quantity(), item.price(),
                        item.sale(), item.totalPrice()))
                .toList();
        BigDecimal totalPrice = items.stream().map(OrderItemResponse::totalPrice)
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
        return new OrderResponse(event.orderId(), event.userId(), items, totalPrice, event.createdAt());
    }
}
