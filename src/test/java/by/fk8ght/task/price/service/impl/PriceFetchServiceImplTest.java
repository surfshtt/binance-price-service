package by.fk8ght.task.price.service.impl;

import by.fk8ght.task.exception.BinanceApiException;
import by.fk8ght.task.exception.BinanceRateLimitException;
import by.fk8ght.task.price.client.BinanceClient;
import by.fk8ght.task.price.config.BinanceProperties;
import by.fk8ght.task.price.model.dto.BinanceAvgPriceDTO;
import by.fk8ght.task.price.model.entity.PriceRecord;
import by.fk8ght.task.price.repository.PriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceFetchServiceImplTest {
    private static final String SYMBOL = "BNBUSDT";
    private static final long CLOSE_TIME_MILLIS = 1_790_000_000_000L;
    private static final Instant CLOSE_TIME = Instant.ofEpochMilli(CLOSE_TIME_MILLIS);
    private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");

    @Mock
    private BinanceClient binanceClient;

    @Mock
    private PriceRepository repository;

    @Mock
    private Clock clock;

    private PriceFetchServiceImpl service;

    @BeforeEach
    void setUp() {
        BinanceProperties properties = new BinanceProperties(
                "http://localhost", SYMBOL, Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(10));
        service = new PriceFetchServiceImpl(binanceClient, repository, properties, clock);
        when(clock.instant()).thenReturn(NOW);
    }

    private BinanceAvgPriceDTO response(String price) {
        BinanceAvgPriceDTO dto = new BinanceAvgPriceDTO();
        dto.setMins(5);
        dto.setPrice(new BigDecimal(price));
        dto.setCloseTime(CLOSE_TIME_MILLIS);
        return dto;
    }

    @Test
    void fetchAndSaveTest() {
        when(binanceClient.getAvgPrice(SYMBOL)).thenReturn(response("623.45000000"));
        when(repository.existsBySymbolAndCloseTime(SYMBOL, CLOSE_TIME)).thenReturn(false);

        service.fetchAndSave();

        ArgumentCaptor<PriceRecord> captor = ArgumentCaptor.forClass(PriceRecord.class);
        verify(repository).save(captor.capture());
        PriceRecord saved = captor.getValue();
        assertEquals(SYMBOL, saved.getSymbol());
        assertEquals(new BigDecimal("623.45000000"), saved.getPrice());
        assertEquals(CLOSE_TIME, saved.getCloseTime());
        assertEquals(NOW, saved.getFetchedAt());
    }

    @Test
    void fetchAndSaveSkipsAlreadySavedSnapshotTest() {
        when(binanceClient.getAvgPrice(SYMBOL)).thenReturn(response("623.45"));
        when(repository.existsBySymbolAndCloseTime(SYMBOL, CLOSE_TIME)).thenReturn(true);

        service.fetchAndSave();

        verify(repository, never()).save(any());
    }

    @Test
    void fetchAndSaveBinanceErrorDoesNotThrowTest() {
        when(binanceClient.getAvgPrice(SYMBOL)).thenThrow(new BinanceApiException("boom"));

        assertDoesNotThrow(() -> service.fetchAndSave());

        verify(repository, never()).save(any());
    }

    @Test
    void fetchAndSaveRecoversAfterErrorTest() {
        when(binanceClient.getAvgPrice(SYMBOL))
                .thenThrow(new BinanceApiException("boom"))
                .thenReturn(response("623.45"));
        when(repository.existsBySymbolAndCloseTime(SYMBOL, CLOSE_TIME)).thenReturn(false);

        service.fetchAndSave();
        service.fetchAndSave();

        verify(repository, times(1)).save(any(PriceRecord.class));
    }

    @Test
    void fetchAndSaveRateLimitPausesRequestsTest() {
        when(binanceClient.getAvgPrice(SYMBOL))
                .thenThrow(new BinanceRateLimitException("limit", Duration.ofSeconds(60)));

        service.fetchAndSave();
        when(clock.instant()).thenReturn(NOW.plusSeconds(10));
        service.fetchAndSave();

        verify(binanceClient, times(1)).getAvgPrice(SYMBOL);
    }

    @Test
    void fetchAndSaveResumesAfterRateLimitPauseTest() {
        when(binanceClient.getAvgPrice(SYMBOL))
                .thenThrow(new BinanceRateLimitException("limit", Duration.ofSeconds(60)))
                .thenReturn(response("623.45"));
        when(repository.existsBySymbolAndCloseTime(SYMBOL, CLOSE_TIME)).thenReturn(false);

        service.fetchAndSave();
        when(clock.instant()).thenReturn(NOW.plusSeconds(61));
        service.fetchAndSave();

        verify(binanceClient, times(2)).getAvgPrice(SYMBOL);
        verify(repository).save(any(PriceRecord.class));
    }

    @Test
    void fetchAndSaveRateLimitWithoutRetryAfterDoesNotPauseTest() {
        when(binanceClient.getAvgPrice(SYMBOL))
                .thenThrow(new BinanceRateLimitException("limit", null))
                .thenReturn(response("623.45"));
        when(repository.existsBySymbolAndCloseTime(SYMBOL, CLOSE_TIME)).thenReturn(false);

        service.fetchAndSave();
        service.fetchAndSave();

        verify(binanceClient, times(2)).getAvgPrice(SYMBOL);
    }
}
