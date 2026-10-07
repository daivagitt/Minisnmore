package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.service.RazorpayWebhookHandlerService;
import com.miniandmore.app.web.rest.v1.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({ "/internal/v1/webhooks/razorpay", "/api/v1/webhooks/razorpay" })
public class InternalRazorpayWebhookResource {

    private final RazorpayWebhookHandlerService handler;

    public InternalRazorpayWebhookResource(RazorpayWebhookHandlerService handler) {
        this.handler = handler;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> receive(
        @RequestBody String rawBody,
        @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature
    ) {
        return ApiResponse.ok(handler.handle(rawBody, signature));
    }
}
