package com.opencrm.pricing.grpc;

import com.opencrm.common.grpc.PriceRequest;
import com.opencrm.common.grpc.PriceResponse;
import com.opencrm.common.valueobject.Money;
import com.opencrm.pricing.engine.PriceCalculationService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pricing gRPC Service - Calculate Price")
class PricingGrpcServiceTest {

    @Mock
    private PriceCalculationService priceCalculationService;

    @Mock
    private StreamObserver<PriceResponse> responseObserver;

    @InjectMocks
    private PricingGrpcService pricingGrpcService;

    @Captor
    private ArgumentCaptor<PriceResponse> responseCaptor;

    @Test
    @DisplayName("should calculate price successfully without approval")
    void calculatePriceSuccessfullyWithoutApproval() {
        // Arrange
        PriceRequest request = PriceRequest.newBuilder()
                .setProductId("PROD-1")
                .setQuantity(5)
                .setCurrencyCode("USD")
                .setCustomerId("CUST-1")
                .putAllConfiguration(Map.of("color", "red"))
                .build();

        Money basePrice = Money.usd(BigDecimal.valueOf(500.00));
        Money discount = Money.usd(BigDecimal.valueOf(50.00));

        when(priceCalculationService.calculateBasePrice("PROD-1", 5, "USD")).thenReturn(basePrice);
        when(priceCalculationService.calculateDiscount("PROD-1", basePrice, "CUST-1", request.getConfigurationMap())).thenReturn(discount);
        when(priceCalculationService.requiresApproval(discount, basePrice)).thenReturn(false);

        // Act
        pricingGrpcService.calculatePrice(request, responseObserver);

        // Assert
        verify(responseObserver).onNext(responseCaptor.capture());
        verify(responseObserver).onCompleted();
        verify(responseObserver, never()).onError(any());

        PriceResponse response = responseCaptor.getValue();
        assertEquals("500.00", response.getPriceAmount());
        assertEquals("USD", response.getCurrencyCode());
        assertEquals("50.00", response.getDiscountAmount());
        assertEquals("450.00", response.getFinalAmount());
        assertFalse(response.getApprovalRequired());
        assertEquals("", response.getApprovalReason());
    }

    @Test
    @DisplayName("should calculate price and require approval")
    void calculatePriceWithApproval() {
        // Arrange
        PriceRequest request = PriceRequest.newBuilder()
                .setProductId("PROD-2")
                .setQuantity(10)
                .setCurrencyCode("EUR")
                .build();

        Money basePrice = Money.of(BigDecimal.valueOf(1000.00), "EUR");
        Money discount = Money.of(BigDecimal.valueOf(300.00), "EUR");

        when(priceCalculationService.calculateBasePrice("PROD-2", 10, "EUR")).thenReturn(basePrice);
        when(priceCalculationService.calculateDiscount(eq("PROD-2"), eq(basePrice), anyString(), anyMap())).thenReturn(discount);
        when(priceCalculationService.requiresApproval(discount, basePrice)).thenReturn(true);

        // Act
        pricingGrpcService.calculatePrice(request, responseObserver);

        // Assert
        verify(responseObserver).onNext(responseCaptor.capture());
        verify(responseObserver).onCompleted();

        PriceResponse response = responseCaptor.getValue();
        assertEquals("1000.00", response.getPriceAmount());
        assertEquals("EUR", response.getCurrencyCode());
        assertEquals("300.00", response.getDiscountAmount());
        assertEquals("700.00", response.getFinalAmount());
        assertTrue(response.getApprovalRequired());
        assertEquals("Discount exceeds threshold", response.getApprovalReason());
    }

    @Test
    @DisplayName("should handle exception and call onError")
    void handleExceptionInCalculation() {
        // Arrange
        PriceRequest request = PriceRequest.newBuilder()
                .setProductId("PROD-3")
                .build();

        RuntimeException exception = new RuntimeException("Calculation failed");
        when(priceCalculationService.calculateBasePrice(anyString(), anyInt(), anyString()))
                .thenThrow(exception);

        // Act
        pricingGrpcService.calculatePrice(request, responseObserver);

        // Assert
        verify(responseObserver).onError(exception);
        verify(responseObserver, never()).onNext(any());
        verify(responseObserver, never()).onCompleted();
    }
}
