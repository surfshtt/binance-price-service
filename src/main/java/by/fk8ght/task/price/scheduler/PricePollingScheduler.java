package by.fk8ght.task.price.scheduler;

import by.fk8ght.task.price.service.PriceFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class PricePollingScheduler {
    private final PriceFetchService fetchService;

    @Scheduled(fixedDelayString = "${binance.poll-interval}")
    public void poll() {
        try {
            fetchService.fetchAndSave();
        } catch (Exception e) {
            log.error("Price polling failed", e);
        }
    }
}
