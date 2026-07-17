package com.eagleeye.collector.taifex;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class TaifexClient {

    private static final String BASE_URL        = "https://www.taifex.com.tw";
    private static final String FUTURES_PATH    = "/enl/eng3/futContractsDate";
    private static final String FUTURES_AH_PATH = "/enl/eng3/futContractsDateAh";
    private static final String OPTIONS_PATH    = "/enl/eng3/optContractsDate";
    private static final String OPTIONS_CALL_PUT_PATH = "/enl/eng3/callsAndPutsDate";
    private static final String DAILY_MARKET_REPORT_PATH = "/enl/eng3/futDailyMarketReport";
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

    public String fetchOptionsCallPutHtml(LocalDate date) {
        return fetch(OPTIONS_CALL_PUT_PATH, date);
    }

    /**
     * Fetches TAIFEX's daily market report (期貨每日交易行情) for one contract —
     * total open interest per contract-month, independent of trader type. Unlike
     * the other reports here, this one requires a POST with form-encoded body
     * (verified against production TAIFEX; a GET with equivalent query params
     * returns "No Data").
     */
    public String fetchDailyMarketReportHtml(LocalDate date, String contract) {
        String queryDate = date.format(DATE_FORMAT);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("queryType", "2");
        form.add("marketCode", "0");
        form.add("dateaddcnt", "");
        form.add("commodity_id", contract);
        form.add("commodity_id2", "");
        form.add("queryDate", queryDate);

        return restClient.post()
                .uri(DAILY_MARKET_REPORT_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
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
