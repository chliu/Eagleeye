package com.eagleeye.web;

import com.eagleeye.domain.entity.TxTick;
import com.eagleeye.domain.repository.TxTickRepository;
import com.eagleeye.web.vp.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VolumeProfileServiceTest {

    @Mock TxTickRepository repo;

    VolumeProfileService service;

    @BeforeEach
    void setUp() {
        service = new VolumeProfileService(repo);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    TxTick tick(String time, int price, int volume) {
        return new TxTick(LocalDate.of(2026, 6, 5), time, price, volume, "202606", false);
    }

    TxTick auction(String time, int price, int volume) {
        return new TxTick(LocalDate.of(2026, 6, 5), time, price, volume, "202606", true);
    }

    // ── getSummary tests ──────────────────────────────────────────────────────

    @Test
    void getSummary_excludesAuctionTicks() {
        when(repo.findByTradeDateOrderByTimeAsc(any()))
            .thenReturn(List.of(
                auction("084500", 40000, 500),
                tick("090000", 40100, 100),
                tick("090001", 40200, 200),
                tick("090002", 40100, 150)
            ));

        VpSummary s = service.getSummary(LocalDate.of(2026, 6, 5));

        assertThat(s.vpoc()).isEqualTo(40100);
        assertThat(s.totalVolume()).isEqualTo(450);
    }

    @Test
    void getSummary_computesOhlc() {
        when(repo.findByTradeDateOrderByTimeAsc(any()))
            .thenReturn(List.of(
                tick("090000", 40100, 100),
                tick("090001", 40500,  50),
                tick("090002", 39900,  80),
                tick("134500", 40200,  60)
            ));

        VpSummary s = service.getSummary(LocalDate.of(2026, 6, 5));

        assertThat(s.open()).isEqualTo(40100);
        assertThat(s.close()).isEqualTo(40200);
        assertThat(s.high()).isEqualTo(40500);
        assertThat(s.low()).isEqualTo(39900);
    }

    @Test
    void getSummary_computesVpoc() {
        when(repo.findByTradeDateOrderByTimeAsc(any()))
            .thenReturn(List.of(
                tick("090000", 40100, 100),
                tick("090001", 40200, 300),
                tick("090002", 40100,  50)
            ));

        VpSummary s = service.getSummary(LocalDate.of(2026, 6, 5));

        assertThat(s.vpoc()).isEqualTo(40200);
        assertThat(s.vpocVolume()).isEqualTo(300);
    }

    @Test
    void getSummary_valueAreaCoversVpocAlone_whenVpocIsAlready70Pct() {
        // total=1000, vpoc=40000(700) → 70% achieved with VPOC alone → vah=val=vpoc
        when(repo.findByTradeDateOrderByTimeAsc(any()))
            .thenReturn(List.of(
                tick("090000", 39900, 100),
                tick("090001", 40000, 700),
                tick("090002", 40100, 200)
            ));

        VpSummary s = service.getSummary(LocalDate.of(2026, 6, 5));

        assertThat(s.vpoc()).isEqualTo(40000);
        assertThat(s.vah()).isEqualTo(40000);
        assertThat(s.val()).isEqualTo(40000);
        assertThat(s.valueAreaPct()).isGreaterThanOrEqualTo(70.0);
    }

    @Test
    void getSummary_valueAreaExpandsBothDirections() {
        // 5 prices: 39800=50, 39900=200, 40000=400, 40100=200, 40200=50
        // total=900, vpoc=40000, target=630
        // Start: 400. up=200 vs down=200 → tie → take up → upper=40100, acc=600
        // Next: up=50 vs down=200 → down wins → lower=39900, acc=800 ≥ 630
        // vah=40100, val=39900
        when(repo.findByTradeDateOrderByTimeAsc(any()))
            .thenReturn(List.of(
                tick("090000", 39800,  50),
                tick("090001", 39900, 200),
                tick("090002", 40000, 400),
                tick("090003", 40100, 200),
                tick("090004", 40200,  50)
            ));

        VpSummary s = service.getSummary(LocalDate.of(2026, 6, 5));

        assertThat(s.vpoc()).isEqualTo(40000);
        assertThat(s.vah()).isEqualTo(40100);
        assertThat(s.val()).isEqualTo(39900);
    }

    @Test
    void getSummary_computesCloseVsVpoc() {
        when(repo.findByTradeDateOrderByTimeAsc(any()))
            .thenReturn(List.of(
                tick("090000", 40000, 100),
                tick("090001", 40000, 200),
                tick("134500", 39850,  50)
            ));

        VpSummary s = service.getSummary(LocalDate.of(2026, 6, 5));

        assertThat(s.closeVsVpoc()).isEqualTo(-150);
    }

    @Test
    void getSummary_returnsDateIn8DigitFormat() {
        when(repo.findByTradeDateOrderByTimeAsc(any()))
            .thenReturn(List.of(tick("090000", 40000, 100)));

        VpSummary s = service.getSummary(LocalDate.of(2026, 6, 5));

        assertThat(s.date()).isEqualTo("20260605");
    }

    // ── getProfile tests ──────────────────────────────────────────────────────

    @Test
    void getProfile_sortsPriceDescending() {
        when(repo.findByTradeDateOrderByTimeAsc(any()))
            .thenReturn(List.of(
                tick("090000", 40100, 100),
                tick("090001", 40300, 200),
                tick("090002", 40200, 150)
            ));

        List<VpEntry> profile = service.getProfile(LocalDate.of(2026, 6, 5), 1);

        assertThat(profile).extracting(VpEntry::price)
            .containsExactly(40300, 40200, 40100);
    }

    @Test
    void getProfile_marksPriceTypes() {
        // 40100=250(vpoc), 40200=100, 40300=100. total=450, target=315
        // Start: 250. up=100 → 350 ≥ 315. vah=40200, val=40100
        when(repo.findByTradeDateOrderByTimeAsc(any()))
            .thenReturn(List.of(
                tick("090000", 40100, 250),
                tick("090001", 40200, 100),
                tick("090002", 40300, 100)
            ));

        List<VpEntry> profile = service.getProfile(LocalDate.of(2026, 6, 5), 1);

        assertThat(profile.get(0).price()).isEqualTo(40300);
        assertThat(profile.get(0).type()).isEqualTo(PriceType.NORMAL);
        assertThat(profile.get(1).price()).isEqualTo(40200);
        assertThat(profile.get(1).type()).isEqualTo(PriceType.VAH);
        assertThat(profile.get(2).price()).isEqualTo(40100);
        assertThat(profile.get(2).type()).isEqualTo(PriceType.VPOC);
    }

    @Test
    void getProfile_marksThinNodes() {
        // total=2000, thin threshold < 2000*0.001=2 → volume=1 is THIN
        when(repo.findByTradeDateOrderByTimeAsc(any()))
            .thenReturn(List.of(
                tick("090000", 40100, 1998),
                tick("090001", 40200,    1),
                tick("090002", 40300,    1)
            ));

        List<VpEntry> profile = service.getProfile(LocalDate.of(2026, 6, 5), 1);

        assertThat(profile.get(0).type()).isEqualTo(PriceType.THIN); // 40300, vol=1
        assertThat(profile.get(1).type()).isEqualTo(PriceType.THIN); // 40200, vol=1
        assertThat(profile.get(2).type()).isEqualTo(PriceType.VPOC); // 40100
    }

    @Test
    void getProfile_bucketsWithStep50() {
        // 40151 → bucket 40150; 40183 → bucket 40150
        when(repo.findByTradeDateOrderByTimeAsc(any()))
            .thenReturn(List.of(
                tick("090000", 40151, 100),
                tick("090001", 40183, 200)
            ));

        List<VpEntry> profile = service.getProfile(LocalDate.of(2026, 6, 5), 50);

        assertThat(profile).hasSize(1);
        assertThat(profile.get(0).price()).isEqualTo(40150);
        assertThat(profile.get(0).volume()).isEqualTo(300);
    }

    @Test
    void getProfile_cumulativePctIncreasesHighToLow() {
        when(repo.findByTradeDateOrderByTimeAsc(any()))
            .thenReturn(List.of(
                tick("090000", 40100, 500),
                tick("090001", 40200, 500)
            ));

        List<VpEntry> profile = service.getProfile(LocalDate.of(2026, 6, 5), 1);

        // sorted desc: 40200 first (50%), 40100 second (100%)
        assertThat(profile.get(0).cumVolumePct()).isEqualTo(50.0);
        assertThat(profile.get(1).cumVolumePct()).isEqualTo(100.0);
    }

    // ── getAvailableDates test ────────────────────────────────────────────────

    @Test
    void getAvailableDates_returnsFormattedDatesDescending() {
        when(repo.findDistinctTradeDates())
            .thenReturn(List.of(
                LocalDate.of(2026, 6, 5),
                LocalDate.of(2026, 6, 4)
            ));

        List<String> dates = service.getAvailableDates();

        assertThat(dates).containsExactly("20260605", "20260604");
    }

    // ── getSessions tests ─────────────────────────────────────────────────────

    @Test
    void getSessions_classifiesSessionsCorrectly() {
        when(repo.findByTradeDateOrderByTimeAsc(any()))
            .thenReturn(List.of(
                tick("084500", 40000, 100),
                tick("085000", 40050, 200),
                tick("090000", 40100, 300),
                tick("130000", 40200, 150)
            ));

        SessionsResponse r = service.getSessions(LocalDate.of(2026, 6, 5));

        assertThat(r.open().vpoc()).isEqualTo(40050);
        assertThat(r.open().volume()).isEqualTo(300);
        assertThat(r.morning().vpoc()).isEqualTo(40100);
        assertThat(r.afternoon().vpoc()).isEqualTo(40200);
    }

    @Test
    void getSessions_returnsZeroVpocForEmptySession() {
        when(repo.findByTradeDateOrderByTimeAsc(any()))
            .thenReturn(List.of(tick("090000", 40100, 100)));

        SessionsResponse r = service.getSessions(LocalDate.of(2026, 6, 5));

        assertThat(r.open().vpoc()).isEqualTo(0);
        assertThat(r.afternoon().vpoc()).isEqualTo(0);
    }

    // ── getLargeTrades tests ──────────────────────────────────────────────────

    @Test
    void getLargeTrades_filtersAndSortsByVolumeDesc() {
        when(repo.findByTradeDateOrderByTimeAsc(any()))
            .thenReturn(List.of(
                tick("090000", 40000,  30),
                tick("090001", 40000, 100),
                tick("090002", 40000, 200)
            ));

        List<LargeTrade> trades = service.getLargeTrades(LocalDate.of(2026, 6, 5), 50);

        assertThat(trades).hasSize(2);
        assertThat(trades.get(0).volume()).isEqualTo(200);
        assertThat(trades.get(1).volume()).isEqualTo(100);
    }

    @Test
    void getLargeTrades_formatsTimeWithColons() {
        when(repo.findByTradeDateOrderByTimeAsc(any()))
            .thenReturn(List.of(tick("091530", 40000, 100)));

        List<LargeTrade> trades = service.getLargeTrades(LocalDate.of(2026, 6, 5), 50);

        assertThat(trades.get(0).time()).isEqualTo("09:15:30");
    }

    @Test
    void getLargeTrades_classifiesAtVpocZone() {
        // vpoc = 40000 (highest volume)
        when(repo.findByTradeDateOrderByTimeAsc(any()))
            .thenReturn(List.of(
                tick("090000", 40000, 300),
                tick("090001", 40100, 100),
                tick("090002", 39900, 100)
            ));

        List<LargeTrade> trades = service.getLargeTrades(LocalDate.of(2026, 6, 5), 50);

        assertThat(trades.get(0).zone()).isEqualTo(TradeZone.AT_VPOC);
    }

    // ── classifySession tests ─────────────────────────────────────────────────

    @Test
    void classifySession_boundaries() {
        assertThat(service.classifySession("084500")).isEqualTo("OPEN");
        assertThat(service.classifySession("085959")).isEqualTo("OPEN");
        assertThat(service.classifySession("090000")).isEqualTo("MORNING");
        assertThat(service.classifySession("115959")).isEqualTo("MORNING");
        assertThat(service.classifySession("120000")).isEqualTo("AFTERNOON");
        assertThat(service.classifySession("134500")).isEqualTo("AFTERNOON");
    }

    // ── getCandles tests ──────────────────────────────────────────────────────

    @Test
    void getCandles_aggregatesTicksIntoOhlcv() {
        when(repo.findByTradeDateOrderByTimeAsc(any()))
            .thenReturn(List.of(
                tick("090000", 40100, 100),
                tick("090001", 40200, 200),
                tick("090130", 40050,  50)
            ));

        List<VpCandle> candles = service.getCandles(LocalDate.of(2026, 6, 5), 5);

        assertThat(candles).hasSize(1);
        VpCandle c = candles.get(0);
        assertThat(c.open()).isEqualTo(40100);
        assertThat(c.high()).isEqualTo(40200);
        assertThat(c.low()).isEqualTo(40050);
        assertThat(c.close()).isEqualTo(40050);
        assertThat(c.volume()).isEqualTo(350);
    }

    @Test
    void getCandles_splitsAtIntervalBoundary() {
        when(repo.findByTradeDateOrderByTimeAsc(any()))
            .thenReturn(List.of(
                tick("090000", 40100, 100),
                tick("090500", 40200, 200),
                tick("090600", 40300,  50)
            ));

        List<VpCandle> candles = service.getCandles(LocalDate.of(2026, 6, 5), 5);

        assertThat(candles).hasSize(2);
        assertThat(candles.get(0).open()).isEqualTo(40100);
        assertThat(candles.get(0).close()).isEqualTo(40100);
        assertThat(candles.get(1).open()).isEqualTo(40200);
        assertThat(candles.get(1).close()).isEqualTo(40300);
    }

    @Test
    void getCandles_excludesAuctionTicks() {
        when(repo.findByTradeDateOrderByTimeAsc(any()))
            .thenReturn(List.of(
                auction("084500", 40000, 500),
                tick("090000", 40100, 100)
            ));

        List<VpCandle> candles = service.getCandles(LocalDate.of(2026, 6, 5), 5);

        assertThat(candles).hasSize(1);
        assertThat(candles.get(0).open()).isEqualTo(40100);
    }

    @Test
    void getCandles_timeIsUtcEpochSeconds() {
        when(repo.findByTradeDateOrderByTimeAsc(any()))
            .thenReturn(List.of(tick("090000", 40100, 100)));

        List<VpCandle> candles = service.getCandles(LocalDate.of(2026, 6, 5), 5);

        long epochDay = LocalDate.of(2026, 6, 5).toEpochDay();
        long expected = epochDay * 86400L + 9 * 3600L - 8 * 3600L;
        assertThat(candles.get(0).time()).isEqualTo(expected);
    }

    @Test
    void getCandles_interval1MinBucketsEachMinute() {
        when(repo.findByTradeDateOrderByTimeAsc(any()))
            .thenReturn(List.of(
                tick("090000", 40100, 10),
                tick("090045", 40150, 20),
                tick("090100", 40200, 30)
            ));

        List<VpCandle> candles = service.getCandles(LocalDate.of(2026, 6, 5), 1);

        assertThat(candles).hasSize(2);
        assertThat(candles.get(0).volume()).isEqualTo(30);
        assertThat(candles.get(1).volume()).isEqualTo(30);
    }
}
