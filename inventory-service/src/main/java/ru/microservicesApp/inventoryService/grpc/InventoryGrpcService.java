package ru.microservicesApp.inventoryService.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.validation.ConstraintViolationException;
import org.springframework.grpc.server.service.GrpcService;
import ru.microservicesApp.inventoryService.grpc.generated.CheckAvailabilityRequest;
import ru.microservicesApp.inventoryService.grpc.generated.CheckAvailabilityResponse;
import ru.microservicesApp.inventoryService.grpc.generated.InventoryServiceGrpc;
import ru.microservicesApp.inventoryService.service.ProductService;

@GrpcService
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {
    private final ProductService productService;

    public InventoryGrpcService(ProductService productService) {
        this.productService = productService;
    }

    @Override
    public void checkAvailability(CheckAvailabilityRequest request,
                                  StreamObserver<CheckAvailabilityResponse> responseObserver) {
        final boolean available;
        try {
            available = productService.checkAvailability(request.getProductId(), request.getQuantity());
        } catch (ConstraintViolationException exception) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(exception.getMessage())
                            .asRuntimeException());
            return;
        }
        responseObserver.onNext(CheckAvailabilityResponse.newBuilder().setAvailable(available).build());
        responseObserver.onCompleted();
    }
}
