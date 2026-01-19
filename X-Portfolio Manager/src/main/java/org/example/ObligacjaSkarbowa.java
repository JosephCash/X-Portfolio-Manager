package org.example;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ObligacjaSkarbowa extends Aktywo {
    private Double manualRate;
    private Double manualMargin;

    // Stary konstruktor (auto-zgadywanie)
    public ObligacjaSkarbowa(String seriesId, double ilosc, String dataZakupu) {
        this(seriesId, null, ilosc, dataZakupu, null, null);
    }

    // Nowy konstruktor z wyborem typu
    public ObligacjaSkarbowa(String seriesId, TypAktywa forcedType, double ilosc, String dataZakupu, Double manualRate, Double manualMargin) {
        super(seriesId, (forcedType != null ? forcedType : zgadnijTyp(seriesId)), ilosc, dataZakupu, Waluta.PLN);
        this.manualRate = manualRate;
        this.manualMargin = manualMargin;
    }

    private static TypAktywa zgadnijTyp(String symbol) {
        if (symbol == null) return TypAktywa.OBLIGACJA_STALA;
        String s = symbol.trim().toUpperCase();
        if (s.startsWith("EDO") || s.startsWith("COI") || s.startsWith("ROS") || s.startsWith("ROD")) {
            return TypAktywa.OBLIGACJA_INDEKSOWANA;
        }
        return TypAktywa.OBLIGACJA_STALA;
    }

    // --- NOWA METODA DO NAPRAWIENIA BŁĘDU ---
    public double getAktualneOprocentowanie() {
        // 1. Jeśli użytkownik podał ręcznie, zwróć to
        if (manualRate != null) return manualRate;

        // 2. Jeśli nie, spróbuj pobrać z bazy definicji (np. dla EDO/COI)
        try {
            SeriesDefinition def = SeriesRepository.getDefinition(this.symbol);
            if (def != null && def.firstPeriodRate != null) {
                return def.firstPeriodRate.doubleValue();
            }
        } catch (Exception e) {
            // Ignoruj błędy jeśli serii nie ma w bazie
        }
        return 0.0;
    }

    @Override
    public double getCenaJednostkowa() {
        SeriesDefinition def = SeriesRepository.getDefinition(this.symbol);

        // Nadpisanie parametrami ręcznymi
        if (manualRate != null || manualMargin != null) {
            double r = (manualRate != null) ? manualRate : def.firstPeriodRate.doubleValue();
            // Jeśli marża jest null (bo np. obligacja stała), przyjmujemy 0.0
            double m = (manualMargin != null) ? manualMargin : (def.margin != null ? def.margin.doubleValue() : 0.0);

            def = new SeriesDefinition(
                    def.seriesId,
                    def.type,
                    def.issueDate,
                    def.maturityDate,
                    r,
                    m,
                    def.earlyRedemptionFee.doubleValue()
            );
        }

        LocalDate zakup = LocalDate.parse(this.dataZakupu);
        LocalDate wycena = LocalDate.now();

        BigDecimal val = BondCalculator.calculateCurrentValue(def, zakup, wycena);
        return val.doubleValue();
    }

    @Override
    public double getCenaZakupu() {
        return 100.0;
    }

    @Override
    public String toCSV() {
        String base = String.join(";",
                "OBL_SKARB",
                symbol,
                typ.name(), // Tu zapisuje się wybrany typ (INDEKSOWANA lub STALA)
                String.valueOf(ilosc),
                dataZakupu,
                "PLN"
        );

        if (manualRate != null || manualMargin != null) {
            String r = (manualRate != null) ? String.valueOf(manualRate) : "";
            String m = (manualMargin != null) ? String.valueOf(manualMargin) : "";
            return base + ";" + r + ";" + m;
        }

        return base;
    }
}