package com.opencrm.config.service;

import com.opencrm.shared.proto.PricingGrpcServiceGrpc;
import com.opencrm.shared.proto.CalculatePriceRequest;
import com.opencrm.shared.proto.CalculatePriceResponse;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ConfigurationService {

    @GrpcClient("pricing-service")
    private PricingGrpcServiceGrpc.PricingGrpcServiceBlockingStub pricingStub;

    public CalculatePriceResponse validateAndPrice(String productId, Map<String, String> config) {
        // Validate configuration logic here
        
        // Call Pricing Engine
        CalculatePriceRequest request = CalculatePriceRequest.newBuilder()
                .setProductId(productId)
                .putAllConfiguration(config)
                .build();
                
        return pricingStub.calculatePrice(request);
    }
}
