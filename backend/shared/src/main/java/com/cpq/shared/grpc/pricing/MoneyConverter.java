package com.cpq.shared.grpc.pricing;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Utility class for converting between proto Money and domain Money objects
 */
public class MoneyConverter {
    
    public static Money toProto(com.cpq.shared.money.Money domainMoney) {
        // Convert BigDecimal to minor units (e.g., cents for USD)
        long minorUnits = domainMoney.getAmount()
            .multiply(BigDecimal.valueOf(100))
            .setScale(0, java.math.RoundingMode.HALF_UP)
            .longValue();
        
        return Money.newBuilder()
            .setCurrency(domainMoney.getCurrency())
            .setAmountMinorUnits(minorUnits)
            .build();
    }
    
    public static com.cpq.shared.money.Money fromProto(Money protoMoney) {
        // Convert minor units back to BigDecimal
        BigDecimal amount = BigDecimal.valueOf(protoMoney.getAmountMinorUnits())
            .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        
        return new com.cpq.shared.money.Money(amount, protoMoney.getCurrency());
    }
    
    public static Timestamp toProto(Instant instant) {
        return Timestamp.newBuilder()
            .setSeconds(instant.getEpochSecond())
            .setNanos(instant.getNano())
            .build();
    }
    
    public static Instant fromProto(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}