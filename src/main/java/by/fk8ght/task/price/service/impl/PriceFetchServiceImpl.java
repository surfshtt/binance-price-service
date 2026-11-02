package by.fk8ght.task.price.service.impl;

import by.fk8ght.task.exception.BinanceApiException;
import by.fk8ght.task.exception.BinanceRateLimitException;
import by.fk8ght.task.price.client.BinanceClient;
import by.fk8ght.task.price.config.BinanceProperties;
import by.fk8ght.task.price.model.dto.BinanceAvgPriceDTO;
import by.fk8ght.task.price.model.entity.PriceRecord;
import by.fk8ght.task.price.repository.PriceRepository;
import by.fk8ght.task.price.service.PriceFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
@Service
public class PriceFetchServiceImpl implements PriceFetchService {
    private final BinanceClient binanceClient;
    private final PriceRepository repository;
    private final BinanceProperties properties;
    private final Clock clock;

    private volatile Instant pausedUntil;

    @Override
    public void fetchAndSave() {
        Instant now = clock.instant();

        if (pausedUntil != null && now.isBefore(pausedUntil)) {
            log.warn("Request skipped. Rate limit pause: {}", pausedUntil);
            return;
        }

        String symbol = properties.symbol();
        BinanceAvgPriceDTO response;
        try {
            response = binanceClient.getAvgPrice(symbol);
        } catch (BinanceRateLimitException e) {
            if (e.getRetryAfter() != null) {
                pausedUntil = now.plus(e.getRetryAfter());
            }
            log.warn("{}. Pause until {}", e.getMessage(), pausedUntil);
            return;
        } catch (BinanceApiException e) {
            log.error("Some Binance api problem: {}", e.getMessage());
            return;
        }

        Instant closeTime = Instant.ofEpochMilli(response.getCloseTime());

        if (repository.existsBySymbolAndCloseTime(symbol, closeTime)) {
            log.debug("Snapshot is already saved");
            return;
        }

        PriceRecord record = new PriceRecord();
        record.setSymbol(symbol);
        record.setPrice(response.getPrice());
        record.setCloseTime(closeTime);
        record.setFetchedAt(now);

        repository.save(record);
        log.info("Saved price {}", response.getPrice().toPlainString());
    }
}
