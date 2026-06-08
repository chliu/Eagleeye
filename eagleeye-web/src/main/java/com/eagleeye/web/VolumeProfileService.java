package com.eagleeye.web;

import com.eagleeye.domain.entity.TxTick;
import com.eagleeye.domain.repository.TxTickRepository;
import com.eagleeye.web.vp.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VolumeProfileService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private record ValueArea(int vpoc, int vah, int val, int vaVolume) {}

    private final TxTickRepository repo;

    public VolumeProfileService(TxTickRepository repo) {
        this.repo = repo;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public List<String> getAvailableDates() {
        return repo.findDistinctTradeDates().stream()
                .map(d -> d.format(DATE_FMT))
                .toList();
    }

    public VpSummary getSummary(LocalDate date) {
        List<TxTick> ticks = loadTicks(date);
        int totalVolume = totalVolume(ticks);
        NavigableMap<Integer, Integer> profile = buildProfile(ticks, 1);
        int vpoc = calcVpoc(profile);
        ValueArea va = calcValueArea(profile, vpoc);

        int open  = ticks.get(0).getPrice();
        int close = ticks.get(ticks.size() - 1).getPrice();
        int high  = ticks.stream().mapToInt(TxTick::getPrice).max().orElseThrow();
        int low   = ticks.stream().mapToInt(TxTick::getPrice).min().orElseThrow();

        double vaPercent = Math.round((double) va.vaVolume() / totalVolume * 1000) / 10.0;

        return new VpSummary(
                date.format(DATE_FMT), "TX",
                open, close, high, low,
                totalVolume, vpoc, profile.get(vpoc),
                va.vah(), va.val(),
                vaPercent,
                close - vpoc
        );
    }

    public List<VpCandle> getCandles(LocalDate date, int intervalMinutes) {
        List<TxTick> ticks = loadTicks(date);
        TreeMap<Long, VpCandle> map = new TreeMap<>();
        for (TxTick t : ticks) {
            long key = toEpochSec(date, t.getTime(), intervalMinutes);
            VpCandle prev = map.get(key);
            if (prev == null) {
                map.put(key, new VpCandle(key,
                    t.getPrice(), t.getPrice(), t.getPrice(), t.getPrice(), t.getVolume()));
            } else {
                map.put(key, new VpCandle(key,
                    prev.open(),
                    Math.max(prev.high(), t.getPrice()),
                    Math.min(prev.low(),  t.getPrice()),
                    t.getPrice(),
                    prev.volume() + t.getVolume()));
            }
        }
        return new ArrayList<>(map.values());
    }

    private long toEpochSec(LocalDate date, String hhmmss, int intervalMinutes) {
        int h = Integer.parseInt(hhmmss.substring(0, 2));
        int m = Integer.parseInt(hhmmss.substring(2, 4));
        int s = Integer.parseInt(hhmmss.substring(4, 6));
        int totalSec = h * 3600 + m * 60 + s;
        int bucketSec = (totalSec / (intervalMinutes * 60)) * (intervalMinutes * 60);
        return date.toEpochDay() * 86400L + bucketSec - 8 * 3600L;
    }

    public List<VpEntry> getProfile(LocalDate date, int step) {
        List<TxTick> ticks = loadTicks(date);
        int totalVolume = totalVolume(ticks);

        NavigableMap<Integer, Integer> rawProfile = buildProfile(ticks, 1);
        int vpoc = calcVpoc(rawProfile);
        ValueArea va = calcValueArea(rawProfile, vpoc);

        NavigableMap<Integer, Integer> displayProfile = (step <= 1) ? rawProfile : buildProfile(ticks, step);
        int vpocBucket = bucket(vpoc, step);
        int vahBucket  = bucket(va.vah(), step);
        int valBucket  = bucket(va.val(), step);

        List<VpEntry> result = new ArrayList<>();
        int cumVolume = 0;

        for (Map.Entry<Integer, Integer> e : displayProfile.descendingMap().entrySet()) {
            int price  = e.getKey();
            int volume = e.getValue();
            cumVolume += volume;

            PriceType type;
            if (price == vpocBucket)     type = PriceType.VPOC;
            else if (price == vahBucket) type = PriceType.VAH;
            else if (price == valBucket) type = PriceType.VAL;
            else if (isThin(volume, totalVolume)) type = PriceType.THIN;
            else type = PriceType.NORMAL;

            boolean inVA  = price >= valBucket && price <= vahBucket;
            double cumPct = Math.round((double) cumVolume / totalVolume * 1000) / 10.0;

            result.add(new VpEntry(price, volume, type, inVA, cumPct));
        }
        return result;
    }

    public SessionsResponse getSessions(LocalDate date) {
        List<TxTick> ticks = loadTicks(date);
        Map<String, List<TxTick>> bySession = ticks.stream()
                .collect(Collectors.groupingBy(t -> classifySession(t.getTime())));

        return new SessionsResponse(
                buildSessionVpoc(bySession.getOrDefault("OPEN",      List.of()), "08:45-09:00"),
                buildSessionVpoc(bySession.getOrDefault("MORNING",   List.of()), "09:00-12:00"),
                buildSessionVpoc(bySession.getOrDefault("AFTERNOON", List.of()), "12:00-13:45")
        );
    }

    public List<LargeTrade> getLargeTrades(LocalDate date, int threshold) {
        List<TxTick> ticks = loadTicks(date);
        NavigableMap<Integer, Integer> profile = buildProfile(ticks, 1);
        int vpoc = calcVpoc(profile);
        ValueArea va = calcValueArea(profile, vpoc);
        List<TradeDirection> directions = calcDirections(ticks);

        List<LargeTrade> result = new ArrayList<>();
        for (int i = 0; i < ticks.size(); i++) {
            TxTick t = ticks.get(i);
            if (t.getVolume() < threshold) continue;
            result.add(new LargeTrade(
                formatTime(t.getTime()),
                t.getPrice(),
                t.getVolume(),
                classifySession(t.getTime()),
                t.getPrice() - vpoc,
                classifyZone(t.getPrice(), vpoc, va.vah(), va.val()),
                directions.get(i)
            ));
        }
        result.sort(Comparator.comparingInt(LargeTrade::volume).reversed());
        return result;
    }

    List<TradeDirection> calcDirections(List<TxTick> ticks) {
        List<TradeDirection> dirs = new ArrayList<>(ticks.size());
        TradeDirection last = TradeDirection.NEUTRAL;
        dirs.add(last);
        for (int i = 1; i < ticks.size(); i++) {
            int curr = ticks.get(i).getPrice();
            int prev = ticks.get(i - 1).getPrice();
            if      (curr > prev) last = TradeDirection.UP;
            else if (curr < prev) last = TradeDirection.DOWN;
            // curr == prev: zero-tick rule — 繼承上次方向，不重設
            dirs.add(last);
        }
        return dirs;
    }

    public TradingPlan getPlan(LocalDate date) {
        List<TxTick> ticks = loadTicks(date);
        int totalVolume = totalVolume(ticks);
        NavigableMap<Integer, Integer> profile1  = buildProfile(ticks, 1);
        NavigableMap<Integer, Integer> profile50 = buildProfile(ticks, 50);
        int vpoc = calcVpoc(profile1);
        ValueArea va = calcValueArea(profile1, vpoc);

        int open  = ticks.get(0).getPrice();
        int close = ticks.get(ticks.size() - 1).getPrice();
        int high  = ticks.stream().mapToInt(TxTick::getPrice).max().orElseThrow();
        int low   = ticks.stream().mapToInt(TxTick::getPrice).min().orElseThrow();
        double vaPercent = Math.round((double) va.vaVolume() / totalVolume * 1000) / 10.0;

        VpSummary summary = new VpSummary(
                date.format(DATE_FMT), "TX",
                open, close, high, low,
                totalVolume, vpoc, profile1.get(vpoc),
                va.vah(), va.val(), vaPercent, close - vpoc
        );
        // S/R levels 用 step-50 profile：每個節點代表 50 點區間，避免相鄰單點被誤選為支撐
        return generatePlan(summary, profile50);
    }

    // ── Private calculations ──────────────────────────────────────────────────

    List<TxTick> loadTicks(LocalDate date) {
        return repo.findByTradeDateOrderByTimeAsc(date)
                .stream().filter(t -> !t.isAuction()).toList();
    }

    NavigableMap<Integer, Integer> buildProfile(List<TxTick> ticks, int step) {
        TreeMap<Integer, Integer> result = new TreeMap<>();
        for (TxTick t : ticks) {
            int p = bucket(t.getPrice(), step);
            result.merge(p, t.getVolume(), Integer::sum);
        }
        return result;
    }

    int calcVpoc(Map<Integer, Integer> profile) {
        return profile.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow();
    }

    ValueArea calcValueArea(NavigableMap<Integer, Integer> profile, int vpoc) {
        int totalVolume = profile.values().stream().mapToInt(Integer::intValue).sum();
        int target = (int) Math.ceil(totalVolume * 0.70);

        List<Integer> prices = new ArrayList<>(profile.keySet()); // ascending
        int vpocIdx = prices.indexOf(vpoc);

        int upper = vpocIdx;
        int lower = vpocIdx;
        int accumulated = profile.get(vpoc);

        while (accumulated < target) {
            int upVol   = (upper < prices.size() - 1) ? profile.get(prices.get(upper + 1)) : 0;
            int downVol = (lower > 0)                 ? profile.get(prices.get(lower - 1)) : 0;
            if (upVol == 0 && downVol == 0) break;
            if (upVol >= downVol) { upper++; accumulated += upVol; }
            else                  { lower--; accumulated += downVol; }
        }

        return new ValueArea(vpoc, prices.get(upper), prices.get(lower), accumulated);
    }

    boolean isThin(int volume, int totalVolume) {
        return volume < totalVolume * 0.001;
    }

    String classifySession(String time) {
        int t = Integer.parseInt(time);
        if (t >= 84500  && t <= 85959)  return "OPEN";
        if (t >= 90000  && t <= 115959) return "MORNING";
        if (t >= 120000 && t <= 134500) return "AFTERNOON";
        return "OTHER";
    }

    private int bucket(int price, int step) {
        return (step <= 1) ? price : (price / step) * step;
    }

    private int totalVolume(List<TxTick> ticks) {
        return ticks.stream().mapToInt(TxTick::getVolume).sum();
    }

    private SessionVpocData buildSessionVpoc(List<TxTick> ticks, String timeRange) {
        if (ticks.isEmpty()) return new SessionVpocData(timeRange, 0, 0);
        NavigableMap<Integer, Integer> profile = buildProfile(ticks, 1);
        return new SessionVpocData(timeRange, calcVpoc(profile), totalVolume(ticks));
    }

    private String formatTime(String time) {
        return time.substring(0, 2) + ":" + time.substring(2, 4) + ":" + time.substring(4, 6);
    }

    private TradeZone classifyZone(int price, int vpoc, int vah, int val) {
        if (price == vpoc) return TradeZone.AT_VPOC;
        if (price > vah)   return TradeZone.ABOVE_VAH;
        if (price < val)   return TradeZone.BELOW_VAL;
        return TradeZone.IN_VA;
    }

    // ── Plan generation ───────────────────────────────────────────────────────

    private TradingPlan generatePlan(VpSummary s, NavigableMap<Integer, Integer> profile) {
        int vpoc  = s.vpoc();
        int vah   = s.vah();
        int val   = s.val();
        int close = s.close();

        List<Integer> sortedVols = new ArrayList<>(profile.values());
        Collections.sort(sortedVols);
        int volThreshold = sortedVols.get((int) (sortedVols.size() * 0.85));

        int resistance1 = firstHighVolAbove(profile, vpoc, vah, volThreshold);
        int resistance2 = firstHighVolAbove(profile, vah, Integer.MAX_VALUE, volThreshold);

        int support1 = (close >= val)
                ? lastHighVolBelow(profile, val, vpoc, volThreshold)
                : lastHighVolBelow(profile, 0, close, volThreshold);
        int support2 = (support1 > 0)
                ? lastHighVolBelow(profile, 0, support1, volThreshold)
                : 0;

        Map<String, PriceLevel> levels = new LinkedHashMap<>();
        if (resistance2 > 0) levels.put("resistance2", new PriceLevel(resistance2, "稀薄區上界", profile.getOrDefault(resistance2, 0)));
        if (resistance1 > 0) levels.put("resistance1", new PriceLevel(resistance1, "高量節點",   profile.getOrDefault(resistance1, 0)));
        levels.put("vah",  new PriceLevel(vah,  "VAH 價值區上緣", profile.getOrDefault(bucket(vah,  50), 0)));
        levels.put("vpoc", new PriceLevel(vpoc, vpocLabel(s),    profile.getOrDefault(bucket(vpoc, 50), 0)));
        levels.put("val",  new PriceLevel(val,  "VAL 多空分界",  profile.getOrDefault(bucket(val,  50), 0)));
        if (support1 > 0) levels.put("support1", new PriceLevel(support1, "支撐一", profile.getOrDefault(support1, 0)));
        if (support2 > 0) levels.put("support2", new PriceLevel(support2, "支撐二", profile.getOrDefault(support2, 0)));

        return new TradingPlan(levels, generateScenarios(s, support1, support2), generateKeyMessage(s));
    }

    private int firstHighVolAbove(NavigableMap<Integer, Integer> profile, int floor, int ceiling, int volThreshold) {
        NavigableMap<Integer, Integer> sub = (ceiling == Integer.MAX_VALUE)
                ? profile.tailMap(floor, false)
                : profile.subMap(floor, false, ceiling, false);
        return sub.entrySet().stream()
                .filter(e -> e.getValue() >= volThreshold)
                .mapToInt(Map.Entry::getKey)
                .findFirst()
                .orElse(0);
    }

    private int lastHighVolBelow(NavigableMap<Integer, Integer> profile, int floor, int ceiling, int volThreshold) {
        if (floor >= ceiling) return 0;
        return profile.subMap(floor, false, ceiling, false)
                .descendingMap().entrySet().stream()
                .filter(e -> e.getValue() >= volThreshold)
                .mapToInt(Map.Entry::getKey)
                .findFirst()
                .orElse(0);
    }

    private String vpocLabel(VpSummary s) {
        return (s.close() < s.val() || s.close() > s.vah()) ? "VPOC 懸空" : "VPOC";
    }

    private List<Scenario> generateScenarios(VpSummary s, int support1, int support2) {
        int vpoc = s.vpoc();
        int vah  = s.vah();
        int val  = s.val();
        int s1   = support1 > 0 ? support1 : val - 100;
        int s2   = support2 > 0 ? support2 : val - 200;

        return List.of(
            new Scenario(1, "開盤站回 VA 內",
                String.format("開盤 > %,d（VAL）", val), "BULLISH",
                String.format("回測 VAL %,d 不破後進多", val),
                List.of(String.format("%,d（VPOC）", vpoc), String.format("%,d（VAH）", vah)),
                String.format("跌破 %,d", val - 50),
                "Open in Value 結構，市場回到共識區，VPOC 磁吸力道強"),

            new Scenario(2, "測試 VAL 二次確認",
                String.format("開盤在 %,d～%,d 之間", s1, val), "NEUTRAL",
                String.format("第一次測試 VAL 失敗，支撐一 %,d 守穩後二次突破 VAL 再進多", s1),
                List.of(String.format("%,d（VPOC）", vpoc)),
                String.format("跌破 %,d", s1),
                "失衡後回補邏輯，需二次確認避免假突破"),

            new Scenario(3, String.format("%,d～%,d 之間橫盤", s1, val),
                "低量震盪，方向不明", "NEUTRAL", "不操作，等方向確認",
                List.of(String.format("放量過 %,d 做多", val), String.format("跌破 %,d 轉空", s1)),
                "依突破方向設定",
                "多空拉鋸帶，低量橫盤無意義，等市場給答案"),

            new Scenario(4, String.format("跌破支撐一 %,d", s1),
                String.format("有效跌破 %,d，測試支撐二 %,d", s1, s2), "BEARISH",
                String.format("等 %,d 量能承接確認後做多或持空", s2),
                List.of(String.format("空方：%,d", s2 - 50), String.format("多方反彈：%,d", s1)),
                String.format("多方：%,d / 空方：%,d", s2 - 50, val),
                String.format("支撐二 %,d 有大量才是真撐", s2)),

            new Scenario(5, String.format("跌破支撐二 %,d", s2),
                String.format("%,d 無量承接，快速穿越", s2), "STRONG_BEARISH",
                String.format("不接刀，等反彈至 %,d～%,d 做空", s1, val),
                List.of(String.format("%,d", s2 - 100), String.format("%,d", s2 - 200)),
                String.format("反彈收回 %,d 以上", val),
                "兩條支撐失守代表結構轉空，市場重新定價")
        );
    }

    private String generateKeyMessage(VpSummary s) {
        int absVsVpoc = Math.abs(s.closeVsVpoc());
        String dir = s.closeVsVpoc() >= 0 ? "上方" : "下方";
        boolean suspended = s.close() < s.val() || s.close() > s.vah();
        String vpocStatus = suspended ? "懸空，具強烈磁吸力道" : "在 VA 內";
        return String.format("多空分界：VAL %,d。收盤在 VPOC %s %d 點，VPOC %,d %s。能否收復 VAL 是明日核心問題。",
                s.val(), dir, absVsVpoc, s.vpoc(), vpocStatus);
    }
}
