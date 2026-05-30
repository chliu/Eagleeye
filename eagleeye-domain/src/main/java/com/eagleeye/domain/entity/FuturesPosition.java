package com.eagleeye.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

@Entity
@Table(
    name = "futures_position",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_futures_position",
        columnNames = {"trade_date", "contract", "trader_type"}
    )
)
public class FuturesPosition extends AbstractMarketPosition {

    protected FuturesPosition() {}

    public FuturesPosition(LocalDate tradeDate, String contract, TraderType traderType) {
        super(tradeDate, contract, traderType);
    }
}
