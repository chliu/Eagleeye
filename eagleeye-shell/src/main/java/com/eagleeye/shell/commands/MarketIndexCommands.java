package com.eagleeye.shell.commands;

import com.eagleeye.collector.service.MarketIndexCollectionResult;
import com.eagleeye.collector.service.MarketIndexService;
import com.eagleeye.domain.entity.TaiexDailyBar;
import com.eagleeye.domain.repository.TaiexDailyBarRepository;
import com.eagleeye.shell.formatter.TableFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Component
public class MarketIndexCommands {

    private final MarketIndexService marketIndexService;
    private final TaiexDailyBarRepository repository;
    private final TableFormatter formatter;

    @Autowired
    public MarketIndexCommands(MarketIndexService marketIndexService,
                               TaiexDailyBarRepository repository,
                               TableFormatter formatter) {
        this.marketIndexService = marketIndexService;
        this.repository = repository;
        this.formatter = formatter;
    }

    @Command(name = "market-index list", description = "Show TAIEX index for a single date")
    public String list(
            @Option(longName = "date", description = "Trade date YYYY-MM-DD (default: today)", defaultValue = "") String date) {
        LocalDate d = (date == null || date.isEmpty()) ? LocalDate.now() : LocalDate.parse(date);
        Optional<TaiexDailyBar> bar = repository.findByTradeDate(d);
        if (bar.isEmpty()) return "No data for " + d;
        return formatter.formatMarketIndex(List.of(bar.get()));
    }

    @Command(name = "market-index show", description = "Show TAIEX daily index over a date range")
    public String show(
            @Option(longName = "from", description = "Start date YYYY-MM-DD (default: 30 days ago)", defaultValue = "") String from,
            @Option(longName = "to",   description = "End date YYYY-MM-DD (default: today)",         defaultValue = "") String to) {
        LocalDate toDate   = (to   == null || to.isEmpty())   ? LocalDate.now()          : LocalDate.parse(to);
        LocalDate fromDate = (from == null || from.isEmpty()) ? toDate.minusDays(30)     : LocalDate.parse(from);
        List<TaiexDailyBar> bars = repository.findByTradeDateBetweenOrderByTradeDateAsc(fromDate, toDate);
        return "TAIEX \u2014 " + fromDate + " \u2192 " + toDate + " (" + bars.size() + " bars)\n"
                + formatter.formatMarketIndex(bars);
    }

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
