package com.eagleeye.shell.commands;

import com.eagleeye.collector.service.FuturesAhService;
import com.eagleeye.domain.entity.TraderType;
import com.eagleeye.domain.repository.FuturesAhPositionRepository;
import com.eagleeye.shell.formatter.TableFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class FuturesAhCommands {

    @Autowired FuturesAhService service;
    @Autowired FuturesAhPositionRepository repository;
    @Autowired TableFormatter formatter;

    @Command(name = "collect-ah", description = "Collect after-hours (夜盤) futures for a date")
    public String collectAh(
            @Option(longName = "date", description = "Attributed trade date YYYY-MM-DD (default: today)", defaultValue = "") String date) {
        LocalDate d = (date == null || date.isEmpty()) ? LocalDate.now() : LocalDate.parse(date);
        var result = service.collectDate(d);
        return switch (result) {
            case com.eagleeye.collector.service.DateCollectionResult.Collected c -> c.tradeDate() + " — after-hours futures collected";
            case com.eagleeye.collector.service.DateCollectionResult.NoData n    -> n.tradeDate() + " — no data";
            case com.eagleeye.collector.service.DateCollectionResult.Error e     -> e.tradeDate() + " — ERROR: " + e.message();
        };
    }

    @Command(name = "futures-ah list", description = "List after-hours futures positions for a date")
    public String futuresAhList(
            @Option(longName = "date", description = "Trade date YYYY-MM-DD (default: today)", defaultValue = "") String date) {
        LocalDate tradeDate = (date == null || date.isEmpty()) ? LocalDate.now() : LocalDate.parse(date);
        var positions = repository.findByTradeDateOrderByContractAscTraderTypeAsc(tradeDate);
        return "After-Hours Futures — " + tradeDate + " (" + positions.size() + " rows)\n"
                + formatter.formatPositions(positions, true);
    }

    @Command(name = "futures-ah show", description = "Show after-hours futures trend for a contract")
    public String futuresAhShow(
            @Option(longName = "contract", description = "Contract code e.g. TX, MTX", required = true) String contract,
            @Option(longName = "trader", description = "DEALER | INVESTMENT_TRUST | FINI", defaultValue = "") String trader,
            @Option(longName = "from", description = "Start date YYYY-MM-DD (default: 30 days ago)", defaultValue = "") String from,
            @Option(longName = "to", description = "End date YYYY-MM-DD (default: today)", defaultValue = "") String to) {
        LocalDate toDate   = (to == null || to.isEmpty())   ? LocalDate.now()             : LocalDate.parse(to);
        LocalDate fromDate = (from == null || from.isEmpty()) ? toDate.minusDays(30)      : LocalDate.parse(from);
        String contractUpper = contract.toUpperCase();

        var positions = (trader == null || trader.isEmpty())
                ? repository.findByContractAndTradeDateBetweenOrderByTradeDateAsc(contractUpper, fromDate, toDate)
                : repository.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(
                        contractUpper, TraderType.valueOf(trader.toUpperCase()), fromDate, toDate);

        return "After-Hours Futures " + contractUpper + " — " + fromDate + " → " + toDate
                + " (" + positions.size() + " rows)\n"
                + formatter.formatTrend(positions);
    }
}
