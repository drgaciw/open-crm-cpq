package com.opencrm.pricing.cost;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * {@code opencrm.pricing.cost-basis}. The staleness window is not configurable: it is Finance decision F-2
 * ({@link CostBasisStaleness}).
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "opencrm.pricing.cost-basis")
public class CostBasisProperties {

    /** The highest {@code max-bytes} allowed: the body is held in memory while it is parsed. */
    static final DataSize MAX_BYTES_CEILING = DataSize.ofMegabytes(100);

    /** Most data rows one export may carry; mock v1 has 31. A larger file is refused whole (422). */
    @Min(1)
    @Max(100_000)
    private int maxRows = 10_000;

    /**
     * Largest import body, {@code opencrm.pricing.cost-basis.max-bytes}: a plain number is bytes, or a size such as
     * {@code 512KB}. Checked before the body is parsed: a declared {@code Content-Length} over it is refused without
     * reading the body, and an undeclared one is read no further than one byte past it.
     */
    @NotNull
    @DataSizeUnit(DataUnit.BYTES)
    private DataSize maxBytes = DataSize.ofMegabytes(5);

    @AssertTrue(message = "max-bytes must be between 1 B and 100 MB")
    public boolean isMaxBytesInRange() {
        return maxBytes == null || (maxBytes.toBytes() >= 1 && maxBytes.compareTo(MAX_BYTES_CEILING) <= 0);
    }
}
