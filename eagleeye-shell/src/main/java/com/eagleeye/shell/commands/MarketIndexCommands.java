package com.eagleeye.shell.commands;

import com.eagleeye.collector.service.MarketIndexCollectionResult;
import com.eagleeye.collector.service.MarketIndexService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

@Component
public class MarketIndexCommands {

    @Autowired
    MarketIndexService marketIndexService;

    @Command(name = "market-index collect", description = "Collect TAIEX data for the month containing the given date")
    public String collect(
            @Option(longName = "date", description = "Trade date YYYY-MM-DD (default: today)", defaultValue = "") String date) {
        LocalDate d = (date == null || date.isEmpty()) ? LocalDate.now() : LocalDate.parse(date);
        MarketIndexCollectionResult result = marketIndexService.collectDate(d);
        return formatResult(result);
    }

    @Command(name = "market-index backfill", description = "Backfill TAIEX data for a range of months")
    public String backfill(
            @Option(longName = "from", description = "Start date YYYY-MM-DD (default: 12 months ago)", defaultValue = "") String from,
            @Option(longName = "to",   description = "End date YYYY-MM-DD (default: today)",           defaultValue = "") String to) {

        LocalDate fromDate = (from == null || from.isEmpty()) ? LocalDate.now().minusMonths(12) : LocalDate.parse(from);
        LocalDate toDate   = (to   == null || to.isEmpty())   ? LocalDate.now()                 : LocalDate.parse(to);

        YearMonth fromYm = YearMonth.from(fromDate);
        YearMonth toYm   = YearMonth.from(toDate);

        StringBuilder sb = new StringBuilder();
        YearMonth current = fromYm;
        while (!current.isAfter(toYm)) {
            MarketIndexCollectionResult result = marketIndexService.collectMonth(current);
            sb.append(formatResult(result)).append("\n");
            current = current.plusMonths(1);
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        return sb.toString().stripTrailing();
    }

    private String formatResult(MarketIndexCollectionResult r) {
        return switch (r.status()) {
            case COLLECTED -> r.yearMonth() + " \u2014 bars: " + r.barsCount();
            case NO_DATA   -> r.yearMonth() + " \u2014 no data";
            case ERROR     -> r.yearMonth() + " \u2014 ERROR: " + r.errorMessage();
        };
    }
}
