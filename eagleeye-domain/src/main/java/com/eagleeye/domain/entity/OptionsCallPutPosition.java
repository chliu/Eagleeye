package com.eagleeye.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

/**
 * Full per-trader breakdown of the TAIFEX callsAndPuts report
 * (三大法人－區分各類選擇權買賣權契約金額), split by call/put.
 *
 * <p>Inherits the 12 trading/open-interest volume+value columns from
 * {@link AbstractMarketPosition}; {@code oiNetValue} here is
 * 未平倉餘額→買賣差額→契約金額, the headline metric.
 */
@Entity
@Table(
    name = "options_call_put_position",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_options_call_put_position",
        columnNames = {"trade_date", "contract", "trader_type", "right_type"}
    )
)
public class OptionsCallPutPosition extends AbstractMarketPosition {

    @Enumerated(EnumType.STRING)
    @Column(name = "right_type", nullable = false, length = 4)
    private RightType rightType;

    protected OptionsCallPutPosition() {}

    public OptionsCallPutPosition(LocalDate tradeDate, String contract,
                                  TraderType traderType, RightType rightType) {
        super(tradeDate, contract, traderType);
        this.rightType = rightType;
    }

    public RightType getRightType() { return rightType; }
    public void setRightType(RightType v) { this.rightType = v; }
}
