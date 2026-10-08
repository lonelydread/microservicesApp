package ru.microservicesApp.orderService.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.microservicesApp.orderService.dto.order.CreateOrderRequest;
import ru.microservicesApp.orderService.dto.order.OrderItemRequest;
import ru.microservicesApp.orderService.dto.order.OrderResponse;
import ru.microservicesApp.orderService.grpc.InventoryGrpcClient;
import ru.microservicesApp.orderService.kafka.event.OrderCreatedEvent;
import ru.microservicesApp.orderService.kafka.producer.OrderProducer;
import ru.microservicesApp.orderService.mapper.OrderMapper;

@Service
public class OrderService {
    private final InventoryGrpcClient inventoryGrpcClient;
    private final OrderProducer orderProducer;
    private final OrderMapper orderMapper;

    public OrderService(InventoryGrpcClient inventoryGrpcClient, OrderProducer orderProducer, OrderMapper orderMapper) {
        this.inventoryGrpcClient = inventoryGrpcClient;
        this.orderProducer = orderProducer;
        this.orderMapper = orderMapper;
    }

    public CompletableFuture<OrderResponse> createOrder(UUID userId, CreateOrderRequest request) {
        List<OrderItemRequest> items = aggregateItems(request.items());
        for (OrderItemRequest item : items) {
            checkAvailability(item);
        }
        OrderCreatedEvent event = orderMapper.toEvent(UUID.randomUUID(), userId, items, Instant.now());
        return orderProducer.send(event).thenApply(ignored -> orderMapper.toResponse(event));
    }

    private List<OrderItemRequest> aggregateItems(List<OrderItemRequest> items) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        try {
            for (OrderItemRequest item : items) {
                quantities.merge(item.productId(), item.quantity(), Math::addExact);
            }
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Total product quantity is too large", exception);
        }
        return quantities.entrySet().stream()
                .map(entry -> new OrderItemRequest(entry.getKey(), entry.getValue())).toList();
    }

    private void checkAvailability(OrderItemRequest item) {
        boolean available;
        try {
            available = inventoryGrpcClient.checkAvailability(item.productId(), item.quantity());
        } catch (StatusRuntimeException exception) {
            HttpStatus status = switch (exception.getStatus().getCode()) {
                case NOT_FOUND -> HttpStatus.NOT_FOUND;
                case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
                case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
                case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
                default -> HttpStatus.BAD_GATEWAY;
            };
            throw new ResponseStatusException(status, "Unable to check product availability", exception);
        }
        if (!available) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock for product " + item.productId());
        }
    }
}
