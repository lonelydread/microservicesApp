package ru.microservicesApp.orderService.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.microservicesApp.orderService.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
