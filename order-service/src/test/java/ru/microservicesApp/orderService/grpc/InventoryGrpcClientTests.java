package ru.microservicesApp.orderService.grpc;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.grpc.client.autoconfigure.CompositeChannelFactoryAutoConfiguration;
import org.springframework.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import ru.microservicesApp.inventoryService.grpc.generated.CheckAvailabilityRequest;
import ru.microservicesApp.inventoryService.grpc.generated.CheckAvailabilityResponse;
import ru.microservicesApp.inventoryService.grpc.generated.InventoryServiceGrpc;
import ru.microservicesApp.orderService.config.GrpcConfig;

import static org.junit.jupiter.api.Assertions.*;

class InventoryGrpcClientTests {
    private static final UUID PRODUCT_ID_7 = UUID.fromString("10000000-0000-4000-8000-000000000007");
    private static final UUID PRODUCT_ID_8 = UUID.fromString("10000000-0000-4000-8000-000000000008");
    private static final UUID PRODUCT_ID_9 = UUID.fromString("10000000-0000-4000-8000-000000000009");
    private static final UUID PRODUCT_ID_10 = UUID.fromString("10000000-0000-4000-8000-000000000010");
    private static final UUID PRODUCT_ID_11 = UUID.fromString("10000000-0000-4000-8000-000000000011");
    private static final UUID PRODUCT_ID_12 = UUID.fromString("10000000-0000-4000-8000-000000000012");
    private Server server;
    private ValidatorFactory validatorFactory;
    private ApplicationContextRunner contextRunner;

    @BeforeEach
    void setUp() throws Exception {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .addService(new InventoryServiceGrpc.InventoryServiceImplBase() {
                    @Override
                    public void checkAvailability(CheckAvailabilityRequest request,
                                                  StreamObserver<CheckAvailabilityResponse> observer) {
                        UUID productId;
                        try {
                            productId = UUID.fromString(request.getProductId());
                        } catch (IllegalArgumentException exception) {
                            observer.onError(Status.INVALID_ARGUMENT.asRuntimeException());
                            return;
                        }
                        if (request.getQuantity() <= 0) {
                            observer.onError(Status.INVALID_ARGUMENT.asRuntimeException());
                        } else if (productId.equals(PRODUCT_ID_8)) {
                            observer.onError(Status.NOT_FOUND.asRuntimeException());
                        } else if (productId.equals(PRODUCT_ID_9)) {
                            // Keep the RPC open until the client's deadline expires.
                        } else if (productId.equals(PRODUCT_ID_10)) {
                            observer.onError(Status.INTERNAL.asRuntimeException());
                        } else if (productId.equals(PRODUCT_ID_11)) {
                            observer.onNext(CheckAvailabilityResponse.newBuilder().setAvailable(true)
                                    .setPrice("not-a-number").setSale("0").build());
                            observer.onCompleted();
                        } else if (productId.equals(PRODUCT_ID_12)) {
                            observer.onNext(CheckAvailabilityResponse.newBuilder().setAvailable(true)
                                    .setPrice("25.50").setSale("100.01").build());
                            observer.onCompleted();
                        } else {
                            observer.onNext(CheckAvailabilityResponse.newBuilder()
                                    .setAvailable(productId.equals(PRODUCT_ID_7) && request.getQuantity() <= 5)
                                    .setPrice("25.50").setSale("12.50").build());
                            observer.onCompleted();
                        }
                    }
                }).build().start();
        contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SslAutoConfiguration.class, GrpcClientAutoConfiguration.class, CompositeChannelFactoryAutoConfiguration.class))
                .withUserConfiguration(GrpcConfig.class)
                .withPropertyValues(
                        "spring.grpc.client.channel.inventory.target=127.0.0.1:" + server.getPort(),
                        "spring.grpc.client.channel.inventory.ssl.enabled=false");
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        validatorFactory.close();
    }

    @Test
    void configuredClientPreservesAvailabilityPricingAndRpcStatuses() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());
            InventoryGrpcClient client = client(context.getBean(
                    InventoryServiceGrpc.InventoryServiceBlockingStub.class), Duration.ofSeconds(3));
            var response = client.checkAvailability(PRODUCT_ID_7, 5);
            assertTrue(response.available());
            assertEquals(new BigDecimal("25.50"), response.price());
            assertEquals(new BigDecimal("12.50"), response.sale());
            assertFalse(client.checkAvailability(PRODUCT_ID_7, 6).available());
            assertStatus(Status.Code.NOT_FOUND, () -> client.checkAvailability(PRODUCT_ID_8, 1));
            assertStatus(Status.Code.INVALID_ARGUMENT, () -> client.checkAvailability(PRODUCT_ID_7, 0));
            assertStatus(Status.Code.INTERNAL, () -> client.checkAvailability(PRODUCT_ID_10, 1));
        });
    }

    @Test
    void appliesDeadlineToEachCall() {
        contextRunner.run(context -> {
            InventoryGrpcClient client = client(context.getBean(
                    InventoryServiceGrpc.InventoryServiceBlockingStub.class), Duration.ofMillis(200));
            assertStatus(Status.Code.DEADLINE_EXCEEDED, () -> client.checkAvailability(PRODUCT_ID_9, 1));
        });
    }

    @Test
    void rejectsMalformedAndOutOfRangeInventoryPricing() {
        contextRunner.run(context -> {
            InventoryGrpcClient client = client(context.getBean(
                    InventoryServiceGrpc.InventoryServiceBlockingStub.class), Duration.ofSeconds(3));
            assertStatus(Status.Code.INTERNAL, () -> client.checkAvailability(PRODUCT_ID_11, 1));
            assertStatus(Status.Code.INTERNAL, () -> client.checkAvailability(PRODUCT_ID_12, 1));
        });
    }

    private InventoryGrpcClient client(InventoryServiceGrpc.InventoryServiceBlockingStub stub, Duration timeout) {
        return new InventoryGrpcClient(stub, timeout, validatorFactory.getValidator());
    }

    private void assertStatus(Status.Code expected, org.junit.jupiter.api.function.Executable call) {
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, call);
        assertEquals(expected, exception.getStatus().getCode());
    }
}