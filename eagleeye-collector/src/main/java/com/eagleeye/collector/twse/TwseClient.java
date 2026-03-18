package com.eagleeye.collector.twse;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * HTTP client for the TWSE TAIEX monthly index data API.
 *
 * Endpoint: GET https://www.twse.com.tw/indicesReport/MI_5MINS_HIST?date=YYYYMMDD&response=json
 *
 * The date parameter selects the month — TWSE returns all trading days in that month.
 * We always use the first day of the month as the query date.
 */
@Component
public class TwseClient {

    private static final String BASE_URL = "https://www.twse.com.tw";
    private static final String TAIEX_PATH = "/indicesReport/MI_5MINS_HIST";
    private static final String MARKET_STATS_PATH = "/rwd/zh/afterTrading/FMTQIK";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestClient restClient;

    public TwseClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl(BASE_URL)
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; EagleEye/1.0)")
                .build();
    }

    public String fetchMonthJson(YearMonth yearMonth) {
        String queryDate = yearMonth.atDay(1).format(DATE_FORMAT);
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(TAIEX_PATH)
                        .queryParam("date", queryDate)
                        .queryParam("response", "json")
                        .build())
                .retrieve()
                .body(String.class);
    }

    public String fetchMarketStatsJson(YearMonth yearMonth) {
        String queryDate = yearMonth.atDay(1).format(DATE_FORMAT);
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(MARKET_STATS_PATH)
                        .queryParam("date", queryDate)
                        .queryParam("response", "json")
                        .build())
                .retrieve()
                .body(String.class);
    }
}
