package com.miniandmore.gateway.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.validation.annotation.Validated;

/** Shared, per-client fixed windows. All policies are independently configurable. */
@Validated
public class RateLimitingProperties {

    private boolean enabled = true;
    private boolean failOpen = false;
    @NotBlank
    private String keyPrefix = "gateway:rate-limit:";
    private List<String> trustedProxyAddresses = List.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1", "::ffff:127.0.0.1");
    @Valid
    private final Policy general = new Policy(600, 60);
    @Valid
    private final Policy auth = new Policy(5, 60);
    @Valid
    private final Policy orders = new Policy(5, 60);
    @Valid
    private final Policy payment = new Policy(30, 60);
    @Valid
    private final Policy webhook = new Policy(300, 60);
    @Valid
    private final Policy admin = new Policy(120, 60);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isFailOpen() { return failOpen; }
    public void setFailOpen(boolean failOpen) { this.failOpen = failOpen; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    public List<String> getTrustedProxyAddresses() { return trustedProxyAddresses; }
    public void setTrustedProxyAddresses(List<String> addresses) { this.trustedProxyAddresses = List.copyOf(addresses); }
    public Policy getGeneral() { return general; }
    public Policy getAuth() { return auth; }
    public Policy getOrders() { return orders; }
    public Policy getPayment() { return payment; }
    public Policy getWebhook() { return webhook; }
    public Policy getAdmin() { return admin; }

    public static class Policy {
        @Min(1)
        private int requests;
        @Min(1)
        private int windowSeconds;

        public Policy(int requests, int windowSeconds) {
            this.requests = requests;
            this.windowSeconds = windowSeconds;
        }

        public int getRequests() { return requests; }
        public void setRequests(int requests) { this.requests = requests; }
        public int getWindowSeconds() { return windowSeconds; }
        public void setWindowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; }
    }
}
