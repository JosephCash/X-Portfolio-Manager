package org.example;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class ObligacjaStala extends Aktywo {
    double oprocentowanie;

    public ObligacjaStala(String symbol, double ilosc, String dataZakupu, double oprocentowanie) {
        super(symbol, TypAktywa.OBLIGACJA_STALA, ilosc, dataZakupu, Waluta.PLN);
        this.oprocentowanie = oprocentowanie;
    }

    @Override
    public double getCenaJednostkowa() {
        long dni = ChronoUnit.DAYS.between(LocalDate.parse(dataZakupu), LocalDate.now());
        double odsetki = 100.0 * (oprocentowanie / 100.0) * (dni / 365.0);
        return 100.0 + odsetki;
    }

    @Override
    public double getCenaZakupu() {
        return 100.0; // Obligacje skarbowe kupujemy zazwyczaj po nominale 100zł
    }

    @Override
    public String toCSV() {
        return String.join(";",
                "OBL_STALA",
                symbol,
                typ.name(),
                String.valueOf(ilosc),
                dataZakupu,
                "PLN",
                String.valueOf(oprocentowanie)
        );
    }
}