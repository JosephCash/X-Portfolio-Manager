package org.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.example.XtbImport.ImportResult;

public class BinanceImport {

    // Kursy do szacowania wpłat historycznych (tylko dla licznika "Wpłacono")
    private static final double USD_PLN_RATE = 4.0;
    private static final double EUR_PLN_RATE = 4.3;
    private static final double BTC_PRICE_ESTIMATE_PLN = 380000.0;
    private static final double ETH_PRICE_ESTIMATE_PLN = 13000.0; // Dodano szacunek dla ETH

    public static ImportResult importujCSV(File plik) {
        ImportResult result = new ImportResult();
        Map<String, Double> portfelMap = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(plik))) {
            String line;
            br.readLine(); // Skip header

            while ((line = br.readLine()) != null) {
                String[] parts = parseLine(line);
                if (parts.length < 6) continue;

                String data = parts[1];
                String operacja = parts[3];
                String coin = parts[4];
                double change;

                try {
                    change = Double.parseDouble(parts[5]);
                } catch (NumberFormatException e) {
                    continue;
                }

                // --- 1. Aktualizacja stanu posiadania ---
                portfelMap.put(coin, portfelMap.getOrDefault(coin, 0.0) + change);

                // --- 2. Logika Wpłat ---
                boolean isDeposit = false;
                double depositValuePln = 0.0;

                // Sprawdzamy czy zmiana jest dodatnia
                if (change > 0) {
                    // A. Standardowe wpłaty, P2P i Crypto Deposit (np. przelew ETH z innego portfela)
                    if (operacja.contains("Deposit") || operacja.contains("Fiat OCBS") || operacja.contains("P2P Trading")) {
                        // USUNIĘTO WARUNEK: if (isFiat(coin) || isStable(coin))
                        // Teraz akceptujemy też wpłaty krypto, o ile potrafimy je wycenić
                        isDeposit = true;
                        depositValuePln = estimatePlnValue(coin, change);
                    }
                    // B. Zakupy bezpośrednie (karta/fiat)
                    else if (operacja.contains("Buy Crypto With Fiat") || operacja.contains("Buy Crypto With Card")) {
                        isDeposit = true;
                        depositValuePln = estimatePlnValue(coin, change);
                    }
                }

                if (isDeposit) {
                    // Jeśli udało się oszacować wartość (nie jest 0), dodajemy do sumy
                    if (depositValuePln > 0) {
                        result.sumaWplat += depositValuePln;
                        result.historiaTransakcji.add(new Transakcja(data, "DEPOSIT", coin, change, depositValuePln, "PLN"));
                    }
                } else if (change < 0 && (operacja.contains("Withdraw"))) {
                    result.historiaTransakcji.add(new Transakcja(data, "WITHDRAW", coin, Math.abs(change), 0.0, coin));
                } else {
                    // Pozostałe operacje (handel, fee, staking reward itp.)
                    String typ = change > 0 ? "BUY" : "SELL";
                    result.historiaTransakcji.add(new Transakcja(data, typ, coin, Math.abs(change), 0.0, ""));
                }
            }

            result.znalezionePliki = 1;

            // --- 3. Tworzenie Aktywów ---
            for (Map.Entry<String, Double> entry : portfelMap.entrySet()) {
                String symbol = entry.getKey();
                double ilosc = entry.getValue();

                // Ignorujemy pył (< 0.00001)
                if (Math.abs(ilosc) > 0.00001) {
                    Aktywo aktywo;

                    if (symbol.equals("PLN")) {
                        aktywo = new AktywoRynkowe("PLN", TypAktywa.AKCJA_PL, ilosc, LocalDate.now().toString(), Waluta.PLN, 1.0);
                    }
                    else if (symbol.equals("EUR")) {
                        aktywo = new AktywoRynkowe("EUR", TypAktywa.AKCJA_USA, ilosc, LocalDate.now().toString(), Waluta.EUR, 1.0);
                    }
                    else if (symbol.equals("USD")) {
                        aktywo = new AktywoRynkowe("USD", TypAktywa.AKCJA_USA, ilosc, LocalDate.now().toString(), Waluta.USD, 1.0);
                    }
                    else {
                        // Krypto
                        aktywo = new AktywoRynkowe(
                                symbol,
                                TypAktywa.KRYPTO,
                                ilosc,
                                LocalDate.now().toString(),
                                Waluta.USDT,
                                0.0 // Cena zakupu nieznana, aplikacja powinna pobrać kurs bieżący
                        );
                    }
                    result.aktywa.add(aktywo);
                }
            }

        } catch (Exception e) {
            if (result.logi != null) result.logi.append("Błąd: ").append(e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    private static double estimatePlnValue(String coin, double amount) {
        if (coin.equals("PLN")) return amount;
        if (coin.equals("EUR")) return amount * EUR_PLN_RATE;
        if (coin.equals("USD") || coin.equals("USDT") || coin.equals("USDC") || coin.equals("BUSD") || coin.equals("FDUSD")) return amount * USD_PLN_RATE;
        if (coin.equals("BTC")) return amount * BTC_PRICE_ESTIMATE_PLN;
        if (coin.equals("ETH")) return amount * ETH_PRICE_ESTIMATE_PLN; // Dodana obsługa ETH
        return 0.0; // Nieznana waluta, nie doliczy do sumy wpłat
    }

    private static boolean isFiat(String symbol) {
        return symbol.equals("PLN") || symbol.equals("EUR") || symbol.equals("USD") || symbol.equals("GBP");
    }

    private static boolean isStable(String symbol) {
        return symbol.equals("USDT") || symbol.equals("USDC") || symbol.equals("BUSD") || symbol.equals("FDUSD");
    }

    private static String[] parseLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '\"') inQuotes = !inQuotes;
            else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().replace("\"", "").trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().replace("\"", "").trim());
        return tokens.toArray(new String[0]);
    }
}