package ru.microservicesApp.inventoryService.grpc;

import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;
import ru.microservicesApp.inventoryService.dto.productavailability.ProductAvailabilityRequest;
import ru.microservicesApp.inventoryService.dto.productavailability.ProductAvailabilityResponse;
import ru.microservicesApp.inventoryService.grpc.generated.CheckAvailabilityRequest;
import ru.microservicesApp.inventoryService.grpc.generated.CheckAvailabilityResponse;
import ru.microservicesApp.inventoryService.grpc.generated.InventoryServiceGrpc;
import ru.microservicesApp.inventoryService.mapper.ProductAvailabilityMapper;
import ru.microservicesApp.inventoryService.service.ProductService;

@GrpcService
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {
    private final ProductService productService;
    private final ProductAvailabilityMapper productAvailabilityMapper;


    public InventoryGrpcService(ProductService productService,
                                ProductAvailabilityMapper productAvailabilityMapper) {
        this.productService = productService;
        this.productAvailabilityMapper = productAvailabilityMapper;
    }

    @Override
    public void checkAvailability(CheckAvailabilityRequest request,
                                  StreamObserver<CheckAvailabilityResponse> responseObserver) {
        ProductAvailabilityRequest availabilityRequest = productAvailabilityMapper.toRequest(request);
        ProductAvailabilityResponse availability = productService.checkAvailability(availabilityRequest);
        CheckAvailabilityResponse response = productAvailabilityMapper.toGrpcResponse(availability);
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
