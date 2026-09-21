package com.opencrm.order.advisory;

/** Optional observation of committed OMS work. It has no business-command capability. */
@FunctionalInterface
public interface OrderAdvisoryTrigger {
    void afterCommit(String orderId);
    OrderAdvisoryTrigger NONE = orderId -> { };

    /** Even a broken optional adapter must not affect the enclosing transaction. */
    static void notifySafely(OrderAdvisoryTrigger trigger, String orderId) {
        try { trigger.afterCommit(orderId); }
        catch (Throwable ignored) { /* Business state and delivery always remain authoritative. */ }
    }
}
