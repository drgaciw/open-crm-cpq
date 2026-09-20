package com.opencrm.order.controller;
import com.opencrm.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class OrderControllerTest {
 @Test void publishedFulfillmentPlanRouteAndLegacyAliasReturnWrappedPlan() throws Exception {
  var service=mock(OrderService.class);var mvc=MockMvcBuilders.standaloneSetup(new OrderController(service)).build();
  mvc.perform(get("/api/v1/orders/order-id/fulfillment-plan")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
  mvc.perform(get("/api/v1/orders/order-id/plan")).andExpect(status().isOk());
 }
}
