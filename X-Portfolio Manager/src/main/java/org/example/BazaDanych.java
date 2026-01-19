package org.example;

import java.io.*;
import java.util.*;

public class BazaDanych {

    private static final String FOLDER_NAME = "X-Portfolio Manager";
    private static final String ICONS_FILE = "portfolio_icons.properties";

    public static List<Aktywo> wczytaj(String nazwaPortfela) {
        List<Aktywo> lista = new ArrayList<>();
        File file = new File(FOLDER_NAME + File.separator + "portfel_" + nazwaPortfela + ".txt");

        if (!file.exists()) return lista;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                String[] parts = line.split(";");
                if (parts.length < 5) continue;

                String marker = parts[0];

                if (marker.equals("OBL_SKARB")) {
                    String sym = parts[1];
                    TypAktywa typZPliku = null;
                    try { typZPliku = TypAktywa.valueOf(parts[2]); } catch(Exception e) {}

                    double ilosc = Double.parseDouble(parts[3]);
                    String data = parts[4];

                    Double mRate = null;
                    Double mMargin = null;

                    if (parts.length > 6 && !parts[6].isEmpty()) {
                        try { mRate = Double.parseDouble(parts[6]); } catch(Exception e) {}
                    }
                    if (parts.length > 7 && !parts[7].isEmpty()) {
                        try { mMargin = Double.parseDouble(parts[7]); } catch(Exception e) {}
                    }
                    lista.add(new ObligacjaSkarbowa(sym, typZPliku, ilosc, data, mRate, mMargin));
                }
                else if (marker.equals("AKCJA_PL") || marker.equals("AKCJA_USA") || marker.equals("KRYPTO")) {
                    try {
                        String symbol = parts[1];
                        TypAktywa typ = TypAktywa.valueOf(parts[2]);
                        double ilosc = Double.parseDouble(parts[3]);
                        String data = parts[4];
                        Waluta waluta = Waluta.valueOf(parts[5]);
                        double cenaZakupu = (parts.length > 6) ? Double.parseDouble(parts[6]) : 0.0;

                        lista.add(new AktywoRynkowe(symbol, typ, ilosc, data, waluta, cenaZakupu));
                    } catch (Exception e) {
                        System.err.println("Błąd wczytywania aktywa: " + line);
                    }
                }
                else if (marker.equals("OBL_STALA")) {
                    lista.add(new ObligacjaSkarbowa(parts[1], TypAktywa.OBLIGACJA_STALA, Double.parseDouble(parts[3]), parts[4], null, null));
                }
                else if (marker.equals("OBL_INDEKS")) {
                    lista.add(new ObligacjaSkarbowa(parts[1], TypAktywa.OBLIGACJA_INDEKSOWANA, Double.parseDouble(parts[3]), parts[4], null, null));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lista;
    }

    public static void zapisz(String nazwaPortfela, List<Aktywo> aktywa) {
        File folder = new File(FOLDER_NAME);
        if (!folder.exists()) folder.mkdir();

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FOLDER_NAME + File.separator + "portfel_" + nazwaPortfela + ".txt"))) {
            for (Aktywo a : aktywa) {
                bw.write(a.toCSV());
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<String> pobierzNazwyPortfeli() {
        List<String> nazwy = new ArrayList<>();
        File folder = new File(FOLDER_NAME);
        if (!folder.exists()) return nazwy;

        File[] files = folder.listFiles((dir, name) -> name.startsWith("portfel_") && name.endsWith(".txt"));
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                nazwy.add(n.substring(8, n.length() - 4));
            }
        }
        Collections.sort(nazwy);
        return nazwy;
    }

    public static void utworzPortfel(String nazwa) {
        zapisz(nazwa, new ArrayList<>());
        ustawIkone(nazwa, "wallet.png");
        // Tworzymy też pusty plik historii
        zapiszHistorie(nazwa, new ArrayList<>());
    }

    public static void usunPortfel(String nazwa) {
        File f = new File(FOLDER_NAME + File.separator + "portfel_" + nazwa + ".txt");
        if (f.exists()) f.delete();
        File fh = new File(FOLDER_NAME + File.separator + "historia_" + nazwa + ".txt");
        if (fh.exists()) fh.delete();
        usunIkone(nazwa);
    }

    public static void zmienNazwePortfela(String staraNazwa, String nowaNazwa) {
        File stary = new File(FOLDER_NAME + File.separator + "portfel_" + staraNazwa + ".txt");
        File nowy = new File(FOLDER_NAME + File.separator + "portfel_" + nowaNazwa + ".txt");
        if (stary.exists()) {
            stary.renameTo(nowy);

            // Zmiana nazwy historii
            File staryH = new File(FOLDER_NAME + File.separator + "historia_" + staraNazwa + ".txt");
            File nowyH = new File(FOLDER_NAME + File.separator + "historia_" + nowaNazwa + ".txt");
            if (staryH.exists()) staryH.renameTo(nowyH);

            String icon = pobierzIkone(staraNazwa);
            usunIkone(staraNazwa);
            ustawIkone(nowaNazwa, icon);
        }
    }

    public static String pobierzIkone(String nazwaPortfela) {
        Properties props = loadIcons();
        return props.getProperty(nazwaPortfela, "wallet.png");
    }

    public static void ustawIkone(String nazwaPortfela, String nazwaIkony) {
        Properties props = loadIcons();
        props.setProperty(nazwaPortfela, nazwaIkony);
        saveIcons(props);
    }

    private static void usunIkone(String nazwaPortfela) {
        Properties props = loadIcons();
        props.remove(nazwaPortfela);
        saveIcons(props);
    }

    private static Properties loadIcons() {
        Properties props = new Properties();
        File f = new File(FOLDER_NAME + File.separator + ICONS_FILE);
        if (f.exists()) {
            try (FileInputStream in = new FileInputStream(f)) {
                props.load(in);
            } catch (IOException e) { e.printStackTrace(); }
        }
        return props;
    }

    private static void saveIcons(Properties props) {
        File folder = new File(FOLDER_NAME);
        if (!folder.exists()) folder.mkdir();
        try (FileOutputStream out = new FileOutputStream(FOLDER_NAME + File.separator + ICONS_FILE)) {
            props.store(out, "Konfiguracja ikon portfeli");
        } catch (IOException e) { e.printStackTrace(); }
    }

    // --- NOWE METODY DO OBSŁUGI HISTORII ---

    public static List<Transakcja> wczytajHistorie(String nazwaPortfela) {
        List<Transakcja> lista = new ArrayList<>();
        File file = new File(FOLDER_NAME + File.separator + "historia_" + nazwaPortfela + ".txt");
        if (!file.exists()) return lista;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(";");
                if (parts.length < 6) continue;
                // data, typ, symbol, ilosc, wartosc, waluta
                lista.add(new Transakcja(parts[0], parts[1], parts[2], Double.parseDouble(parts[3]), Double.parseDouble(parts[4]), parts[5]));
            }
        } catch (Exception e) { e.printStackTrace(); }
        return lista;
    }

    public static void zapiszHistorie(String nazwaPortfela, List<Transakcja> historia) {
        File folder = new File(FOLDER_NAME);
        if (!folder.exists()) folder.mkdir();

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FOLDER_NAME + File.separator + "historia_" + nazwaPortfela + ".txt"))) {
            for (Transakcja t : historia) {
                bw.write(t.toCSV());
                bw.newLine();
            }
        } catch (IOException e) { e.printStackTrace(); }
    }
}