package org.example;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SeriesDefinition {
    public final String seriesId;
    public final BondType type;
    public final LocalDate issueDate;
    public final LocalDate maturityDate;
    public final BigDecimal firstPeriodRate;
    public final BigDecimal margin;
    public final BigDecimal earlyRedemptionFee;

    public SeriesDefinition(String seriesId, BondType type, LocalDate issueDate,
                            LocalDate maturityDate, double firstPeriodRate,
                            double margin, double earlyRedemptionFee) {
        this.seriesId = seriesId;
        this.type = type;
        this.issueDate = issueDate;
        this.maturityDate = maturityDate;
        this.firstPeriodRate = BigDecimal.valueOf(firstPeriodRate);
        this.margin = BigDecimal.valueOf(margin);
        this.earlyRedemptionFee = BigDecimal.valueOf(earlyRedemptionFee);
    }
}