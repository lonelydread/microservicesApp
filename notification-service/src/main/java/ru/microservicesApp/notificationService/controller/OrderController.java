package ru.microservicesApp.notificationService.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.microservicesApp.notificationService.dto.order.OrderResponse;
import ru.microservicesApp.notificationService.service.OrderService;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/all")
    public List<OrderResponse> getAllOrders() {
        return orderService.getAllOrders();
    }

    @GetMapping("/{order_id}")
    public List<OrderResponse> getOrdersByOrderId(@PathVariable("order_id") UUID orderId) {
        return orderService.getOrdersByOrderId(orderId);
    }

    @GetMapping("/user/{user_id}")
    public List<OrderResponse> getOrdersByUserId(@PathVariable("user_id") UUID userId) {
        return orderService.getOrdersByUserId(userId);
    }
}
