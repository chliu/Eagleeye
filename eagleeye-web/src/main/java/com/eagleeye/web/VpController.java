package com.eagleeye.web;

import com.eagleeye.web.vp.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/vp")
public class VpController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private final VolumeProfileService service;

    public VpController(VolumeProfileService service) {
        this.service = service;
    }

    @GetMapping("/available-dates")
    public List<String> availableDates(@RequestParam(defaultValue = "TX") String product) {
        return service.getAvailableDates();
    }

    @GetMapping("/summary")
    public VpSummary summary(@RequestParam String date,
                             @RequestParam(defaultValue = "TX") String product) {
        return service.getSummary(parse(date));
    }

    @GetMapping("/profile")
    public List<VpEntry> profile(@RequestParam String date,
                                 @RequestParam(defaultValue = "TX") String product,
                                 @RequestParam(defaultValue = "50") int step) {
        return service.getProfile(parse(date), step);
    }

    @GetMapping("/sessions")
    public SessionsResponse sessions(@RequestParam String date,
                                     @RequestParam(defaultValue = "TX") String product) {
        return service.getSessions(parse(date));
    }

    @GetMapping("/large-trades")
    public List<LargeTrade> largeTrades(@RequestParam String date,
                                        @RequestParam(defaultValue = "TX") String product,
                                        @RequestParam(defaultValue = "50") int threshold) {
        return service.getLargeTrades(parse(date), threshold);
    }

    @GetMapping("/candles")
    public List<VpCandle> candles(
            @RequestParam String date,
            @RequestParam(defaultValue = "TX") String product,
            @RequestParam(defaultValue = "5") int interval) {
        return service.getCandles(parse(date), interval);
    }

    @GetMapping("/plan")
    public TradingPlan plan(@RequestParam String date,
                            @RequestParam(defaultValue = "TX") String product) {
        return service.getPlan(parse(date));
    }

    private LocalDate parse(String date) {
        return LocalDate.parse(date, DATE_FMT);
    }
}
