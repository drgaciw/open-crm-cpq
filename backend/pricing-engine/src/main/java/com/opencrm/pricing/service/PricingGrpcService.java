package com.opencrm.pricing.service;

import com.opencrm.shared.proto.PricingGrpcServiceGrpc;
import com.opencrm.shared.proto.CalculatePriceRequest;
import com.opencrm.shared.proto.CalculatePriceResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import java.math.BigDecimal;

@GrpcService
public class PricingGrpcService extends PricingGrpcServiceGrpc.PricingGrpcServiceImplBase {
    
    @Override
    public void calculatePrice(CalculatePriceRequest request, StreamObserver<CalculatePriceResponse> responseObserver) {
        // In a real implementation, we would look up the product price and apply rules.
        // For now, we return a dummy response.
        
        CalculatePriceResponse response = CalculatePriceResponse.newBuilder()
                .setBasePrice("100.00")
                .setCalculatedPrice("100.00")
                .setCurrencyCode("USD")
                .build();
                
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
