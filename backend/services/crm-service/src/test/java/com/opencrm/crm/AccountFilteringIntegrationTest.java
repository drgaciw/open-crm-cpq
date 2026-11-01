package com.opencrm.crm;

import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealRepository;
import com.opencrm.crm.deal.DealStage;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountFilteringIntegrationTest extends BaseIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository organizations;
    @Autowired DealRepository deals;

    @Test
    void filtersAccountsBeforePaginationForListAndSearch() throws Exception {
        organizations.save(Organization.builder().name("Acme A").industry("Finance").territory("West").ownerId("ada").build());
        organizations.save(Organization.builder().name("Acme B").industry("Technology").territory("West Coast").ownerId("Ada Smith").build());
        organizations.save(Organization.builder().name("Acme C").industry("Technology").territory("west coast").ownerId("ada jones").build());
        for (String path : new String[]{"/api/v1/organizations", "/api/v1/organizations/search"}) {
            var request = get(path).param("page", "1").param("size", "1").param("sort", "name,asc")
                    .param("industry", "technology").param("territory", "WEST").param("ownerId", "ADA");
            if (path.endsWith("search")) request.param("q", "Acme");
            mvc.perform(request).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").value("Acme C"))
                    .andExpect(jsonPath("$.pagination.totalElements").value(2));
        }
    }

    @Test
    void filtersDealsByOrganizationAndStageBeforePagination() throws Exception {
        Organization one = organizations.save(Organization.builder().name("First").build());
        Organization two = organizations.save(Organization.builder().name("Second").build());
        deals.save(Deal.builder().name("A other").organization(two).stage(DealStage.PROSPECTING).build());
        deals.save(Deal.builder().name("B match").organization(one).stage(DealStage.PROSPECTING).build());
        deals.save(Deal.builder().name("C match").organization(one).stage(DealStage.PROSPECTING).build());
        mvc.perform(get("/api/v1/deals").param("organizationId", one.getId().toString())
                        .param("stage", "PROSPECTING").param("page", "1").param("size", "1").param("sort", "name,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].name").value("C match"))
                .andExpect(jsonPath("$.pagination.totalElements").value(2));
    }
}
