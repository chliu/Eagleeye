package com.eagleeye.collector.service;

import com.eagleeye.collector.twse.TwseClient;
import com.eagleeye.collector.service.CollectionStatus;
import com.eagleeye.domain.entity.MarginTransaction;
import com.eagleeye.domain.repository.MarginTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:margin_it;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class MarginTransactionServiceIT {

    @Autowired
    private MarginTransactionService service;

    @Autowired
    private MarginTransactionRepository repository;

    @MockitoBean
    private TwseClient twseClient;

    private static final LocalDate DATE = LocalDate.of(2026, 3, 18);

    private static final String MARGIN_JSON = """
            {
              "stat": "OK",
              "date": "20260313",
              "tables": [
                {
                  "title": "2026/03/13 Margin transaction summary",
                  "fields": ["Item","Margin Purchase/ Short Covering","Margin Sale/Short Sale","Cash Redemption/ Stock Redemption","Balance of Previous Day","Balance of the Day"],
                  "data": [
                    ["Margin Purchase (Trading unit)", "526,296", "485,038", "6,678", "8,074,444", "8,109,024"],
                    ["Short Sale (Trading unit)", "31,407", "23,277", "1,999", "215,077", "204,948"]
                  ]
                }
              ]
            }
            """;

    private static final String NO_DATA_JSON = """
            {"stat": "NO DATA", "data": []}
            """;

    // ── collectDate: success path ─────────────────────────────────────────────

    @Test
    void collectDate_returnsCollected_andPersistsAllTenFields() {
        when(twseClient.fetchMarginJson(DATE)).thenReturn(MARGIN_JSON);

        DateCollectionResult result = service.collectDate(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.COLLECTED);
        assertThat(result.tradeDate()).isEqualTo(DATE);

        MarginTransaction saved = repository.findByTradeDate(DATE).orElseThrow();
        assertThat(saved.getMarginPurchase()).isEqualTo(526_296L);
        assertThat(saved.getMarginSale()).isEqualTo(485_038L);
        assertThat(saved.getMarginCashRedemption()).isEqualTo(6_678L);
        assertThat(saved.getMarginPrevBalance()).isEqualTo(8_074_444L);
        assertThat(saved.getMarginBalance()).isEqualTo(8_109_024L);
        assertThat(saved.getShortCovering()).isEqualTo(31_407L);
        assertThat(saved.getShortSale()).isEqualTo(23_277L);
        assertThat(saved.getShortStockRedemption()).isEqualTo(1_999L);
        assertThat(saved.getShortPrevBalance()).isEqualTo(215_077L);
        assertThat(saved.getShortBalance()).isEqualTo(204_948L);
    }

    // ── upsert ────────────────────────────────────────────────────────────────

    @Test
    void collectDate_upserts_updatesExistingRowWithoutCreatingDuplicate() {
        when(twseClient.fetchMarginJson(DATE)).thenReturn(MARGIN_JSON);
        service.collectDate(DATE);
        assertThat(repository.count()).isEqualTo(1);

        String updatedJson = """
                {
                  "stat": "OK",
                  "tables": [
                    {
                      "data": [
                        ["Margin Purchase (Trading unit)", "600,000", "500,000", "7,000", "9,000,000", "9,100,000"],
                        ["Short Sale (Trading unit)", "40,000", "30,000", "2,000", "220,000", "230,000"]
                      ]
                    }
                  ]
                }
                """;
        when(twseClient.fetchMarginJson(DATE)).thenReturn(updatedJson);
        service.collectDate(DATE);

        assertThat(repository.count()).isEqualTo(1);
        MarginTransaction updated = repository.findByTradeDate(DATE).orElseThrow();
        assertThat(updated.getMarginPurchase()).isEqualTo(600_000L);
        assertThat(updated.getShortBalance()).isEqualTo(230_000L);
    }

    // ── no-data path ──────────────────────────────────────────────────────────

    @Test
    void collectDate_returnsNoData_andPersistsNothing_whenApiReturnsNoStat() {
        when(twseClient.fetchMarginJson(DATE)).thenReturn(NO_DATA_JSON);

        DateCollectionResult result = service.collectDate(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.NO_DATA);
        assertThat(repository.findByTradeDate(DATE)).isEmpty();
    }

    // ── error path ────────────────────────────────────────────────────────────

    @Test
    void collectDate_returnsError_andPersistsNothing_whenClientThrows() {
        when(twseClient.fetchMarginJson(DATE)).thenThrow(new RuntimeException("connection timeout"));

        DateCollectionResult result = service.collectDate(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.ERROR);
        assertThat(result.errorMessage()).contains("connection timeout");
        assertThat(repository.findByTradeDate(DATE)).isEmpty();
    }
}
