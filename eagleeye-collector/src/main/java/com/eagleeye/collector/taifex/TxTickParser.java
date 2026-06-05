package com.eagleeye.collector.taifex;

import com.eagleeye.domain.entity.TxTick;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TxTickParser {

    public List<TxTick> parse(List<String> lines, LocalDate tradeDate) {
        // Steps 1–4: filter and validate each row
        record Row(String[] cols, String contractMonth, int price, int volume) {}
        List<Row> candidates = new ArrayList<>();

        for (String line : lines) {
            String[] cols = line.split(",", -1);
            if (cols.length < 9) continue;

            if (!"TX".equals(cols[1].trim())) continue;                     // rule 1
            String contract = cols[2].trim();
            if (contract.contains("/")) continue;                            // rule 2

            int price, volume;
            try {
                price  = Integer.parseInt(cols[4].trim().replace(",", "")); // rule 3
                volume = Integer.parseInt(cols[5].trim().replace(",", "")); // rule 3
            } catch (NumberFormatException e) {
                continue;
            }
            if (price < 1000) continue;                                      // rule 4

            candidates.add(new Row(cols, contract, price, volume));
        }

        if (candidates.isEmpty()) return List.of();

        // Step 5: keep only the contract_month with the highest total volume
        String dominant = candidates.stream()
            .collect(Collectors.groupingBy(Row::contractMonth,
                     Collectors.summingInt(Row::volume)))
            .entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("");

        // Steps 6–8: pad time, filter time range, mark auction
        List<TxTick> ticks = new ArrayList<>();
        for (Row r : candidates) {
            if (!r.contractMonth().equals(dominant)) continue;               // step 5

            String time = padTime(r.cols()[3].trim());                       // step 6
            if (time.compareTo("084500") < 0 || time.compareTo("134500") > 0) continue; // step 7

            boolean auction = r.cols()[8].trim().contains("*");              // step 8
            ticks.add(new TxTick(tradeDate, time, r.price(), r.volume(), dominant, auction));
        }

        return ticks;
    }

    private String padTime(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        while (digits.length() < 6) digits = "0" + digits;
        return digits.substring(0, 6);
    }
}
