package com.eagleeye.shell.formatter;

import com.eagleeye.domain.entity.FuturesAhPosition;
import com.eagleeye.domain.entity.FuturesPosition;
import com.eagleeye.domain.entity.InstitutionalFlow;
import com.eagleeye.domain.entity.MarginTransaction;
import com.eagleeye.domain.entity.OptionsPosition;
import com.eagleeye.domain.entity.TaiexIndex;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders position data as Unicode box-drawing tables.
 *
 * List view  (one date, all contracts):
 *   ┌──────────┬──────────────────┬─────────────┬─────────────┬─────────────┬─────────────┬─────────────┬─────────────┐
 *   │ Contract │ Trader           │    Trd Long │   Trd Short │     Trd Net │     OI Long │    OI Short │      OI Net │
 *   ├──────────┼──────────────────┼─────────────┼─────────────┼─────────────┼─────────────┼─────────────┼─────────────┤
 *   │ TX       │ DEALER           │      20,634 │      18,585 │      +2,049 │      10,099 │       5,096 │      +5,003 │
 *   │          │ INVESTMENT_TRUST │         100 │         200 │        -100 │          50 │         150 │        -100 │
 *   │          │ FINI             │       5,000 │       4,000 │      +1,000 │       2,000 │       1,500 │        +500 │
 *   ├──────────┼──────────────────┼─────────────┼─────────────┼─────────────┼─────────────┼─────────────┼─────────────┤
 *   │ MTX      │ DEALER           │         ... │         ... │         ... │         ... │         ... │         ... │
 *
 * Trend view (one contract, multiple dates):
 *   ┌────────────┬──────────────────┬─────────────┬─────────────┬─────────────┬─────────────┬─────────────┬─────────────┐
 *   │ Date       │ Trader           │    Trd Long │   Trd Short │     Trd Net │     OI Long │    OI Short │      OI Net │
 */
@Component
public class TableFormatter {

    private static final NumberFormat NF = NumberFormat.getNumberInstance(Locale.US);

    // Column widths (inner content, excluding │ padding)
    private static final int W_CONTRACT  = 10;
    private static final int W_DATE      = 10;
    private static final int W_TRADER    = 16;
    private static final int W_NUM       = 11;  // e.g. "+132,293"
    private static final int W_OHLC      = 12;  // e.g. "29,349.81"
    private static final int W_VOLUME    = 14;  // e.g. "10,724,729,528"
    private static final int W_TURNOVER  = 16;  // e.g. "669,781,989,470"
    private static final int W_MARGIN    = 11;  // e.g. "8,109,024" (9 chars)
    private static final int W_FLOW      = 17;  // e.g. "+123,456,789,012" (15 chars + sign + space)

    // Box-drawing characters
    private static final char H  = '─';
    private static final char V  = '│';
    private static final char TL = '┌', TR = '┐', BL = '└', BR = '┘';
    private static final char LT = '├', RT = '┤', TT = '┬', BT = '┴', CR = '┼';

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** One date, all contracts (list view). Shows Contract + Trader columns. */
    public String formatPositions(List<? extends Object> positions, boolean isFutures) {
        if (positions.isEmpty()) return "No data found.";

        int[] widths = {W_CONTRACT, W_TRADER, W_NUM, W_NUM, W_NUM, W_NUM, W_NUM, W_NUM};
        String[] headers = {"Contract", "Trader", "Trd Long", "Trd Short", "Trd Net", "OI Long", "OI Short", "OI Net"};

        List<Row> rows = new ArrayList<>();
        String lastContract = null;

        for (Object p : positions) {
            String contract = contract(p);
            String trader   = trader(p);
            boolean newGroup = !contract.equals(lastContract);

            if (newGroup && lastContract != null) {
                rows.add(Row.divider());
            }
            rows.add(Row.data(
                    newGroup ? contract : "",
                    trader,
                    fmtVol(tradingLong(p)),
                    fmtVol(tradingShort(p)),
                    fmtNet(tradingNet(p)),
                    fmtVol(oiLong(p)),
                    fmtVol(oiShort(p)),
                    fmtNet(oiNet(p))
            ));
            lastContract = contract;
        }

        return renderTable(headers, widths, rows, 2);
    }

    /** TAIEX daily bars over a date range. Columns: Date, Open, High, Low, Close, Volume, Turnover. */
    public String formatMarketIndex(List<TaiexIndex> bars) {
        if (bars.isEmpty()) return "No data found.";

        int[] widths  = {W_DATE, W_OHLC, W_OHLC, W_OHLC, W_OHLC, W_VOLUME, W_TURNOVER};
        String[] headers = {"Date", "Open", "High", "Low", "Close", "Volume", "Turnover"};

        List<Row> rows = new ArrayList<>();
        for (TaiexIndex b : bars) {
            rows.add(Row.data(
                    b.getTradeDate().toString(),
                    fmtOhlc(b.getOpen()),
                    fmtOhlc(b.getHigh()),
                    fmtOhlc(b.getLow()),
                    fmtOhlc(b.getClose()),
                    fmtVol(b.getVolume()),
                    fmtVol(b.getTurnover())
            ));
        }
        return renderTable(headers, widths, rows, 1);
    }

