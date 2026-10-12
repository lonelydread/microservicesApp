package ru.microservicesApp.notificationService.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.microservicesApp.notificationService.dto.order.OrderResponse;
import ru.microservicesApp.notificationService.kafka.event.OrderCreatedEvent;
import ru.microservicesApp.notificationService.mapper.OrderMapper;
import ru.microservicesApp.notificationService.repository.OrderRepository;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    public OrderService(OrderRepository orderRepository, OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll(Sort.by("id")).stream().map(orderMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByOrderId(UUID orderId) {
        return orderRepository.findByOrderIdOrderByIdAsc(orderId).stream().map(orderMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByUserId(UUID userId) {
        return orderRepository.findByUserIdOrderByIdAsc(userId).stream().map(orderMapper::toResponse).toList();
    }

    @Transactional
    public void saveOrder(OrderCreatedEvent event) {
        if (orderRepository.existsByOrderId(event.orderId())) {
            return;
        }
        orderRepository.saveAll(orderMapper.toEntities(event));
    }
}
