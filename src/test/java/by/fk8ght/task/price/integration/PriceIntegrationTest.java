package by.fk8ght.task.price.integration;

import by.fk8ght.task.price.model.entity.PriceRecord;
import by.fk8ght.task.price.repository.PriceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PriceIntegrationTest {
    private static final String SYMBOL = "BNBUSDT";
    private static final long BINANCE_CLOSE_TIME_MILLIS = 1_790_000_000_000L;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @RegisterExtension
    static WireMockExtension binance = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("binance.base-url", binance::baseUrl);
        registry.add("binance.poll-interval", () -> "PT1S");
    }

    @BeforeAll
    static void stubBinance() {
        binance.stubFor(get(urlEqualTo("/api/v3/avgPrice?symbol=" + SYMBOL)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"mins\":5,\"price\":\"623.45000000\",\"closeTime\":" + BINANCE_CLOSE_TIME_MILLIS + "}")));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PriceRepository repository;

    @BeforeEach
    void seedHistory() {
        saveIfAbsent("10", "2020-01-01T00:00:00Z", 1L);
        saveIfAbsent("20", "2020-01-01T00:01:00Z", 2L);
        saveIfAbsent("30", "2020-01-01T00:02:00Z", 3L);
    }

    private void saveIfAbsent(String price, String fetchedAt, long closeTimeMillis) {
        Instant closeTime = Instant.ofEpochMilli(closeTimeMillis);
        if (repository.existsBySymbolAndCloseTime(SYMBOL, closeTime)) {
            return;
        }
        PriceRecord record = new PriceRecord();
        record.setSymbol(SYMBOL);
        record.setPrice(new BigDecimal(price));
        record.setCloseTime(closeTime);
        record.setFetchedAt(Instant.parse(fetchedAt));
        repository.save(record);
    }

    @Test
    void averageOfPeriod() {
        JsonNode body = restTemplate.getForObject(
                "/api/v1/prices/average?from=2020-01-01T00:00:00Z&to=2020-01-01T00:02:00Z", JsonNode.class);

        assertThat(body.get("averagePrice").decimalValue()).isEqualByComparingTo("20");
    }

    @Test
    void averageIsLimitedByPeriod() {
        JsonNode body = restTemplate.getForObject(
                "/api/v1/prices/average?from=2020-01-01T00:01:00Z&to=2020-01-01T00:02:00Z", JsonNode.class);

        assertThat(body.get("averagePrice").decimalValue()).isEqualByComparingTo("25");
    }

    @Test
    void averageWithoutDataIsNotFound() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/prices/average?from=2019-01-01T00:00:00Z&to=2019-01-02T00:00:00Z", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void pricesArePaged() {
        String range = "from=2020-01-01T00:00:00Z&to=2020-01-01T00:02:00Z";

        JsonNode firstPage = restTemplate.getForObject("/api/v1/prices?" + range + "&page=0&size=2", JsonNode.class);
        assertThat(firstPage.get("items")).hasSize(2);
        assertThat(firstPage.get("items").get(0).get("price").asText()).isEqualTo("10");
        assertThat(firstPage.get("items").get(1).get("price").asText()).isEqualTo("20");
        assertThat(firstPage.get("page").asInt()).isZero();
        assertThat(firstPage.get("size").asInt()).isEqualTo(2);
        assertThat(firstPage.get("totalElements").asLong()).isEqualTo(3);
        assertThat(firstPage.get("hasNext").asBoolean()).isTrue();

        JsonNode secondPage = restTemplate.getForObject("/api/v1/prices?" + range + "&page=1&size=2", JsonNode.class);
        assertThat(secondPage.get("items")).hasSize(1);
        assertThat(secondPage.get("items").get(0).get("price").asText()).isEqualTo("30");
        assertThat(secondPage.get("hasNext").asBoolean()).isFalse();
    }

    @Test
    void trendOfGrowingPricesIsUpward() {
        JsonNode body = restTemplate.getForObject(
                "/api/v1/prices/trend?from=2020-01-01T00:00:00Z&to=2020-01-01T00:02:00Z", JsonNode.class);

        assertThat(body.get("symbol").asText()).isEqualTo(SYMBOL);
        assertThat(body.get("start_date").asText()).isEqualTo("2020-01-01T00:00:00Z");
        assertThat(body.get("end_date").asText()).isEqualTo("2020-01-01T00:02:00Z");
        assertThat(body.get("trend").asText()).isEqualTo("UPWARD");
    }

    @Test
    void trendOfSinglePriceIsFlat() {
        JsonNode body = restTemplate.getForObject(
                "/api/v1/prices/trend?from=2020-01-01T00:00:00Z&to=2020-01-01T00:00:30Z", JsonNode.class);

        assertThat(body.get("trend").asText()).isEqualTo("FLAT");
    }

    @Test
    void invalidIntervalIsBadRequest() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/prices/trend?from=2020-02-01T00:00:00Z&to=2020-01-01T00:00:00Z", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
