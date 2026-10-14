package ru.microservicesApp.notificationService.entity;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(
        name = "uk_orders_order_product", columnNames = {"order_id", "product_id"}))
@Getter
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(name = "sale", nullable = false, precision = 5, scale = 2)
    private BigDecimal sale;

    @Column(name = "total_price", nullable = false, precision = 29, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    protected Order() {
    }

    public Order(UUID orderId, UUID productId, Integer quantity, BigDecimal price,
                 BigDecimal sale, BigDecimal totalPrice, UUID userId) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.price = price;
        this.sale = sale;
        this.totalPrice = totalPrice;
        this.userId = userId;
    }
}
