package com.opencrm.ai.profile;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SellingProfileServiceTest {
    SellingProfileRepository repository=mock(SellingProfileRepository.class);
    SafeWebsiteClient websites=mock(SafeWebsiteClient.class);
    SellingProfileService service=new SellingProfileService(repository,websites);
    static SellingProfile.Fields fields() { return new SellingProfile.Fields("Company","Product","Value","ICP","Competition","No discounts","Concise"); }
    @Test void missingTenantProfileIsEmptyWithoutFallback() {
        when(repository.find("a")).thenReturn(Optional.empty());
        assertTrue(service.get("a").isEmpty()); verify(repository).find("a"); verifyNoMoreInteractions(repository);
    }
    @Test void updatesUseTenantAndVerifiedActor() {
        service.put("tenant-a",fields(),"actor"); verify(repository).save("tenant-a",fields(),"actor");
        service.put("tenant-b",fields(),"other"); verify(repository).save("tenant-b",fields(),"other");
    }
    @Test void invalidTenantOrActorNeverAccessesRepository() {
        assertThrows(IllegalArgumentException.class,()->service.get(" "));
        assertThrows(IllegalArgumentException.class,()->service.put("a",fields(),null));
        assertThrows(IllegalArgumentException.class,()->service.ingest(null,"https://example.com","a"));
        verifyNoInteractions(repository,websites);
    }
    @Test void ingestStoresSanitizedSummaryWithShaNotRawHtml() {
        when(websites.fetch("https://example.com")).thenReturn("hello");
        service.ingest("tenant","https://example.com","actor");
        verify(repository).saveSummary("tenant","https://example.com","hello",
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824","actor");
    }
    @Test void rejectedFetchDoesNotOverwriteExistingProfile() {
        when(websites.fetch(anyString())).thenThrow(new IllegalArgumentException());
        assertThrows(IllegalArgumentException.class,()->service.ingest("a","https://127.0.0.1","actor"));
        verifyNoInteractions(repository);
    }
    @Test void normalizesNullFieldsAndEnforcesBound() {
        var f=new SellingProfile.Fields(null," x ",null,null,null,null,null);
        assertEquals("",f.companyOverview()); assertEquals("x",f.productOverview());
        assertThrows(IllegalArgumentException.class,()->new SellingProfile.Fields("x".repeat(4001),null,null,null,null,null,null));
    }
}
