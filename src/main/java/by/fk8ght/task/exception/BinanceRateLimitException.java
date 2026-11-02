package by.fk8ght.task.exception;

import lombok.Getter;

import java.time.Duration;

@Getter
public class BinanceRateLimitException extends BinanceApiException {
    private final Duration retryAfter;

    public BinanceRateLimitException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }
}
