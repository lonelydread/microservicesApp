package ru.microservicesApp.orderService.grpc;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.microservicesApp.inventoryService.grpc.generated.CheckAvailabilityRequest;
import ru.microservicesApp.inventoryService.grpc.generated.InventoryServiceGrpc;

@Component
public class InventoryGrpcClient {
    private final InventoryServiceGrpc.InventoryServiceBlockingStub stub;
    private final Duration timeout;

    public InventoryGrpcClient(InventoryServiceGrpc.InventoryServiceBlockingStub stub,
                               @Value("${inventory.grpc.timeout:3s}") Duration timeout) {
        this.stub = stub;
        this.timeout = timeout;
    }

    public boolean checkAvailability(long productId, int quantity) {
        CheckAvailabilityRequest request = CheckAvailabilityRequest.newBuilder()
                .setProductId(productId).setQuantity(quantity).build();
        return stub.withDeadlineAfter(timeout.toNanos(), TimeUnit.NANOSECONDS)
                .checkAvailability(request).getAvailable();
    }
}