package ru.microservicesApp.orderService.controller;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.microservicesApp.orderService.dto.order.CreateOrderRequest;
import ru.microservicesApp.orderService.dto.order.OrderResponse;
import ru.microservicesApp.orderService.service.OrderService;

@RestController
@RequestMapping("/api/order")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<OrderResponse>> createOrder(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(UUID.fromString(jwt.getSubject()), request)
                .thenApply(response -> ResponseEntity.accepted().body(response));
    }
}
