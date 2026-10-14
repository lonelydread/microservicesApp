package ru.microservicesApp.inventoryService.grpc;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.grpc.server.exception.GrpcExceptionHandlerInterceptor;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.microservicesApp.inventoryService.entity.Product;
import ru.microservicesApp.inventoryService.exception.GlobalExceptionHandler;
import ru.microservicesApp.inventoryService.grpc.generated.CheckAvailabilityRequest;
import ru.microservicesApp.inventoryService.grpc.generated.InventoryServiceGrpc;
import ru.microservicesApp.inventoryService.repository.ProductRepository;
import ru.microservicesApp.inventoryService.mapper.ProductAvailabilityMapper;
import ru.microservicesApp.inventoryService.service.ProductService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryGrpcServiceTests {
    private static final UUID PRODUCT_ID = UUID.fromString("10000000-0000-4000-8000-000000000007");
    private ProductRepository repository;
    private ValidatorFactory validatorFactory;
    private Server server;
    private ManagedChannel channel;
    private InventoryServiceGrpc.InventoryServiceBlockingStub stub;

    @BeforeEach
    void startServer() throws Exception {
        repository = mock(ProductRepository.class);
        validatorFactory = Validation.buildDefaultValidatorFactory();
        ProxyFactory proxyFactory = new ProxyFactory(new ProductService(repository, new ProductAvailabilityMapper()));
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new MethodValidationInterceptor(validatorFactory.getValidator()));
        ProductService productService = (ProductService) proxyFactory.getProxy();
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .intercept(new GrpcExceptionHandlerInterceptor(new GlobalExceptionHandler()))
                .addService(new InventoryGrpcService(productService, new ProductAvailabilityMapper())).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        stub = InventoryServiceGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void stopServer() throws Exception {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void returnsPriceAndSaleFromTheSameProductRead() {
        Product product = productWithStock(5);
        product.setPrice(new BigDecimal("25.50"));
        product.setSale(new BigDecimal("12.50"));
        when(repository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        var response = stub.checkAvailability(request(PRODUCT_ID, 5));
        assertTrue(response.getAvailable());
        assertEquals("25.50", response.getPrice());
        assertEquals("12.50", response.getSale());
        verify(repository, times(1)).findById(PRODUCT_ID);
        assertEquals(5, product.getQuantity());
        verify(repository, never()).save(any(Product.class));
    }

    @Test
    void returnsUnavailableWhenStockIsInsufficient() {
        when(repository.findById(PRODUCT_ID)).thenReturn(Optional.of(productWithStock(5)));
        assertFalse(stub.checkAvailability(request(PRODUCT_ID, 6)).getAvailable());
    }

    @Test
    void returnsNotFoundWhenProductDoesNotExist() {
        when(repository.findById(PRODUCT_ID)).thenReturn(Optional.empty());
        var exception = assertThrows(StatusRuntimeException.class,
                () -> stub.checkAvailability(request(PRODUCT_ID, 1)));
        assertEquals(Status.Code.NOT_FOUND, exception.getStatus().getCode());
    }

    @Test
    void rejectsInvalidIdsAndQuantitiesBeforeAccessingRepository() {
        for (String id : List.of("", "7", "not-a-uuid", "1-1-1-1-1", PRODUCT_ID + "x")) {
            assertInvalidArgument(CheckAvailabilityRequest.newBuilder().setProductId(id).setQuantity(1).build());
        }
        for (int quantity : new int[] {0, -1}) {
            assertInvalidArgument(request(PRODUCT_ID, quantity));
        }
        assertInvalidArgument(CheckAvailabilityRequest.getDefaultInstance());
        verifyNoInteractions(repository);
    }

    private void assertInvalidArgument(CheckAvailabilityRequest request) {
        var exception = assertThrows(StatusRuntimeException.class, () -> stub.checkAvailability(request));
        assertEquals(Status.Code.INVALID_ARGUMENT, exception.getStatus().getCode());
    }

    private CheckAvailabilityRequest request(UUID productId, int quantity) {
        return CheckAvailabilityRequest.newBuilder().setProductId(productId.toString()).setQuantity(quantity).build();
    }

    private Product productWithStock(Integer quantity) {
        Product product = new Product();
        product.setQuantity(quantity);
        product.setPrice(new BigDecimal("25.50"));
        return product;
    }
}
