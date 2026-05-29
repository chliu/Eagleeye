package com.eagleeye.collector.taifex;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class TaifexClient {

    private static final String BASE_URL        = "https://www.taifex.com.tw";
    private static final String FUTURES_PATH    = "/enl/eng3/futContractsDate";
    private static final String FUTURES_AH_PATH = "/enl/eng3/futContractsDateAh";
    private static final String OPTIONS_PATH    = "/enl/eng3/optContractsDate";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final RestClient restClient;

    public TaifexClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl(BASE_URL)
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; EagleEye/1.0)")
                .build();
    }

    public String fetchFuturesHtml(LocalDate date) {
        return fetch(FUTURES_PATH, date);
    }

    public String fetchFuturesAhHtml(LocalDate date) {
        return fetch(FUTURES_AH_PATH, date);
    }

    public String fetchOptionsHtml(LocalDate date) {
        return fetch(OPTIONS_PATH, date);
    }

    private String fetch(String path, LocalDate date) {
        String queryDate = date.format(DATE_FORMAT);
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(path)
                        .queryParam("queryType", "1")
                        .queryParam("goDay", "")
                        .queryParam("doQuery", "1")
                        .queryParam("dateaddcnt", "")
                        .queryParam("queryDate", queryDate)
                        .queryParam("commodityId", "")
                        .queryParam("button", "Send Query")
                        .build())
                .retrieve()
                .body(String.class);
    }
}
