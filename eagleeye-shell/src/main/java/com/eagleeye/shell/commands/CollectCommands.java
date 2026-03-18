package com.eagleeye.shell.commands;

import com.eagleeye.collector.service.CollectionResult;
import com.eagleeye.collector.service.CollectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class CollectCommands {

    @Autowired
    CollectionService collectionService;

    @Command(name = "collect", description = "Collect trading data (futures + options)")
    public String collect(
            @Option(longName = "date", description = "Trade date YYYY-MM-DD (default: today)", defaultValue = "") String date) {
        LocalDate d = (date == null || date.isEmpty()) ? LocalDate.now() : LocalDate.parse(date);
        CollectionResult result = collectionService.collectAll(d);
        return formatResult(result);
    }

    private String formatResult(CollectionResult r) {
        return switch (r.status()) {
            case COLLECTED -> r.date() + " \u2014 futures: " + r.futuresCount() + ", options: " + r.optionsCount();
            case NO_DATA   -> r.date() + " \u2014 no data (holiday)";
            case ERROR     -> r.date() + " \u2014 ERROR: " + r.errorMessage();
        };
    }
}
