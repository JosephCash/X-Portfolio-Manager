package org.example;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

public class BondCalculator {
    private static final BigDecimal NOMINAL = BigDecimal.valueOf(100.00);
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    public static BigDecimal calculateCurrentValue(SeriesDefinition series, LocalDate purchaseDate, LocalDate valuationDate) {
        if (valuationDate.isBefore(purchaseDate)) return NOMINAL;
        if (valuationDate.isAfter(series.maturityDate)) valuationDate = series.maturityDate;

        switch (series.type) {
            case ROR:
            case DOR:
                return calculateFloating(series, purchaseDate, valuationDate);
            case TOS:
                return calculateFixed(series, purchaseDate, valuationDate);
            case EDO:
                return calculateIndexedCapitalized(series, purchaseDate, valuationDate);
            case COI:
                return calculateIndexedLinear(series, purchaseDate, valuationDate);
            default:
                return NOMINAL;
        }
    }

    private static BigDecimal calculateFloating(SeriesDefinition series, LocalDate purchaseDate, LocalDate valuationDate) {
        BigDecimal totalInterest = BigDecimal.ZERO;
        LocalDate current = purchaseDate;

        while (current.plusMonths(1).isBefore(valuationDate) || current.plusMonths(1).isEqual(valuationDate)) {
            LocalDate nextMonth = current.plusMonths(1);
            BigDecimal rate = getRateForPeriodFloating(series, current);
            BigDecimal monthInterest = NOMINAL.multiply(rate.divide(BigDecimal.valueOf(100), MC))
                    .divide(BigDecimal.valueOf(12), MC);
            totalInterest = totalInterest.add(monthInterest);
            current = nextMonth;
        }

        long days = ChronoUnit.DAYS.between(current, valuationDate);
        if (days > 0) {
            BigDecimal rate = getRateForPeriodFloating(series, current);
            BigDecimal dailyRate = rate.divide(BigDecimal.valueOf(100), MC).divide(BigDecimal.valueOf(365), MC);
            totalInterest = totalInterest.add(NOMINAL.multiply(dailyRate).multiply(BigDecimal.valueOf(days)));
        }
        return NOMINAL.add(totalInterest);
    }

    private static BigDecimal calculateIndexedCapitalized(SeriesDefinition series, LocalDate purchaseDate, LocalDate valuationDate) {
        BigDecimal currentValue = NOMINAL;
        LocalDate periodStart = purchaseDate;

        int years = 0;
        while (periodStart.plusYears(1).isBefore(valuationDate) || periodStart.plusYears(1).isEqual(valuationDate)) {
            years++;
            BigDecimal rate = getRateForYearIndexed(series, years);
            BigDecimal factor = BigDecimal.ONE.add(rate.divide(BigDecimal.valueOf(100), MC));
            currentValue = currentValue.multiply(factor);
            periodStart = periodStart.plusYears(1);
        }

        long days = ChronoUnit.DAYS.between(periodStart, valuationDate);
        if (days > 0) {
            BigDecimal rate = getRateForYearIndexed(series, years + 1);
            BigDecimal yearFraction = BigDecimal.valueOf(days).divide(BigDecimal.valueOf(365), MC);
            BigDecimal interest = currentValue.multiply(rate.divide(BigDecimal.valueOf(100), MC)).multiply(yearFraction);
            currentValue = currentValue.add(interest);
        }
        return currentValue;
    }

    private static BigDecimal calculateIndexedLinear(SeriesDefinition series, LocalDate purchaseDate, LocalDate valuationDate) {
        LocalDate periodStart = purchaseDate;
        int yearIndex = 1;

        while (periodStart.plusYears(1).isBefore(valuationDate)) {
            periodStart = periodStart.plusYears(1);
            yearIndex++;
        }

        BigDecimal rate = getRateForYearIndexed(series, yearIndex);
        long days = ChronoUnit.DAYS.between(periodStart, valuationDate);

        BigDecimal interest = NOMINAL.multiply(rate.divide(BigDecimal.valueOf(100), MC))
                .multiply(BigDecimal.valueOf(days).divide(BigDecimal.valueOf(365), MC));

        return NOMINAL.add(interest);
    }

    private static BigDecimal calculateFixed(SeriesDefinition series, LocalDate purchaseDate, LocalDate valuationDate) {
        long days = ChronoUnit.DAYS.between(purchaseDate, valuationDate);
        BigDecimal years = BigDecimal.valueOf(days).divide(BigDecimal.valueOf(365), MC);

        BigDecimal base = BigDecimal.ONE.add(series.firstPeriodRate.divide(BigDecimal.valueOf(100), MC));
        double factor = Math.pow(base.doubleValue(), years.doubleValue());

        return NOMINAL.multiply(BigDecimal.valueOf(factor));
    }

    private static BigDecimal getRateForPeriodFloating(SeriesDefinition series, LocalDate date) {
        return MacroDataProvider.getInstance().getNbpRateForMonth(YearMonth.from(date));
    }

    private static BigDecimal getRateForYearIndexed(SeriesDefinition series, int yearNumber) {
        if (yearNumber == 1) return series.firstPeriodRate;
        BigDecimal inflation = MacroDataProvider.getInstance().getCpiForMonth(YearMonth.now().minusMonths(2));
        return inflation.add(series.margin);
    }
}