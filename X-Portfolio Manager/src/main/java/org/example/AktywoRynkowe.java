package org.example;

public class AktywoRynkowe extends Aktywo {
    public double cenaZakupu;

    public AktywoRynkowe(String symbol, TypAktywa typ, double ilosc, String dataZakupu, Waluta waluta, double cenaZakupu) {
        super(symbol, typ, ilosc, dataZakupu, waluta);
        this.cenaZakupu = cenaZakupu;
    }

    @Override
    public double getCenaJednostkowa() {
        return MarketData.pobierzCene(this.symbol, this.typ);
    }

    @Override
    public double getCenaZakupu() {
        return cenaZakupu;
    }

    // --- POPRAWKA: Używamy typ.name() zamiast "RYNEK", aby BazaDanych mogła to odczytać ---
    @Override
    public String toCSV() {
        return String.join(";",
                typ.name(),            // <--- ZMIANA: Było "RYNEK", jest typ.name() (np. AKCJA_PL)
                symbol,
                typ.name(),
                String.valueOf(ilosc),
                dataZakupu,
                waluta.name(),
                String.valueOf(cenaZakupu)
        );
    }
}