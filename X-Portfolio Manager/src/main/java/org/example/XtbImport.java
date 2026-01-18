package org.example;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class XtbImport {

    public static class ImportResult {
        public int znalezionePliki = 0;
        public double sumaWplat = 0;
        public List<Aktywo> aktywa = new ArrayList<>();
        public StringBuilder logi = new StringBuilder();
    }

    public static ImportResult importujPliki(List<File> pliki) {
        ImportResult result = new ImportResult();

        if (pliki == null || pliki.isEmpty()) {
            result.logi.append("Błąd: nie podano plików.\n");
            return result;
        }

        for (File f : pliki) {
            if (f == null || !f.exists() || !f.isFile()) continue;

            String name = f.getName().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) continue;

            if (parseExcelOpenPositions(f, result)) {
                result.znalezionePliki++;
            }
        }

        if (result.znalezionePliki == 0) {
            result.logi.append("Błąd: nie znaleziono arkusza 'OPEN POSITION' w żadnym pliku.\n");
        } else if (result.aktywa.isEmpty()) {
            result.logi.append("Arkusz znaleziony, ale nie odczytano żadnej pozycji.\n");
        }

        return result;
    }

    private static boolean parseExcelOpenPositions(File file, ImportResult result) {
        try (InputStream in = new FileInputStream(file);
             Workbook wb = WorkbookFactory.create(in)) {

            Sheet openSheet = null;
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet s = wb.getSheetAt(i);
                String sheetName = s.getSheetName() == null ? "" : s.getSheetName();
                if (sheetName.toUpperCase(Locale.ROOT).contains("OPEN POSITION")) {
                    openSheet = s;
                    break;
                }
            }

            if (openSheet == null) {
                result.logi.append("Brak arkusza OPEN POSITION w pliku: ").append(file.getName()).append("\n");
                return false;
            }

            parseOpenSheet(openSheet, result);
            return true;

        } catch (Exception e) {
            result.logi.append("Błąd odczytu Excela ").append(file.getName()).append(": ")
                    .append(e.getMessage()).append("\n");
            return false;
        }
    }

    private static void parseOpenSheet(Sheet sheet, ImportResult result) {
        DataFormatter fmt = new DataFormatter(Locale.ROOT);

        int headerRowIdx = findHeaderRow(sheet, fmt);
        if (headerRowIdx == -1) {
            result.logi.append("Nie znaleziono nagłówka w arkuszu: ").append(sheet.getSheetName()).append("\n");
            return;
        }

        Row header = sheet.getRow(headerRowIdx);
        Map<String, Integer> col = mapHeaderColumns(header, fmt);

        Integer cSymbol = col.get("SYMBOL");
        Integer cType = col.get("TYPE");
        Integer cVolume = col.get("VOLUME");
        Integer cOpenTime = col.get("OPEN TIME");
        Integer cOpenPrice = col.get("OPEN PRICE");

        if (cSymbol == null || cType == null || cVolume == null || cOpenTime == null || cOpenPrice == null) {
            result.logi.append("Brak wymaganych kolumn w arkuszu ")
                    .append(sheet.getSheetName())
                    .append(". Znalazłem: ").append(col.keySet()).append("\n");
            return;
        }

        for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String type = clean(cellAsString(row.getCell(cType), fmt));
            if (!type.equalsIgnoreCase("BUY")) continue;

            try {
                String symbol = clean(cellAsString(row.getCell(cSymbol), fmt));
                double volume = cellAsDouble(row.getCell(cVolume), fmt);
                double openPrice = cellAsDouble(row.getCell(cOpenPrice), fmt);
                String openDate = cellAsDateYYYYMMDD(row.getCell(cOpenTime), fmt);

                if (symbol.isEmpty() || volume == 0 || openPrice == 0 || openDate.isEmpty()) continue;

                result.aktywa.add(tworzAktywo(symbol, volume, openPrice, openDate));
            } catch (Exception ignore) {
            }
        }
    }

    private static int findHeaderRow(Sheet sheet, DataFormatter fmt) {
        int maxScan = Math.min(sheet.getLastRowNum(), 200);
        for (int r = 0; r <= maxScan; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            Set<String> seen = new HashSet<>();
            for (int c = 0; c < Math.min(row.getLastCellNum(), 60); c++) {
                String v = clean(cellAsString(row.getCell(c), fmt)).toUpperCase(Locale.ROOT);
                if (!v.isEmpty()) seen.add(v);
            }

            if (seen.contains("SYMBOL") &&
                    seen.contains("TYPE") &&
                    seen.contains("VOLUME") &&
                    seen.contains("OPEN TIME") &&
                    seen.contains("OPEN PRICE")) {
                return r;
            }
        }
        return -1;
    }

    private static Map<String, Integer> mapHeaderColumns(Row header, DataFormatter fmt) {
        Map<String, Integer> col = new HashMap<>();
        if (header == null) return col;

        for (int c = 0; c < header.getLastCellNum(); c++) {
            String key = clean(cellAsString(header.getCell(c), fmt)).toUpperCase(Locale.ROOT);
            if (!key.isEmpty()) col.put(key, c);
        }
        return col;
    }

    private static String cellAsString(Cell cell, DataFormatter fmt) {
        if (cell == null) return "";
        return fmt.formatCellValue(cell);
    }

    private static double cellAsDouble(Cell cell, DataFormatter fmt) {
        if (cell == null) return 0.0;
        if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();

        String s = clean(cellAsString(cell, fmt)).replace(" ", "").replace(",", ".");
        if (s.isEmpty()) return 0.0;
        return Double.parseDouble(s);
    }

    private static String cellAsDateYYYYMMDD(Cell cell, DataFormatter fmt) {
        if (cell == null) return "";

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDate ld = cell.getDateCellValue().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            return ld.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }

        String s = clean(cellAsString(cell, fmt));
        if (s.isEmpty()) return "";
        if (s.contains(" ")) s = s.split(" ")[0];
        return s;
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim().replace("\"", "");
    }

    private static Aktywo tworzAktywo(String symbolRaw, double ilosc, double cenaZakupu, String dataZakupu) {
        Waluta waluta = Waluta.USD;
        TypAktywa typ = TypAktywa.AKCJA_USA;

        symbolRaw = symbolRaw.trim();

        if (symbolRaw.endsWith(".UK")) {
            // POPRAWKA: EIMI.UK jest notowany w USD, mimo że jest na giełdzie w UK.
            // Sprawdzamy, czy to ten konkretny ETF (lub inne znane wyjątki).
            if (symbolRaw.equalsIgnoreCase("EIMI.UK") || symbolRaw.contains("USD")) {
                waluta = Waluta.USD;
            } else {
                waluta = Waluta.GBP;
            }
            typ = TypAktywa.AKCJA_USA;
        } else if (symbolRaw.endsWith(".DE")) {
            waluta = Waluta.EUR;
            typ = TypAktywa.AKCJA_USA;
        } else if (symbolRaw.endsWith(".PL")) {
            waluta = Waluta.PLN;
            typ = TypAktywa.AKCJA_PL;
            symbolRaw = symbolRaw.replace(".PL", "");
        } else if (symbolRaw.startsWith("ETF") && (symbolRaw.endsWith("TR") || symbolRaw.contains("M40"))) {
            waluta = Waluta.PLN;
            typ = TypAktywa.AKCJA_PL;
        }

        if (symbolRaw.equals("BTC") || symbolRaw.equals("ETH") || symbolRaw.equals("SOL") || symbolRaw.startsWith("BTC.")) {
            typ = TypAktywa.KRYPTO;
            waluta = Waluta.USDT;
        }

        if (dataZakupu.contains(" ")) dataZakupu = dataZakupu.split(" ")[0];

        return new AktywoRynkowe(symbolRaw, typ, ilosc, dataZakupu, waluta, cenaZakupu);
    }
}
