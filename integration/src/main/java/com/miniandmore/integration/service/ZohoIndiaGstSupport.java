package com.miniandmore.integration.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/** Maps Indian state names to Zoho Books {@code place_of_supply} / {@code state_code} values. */
final class ZohoIndiaGstSupport {

    private static final Map<String, String> STATE_CODES = Map.ofEntries(
        Map.entry("andaman and nicobar islands", "AN"),
        Map.entry("andhra pradesh", "AD"),
        Map.entry("arunachal pradesh", "AR"),
        Map.entry("assam", "AS"),
        Map.entry("bihar", "BR"),
        Map.entry("chandigarh", "CH"),
        Map.entry("chhattisgarh", "CG"),
        Map.entry("chattisgarh", "CG"),
        Map.entry("dadra and nagar haveli and daman and diu", "DN"),
        Map.entry("delhi", "DL"),
        Map.entry("goa", "GA"),
        Map.entry("gujarat", "GJ"),
        Map.entry("haryana", "HR"),
        Map.entry("himachal pradesh", "HP"),
        Map.entry("jammu and kashmir", "JK"),
        Map.entry("jharkhand", "JH"),
        Map.entry("karnataka", "KA"),
        Map.entry("kerala", "KL"),
        Map.entry("ladakh", "LA"),
        Map.entry("lakshadweep", "LD"),
        Map.entry("madhya pradesh", "MP"),
        Map.entry("maharashtra", "MH"),
        Map.entry("manipur", "MN"),
        Map.entry("meghalaya", "ML"),
        Map.entry("mizoram", "MZ"),
        Map.entry("nagaland", "NL"),
        Map.entry("odisha", "OD"),
        Map.entry("orissa", "OD"),
        Map.entry("pondicherry", "PY"),
        Map.entry("puducherry", "PY"),
        Map.entry("punjab", "PB"),
        Map.entry("rajasthan", "RJ"),
        Map.entry("sikkim", "SK"),
        Map.entry("tamil nadu", "TN"),
        Map.entry("telangana", "TS"),
        Map.entry("tripura", "TR"),
        Map.entry("uttar pradesh", "UP"),
        Map.entry("uttarakhand", "UK"),
        Map.entry("west bengal", "WB")
    );

    private static final Set<String> VALID_STATE_CODES = Set.copyOf(STATE_CODES.values());

    private static final List<String> STATE_NAMES_BY_LENGTH = sortedStateNames();

    private ZohoIndiaGstSupport() {}

    static String placeOfSupplyFromState(String state) {
        if (StringUtils.isBlank(state)) {
            return null;
        }
        String normalized = state.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() == 2 && normalized.chars().allMatch(Character::isLetter)) {
            String code = normalized.toUpperCase(Locale.ROOT);
            if (VALID_STATE_CODES.contains(code)) {
                return code;
            }
        }
        String direct = STATE_CODES.get(normalized);
        if (direct != null) {
            return direct;
        }
        for (String key : STATE_NAMES_BY_LENGTH) {
            if (normalized.contains(key)) {
                return STATE_CODES.get(key);
            }
            if (normalized.length() >= 4 && key.contains(normalized)) {
                return STATE_CODES.get(key);
            }
        }
        return null;
    }

    static boolean isIndiaDataCenter(String dataCenter) {
        return "in".equalsIgnoreCase(StringUtils.trimToEmpty(dataCenter));
    }

    private static List<String> sortedStateNames() {
        List<String> keys = new ArrayList<>(STATE_CODES.keySet());
        keys.sort(Comparator.comparingInt(String::length).reversed());
        return List.copyOf(keys);
    }
}
