package com.opencrm.pricing.openl;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pricing/openl/tables")
@RequiredArgsConstructor
public class OpenLTableAdminController {

    private final OpenLTableImportService openLTableImportService;

    @PostMapping("/validate")
    public ResponseEntity<OpenLTableValidationResult> validateTable(@Valid @RequestBody OpenLTableImportRequest request) {
        return ResponseEntity.ok(openLTableImportService.validateImport(request));
    }
}
