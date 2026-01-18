package org.example;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class SeriesRepository {
    private static final Map<String, SeriesDefinition> seriesMap = new HashMap<>();

    static {
        // --- AUTENTYCZNE DANE EMISJI ---

        // EDO0535 (Emisja Maj 2025)
        // 1 rok: 6.35%, Marża: 2.00%
        seriesMap.put("EDO0535", new SeriesDefinition(
                "EDO0535", BondType.EDO,
                LocalDate.of(2025, 5, 1), LocalDate.of(2035, 5, 1),
                6.35, 2.00, 2.00
        ));

        // EDO0136 (Emisja Styczeń 2026)
        // 1 rok: 5.60%, Marża: 2.00%
        seriesMap.put("EDO0136", new SeriesDefinition(
                "EDO0136", BondType.EDO,
                LocalDate.of(2026, 1, 1), LocalDate.of(2036, 1, 1),
                5.60, 2.00, 2.00
        ));

        // Inne przykłady
        seriesMap.put("ROR0125", new SeriesDefinition(
                "ROR0125", BondType.ROR,
                LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1),
                6.50, 0.00, 0.50
        ));
    }

    public static SeriesDefinition getDefinition(String seriesId) {
        if (!seriesMap.containsKey(seriesId)) {
            return guessDefinitionFromId(seriesId);
        }
        return seriesMap.get(seriesId);
    }

    private static SeriesDefinition guessDefinitionFromId(String id) {
        BondType type = BondType.ROR;
        double defaultRate = 6.0;
        double defaultMargin = 1.0;

        // Jeśli nie znamy serii, zgadujemy parametry bliższe rzeczywistości (ok. 6-7%)
        if (id.startsWith("EDO")) {
            type = BondType.EDO;
            defaultRate = 6.50;
            defaultMargin = 2.00;
        }
        else if (id.startsWith("COI")) {
            type = BondType.COI;
            defaultRate = 6.10;
            defaultMargin = 1.50;
        }
        else if (id.startsWith("TOS")) {
            type = BondType.TOS;
            defaultRate = 5.75;
        }

        // Próba odgadnięcia daty z końcówki symbolu (np. EDO0535 -> 05 i 35)
        LocalDate maturityDate = LocalDate.now().plusYears(1);
        try {
            if (id.length() >= 7) {
                String mm = id.substring(3, 5);
                String yy = id.substring(5, 7);
                int month = Integer.parseInt(mm);
                int year = 2000 + Integer.parseInt(yy);
                maturityDate = LocalDate.of(year, month, 1);
            }
        } catch (Exception ignored) {
            if (type == BondType.EDO) maturityDate = LocalDate.now().plusYears(10);
        }

        LocalDate issueDate = maturityDate.minusYears(type == BondType.EDO ? 10 : (type == BondType.COI ? 4 : 1));

        return new SeriesDefinition(id, type, issueDate, maturityDate, defaultRate, defaultMargin, 0.70);
    }
}