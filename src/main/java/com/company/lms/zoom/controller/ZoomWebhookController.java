package com.company.lms.zoom.controller;

import com.company.lms.common.dto.ApiResponse;
import com.company.lms.zoom.service.ZoomService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/zoom")
@RequiredArgsConstructor
public class ZoomWebhookController {
    private final ZoomService zoomService;

    @PostMapping("/webhook")
    public ApiResponse<String> webhook(@RequestHeader(value = "x-zm-signature", required = false) String signature,
                                       @RequestHeader(value = "x-zm-request-timestamp", required = false) String timestamp,
                                       @RequestBody String payload,
                                       HttpServletRequest request) {
        zoomService.receiveWebhook(signature, timestamp, payload);
        return ApiResponse.ok("OK", request.getHeader("X-Request-Id"));
    }
}
