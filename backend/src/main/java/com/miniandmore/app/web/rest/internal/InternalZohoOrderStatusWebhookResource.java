package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.service.ZohoOrderStatusWebhookHandlerService;
import com.miniandmore.app.service.ZohoWebhookSupport;
import com.miniandmore.app.web.rest.v1.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({ "/internal/v1/zoho", "/api/v1/zoho" })
public class InternalZohoOrderStatusWebhookResource {

    private final ZohoOrderStatusWebhookHandlerService webhookHandlerService;

    public InternalZohoOrderStatusWebhookResource(ZohoOrderStatusWebhookHandlerService webhookHandlerService) {
        this.webhookHandlerService = webhookHandlerService;
    }

    @PostMapping("/order-status-webhook")
    public ApiResponse<Map<String, Object>> receiveOrderStatusWebhook(
        @RequestBody String rawBody,
        @RequestHeader(value = "X-Zoho-Order-Status-Signature", required = false) String signature,
        @RequestHeader(value = "X-Zoho-Webhook-Signature", required = false) String genericSignature,
        @RequestHeader(value = "X-Zoho-Order-Status-Secret", required = false) String sharedSecret
    ) {
        return ApiResponse.ok(webhookHandlerService.handle(rawBody, ZohoWebhookSupport.firstNonBlank(signature, genericSignature), sharedSecret));
    }
}
