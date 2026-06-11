package com.miniandmore.gateway.aop.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tech.jhipster.config.JHipsterConstants;

class LoggingAspectTest {

    @Test
    void pointcuts_areCallable() {
        LoggingAspect aspect = new LoggingAspect(new MockEnvironment());
        aspect.springBeanPointcut();
        aspect.applicationPackagePointcut();
    }

    @Test
    void logAfterThrowing_inDevProfile_logsWithExceptionMessage() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT);
        LoggingAspect aspect = new LoggingAspect(env);
        JoinPoint joinPoint = mockJoinPoint("com.miniandmore.gateway.service.UserService", "save");

        aspect.logAfterThrowing(joinPoint, new RuntimeException("boom"));
    }

    @Test
    void logAfterThrowing_inDevProfile_nullCause() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT);
        LoggingAspect aspect = new LoggingAspect(env);
        JoinPoint joinPoint = mockJoinPoint("com.miniandmore.gateway.service.UserService", "save");

        aspect.logAfterThrowing(joinPoint, new RuntimeException("no-cause"));
    }

    @Test
    void logAfterThrowing_inProdProfile_logsWithoutStackTrace() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(JHipsterConstants.SPRING_PROFILE_PRODUCTION);
        LoggingAspect aspect = new LoggingAspect(env);
        JoinPoint joinPoint = mockJoinPoint("com.miniandmore.gateway.service.UserService", "save");

        aspect.logAfterThrowing(joinPoint, new RuntimeException("boom", new IllegalStateException("cause")));
    }

    @Test
    void logAround_proceedsAndReturnsResult() throws Throwable {
        LoggingAspect aspect = new LoggingAspect(new MockEnvironment());
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringTypeName()).thenReturn("com.miniandmore.gateway.service.UserService");
        when(signature.getName()).thenReturn("findAll");
        when(joinPoint.getArgs()).thenReturn(new Object[] {});
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.logAround(joinPoint);

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void logAround_illegalArgumentExceptionIsRethrown() throws Throwable {
        LoggingAspect aspect = new LoggingAspect(new MockEnvironment());
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringTypeName()).thenReturn("com.miniandmore.gateway.service.UserService");
        when(signature.getName()).thenReturn("save");
        when(joinPoint.getArgs()).thenReturn(new Object[] { "bad" });
        when(joinPoint.proceed()).thenThrow(new IllegalArgumentException("invalid"));

        assertThatThrownBy(() -> aspect.logAround(joinPoint)).isInstanceOf(IllegalArgumentException.class);
    }

    private static JoinPoint mockJoinPoint(String typeName, String methodName) {
        JoinPoint joinPoint = mock(JoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringTypeName()).thenReturn(typeName);
        when(signature.getName()).thenReturn(methodName);
        return joinPoint;
    }
}
