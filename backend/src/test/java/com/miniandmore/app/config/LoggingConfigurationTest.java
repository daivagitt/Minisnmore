package com.miniandmore.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import tech.jhipster.config.JHipsterProperties;
import tools.jackson.databind.ObjectMapper;

class LoggingConfigurationTest {

    @Test
    void constructor_jsonFormatDisabled_doesNotThrow() throws Exception {
        JHipsterProperties properties = new JHipsterProperties();
        properties.getLogging().setUseJsonFormat(false);
        properties.getLogging().getLogstash().setEnabled(false);
        ObjectMapper mapper = new ObjectMapper();

        new LoggingConfiguration("miniandmore", "8080", properties, emptyProvider(), emptyProvider(), mapper);
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return null;
            }

            @Override
            public T getObject(Object... args) {
                return null;
            }

            @Override
            public T getIfAvailable() {
                return null;
            }

            @Override
            public T getIfUnique() {
                return null;
            }

            @Override
            public java.util.Iterator<T> iterator() {
                return java.util.Collections.emptyIterator();
            }
        };
    }
}
