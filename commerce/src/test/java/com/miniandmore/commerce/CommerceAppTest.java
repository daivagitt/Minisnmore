package com.miniandmore.commerce;

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class CommerceAppTest {

    @Test
    void main_invokesSpringApplicationRun() {
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            spring.when(() -> SpringApplication.run(CommerceApp.class, new String[] {})).thenReturn(null);
            CommerceApp.main(new String[] {});
            spring.verify(() -> SpringApplication.run(CommerceApp.class, new String[] {}));
        }
    }
}
