package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.service.XpressBeesShipmentWebhookHandlerService;
import com.miniandmore.app.service.ZohoWebhookSupport;
import com.miniandmore.app.web.rest.v1.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({ "/internal/v1/webhooks/xpressbees", "/api/v1/webhooks/xpressbees" })
public class InternalXpressBeesShipmentWebhookResource {

    private final XpressBeesShipmentWebhookHandlerService handler;

    public InternalXpressBeesShipmentWebhookResource(XpressBeesShipmentWebhookHandlerService handler) {
        this.handler = handler;
    }

    @PostMapping("/shipments")
    public ApiResponse<Map<String, Object>> receive(
        @RequestBody String rawBody,
        @RequestHeader(value = "X-Hmac-SHA256", required = false) String hmacSha256,
        @RequestHeader(value = "X-XpressBees-Signature", required = false) String signature,
        @RequestHeader(value = "X-XpressBees-Webhook-Signature", required = false) String alternateSignature,
        @RequestHeader(value = "X-XpressBees-Secret", required = false) String sharedSecret
    ) {
        return ApiResponse.ok(
            handler.handle(rawBody, ZohoWebhookSupport.firstNonBlank(hmacSha256, signature, alternateSignature), sharedSecret)
        );
    }
}
