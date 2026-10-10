package com.miniandmore.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LiquibaseMigrationRunnerTest {

    @Test
    void main_withoutDatasourceEnv_throwsIllegalStateException() {
        assertThatThrownBy(() -> LiquibaseMigrationRunner.main(new String[] {}))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SPRING_LIQUIBASE_URL or SPRING_DATASOURCE_URL");
    }

    @Test
    void firstNonBlank_prefersFirstNonBlankValue() {
        String first = ReflectionTestUtils.invokeMethod(LiquibaseMigrationRunner.class, "firstNonBlank", "  jdbc:url  ", "fallback");
        assertThat(first).isEqualTo("jdbc:url");
        String second = ReflectionTestUtils.invokeMethod(LiquibaseMigrationRunner.class, "firstNonBlank", null, "  second  ");
        assertThat(second).isEqualTo("second");
        String blank = ReflectionTestUtils.invokeMethod(LiquibaseMigrationRunner.class, "firstNonBlank", "   ", (Object) null);
        assertThat(blank).isNull();
    }

    @Test
    void requireSetting_blankValueThrowsIllegalStateException() {
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(LiquibaseMigrationRunner.class, "requireSetting", "  ", "TEST_VAR"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("TEST_VAR");
    }

}
