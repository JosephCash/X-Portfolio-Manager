package org.example;

import org.apache.poi.ss.usermodel.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XtbImport {

    // Kursy awaryjne (używane tylko, gdy nie ma info w komentarzu)
    private static final double FALLBACK_USD_PLN = 4.0;
    private static final double FALLBACK_EUR_PLN = 4.3;

    public static class ImportResult {
        public int znalezionePliki = 0;
        public double sumaWplat = 0;
        public List<Aktywo> aktywa = new ArrayList<>();
        public List<Transakcja> historiaTransakcji = new ArrayList<>();
        public StringBuilder logi = new StringBuilder();
    }

    public static ImportResult importujPliki(List<File> pliki) {
        ImportResult result = new ImportResult();
        if (pliki == null || pliki.isEmpty()) return result;

        // Zbiory do unikania duplikatów
        Set<String> processedCashIds = new HashSet<>();
        Set<String> processedPositionIds = new HashSet<>();

        for (File f : pliki) {
            if (f == null || !f.exists() || !f.isFile()) continue;
            String name = f.getName().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) continue;

            boolean cosZnaleziono = false;
            try (InputStream in = new FileInputStream(f);
                 Workbook wb = WorkbookFactory.create(in)) {

                // Wykrywanie waluty konta
                String walutaKonta = detectAccountCurrency(wb);

                // 1. OPEN POSITION (Otwarte pozycje - Twoje aktualne akcje)
                Sheet openSheet = findSheet(wb, "OPEN POSITION");
                if (openSheet != null) {
                    parseOpenSheet(openSheet, result, processedPositionIds);
                    cosZnaleziono = true;
                }

                // 2. CASH OPERATION (Historia wpłat/wypłat)
                Sheet cashSheet = findSheet(wb, "CASH OPERATION");
                if (cashSheet != null) {
                    parseCashSheet(cashSheet, result, walutaKonta, processedCashIds);
                    cosZnaleziono = true;
                }

                // 3. CLOSED TRANSACTION (Historia zamkniętych)
                Sheet closedSheet = findSheet(wb, "CLOSED TRANSACTION");
                if (closedSheet != null) { parseClosedSheet(closedSheet, result); cosZnaleziono = true; }

                if (cosZnaleziono) result.znalezionePliki++;

            } catch (Exception e) {
                e.printStackTrace();
                result.logi.append("Błąd pliku ").append(name).append(": ").append(e.getMessage()).append("\n");
            }
        }
        return result;
    }

    private static String detectAccountCurrency(Workbook wb) {
        try {
            Sheet s = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter(Locale.ROOT);

            for (int r = 0; r < 20; r++) {
                Row row = s.getRow(r);
                if (row == null) continue;
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String val = clean(cellAsString(row.getCell(c), fmt)).toUpperCase();
                    if (val.contains("CURRENCY")) {
                        String nextVal = clean(cellAsString(row.getCell(c+1), fmt)).toUpperCase();
                        if (isCurrency(nextVal)) return nextVal;

                        Row nextRow = s.getRow(r+1);
                        if (nextRow != null) {
                            String underVal = clean(cellAsString(nextRow.getCell(c), fmt)).toUpperCase();
                            if (isCurrency(underVal)) return underVal;
                        }
                    }
                }
            }
        } catch (Exception e) { }
        return "PLN";
    }

    private static boolean isCurrency(String s) {
        return s.equals("PLN") || s.equals("USD") || s.equals("EUR");
    }

    private static Sheet findSheet(Workbook wb, String partialName) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            Sheet s = wb.getSheetAt(i);
            if (s.getSheetName().toUpperCase().contains(partialName)) return s;
        }
        return null;
    }

    private static void parseOpenSheet(Sheet sheet, ImportResult result, Set<String> processedPositionIds) {
        DataFormatter fmt = new DataFormatter(Locale.ROOT);
        int headerRowIdx = findHeaderRow(sheet, fmt, "SYMBOL", "TYPE", "VOLUME");
        if(headerRowIdx == -1) return;

        Row header = sheet.getRow(headerRowIdx);
        Map<String, Integer> col = mapHeaderColumns(header, fmt);

        Integer cPosId = col.get("POSITION");
        Integer cSymbol = col.get("SYMBOL");
        Integer cType = col.get("TYPE");
        Integer cVolume = col.get("VOLUME");
        Integer cOpenPrice = col.get("OPEN PRICE");
        Integer cMarketPrice = col.get("MARKET PRICE");
        Integer cTime = col.get("OPEN TIME");

        for(int r=headerRowIdx+1; r<=sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r); if(row==null) continue;
            try {
                String type = (cType != null) ? clean(cellAsString(row.getCell(cType), fmt)) : "";
                if(!type.equalsIgnoreCase("BUY")) continue;

                if (cPosId != null) {
                    String id = clean(cellAsString(row.getCell(cPosId), fmt));
                    if (!id.isEmpty()) {
                        if (processedPositionIds.contains(id)) continue;
                        processedPositionIds.add(id);
                    }
                }

                String sym = (cSymbol != null) ? clean(cellAsString(row.getCell(cSymbol), fmt)) : "";
                double vol = (cVolume != null) ? cellAsDouble(row.getCell(cVolume), fmt) : 0.0;

                // Używamy Market Price jeśli dostępna, w przeciwnym razie Open Price
                double price = 0.0;
                if (cMarketPrice != null) {
                    price = cellAsDouble(row.getCell(cMarketPrice), fmt);
                }
                if (price == 0.0 && cOpenPrice != null) {
                    price = cellAsDouble(row.getCell(cOpenPrice), fmt);
                }

                String date = (cTime != null) ? cellAsDateYYYYMMDD(row.getCell(cTime), fmt) : LocalDate.now().toString();

                if(!sym.isEmpty() && vol > 0) {
                    result.aktywa.add(tworzAktywo(sym, vol, price, date));
                }
            } catch(Exception e){}
        }
    }

    private static void parseCashSheet(Sheet sheet, ImportResult result, String accountCurrency, Set<String> processedCashIds) {
        DataFormatter fmt = new DataFormatter(Locale.ROOT);
        int headerRowIdx = findHeaderRow(sheet, fmt, "TYPE", "AMOUNT", "TIME");
        if (headerRowIdx == -1) return;

        Row header = sheet.getRow(headerRowIdx);
        Map<String, Integer> col = mapHeaderColumns(header, fmt);

        Integer cType = col.get("TYPE");
        Integer cAmount = col.get("AMOUNT");
        Integer cTime = col.get("TIME");
        Integer cComment = col.get("COMMENT");
        Integer cId = col.get("ID");

        if (cType == null || cAmount == null) return;

        Pattern ratePattern = Pattern.compile("Exchange rate:([0-9.]+)");

        for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            try {
                if (cId != null) {
                    String id = clean(cellAsString(row.getCell(cId), fmt));
                    if (!id.isEmpty()) {
                        if (processedCashIds.contains(id)) continue;
                        processedCashIds.add(id);
                    }
                }

                String type = clean(cellAsString(row.getCell(cType), fmt)).toUpperCase();
                double amount = cellAsDouble(row.getCell(cAmount), fmt);
                String dateStr = (cTime != null) ? cellAsFullDate(row.getCell(cTime), fmt) : "";
                String comment = (cComment != null) ? clean(cellAsString(row.getCell(cComment), fmt)).replace(",", ".") : "";

                if (Math.abs(amount) < 0.001) continue;

                boolean isDeposit = false;
                if (type.contains("DEPOSIT") || type.contains("WPŁATA") || type.contains("WPLATA")) isDeposit = true;
                if (type.contains("OPENING BALANCE") || comment.toUpperCase().contains("OPENING BALANCE")) isDeposit = true;
                if ((type.contains("TRANSFER") || comment.toUpperCase().contains("TRANSFER")) && amount > 0) isDeposit = true;

                if (isDeposit) {
                    double amountPLN = 0.0;
                    if (accountCurrency.equals("PLN")) {
                        amountPLN = amount;
                    } else {
                        Matcher m = ratePattern.matcher(comment);
                        if (m.find()) {
                            try {
                                double rate = Double.parseDouble(m.group(1));
                                if (rate > 0) {
                                    String upperComment = comment.toUpperCase();
                                    if (upperComment.contains("PLN TO " + accountCurrency)) {
                                        amountPLN = amount / rate;
                                    } else if (upperComment.contains("EUR TO " + accountCurrency)) {
                                        double amountEUR = amount / rate;
                                        amountPLN = amountEUR * FALLBACK_EUR_PLN;
                                    } else {
                                        amountPLN = estimateByFallback(amount, accountCurrency);
                                    }
                                }
                            } catch (Exception e) {
                                amountPLN = estimateByFallback(amount, accountCurrency);
                            }
                        } else {
                            amountPLN = estimateByFallback(amount, accountCurrency);
                        }
                    }
                    result.sumaWplat += amountPLN;
                    result.historiaTransakcji.add(new Transakcja(dateStr, "DEPOSIT", accountCurrency, amount, amountPLN, "PLN"));
                }
                else if (type.contains("WITHDRAW") || type.contains("WYPŁATA")) {
                    double amountPLN = estimateByFallback(Math.abs(amount), accountCurrency);
                    result.historiaTransakcji.add(new Transakcja(dateStr, "WITHDRAW", accountCurrency, Math.abs(amount), amountPLN, "PLN"));
                }

            } catch (Exception ignored) {}
        }
    }

    private static double estimateByFallback(double amount, String currency) {
        if (currency.equals("USD")) return amount * FALLBACK_USD_PLN;
        if (currency.equals("EUR")) return amount * FALLBACK_EUR_PLN;
        return amount;
    }

    private static void parseClosedSheet(Sheet sheet, ImportResult result) {
        // Bez zmian
    }

    private static int findHeaderRow(Sheet sheet, DataFormatter fmt, String... requiredCols) {
        for(int r=0; r<Math.min(sheet.getLastRowNum(), 50); r++) {
            Row row = sheet.getRow(r); if(row==null) continue;
            String line = "";
            for(int c=0; c<row.getLastCellNum(); c++) line += clean(cellAsString(row.getCell(c), fmt)).toUpperCase() + " ";
            boolean ok = true;
            for(String req : requiredCols) if(!line.contains(req)) ok=false;
            if(ok) return r;
        }
        return -1;
    }

    private static Map<String, Integer> mapHeaderColumns(Row header, DataFormatter fmt) {
        Map<String, Integer> col = new HashMap<>();
        for(int c=0; c<header.getLastCellNum(); c++) col.put(clean(cellAsString(header.getCell(c), fmt)).toUpperCase(), c);
        return col;
    }

    private static String cellAsString(Cell c, DataFormatter f) { return c==null ? "" : f.formatCellValue(c); }

    private static double cellAsDouble(Cell c, DataFormatter f) {
        if(c==null) return 0;
        if(c.getCellType()==CellType.NUMERIC) return c.getNumericCellValue();
        try { return Double.parseDouble(clean(cellAsString(c, f)).replace(" ","").replace(",",".")); } catch(Exception e) { return 0; }
    }

    private static String cellAsDateYYYYMMDD(Cell c, DataFormatter f) {
        if(c!=null && c.getCellType()==CellType.NUMERIC && DateUtil.isCellDateFormatted(c))
            return c.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString();
        return clean(cellAsString(c, f)).split(" ")[0];
    }

    private static String cellAsFullDate(Cell c, DataFormatter f) {
        if(c!=null && c.getCellType()==CellType.NUMERIC && DateUtil.isCellDateFormatted(c))
            return c.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return clean(cellAsString(c, f));
    }

    private static String clean(String s) { return s==null ? "" : s.trim().replace("\"", "").replace("\u00A0",""); }

    // --- POPRAWIONA METODA TWORZENIA AKTYWA ---
    private static Aktywo tworzAktywo(String s, double ilosc, double cena, String data) {
        s = s.trim();
        Waluta w = Waluta.USD;
        TypAktywa t = TypAktywa.AKCJA_USA;

        if(s.endsWith(".PL") || (s.startsWith("ETF") && s.contains("PL"))) {
            w = Waluta.PLN;
            t = TypAktywa.AKCJA_PL;
            s = s.replace(".PL","");
        }
        else if(s.endsWith(".DE")) {
            w = Waluta.EUR;
        }
        else if(s.endsWith(".UK")) {
            // FIX: EIMI.UK, CNDX.UK, CSPX.UK itp. są notowane w USD na LSE, a nie w GBP.
            // Sprawdzamy popularne ETF-y dolarowe, które mają suffix .UK w XTB
            if (s.startsWith("EIMI") || s.startsWith("CNDX") || s.startsWith("CSPX") || s.startsWith("ISPE") || s.startsWith("IDPE")) {
                w = Waluta.USD;
            } else {
                w = Waluta.GBP; // Domyślnie dla reszty UK (np. akcje brytyjskie)
            }
        }

        return new AktywoRynkowe(s, t, ilosc, data, w, cena);
    }
}