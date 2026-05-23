package com.eagleeye.collector.twse;

import com.eagleeye.domain.entity.InstitutionalFlow;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InstitutionalFlowParserTest {

    private InstitutionalFlowParser parser;

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

    @BeforeEach
    void setUp() {
        parser = new InstitutionalFlowParser(new ObjectMapper());
    }

    @Test
    void parse_success_returnsAllNineFields() {
        InstitutionalFlow flow = parser.parse(FLOW_JSON, DATE);

        assertThat(flow).isNotNull();
        assertThat(flow.getTradeDate()).isEqualTo(DATE);
        assertThat(flow.getForeignBuy()).isEqualTo(100_000_000_000L);
        assertThat(flow.getForeignSell()).isEqualTo(80_000_000_000L);
        assertThat(flow.getForeignNet()).isEqualTo(20_000_000_000L);
        assertThat(flow.getInvestmentTrustBuy()).isEqualTo(5_000_000_000L);
        assertThat(flow.getInvestmentTrustSell()).isEqualTo(4_000_000_000L);
        assertThat(flow.getInvestmentTrustNet()).isEqualTo(1_000_000_000L);
        assertThat(flow.getDealerBuy()).isEqualTo(3_000_000_000L);
        assertThat(flow.getDealerSell()).isEqualTo(2_500_000_000L);
        assertThat(flow.getDealerNet()).isEqualTo(500_000_000L);
    }

    @Test
    void parse_statNotOk_returnsNull() {
        String json = """
                {"stat": "NO DATA", "tables": [{"data": []}]}
                """;
        assertThat(parser.parse(json, DATE)).isNull();
    }

    @Test
    void parse_emptyData_returnsNull() {
        String json = """
                {"stat": "OK", "tables": [{"data": []}]}
                """;
        assertThat(parser.parse(json, DATE)).isNull();
    }

    @Test
    void parse_invalidJson_returnsNull() {
        assertThat(parser.parse("not-json", DATE)).isNull();
    }

    @Test
    void parse_missingInvestorGroup_returnsNull() {
        String json = """
                {
                  "stat": "OK",
                  "tables": [
                    {
                      "data": [
                        ["Foreign Investors", "100,000,000,000", "80,000,000,000", "20,000,000,000"]
                      ]
                    }
                  ]
                }
                """;
        assertThat(parser.parse(json, DATE)).isNull();
    }
}
