package ru.microservicesApp.orderService.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import io.grpc.Status;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ru.microservicesApp.orderService.dto.inventory.ProductAvailability;
import ru.microservicesApp.orderService.grpc.InventoryGrpcClient;
import ru.microservicesApp.orderService.kafka.event.OrderCreatedEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-controller;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.kafka.admin.auto-create=false",
        "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "orders.kafka.topic=orders.created"
})
class OrderControllerTests {
    private static final UUID FIRST_PRODUCT = UUID.randomUUID();
    private static final UUID SECOND_PRODUCT = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    @Autowired WebApplicationContext context;
    @Autowired JwtEncoder encoder;
    @MockitoBean InventoryGrpcClient inventory;
    @MockitoBean KafkaTemplate<String, OrderCreatedEvent> kafka;
    private MockMvc mvc;
    private String accessToken;

    @BeforeEach
    void setUp() {
        reset(inventory, kafka);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        Instant now = Instant.now();
        accessToken = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("order-service").subject(userId.toString())
                        .issuedAt(now).expiresAt(now.plusSeconds(900)).claim("token_use", "access")
                        .claim("roles", List.of("ROLE_USER")).build())).getTokenValue();
    }

    @Test
    void aggregatesAndPricesTheOrderAndWaitsForKafkaConfirmation() throws Exception {
        when(inventory.checkAvailability(FIRST_PRODUCT, 2)).thenReturn(
                new ProductAvailability(true, new BigDecimal("25.50"), new BigDecimal("12.50")));
        when(inventory.checkAvailability(SECOND_PRODUCT, 3)).thenReturn(
                new ProductAvailability(true, new BigDecimal("500.00"), BigDecimal.ZERO));
        CompletableFuture<SendResult<String, OrderCreatedEvent>> pending = new CompletableFuture<>();
        when(kafka.send(anyString(), anyString(), any(OrderCreatedEvent.class))).thenReturn(pending);
        String body = """
                {"userId":"%s","items":[{"productId":"%s","quantity":1},
                {"productId":"%s","quantity":3},{"productId":"%s","quantity":1}]}
                """.formatted(UUID.randomUUID(), FIRST_PRODUCT, SECOND_PRODUCT, FIRST_PRODUCT);
        var result = mvc.perform(post("/api/order").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(request().asyncStarted()).andReturn();
        assertFalse(result.getResponse().isCommitted());
        ArgumentCaptor<OrderCreatedEvent> captured = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(kafka).send(eq("orders.created"), anyString(), captured.capture());
        OrderCreatedEvent event = captured.getValue();
        assertEquals(userId, event.userId());
        assertEquals(2, event.items().size());
        assertNotNull(event.orderId());
        assertNotNull(event.createdAt());
        verify(inventory).checkAvailability(FIRST_PRODUCT, 2);
        verify(inventory).checkAvailability(SECOND_PRODUCT, 3);
        verifyNoMoreInteractions(inventory);
        verify(kafka).send("orders.created", event.orderId().toString(), event);
        pending.complete(new SendResult<>(new ProducerRecord<>("orders.created", event.orderId().toString(), event), null));

        mvc.perform(asyncDispatch(result)).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.orderId").value(event.orderId().toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].productId").value(FIRST_PRODUCT.toString()))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].price").value(25.50))
                .andExpect(jsonPath("$.items[0].sale").value(12.50))
                .andExpect(jsonPath("$.items[0].totalPrice").value(44.63))
                .andExpect(jsonPath("$.items[1].productId").value(SECOND_PRODUCT.toString()))
                .andExpect(jsonPath("$.items[1].quantity").value(3))
                .andExpect(jsonPath("$.items[1].price").value(500.00))
                .andExpect(jsonPath("$.items[1].sale").value(0))
                .andExpect(jsonPath("$.items[1].totalPrice").value(1500.00))
                .andExpect(jsonPath("$.totalPrice").value(1544.63));
    }

    @Test
    void insufficientStockPreventsPublication() throws Exception {
        when(inventory.checkAvailability(FIRST_PRODUCT, 2)).thenReturn(
                new ProductAvailability(false, new BigDecimal("25.50"), BigDecimal.ZERO));
        mvc.perform(post("/api/order").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isConflict())
                .andExpect(content().string(""));
        verifyNoInteractions(kafka);
    }

    @Test
    void mapsInventoryFailuresWithoutPublishing() throws Exception {
        Map<Status, Integer> failures = Map.of(Status.NOT_FOUND, 404, Status.INVALID_ARGUMENT, 400,
                Status.UNAVAILABLE, 503, Status.DEADLINE_EXCEEDED, 504, Status.INTERNAL, 502);
        for (var failure : failures.entrySet()) {
            doThrow(failure.getKey().asRuntimeException()).when(inventory).checkAvailability(FIRST_PRODUCT, 2);
            mvc.perform(post("/api/order").header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                    .andExpect(status().is(failure.getValue()))
                    .andExpect(content().string(""));
        }
        verifyNoInteractions(kafka);
    }

    @Test
    void kafkaFailuresReturnUnavailableInsteadOfAccepted() throws Exception {
        when(inventory.checkAvailability(FIRST_PRODUCT, 2)).thenReturn(
                new ProductAvailability(true, new BigDecimal("25.50"), BigDecimal.ZERO));
        when(kafka.send(anyString(), anyString(), any(OrderCreatedEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new org.apache.kafka.common.errors.TimeoutException("test")));
        var result = mvc.perform(post("/api/order").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isServiceUnavailable()).andExpect(content().string(""));
        when(kafka.send(anyString(), anyString(), any(OrderCreatedEvent.class)))
                .thenThrow(new org.apache.kafka.common.errors.TimeoutException("test"));
        mvc.perform(post("/api/order").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isServiceUnavailable()).andExpect(content().string(""));
    }

    @Test
    void unauthorizedAndInvalidRequestsNeverCallInventoryOrKafka() throws Exception {
        mvc.perform(post("/api/order").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isUnauthorized()).andExpect(content().string(""));
        for (String body : List.of("{\"items\":[]}", "{\"items\":[null]}",
                "{\"items\":[{\"quantity\":1}]}", validBody().replace("\"quantity\":2", "\"quantity\":0"),
                validBody().replace(FIRST_PRODUCT.toString(), "not-a-uuid"))) {
            mvc.perform(post("/api/order").header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(content().string(""));
        }
        verifyNoInteractions(inventory, kafka);
    }

    private String validBody() {
        return "{\"items\":[{\"productId\":\"" + FIRST_PRODUCT + "\",\"quantity\":2}]}";
    }

    private String bearer() {
        return "Bearer " + accessToken;
    }
}
