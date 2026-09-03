package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.service.ZohoShipmentWebhookHandlerService;
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
public class InternalZohoShipmentWebhookResource {

    private final ZohoShipmentWebhookHandlerService webhookHandlerService;

    public InternalZohoShipmentWebhookResource(ZohoShipmentWebhookHandlerService webhookHandlerService) {
        this.webhookHandlerService = webhookHandlerService;
    }

    @PostMapping("/shipment-webhook")
    public ApiResponse<Map<String, Object>> receiveShipmentWebhook(
        @RequestBody String rawBody,
        @RequestHeader(value = "X-Zoho-Shipment-Signature", required = false) String signature,
        @RequestHeader(value = "X-Zoho-Webhook-Signature", required = false) String genericSignature,
        @RequestHeader(value = "X-Zoho-Shipment-Secret", required = false) String sharedSecret
    ) {
        return ApiResponse.ok(webhookHandlerService.handle(rawBody, ZohoWebhookSupport.firstNonBlank(signature, genericSignature), sharedSecret));
    }
}
