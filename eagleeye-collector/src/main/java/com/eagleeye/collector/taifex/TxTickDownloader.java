package com.eagleeye.collector.taifex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

@Component
public class TxTickDownloader {

    private static final Logger log = LoggerFactory.getLogger(TxTickDownloader.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy_MM_dd");
    private static final int MAX_RETRIES = 3;

    private final RestClient restClient;

    public TxTickDownloader(RestClient.Builder builder) {
        this.restClient = builder
            .baseUrl("https://www.taifex.com.tw")
            .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; EagleEye/1.0)")
            .build();
    }

    /**
     * Downloads and unzips the TAIFEX daily CSV for the given date.
     * Returns null when the file is not available (holiday / weekend).
     * Retries up to 3 times on transient errors (backoff: 1s, 2s, 4s).
     */
    public List<String> downloadLines(LocalDate date) throws IOException, InterruptedException {
        String path = "/file/taifex/Dailydownload/DailydownloadCSV/Daily_" + date.format(FMT) + ".zip";

        byte[] zipBytes = null;
        long delayMs = 1_000;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                zipBytes = restClient.get()
                    .uri(path)
                    .retrieve()
                    .body(byte[].class);
                break;
            } catch (HttpClientErrorException.NotFound e) {
                log.info("No tick data for {} (404)", date);
                return null;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) throw e;
                log.warn("Download attempt {}/{} failed for {}: {}", attempt, MAX_RETRIES, date, e.getMessage());
                Thread.sleep(delayMs);
                delayMs *= 2;
            }
        }

        if (zipBytes == null) return null;
        return unzipToLines(zipBytes);
    }

    private List<String> unzipToLines(byte[] zipBytes) throws IOException {
        List<String> lines = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            if (zis.getNextEntry() == null) return lines;
            String content = new String(zis.readAllBytes(), Charset.forName("Big5"));
            for (String line : content.split("\r?\n")) {
                if (!line.isBlank()) lines.add(line);
            }
        }
        return lines;
    }
}
