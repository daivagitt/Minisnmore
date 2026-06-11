package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miniandmore.integration.config.ApplicationProperties;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class S3StorageServiceGapsTest {

    private ApplicationProperties applicationProperties;
    private S3StorageService service;

    @BeforeEach
    void setUp() {
        applicationProperties = new ApplicationProperties();
        ApplicationProperties.Storage.S3 s3 = applicationProperties.getStorage().getS3();
        s3.setEndpoint("https://account.r2.cloudflarestorage.com/my-bucket");
        s3.setBucket("my-bucket");
        s3.setAccessKey("key");
        s3.setSecretKey("secret");
        s3.setRegion("auto");
        s3.setPublicBaseUrl("https://pub.example.com");
        service = new S3StorageService(applicationProperties);
    }

    @Test
    void resolveEndpoint_stripsBucketSuffix() throws Exception {
        String endpoint = (String) invoke("resolveEndpoint", "my-bucket");
        assertThat(endpoint).isEqualTo("https://account.r2.cloudflarestorage.com");
    }

    @Test
    void resolveContentType_coversExtensionsAndOctetStream() throws Exception {
        assertThat(invoke("resolveContentType", null, "photo.gif")).isEqualTo("image/gif");
        assertThat(invoke("resolveContentType", null, "file.bin")).isEqualTo("application/octet-stream");
        assertThat(invoke("resolveContentType", "image/jpg", "photo.jpg")).isEqualTo("image/jpeg");
        assertThat(invoke("resolveContentType", "image/x-png", "photo.png")).isEqualTo("image/png");
        assertThat(invoke("resolveContentType", "image/pjpeg", "photo.jpeg")).isEqualTo("image/jpeg");
        assertThat(invoke("resolveContentType", "text/plain", "notes.txt")).isEqualTo("application/octet-stream");
    }

    @Test
    void effectivePublicBaseUrl_rejectsCloudflareApiHost() {
        applicationProperties.getStorage().getS3().setPublicBaseUrl("https://bucket.cloudflarestorage.com");
        assertThatThrownBy(() -> invoke("effectivePublicBaseUrl"))
            .cause()
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void lazyClientAndPresigner_initializeOnce() {
        ReflectionTestUtils.setField(service, "s3Client", null);
        ReflectionTestUtils.setField(service, "s3Presigner", null);
        Object client = ReflectionTestUtils.invokeMethod(service, "client");
        Object presigner = ReflectionTestUtils.invokeMethod(service, "presigner");
        assertThat(client).isNotNull();
        assertThat(presigner).isNotNull();
    }

    private Object invoke(String name, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i] == null ? String.class : args[i].getClass();
        }
        Method method = S3StorageService.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(service, args);
    }
}
