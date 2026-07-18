package com.eagleeye.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

@Entity
@Table(
    name = "futures_market_oi",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_futures_market_oi",
        columnNames = {"trade_date", "contract"}
    )
)
public class FuturesMarketOi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = LocalDateToIsoStringConverter.class)
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "contract", nullable = false, length = 10)
    private String contract;

    @Column(name = "total_oi")
    private Long totalOi;

    protected FuturesMarketOi() {}

    public FuturesMarketOi(LocalDate tradeDate, String contract) {
        this.tradeDate = tradeDate;
        this.contract = contract;
    }

    public Long getId() { return id; }
    public LocalDate getTradeDate() { return tradeDate; }
    public String getContract() { return contract; }
    public Long getTotalOi() { return totalOi; }

    public void setTotalOi(Long totalOi) { this.totalOi = totalOi; }
}
