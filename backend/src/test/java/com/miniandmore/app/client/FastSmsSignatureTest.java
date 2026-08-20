package com.miniandmore.app.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FastSmsSignatureTest {

    @Test
    void generate_matchesPhpReferenceAlgorithm() {
        String accessToken = "64L1DG6C1IUWZEA";
        String accessTokenKey = "Co8I7VvSXA[3h9d,k_)|Jf0FrgsKu+p;";
        long expire = 1_724_837_000L;
        String requestFor = FastSmsSignature.REQUEST_SEND_SMS;

        String timeKey = FastSmsSignature.md5(requestFor + FastSmsSignature.SALT + expire);
        String timeAccessTokenKey = FastSmsSignature.md5(accessToken + timeKey);
        String expected = FastSmsSignature.md5(timeAccessTokenKey + accessTokenKey);

        assertThat(FastSmsSignature.generate(accessToken, accessTokenKey, requestFor, expire)).isEqualTo(expected);
        assertThat(expected).hasSize(32).matches("^[a-f0-9]{32}$");
    }
}
