package com.opencrm.dealdesk.rules.service;

import com.opencrm.dealdesk.model.Deal;
import com.opencrm.dealdesk.model.tcv.ContingentRevenue;
import com.opencrm.dealdesk.model.tcv.DiscountsAndAdjustments;
import com.opencrm.dealdesk.model.tcv.TCVCalculation;
import com.opencrm.dealdesk.model.tcv.TCVComponent;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class TCVRuleExecutorService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int PERCENTAGE_SCALE = 2;

    public void executeTCVRules(Deal deal) {
        if (deal == null) {
            throw new IllegalArgumentException("deal must not be null");
        }

        TCVCalculation tcvCalculation = new TCVCalculation();
        tcvCalculation.setBaseValue(calculateBaseValue(deal.getComponents()));
        tcvCalculation.setVolumeDiscountAmount(ZERO);
        tcvCalculation.setEarlyPaymentDiscountAmount(ZERO);
        tcvCalculation.setTotalAdjustments(ZERO);
        tcvCalculation.setContingentRevenueAmount(ZERO);

        applyDiscountsAndAdjustments(tcvCalculation, deal.getAdjustments());
        applyContingentRevenue(tcvCalculation, deal.getContingentRevenue());
        calculateFinalValue(tcvCalculation);

        deal.setTcvCalculation(tcvCalculation);
        deal.setValue(tcvCalculation.getFinalValue());
    }

    private BigDecimal calculateBaseValue(List<TCVComponent> components) {
        if (components == null || components.isEmpty()) {
            return ZERO;
        }

        BigDecimal baseValue = ZERO;
        for (TCVComponent component : components) {
            if (component == null) {
                continue;
            }

            BigDecimal quantity = component.getQuantity();
            BigDecimal unitPrice = component.getUnitPrice();
            if (quantity == null || unitPrice == null) {
                continue;
            }

            baseValue = baseValue.add(quantity.multiply(unitPrice));
        }

        return baseValue;
    }

    private void applyDiscountsAndAdjustments(TCVCalculation tcvCalculation, DiscountsAndAdjustments adjustments) {
        if (adjustments == null) {
            return;
        }

        tcvCalculation.setVolumeDiscountAmount(resolveDiscountAmount(
                tcvCalculation.getBaseValue(),
                adjustments.getVolumeDiscountAmount(),
                adjustments.getVolumeDiscountPercentage()
        ));

        tcvCalculation.setEarlyPaymentDiscountAmount(resolveDiscountAmount(
                tcvCalculation.getBaseValue(),
                adjustments.getEarlyPaymentDiscountAmount(),
                adjustments.getEarlyPaymentDiscountPercentage()
        ));

        BigDecimal totalAdjustments = ZERO;
        if (adjustments.getPriceAdjustmentAmount() != null) {
            totalAdjustments = totalAdjustments.add(adjustments.getPriceAdjustmentAmount());
        }
        if (adjustments.getOtherDiscountAmount() != null) {
            totalAdjustments = totalAdjustments.add(adjustments.getOtherDiscountAmount());
        }
        tcvCalculation.setTotalAdjustments(totalAdjustments);
    }

    private BigDecimal resolveDiscountAmount(
            BigDecimal baseValue,
            BigDecimal explicitAmount,
            BigDecimal percentage
    ) {
        if (explicitAmount != null) {
            return explicitAmount;
        }
        if (percentage == null || baseValue == null) {
            return ZERO;
        }
        return baseValue.multiply(percentage).divide(HUNDRED, PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }

    private void applyContingentRevenue(TCVCalculation tcvCalculation, ContingentRevenue contingentRevenue) {
        if (contingentRevenue == null) {
            tcvCalculation.setContingentRevenueAmount(ZERO);
            return;
        }

        if (contingentRevenue.isIncludedInTCV() && contingentRevenue.getAmount() != null) {
            tcvCalculation.setContingentRevenueAmount(contingentRevenue.getAmount());
            return;
        }

        tcvCalculation.setContingentRevenueAmount(ZERO);
    }

    private void calculateFinalValue(TCVCalculation tcvCalculation) {
        BigDecimal finalValue = nullSafe(tcvCalculation.getBaseValue())
                .subtract(nullSafe(tcvCalculation.getVolumeDiscountAmount()))
                .subtract(nullSafe(tcvCalculation.getEarlyPaymentDiscountAmount()))
                .add(nullSafe(tcvCalculation.getTotalAdjustments()))
                .add(nullSafe(tcvCalculation.getContingentRevenueAmount()));

        tcvCalculation.setFinalValue(finalValue);
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? ZERO : value;
    }
}
