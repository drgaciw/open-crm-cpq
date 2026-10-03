package com.opencrm.quote.ramp;

import com.opencrm.common.entity.BaseEntity;
import com.opencrm.quote.lineitem.QuoteLineItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "quote_ramp_steps", indexes = {
        @Index(name = "idx_quote_ramp_steps_line_item_id", columnList = "line_item_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_quote_ramp_steps_line_sequence", columnNames = {"line_item_id", "sequence"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class QuoteRampStep extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "line_item_id", nullable = false)
    private QuoteLineItem lineItem;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Column(name = "start_month", nullable = false)
    private int startMonth;

    @Column(name = "months", nullable = false)
    private int months;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "price_basis", nullable = false, length = 30)
    private String priceBasis;

    @Column(name = "unit_price", precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "discount_fraction", precision = 19, scale = 4)
    private BigDecimal discountFraction;
}
