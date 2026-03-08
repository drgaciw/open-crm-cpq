package com.opencrm.dealdesk.model;

import com.opencrm.common.entity.BaseEntity;
import com.opencrm.dealdesk.model.tcv.ContingentRevenue;
import com.opencrm.dealdesk.model.tcv.DiscountsAndAdjustments;
import com.opencrm.dealdesk.model.tcv.PricingModel;
import com.opencrm.dealdesk.model.tcv.TCVCalculation;
import com.opencrm.dealdesk.model.tcv.TCVComponent;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "deals", schema = "deal_desk", indexes = {
        @Index(name = "idx_deal_status", columnList = "status"),
        @Index(name = "idx_deal_account_id", columnList = "account_id"),
        @Index(name = "idx_deal_sf_opp_id", columnList = "salesforce_opportunity_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Deal extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "deal_value", precision = 19, scale = 4)
    private BigDecimal value;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private DealStatus status = DealStatus.DRAFT;

    @Column(name = "salesforce_opportunity_id", unique = true)
    private String salesforceOpportunityId;

    @Column(name = "account_id")
    private String accountId;

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "components", columnDefinition = "jsonb")
    private List<TCVComponent> components;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pricing_model", columnDefinition = "jsonb")
    private PricingModel pricingModel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "adjustments", columnDefinition = "jsonb")
    private DiscountsAndAdjustments adjustments;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contingent_revenue", columnDefinition = "jsonb")
    private ContingentRevenue contingentRevenue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tcv_calculation", columnDefinition = "jsonb")
    private TCVCalculation tcvCalculation;

    @Column(name = "synced", nullable = false)
    private boolean synced = false;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "deal_products", schema = "deal_desk", joinColumns = @JoinColumn(name = "deal_id"))
    @Column(name = "product_id", nullable = false)
    private Set<String> productIds = new LinkedHashSet<>();
}
