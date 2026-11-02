package by.fk8ght.task.price.controller.impl;

import by.fk8ght.task.price.controller.PriceApi;
import by.fk8ght.task.price.model.dto.PriceAverageDTO;
import by.fk8ght.task.price.model.dto.PriceDTO;
import by.fk8ght.task.price.model.dto.PricePageDTO;
import by.fk8ght.task.price.model.dto.PriceTrendDTO;
import by.fk8ght.task.price.service.PriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RequiredArgsConstructor
@RestController
public class PriceController implements PriceApi {
    private final PriceService priceService;

    @Override
    public ResponseEntity<PriceDTO> getLatest() {
        return ResponseEntity.ok(priceService.getLatest());
    }

    @Override
    public ResponseEntity<PriceAverageDTO> getAverage(Instant from, Instant to) {
        return ResponseEntity.ok(priceService.getAverage(from, to));
    }

    @Override
    public ResponseEntity<PricePageDTO> getPrices(Instant from, Instant to, Pageable pageable) {
        return ResponseEntity.ok(priceService.getPrices(from, to, pageable));
    }

    @Override
    public ResponseEntity<PriceTrendDTO> getTrend(Instant from, Instant to) {
        return ResponseEntity.ok(priceService.getTrend(from, to));
    }
}
