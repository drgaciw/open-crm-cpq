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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PricingGrpcService - calculatePrice")
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
    @DisplayName("should calculate price successfully and return response without approval")
    void calculatePriceSuccessfullyWithoutApproval() {
        // Arrange
        PriceRequest request = PriceRequest.newBuilder()
                .setProductId("PROD-1")
                .setQuantity(5)
                .setCurrencyCode("USD")
                .setCustomerId("CUST-1")
                .putAllConfiguration(Map.of("color", "red"))
                .build();

        Money basePrice = Money.of(new BigDecimal("500.00"), "USD");
        Money discount = Money.of(new BigDecimal("50.00"), "USD");

        when(priceCalculationService.calculateBasePrice(
                eq("PROD-1"), eq(5), eq("USD")))
                .thenReturn(basePrice);

        when(priceCalculationService.calculateDiscount(
                eq("PROD-1"), eq(basePrice), eq("CUST-1"), eq(request.getConfigurationMap())))
                .thenReturn(discount);

        when(priceCalculationService.requiresApproval(eq(discount), eq(basePrice)))
                .thenReturn(false);

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
    @DisplayName("should calculate price successfully and return response requiring approval")
    void calculatePriceSuccessfullyWithApproval() {
        // Arrange
        PriceRequest request = PriceRequest.newBuilder()
                .setProductId("PROD-1")
                .setQuantity(5)
                .setCurrencyCode("USD")
                .setCustomerId("CUST-1")
                .putAllConfiguration(Map.of("color", "red"))
                .build();

        Money basePrice = Money.of(new BigDecimal("500.00"), "USD");
        Money discount = Money.of(new BigDecimal("150.00"), "USD");

        when(priceCalculationService.calculateBasePrice(
                eq("PROD-1"), eq(5), eq("USD")))
                .thenReturn(basePrice);

        when(priceCalculationService.calculateDiscount(
                eq("PROD-1"), eq(basePrice), eq("CUST-1"), eq(request.getConfigurationMap())))
                .thenReturn(discount);

        when(priceCalculationService.requiresApproval(eq(discount), eq(basePrice)))
                .thenReturn(true);

        // Act
        pricingGrpcService.calculatePrice(request, responseObserver);

        // Assert
        verify(responseObserver).onNext(responseCaptor.capture());
        verify(responseObserver).onCompleted();
        verify(responseObserver, never()).onError(any());

        PriceResponse response = responseCaptor.getValue();
        assertEquals("500.00", response.getPriceAmount());
        assertEquals("USD", response.getCurrencyCode());
        assertEquals("150.00", response.getDiscountAmount());
        assertEquals("350.00", response.getFinalAmount());
        assertTrue(response.getApprovalRequired());
        assertEquals("Discount exceeds threshold", response.getApprovalReason());
    }

    @Test
    @DisplayName("should handle exception and call onError")
    void handleExceptionAndCallOnError() {
        // Arrange
        PriceRequest request = PriceRequest.newBuilder()
                .setProductId("PROD-1")
                .setQuantity(5)
                .setCurrencyCode("USD")
                .build();

        RuntimeException exception = new RuntimeException("Test exception");
        when(priceCalculationService.calculateBasePrice(anyString(), anyInt(), anyString()))
                .thenThrow(exception);

        // Act
        pricingGrpcService.calculatePrice(request, responseObserver);

        // Assert
        verify(responseObserver, never()).onNext(any());
        verify(responseObserver, never()).onCompleted();
        verify(responseObserver).onError(exception);
    }
}
