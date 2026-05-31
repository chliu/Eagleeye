package com.eagleeye.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(
    name = "institutional_flow",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_institutional_flow_trade_date",
        columnNames = {"trade_date"}
    )
)
public class InstitutionalFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = LocalDateToIsoStringConverter.class)
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    // Foreign Investors — NTD
    @Column(name = "foreign_buy")  private Long foreignBuy;
    @Column(name = "foreign_sell") private Long foreignSell;
    @Column(name = "foreign_net")  private Long foreignNet;

    // Investment Trust — NTD
    @Column(name = "investment_trust_buy")  private Long investmentTrustBuy;
    @Column(name = "investment_trust_sell") private Long investmentTrustSell;
    @Column(name = "investment_trust_net")  private Long investmentTrustNet;

    // Dealers — NTD
    @Column(name = "dealer_buy")  private Long dealerBuy;
    @Column(name = "dealer_sell") private Long dealerSell;
    @Column(name = "dealer_net")  private Long dealerNet;

    protected InstitutionalFlow() {}

    public InstitutionalFlow(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    public Long getId()                   { return id; }
    public LocalDate getTradeDate()       { return tradeDate; }
    public Long getForeignBuy()           { return foreignBuy; }
    public Long getForeignSell()          { return foreignSell; }
    public Long getForeignNet()           { return foreignNet; }
    public Long getInvestmentTrustBuy()   { return investmentTrustBuy; }
    public Long getInvestmentTrustSell()  { return investmentTrustSell; }
    public Long getInvestmentTrustNet()   { return investmentTrustNet; }
    public Long getDealerBuy()            { return dealerBuy; }
    public Long getDealerSell()           { return dealerSell; }
    public Long getDealerNet()            { return dealerNet; }

    public void setForeignBuy(Long v)          { this.foreignBuy = v; }
    public void setForeignSell(Long v)         { this.foreignSell = v; }
    public void setForeignNet(Long v)          { this.foreignNet = v; }
    public void setInvestmentTrustBuy(Long v)  { this.investmentTrustBuy = v; }
    public void setInvestmentTrustSell(Long v) { this.investmentTrustSell = v; }
    public void setInvestmentTrustNet(Long v)  { this.investmentTrustNet = v; }
    public void setDealerBuy(Long v)           { this.dealerBuy = v; }
    public void setDealerSell(Long v)          { this.dealerSell = v; }
    public void setDealerNet(Long v)           { this.dealerNet = v; }
}
