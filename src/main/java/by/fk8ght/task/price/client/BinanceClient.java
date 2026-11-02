package by.fk8ght.task.price.client;

import by.fk8ght.task.exception.BinanceApiException;
import by.fk8ght.task.exception.BinanceRateLimitException;
import by.fk8ght.task.price.model.dto.BinanceAvgPriceDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class BinanceClient {
    private static final String AVG_PRICE_PATH = "/api/v3/avgPrice";
    private static final String RETRY_AFTER_HEADER = "Retry-After";

    private final RestClient binanceRestClient;

    public BinanceAvgPriceDTO getAvgPrice(String symbol) {
        BinanceAvgPriceDTO response;
        try {
            response = binanceRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path(AVG_PRICE_PATH).queryParam("symbol", symbol).build())
                    .retrieve()
                    .body(BinanceAvgPriceDTO.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == HttpStatus.TOO_MANY_REQUESTS.value() || status == 418) {
                if(e.getResponseHeaders() == null) {
                    throw new BinanceRateLimitException("Binance rate limit hit for " + symbol + ", status " + status, null);
                } else {
                    throw new BinanceRateLimitException("Binance rate limit hit for " + symbol + ", status " + status, parseRetryAfter(e.getResponseHeaders().getFirst(RETRY_AFTER_HEADER)));
                }
            }

            throw new BinanceApiException("Binance returned status " + status + " for " + symbol + ": " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new BinanceApiException("Binance request for " + symbol + " failed: " + e.getMessage(), e);
        }

        validate(symbol, response);
        return response;
    }

    private Duration parseRetryAfter(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        try {
            long seconds = Long.parseLong(headerValue.trim());
            if(seconds >= 0) {
                return Duration.ofSeconds(seconds);
            } else{
                return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void validate(String symbol, BinanceAvgPriceDTO response) {
        if (response == null
                || response.getPrice() == null
                || response.getPrice().signum() <= 0
                || response.getCloseTime() == null) {
            throw new BinanceApiException("Binance returned unexpected body for " + symbol + ": " + response);
        }
    }
}
