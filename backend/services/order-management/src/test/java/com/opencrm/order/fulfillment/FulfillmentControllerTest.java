package com.opencrm.order.fulfillment;
import com.opencrm.order.domain.OrderProblem;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class FulfillmentControllerTest {
 static final String BODY="{\"expectedVersion\":0,\"operationKey\":\"evidence\",\"reason\":\"verified\",\"lineId\":\"line\",\"kind\":\"SERVICE\",\"quantity\":1,\"externalReference\":\"external\",\"occurredAt\":\"2026-09-20T00:00:00Z\"}";
 @Test void durableContradictionIsAcceptedForReconciliation() throws Exception {var service=mock(EvidenceService.class);when(service.record(eq("order"),any())).thenReturn(new EvidenceReceipt("order","evidence","CONFLICT",1));var mvc=MockMvcBuilders.standaloneSetup(new FulfillmentController(service)).build();mvc.perform(post("/api/v1/orders/order/fulfillment-evidence").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("CONFLICT"));}
 @Test void rejectedOperationBodyRemainsConflict() throws Exception {var service=mock(EvidenceService.class);when(service.record(eq("order"),any())).thenThrow(OrderProblem.conflict("Operation key body conflict"));var mvc=MockMvcBuilders.standaloneSetup(new FulfillmentController(service)).build();mvc.perform(post("/api/v1/orders/order/fulfillment-evidence").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false));}
}
