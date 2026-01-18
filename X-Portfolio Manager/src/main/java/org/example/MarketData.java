package org.example;

import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarketData {

    // --- KONFIGURACJA ---
    private static final double KOREKTA_XTB = 0.992; // Korekta spreadu
    private static final long CZAS_CACHE_MS = 3600 * 1000; // 1 godzina

    // --- SYSTEM PAMIĘCI (CACHE) ---
    private static final Map<String, Double> cacheCeny = new HashMap<>();
    private static final Map<String, Long> cacheCzas = new HashMap<>();
    private static boolean uzytoCacheWSesji = false;

    // --- INTERFEJS PUBLICZNY ---

    public static double pobierzCene(String symbol, TypAktywa typ) {
        String tickerStooq = mapujSymbolNaStooq(symbol, typ);

        Double cached = pobierzZCache(tickerStooq);
        if (cached != null) {
            uzytoCacheWSesji = true;
            return cached * KOREKTA_XTB;
        }

        if (SwingUtilities.isEventDispatchThread()) return 0.0;

        uzytoCacheWSesji = false;
        double cena = pobierzZInternetuHybrydowo(symbol, typ, tickerStooq);

        if (cena > 0) {
            zapiszDoCache(tickerStooq, cena);
            return cena * KOREKTA_XTB;
        }
        return 0.0;
    }

    public static double pobierzKursUSD() { return pobierzKursWaluty("USDPLN", 4.00); }
    public static double pobierzKursEUR() { return pobierzKursWaluty("EURPLN", 4.30); }
    public static double pobierzKursGBP() { return pobierzKursWaluty("GBPPLN", 5.20); }

    public static boolean czyDaneZCache() { return uzytoCacheWSesji; }

    // --- LOGIKA CACHE ---

    private static Double pobierzZCache(String klucz) {
        if (cacheCeny.containsKey(klucz) && cacheCzas.containsKey(klucz)) {
            long czasZapisu = cacheCzas.get(klucz);
            if (System.currentTimeMillis() - czasZapisu < CZAS_CACHE_MS) return cacheCeny.get(klucz);
        }
        return null;
    }

    private static void zapiszDoCache(String klucz, double wartosc) {
        cacheCeny.put(klucz, wartosc);
        cacheCzas.put(klucz, System.currentTimeMillis());
    }

    private static double pobierzKursWaluty(String para, double fallback) {
        Double cached = pobierzZCache(para);
        if (cached != null) return cached;
        if (SwingUtilities.isEventDispatchThread()) return fallback;

        double val = pobierzZeStooqCSV(para);
        if (val == 0.0) val = pobierzZYahooJson(para + "=X");

        if (val > 0) {
            zapiszDoCache(para, val);
            return val;
        }
        return fallback;
    }

    // --- LOGIKA HYBRYDOWA ---

    private static double pobierzZInternetuHybrydowo(String symbol, TypAktywa typ, String tickerStooq) {
        double cena = pobierzZeStooqCSV(tickerStooq);
        if (cena == 0.0) {
            String tickerYahoo = mapujSymbolNaYahoo(symbol, typ);
            cena = pobierzZYahooJson(tickerYahoo);
        }
        return cena;
    }

    // --- YAHOO (POPRAWIONE MAPOWANIE) ---

    private static double pobierzZYahooJson(String ticker) {
        try {
            String urlStr = "https://query1.finance.yahoo.com/v8/finance/chart/" + ticker + "?interval=1d";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(3000);

            if (conn.getResponseCode() != 200) return 0.0;

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) content.append(line);
            in.close();

            Pattern pattern = Pattern.compile("\"regularMarketPrice\":([0-9\\.]+)");
            Matcher matcher = pattern.matcher(content.toString());
            if (matcher.find()) return Double.parseDouble(matcher.group(1));
        } catch (Exception ignored) {}
        return 0.0;
    }

    private static String mapujSymbolNaYahoo(String symbol, TypAktywa typ) {
        symbol = symbol.toUpperCase().trim();

        if (typ == TypAktywa.KRYPTO) {
            if (symbol.contains("USD")) return symbol.replace("USD", "-USD");
            return symbol + "-USD";
        }
        if (typ == TypAktywa.AKCJA_PL) {
            if (symbol.contains(".")) return symbol.substring(0, symbol.indexOf('.')) + ".WA";
            return symbol + ".WA";
        }

        // --- FIX DLA NIEMIEC I UK ---
        if (symbol.endsWith(".DE")) return symbol; // Yahoo rozumie .DE
        if (symbol.endsWith(".UK")) return symbol.replace(".UK", ".L"); // Yahoo używa .L dla Londynu!
        // ----------------------------

        if (typ == TypAktywa.AKCJA_USA) {
            if (symbol.equals("IUSQ") || symbol.equals("EUNL") || symbol.equals("IS3N")) return symbol + ".DE";
        }
        if (symbol.endsWith(".US")) return symbol.replace(".US", "");
        return symbol;
    }

    // --- STOOQ ---

    private static String mapujSymbolNaStooq(String symbol, TypAktywa typ) {
        symbol = symbol.toUpperCase().trim();
        if (typ == TypAktywa.KRYPTO) {
            if (symbol.contains("USD") && !symbol.contains(".")) return symbol;
            return symbol + ".V";
        }
        if (typ == TypAktywa.AKCJA_PL) {
            if (symbol.equals("ETFBS80TR") || symbol.equals("ETFBM40TR") || symbol.equals("ETFBW20TR")) return symbol + ".PL";
            return symbol.replace(".WA", "").replace(".PL", "");
        }
        if (symbol.endsWith(".DE")) return symbol;
        if (typ == TypAktywa.AKCJA_USA) {
            if (symbol.equals("IUSQ") || symbol.equals("EUNL") || symbol.equals("IS3N")) return symbol + ".DE";
        }
        if (symbol.endsWith(".UK")) return symbol;
        if (!symbol.contains(".")) return symbol + ".US";
        return symbol;
    }

    private static double pobierzZeStooqCSV(String ticker) {
        try {
            String urlStr = "https://stooq.pl/q/l/?s=" + ticker.toLowerCase() + "&f=sd2t2ohlc&h&e=csv";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(3000);

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            int lineCount = 0;
            while ((line = in.readLine()) != null) {
                lineCount++;
                if (lineCount == 2) {
                    if (line.trim().startsWith("<")) return 0.0;
                    String[] parts = line.split(",");
                    if (parts.length >= 7) {
                        try {
                            String priceStr = parts[6];
                            if (!priceStr.equalsIgnoreCase("N/A")) return Double.parseDouble(priceStr);
                        } catch (Exception ignored) {}
                    }
                }
            }
            in.close();
        } catch (Exception ignored) {}
        return 0.0;
    }
}