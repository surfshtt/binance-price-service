package by.fk8ght.task.price.repository;

import by.fk8ght.task.price.model.entity.PriceRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Repository
public interface PriceRepository extends JpaRepository<PriceRecord, Long> {

    boolean existsBySymbolAndCloseTime(String symbol, Instant closeTime);

    Optional<PriceRecord> findFirstBySymbolOrderByFetchedAtDescIdDesc(String symbol);

    Optional<PriceRecord> findFirstBySymbolAndFetchedAtBetweenOrderByFetchedAtAscIdAsc(String symbol, Instant from, Instant to);

    Optional<PriceRecord> findFirstBySymbolAndFetchedAtBetweenOrderByFetchedAtDescIdDesc(String symbol, Instant from, Instant to);

    Page<PriceRecord> findBySymbolAndFetchedAtBetween(String symbol, Instant from, Instant to, Pageable pageable);

    //jpql конвертил в double, поэтому на чистом sql запрос
    @Query(value = """
        select avg(price)
        from prices
        where symbol = :symbol
          and fetched_at between :from and :to
    """, nativeQuery = true)
    BigDecimal findAveragePrice(String symbol, Instant from, Instant to);
}
