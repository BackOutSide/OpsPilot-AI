package org.example.service.resilience;

/**
 * 熔断器打开时抛出的异常。
 */
public class CircuitBreakerOpenException extends RuntimeException {
    public CircuitBreakerOpenException(String message) {
        super(message);
    }
}
