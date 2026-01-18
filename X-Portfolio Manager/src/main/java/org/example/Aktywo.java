package org.example;

import java.io.Serializable;

public abstract class Aktywo implements Serializable {
    private static final long serialVersionUID = 1L;

    public String symbol;
    public TypAktywa typ;
    public double ilosc;
    public String dataZakupu;
    public Waluta waluta;

    public Aktywo(String symbol, TypAktywa typ, double ilosc, String dataZakupu, Waluta waluta) {
        this.symbol = symbol;
        this.typ = typ;
        this.ilosc = ilosc;
        this.dataZakupu = dataZakupu;
        this.waluta = waluta;
    }

    public abstract double getCenaJednostkowa();
    public abstract double getCenaZakupu();

    // TEJ METODY BRAKOWAŁO - jest niezbędna dla BazaDanych.zapisz()
    public abstract String toCSV();

    public double getWartoscPLN(double kursUSD, double kursEUR, double kursGBP) {
        double wartoscWWalucie = getCenaJednostkowa() * ilosc;
        switch (waluta) {
            case USD: return wartoscWWalucie * kursUSD;
            case EUR: return wartoscWWalucie * kursEUR;
            case GBP: return wartoscWWalucie * kursGBP;
            case USDT: return wartoscWWalucie * kursUSD;
            default: return wartoscWWalucie;
        }
    }

    @Override
    public String toString() {
        return symbol + " (" + ilosc + ")";
    }
}