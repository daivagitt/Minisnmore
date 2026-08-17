package com.miniandmore.app.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * GST place-of-supply state codes for India. Shared by order sync integrations.
 */
final class IndiaPlaceOfSupplyCodes {

    private static final Map<String, String> BY_NORMALIZED_NAME = buildCodes();

    private IndiaPlaceOfSupplyCodes() {}

    static String lookup(String normalizedStateName) {
        return BY_NORMALIZED_NAME.get(normalizedStateName);
    }

    static boolean containsValue(String code) {
        return BY_NORMALIZED_NAME.containsValue(code);
    }

    private static Map<String, String> buildCodes() {
        Map<String, String> codes = new HashMap<>();
        codes.put("andamanandnicobarislands", "AN");
        codes.put("andhrapradesh", "AP");
        codes.put("arunachalpradesh", "AR");
        codes.put("assam", "AS");
        codes.put("bihar", "BR");
        codes.put("chandigarh", "CH");
        codes.put("chhattisgarh", "CT");
        codes.put("dadraandnagarhaveliaanddamananddiu", "DN");
        codes.put("dadraandnagarhaveli", "DN");
        codes.put("damananddiu", "DD");
        codes.put("delhi", "DL");
        codes.put("newdelhi", "DL");
        codes.put("goa", "GA");
        codes.put("gujarat", "GJ");
        codes.put("haryana", "HR");
        codes.put("himachalpradesh", "HP");
        codes.put("jammuandkashmir", "JK");
        codes.put("jharkhand", "JH");
        codes.put("karnataka", "KA");
        codes.put("kerala", "KL");
        codes.put("ladakh", "LA");
        codes.put("lakshadweep", "LD");
        codes.put("madhyapradesh", "MP");
        codes.put("maharashtra", "MH");
        codes.put("manipur", "MN");
        codes.put("meghalaya", "ML");
        codes.put("mizoram", "MZ");
        codes.put("nagaland", "NL");
        codes.put("odisha", "OR");
        codes.put("orissa", "OR");
        codes.put("puducherry", "PY");
        codes.put("pondicherry", "PY");
        codes.put("punjab", "PB");
        codes.put("rajasthan", "RJ");
        codes.put("sikkim", "SK");
        codes.put("tamilnadu", "TN");
        codes.put("telangana", "TG");
        codes.put("tripura", "TR");
        codes.put("uttarpradesh", "UP");
        codes.put("uttarakhand", "UT");
        codes.put("uttaranchal", "UT");
        codes.put("westbengal", "WB");
        return Collections.unmodifiableMap(codes);
    }
}
