package com.miniandmore.app.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * FastSMS India / RITS SMS API v1.0 auth signature.
 *
 * <pre>
 * TimeKey            = MD5(RequestFor + "sms@rits-v1.0" + RequestExpireTime)
 * TimeAccessTokenKey = MD5(ACCESS_TOKEN + TimeKey)
 * SIGNATURE          = MD5(TimeAccessTokenKey + ACCESS_TOKEN_KEY)
 * </pre>
 */
public final class FastSmsSignature {

    public static final String SALT = "sms@rits-v1.0";
    public static final String REQUEST_SEND_SMS = "send-sms";
    public static final String REQUEST_SEND_SMS_ARRAY = "send-sms-array";
    public static final String REQUEST_SEND_DYNAMIC_SMS = "send-dynamic-sms";

    private FastSmsSignature() {}

    public static String generate(String accessToken, String accessTokenKey, String requestFor, long expireEpochSeconds) {
        String timeKey = md5(requestFor + SALT + expireEpochSeconds);
        String timeAccessTokenKey = md5(accessToken + timeKey);
        return md5(timeAccessTokenKey + accessTokenKey);
    }

    static String md5(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 not available", ex);
        }
    }
}
