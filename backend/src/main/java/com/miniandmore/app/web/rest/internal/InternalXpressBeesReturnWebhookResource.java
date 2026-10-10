package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.service.XpressBeesReturnWebhookHandlerService;
import com.miniandmore.app.service.ZohoWebhookSupport;
import com.miniandmore.app.web.rest.v1.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({ "/internal/v1/webhooks/xpressbees", "/api/v1/webhooks/xpressbees" })
public class InternalXpressBeesReturnWebhookResource {

    private final XpressBeesReturnWebhookHandlerService webhookHandlerService;

    public InternalXpressBeesReturnWebhookResource(XpressBeesReturnWebhookHandlerService webhookHandlerService) {
        this.webhookHandlerService = webhookHandlerService;
    }

    @PostMapping("/returns")
    public ApiResponse<Map<String, Object>> receiveReturnWebhook(
        @RequestBody String rawBody,
        @RequestHeader(value = "X-Hmac-SHA256", required = false) String hmacSha256,
        @RequestHeader(value = "X-XpressBees-Signature", required = false) String signature,
        @RequestHeader(value = "X-XpressBees-Webhook-Signature", required = false) String altSignature,
        @RequestHeader(value = "X-XpressBees-Secret", required = false) String sharedSecret
    ) {
        return ApiResponse.ok(
            webhookHandlerService.handle(
                rawBody,
                ZohoWebhookSupport.firstNonBlank(hmacSha256, signature, altSignature),
                sharedSecret
            )
        );
    }
}
