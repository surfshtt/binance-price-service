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
import by.fk8ght.task.price.service.PriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@RequiredArgsConstructor
@Service
public class PriceServiceImpl implements PriceService {

    static final int AVERAGE_SCALE = 8;

    private final PriceRepository repository;
    private final PriceMapper mapper;
    private final BinanceProperties binanceProperties;
    private final TrendProperties trendProperties;

    @Transactional(readOnly = true)
    @Override
    public PriceDTO getLatest() {
        PriceRecord record = repository.findFirstBySymbolOrderByFetchedAtDescIdDesc(binanceProperties.symbol())
                .orElseThrow(() -> new NotFoundException("No prices saved yer"));

        return mapper.toDto(record);
    }

    @Transactional(readOnly = true)
    @Override
    public PriceAverageDTO getAverage(Instant from, Instant to) {
        validateInterval(from, to);

        BigDecimal average = repository.findAveragePrice(binanceProperties.symbol(), from, to);
        if (average == null) {
            throw new NotFoundException("No prices in the given interval");
        }

        return new PriceAverageDTO(average.setScale(AVERAGE_SCALE, RoundingMode.HALF_UP));
    }

    @Transactional(readOnly = true)
    @Override
    public PricePageDTO getPrices(Instant from, Instant to, Pageable pageable) {
        validateInterval(from, to);

        PageRequest pageRequest = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("fetchedAt", "id"));
        Page<PriceRecord> records = repository.findBySymbolAndFetchedAtBetween(binanceProperties.symbol(), from, to, pageRequest);

        return new PricePageDTO(
                records.getContent().stream().map(mapper::toDto).toList(),
                records.getNumber(),
                records.getSize(),
                records.getTotalElements(),
                records.hasNext()
        );
    }

    @Transactional(readOnly = true)
    @Override
    public PriceTrendDTO getTrend(Instant from, Instant to) {
        validateInterval(from, to);

        String symbol = binanceProperties.symbol();
        PriceRecord first = repository.findFirstBySymbolAndFetchedAtBetweenOrderByFetchedAtAscIdAsc(symbol, from, to)
                .orElseThrow(() -> new NotFoundException("No prices in the given interval"));
        PriceRecord last = repository.findFirstBySymbolAndFetchedAtBetweenOrderByFetchedAtDescIdDesc(symbol, from, to)
                .orElseThrow(() -> new NotFoundException("No prices in the given interval"));

        TrendType type = null;
        BigDecimal change = last.getPrice().subtract(first.getPrice());
        BigDecimal tolerance = first.getPrice().multiply(trendProperties.flatThresholdPercent()).movePointLeft(2);

        if (change.compareTo(tolerance) > 0) {
            type = TrendType.UPWARD;
        } else if (change.compareTo(tolerance.negate()) < 0) {
            type = TrendType.DOWNWARD;
        } else {
            type = TrendType.FLAT;
        }

        return new PriceTrendDTO(symbol, from, to, type);
    }

    private void validateInterval(Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new BadRequestException("incorrect date");
        }
    }
}
