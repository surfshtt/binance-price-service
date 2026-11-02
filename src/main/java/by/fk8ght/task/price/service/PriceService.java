package by.fk8ght.task.price.service;

import by.fk8ght.task.price.model.dto.PriceAverageDTO;
import by.fk8ght.task.price.model.dto.PriceDTO;
import by.fk8ght.task.price.model.dto.PricePageDTO;
import by.fk8ght.task.price.model.dto.PriceTrendDTO;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface PriceService {
    PriceDTO getLatest();
    PriceAverageDTO getAverage(Instant from, Instant to);
    PricePageDTO getPrices(Instant from, Instant to, Pageable pageable);
    PriceTrendDTO getTrend(Instant from, Instant to);
}
