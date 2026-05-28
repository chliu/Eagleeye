package com.eagleeye.shell.commands;

import com.eagleeye.collector.service.DateCollectionResult;
import com.eagleeye.collector.service.InstitutionalFlowService;
import com.eagleeye.domain.entity.InstitutionalFlow;
import com.eagleeye.domain.repository.InstitutionalFlowRepository;
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
public class InstitutionalFlowCommands {

    private final InstitutionalFlowService service;
    private final InstitutionalFlowRepository repository;
    private final TableFormatter formatter;

    @Autowired
    public InstitutionalFlowCommands(InstitutionalFlowService service,
                                     InstitutionalFlowRepository repository,
                                     TableFormatter formatter) {
        this.service = service;
        this.repository = repository;
        this.formatter = formatter;
    }

    @Command(name = "institutional-flow list", description = "Show institutional flow data for a single date")
    public String list(
            @Option(longName = "date", description = "Trade date YYYY-MM-DD (default: today)", defaultValue = "") String date) {
        LocalDate d = (date == null || date.isEmpty()) ? LocalDate.now() : LocalDate.parse(date);
        Optional<InstitutionalFlow> flow = repository.findByTradeDate(d);
        if (flow.isEmpty()) return "No data for " + d;
        return formatter.formatInstitutionalFlow(List.of(flow.get()));
    }

    @Command(name = "institutional-flow show", description = "Show institutional flow data over a date range")
    public String show(
            @Option(longName = "from", description = "Start date YYYY-MM-DD (default: 30 days ago)", defaultValue = "") String from,
            @Option(longName = "to",   description = "End date YYYY-MM-DD (default: today)",         defaultValue = "") String to) {
        LocalDate toDate   = (to   == null || to.isEmpty())   ? LocalDate.now()      : LocalDate.parse(to);
        LocalDate fromDate = (from == null || from.isEmpty()) ? toDate.minusDays(30) : LocalDate.parse(from);
        List<InstitutionalFlow> flows =
                repository.findByTradeDateBetweenOrderByTradeDateAsc(fromDate, toDate);
        return "Institutional Flow \u2014 " + fromDate + " \u2192 " + toDate
                + " (" + flows.size() + " records)\n"
                + formatter.formatInstitutionalFlow(flows);
    }

    @Command(name = "institutional-flow collect", description = "Collect institutional flow data for a date")
    public String collect(
            @Option(longName = "date", description = "Trade date YYYY-MM-DD (default: today)", defaultValue = "") String date) {
        LocalDate d = (date == null || date.isEmpty()) ? LocalDate.now() : LocalDate.parse(date);
        return formatResult(service.collectDate(d));
    }

    @Command(name = "institutional-flow backfill", description = "Backfill institutional flow data for a date range")
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
                sb.append(formatResult(service.collectDate(current))).append("\n");
                try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            current = current.plusDays(1);
        }
        return sb.toString().stripTrailing();
    }

    private String formatResult(DateCollectionResult r) {
        return switch (r.status()) {
            case COLLECTED -> r.tradeDate() + " \u2014 collected";
            case NO_DATA   -> r.tradeDate() + " \u2014 no data";
            case ERROR     -> r.tradeDate() + " \u2014 ERROR: " + r.errorMessage();
        };
    }
}
