package org.example;

import java.io.Serializable;

public class Transakcja implements Serializable {
    private static final long serialVersionUID = 1L;

    public String data;      // Format yyyy-MM-dd HH:mm:ss
    public String typ;       // "DEPOSIT", "WITHDRAW", "BUY", "SELL", "FEE"
    public String symbol;    // np. "BTC", "PLN", "USDT"
    public double ilosc;     // Ilość aktywa
    public double wartosc;   // Wartość (często w walucie FIAT lub Stablecoinie)
    public String waluta;    // np. "PLN", "USD"

    public Transakcja(String data, String typ, String symbol, double ilosc, double wartosc, String waluta) {
        this.data = data;
        this.typ = typ;
        this.symbol = symbol;
        this.ilosc = ilosc;
        this.wartosc = wartosc;
        this.waluta = waluta;
    }

    // Prosta reprezentacja CSV do zapisu w pliku tekstowym
    public String toCSV() {
        return data + ";" + typ + ";" + symbol + ";" + ilosc + ";" + wartosc + ";" + waluta;
    }
}