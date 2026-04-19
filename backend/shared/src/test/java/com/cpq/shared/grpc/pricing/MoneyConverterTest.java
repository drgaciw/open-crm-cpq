package com.cpq.shared.grpc.pricing;

import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MoneyConverterTest {

    @Test
    void toProto_fromDomainMoney_shouldConvertCorrectly() {
        com.cpq.shared.money.Money domainMoney = new com.cpq.shared.money.Money(new BigDecimal("12.34"), "USD");

        Money protoMoney = MoneyConverter.toProto(domainMoney);

        assertNotNull(protoMoney);
        assertEquals("USD", protoMoney.getCurrency());
        assertEquals(1234L, protoMoney.getAmountMinorUnits());
    }

    @Test
    void toProto_fromDomainMoney_withNoDecimals_shouldConvertCorrectly() {
        com.cpq.shared.money.Money domainMoney = new com.cpq.shared.money.Money(new BigDecimal("15"), "EUR");

        Money protoMoney = MoneyConverter.toProto(domainMoney);

        assertNotNull(protoMoney);
        assertEquals("EUR", protoMoney.getCurrency());
        assertEquals(1500L, protoMoney.getAmountMinorUnits());
    }

    @Test
    void fromProto_toDomainMoney_shouldConvertCorrectly() {
        Money protoMoney = Money.newBuilder()
                .setCurrency("GBP")
                .setAmountMinorUnits(5678L)
                .build();

        com.cpq.shared.money.Money domainMoney = MoneyConverter.fromProto(protoMoney);

        assertNotNull(domainMoney);
        assertEquals("GBP", domainMoney.getCurrency());
        assertEquals(new BigDecimal("56.78"), domainMoney.getAmount());
    }

    @Test
    void fromProto_toDomainMoney_withNoCents_shouldConvertCorrectly() {
        Money protoMoney = Money.newBuilder()
                .setCurrency("JPY")
                .setAmountMinorUnits(10000L)
                .build();

        com.cpq.shared.money.Money domainMoney = MoneyConverter.fromProto(protoMoney);

        assertNotNull(domainMoney);
        assertEquals("JPY", domainMoney.getCurrency());
        assertEquals(new BigDecimal("100.00"), domainMoney.getAmount());
    }

    @Test
    void toProto_fromInstant_shouldConvertCorrectly() {
        Instant instant = Instant.ofEpochSecond(1609459200L, 123456789); // 2021-01-01T00:00:00Z + nanos

        Timestamp timestamp = MoneyConverter.toProto(instant);

        assertNotNull(timestamp);
        assertEquals(1609459200L, timestamp.getSeconds());
        assertEquals(123456789, timestamp.getNanos());
    }

    @Test
    void fromProto_toInstant_shouldConvertCorrectly() {
        Timestamp timestamp = Timestamp.newBuilder()
                .setSeconds(1672531200L) // 2023-01-01T00:00:00Z
                .setNanos(987654321)
                .build();

        Instant instant = MoneyConverter.fromProto(timestamp);

        assertNotNull(instant);
        assertEquals(1672531200L, instant.getEpochSecond());
        assertEquals(987654321, instant.getNano());
    }
}
