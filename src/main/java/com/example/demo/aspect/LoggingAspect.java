package com.example.demo.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Logs every public service method: entry arguments, exit value, elapsed time, and exceptions.
 *
 * WHY @Around INSTEAD OF @Before + @AfterReturning + @AfterThrowing?
 *   @Around is a single join point that wraps the entire method call.
 *   It gives us one place to measure elapsed time (start before → stop after)
 *   and to handle both the success and exception paths without duplicating the
 *   method-name resolution logic.
 *
 * WHY POINTCUT ON com.example.demo.service?
 *   Controllers handle HTTP concerns; repositories are thin wrappers over SQL.
 *   Services hold business logic — the layer where latency and errors matter most.
 *   Logging here gives a clean picture of what the application is actually doing
 *   without drowning the output in framework noise.
 */
@Slf4j
@Aspect
@Component
public class LoggingAspect {

    /**
     * Intercepts every public method in every class under com.example.demo.service.
     *
     * ProceedingJoinPoint.proceed() calls the real method.
     * Everything before proceed() is "before" advice; everything after is "after".
     * We wrap proceed() in try/catch to also intercept thrown exceptions.
     */
    @Around("execution(public * com.example.demo.service..*(..))")
    public Object logServiceCall(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        String className  = sig.getDeclaringType().getSimpleName();
        String methodName = sig.getName();
        Object[] args     = joinPoint.getArgs();

        log.debug("[{}#{}] called with args: {}", className, methodName, Arrays.toString(args));

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long elapsed  = System.currentTimeMillis() - start;

            log.debug("[{}#{}] returned {} in {} ms",
                    className, methodName, result, elapsed);

            return result;
        } catch (Throwable ex) {
            long elapsed = System.currentTimeMillis() - start;

            log.error("[{}#{}] threw {} after {} ms — message: {}",
                    className, methodName,
                    ex.getClass().getSimpleName(), elapsed,
                    ex.getMessage());

            throw ex;
        }
    }
}
