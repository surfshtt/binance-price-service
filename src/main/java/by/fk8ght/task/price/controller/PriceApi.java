package by.fk8ght.task.price.controller;

import by.fk8ght.task.price.model.dto.PriceAverageDTO;
import by.fk8ght.task.price.model.dto.PriceDTO;
import by.fk8ght.task.price.model.dto.PricePageDTO;
import by.fk8ght.task.price.model.dto.PriceTrendDTO;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;

@RequestMapping("/api/v1/prices")
public interface PriceApi {
    @GetMapping("/latest")
    ResponseEntity<PriceDTO> getLatest();

    @GetMapping("/average")
    ResponseEntity<PriceAverageDTO> getAverage(@RequestParam("from") Instant from, @RequestParam("to") Instant to);

    @GetMapping
    ResponseEntity<PricePageDTO> getPrices(@RequestParam("from") Instant from, @RequestParam("to") Instant to,
                                           @PageableDefault(size = 20, sort = {"fetchedAt", "id"}) Pageable pageable);

    @GetMapping("/trend")
    ResponseEntity<PriceTrendDTO> getTrend(@RequestParam("from") Instant from, @RequestParam("to") Instant to);
}
