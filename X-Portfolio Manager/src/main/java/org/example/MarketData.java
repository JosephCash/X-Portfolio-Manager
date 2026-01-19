package org.example;

import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarketData {

    // Cache na 2 minuty - zapobiega ciągłemu odpytywaniu API
    private static final long CZAS_CACHE_MS = 120 * 1000;

    // Bezpieczne mapy dla wielowątkowości
    private static final Map<String, Double> cacheCeny = new ConcurrentHashMap<>();
    private static final Map<String, Long> cacheCzas = new ConcurrentHashMap<>();

    // Cache walut (USD, EUR, GBP)
    private static double cachedUSD = 0.0;
    private static double cachedEUR = 0.0;
    private static double cachedGBP = 0.0;
    private static long lastCurrencyUpdate = 0;

    // --- METODY PUBLICZNE ---

    public static void wyczyscCache() {
        cacheCeny.clear();
        cacheCzas.clear();
        cachedUSD = 0; cachedEUR = 0; cachedGBP = 0;
        lastCurrencyUpdate = 0;
        System.out.println("Cache cen wyczyszczony.");
    }

    // TA METODA BYŁA BRAKUJĄCA - SPRAWDZA CZY MAMY JAKIEKOLWIEK DANE
    public static boolean czyDaneZCache() {
        return !cacheCeny.isEmpty();
    }

    public static double pobierzCene(String symbol, TypAktywa typ) {
        // Waluty bazowe zawsze mają wartość 1.0 (względem samych siebie)
        if (symbol.equals("PLN") || symbol.equals("EUR") || symbol.equals("USD")) return 1.0;

        String klucz = symbol + "_" + typ.name();

        // 1. Sprawdź Cache
        if (cacheCeny.containsKey(klucz)) {
            if (System.currentTimeMillis() - cacheCzas.getOrDefault(klucz, 0L) < CZAS_CACHE_MS) {
                return cacheCeny.get(klucz);
            }
        }

        // Jeśli jesteśmy w wątku UI i nie ma w cache, zwróć 0 (żeby nie zawiesić ekranu),
        // ale Main używa SwingWorker, więc to rzadki przypadek.
        if (SwingUtilities.isEventDispatchThread()) return cacheCeny.getOrDefault(klucz, 0.0);

        double cena = 0.0;

        // 2. KRYPTO -> BINANCE API (Ceny w USD)
        if (typ == TypAktywa.KRYPTO) {
            cena = pobierzZBinance(symbol);
        }
        // 3. AKCJE USA -> YAHOO
        else if (typ == TypAktywa.AKCJA_USA) {
            cena = pobierzZYahooJson(mapujSymbolNaYahoo(symbol, typ));
        }
        // 4. AKCJE PL -> STOOQ (Fallback Yahoo)
        else {
            String sTicker = mapujSymbolNaStooq(symbol, typ);
            cena = pobierzZeStooqCSV(sTicker);
            if (cena == 0.0) {
                cena = pobierzZYahooJson(mapujSymbolNaYahoo(symbol, typ));
            }
        }

        if (cena > 0) {
            cacheCeny.put(klucz, cena);
            cacheCzas.put(klucz, System.currentTimeMillis());
            return cena;
        }

        return cacheCeny.getOrDefault(klucz, 0.0);
    }

    // --- WALUTY ---
    private static void aktualizujWaluty() {
        if (System.currentTimeMillis() - lastCurrencyUpdate < 300000 && cachedUSD > 0) return;

        double u = pobierzZeStooqCSV("USDPLN");
        if (u == 0) u = pobierzZYahooJson("USDPLN=X");
        if (u > 0) cachedUSD = u; else if (cachedUSD == 0) cachedUSD = 4.00; // Fallback

        double e = pobierzZeStooqCSV("EURPLN");
        if (e == 0) e = pobierzZYahooJson("EURPLN=X");
        if (e > 0) cachedEUR = e; else if (cachedEUR == 0) cachedEUR = 4.30;

        double g = pobierzZeStooqCSV("GBPPLN");
        if (g == 0) g = pobierzZYahooJson("GBPPLN=X");
        if (g > 0) cachedGBP = g; else if (cachedGBP == 0) cachedGBP = 5.20;

        lastCurrencyUpdate = System.currentTimeMillis();
    }

    public static double pobierzKursUSD() { aktualizujWaluty(); return cachedUSD; }
    public static double pobierzKursEUR() { aktualizujWaluty(); return cachedEUR; }
    public static double pobierzKursGBP() { aktualizujWaluty(); return cachedGBP; }

    // --- BINANCE API (KRYPTO) ---
    private static double pobierzZBinance(String symbol) {
        String raw = symbol.toUpperCase().replace("-USD", "").replace("-PLN", "").trim();
        // Stablecoiny = 1 USD
        if (raw.equals("USDT") || raw.equals("USDC") || raw.equals("BUSD") || raw.equals("FDUSD")) return 1.0;

        try {
            // Pobieramy cenę w USDT
            String json = quickGet("https://api.binance.com/api/v3/ticker/price?symbol=" + raw + "USDT");
            if (json != null) {
                Matcher m = Pattern.compile("\"price\":\"([0-9\\.]+)\"").matcher(json);
                if (m.find()) return Double.parseDouble(m.group(1));
            }
        } catch (Exception ignored) {}
        return 0.0;
    }

    // --- YAHOO JSON API ---
    private static double pobierzZYahooJson(String ticker) {
        try {
            String json = quickGet("https://query1.finance.yahoo.com/v8/finance/chart/" + ticker + "?interval=1d");
            if (json != null) {
                Matcher m = Pattern.compile("\"regularMarketPrice\":\\{?\"?raw\"?:?([0-9\\.]+)[,}]").matcher(json);
                if (m.find()) return Double.parseDouble(m.group(1));

                m = Pattern.compile("\"regularMarketPrice\":([0-9\\.]+)").matcher(json);
                if (m.find()) return Double.parseDouble(m.group(1));
            }
        } catch (Exception ignored) {}
        return 0.0;
    }

    // --- STOOQ CSV ---
    private static double pobierzZeStooqCSV(String ticker) {
        try {
            String csv = quickGet("https://stooq.pl/q/l/?s=" + ticker.toLowerCase() + "&f=sd2t2ohlc&h&e=csv");
            if (csv != null) {
                String[] lines = csv.split("\n");
                if (lines.length > 1) {
                    String[] parts = lines[1].split(",");
                    if (parts.length >= 7 && !parts[6].equalsIgnoreCase("N/A")) {
                        return Double.parseDouble(parts[6]);
                    }
                }
            }
        } catch (Exception ignored) {}
        return 0.0;
    }

    // Szybki GET HTTP
    private static String quickGet(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            if (conn.getResponseCode() == 200) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) sb.append(line);
                    return sb.toString();
                }
            }
        } catch (Exception e) { return null; }
        return null;
    }

    // --- MAPOWANIE SYMBOLI ---
    private static String mapujSymbolNaYahoo(String s, TypAktywa t) {
        s = s.toUpperCase().trim();
        if (t == TypAktywa.KRYPTO) return s.contains("-") ? s : s + "-USD";
        if (t == TypAktywa.AKCJA_PL) return s.contains(".") ? s.split("\\.")[0] + ".WA" : s + ".WA";
        if (s.endsWith(".DE")) return s;
        if (s.endsWith(".UK")) return s.replace(".UK", ".L");
        if (s.endsWith(".US")) return s.replace(".US", "");
        return s;
    }

    private static String mapujSymbolNaStooq(String s, TypAktywa t) {
        s = s.toUpperCase().trim();
        if (t == TypAktywa.AKCJA_PL) return s.replace(".WA", "").replace(".PL", "");
        if (s.endsWith(".DE")) return s;
        if (s.endsWith(".UK")) return s;
        if (!s.contains(".")) return s + ".US";
        return s;
    }
}