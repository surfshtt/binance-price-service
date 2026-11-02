package by.fk8ght.task.price.client;

import by.fk8ght.task.exception.BinanceApiException;
import by.fk8ght.task.exception.BinanceRateLimitException;
import by.fk8ght.task.price.config.AppConfig;
import by.fk8ght.task.price.config.BinanceProperties;
import by.fk8ght.task.price.model.dto.BinanceAvgPriceDTO;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class BinanceClientTest {
    private static final String URL = "/api/v3/avgPrice?symbol=BNBUSDT";

    private BinanceClient client;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        BinanceProperties properties = new BinanceProperties(
                wireMock.getHttpBaseUrl(), "BNBUSDT", Duration.ofSeconds(30), Duration.ofSeconds(1), Duration.ofMillis(500));
        client = new BinanceClient(new AppConfig().binanceRestClient(properties));
    }

    @Test
    void getAvgPriceSuccessTest() {
        stubFor(get(urlEqualTo(URL)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"mins":5,"price":"666.00000000","closeTime":1790000000000}
                        """)));

        BinanceAvgPriceDTO result = client.getAvgPrice("BNBUSDT");

        assertThat(result.getPrice()).isEqualTo(new BigDecimal("666.00000000"));
        assertThat(result.getCloseTime()).isEqualTo(1790000000000L);
        assertThat(result.getMins()).isEqualTo(5);
    }

    @Test
    void getAvgPriceInvalidSymbolTest() {
        stubFor(get(urlEqualTo(URL)).willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":-1121,\"msg\":\"Invalid symbol.\"}")));

        assertThatThrownBy(() -> client.getAvgPrice("BNBUSDT"))
                .isInstanceOf(BinanceApiException.class)
                .isNotInstanceOf(BinanceRateLimitException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("Invalid symbol");
    }

    @Test
    void getAvgPriceRateLimitWithRetryAfterTest() {
        stubFor(get(urlEqualTo(URL)).willReturn(aResponse()
                .withStatus(429)
                .withHeader("Retry-After", "30")));

        assertThatThrownBy(() -> client.getAvgPrice("BNBUSDT"))
                .isInstanceOfSatisfying(BinanceRateLimitException.class,
                        e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(30)));
    }

    @Test
    void getAvgPriceIpBanTest() {
        stubFor(get(urlEqualTo(URL)).willReturn(aResponse()
                .withStatus(418)
                .withHeader("Retry-After", "120")));

        assertThatThrownBy(() -> client.getAvgPrice("BNBUSDT"))
                .isInstanceOfSatisfying(BinanceRateLimitException.class,
                        e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(120)));
    }

    @Test
    void getAvgPriceRateLimitWithoutRetryAfterTest() {
        stubFor(get(urlEqualTo(URL)).willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> client.getAvgPrice("BNBUSDT"))
                .isInstanceOfSatisfying(BinanceRateLimitException.class,
                        e -> assertThat(e.getRetryAfter()).isNull());
    }

    @Test
    void getAvgPriceServerErrorTest() {
        stubFor(get(urlEqualTo(URL)).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.getAvgPrice("BNBUSDT"))
                .isInstanceOf(BinanceApiException.class)
                .hasMessageContaining("503");
    }

    @Test
    void getAvgPriceMalformedBodyTest() {
        stubFor(get(urlEqualTo(URL)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("gfhmjkdmblkmdklb")));

        assertThatThrownBy(() -> client.getAvgPrice("BNBUSDT"))
                .isInstanceOf(BinanceApiException.class);
    }
}
