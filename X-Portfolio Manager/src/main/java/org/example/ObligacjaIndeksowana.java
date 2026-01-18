package org.example;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class ObligacjaIndeksowana extends Aktywo {
    double oprocentowanie1Rok;
    double marza;

    public ObligacjaIndeksowana(String symbol, double ilosc, String dataZakupu, double oprocentowanie1Rok, double marza) {
        super(symbol, TypAktywa.OBLIGACJA_INDEKSOWANA, ilosc, dataZakupu, Waluta.PLN);
        this.oprocentowanie1Rok = oprocentowanie1Rok;
        this.marza = marza;
    }

    @Override
    public double getCenaJednostkowa() {
        long dni = ChronoUnit.DAYS.between(LocalDate.parse(dataZakupu), LocalDate.now());
        // Uproszczona logika: po roku (365 dni) wchodzi oprocentowanie oparte o marżę + inflację (tutaj stałe 5% jako baza)
        double aktualnyProcent = (dni > 365) ? (5.0 + marza) : oprocentowanie1Rok;
        double odsetki = 100.0 * (aktualnyProcent / 100.0) * (dni / 365.0);
        return 100.0 + odsetki;
    }

    @Override
    public double getCenaZakupu() {
        return 100.0;
    }

    @Override
    public String toCSV() {
        return String.join(";",
                "OBL_INDEKS",
                symbol,
                typ.name(),
                String.valueOf(ilosc),
                dataZakupu,
                "PLN",
                String.valueOf(oprocentowanie1Rok),
                String.valueOf(marza)
        );
    }
}