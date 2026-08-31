package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.service.ZohoStockWebhookHandlerService;
import com.miniandmore.app.web.rest.v1.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({ "/internal/v1/zoho", "/api/v1/zoho" })
public class InternalZohoStockWebhookResource {

    private final ZohoStockWebhookHandlerService webhookHandlerService;

    public InternalZohoStockWebhookResource(ZohoStockWebhookHandlerService webhookHandlerService) {
        this.webhookHandlerService = webhookHandlerService;
    }

    @PostMapping("/stock-webhook")
    public ApiResponse<Map<String, Object>> receiveStockWebhook(
        @RequestBody String rawBody,
        @RequestHeader(value = "X-Zoho-Stock-Signature", required = false) String signature
    ) {
        return ApiResponse.ok(webhookHandlerService.handle(rawBody, signature));
    }
}
