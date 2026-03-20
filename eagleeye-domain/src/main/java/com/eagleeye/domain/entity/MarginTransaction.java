package com.eagleeye.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(
    name = "margin_transaction",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_margin_transaction_trade_date",
        columnNames = {"trade_date"}
    )
)
public class MarginTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    // Margin (融資) — trading units (lots/張)
    @Column(name = "margin_purchase")        private Long marginPurchase;
    @Column(name = "margin_sale")            private Long marginSale;
    @Column(name = "margin_cash_redemption") private Long marginCashRedemption;
    @Column(name = "margin_prev_balance")    private Long marginPrevBalance;
    @Column(name = "margin_balance")         private Long marginBalance;

    // Short (融券) — trading units (lots/張)
    @Column(name = "short_covering")           private Long shortCovering;
    @Column(name = "short_sale")               private Long shortSale;
    @Column(name = "short_stock_redemption")   private Long shortStockRedemption;
    @Column(name = "short_prev_balance")       private Long shortPrevBalance;
    @Column(name = "short_balance")            private Long shortBalance;

    protected MarginTransaction() {}

    public MarginTransaction(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    public Long getId()                   { return id; }
    public LocalDate getTradeDate()       { return tradeDate; }
    public Long getMarginPurchase()       { return marginPurchase; }
    public Long getMarginSale()           { return marginSale; }
    public Long getMarginCashRedemption() { return marginCashRedemption; }
    public Long getMarginPrevBalance()    { return marginPrevBalance; }
    public Long getMarginBalance()        { return marginBalance; }
    public Long getShortCovering()        { return shortCovering; }
    public Long getShortSale()            { return shortSale; }
    public Long getShortStockRedemption() { return shortStockRedemption; }
    public Long getShortPrevBalance()     { return shortPrevBalance; }
    public Long getShortBalance()         { return shortBalance; }

    public void setMarginPurchase(Long v)       { this.marginPurchase = v; }
    public void setMarginSale(Long v)           { this.marginSale = v; }
    public void setMarginCashRedemption(Long v) { this.marginCashRedemption = v; }
    public void setMarginPrevBalance(Long v)    { this.marginPrevBalance = v; }
    public void setMarginBalance(Long v)        { this.marginBalance = v; }
    public void setShortCovering(Long v)        { this.shortCovering = v; }
    public void setShortSale(Long v)            { this.shortSale = v; }
    public void setShortStockRedemption(Long v) { this.shortStockRedemption = v; }
    public void setShortPrevBalance(Long v)     { this.shortPrevBalance = v; }
    public void setShortBalance(Long v)         { this.shortBalance = v; }
}
