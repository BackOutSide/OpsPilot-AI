package org.example.service.resilience;

import org.example.config.ToolResilienceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 统一容错执行器
 * 为只读查询型依赖提供有限重试、简单熔断和降级回退能力
 */
@Service
public class ResilienceExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ResilienceExecutor.class);

    private final Map<String, CircuitState> circuitStates = new ConcurrentHashMap<>();

    public <T> T execute(String dependencyName,
                         ToolResilienceProperties.Dependency dependency,
                         CheckedSupplier<T> action,
                         Function<Exception, T> fallback) {
        if (dependency == null || !dependency.isEnabled()) {
            try {
                return action.get();
            } catch (Exception e) {
                return fallback.apply(e);
            }
        }

        /*
        *判断是否熔断
        */
        CircuitState state = circuitStates.computeIfAbsent(dependencyName, ignored -> new CircuitState());
        long now = System.currentTimeMillis();
        if (state.isOpen(now)) {
            CircuitBreakerOpenException ex = new CircuitBreakerOpenException(
                    "Circuit breaker is open for dependency: " + dependencyName
            );
            logger.warn("依赖已熔断，直接走降级: dependency={}, reopenAt={}", dependencyName, state.openUntilMs);
            return fallback.apply(ex);
        }

        /*
        *重试循环
        */
        int maxAttempts = Math.max(1, dependency.getMaxAttempts());
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T result = action.get();
                state.reset();
                return result;
            } catch (Exception e) {
                lastException = e;
                int failures = state.recordFailureAndReturnCount();
                logger.warn("依赖调用失败: dependency={}, attempt={}/{}, failureCount={}, message={}",
                        dependencyName, attempt, maxAttempts, failures, e.getMessage());

                if (failures >= dependency.getFailureThreshold()) {
                    state.openUntilMs = now + dependency.getOpenDurationMs();
                    logger.warn("依赖触发熔断: dependency={}, openDurationMs={}",
                            dependencyName, dependency.getOpenDurationMs());
                }

                if (attempt >= maxAttempts || !isRetryable(e)) {
                    break;
                }

                sleep(backoffDelayMs(dependency, attempt));
            }
        }

        return fallback.apply(lastException == null
                ? new RuntimeException("Unknown failure for dependency: " + dependencyName)
                : lastException);
    }

    private boolean isRetryable(Exception e) {
        return !(e instanceof IllegalArgumentException);
    }

    private long backoffDelayMs(ToolResilienceProperties.Dependency dependency, int attempt) {
        long delay = dependency.getInitialBackoffMs() * (1L << Math.max(0, attempt - 1));
        return Math.min(delay, dependency.getMaxBackoffMs());
    }

    private void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry backoff interrupted", e);
        }
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private static final class CircuitState {
        private int consecutiveFailures;
        private long openUntilMs;

        private boolean isOpen(long now) {
            return openUntilMs > now;
        }

        private int recordFailureAndReturnCount() {
            consecutiveFailures++;
            return consecutiveFailures;
        }

        private void reset() {
            consecutiveFailures = 0;
            openUntilMs = 0L;
        }
    }
}
