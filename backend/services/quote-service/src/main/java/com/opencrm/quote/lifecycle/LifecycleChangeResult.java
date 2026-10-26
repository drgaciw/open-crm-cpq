package com.opencrm.quote.lifecycle;

/** Body plus whether this call returned a previously stored change. */
public record LifecycleChangeResult(LifecycleChangeResponse body, boolean replayed) {

    public static LifecycleChangeResult created(LifecycleChangeResponse body) {
        return new LifecycleChangeResult(body, false);
    }

    public static LifecycleChangeResult replay(LifecycleChangeResponse body) {
        return new LifecycleChangeResult(body, true);
    }
}
