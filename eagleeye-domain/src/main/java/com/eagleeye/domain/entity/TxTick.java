package com.eagleeye.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(
    name = "tx_tick",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_tx_tick",
        columnNames = {"trade_date", "time", "price", "volume"}
    )
)
public class TxTick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = LocalDateToIsoStringConverter.class)
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "time", nullable = false, length = 6)
    private String time;

    @Column(name = "price", nullable = false)
    private int price;

    @Column(name = "volume", nullable = false)
    private int volume;

    @Column(name = "contract_month", nullable = false)
    private String contractMonth;

    @Column(name = "is_auction", nullable = false)
    private boolean auction;

    protected TxTick() {}

    public TxTick(LocalDate tradeDate, String time, int price, int volume,
                  String contractMonth, boolean auction) {
        this.tradeDate = tradeDate;
        this.time = time;
        this.price = price;
        this.volume = volume;
        this.contractMonth = contractMonth;
        this.auction = auction;
    }

    public Long getId()                { return id; }
    public LocalDate getTradeDate()    { return tradeDate; }
    public String getTime()            { return time; }
    public int getPrice()              { return price; }
    public int getVolume()             { return volume; }
    public String getContractMonth()   { return contractMonth; }
    public boolean isAuction()         { return auction; }
}
