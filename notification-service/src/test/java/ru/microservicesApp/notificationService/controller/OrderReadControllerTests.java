package ru.microservicesApp.notificationService.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ru.microservicesApp.notificationService.entity.Order;
import ru.microservicesApp.notificationService.repository.OrderRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-read;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false"
})
class OrderReadControllerTests {
    private static final UUID FIRST_ORDER_ID = UUID.randomUUID();
    private static final UUID SECOND_ORDER_ID = UUID.randomUUID();
    private static final UUID THIRD_ORDER_ID = UUID.randomUUID();
    private static final UUID FIRST_USER_ID = UUID.randomUUID();
    private static final UUID SECOND_USER_ID = UUID.randomUUID();
    private static final UUID FIRST_PRODUCT_ID = UUID.randomUUID();
    private static final UUID SECOND_PRODUCT_ID = UUID.randomUUID();

    @Autowired WebApplicationContext context;
    @Autowired OrderRepository repository;
    private MockMvc mvc;
    private Long firstRowId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        repository.deleteAll();
        var rows = repository.saveAll(List.of(
                new Order(FIRST_ORDER_ID, FIRST_PRODUCT_ID, 2, new BigDecimal("1000.00"),
                        new BigDecimal("10.00"), new BigDecimal("1800.00"), FIRST_USER_ID),
                new Order(FIRST_ORDER_ID, SECOND_PRODUCT_ID, 3, new BigDecimal("500.00"),
                        BigDecimal.ZERO, new BigDecimal("1500.00"), FIRST_USER_ID),
                new Order(SECOND_ORDER_ID, FIRST_PRODUCT_ID, 1, new BigDecimal("1000.00"),
                        BigDecimal.ZERO, new BigDecimal("1000.00"), FIRST_USER_ID),
                new Order(THIRD_ORDER_ID, SECOND_PRODUCT_ID, 1, new BigDecimal("500.00"),
                        BigDecimal.ZERO, new BigDecimal("500.00"), SECOND_USER_ID)));
        firstRowId = rows.getFirst().getId();
    }

    @Test
    void readsAllPurchasesAndFiltersByOrderAndUser() throws Exception {
        mvc.perform(get("/api/orders/all")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].id").value(firstRowId))
                .andExpect(jsonPath("$[0].orderId").value(FIRST_ORDER_ID.toString()))
                .andExpect(jsonPath("$[0].productId").value(FIRST_PRODUCT_ID.toString()))
                .andExpect(jsonPath("$[0].quantity").value(2))
                .andExpect(jsonPath("$[0].price").value(1000.00))
                .andExpect(jsonPath("$[0].sale").value(10.00))
                .andExpect(jsonPath("$[0].totalPrice").value(1800.00))
                .andExpect(jsonPath("$[0].userId").value(FIRST_USER_ID.toString()));

        mvc.perform(get("/api/orders/{id}", FIRST_ORDER_ID)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].orderId").value(FIRST_ORDER_ID.toString()))
                .andExpect(jsonPath("$[1].orderId").value(FIRST_ORDER_ID.toString()))
                .andExpect(jsonPath("$[1].productId").value(SECOND_PRODUCT_ID.toString()));

        mvc.perform(get("/api/orders/user/{id}", FIRST_USER_ID)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].userId").value(FIRST_USER_ID.toString()))
                .andExpect(jsonPath("$[1].userId").value(FIRST_USER_ID.toString()))
                .andExpect(jsonPath("$[2].userId").value(FIRST_USER_ID.toString()))
                .andExpect(jsonPath("$[2].orderId").value(SECOND_ORDER_ID.toString()));
    }

    @Test
    void missingPurchasesReturnEmptyListsAndMalformedIdsReturnBadRequest() throws Exception {
        mvc.perform(get("/api/orders/{id}", UUID.randomUUID())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/orders/user/{id}", UUID.randomUUID())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/orders/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders/user/not-a-uuid")).andExpect(status().isBadRequest());
        repository.deleteAll();
        mvc.perform(get("/api/orders/all")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
