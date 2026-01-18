package org.example;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class Main extends JPanel {

    // --- KOLORYSTYKA (Zdefiniowana lokalnie, aby uniknąć błędów) ---
    private static final Color BG = new Color(18, 18, 18);
    private static final Color CARD_BG = new Color(30, 30, 30);
    private static final Color INPUT_BG = new Color(45, 45, 45); // <--- TUTAJ BYŁ BŁĄD, TERAZ JEST OK
    private static final Color TEXT_MAIN = new Color(220, 220, 220);
    private static final Color TEXT_DIM = new Color(150, 150, 150);
    private static final Color BORDER = new Color(60, 60, 60);
    private static final Color GREEN = new Color(39, 174, 96);
    private static final Color RED = new Color(192, 57, 43);
    private static final Color BLUE = new Color(41, 128, 185);
    private static final Color SELECTION_BG = new Color(70, 70, 70);

    private static final Font FONT_STD = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font FONT_ARROW = new Font("Segoe UI Symbol", Font.PLAIN, 10);

    private JTable tabela;
    private DefaultTableModel modelTabeli;
    private JLabel labelSuma, labelStatus;
    private JButton przyciskOdswiez;

    private List<Aktywo> portfel;
    private String nazwaPortfela;
    private PortfolioManager parentManager;
    private Set<String> rozwinieteGrupy = new HashSet<>();
    private Map<Integer, Aktywo> mapaWierszyDoAktywow = new HashMap<>();

    private double currentUSD, currentEUR, currentGBP;

    public Main(String nazwaPortfela, PortfolioManager manager) {
        this.nazwaPortfela = nazwaPortfela;
        this.parentManager = manager;
        this.currentUSD = MarketData.pobierzKursUSD();
        this.currentEUR = MarketData.pobierzKursEUR();
        this.currentGBP = MarketData.pobierzKursGBP();

        setLayout(new BorderLayout());
        setBackground(BG);
        portfel = BazaDanych.wczytaj(nazwaPortfela);

        // --- NAGŁÓWEK ---
        JPanel header = new JPanel(new BorderLayout()); header.setBackground(BG); header.setBorder(new EmptyBorder(15, 40, 15, 40));
        JPanel leftHead = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); leftHead.setOpaque(false);
        JButton btnBack = new JButton("← WRÓĆ"); styleMiniButton(btnBack); btnBack.addActionListener(e -> parentManager.wrocDoDashboard());
        JLabel title = new JLabel("  " + nazwaPortfela.toUpperCase()); title.setFont(new Font("Segoe UI", Font.BOLD, 24)); title.setForeground(TEXT_MAIN);
        leftHead.add(btnBack); leftHead.add(title);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0)); toolbar.setBackground(BG);
        JButton btnAdd = new JButton("DODAJ"); styleMinimalistButton(btnAdd); btnAdd.addActionListener(e -> pokazOknoDodawania());
        JButton btnImp = new JButton("IMPORT XTB"); styleMinimalistButton(btnImp); btnImp.addActionListener(e -> pokazOknoImportu());
        JButton btnDel = new JButton("USUŃ"); styleMinimalistButton(btnDel); btnDel.addActionListener(e -> usunZaznaczone());
        toolbar.add(btnAdd); toolbar.add(btnImp); toolbar.add(btnDel);

        header.add(leftHead, BorderLayout.WEST); header.add(toolbar, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // --- TABELA ---
        String[] cols = {"Symbol", "Typ", "Data / Ilość Poz.", "Ilość", "Śr. Cena / Cena", "Cena Rynkowa", "Wartość (PLN)"};
        modelTabeli = new DefaultTableModel(cols, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
        tabela = new JTable(modelTabeli);
        stylizujTabele();

        tabela.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                int r = tabela.rowAtPoint(e.getPoint());
                if (r >= 0 && tabela.columnAtPoint(e.getPoint()) == 0) {
                    String val = (String) tabela.getValueAt(r, 0);
                    if (val != null && (val.contains("▶") || val.contains("▼"))) {
                        String sym = val.replace("▶", "").replace("▼", "").trim();
                        if (rozwinieteGrupy.contains(sym)) rozwinieteGrupy.remove(sym); else rozwinieteGrupy.add(sym);
                        przeliczWidok();
                    }
                }
            }
        });
        tabela.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                int r = tabela.rowAtPoint(e.getPoint());
                if(r>=0 && tabela.columnAtPoint(e.getPoint())==0) {
                    String v=(String)tabela.getValueAt(r,0);
                    if(v!=null&&(v.contains("▶")||v.contains("▼"))) { tabela.setCursor(new Cursor(Cursor.HAND_CURSOR)); return; }
                }
                tabela.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
        });

        JScrollPane scroll = new JScrollPane(tabela);
        PortfolioManager.styleScrollPane(scroll);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setViewportBorder(null);
        scroll.getViewport().setBackground(CARD_BG);
        scroll.setBackground(CARD_BG);

        JPanel corner = new JPanel(); corner.setBackground(BG);
        scroll.setCorner(ScrollPaneConstants.UPPER_RIGHT_CORNER, corner);

        JPanel tableContainer = new JPanel(new BorderLayout()); tableContainer.setBackground(BG); tableContainer.setBorder(new EmptyBorder(0, 40, 0, 40));
        tableContainer.add(scroll, BorderLayout.CENTER);
        add(tableContainer, BorderLayout.CENTER);

        // --- STOPKA ---
        JPanel footer = new JPanel(new BorderLayout()); footer.setBackground(CARD_BG); footer.setBorder(new EmptyBorder(15, 40, 15, 40));
        labelStatus = new JLabel(" Gotowy."); labelStatus.setForeground(TEXT_DIM);
        JPanel rightFoot = new JPanel(new FlowLayout(FlowLayout.RIGHT)); rightFoot.setOpaque(false);
        labelSuma = new JLabel("0.00 PLN"); labelSuma.setFont(new Font("Segoe UI", Font.BOLD, 24)); labelSuma.setForeground(GREEN);
        przyciskOdswiez = new JButton("ODŚWIEŻ CENY"); PortfolioManager.styleButton(przyciskOdswiez, GREEN); przyciskOdswiez.addActionListener(e -> odswiezCeny());
        rightFoot.add(labelSuma); rightFoot.add(Box.createHorizontalStrut(20)); rightFoot.add(przyciskOdswiez);
        footer.add(labelStatus, BorderLayout.WEST); footer.add(rightFoot, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        przeliczWidok();
        if (!MarketData.czyDaneZCache()) odswiezCeny(); else labelStatus.setText(" Dane załadowane z pamięci podręcznej.");
    }

    private void odswiezCeny() {
        przyciskOdswiez.setEnabled(false); przyciskOdswiez.setBackground(BORDER); labelStatus.setText(" Pobieranie aktualnych cen...");
        new SwingWorker<Void, Void>() {
            double u, e, g;
            @Override protected Void doInBackground() {
                u = MarketData.pobierzKursUSD(); e = MarketData.pobierzKursEUR(); g = MarketData.pobierzKursGBP();
                if(u==0) u=currentUSD; if(e==0) e=currentEUR; if(g==0) g=currentGBP;
                for(Aktywo a : portfel) { if(a instanceof AktywoRynkowe) { a.getCenaJednostkowa(); try{Thread.sleep(150);}catch(Exception x){} } }
                return null;
            }
            @Override protected void done() { currentUSD=u; currentEUR=e; currentGBP=g; przeliczWidok(); labelStatus.setText(" Zaktualizowano kursy i ceny."); przyciskOdswiez.setEnabled(true); przyciskOdswiez.setBackground(GREEN); }
        }.execute();
    }

    private void przeliczWidok() {
        int sel = tabela.getSelectedRow(); modelTabeli.setRowCount(0); mapaWierszyDoAktywow.clear(); double sumaTotal = 0;
        Map<String, List<Aktywo>> grupy = portfel.stream().collect(Collectors.groupingBy(a -> a.symbol));
        List<String> klucze = new ArrayList<>(grupy.keySet()); Collections.sort(klucze);
        int r = 0;
        for (String sym : klucze) {
            List<Aktywo> lista = grupy.get(sym);
            if (lista.size() == 1) {
                Aktywo a = lista.get(0); double w = a.getWartoscPLN(currentUSD, currentEUR, currentGBP); sumaTotal += w; dodajWiersz(a, r++, false);
            } else {
                double ilosc = 0, wartosc = 0, koszt = 0;
                for (Aktywo a : lista) { double w = a.getWartoscPLN(currentUSD, currentEUR, currentGBP); ilosc += a.ilosc; wartosc += w; sumaTotal += w; if(a instanceof AktywoRynkowe) koszt += (((AktywoRynkowe)a).cenaZakupu * a.ilosc); }
                double srCena = ilosc>0 ? koszt/ilosc : 0; double cenaRynk = lista.get(0).getCenaJednostkowa(); String pre = rozwinieteGrupy.contains(sym) ? "▼ " : "▶ ";
                modelTabeli.addRow(new Object[]{ pre + sym, lista.get(0).typ, lista.size()+" poz.", String.format("%.4f", ilosc), srCena>0?String.format("Śr: %.2f", srCena):"-", String.format("%.2f", cenaRynk), String.format("%,.2f zł", wartosc) });
                mapaWierszyDoAktywow.put(r++, null);
                if (rozwinieteGrupy.contains(sym)) for(Aktywo a : lista) dodajWiersz(a, r++, true);
            }
        }
        labelSuma.setText(String.format("%,.2f PLN", sumaTotal));
        if(sel>=0 && sel<tabela.getRowCount()) tabela.setRowSelectionInterval(sel, sel);
    }

    private void dodajWiersz(Aktywo a, int row, boolean wciecie) {
        double w = a.getWartoscPLN(currentUSD, currentEUR, currentGBP); String zak = a.getCenaZakupu()>0 ? String.format("%.2f", a.getCenaZakupu()) : "-"; String sym = wciecie ? "      ↳ " + a.dataZakupu : a.symbol;
        if(wciecie) modelTabeli.addRow(new Object[]{sym, "", "Zakup: "+a.dataZakupu, a.ilosc, zak, "", String.format("%,.2f zł", w)});
        else modelTabeli.addRow(new Object[]{a.symbol, a.typ, a.dataZakupu, a.ilosc, zak, String.format("%.2f", a.getCenaJednostkowa()), String.format("%,.2f zł", w)});
        mapaWierszyDoAktywow.put(row, a);
    }

    private void usunZaznaczone() {
        int[] rows = tabela.getSelectedRows(); if(rows.length==0) return;
        Set<Aktywo> del = new HashSet<>();
        for(int r : rows) { Aktywo a = mapaWierszyDoAktywow.get(r); if(a!=null) del.add(a); else { String s = (String)tabela.getValueAt(r,0); if(s!=null) { String sym = s.replace("▶","").replace("▼","").trim(); for(Aktywo x:portfel) if(x.symbol.equals(sym)) del.add(x); } } }
        if(del.isEmpty()) return;
        int c = JOptionPane.showConfirmDialog(this, "Usunąć "+del.size()+" poz.?", "Usuwanie", JOptionPane.YES_NO_OPTION);
        if(c==JOptionPane.YES_OPTION) { portfel.removeAll(del); BazaDanych.zapisz(portfel, nazwaPortfela); przeliczWidok(); }
    }

    // --- DARK MODE DIALOGS (Dodawanie i Import) ---

    // IMPORT
    private void pokazOknoImportu() {
        Window parent = SwingUtilities.getWindowAncestor(this); JDialog d = new JDialog(parent, "Import", Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true); d.setSize(500, 400); d.setLocationRelativeTo(parent);
        JPanel root = new JPanel(new BorderLayout()); root.setBorder(BorderFactory.createLineBorder(BORDER, 1)); root.setBackground(CARD_BG); d.setContentPane(root);

        // Header
        JPanel head = new JPanel(new BorderLayout()); head.setBackground(CARD_BG); head.setBorder(new EmptyBorder(10,15,10,15));
        JLabel title = new JLabel("Import XTB"); title.setForeground(TEXT_MAIN); title.setFont(FONT_BOLD);
        JButton close = new JButton("X"); styleMiniButton(close); close.addActionListener(e->d.dispose());
        head.add(title, BorderLayout.WEST); head.add(close, BorderLayout.EAST); root.add(head, BorderLayout.NORTH);

        // Content
        JPanel center = new JPanel(new GridBagLayout()); center.setBackground(INPUT_BG);
        center.setBorder(BorderFactory.createCompoundBorder(new EmptyBorder(20,20,20,20), BorderFactory.createDashedBorder(TEXT_DIM, 2, 5, 5, true)));
        JLabel info = new JLabel("<html><center>Upuść pliki CSV / XLSX tutaj<br><br><small style='color:#999'>OPEN POSITION lub CASH OPERATION</small></center></html>");
        info.setForeground(TEXT_MAIN); info.setHorizontalAlignment(SwingConstants.CENTER); center.add(info);

        center.setDropTarget(new DropTarget() {
            public synchronized void drop(DropTargetDropEvent evt) { try { evt.acceptDrop(DnDConstants.ACTION_COPY); List<File> f = (List<File>) evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor); procesujPliki(f, d); } catch (Exception e) {} }
        });

        JButton btnSelect = new JButton("WYBIERZ Z DYSKU"); PortfolioManager.styleButton(btnSelect, BLUE);
        btnSelect.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(); fc.setMultiSelectionEnabled(true); fc.setFileFilter(new FileNameExtensionFilter("XTB Reports", "xlsx", "xls", "csv"));
            if (fc.showOpenDialog(d) == JFileChooser.APPROVE_OPTION) procesujPliki(Arrays.asList(fc.getSelectedFiles()), d);
        });

        JPanel wrap = new JPanel(new BorderLayout(0, 20)); wrap.setBackground(CARD_BG); wrap.setBorder(new EmptyBorder(20,20,20,20));
        wrap.add(center, BorderLayout.CENTER); wrap.add(btnSelect, BorderLayout.SOUTH); root.add(wrap, BorderLayout.CENTER); d.setVisible(true);
    }

    private void procesujPliki(List<File> pliki, JDialog d) {
        XtbImport.ImportResult res = XtbImport.importujPliki(pliki);
        if (res.znalezionePliki > 0 && !res.aktywa.isEmpty()) { portfel.addAll(res.aktywa); BazaDanych.zapisz(portfel, nazwaPortfela); przeliczWidok(); JOptionPane.showMessageDialog(d, "Zaimportowano "+res.aktywa.size()+" pozycji."); d.dispose(); }
        else JOptionPane.showMessageDialog(d, "Brak danych w plikach.");
    }

    // DODAWANIE
    private void pokazOknoDodawania() {
        Window parent = SwingUtilities.getWindowAncestor(this); JDialog d = new JDialog(parent, "Nowa Pozycja", Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true); d.setSize(450, 700); d.setLocationRelativeTo(parent);
        JPanel root = new JPanel(new BorderLayout()); root.setBorder(BorderFactory.createLineBorder(BORDER, 1)); root.setBackground(CARD_BG); d.setContentPane(root);

        // Header
        JPanel head = new JPanel(new BorderLayout()); head.setBackground(CARD_BG); head.setBorder(new EmptyBorder(10,15,10,15));
        JLabel title = new JLabel("Dodaj Pozycję"); title.setForeground(TEXT_MAIN); title.setFont(FONT_BOLD);
        JButton close = new JButton("X"); styleMiniButton(close); close.addActionListener(e->d.dispose());
        head.add(title, BorderLayout.WEST); head.add(close, BorderLayout.EAST); root.add(head, BorderLayout.NORTH);

        // Form
        JPanel form = new JPanel(); form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS)); form.setBackground(CARD_BG); form.setBorder(new EmptyBorder(20,20,20,20));

        JComboBox<String> cat = new JComboBox<>(new String[]{"AKCJA", "KRYPTOWALUTA", "OBLIGACJA"}); stylizujComboBox(cat);
        JComboBox<String> sub = new JComboBox<>(); stylizujComboBox(sub);
        JTextField sym = stylizujInput(new JTextField()); JTextField qty = stylizujInput(new JTextField());
        JSpinner date = new JSpinner(new SpinnerDateModel()); date.setEditor(new JSpinner.DateEditor(date, "dd.MM.yyyy")); stylizujSpinner(date); date.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        JTextField price = stylizujInput(new JTextField("0.0")); JComboBox<Waluta> curr = new JComboBox<>(Waluta.values()); stylizujComboBox(curr);
        JTextField suffix = stylizujInput(new JTextField(".")); JTextField opr1 = stylizujInput(new JTextField("0.0")); JTextField opr2 = stylizujInput(new JTextField("0.0"));

        JPanel pPrice = createFieldPanel("Cena zakupu (1 szt):", price);
        JPanel pCurr = createFieldPanel("Waluta:", curr);
        JPanel pSuffix = createFieldPanel("Suffix (np. .DE):", suffix); pSuffix.setVisible(false);
        JPanel pBond = new JPanel(new GridLayout(4,1,5,5)); pBond.setBackground(CARD_BG); pBond.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BORDER), "Obligacja", 0,0, FONT_BOLD, TEXT_MAIN));
        pBond.add(createLabel("Oproc. 1 rok:")); pBond.add(opr1); pBond.add(createLabel("Marża:")); pBond.add(opr2); pBond.setVisible(false);

        Runnable logic = () -> {
            String c = (String)cat.getSelectedItem(); sub.removeAllItems();
            if("AKCJA".equals(c)) { sub.addItem("GPW (PL)"); sub.addItem("USA"); sub.addItem("UK"); sub.addItem("DE"); sub.addItem("Inny"); pBond.setVisible(false); pPrice.setVisible(true); pCurr.setVisible(true); sub.setEnabled(true); }
            else if("KRYPTOWALUTA".equals(c)) { sub.addItem("Global"); sub.setEnabled(false); pBond.setVisible(false); pPrice.setVisible(true); pCurr.setVisible(false); }
            else { sub.addItem("Stała"); sub.addItem("Indeksowana"); sub.setEnabled(true); pBond.setVisible(true); pPrice.setVisible(false); pCurr.setVisible(false); }
            pSuffix.setVisible(false); d.revalidate(); d.repaint();
        };
        logic.run();
        cat.addActionListener(e->logic.run());
        sub.addActionListener(e->{ String s=(String)sub.getSelectedItem(); pSuffix.setVisible(s!=null && s.contains("Inny")); });

        form.add(createLabel("Kategoria:")); form.add(cat); form.add(Box.createVerticalStrut(10));
        form.add(createLabel("Typ:")); form.add(sub); form.add(Box.createVerticalStrut(10));
        form.add(pSuffix);
        form.add(createLabel("Symbol (np. CDR, AAPL, BTC):")); form.add(sym); form.add(Box.createVerticalStrut(10));
        form.add(createLabel("Ilość:")); form.add(qty); form.add(Box.createVerticalStrut(10));
        form.add(pPrice); form.add(Box.createVerticalStrut(10));
        form.add(createLabel("Data zakupu:")); form.add(date); form.add(Box.createVerticalStrut(10));
        form.add(pCurr); form.add(pBond); form.add(Box.createVerticalStrut(20));

        JButton save = new JButton("ZAPISZ POZYCJĘ"); PortfolioManager.styleButton(save, BLUE); save.setAlignmentX(Component.CENTER_ALIGNMENT);
        save.addActionListener(e -> {
            try {
                String k = (String)cat.getSelectedItem(); String sType = (String)sub.getSelectedItem();
                String s = sym.getText().trim().toUpperCase(); double q = Double.parseDouble(qty.getText().replace(",","."));
                LocalDate ld = ((Date)date.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                double p = 0; try{p=Double.parseDouble(price.getText().replace(",","."));}catch(Exception x){}

                Aktywo newAsset = null;
                if("KRYPTOWALUTA".equals(k)) newAsset = new AktywoRynkowe(s, TypAktywa.KRYPTO, q, ld.toString(), Waluta.USDT, p);
                else if("AKCJA".equals(k)) {
                    if(sType.contains("GPW") && !s.endsWith(".WA")) s+=".WA";
                    if(sType.contains("USA") && !s.endsWith(".US")) s+=".US";
                    if(sType.contains("UK") && !s.endsWith(".UK")) s+=".UK";
                    if(sType.contains("DE") && !s.endsWith(".DE")) s+=".DE";
                    if(sType.contains("Inny")) { String sf = suffix.getText().trim().toUpperCase(); if(!sf.startsWith(".")) sf="."+sf; if(!s.endsWith(sf)) s+=sf; }
                    newAsset = new AktywoRynkowe(s, TypAktywa.AKCJA_USA, q, ld.toString(), (Waluta)curr.getSelectedItem(), p);
                } else {
                    double o1 = Double.parseDouble(opr1.getText().replace(",","."));
                    if(sType.contains("Stała")) newAsset = new ObligacjaStala(s, q, ld.toString(), o1);
                    else newAsset = new ObligacjaIndeksowana(s, q, ld.toString(), o1, Double.parseDouble(opr2.getText().replace(",",".") ));
                }

                if(newAsset != null) { portfel.add(newAsset); BazaDanych.zapisz(portfel, nazwaPortfela); przeliczWidok(); d.dispose(); }
            } catch(Exception ex) { JOptionPane.showMessageDialog(d, "Błąd danych: " + ex.getMessage()); }
        });

        form.add(save);
        JScrollPane sp = new JScrollPane(form); PortfolioManager.styleScrollPane(sp); sp.getViewport().setBackground(CARD_BG); root.add(sp, BorderLayout.CENTER);
        d.setVisible(true);
    }

    private JPanel createFieldPanel(String label, JComponent field) {
        JPanel p = new JPanel(new GridLayout(2,1)); p.setBackground(CARD_BG);
        JLabel l = new JLabel(label); l.setForeground(TEXT_MAIN); l.setFont(FONT_STD);
        p.add(l); p.add(field); return p;
    }

    // --- STYLE ---
    private JLabel createLabel(String t) { JLabel l = new JLabel(t); l.setForeground(TEXT_MAIN); l.setFont(FONT_STD); l.setAlignmentX(Component.LEFT_ALIGNMENT); return l; }
    private JTextField stylizujInput(JTextField tf) { tf.setOpaque(true); tf.setBackground(INPUT_BG); tf.setForeground(TEXT_MAIN); tf.setCaretColor(TEXT_MAIN); tf.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER, 1), BorderFactory.createEmptyBorder(4, 7, 4, 7))); tf.setFont(FONT_STD); tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35)); return tf; }
    private void styleMinimalistButton(JButton b) { b.setUI(new BasicButtonUI()); b.setBackground(INPUT_BG); b.setForeground(TEXT_MAIN); b.setFocusPainted(false); b.setBorderPainted(false); b.setFont(new Font("Segoe UI", Font.BOLD, 12)); b.setBorder(new EmptyBorder(8, 15, 8, 15)); b.setCursor(new Cursor(Cursor.HAND_CURSOR)); b.addMouseListener(new MouseAdapter() { public void mouseEntered(MouseEvent e) { b.setBackground(new Color(60, 60, 60)); } public void mouseExited(MouseEvent e) { b.setBackground(INPUT_BG); } }); }
    private void styleMiniButton(JButton b) { b.setUI(new BasicButtonUI()); b.setBackground(BG); b.setForeground(TEXT_DIM); b.setBorder(null); b.setFocusPainted(false); b.setFont(new Font("Segoe UI", Font.BOLD, 14)); b.setCursor(new Cursor(Cursor.HAND_CURSOR)); b.addMouseListener(new MouseAdapter() { public void mouseEntered(MouseEvent e) { b.setForeground(Color.WHITE); } public void mouseExited(MouseEvent e) { b.setForeground(TEXT_DIM); } }); }
    private void stylizujComboBox(JComboBox<?> combo) { combo.setFont(FONT_STD); combo.setBackground(INPUT_BG); combo.setForeground(TEXT_MAIN); combo.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER, 1), BorderFactory.createEmptyBorder(5, 7, 5, 7))); combo.setOpaque(true); combo.setFocusable(false); combo.setRenderer(new DefaultListCellRenderer() { @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) { JLabel c = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus); c.setOpaque(true); c.setBorder(new EmptyBorder(6, 8, 6, 8)); boolean disabled = !combo.isEnabled(); if (isSelected && !disabled) c.setBackground(SELECTION_BG); else c.setBackground(INPUT_BG); if (disabled) c.setForeground(TEXT_DIM); else c.setForeground(isSelected ? Color.WHITE : TEXT_MAIN); return c; } }); combo.setUI(new BasicComboBoxUI() { @Override protected JButton createArrowButton() { JButton btn = new JButton("▼"); btn.setBackground(INPUT_BG); btn.setBorder(BorderFactory.createEmptyBorder()); return btn; } @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) { g.setColor(INPUT_BG); g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height); } }); combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35)); }
    private void stylizujSpinner(JSpinner spinner) { spinner.setFont(FONT_STD); spinner.setBackground(INPUT_BG); spinner.setForeground(TEXT_MAIN); spinner.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER, 1), BorderFactory.createEmptyBorder(2, 2, 2, 2))); JComponent editor = spinner.getEditor(); if (editor instanceof JSpinner.DefaultEditor) { JFormattedTextField tf = ((JSpinner.DefaultEditor) editor).getTextField(); tf.setBackground(INPUT_BG); tf.setForeground(TEXT_MAIN); tf.setCaretColor(TEXT_MAIN); tf.setSelectionColor(SELECTION_BG); tf.setHorizontalAlignment(JTextField.LEFT); } spinner.setUI(new BasicSpinnerUI() { @Override protected Component createNextButton() { return createFlatArrowButton("▲"); } @Override protected Component createPreviousButton() { return createFlatArrowButton("▼"); } private JButton createFlatArrowButton(String symbol) { JButton btn = new JButton(symbol); btn.setFont(FONT_ARROW); btn.setMargin(new Insets(0, 0, 0, 0)); btn.setBackground(INPUT_BG); btn.setForeground(TEXT_DIM); btn.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5)); btn.setFocusable(false); btn.setOpaque(true); btn.setContentAreaFilled(true); btn.addMouseListener(new MouseAdapter() { public void mouseEntered(MouseEvent e) { btn.setBackground(BORDER); btn.setForeground(Color.WHITE); } public void mouseExited(MouseEvent e) { btn.setBackground(INPUT_BG); btn.setForeground(TEXT_DIM); } }); return btn; } }); }

    // --- FIX BIAŁEGO NAGŁÓWKA ---
    private void stylizujTabele() {
        tabela.setBackground(CARD_BG); tabela.setForeground(TEXT_MAIN); tabela.setRowHeight(35); tabela.setFont(new Font("Segoe UI", Font.PLAIN, 13)); tabela.setShowVerticalLines(false); tabela.setGridColor(BORDER); tabela.setSelectionBackground(new Color(70, 70, 70)); tabela.setSelectionForeground(Color.WHITE);
        JTableHeader h = tabela.getTableHeader();
        h.setBackground(BG); h.setForeground(TEXT_DIM); h.setBorder(BorderFactory.createMatteBorder(0,0,1,0,BORDER)); h.setFont(new Font("Segoe UI", Font.BOLD, 12));

        h.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                l.setBackground(BG); // FIX: Wymuszenie tła
                l.setForeground(TEXT_DIM); l.setFont(new Font("Segoe UI", Font.BOLD, 12));
                l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,0,BORDER), new EmptyBorder(0,10,0,0)));
                return l;
            }
        });

        tabela.setDefaultRenderer(Object.class, new DefaultTableCellRenderer(){ public Component getTableCellRendererComponent(JTable t, Object v, boolean isSel, boolean foc, int r, int c) { Component comp = super.getTableCellRendererComponent(t,v,isSel,foc,r,c); setBorder(new EmptyBorder(0, 10, 0, 0)); if(isSel) { setBackground(new Color(70,70,70)); setForeground(Color.WHITE); } else { Aktywo a = mapaWierszyDoAktywow.get(r); if(a==null) { setBackground(new Color(38,38,38)); setForeground(BLUE); setFont(getFont().deriveFont(Font.BOLD)); } else { setBackground(CARD_BG); setForeground(TEXT_MAIN); } } return comp; } });
    }
}