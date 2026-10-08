package ru.microservicesApp.orderService.grpc;

import java.net.InetSocketAddress;
import java.time.Duration;
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
    private Server server;
    private ApplicationContextRunner contextRunner;

    @BeforeEach
    void setUp() throws Exception {
        server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .addService(new InventoryServiceGrpc.InventoryServiceImplBase() {
                    @Override
                    public void checkAvailability(CheckAvailabilityRequest request,
                                                  StreamObserver<CheckAvailabilityResponse> observer) {
                        if (request.getProductId() <= 0 || request.getQuantity() <= 0) {
                            observer.onError(Status.INVALID_ARGUMENT.asRuntimeException());
                        } else if (request.getProductId() == 8) {
                            observer.onError(Status.NOT_FOUND.asRuntimeException());
                        } else if (request.getProductId() == 9) {
                            // Keep the RPC open until the client's deadline expires.
                        } else if (request.getProductId() == 10) {
                            observer.onError(Status.INTERNAL.asRuntimeException());
                        } else {
                            observer.onNext(CheckAvailabilityResponse.newBuilder()
                                    .setAvailable(request.getProductId() == 7 && request.getQuantity() <= 5).build());
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
    }

    @Test
    void configuredClientChecksAvailabilityOverTcp() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());
            InventoryGrpcClient client = client(context.getBean(
                    InventoryServiceGrpc.InventoryServiceBlockingStub.class), Duration.ofSeconds(3));
            assertTrue(client.checkAvailability(7, 5));
            assertFalse(client.checkAvailability(7, 6));
        });
    }

    @Test
    void preservesRpcErrorStatusesInsteadOfTreatingThemAsInsufficientStock() {
        contextRunner.run(context -> {
            InventoryGrpcClient client = client(context.getBean(
                    InventoryServiceGrpc.InventoryServiceBlockingStub.class), Duration.ofSeconds(3));
            assertStatus(Status.Code.NOT_FOUND, () -> client.checkAvailability(8, 1));
            assertStatus(Status.Code.INVALID_ARGUMENT, () -> client.checkAvailability(0, 1));
            assertStatus(Status.Code.INTERNAL, () -> client.checkAvailability(10, 1));
        });
    }

    @Test
    void appliesDeadlineToEachCall() {
        contextRunner.run(context -> {
            InventoryGrpcClient client = client(context.getBean(
                    InventoryServiceGrpc.InventoryServiceBlockingStub.class), Duration.ofMillis(200));
            assertStatus(Status.Code.DEADLINE_EXCEEDED, () -> client.checkAvailability(9, 1));
        });
    }

    @Test
    void reportsUnavailableServer() throws Exception {
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        contextRunner.run(context -> {
            InventoryGrpcClient client = client(context.getBean(
                    InventoryServiceGrpc.InventoryServiceBlockingStub.class), Duration.ofSeconds(3));
            assertStatus(Status.Code.UNAVAILABLE, () -> client.checkAvailability(7, 1));
        });
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new InventoryGrpcClient(null, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new InventoryGrpcClient(null, Duration.ofSeconds(-1)));
    }

    private InventoryGrpcClient client(InventoryServiceGrpc.InventoryServiceBlockingStub stub, Duration timeout) {
        return new InventoryGrpcClient(stub, timeout);
    }

    private void assertStatus(Status.Code expected, org.junit.jupiter.api.function.Executable call) {
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, call);
        assertEquals(expected, exception.getStatus().getCode());
    }
}