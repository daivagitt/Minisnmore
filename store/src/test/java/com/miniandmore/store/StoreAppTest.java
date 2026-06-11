package com.miniandmore.store;

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class StoreAppTest {

    @Test
    void main_invokesSpringApplicationRun() {
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            spring.when(() -> SpringApplication.run(StoreApp.class, new String[] {})).thenReturn(null);
            StoreApp.main(new String[] {});
            spring.verify(() -> SpringApplication.run(StoreApp.class, new String[] {}));
        }
    }
}
