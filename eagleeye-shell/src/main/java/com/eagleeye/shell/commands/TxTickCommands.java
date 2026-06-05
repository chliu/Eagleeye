package com.eagleeye.shell.commands;

import com.eagleeye.collector.service.DateCollectionResult;
import com.eagleeye.collector.service.TxTickService;
import com.eagleeye.domain.entity.TxTick;
import com.eagleeye.domain.repository.TxTickRepository;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Component
public class TxTickCommands {

    private final TxTickService service;
    private final TxTickRepository repository;

    public TxTickCommands(TxTickService service, TxTickRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @Command(name = "tx-tick collect", description = "Collect TX tick data for a date")
    public String collect(
            @Option(longName = "date", description = "Trade date YYYY-MM-DD (default: today)", defaultValue = "") String date) {
        LocalDate d = (date == null || date.isEmpty()) ? LocalDate.now() : LocalDate.parse(date);
        return switch (service.collectDate(d)) {
            case DateCollectionResult.Collected c -> d + " — TX ticks collected";
            case DateCollectionResult.NoData n    -> d + " — no data";
            case DateCollectionResult.Error e     -> d + " — ERROR: " + e.message();
        };
    }

    @Command(name = "tx-tick list", description = "Show TX tick count and first 5 rows for a date")
    public String list(
            @Option(longName = "date", description = "Trade date YYYY-MM-DD (default: today)", defaultValue = "") String date) {
        LocalDate d = (date == null || date.isEmpty()) ? LocalDate.now() : LocalDate.parse(date);
        long count = repository.countByTradeDate(d);
        List<TxTick> sample = repository.findTop5ByTradeDateOrderByTimeAsc(d);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("TX Ticks — %s (%d rows)%n", d, count));
        if (!sample.isEmpty()) {
            sb.append(String.format("  %-6s  %-7s  %-4s  %-12s  %s%n", "TIME", "PRICE", "VOL", "CONTRACT", "AUC"));
            for (TxTick t : sample) {
                sb.append(String.format("  %-6s  %-7d  %-4d  %-12s  %s%n",
                    t.getTime(), t.getPrice(), t.getVolume(), t.getContractMonth(),
                    t.isAuction() ? "Y" : ""));
            }
            if (count > 5) sb.append(String.format("  ... (%d more)%n", count - 5));
        }
        return sb.toString();
    }

    @Command(name = "tx-tick backfill", description = "Backfill TX tick data for a date range")
    public String backfill(
            @Option(longName = "from", required = true) String from,
            @Option(longName = "to", defaultValue = "") String to) throws InterruptedException {
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate   = (to == null || to.isEmpty()) ? LocalDate.now() : LocalDate.parse(to);
        int ok = 0, holidays = 0, errors = 0;
        LocalDate current = fromDate;
        while (!current.isAfter(toDate)) {
            if (current.getDayOfWeek() == DayOfWeek.SATURDAY ||
                current.getDayOfWeek() == DayOfWeek.SUNDAY) {
                current = current.plusDays(1);
                continue;
            }
            switch (service.collectDate(current)) {
                case DateCollectionResult.Collected c -> ok++;
                case DateCollectionResult.NoData n    -> holidays++;
                case DateCollectionResult.Error e     -> errors++;
            }
            Thread.sleep(1_000);
            current = current.plusDays(1);
        }
        return String.format("TX Tick backfill %s → %s: %d collected, %d holidays, %d errors",
            fromDate, toDate, ok, holidays, errors);
    }
}
