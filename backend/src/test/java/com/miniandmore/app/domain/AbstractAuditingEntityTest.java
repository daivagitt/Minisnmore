package com.miniandmore.app.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AbstractAuditingEntityTest {

    @Test
    void auditingGettersAndSetters() {
        TestAuditingEntity entity = new TestAuditingEntity();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        entity.setCreatedBy("admin");
        entity.setCreatedDate(now);
        entity.setLastModifiedBy("editor");
        entity.setLastModifiedDate(now);

        assertThat(entity.getCreatedBy()).isEqualTo("admin");
        assertThat(entity.getCreatedDate()).isEqualTo(now);
        assertThat(entity.getLastModifiedBy()).isEqualTo("editor");
        assertThat(entity.getLastModifiedDate()).isEqualTo(now);
        assertThat(entity.getId()).isEqualTo(1L);
    }

    private static final class TestAuditingEntity extends AbstractAuditingEntity<Long> {

        @Override
        public Long getId() {
            return 1L;
        }
    }
}
