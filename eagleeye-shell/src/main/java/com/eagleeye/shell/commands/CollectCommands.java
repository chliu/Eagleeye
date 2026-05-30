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
        return switch (r) {
            case CollectionResult.Collected c -> c.date() + " \u2014 futures: " + c.futuresCount() + ", options: " + c.optionsCount();
            case CollectionResult.NoData n    -> n.date() + " \u2014 no data (holiday)";
            case CollectionResult.Error e     -> e.date() + " \u2014 ERROR: " + e.message();
        };
    }
}
