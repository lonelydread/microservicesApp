package ru.microservicesApp.notificationService.mapper;

import java.util.List;

import org.springframework.stereotype.Component;
import ru.microservicesApp.notificationService.dto.order.OrderResponse;
import ru.microservicesApp.notificationService.entity.Order;
import ru.microservicesApp.notificationService.kafka.event.OrderCreatedEvent;

@Component
public class OrderMapper {
    public OrderResponse toResponse(Order order) {
        return new OrderResponse(order.getId(),
                                 order.getOrderId(),
                                 order.getProductId(),
                                 order.getQuantity(),
                                 order.getPrice(),
                                 order.getSale(),
                                 order.getTotalPrice(),
                                 order.getUserId());
    }

    public List<Order> toEntities(OrderCreatedEvent event) {
        return event.items().stream()
                .map(item -> new Order(event.orderId(), item.productId(), item.quantity(), item.price(),
                        item.sale(), item.totalPrice(), event.userId()))
                .toList();
    }
}