    /** Taiwan market-wide margin transaction daily summary. Columns: Date + 8 numeric. */
    public String formatMarginTransaction(List<MarginTransaction> bars) {
        if (bars.isEmpty()) return "No data found.";

        int[] widths  = {W_DATE, W_MARGIN, W_MARGIN, W_MARGIN, W_MARGIN, W_MARGIN, W_MARGIN, W_MARGIN, W_MARGIN};
        String[] headers = {"Date", "M-Buy", "M-Sell", "M-Redeem", "M-Bal", "S-Cover", "S-Sell", "S-Redeem", "S-Bal"};

        List<Row> rows = new ArrayList<>();
        for (MarginTransaction b : bars) {
            rows.add(Row.data(
                    b.getTradeDate().toString(),
                    fmtVol(b.getMarginPurchase()),
                    fmtVol(b.getMarginSale()),
                    fmtVol(b.getMarginCashRedemption()),
                    fmtVol(b.getMarginBalance()),
                    fmtVol(b.getShortCovering()),
                    fmtVol(b.getShortSale()),
                    fmtVol(b.getShortStockRedemption()),
                    fmtVol(b.getShortBalance())
            ));
        }
        return renderTable(headers, widths, rows, 1);
    }

    /** Institutional investor daily trading values. Columns: Date + 9 numeric. */
    public String formatInstitutionalFlow(List<InstitutionalFlow> flows) {
        if (flows.isEmpty()) return "No data found.";

        int[] widths = {W_DATE, W_FLOW, W_FLOW, W_FLOW, W_FLOW, W_FLOW, W_FLOW, W_FLOW, W_FLOW, W_FLOW};
        String[] headers = {"Date", "F-Buy", "F-Sell", "F-Net", "IT-Buy", "IT-Sell", "IT-Net", "D-Buy", "D-Sell", "D-Net"};

        List<Row> rows = new ArrayList<>();
        for (InstitutionalFlow f : flows) {
            rows.add(Row.data(
                    f.getTradeDate().toString(),
                    fmtVol(f.getForeignBuy()),
                    fmtVol(f.getForeignSell()),
                    fmtNet(f.getForeignNet()),
                    fmtVol(f.getInvestmentTrustBuy()),
                    fmtVol(f.getInvestmentTrustSell()),
                    fmtNet(f.getInvestmentTrustNet()),
                    fmtVol(f.getDealerBuy()),
                    fmtVol(f.getDealerSell()),
                    fmtNet(f.getDealerNet())
            ));
        }
        return renderTable(headers, widths, rows, 1);
    }

    /** One contract over a date range (trend view). Shows Date + Trader columns. */
    public String formatTrend(List<? extends Object> positions) {
        if (positions.isEmpty()) return "No data found.";

        int[] widths = {W_DATE, W_TRADER, W_NUM, W_NUM, W_NUM, W_NUM, W_NUM, W_NUM};
        String[] headers = {"Date", "Trader", "Trd Long", "Trd Short", "Trd Net", "OI Long", "OI Short", "OI Net"};

        List<Row> rows = new ArrayList<>();
        String lastDate = null;

        for (Object p : positions) {
            String date   = tradeDate(p);
            String trader = trader(p);
            boolean newGroup = !date.equals(lastDate);

            if (newGroup && lastDate != null) {
                rows.add(Row.divider());
            }
            rows.add(Row.data(
                    newGroup ? date : "",
                    trader,
                    fmtVol(tradingLong(p)),
                    fmtVol(tradingShort(p)),
                    fmtNet(tradingNet(p)),
                    fmtVol(oiLong(p)),
                    fmtVol(oiShort(p)),
                    fmtNet(oiNet(p))
            ));
            lastDate = date;
        }

        return renderTable(headers, widths, rows, 2);
    }

    // -----------------------------------------------------------------------
    // Table rendering engine
    // -----------------------------------------------------------------------

    private String renderTable(String[] headers, int[] widths, List<Row> rows, int numTextCols) {
        StringBuilder sb = new StringBuilder();

        sb.append(borderLine(TL, TT, H, TR, widths)).append('\n');
        sb.append(dataLine(headers, widths, numTextCols)).append('\n');
        sb.append(borderLine(LT, CR, H, RT, widths)).append('\n');

        for (Row row : rows) {
            if (row.isDivider) {
                sb.append(borderLine(LT, CR, H, RT, widths)).append('\n');
            } else {
                sb.append(dataLine(row.cells, widths, numTextCols)).append('\n');
            }
        }

        sb.append(borderLine(BL, BT, H, BR, widths)).append('\n');
        return sb.toString();
    }

