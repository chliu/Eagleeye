package com.eagleeye.shell.commands;

import com.eagleeye.collector.service.MarginCollectionResult;
import com.eagleeye.collector.service.MarginTransactionService;
import com.eagleeye.domain.entity.MarginTransaction;
import com.eagleeye.domain.repository.MarginTransactionRepository;
import com.eagleeye.shell.formatter.TableFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class MarginTransactionCommands {

    private final MarginTransactionService marginTransactionService;
    private final MarginTransactionRepository repository;
    private final TableFormatter formatter;

    @Autowired
    public MarginTransactionCommands(MarginTransactionService marginTransactionService,
                                     MarginTransactionRepository repository,
                                     TableFormatter formatter) {
        this.marginTransactionService = marginTransactionService;
        this.repository = repository;
        this.formatter = formatter;
    }

    @Command(name = "margin list", description = "Show margin transaction data for a single date")
    public String list(
            @Option(longName = "date", description = "Trade date YYYY-MM-DD (default: today)", defaultValue = "") String date) {
        LocalDate d = (date == null || date.isEmpty()) ? LocalDate.now() : LocalDate.parse(date);
        Optional<MarginTransaction> bar = repository.findByTradeDate(d);
        if (bar.isEmpty()) return "No data for " + d;
        return formatter.formatMarginTransaction(List.of(bar.get()));
    }

    @Command(name = "margin show", description = "Show margin transaction data over a date range")
    public String show(
            @Option(longName = "from", description = "Start date YYYY-MM-DD (default: 30 days ago)", defaultValue = "") String from,
            @Option(longName = "to",   description = "End date YYYY-MM-DD (default: today)",         defaultValue = "") String to) {
        LocalDate toDate   = (to   == null || to.isEmpty())   ? LocalDate.now()      : LocalDate.parse(to);
        LocalDate fromDate = (from == null || from.isEmpty()) ? toDate.minusDays(30) : LocalDate.parse(from);
        List<MarginTransaction> bars = repository.findByTradeDateBetweenOrderByTradeDateAsc(fromDate, toDate);
        return "Margin \u2014 " + fromDate + " \u2192 " + toDate + " (" + bars.size() + " bars)\n"
                + formatter.formatMarginTransaction(bars);
    }

    @Command(name = "margin collect", description = "Collect margin transaction data for a date")
    public String collect(
            @Option(longName = "date", description = "Trade date YYYY-MM-DD (default: today)", defaultValue = "") String date) {
        LocalDate d = (date == null || date.isEmpty()) ? LocalDate.now() : LocalDate.parse(date);
        return formatResult(marginTransactionService.collectDate(d));
    }

    @Command(name = "margin backfill", description = "Backfill margin transaction data for a date range")
    public String backfill(
            @Option(longName = "from", description = "Start date YYYY-MM-DD (default: 12 months ago)", defaultValue = "") String from,
            @Option(longName = "to",   description = "End date YYYY-MM-DD (default: today)",           defaultValue = "") String to) {
        LocalDate fromDate = (from == null || from.isEmpty()) ? LocalDate.now().minusMonths(12) : LocalDate.parse(from);
        LocalDate toDate   = (to   == null || to.isEmpty())   ? LocalDate.now()                 : LocalDate.parse(to);

        StringBuilder sb = new StringBuilder();
        LocalDate current = fromDate;
        while (!current.isAfter(toDate)) {
            if (current.getDayOfWeek() != DayOfWeek.SATURDAY
                    && current.getDayOfWeek() != DayOfWeek.SUNDAY) {
                sb.append(formatResult(marginTransactionService.collectDate(current))).append("\n");
                try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            current = current.plusDays(1);
        }
        return sb.toString().stripTrailing();
    }

    private String formatResult(MarginCollectionResult r) {
        return switch (r.status()) {
            case COLLECTED -> r.tradeDate() + " \u2014 collected";
            case NO_DATA   -> r.tradeDate() + " \u2014 no data";
            case ERROR     -> r.tradeDate() + " \u2014 ERROR: " + r.errorMessage();
        };
    }
}
