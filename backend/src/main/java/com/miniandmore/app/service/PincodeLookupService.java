package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.XpressBeesClient;
import com.miniandmore.app.service.dto.PincodeLookupDTO;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class PincodeLookupService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final String USER_AGENT = "MiniSnMore/1.0 (+https://minisnmore.com)";
    private static final List<String> METRO_NAMES = List.of(
        "Mumbai",
        "Delhi",
        "Bengaluru",
        "Bangalore",
        "Chennai",
        "Kolkata",
        "Hyderabad",
        "Pune"
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final XpressBeesClient xpressBeesClient;

    public PincodeLookupService(ObjectMapper objectMapper, XpressBeesClient xpressBeesClient) {
        this.objectMapper = objectMapper;
        this.xpressBeesClient = xpressBeesClient;
        this.httpClient = HttpClient
            .newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    }

    public Optional<PincodeLookupDTO> lookup(String pincode) {
        if (pincode == null || !pincode.matches("^[1-9]\\d{5}$")) {
            return Optional.empty();
        }
        if (xpressBeesClient.isConfigured()) {
            Optional<PincodeLookupDTO> serviceable = lookupXpressBees(pincode);
            if (serviceable.isPresent()) {
                return serviceable;
            }
            return Optional.empty();
        }
        // India Post has broader coverage for Indian pincodes; Zippopotam is the fallback.
        return lookupIndiaPost(pincode).or(() -> lookupZippopotam(pincode));
    }

    private Optional<PincodeLookupDTO> lookupXpressBees(String pincode) {
        return xpressBeesClient
            .checkServiceability(pincode)
            .map(result -> new PincodeLookupDTO(result.city(), result.state(), result.eta()));
    }

    private Optional<PincodeLookupDTO> lookupZippopotam(String pincode) {
        try {
            var request = HttpRequest
                .newBuilder()
                .uri(URI.create("https://api.zippopotam.us/in/" + pincode))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            return parseZippopotamResponse(response.body(), pincode);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<PincodeLookupDTO> lookupIndiaPost(String pincode) {
        try {
            var request = HttpRequest
                .newBuilder()
                .uri(URI.create("https://api.postalpincode.in/pincode/" + pincode))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            return parseIndiaPostResponse(response.body(), pincode);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    Optional<PincodeLookupDTO> parseZippopotamResponse(String body, String pincode) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode places = root.path("places");
            if (!places.isArray() || places.isEmpty()) {
                return Optional.empty();
            }
            JsonNode first = places.get(0);
            String city = text(first, "place name");
            String state = text(first, "state");
            if (city.isBlank() || state.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new PincodeLookupDTO(city, state, deliveryEta(pincode, state, city)));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    Optional<PincodeLookupDTO> parseIndiaPostResponse(String body, String pincode) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.isArray() || root.isEmpty()) {
                return Optional.empty();
            }
            JsonNode first = root.get(0);
            if (!"Success".equalsIgnoreCase(text(first, "Status"))) {
                return Optional.empty();
            }
            JsonNode offices = first.path("PostOffice");
            if (!offices.isArray() || offices.isEmpty()) {
                return Optional.empty();
            }
            JsonNode office = offices.get(0);
            String city = text(office, "District");
            String state = text(office, "State");
            if (city.isBlank() || state.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new PincodeLookupDTO(city, state, deliveryEta(pincode, state, city)));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    static String deliveryEta(String pincode, String state, String city) {
        String haystack = (city + " " + state).toLowerCase();
        if (METRO_NAMES.stream().anyMatch(m -> haystack.contains(m.toLowerCase()))) {
            return "2–3 days";
        }
        if (matchesMetroPinPrefix(pincode)) {
            return "2–3 days";
        }
        return "4–6 days";
    }

    private static boolean matchesMetroPinPrefix(String pincode) {
        return (
            pincode.startsWith("560") ||
            pincode.startsWith("561") ||
            pincode.startsWith("400") ||
            pincode.startsWith("401") ||
            pincode.startsWith("110") ||
            pincode.startsWith("122") ||
            pincode.startsWith("600") ||
            pincode.startsWith("601") ||
            pincode.startsWith("700") ||
            pincode.startsWith("711") ||
            pincode.startsWith("500") ||
            pincode.startsWith("501") ||
            pincode.startsWith("411") ||
            pincode.startsWith("412")
        );
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }
}
