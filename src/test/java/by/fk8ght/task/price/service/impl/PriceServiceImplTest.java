package by.fk8ght.task.price.service.impl;

import by.fk8ght.task.exception.BadRequestException;
import by.fk8ght.task.exception.NotFoundException;
import by.fk8ght.task.price.config.BinanceProperties;
import by.fk8ght.task.price.config.TrendProperties;
import by.fk8ght.task.price.model.dto.PriceAverageDTO;
import by.fk8ght.task.price.model.dto.PriceDTO;
import by.fk8ght.task.price.model.dto.PricePageDTO;
import by.fk8ght.task.price.model.dto.PriceTrendDTO;
import by.fk8ght.task.price.model.dto.TrendType;
import by.fk8ght.task.price.model.entity.PriceRecord;
import by.fk8ght.task.price.model.mapper.PriceMapper;
import by.fk8ght.task.price.repository.PriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceServiceImplTest {
    private static final String SYMBOL = "BNBUSDT";
    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-30T00:00:00Z");

    @Mock
    private PriceRepository repository;

    @Mock
    private PriceMapper mapper;

    private PriceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = createService(BigDecimal.ZERO);
    }

    private PriceServiceImpl createService(BigDecimal flatThresholdPercent) {
        BinanceProperties binanceProperties = new BinanceProperties(
                "http://localhost", SYMBOL, Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(10));
        return new PriceServiceImpl(repository, mapper, binanceProperties, new TrendProperties(flatThresholdPercent));
    }

    private PriceRecord record(String price) {
        PriceRecord record = new PriceRecord();
        record.setSymbol(SYMBOL);
        record.setPrice(new BigDecimal(price));
        return record;
    }

    @Test
    void getLatestTest() {
        PriceRecord record = record("10.5");
        PriceDTO dto = new PriceDTO(SYMBOL, new BigDecimal("10.5"), Instant.parse("2026-09-01T10:00:00Z"));

        when(repository.findFirstBySymbolOrderByFetchedAtDescIdDesc(SYMBOL)).thenReturn(Optional.of(record));
        when(mapper.toDto(record)).thenReturn(dto);

        PriceDTO result = service.getLatest();

        assertEquals(dto, result);
    }

    @Test
    void getLatestNotFoundTest() {
        when(repository.findFirstBySymbolOrderByFetchedAtDescIdDesc(SYMBOL)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getLatest());
    }

    @Test
    void getAverageTest() {
        when(repository.findAveragePrice(SYMBOL, FROM, TO)).thenReturn(new BigDecimal("10.2"));

        PriceAverageDTO result = service.getAverage(FROM, TO);

        assertEquals(new BigDecimal("10.20000000"), result.getAveragePrice());
    }

    @Test
    void getAverageRoundsToEightDecimalsTest() {
        when(repository.findAveragePrice(SYMBOL, FROM, TO)).thenReturn(new BigDecimal("10.123456785"));

        PriceAverageDTO result = service.getAverage(FROM, TO);

        assertEquals(new BigDecimal("10.12345679"), result.getAveragePrice());
    }

    @Test
    void getAverageNoDataTest() {
        when(repository.findAveragePrice(SYMBOL, FROM, TO)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> service.getAverage(FROM, TO));
    }

    @Test
    void getAverageFromAfterToTest() {
        assertThrows(BadRequestException.class, () -> service.getAverage(TO, FROM));
        verifyNoInteractions(repository);
    }

    @Test
    void getPricesTest() {
        PriceRecord record = record("10");
        PriceDTO dto = new PriceDTO(SYMBOL, new BigDecimal("10"), FROM);

        when(repository.findBySymbolAndFetchedAtBetween(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(record), PageRequest.of(0, 1), 3));
        when(mapper.toDto(record)).thenReturn(dto);

        PricePageDTO result = service.getPrices(FROM, TO, PageRequest.of(0, 1));

        assertEquals(List.of(dto), result.getItems());
        assertEquals(0, result.getPage());
        assertEquals(1, result.getSize());
        assertEquals(3, result.getTotalElements());
        assertTrue(result.isHasNext());
    }

    @Test
    void getPricesLastPageHasNoNextTest() {
        PriceRecord record = record("10");

        when(repository.findBySymbolAndFetchedAtBetween(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(record), PageRequest.of(2, 1), 3));
        when(mapper.toDto(record)).thenReturn(new PriceDTO(SYMBOL, new BigDecimal("10"), FROM));

        PricePageDTO result = service.getPrices(FROM, TO, PageRequest.of( 2, 1));

        assertFalse(result.isHasNext());
    }

    @Test
    void getPricesSortsByFetchedAtAndIdTest() {
        when(repository.findBySymbolAndFetchedAtBetween(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.getPrices(FROM, TO, PageRequest.of(1, 20));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findBySymbolAndFetchedAtBetween(any(), any(), any(), captor.capture());
        Pageable pageable = captor.getValue();
        assertEquals(1, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
        assertTrue(pageable.getSort().getOrderFor("fetchedAt").isAscending());
        assertTrue(pageable.getSort().getOrderFor("id").isAscending());
    }

    @Test
    void getPricesFromAfterToTest() {
        assertThrows(BadRequestException.class, () -> service.getPrices(TO, FROM, PageRequest.of(0, 20)));
    }

    private void mockFirstAndLast(String firstPrice, String lastPrice) {
        when(repository.findFirstBySymbolAndFetchedAtBetweenOrderByFetchedAtAscIdAsc(SYMBOL, FROM, TO))
                .thenReturn(Optional.of(record(firstPrice)));
        when(repository.findFirstBySymbolAndFetchedAtBetweenOrderByFetchedAtDescIdDesc(SYMBOL, FROM, TO))
                .thenReturn(Optional.of(record(lastPrice)));
    }

    @Test
    void getTrendUpwardTest() {
        mockFirstAndLast("100", "101");

        PriceTrendDTO result = service.getTrend(FROM, TO);

        assertEquals(TrendType.UPWARD, result.getTrend());
        assertEquals(SYMBOL, result.getSymbol());
        assertEquals(FROM, result.getStartDate());
        assertEquals(TO, result.getEndDate());
    }

    @Test
    void getTrendDownwardTest() {
        mockFirstAndLast("100", "99.99999999");

        assertEquals(TrendType.DOWNWARD, service.getTrend(FROM, TO).getTrend());
    }

    @Test
    void getTrendFlatWhenPricesEqualTest() {
        mockFirstAndLast("100.00", "100");

        assertEquals(TrendType.FLAT, service.getTrend(FROM, TO).getTrend());
    }

    @Test
    void getTrendFlatWithinThresholdTest() {
        service = createService(new BigDecimal("1"));
        mockFirstAndLast("100", "101");

        assertEquals(TrendType.FLAT, service.getTrend(FROM, TO).getTrend());
    }

    @Test
    void getTrendUpwardAboveThresholdTest() {
        service = createService(new BigDecimal("1"));
        mockFirstAndLast("100", "101.01");

        assertEquals(TrendType.UPWARD, service.getTrend(FROM, TO).getTrend());
    }

    @Test
    void getTrendDownwardBelowThresholdTest() {
        service = createService(new BigDecimal("1"));
        mockFirstAndLast("100", "98.99");

        assertEquals(TrendType.DOWNWARD, service.getTrend(FROM, TO).getTrend());
    }

    @Test
    void getTrendNoDataTest() {
        when(repository.findFirstBySymbolAndFetchedAtBetweenOrderByFetchedAtAscIdAsc(SYMBOL, FROM, TO))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getTrend(FROM, TO));
    }

    @Test
    void getTrendFromAfterToTest() {
        assertThrows(BadRequestException.class, () -> service.getTrend(TO, FROM));
    }
}
