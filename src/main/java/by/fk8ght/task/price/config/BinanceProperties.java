package by.fk8ght.task.price.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "binance")
public record BinanceProperties(
        @NotBlank String baseUrl,
        @NotBlank String symbol,
        @NotNull Duration pollInterval,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {
}
