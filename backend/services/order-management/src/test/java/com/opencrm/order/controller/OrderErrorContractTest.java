package com.opencrm.order.controller;
import com.opencrm.order.domain.OrderProblem;
import com.opencrm.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class OrderErrorContractTest {
 @Test void semanticErrorsHaveStablePublicCodes() throws Exception {
  var orders=mock(OrderService.class);when(orders.get("missing")).thenThrow(OrderProblem.missing());
  MockMvcBuilders.standaloneSetup(new OrderController(orders)).build().perform(get("/api/v1/orders/missing"))
   .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("OMS_404"));
 }
}
