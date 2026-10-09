package ru.microservicesApp.orderService.grpc;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.grpc.Status;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.microservicesApp.inventoryService.grpc.generated.CheckAvailabilityRequest;
import ru.microservicesApp.inventoryService.grpc.generated.InventoryServiceGrpc;
import ru.microservicesApp.orderService.dto.inventory.ProductAvailability;

@Component
public class InventoryGrpcClient {
    private final InventoryServiceGrpc.InventoryServiceBlockingStub stub;
    private final Duration timeout;
    private final Validator validator;

    public InventoryGrpcClient(InventoryServiceGrpc.InventoryServiceBlockingStub stub,
                               @Value("${inventory.grpc.timeout:3s}") Duration timeout,
                               Validator validator) {
        this.stub = stub;
        this.timeout = timeout;
        this.validator = validator;
    }

    public ProductAvailability checkAvailability(long productId, int quantity) {
        CheckAvailabilityRequest request = CheckAvailabilityRequest.newBuilder()
                .setProductId(productId).setQuantity(quantity).build();
        var response = stub.withDeadlineAfter(timeout.toNanos(), TimeUnit.NANOSECONDS)
                .checkAvailability(request);
        ProductAvailability availability;
        try {
            availability = new ProductAvailability(response.getAvailable(),
                    new BigDecimal(response.getPrice()), new BigDecimal(response.getSale()));
        } catch (NumberFormatException exception) {
            throw Status.INTERNAL.withDescription("Invalid inventory pricing response")
                    .withCause(exception).asRuntimeException();
        }
        if (!validator.validate(availability).isEmpty()) {
            throw Status.INTERNAL.withDescription("Invalid inventory pricing response").asRuntimeException();
        }
        return availability;
    }
}
