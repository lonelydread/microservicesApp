package ru.microservicesApp.orderService.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import io.grpc.Status;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import ru.microservicesApp.orderService.config.SecurityConfig;
import ru.microservicesApp.orderService.exception.GlobalExceptionHandler;
import ru.microservicesApp.orderService.grpc.InventoryGrpcClient;
import ru.microservicesApp.orderService.kafka.event.OrderCreatedEvent;
import ru.microservicesApp.orderService.kafka.event.OrderItemEvent;
import ru.microservicesApp.orderService.kafka.producer.OrderProducer;
import ru.microservicesApp.orderService.mapper.OrderMapper;
import ru.microservicesApp.orderService.service.OrderService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = OrderControllerTests.TestConfig.class)
@TestPropertySource(properties = {
        "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "orders.kafka.topic=orders.created"
})
class OrderControllerTests {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, OrderController.class, OrderService.class, OrderMapper.class,
            OrderProducer.class, GlobalExceptionHandler.class})
    static class TestConfig {
        @Bean UserDetailsService userDetailsService() { return mock(UserDetailsService.class); }
        @Bean InventoryGrpcClient inventoryGrpcClient() { return mock(InventoryGrpcClient.class); }
        @Bean @SuppressWarnings("unchecked")
        KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate() { return mock(KafkaTemplate.class); }
        @Bean static ConversionService conversionService() { return new ApplicationConversionService(); }
    }

    @Autowired WebApplicationContext context;
    @Autowired InventoryGrpcClient inventory;
    @Autowired KafkaTemplate<String, OrderCreatedEvent> kafka;
    @Autowired JwtEncoder encoder;
    private MockMvc mvc;
    private String accessToken;
    private final UUID userId = new UUID(0, 7);

    @BeforeEach
    void setUp() {
        reset(inventory, kafka);
        when(inventory.checkAvailability(anyLong(), anyInt())).thenReturn(true);
        when(kafka.send(anyString(), anyString(), any(OrderCreatedEvent.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(new SendResult<>(
                        new ProducerRecord<String, OrderCreatedEvent>(invocation.getArgument(0),
                                invocation.getArgument(1), invocation.getArgument(2)), null)));
        accessToken = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("order-service").subject(userId.toString())
                        .claim("token_use", "access").claim("roles", List.of("ROLE_USER"))
                        .expiresAt(Instant.now().plusSeconds(900)).build())).getTokenValue();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void publishesOrderForAuthenticatedUserAndWaitsForKafkaAcknowledgement() throws Exception {
        CompletableFuture<SendResult<String, OrderCreatedEvent>> pending = new CompletableFuture<>();
        when(kafka.send(anyString(), anyString(), any(OrderCreatedEvent.class))).thenReturn(pending);
        var result = mvc.perform(post("/api/order").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"00000000-0000-0000-0000-000000000099\","
                                + "\"items\":[{\"productId\":7,\"quantity\":2},{\"productId\":8,\"quantity\":1}]}"))
                .andExpect(request().asyncStarted()).andReturn();
        assertFalse(result.getResponse().isCommitted());
        ArgumentCaptor<OrderCreatedEvent> captured = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(kafka).send(eq("orders.created"), anyString(), captured.capture());
        OrderCreatedEvent event = captured.getValue();
        assertEquals(userId, event.userId());
        assertEquals(List.of(new OrderItemEvent(7, 2), new OrderItemEvent(8, 1)), event.items());
        assertNotNull(event.orderId());
        assertNotNull(event.createdAt());
        verify(kafka).send("orders.created", event.orderId().toString(), event);
        verify(inventory).checkAvailability(7, 2);
        verify(inventory).checkAvailability(8, 1);
        pending.complete(new SendResult<>(new ProducerRecord<>("orders.created", event.orderId().toString(), event), null));
        mvc.perform(asyncDispatch(result)).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.orderId").value(event.orderId().toString()))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void combinesRepeatedProductsBeforeCheckingStockAndPublishing() throws Exception {
        var result = mvc.perform(post("/api/order").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":7,\"quantity\":2},{\"productId\":7,\"quantity\":3}]}"))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isAccepted());
        verify(inventory).checkAvailability(7, 5);
        verify(inventory, times(1)).checkAvailability(anyLong(), anyInt());
        ArgumentCaptor<OrderCreatedEvent> captured = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(kafka).send(eq("orders.created"), anyString(), captured.capture());
        assertEquals(List.of(new OrderItemEvent(7, 5)), captured.getValue().items());
    }

    @Test
    void doesNotPublishWhenCombinedQuantityExceedsAvailableStock() throws Exception {
        when(inventory.checkAvailability(7, 5)).thenReturn(false);
        mvc.perform(post("/api/order").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":7,\"quantity\":2},{\"productId\":7,\"quantity\":3}]}"))
                .andExpect(status().isConflict());
        verifyNoInteractions(kafka);
    }

    @Test
    void doesNotPublishWhenAnyProductIsUnavailable() throws Exception {
        when(inventory.checkAvailability(8, 1)).thenReturn(false);
        mvc.perform(post("/api/order").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":7,\"quantity\":2},{\"productId\":8,\"quantity\":1}]}"))
                .andExpect(status().isConflict());
        verifyNoInteractions(kafka);
    }

    @Test
    void mapsInventoryFailuresWithoutPublishingAnOrder() throws Exception {
        List<Status> failures = List.of(Status.NOT_FOUND, Status.UNAVAILABLE, Status.DEADLINE_EXCEEDED, Status.INTERNAL);
        List<Integer> expected = List.of(404, 503, 504, 502);
        for (int i = 0; i < failures.size(); i++) {
            doThrow(failures.get(i).asRuntimeException()).when(inventory).checkAvailability(7, 2);
            mvc.perform(post("/api/order").header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                    .andExpect(status().is(expected.get(i)));
        }
        verifyNoInteractions(kafka);
    }

    @Test
    void returnsServiceUnavailableWhenKafkaRejectsPublication() throws Exception {
        when(kafka.send(anyString(), anyString(), any(OrderCreatedEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new org.apache.kafka.common.errors.TimeoutException("test")));
        var result = mvc.perform(post("/api/order").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isServiceUnavailable());
    }

    @Test
    void handlesSynchronousKafkaFailureWithoutReturningAccepted() throws Exception {
        when(kafka.send(anyString(), anyString(), any(OrderCreatedEvent.class)))
                .thenThrow(new org.apache.kafka.common.errors.TimeoutException("test"));
        mvc.perform(post("/api/order").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void rejectsInvalidRequestsBeforeCallingInventoryOrKafka() throws Exception {
        for (String body : List.of("{}", "{\"items\":[]}", "{\"items\":[null]}",
                "{\"items\":[{}]}", "{\"items\":[{\"productId\":0,\"quantity\":1}]}",
                "{\"items\":[{\"productId\":7,\"quantity\":0}]}",
                "{\"items\":[{\"productId\":7,\"quantity\":-1}]}")) {
            mvc.perform(post("/api/order").header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(inventory, kafka);
    }

    @Test
    void rejectsQuantityOverflowBeforeCallingInventoryOrKafka() throws Exception {
        mvc.perform(post("/api/order").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":7,\"quantity\":2147483647},{\"productId\":7,\"quantity\":1}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(inventory, kafka);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/order").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(inventory, kafka);
    }

    private String validBody() {
        return "{\"items\":[{\"productId\":7,\"quantity\":2}]}";
    }
}
