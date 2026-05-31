package com.eagleeye.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(
    name = "taiex_index",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_taiex_index_trade_date",
        columnNames = {"trade_date"}
    )
)
public class TaiexIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = LocalDateToIsoStringConverter.class)
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    // OHLC stored as fixed-point integer (index points × 100)
    // e.g. 20234.56 is stored as 2023456
    @Column(name = "open")
    private Long open;

    @Column(name = "high")
    private Long high;

    @Column(name = "low")
    private Long low;

    @Column(name = "close")
    private Long close;

    @Column(name = "volume")
    private Long volume;

    @Column(name = "turnover")
    private Long turnover;

    protected TaiexIndex() {}

    public TaiexIndex(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    public Long getId()             { return id; }
    public LocalDate getTradeDate() { return tradeDate; }
    public Long getOpen()           { return open; }
    public Long getHigh()           { return high; }
    public Long getLow()            { return low; }
    public Long getClose()          { return close; }
    public Long getVolume()         { return volume; }
    public Long getTurnover()       { return turnover; }

    public void setOpen(Long v)     { this.open = v; }
    public void setHigh(Long v)     { this.high = v; }
    public void setLow(Long v)      { this.low = v; }
    public void setClose(Long v)    { this.close = v; }
    public void setVolume(Long v)   { this.volume = v; }
    public void setTurnover(Long v) { this.turnover = v; }
}
