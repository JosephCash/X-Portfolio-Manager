package org.example;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MacroDataProvider {
    private static MacroDataProvider instance;

    private final Map<YearMonth, BigDecimal> nbpReferenceRates = new ConcurrentHashMap<>();
    private final Map<YearMonth, BigDecimal> cpiInflation = new ConcurrentHashMap<>();

    private MacroDataProvider() {
        ladujDaneStartowe();
    }

    public static synchronized MacroDataProvider getInstance() {
        if (instance == null) instance = new MacroDataProvider();
        return instance;
    }

    public BigDecimal getNbpRateForMonth(YearMonth month) {
        return nbpReferenceRates.getOrDefault(month, BigDecimal.valueOf(5.75));
    }

    public BigDecimal getCpiForMonth(YearMonth month) {
        return cpiInflation.getOrDefault(month, BigDecimal.valueOf(5.0));
    }

    private void ladujDaneStartowe() {
        YearMonth now = YearMonth.now();
        // Generujemy przykładowe dane na 2 lata wstecz
        for (int i = 0; i < 24; i++) {
            nbpReferenceRates.put(now.minusMonths(i), BigDecimal.valueOf(5.75));
            cpiInflation.put(now.minusMonths(i), BigDecimal.valueOf(4.5));
        }
    }
}