package ru.microservicesApp.inventoryService.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.web.server.ResponseStatusException;
import ru.microservicesApp.inventoryService.grpc.generated.CheckAvailabilityRequest;
import ru.microservicesApp.inventoryService.grpc.generated.CheckAvailabilityResponse;
import ru.microservicesApp.inventoryService.grpc.generated.InventoryServiceGrpc;
import ru.microservicesApp.inventoryService.service.ProductService;

@GrpcService
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(InventoryGrpcService.class);
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
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Product id and quantity must be positive")
                    .asRuntimeException());
            return;
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 404) {
                responseObserver.onError(Status.NOT_FOUND.withDescription("Product not found").asRuntimeException());
            } else {
                logger.error("Unable to check product availability", exception);
                responseObserver.onError(Status.INTERNAL.withDescription("Unable to check product availability")
                        .asRuntimeException());
            }
            return;
        } catch (RuntimeException exception) {
            logger.error("Unable to check product availability", exception);
            responseObserver.onError(Status.INTERNAL.withDescription("Unable to check product availability")
                    .asRuntimeException());
            return;
        }
        responseObserver.onNext(CheckAvailabilityResponse.newBuilder().setAvailable(available).build());
        responseObserver.onCompleted();
    }
}