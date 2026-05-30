package com.eagleeye.collector.service;

import com.eagleeye.collector.twse.TwseClient;
import com.eagleeye.domain.entity.InstitutionalFlow;
import com.eagleeye.domain.repository.InstitutionalFlowRepository;
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
        "spring.datasource.url=jdbc:h2:mem:iflow_it;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class InstitutionalFlowServiceIT {

    @Autowired
    private InstitutionalFlowService service;

    @Autowired
    private InstitutionalFlowRepository repository;

    @MockitoBean
    private TwseClient twseClient;

    private static final LocalDate DATE = LocalDate.of(2026, 3, 19);

    private static final String FLOW_JSON = """
            {
              "stat": "OK",
              "tables": [
                {
                  "data": [
                    ["Foreign Investors", "100,000,000,000", "80,000,000,000", "20,000,000,000"],
                    ["Investment Trust",   "5,000,000,000",  "4,000,000,000",  "1,000,000,000"],
                    ["Dealers",            "3,000,000,000",  "2,500,000,000",    "500,000,000"]
                  ]
                }
              ]
            }
            """;

    private static final String NO_DATA_JSON = """
            {"stat": "NO DATA", "tables": [{"data": []}]}
            """;

    @Test
    void collectDate_returnsCollected_andPersistsAllNineFields() {
        when(twseClient.fetchInstitutionalFlowJson(DATE)).thenReturn(FLOW_JSON);

        DateCollectionResult result = service.collectDate(DATE);

        assertThat(result).isInstanceOf(DateCollectionResult.Collected.class);
        assertThat(result.tradeDate()).isEqualTo(DATE);

        InstitutionalFlow saved = repository.findByTradeDate(DATE).orElseThrow();
        assertThat(saved.getForeignBuy()).isEqualTo(100_000_000_000L);
        assertThat(saved.getForeignSell()).isEqualTo(80_000_000_000L);
        assertThat(saved.getForeignNet()).isEqualTo(20_000_000_000L);
        assertThat(saved.getInvestmentTrustBuy()).isEqualTo(5_000_000_000L);
        assertThat(saved.getInvestmentTrustSell()).isEqualTo(4_000_000_000L);
        assertThat(saved.getInvestmentTrustNet()).isEqualTo(1_000_000_000L);
        assertThat(saved.getDealerBuy()).isEqualTo(3_000_000_000L);
        assertThat(saved.getDealerSell()).isEqualTo(2_500_000_000L);
        assertThat(saved.getDealerNet()).isEqualTo(500_000_000L);
    }

    @Test
    void collectDate_upserts_updatesExistingRowWithoutCreatingDuplicate() {
        when(twseClient.fetchInstitutionalFlowJson(DATE)).thenReturn(FLOW_JSON);
        service.collectDate(DATE);
        assertThat(repository.count()).isEqualTo(1);

        String updatedJson = """
                {
                  "stat": "OK",
                  "tables": [
                    {
                      "data": [
                        ["Foreign Investors", "200,000,000,000", "150,000,000,000", "50,000,000,000"],
                        ["Investment Trust",   "6,000,000,000",   "5,000,000,000",  "1,000,000,000"],
                        ["Dealers",            "4,000,000,000",   "3,000,000,000",  "1,000,000,000"]
                      ]
                    }
                  ]
                }
                """;
        when(twseClient.fetchInstitutionalFlowJson(DATE)).thenReturn(updatedJson);
        service.collectDate(DATE);

        assertThat(repository.count()).isEqualTo(1);
        InstitutionalFlow updated = repository.findByTradeDate(DATE).orElseThrow();
        assertThat(updated.getForeignBuy()).isEqualTo(200_000_000_000L);
        assertThat(updated.getDealerNet()).isEqualTo(1_000_000_000L);
    }

    @Test
    void collectDate_returnsNoData_andPersistsNothing_whenApiReturnsNoStat() {
        when(twseClient.fetchInstitutionalFlowJson(DATE)).thenReturn(NO_DATA_JSON);

        DateCollectionResult result = service.collectDate(DATE);

        assertThat(result).isInstanceOf(DateCollectionResult.NoData.class);
        assertThat(repository.findByTradeDate(DATE)).isEmpty();
    }

    @Test
    void collectDate_returnsError_andPersistsNothing_whenClientThrows() {
        when(twseClient.fetchInstitutionalFlowJson(DATE))
                .thenThrow(new RuntimeException("connection timeout"));

        DateCollectionResult result = service.collectDate(DATE);

        assertThat(result).isInstanceOf(DateCollectionResult.Error.class);
        DateCollectionResult.Error error = (DateCollectionResult.Error) result;
        assertThat(error.message()).contains("connection timeout");
        assertThat(repository.findByTradeDate(DATE)).isEmpty();
    }
}
