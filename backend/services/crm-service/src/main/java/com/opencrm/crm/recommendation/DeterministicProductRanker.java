package com.opencrm.crm.recommendation;

import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealStage;
import com.opencrm.crm.deal.DealType;
import com.opencrm.crm.organization.Organization;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic ranker. No model call, no network, no feature flag.
 *
 * <p>Weights sum to at most 1: base 0.40, deal type 0.25, industry 0.15, keyword 0.10,
 * account win 0.05, peer win 0.05. ADR-015: the score does not change the deal.
 */
@Component
public class DeterministicProductRanker {

    static final BigDecimal BASE = new BigDecimal("0.40");
    private static final BigDecimal TYPE_MATCH = new BigDecimal("0.25");
    private static final BigDecimal INDUSTRY_MATCH = new BigDecimal("0.15");
    private static final BigDecimal KEYWORD = new BigDecimal("0.10");
    private static final BigDecimal ACCOUNT_WIN = new BigDecimal("0.05");
    private static final BigDecimal PEER_WIN = new BigDecimal("0.05");
    private static final int SCALE = 2;

    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]{3,}");

    public List<RecommendedProductDto> rank(Deal deal, Organization organization, List<Deal> accountHistory, List<Deal> peerWins) {
        DealType dealType = deal.getType();
        String industry = normalizedIndustry(organization);
        Set<String> tokens = tokens(deal.getName(), deal.getDescription());
        List<Deal> account = others(deal, accountHistory);
        List<Deal> peers = others(deal, peerWins);

        List<RecommendedProductDto> ranked = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AdvisoryProductCatalog.Product product : AdvisoryProductCatalog.PRODUCTS) {
            if (!seen.add(product.productId())) {
                continue;
            }
            Score score = score(product, dealType, industry, tokens, account, peers);
            if (!score.signaled()) {
                continue;
            }
            ranked.add(new RecommendedProductDto(product.productId(), rationale(product, dealType, industry, score), score.value()));
        }
        if (ranked.isEmpty()) {
            for (String productId : AdvisoryProductCatalog.GENERAL_COMPANION_IDS) {
                AdvisoryProductCatalog.Product product = AdvisoryProductCatalog.require(productId);
                Score score = new Score(BASE, false, false, null, 0, 0);
                ranked.add(new RecommendedProductDto(
                        product.productId(),
                        rationale(product, dealType, industry, score) + " No stronger account, win, or catalog signal, so this is a general companion.",
                        score.value()));
            }
        }
        ranked.sort(Comparator.comparing(RecommendedProductDto::confidence).reversed()
                .thenComparing(RecommendedProductDto::productId));
        return List.copyOf(ranked);
    }

    private static Score score(AdvisoryProductCatalog.Product product, DealType dealType, String industry,
                                Set<String> tokens, List<Deal> account, List<Deal> peers) {
        boolean typeMatch = dealType != null && product.dealTypes().contains(dealType);
        boolean industryMatch = industry != null && product.industries().contains(industry);
        String keyword = matchedKeyword(product, tokens);
        int accountWins = wonCount(account, product.dealTypes());
        int peerWinCount = wonCount(peers, product.dealTypes());
        boolean signaled = typeMatch || industryMatch || keyword != null || accountWins > 0 || peerWinCount > 0;
        BigDecimal value = BASE;
        if (typeMatch) {
            value = value.add(TYPE_MATCH);
        }
        if (industryMatch) {
            value = value.add(INDUSTRY_MATCH);
        }
        if (keyword != null) {
            value = value.add(KEYWORD);
        }
        if (accountWins > 0) {
            value = value.add(ACCOUNT_WIN);
        }
        if (peerWinCount > 0) {
            value = value.add(PEER_WIN);
        }
        return new Score(clamp(value), typeMatch, industryMatch, keyword, accountWins, peerWinCount, signaled);
    }

    private static BigDecimal clamp(BigDecimal value) {
        BigDecimal scaled = value.setScale(SCALE, RoundingMode.HALF_UP);
        if (scaled.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        if (scaled.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE.setScale(SCALE, RoundingMode.HALF_UP);
        }
        return scaled;
    }

    private static String rationale(AdvisoryProductCatalog.Product product, DealType dealType, String industry, Score score) {
        StringBuilder text = new StringBuilder();
        if (dealType == null) {
            text.append("Advisory suggestion for ").append(product.name()).append(" on an open opportunity.");
        } else {
            text.append("Advisory suggestion for ").append(product.name()).append(" on a ")
                    .append(dealType.name()).append(" opportunity.");
        }
        if (score.typeMatch()) {
            text.append(" Deal type matches this product.");
        }
        if (score.industryMatch()) {
            text.append(" Account industry ").append(industry).append(" matches the catalog.");
        }
        if (score.keyword() != null) {
            text.append(" Deal text matches ").append(score.keyword()).append('.');
        }
        if (score.accountWins() > 0) {
            text.append(" This account has ").append(wonPhrase(score.accountWins())).append(" of this motion.");
        }
        if (score.peerWins() > 0) {
            text.append(' ').append(wonPhrase(score.peerWins())).append(" in the same industry used this motion.");
        }
        return text.toString();
    }

    private static String wonPhrase(int count) {
        return count == 1 ? "1 won deal" : count + " won deals";
    }

    private static String matchedKeyword(AdvisoryProductCatalog.Product product, Set<String> tokens) {
        for (String keyword : product.keywords()) {
            if (tokens.contains(keyword)) {
                return keyword;
            }
        }
        return null;
    }

    private static int wonCount(List<Deal> deals, Set<DealType> types) {
        int count = 0;
        for (Deal deal : deals) {
            if (deal.getStage() == DealStage.WON && deal.getType() != null && types.contains(deal.getType())) {
                count++;
            }
        }
        return count;
    }

    private static List<Deal> others(Deal current, List<Deal> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Deal> kept = new ArrayList<>();
        for (Deal row : rows) {
            if (row == null || row.isDeleted()) {
                continue;
            }
            if (current.getId() != null && current.getId().equals(row.getId())) {
                continue;
            }
            kept.add(row);
        }
        kept.sort(Comparator.comparing(Deal::getId, Comparator.nullsLast(Long::compareTo)));
        return List.copyOf(kept);
    }

    private static String normalizedIndustry(Organization organization) {
        if (organization == null || organization.getIndustry() == null || organization.getIndustry().isBlank()) {
            return null;
        }
        return organization.getIndustry().trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> tokens(String name, String description) {
        StringBuilder text = new StringBuilder();
        if (name != null) {
            text.append(name);
        }
        text.append(' ');
        if (description != null) {
            text.append(description);
        }
        Set<String> tokens = new HashSet<>();
        Matcher matcher = TOKEN.matcher(text.toString().toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private record Score(
            BigDecimal value,
            boolean typeMatch,
            boolean industryMatch,
            String keyword,
            int accountWins,
            int peerWins,
            boolean signaled) {

        private Score(BigDecimal value, boolean typeMatch, boolean industryMatch, String keyword,
                      int accountWins, int peerWins) {
            this(value, typeMatch, industryMatch, keyword, accountWins, peerWins, true);
        }
    }
}
