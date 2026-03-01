package com.opencrm.pricing.grpc;

import com.opencrm.common.grpc.*;
import com.opencrm.common.valueobject.Money;
import com.opencrm.pricing.engine.PriceCalculationService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;

/**
 * gRPC server implementation for pricing service.
 * Used by Configuration Engine for synchronous price calculations.
 */
@GrpcService
@RequiredArgsConstructor
@Slf4j
public class PricingGrpcService extends PricingServiceGrpc.PricingServiceImplBase {
    
    private final PriceCalculationService priceCalculationService;
    
    @Override
    public void calculatePrice(PriceRequest request, StreamObserver<PriceResponse> responseObserver) {
        try {
            log.debug("Received gRPC price calculation request for product: {}", request.getProductId());
            
            // Calculate price using Money value objects
            Money basePrice = priceCalculationService.calculateBasePrice(
                    request.getProductId(),
                    request.getQuantity(),
                    request.getCurrencyCode()
            );
            
            Money discount = priceCalculationService.calculateDiscount(
                    request.getProductId(),
                    basePrice,
                    request.getCustomerId(),
                    request.getConfigurationMap()
            );
            
            Money finalPrice = basePrice.subtract(discount);
            
            // Check if approval is required
            boolean approvalRequired = priceCalculationService.requiresApproval(discount, basePrice);
            String approvalReason = approvalRequired ? "Discount exceeds threshold" : "";
            
            // Build response
            PriceResponse response = PriceResponse.newBuilder()
                    .setPriceAmount(basePrice.getAmount().toPlainString())
                    .setCurrencyCode(basePrice.getCurrencyCode())
                    .setDiscountAmount(discount.getAmount().toPlainString())
                    .setFinalAmount(finalPrice.getAmount().toPlainString())
                    .setApprovalRequired(approvalRequired)
                    .setApprovalReason(approvalReason)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            log.debug("Completed gRPC price calculation for product: {}", request.getProductId());
            
        } catch (Exception e) {
            log.error("Error calculating price via gRPC", e);
            responseObserver.onError(e);
        }
    }
    
    @Override
    public void calculateQuoteTotal(QuoteTotalRequest request, StreamObserver<QuoteTotalResponse> responseObserver) {
        try {
            log.debug("Received gRPC quote total calculation request for quote: {}", request.getQuoteId());
            
            Money subtotal = Money.zero("USD");
            Money totalDiscount = Money.zero("USD");
            
            // Calculate totals from line items
            for (LineItem item : request.getLineItemsList()) {
                Money unitPrice = Money.of(new BigDecimal(item.getUnitPrice()), "USD");
                Money lineTotal = unitPrice.multiply(item.getQuantity());
                
                BigDecimal discountPct = new BigDecimal(item.getDiscountPercentage());
                Money lineDiscount = lineTotal.percentage(discountPct);
                
                subtotal = subtotal.add(lineTotal);
                totalDiscount = totalDiscount.add(lineDiscount);
            }
            
            Money taxAmount = subtotal.subtract(totalDiscount).percentage(BigDecimal.valueOf(10)); // 10% tax
            Money totalAmount = subtotal.subtract(totalDiscount).add(taxAmount);
            
            QuoteTotalResponse response = QuoteTotalResponse.newBuilder()
                    .setSubtotal(subtotal.getAmount().toPlainString())
                    .setTotalDiscount(totalDiscount.getAmount().toPlainString())
                    .setTaxAmount(taxAmount.getAmount().toPlainString())
                    .setTotalAmount(totalAmount.getAmount().toPlainString())
                    .setCurrencyCode("USD")
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            log.debug("Completed gRPC quote total calculation for quote: {}", request.getQuoteId());
            
        } catch (Exception e) {
            log.error("Error calculating quote total via gRPC", e);
            responseObserver.onError(e);
        }
    }
}
