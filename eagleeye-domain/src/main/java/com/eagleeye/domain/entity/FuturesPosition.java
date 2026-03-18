package com.eagleeye.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(
    name = "futures_position",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_futures_position",
        columnNames = {"trade_date", "contract", "trader_type"}
    )
)
public class FuturesPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "contract", nullable = false, length = 10)
    private String contract;

    @Enumerated(EnumType.STRING)
    @Column(name = "trader_type", nullable = false, length = 20)
    private TraderType traderType;

    // Trading Volume & Value
    @Column(name = "trading_long_volume")
    private Long tradingLongVolume;

    @Column(name = "trading_long_value")
    private Long tradingLongValue;

    @Column(name = "trading_short_volume")
    private Long tradingShortVolume;

    @Column(name = "trading_short_value")
    private Long tradingShortValue;

    @Column(name = "trading_net_volume")
    private Long tradingNetVolume;

    @Column(name = "trading_net_value")
    private Long tradingNetValue;

    // Open Interest Volume & Value
    @Column(name = "oi_long_volume")
    private Long oiLongVolume;

    @Column(name = "oi_long_value")
    private Long oiLongValue;

    @Column(name = "oi_short_volume")
    private Long oiShortVolume;

    @Column(name = "oi_short_value")
    private Long oiShortValue;

    @Column(name = "oi_net_volume")
    private Long oiNetVolume;

    @Column(name = "oi_net_value")
    private Long oiNetValue;

    protected FuturesPosition() {}

    public FuturesPosition(LocalDate tradeDate, String contract, TraderType traderType) {
        this.tradeDate = tradeDate;
        this.contract = contract;
        this.traderType = traderType;
    }

    public Long getId() { return id; }
    public LocalDate getTradeDate() { return tradeDate; }
    public String getContract() { return contract; }
    public TraderType getTraderType() { return traderType; }
    public Long getTradingLongVolume() { return tradingLongVolume; }
    public Long getTradingLongValue() { return tradingLongValue; }
    public Long getTradingShortVolume() { return tradingShortVolume; }
    public Long getTradingShortValue() { return tradingShortValue; }
    public Long getTradingNetVolume() { return tradingNetVolume; }
    public Long getTradingNetValue() { return tradingNetValue; }
    public Long getOiLongVolume() { return oiLongVolume; }
    public Long getOiLongValue() { return oiLongValue; }
    public Long getOiShortVolume() { return oiShortVolume; }
    public Long getOiShortValue() { return oiShortValue; }
    public Long getOiNetVolume() { return oiNetVolume; }
    public Long getOiNetValue() { return oiNetValue; }

    public void setTradingLongVolume(Long v) { this.tradingLongVolume = v; }
    public void setTradingLongValue(Long v) { this.tradingLongValue = v; }
    public void setTradingShortVolume(Long v) { this.tradingShortVolume = v; }
    public void setTradingShortValue(Long v) { this.tradingShortValue = v; }
    public void setTradingNetVolume(Long v) { this.tradingNetVolume = v; }
    public void setTradingNetValue(Long v) { this.tradingNetValue = v; }
    public void setOiLongVolume(Long v) { this.oiLongVolume = v; }
    public void setOiLongValue(Long v) { this.oiLongValue = v; }
    public void setOiShortVolume(Long v) { this.oiShortVolume = v; }
    public void setOiShortValue(Long v) { this.oiShortValue = v; }
    public void setOiNetVolume(Long v) { this.oiNetVolume = v; }
    public void setOiNetValue(Long v) { this.oiNetValue = v; }
}
