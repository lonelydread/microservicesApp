package ru.microservicesApp.notificationService.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.microservicesApp.notificationService.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
    boolean existsByOrderId(UUID orderId);
    List<Order> findByOrderIdOrderByIdAsc(UUID orderId);
    List<Order> findByUserIdOrderByIdAsc(UUID userId);
}
