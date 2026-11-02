package by.fk8ght.task.price.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BinanceAvgPriceDTO {
    private Integer mins;
    private BigDecimal price;
    private Long closeTime;
}