    private String borderLine(char left, char mid, char fill, char right, int[] widths) {
        StringBuilder sb = new StringBuilder();
        sb.append(left);
        for (int i = 0; i < widths.length; i++) {
            sb.append(String.valueOf(fill).repeat(widths[i] + 2)); // +2 for spaces
            sb.append(i < widths.length - 1 ? mid : right);
        }
        return sb.toString();
    }

    private String dataLine(String[] cells, int[] widths, int numTextCols) {
        StringBuilder sb = new StringBuilder();
        sb.append(V);
        for (int i = 0; i < widths.length; i++) {
            String cell = cells[i];
            // First numTextCols columns left-aligned, numeric columns right-aligned
            String fmt = i < numTextCols ? " %-" + widths[i] + "s " : " %" + widths[i] + "s ";
            sb.append(String.format(fmt, truncate(cell, widths[i])));
            sb.append(V);
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Reflection-free field accessors via instanceof
    // -----------------------------------------------------------------------

    private String contract(Object p) {
        if (p instanceof FuturesPosition f)   return f.getContract();
        if (p instanceof FuturesAhPosition f) return f.getContract();
        if (p instanceof OptionsPosition o)   return o.getContract();
        return "";
    }

    private String tradeDate(Object p) {
        if (p instanceof FuturesPosition f)   return f.getTradeDate().toString();
        if (p instanceof FuturesAhPosition f) return f.getTradeDate().toString();
        if (p instanceof OptionsPosition o)   return o.getTradeDate().toString();
        return "";
    }

    private String trader(Object p) {
        if (p instanceof FuturesPosition f)   return f.getTraderType().name();
        if (p instanceof FuturesAhPosition f) return f.getTraderType().name();
        if (p instanceof OptionsPosition o)   return o.getTraderType().name();
        return "";
    }

    private Long tradingLong(Object p) {
        if (p instanceof FuturesPosition f)   return f.getTradingLongVolume();
        if (p instanceof FuturesAhPosition f) return f.getTradingLongVolume();
        return ((OptionsPosition) p).getTradingLongVolume();
    }

    private Long tradingShort(Object p) {
        if (p instanceof FuturesPosition f)   return f.getTradingShortVolume();
        if (p instanceof FuturesAhPosition f) return f.getTradingShortVolume();
        return ((OptionsPosition) p).getTradingShortVolume();
    }

    private Long tradingNet(Object p) {
        if (p instanceof FuturesPosition f)   return f.getTradingNetVolume();
        if (p instanceof FuturesAhPosition f) return f.getTradingNetVolume();
        return ((OptionsPosition) p).getTradingNetVolume();
    }

    private Long oiLong(Object p) {
        if (p instanceof FuturesPosition f)   return f.getOiLongVolume();
        if (p instanceof FuturesAhPosition f) return f.getOiLongVolume();
        return ((OptionsPosition) p).getOiLongVolume();
    }

    private Long oiShort(Object p) {
        if (p instanceof FuturesPosition f)   return f.getOiShortVolume();
        if (p instanceof FuturesAhPosition f) return f.getOiShortVolume();
        return ((OptionsPosition) p).getOiShortVolume();
    }

    private Long oiNet(Object p) {
        if (p instanceof FuturesPosition f)   return f.getOiNetVolume();
        if (p instanceof FuturesAhPosition f) return f.getOiNetVolume();
        return ((OptionsPosition) p).getOiNetVolume();
    }

    // -----------------------------------------------------------------------
    // Number formatting
    // -----------------------------------------------------------------------

    private String fmtOhlc(Long v) {
        if (v == null) return "-";
        BigDecimal decimal = BigDecimal.valueOf(v).movePointLeft(2);
        return NF.format(decimal);
    }

    private String fmtVol(Long v) {
        return v == null ? "-" : NF.format(v);
    }

    private String fmtNet(Long v) {
        if (v == null) return "-";
        return (v >= 0 ? "+" : "") + NF.format(v);
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    // -----------------------------------------------------------------------
    // Internal row type
    // -----------------------------------------------------------------------

    private static class Row {
        final boolean isDivider;
        final String[] cells;

        private Row(boolean isDivider, String[] cells) {
            this.isDivider = isDivider;
            this.cells = cells;
        }

        static Row divider() { return new Row(true, null); }
        static Row data(String... cells) { return new Row(false, cells); }
    }
}
