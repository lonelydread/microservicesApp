package ru.microservicesApp.orderService.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;
import ru.microservicesApp.inventoryService.grpc.generated.InventoryServiceGrpc;

@Configuration
public class GrpcConfig {
    @Bean
    public InventoryServiceGrpc.InventoryServiceBlockingStub inventoryBlockingStub(GrpcChannelFactory channels) {
        return InventoryServiceGrpc.newBlockingStub(channels.createChannel("inventory"));
    }
}