package by.fk8ght.task.price.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties(prefix = "trend")
public record TrendProperties(
        @NotNull
        @DecimalMin("0")
        BigDecimal flatThresholdPercent
) {
}
