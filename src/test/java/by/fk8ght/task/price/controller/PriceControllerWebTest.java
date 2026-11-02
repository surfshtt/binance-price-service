package by.fk8ght.task.price.controller;

import by.fk8ght.task.exception.BadRequestException;
import by.fk8ght.task.exception.NotFoundException;
import by.fk8ght.task.price.controller.impl.PriceController;
import by.fk8ght.task.price.model.dto.PriceAverageDTO;
import by.fk8ght.task.price.model.dto.PriceDTO;
import by.fk8ght.task.price.model.dto.PricePageDTO;
import by.fk8ght.task.price.model.dto.PriceTrendDTO;
import by.fk8ght.task.price.model.dto.TrendType;
import by.fk8ght.task.price.service.PriceService;
import by.fk8ght.task.exception.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PriceController.class)
@Import(GlobalExceptionHandler.class)
class PriceControllerWebTest {
    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-30T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PriceService priceService;

    @Test
    void getLatestTest() throws Exception {
        when(priceService.getLatest()).thenReturn(
                new PriceDTO("BNBUSDT", new BigDecimal("623.45000000"), Instant.parse("2026-09-01T10:15:30.123456Z")));

        mockMvc.perform(get("/api/v1/prices/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BNBUSDT"))
                .andExpect(jsonPath("$.price").value("623.45000000"))
                .andExpect(jsonPath("$.fetchedAt").value("2026-09-01T10:15:30Z"));
    }

    @Test
    void getLatestNotFoundTest() throws Exception {
        when(priceService.getLatest()).thenThrow(new NotFoundException("No prices have been saved yet"));

        mockMvc.perform(get("/api/v1/prices/latest"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("No prices have been saved yet"))
                .andExpect(jsonPath("$.path").value("/api/v1/prices/latest"));
    }

    @Test
    void getAverageTest() throws Exception {
        when(priceService.getAverage(FROM, TO)).thenReturn(new PriceAverageDTO(new BigDecimal("10.2")));

        mockMvc.perform(get("/api/v1/prices/average")
                        .param("from", "2026-09-01T00:00:00Z")
                        .param("to", "2026-09-30T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averagePrice").value(10.2));
    }

    @Test
    void getAverageMissingParameterTest() throws Exception {
        mockMvc.perform(get("/api/v1/prices/average").param("from", "2026-09-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required parameter 'to' is missing"));

        verifyNoInteractions(priceService);
    }

    @Test
    void getAverageInvalidDateTest() throws Exception {
        mockMvc.perform(get("/api/v1/prices/average")
                        .param("from", "yesterday")
                        .param("to", "2026-09-30T00:00:00Z"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(priceService);
    }

    @Test
    void getAverageBadIntervalTest() throws Exception {
        when(priceService.getAverage(TO, FROM)).thenThrow(new BadRequestException("'from' must not be after 'to'"));

        mockMvc.perform(get("/api/v1/prices/average")
                        .param("from", "2026-09-30T00:00:00Z")
                        .param("to", "2026-09-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'from' must not be after 'to'"));
    }

    @Test
    void getPricesTest() throws Exception {
        PricePageDTO page = new PricePageDTO(
                List.of(new PriceDTO("BNBUSDT", new BigDecimal("623.45"), Instant.parse("2026-09-01T10:15:30Z"))),
                0, 20, 135, true);
        Pageable pageable = PageRequest.of(0, 20, Sort.by("fetchedAt", "id"));
        when(priceService.getPrices(FROM, TO, pageable)).thenReturn(page);

        mockMvc.perform(get("/api/v1/prices")
                        .param("from", "2026-09-01T00:00:00Z")
                        .param("to", "2026-09-30T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].symbol").value("BNBUSDT"))
                .andExpect(jsonPath("$.items[0].price").value("623.45"))
                .andExpect(jsonPath("$.items[0].fetchedAt").value("2026-09-01T10:15:30Z"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(135))
                .andExpect(jsonPath("$.hasNext").value(true));
    }


    @Test
    void getTrendTest() throws Exception {
        when(priceService.getTrend(FROM, TO)).thenReturn(new PriceTrendDTO("BNBUSDT", FROM, TO, TrendType.UPWARD));

        mockMvc.perform(get("/api/v1/prices/trend")
                        .param("from", "2026-09-01T00:00:00Z")
                        .param("to", "2026-09-30T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BNBUSDT"))
                .andExpect(jsonPath("$.start_date").value("2026-09-01T00:00:00Z"))
                .andExpect(jsonPath("$.end_date").value("2026-09-30T00:00:00Z"))
                .andExpect(jsonPath("$.trend").value("UPWARD"));
    }
}
