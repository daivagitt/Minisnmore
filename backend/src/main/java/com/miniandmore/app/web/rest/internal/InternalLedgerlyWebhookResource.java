package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.service.LedgerlyWebhookHandlerService;
import com.miniandmore.app.web.rest.v1.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/ledgerly")
public class InternalLedgerlyWebhookResource {

    private final LedgerlyWebhookHandlerService webhookHandlerService;

    public InternalLedgerlyWebhookResource(LedgerlyWebhookHandlerService webhookHandlerService) {
        this.webhookHandlerService = webhookHandlerService;
    }

    @PostMapping("/webhooks")
    public ApiResponse<Map<String, Object>> receiveWebhook(
        @RequestBody String rawBody,
        @RequestHeader(value = "X-Ledgerly-Signature", required = false) String signature,
        @RequestHeader(value = "X-Ledgerly-Event", required = false) String event
    ) {
        return ApiResponse.ok(webhookHandlerService.handle(rawBody, signature, event));
    }
}
