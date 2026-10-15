package com.opencrm.gateway.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Same-origin SPA bootstrap for the gateway's CSRF cookie. The security chain's
 * {@code issueCsrfCookie} filter issues the token; this endpoint exposes no session or identity data.
 */
@RestController
public class CsrfController {

    @GetMapping("/api/v1/csrf")
    public ResponseEntity<Void> bootstrap() {
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
