package ru.microservicesApp.inventoryService.grpc;

import java.util.Optional;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.springframework.aop.framework.ProxyFactory;
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
import ru.microservicesApp.inventoryService.grpc.generated.CheckAvailabilityRequest;
import ru.microservicesApp.inventoryService.grpc.generated.InventoryServiceGrpc;
import ru.microservicesApp.inventoryService.repository.ProductRepository;
import ru.microservicesApp.inventoryService.service.ProductService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryGrpcServiceTests {
    private ProductRepository repository;
    private ValidatorFactory validatorFactory;
    private Server server;
    private ManagedChannel channel;
    private InventoryServiceGrpc.InventoryServiceBlockingStub stub;

    @BeforeEach
    void startServer() throws Exception {
        repository = mock(ProductRepository.class);
        validatorFactory = Validation.buildDefaultValidatorFactory();
        ProxyFactory proxyFactory = new ProxyFactory(new ProductService(repository));
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new MethodValidationInterceptor(validatorFactory.getValidator()));
        ProductService productService = (ProductService) proxyFactory.getProxy();
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new InventoryGrpcService(productService)).build().start();
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
    void returnsAvailableWhenRequestedQuantityEqualsStock() {
        Product product = productWithStock(5);
        when(repository.findById(7L)).thenReturn(Optional.of(product));
        assertTrue(stub.checkAvailability(request(7L, 5)).getAvailable());
        assertEquals(5, product.getQuantity());
        verify(repository, never()).save(any(Product.class));
        verify(repository, never()).delete(any(Product.class));
    }

    @Test
    void returnsAvailableWhenStockExceedsRequestedQuantity() {
        when(repository.findById(7L)).thenReturn(Optional.of(productWithStock(5)));
        assertTrue(stub.checkAvailability(request(7L, 1)).getAvailable());
    }

    @Test
    void returnsUnavailableWhenStockIsInsufficient() {
        when(repository.findById(7L)).thenReturn(Optional.of(productWithStock(5)));
        assertFalse(stub.checkAvailability(request(7L, 6)).getAvailable());
    }

    @Test
    void returnsUnavailableWhenStockIsZeroOrUnknown() {
        when(repository.findById(7L)).thenReturn(Optional.of(productWithStock(0)));
        assertFalse(stub.checkAvailability(request(7L, 1)).getAvailable());
        when(repository.findById(7L)).thenReturn(Optional.of(productWithStock(null)));
        assertFalse(stub.checkAvailability(request(7L, 1)).getAvailable());
    }

    @Test
    void returnsNotFoundWhenProductDoesNotExist() {
        when(repository.findById(7L)).thenReturn(Optional.empty());
        var exception = assertThrows(StatusRuntimeException.class,
                () -> stub.checkAvailability(request(7L, 1)));
        assertEquals(Status.Code.NOT_FOUND, exception.getStatus().getCode());
    }

    @Test
    void rejectsInvalidIdsAndQuantitiesBeforeAccessingRepository() {
        for (long id : new long[] {0, -1}) {
            assertInvalidArgument(request(id, 1));
        }
        for (int quantity : new int[] {0, -1}) {
            assertInvalidArgument(request(7L, quantity));
        }
        assertInvalidArgument(CheckAvailabilityRequest.getDefaultInstance());
        verifyNoInteractions(repository);
    }

    @Test
    void acceptsMaximumSupportedQuantity() {
        when(repository.findById(Long.MAX_VALUE))
                .thenReturn(Optional.of(productWithStock(Integer.MAX_VALUE)));
        assertTrue(stub.checkAvailability(request(Long.MAX_VALUE, Integer.MAX_VALUE)).getAvailable());
    }

    @Test
    void returnsInternalWithoutExposingDatabaseError() {
        when(repository.findById(7L)).thenThrow(new IllegalStateException("Private database details"));
        var exception = assertThrows(StatusRuntimeException.class,
                () -> stub.checkAvailability(request(7L, 1)));
        assertEquals(Status.Code.INTERNAL, exception.getStatus().getCode());
        assertEquals("Unable to check product availability", exception.getStatus().getDescription());
    }

    private void assertInvalidArgument(CheckAvailabilityRequest request) {
        var exception = assertThrows(StatusRuntimeException.class, () -> stub.checkAvailability(request));
        assertEquals(Status.Code.INVALID_ARGUMENT, exception.getStatus().getCode());
    }

    private CheckAvailabilityRequest request(long productId, int quantity) {
        return CheckAvailabilityRequest.newBuilder().setProductId(productId).setQuantity(quantity).build();
    }

    private Product productWithStock(Integer quantity) {
        Product product = new Product();
        product.setQuantity(quantity);
        return product;
    }
}
