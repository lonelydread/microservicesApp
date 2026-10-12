package ru.microservicesApp.orderService.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import ru.microservicesApp.orderService.dto.inventory.ProductAvailability;
import ru.microservicesApp.orderService.dto.order.OrderItemRequest;
import ru.microservicesApp.orderService.kafka.event.OrderCreatedEvent;

import static org.junit.jupiter.api.Assertions.*;

class OrderMapperTests {
    private static final UUID PRODUCT_ID_7 = UUID.fromString("10000000-0000-4000-8000-000000000007");
    private static final UUID PRODUCT_ID_101 = UUID.fromString("10000000-0000-4000-8000-000000000101");
    private static final UUID PRODUCT_ID_102 = UUID.fromString("10000000-0000-4000-8000-000000000102");
    private final OrderMapper mapper = new OrderMapper();

    @Test
    void calculatesDiscountedLineTotalAndRoundsOnlyTheFinalAmount() {
        var item = mapper.toItemEvent(new OrderItemRequest(PRODUCT_ID_7, 2),
                new ProductAvailability(true, new BigDecimal("25.50"), new BigDecimal("12.50")));
        assertEquals(new BigDecimal("44.63"), item.totalPrice());
        assertEquals(new BigDecimal("25.50"), item.price());
        assertEquals(new BigDecimal("12.50"), item.sale());
    }

    @Test
    void supportsZeroAndFullDiscount() {
        assertEquals(new BigDecimal("51.00"), mapper.toItemEvent(new OrderItemRequest(PRODUCT_ID_7, 2),
                new ProductAvailability(true, new BigDecimal("25.50"), BigDecimal.ZERO)).totalPrice());
        assertEquals(new BigDecimal("0.00"), mapper.toItemEvent(new OrderItemRequest(PRODUCT_ID_7, 2),
                new ProductAvailability(true, new BigDecimal("25.50"), new BigDecimal("100"))).totalPrice());
    }

    @Test
    void wholeOrderTotalEqualsTheSumOfRoundedItemTotals() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-09T00:00:00Z");
        ProductAvailability availability = new ProductAvailability(true, new BigDecimal("0.05"),
                new BigDecimal("10"));
        var first = mapper.toItemEvent(new OrderItemRequest(PRODUCT_ID_101, 1), availability);
        var second = mapper.toItemEvent(new OrderItemRequest(PRODUCT_ID_102, 1), availability);
        var response = mapper.toResponse(new OrderCreatedEvent(orderId, userId, List.of(first, second), createdAt));
        assertEquals(orderId, response.orderId());
        assertEquals(userId, response.userId());
        assertEquals(createdAt, response.createdAt());
        assertEquals(2, response.items().size());
        assertEquals(PRODUCT_ID_101, response.items().getFirst().productId());
        assertEquals(PRODUCT_ID_102, response.items().getLast().productId());
        assertEquals(new BigDecimal("0.05"), response.items().getFirst().totalPrice());
        assertEquals(new BigDecimal("0.05"), response.items().getLast().totalPrice());
        assertEquals(new BigDecimal("0.10"), response.totalPrice());
    }

    @Test
    void preservesPrecisionForMaximumPriceAndQuantity() {
        BigDecimal price = new BigDecimal("99999999999999999.99");
        var item = mapper.toItemEvent(new OrderItemRequest(PRODUCT_ID_7, Integer.MAX_VALUE),
                new ProductAvailability(true, price, BigDecimal.ZERO));
        assertEquals(price.multiply(BigDecimal.valueOf(Integer.MAX_VALUE)), item.totalPrice());
        assertEquals(29, item.totalPrice().precision());
    }
}
