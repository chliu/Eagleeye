package com.eagleeye.shell.commands;

import com.eagleeye.domain.entity.TraderType;
import com.eagleeye.domain.repository.OptionsPositionRepository;
import com.eagleeye.shell.formatter.TableFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class OptionsCommands {

    @Autowired
    OptionsPositionRepository repository;

    @Autowired
    TableFormatter formatter;

    @Command(name = "options list", description = "List all options positions for a date")
    public String optionsList(
            @Option(longName = "date", description = "Trade date YYYY-MM-DD (default: today)", defaultValue = "") String date) {
        LocalDate tradeDate = (date == null || date.isEmpty()) ? LocalDate.now() : LocalDate.parse(date);
        var positions = repository.findByTradeDateOrderByContractAscTraderTypeAsc(tradeDate);
        return "Options \u2014 " + tradeDate + " (" + positions.size() + " rows)\n"
                + formatter.formatPositions(positions, false);
    }

    @Command(name = "options show", description = "Show options trend for a contract over a date range")
    public String optionsShow(
            @Option(longName = "contract", description = "Contract code e.g. TXO", required = true) String contract,
            @Option(longName = "trader", description = "DEALER | INVESTMENT_TRUST | FINI", defaultValue = "") String trader,
            @Option(longName = "from", description = "Start date YYYY-MM-DD (default: 30 days ago)", defaultValue = "") String from,
            @Option(longName = "to", description = "End date YYYY-MM-DD (default: today)", defaultValue = "") String to) {
        LocalDate toDate = (to == null || to.isEmpty()) ? LocalDate.now() : LocalDate.parse(to);
        LocalDate fromDate = (from == null || from.isEmpty()) ? toDate.minusDays(30) : LocalDate.parse(from);
        String contractUpper = contract.toUpperCase();

        var positions = (trader == null || trader.isEmpty())
                ? repository.findByContractAndTradeDateBetweenOrderByTradeDateAsc(contractUpper, fromDate, toDate)
                : repository.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(
                        contractUpper, TraderType.valueOf(trader.toUpperCase()), fromDate, toDate);

        return "Options " + contractUpper + " \u2014 " + fromDate + " \u2192 " + toDate
                + " (" + positions.size() + " rows)\n"
                + formatter.formatTrend(positions);
    }
}
