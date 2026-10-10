package com.opencrm.contract.controller;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.contract.dto.RampScheduleDto;
import com.opencrm.contract.service.RampScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class RampScheduleController {
    private final RampScheduleService schedules;
    @GetMapping({"/api/v1/subscriptions/by-number/{number}/ramp-schedule",
            "/internal/v1/subscriptions/by-number/{number}/ramp-schedule"})
    public ApiResponse<RampScheduleDto> byNumber(@PathVariable String number,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ApiResponse.success(schedules.byNumber(number, asOf));
    }
    @GetMapping("/api/v1/subscriptions/{id}/ramp-schedule")
    public ApiResponse<RampScheduleDto> byId(@PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ApiResponse.success(schedules.byId(id, asOf));
    }
}
