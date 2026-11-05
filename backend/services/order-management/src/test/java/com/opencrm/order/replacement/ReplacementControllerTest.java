package com.opencrm.order.replacement;

import com.opencrm.order.domain.OrderProblem;
import com.opencrm.order.dto.OrderDtos.Command;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReplacementControllerTest {
 @Test void durableRequestReturns202WithReceipt() throws Exception {
  var service=mock(ReplacementService.class);var command=new Command(0,"correction","key");
  when(service.request("order",command)).thenReturn(new ReplacementService.Receipt("key","order","event",1,"SUCCEEDED"));
  var mvc=MockMvcBuilders.standaloneSetup(new ReplacementController(service)).build();
  mvc.perform(post("/api/v1/orders/order/replacement-requests").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"reason\":\"correction\",\"operationKey\":\"key\"}"))
   .andExpect(status().isAccepted()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.eventId").value("event")).andExpect(jsonPath("$.data.orderVersion").value(1));
 }
 @Test void invalidInputNeverCallsServiceAndConflictRetainsApiEnvelope() throws Exception {
  var service=mock(ReplacementService.class);var mvc=MockMvcBuilders.standaloneSetup(new ReplacementController(service)).build();
  mvc.perform(post("/api/v1/orders/order/replacement-requests").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":-1,\"reason\":\"\",\"operationKey\":\"\"}"))
   .andExpect(status().isBadRequest());verifyNoInteractions(service);
  when(service.request(anyString(),any())).thenThrow(OrderProblem.conflict("Order version changed"));
  mvc.perform(post("/api/v1/orders/order/replacement-requests").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"reason\":\"correction\",\"operationKey\":\"key\"}"))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false));
 }
}
