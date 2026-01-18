package org.example;

import java.io.*;
import java.util.*;

public class BazaDanych {
    private static final String PREFIX = "portfel_";
    private static final String EXT = ".txt";
    private static final String CONFIG_FILE = "portfolio_icons.properties";
    private static final Properties iconProps = new Properties();

    static {
        // Migracja starego pliku
        File stary = new File("portfel.txt");
        if (stary.exists()) stary.renameTo(new File(PREFIX + "Główny" + EXT));

        // Ładowanie konfiguracji ikon
        zaladujIkony();
    }

    private static void zaladujIkony() {
        try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
            iconProps.load(in);
        } catch (IOException e) {
            // Plik pewnie nie istnieje, to normalne przy pierwszym uruchomieniu
        }
    }

    private static void zapiszIkony() {
        try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
            iconProps.store(out, "Przypisanie ikon do portfeli");
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void ustawIkone(String nazwaPortfela, String nazwaIkony) {
        iconProps.setProperty(nazwaPortfela, nazwaIkony);
        zapiszIkony();
    }

    public static String pobierzIkone(String nazwaPortfela) {
        return iconProps.getProperty(nazwaPortfela, "wallet.png"); // Domyślna ikona
    }

    public static List<String> pobierzNazwyPortfeli() {
        List<String> portfele = new ArrayList<>();
        File folder = new File(".");
        File[] pliki = folder.listFiles((dir, name) -> name.startsWith(PREFIX) && name.endsWith(EXT));
        if (pliki != null) {
            for (File f : pliki) portfele.add(f.getName().replace(PREFIX, "").replace(EXT, ""));
        }
        return portfele;
    }

    public static void utworzPortfel(String nazwa) {
        try { new File(PREFIX + nazwa + EXT).createNewFile(); } catch (IOException e) { e.printStackTrace(); }
    }

    public static boolean zmienNazwePortfela(String stara, String nowa) {
        File f1 = new File(PREFIX + stara + EXT);
        File f2 = new File(PREFIX + nowa + EXT);

        if (f1.exists() && !f2.exists()) {
            boolean sukces = f1.renameTo(f2);
            if (sukces) {
                // Przenosimy też ikonę w konfiguracji
                String icon = iconProps.getProperty(stara);
                if (icon != null) {
                    iconProps.remove(stara);
                    iconProps.setProperty(nowa, icon);
                    zapiszIkony();
                }
            }
            return sukces;
        }
        return false;
    }

    public static void usunPortfel(String nazwa) {
        File f = new File(PREFIX + nazwa + EXT);
        if(f.exists()) f.delete();
        // Usuwamy wpis o ikonie
        if (iconProps.containsKey(nazwa)) {
            iconProps.remove(nazwa);
            zapiszIkony();
        }
    }

    public static void zapisz(List<Aktywo> lista, String nazwa) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(PREFIX + nazwa + EXT))) {
            for (Aktywo a : lista) writer.println(a.toCSV());
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static List<Aktywo> wczytaj(String nazwa) {
        List<Aktywo> lista = new ArrayList<>();
        File file = new File(PREFIX + nazwa + EXT);
        if (!file.exists()) return lista;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(";");
                if (parts.length < 6) continue;

                String klasa = parts[0];
                String symbol = parts[1]; // <--- TUTAJ BYŁ BŁĄD (było 'sym')
                TypAktywa typ = TypAktywa.valueOf(parts[2]);
                double ilosc = Double.parseDouble(parts[3]);
                String data = parts[4];
                Waluta waluta = Waluta.valueOf(parts[5]);

                if (klasa.equals("RYNEK")) {
                    double cena = (parts.length > 6) ? Double.parseDouble(parts[6]) : 0.0;
                    lista.add(new AktywoRynkowe(symbol, typ, ilosc, data, waluta, cena));
                } else if (klasa.equals("OBL_STALA")) {
                    lista.add(new ObligacjaStala(symbol, ilosc, data, Double.parseDouble(parts[6])));
                } else if (klasa.equals("OBL_INDEKS")) {
                    lista.add(new ObligacjaIndeksowana(symbol, ilosc, data, Double.parseDouble(parts[6]), Double.parseDouble(parts[7])));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return lista;
    }
}