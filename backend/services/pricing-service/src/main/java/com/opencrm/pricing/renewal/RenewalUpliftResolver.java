package com.opencrm.pricing.renewal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Prices one flat renewal period for {@link RenewalUpliftBasis#CONTRACTED_NET} and
 * {@link RenewalUpliftMode#UPLIFT_PCT}, using the uplift ADR (D-01 through D-05).
 *
 * <p>Callers pass the five rungs unmerged. This class does not read another service's database,
 * does not call the rules port, does not persist a snapshot, and does not write an event.
 * List bases stay unresolved until DRG-539. Other modes stay unpriced until DRG-549.
 * Escalator years are not expanded (DRG-533, DRG-568).
 */
public final class RenewalUpliftResolver {
    public static final String RULES_EXPLANATION_CODE = "RENEWAL_UPLIFT_POLICY";

    private static final int UNIT_SCALE = 4;
    private static final int AMOUNT_SCALE = 2;
    private static final List<RenewalUpliftRungKind> PRECEDENCE = List.of(
            RenewalUpliftRungKind.CLAUSE,
            RenewalUpliftRungKind.LINE,
            RenewalUpliftRungKind.AGREEMENT,
            RenewalUpliftRungKind.ACCOUNT,
            RenewalUpliftRungKind.RULES);

    public RenewalUpliftResult resolve(RenewalUpliftRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Renewal uplift request is required");
        }
        if (request.quantity() != null && request.quantity().signum() < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        if (request.termMonths() != null && request.termMonths() < 1) {
            throw new IllegalArgumentException("Term months must be at least 1 when supplied");
        }

        Map<RenewalUpliftRungKind, RenewalUpliftRung> rungs = index(request.rungs());
        FieldSource<RenewalUpliftMode> mode = findFirst(rungs, RenewalUpliftRung::mode, false);
        if (mode != null) {
            if (mode.value() != RenewalUpliftMode.UPLIFT_PCT) {
                throw new RenewalUpliftModeOutOfSliceException(mode.value());
            }
            requiredSourceId(rungs.get(mode.rung()));
        }
        FieldSource<RenewalUpliftBasis> basis = first(rungs, RenewalUpliftRung::basis, false);
        FieldSource<BigDecimal> percent = first(rungs, RenewalUpliftRung::upliftPercentPoints, true);
        FieldSource<BigDecimal> maxIncrease = first(rungs, RenewalUpliftRung::maxIncreasePct, false);
        FieldSource<Boolean> notAboveList = first(rungs, RenewalUpliftRung::notAboveList, false);
        FieldSource<Boolean> notBelow = first(rungs, RenewalUpliftRung::notBelowExitNet, false);
        FieldSource<BigDecimal> minIncrease = first(rungs, RenewalUpliftRung::minIncreasePct, false);
        FieldSource<BigDecimal> minUnit = first(rungs, RenewalUpliftRung::minUnitAmount, false);
        FieldSource<RenewalPriceAsOf> priceAsOf = first(rungs, RenewalUpliftRung::priceAsOf, false);

        Merged merged = new Merged(
                request,
                mode,
                basis == null ? def(RenewalUpliftBasis.CONTRACTED_NET) : basis,
                percent,
                maxIncrease,
                notAboveList == null ? def(Boolean.FALSE) : notAboveList,
                notBelow == null ? def(Boolean.TRUE) : notBelow,
                minIncrease,
                minUnit,
                priceAsOf == null ? def(RenewalPriceAsOf.EFFECTIVE_DATE) : priceAsOf,
                clauseId(mode, basis, percent, maxIncrease, notAboveList, notBelow, minIncrease, minUnit, priceAsOf),
                retention(rungs));
        Pin pin = selectPin(merged.priceAsOf().value(), request.pins());

        if (mode == null) {
            return finish(merged, pin, RenewalUpliftResolution.UNRESOLVED_RENEWAL_MODE, null, null, null, null, null);
        }
        if (percent == null) {
            return finish(merged, pin, RenewalUpliftResolution.UNRESOLVED_UPLIFT, null, null, null, null, null);
        }
        if (percent.value().signum() < 0 || percent.value().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Renewal uplift percent points must be between 0 and 100");
        }
        if (merged.basis().value() != RenewalUpliftBasis.CONTRACTED_NET
                || request.exitNet() == null
                || request.exitNet().signum() < 0) {
            return finish(merged, pin, RenewalUpliftResolution.UNRESOLVED_BASIS, null, null, null, null, null);
        }
        boolean listRequired = Boolean.TRUE.equals(merged.notAboveList().value());
        if (pin.date() == null || pin.versionId() == null
                || (listRequired && pin.listUnit() == null)) {
            return finish(merged, pin, RenewalUpliftResolution.UNRESOLVED_BASIS, null, null, null, null, null);
        }

        Side cap = cap(request.exitNet(), maxIncrease, listRequired ? pin.listUnit() : null);
        Side floor = floor(request.exitNet(), merged.notBelowExitNet().value(), minIncrease, minUnit);
        if (cap != null && floor != null && floor.normalized().compareTo(cap.normalized()) > 0) {
            return finish(merged, pin, RenewalUpliftResolution.PRICE_PROTECTION_CONFLICT,
                    bound(cap), bound(floor), null, null, null);
        }

        BigDecimal unit = increase(request.exitNet(), percent.value()).setScale(UNIT_SCALE, RoundingMode.HALF_UP);
        MonetaryBound capApplied = null;
        MonetaryBound floorApplied = null;
        if (cap != null && unit.compareTo(cap.normalized()) > 0) {
            unit = cap.normalized();
            capApplied = bound(cap);
        }
        if (floor != null && unit.compareTo(floor.normalized()) < 0) {
            unit = floor.normalized();
            floorApplied = bound(floor);
        }
        return finish(merged, pin, RenewalUpliftResolution.PRICED, bound(cap), bound(floor), capApplied, floorApplied, unit);
    }

    private static RenewalUpliftResult finish(
            Merged merged,
            Pin pin,
            RenewalUpliftResolution resolution,
            MonetaryBound capBound,
            MonetaryBound floorBound,
            MonetaryBound capApplied,
            MonetaryBound floorApplied,
            BigDecimal unitPrice) {
        RenewalUpliftRequest request = merged.request();
        boolean rulesSupplied = merged.percentPoints() != null
                && merged.percentPoints().rung() == RenewalUpliftRungKind.RULES
                && resolution != RenewalUpliftResolution.UNRESOLVED_RENEWAL_MODE
                && resolution != RenewalUpliftResolution.UNRESOLVED_UPLIFT;
        BigDecimal basisAmount = null;
        if (merged.basis().value() == RenewalUpliftBasis.CONTRACTED_NET
                && request.exitNet() != null
                && request.exitNet().signum() >= 0) {
            basisAmount = request.exitNet();
        }
        BigDecimal extended = null;
        Integer periods = null;
        if (unitPrice != null) {
            periods = 1;
            if (request.quantity() != null && request.termMonths() != null) {
                extended = extend(unitPrice, request.quantity(), request.termMonths());
            }
        }
        return new RenewalUpliftResult(
                resolution,
                merged.mode(),
                merged.basis(),
                merged.percentPoints(),
                request.exitNet(),
                basisAmount,
                merged.priceAsOf(),
                pin.date(),
                pin.versionId(),
                pin.listUnit(),
                merged.maxIncreasePct(),
                merged.notAboveList(),
                merged.notBelowExitNet(),
                merged.minIncreasePct(),
                merged.minUnitAmount(),
                capBound,
                floorBound,
                capApplied,
                floorApplied,
                unitPrice,
                extended,
                periods,
                appliedId(merged, resolution),
                merged.clauseId(),
                rulesSupplied,
                rulesSupplied ? RULES_EXPLANATION_CODE : null,
                merged.retentionMarker(),
                request.currency(),
                request.quantity(),
                request.termMonths());
    }

    private static String appliedId(Merged merged, RenewalUpliftResolution resolution) {
        if (resolution == RenewalUpliftResolution.UNRESOLVED_RENEWAL_MODE || merged.mode() == null) {
            return null;
        }
        if (merged.percentPoints() != null && resolution != RenewalUpliftResolution.UNRESOLVED_UPLIFT) {
            return merged.percentPoints().sourceId();
        }
        return merged.mode().sourceId();
    }

    private static Side cap(BigDecimal exitNet, FieldSource<BigDecimal> maxIncrease, BigDecimal listUnit) {
        Side winner = null;
        if (maxIncrease != null) {
            winner = lower(winner, side(increase(exitNet, maxIncrease.value()), RoundingMode.FLOOR));
        }
        if (listUnit != null) {
            winner = lower(winner, side(listUnit, RoundingMode.FLOOR));
        }
        return winner;
    }

    private static Side floor(BigDecimal exitNet, boolean notBelowExitNet, FieldSource<BigDecimal> minIncrease,
                              FieldSource<BigDecimal> minUnit) {
        Side winner = null;
        if (notBelowExitNet) {
            winner = higher(winner, side(exitNet, RoundingMode.CEILING));
        }
        if (minIncrease != null) {
            winner = higher(winner, side(increase(exitNet, minIncrease.value()), RoundingMode.CEILING));
        }
        if (minUnit != null) {
            winner = higher(winner, side(minUnit.value(), RoundingMode.CEILING));
        }
        return winner;
    }

    private static Side lower(Side current, Side candidate) {
        if (current == null || candidate.normalized().compareTo(current.normalized()) < 0
                || (candidate.normalized().compareTo(current.normalized()) == 0
                    && candidate.exact().compareTo(current.exact()) < 0)) {
            return candidate;
        }
        return current;
    }

    private static Side higher(Side current, Side candidate) {
        if (current == null || candidate.normalized().compareTo(current.normalized()) > 0
                || (candidate.normalized().compareTo(current.normalized()) == 0
                    && candidate.exact().compareTo(current.exact()) > 0)) {
            return candidate;
        }
        return current;
    }

    private static Side side(BigDecimal exact, RoundingMode mode) {
        return new Side(exact, exact.setScale(UNIT_SCALE, mode));
    }

    private static MonetaryBound bound(Side side) {
        if (side == null) {
            return null;
        }
        return new MonetaryBound(side.exact(), side.normalized());
    }

    private static BigDecimal increase(BigDecimal base, BigDecimal percentPoints) {
        return base.multiply(BigDecimal.ONE.add(percentPoints.movePointLeft(2)));
    }

    private static BigDecimal extend(BigDecimal unit, BigDecimal quantity, int termMonths) {
        BigDecimal monthly = unit.multiply(quantity).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        return monthly.multiply(BigDecimal.valueOf(termMonths)).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private static Map<RenewalUpliftRungKind, RenewalUpliftRung> index(List<RenewalUpliftRung> rungs) {
        Map<RenewalUpliftRungKind, RenewalUpliftRung> indexed = new EnumMap<>(RenewalUpliftRungKind.class);
        if (rungs == null) {
            return indexed;
        }
        for (RenewalUpliftRung rung : rungs) {
            if (rung == null || rung.kind() == null) {
                throw new IllegalArgumentException("Each renewal uplift rung needs a kind");
            }
            if (indexed.put(rung.kind(), rung) != null) {
                throw new IllegalArgumentException("Duplicate renewal uplift rung: " + rung.kind());
            }
        }
        return indexed;
    }

    private static <T> FieldSource<T> first(
            Map<RenewalUpliftRungKind, RenewalUpliftRung> rungs,
            FieldReader<T> read,
            boolean allowRules) {
        FieldSource<T> source = findFirst(rungs, read, allowRules);
        if (source != null) {
            requiredSourceId(rungs.get(source.rung()));
        }
        return source;
    }

    private static <T> FieldSource<T> findFirst(
            Map<RenewalUpliftRungKind, RenewalUpliftRung> rungs,
            FieldReader<T> read,
            boolean allowRules) {
        for (RenewalUpliftRungKind kind : PRECEDENCE) {
            if (kind == RenewalUpliftRungKind.RULES && !allowRules) {
                continue;
            }
            RenewalUpliftRung rung = rungs.get(kind);
            if (rung == null) {
                continue;
            }
            T value = read.read(rung);
            if (value != null) {
                return new FieldSource<>(value, kind, blankToNull(rung.sourceId()));
            }
        }
        return null;
    }

    private static String clauseId(FieldSource<?>... sources) {
        for (FieldSource<?> source : sources) {
            if (source != null && source.rung() == RenewalUpliftRungKind.CLAUSE) {
                return source.sourceId();
            }
        }
        return null;
    }

    private static String retention(Map<RenewalUpliftRungKind, RenewalUpliftRung> rungs) {
        RenewalUpliftRung rules = rungs.get(RenewalUpliftRungKind.RULES);
        if (rules == null) {
            return null;
        }
        String marker = blankToNull(rules.retentionMarker());
        if (marker != null) {
            requiredSourceId(rules);
        }
        return marker;
    }

    private static String requiredSourceId(RenewalUpliftRung rung) {
        String sourceId = blankToNull(rung.sourceId());
        if (sourceId == null) {
            throw new IllegalArgumentException("Contributing " + rung.kind() + " rung requires a source ID");
        }
        return sourceId;
    }

    private static Pin selectPin(RenewalPriceAsOf priceAsOf, RenewalPriceAsOfPins pins) {
        if (pins == null) {
            return new Pin(null, null, null);
        }
        return switch (priceAsOf) {
            case ACTION_DATE -> new Pin(pins.actionDate(), blankToNull(pins.actionPriceBookVersionId()), pins.actionListUnit());
            case EFFECTIVE_DATE -> new Pin(
                    pins.effectiveDate(), blankToNull(pins.effectivePriceBookVersionId()), pins.effectiveListUnit());
            case LINE_START -> new Pin(pins.lineStart(), blankToNull(pins.lineStartPriceBookVersionId()), pins.lineStartListUnit());
        };
    }

    private static <T> FieldSource<T> def(T value) {
        return new FieldSource<>(value, null, null);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    @FunctionalInterface
    private interface FieldReader<T> {
        T read(RenewalUpliftRung rung);
    }

    private record Merged(
            RenewalUpliftRequest request,
            FieldSource<RenewalUpliftMode> mode,
            FieldSource<RenewalUpliftBasis> basis,
            FieldSource<BigDecimal> percentPoints,
            FieldSource<BigDecimal> maxIncreasePct,
            FieldSource<Boolean> notAboveList,
            FieldSource<Boolean> notBelowExitNet,
            FieldSource<BigDecimal> minIncreasePct,
            FieldSource<BigDecimal> minUnitAmount,
            FieldSource<RenewalPriceAsOf> priceAsOf,
            String clauseId,
            String retentionMarker) {
    }

    private record Pin(LocalDate date, String versionId, BigDecimal listUnit) {
    }

    private record Side(BigDecimal exact, BigDecimal normalized) {
    }
}
