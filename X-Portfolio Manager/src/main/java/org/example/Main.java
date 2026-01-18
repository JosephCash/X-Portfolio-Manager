package org.example;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.plaf.basic.BasicTableHeaderUI;
import javax.swing.table.*;
import javax.swing.text.MaskFormatter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static org.example.PortfolioManager.*;

public class Main extends JPanel {

    // --- STAŁE ---
    private static final int ARROW_TOUCH_WIDTH = 26;

    // --- IKONY ---
    private ImageIcon iconArrowRight, iconArrowDown;
    private ImageIcon iconArrowRightW, iconArrowDownW;
    private ImageIcon iconArrowLeft, iconArrowLeftW;

    private JTable tabela;
    private DefaultTableModel modelTabeli;
    private JLabel labelSuma, labelStatus;
    private JButton przyciskOdswiez;
    private JProgressBar pasekPostepu;

    private List<Aktywo> portfel;
    private String nazwaPortfela;
    private PortfolioManager parentManager;
    private Set<String> rozwinieteGrupy = new HashSet<>();
    private Map<Integer, Aktywo> mapaWierszyDoAktywow = new HashMap<>();

    // Stan myszki
    private int hoveredRow = -1;
    private boolean isArrowHovered = false;

    private double currentUSD, currentEUR, currentGBP;

    public Main(String nazwaPortfela, PortfolioManager manager) {
        this.nazwaPortfela = nazwaPortfela;
        this.parentManager = manager;

        // --- ŁADOWANIE IKON ---
        int iconSize = 12;
        this.iconArrowRight  = PortfolioManager.loadIcon("arrow_right.png", iconSize);
        this.iconArrowDown   = PortfolioManager.loadIcon("arrow_down.png", iconSize);
        this.iconArrowRightW = PortfolioManager.loadIcon("arrow_right_w.png", iconSize);
        this.iconArrowDownW  = PortfolioManager.loadIcon("arrow_down_w.png", iconSize);
        this.iconArrowLeft   = PortfolioManager.loadIcon("arrow_left.png", 16);
        this.iconArrowLeftW  = PortfolioManager.loadIcon("arrow_left_w.png", 16);

        this.currentUSD = MarketData.pobierzKursUSD();
        this.currentEUR = MarketData.pobierzKursEUR();
        this.currentGBP = MarketData.pobierzKursGBP();

        setLayout(new BorderLayout());
        setBackground(BG_COLOR);

        portfel = BazaDanych.wczytaj(nazwaPortfela);

        // --- NAGŁÓWEK ---
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_COLOR);
        header.setBorder(new EmptyBorder(15, 40, 15, 40));

        JPanel leftHead = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftHead.setOpaque(false);

        JButton btnBack = new JButton(" WRÓĆ");
        if (iconArrowLeft != null) btnBack.setIcon(iconArrowLeft);
        btnBack.setUI(new BasicButtonUI());
        btnBack.setBackground(BG_COLOR);
        btnBack.setForeground(TEXT_DIM);
        btnBack.setBorder(null);
        btnBack.setFocusPainted(false);
        btnBack.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnBack.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btnBack.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                btnBack.setForeground(Color.WHITE);
                if (iconArrowLeftW != null) btnBack.setIcon(iconArrowLeftW);
            }
            public void mouseExited(MouseEvent e)  {
                btnBack.setForeground(TEXT_DIM);
                if (iconArrowLeft != null) btnBack.setIcon(iconArrowLeft);
            }
        });
        btnBack.addActionListener(e -> parentManager.wrocDoDashboard());

        JLabel title = new JLabel("  " + nazwaPortfela.toUpperCase());
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(TEXT_MAIN);

        leftHead.add(btnBack);
        leftHead.add(title);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        toolbar.setBackground(BG_COLOR);

        JButton btnAdd = new JButton("DODAJ");
        styleMinimalistButton(btnAdd);
        btnAdd.addActionListener(e -> pokazOknoDodawania());

        JButton btnImp = new JButton("IMPORT DANE");
        styleMinimalistButton(btnImp);
        btnImp.addActionListener(e -> pokazOknoImportu());

        JButton btnDel = new JButton("USUŃ");
        styleMinimalistButton(btnDel);
        btnDel.addActionListener(e -> usunZaznaczone());

        toolbar.add(btnAdd);
        toolbar.add(btnImp);
        toolbar.add(btnDel);

        header.add(leftHead, BorderLayout.WEST);
        header.add(toolbar, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // --- TABELA ---
        String[] cols = {"Symbol", "Typ", "Data / Ilość Poz.", "Ilość", "Śr. Cena / Cena", "Cena Rynkowa", "Wartość (PLN)"};
        modelTabeli = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        tabela = new JTable(modelTabeli);
        stylizujTabele();

        tabela.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int r = tabela.rowAtPoint(e.getPoint());
                int c = tabela.columnAtPoint(e.getPoint());
                if (r >= 0 && c == 0) {
                    if (mapaWierszyDoAktywow.get(r) == null) {
                        Rectangle cellRect = tabela.getCellRect(r, 0, false);
                        int clickX = e.getX() - cellRect.x;
                        if (clickX < ARROW_TOUCH_WIDTH) {
                            String sym = (String) tabela.getValueAt(r, 0);
                            if (sym != null) {
                                sym = sym.trim();
                                if (rozwinieteGrupy.contains(sym)) rozwinieteGrupy.remove(sym);
                                else rozwinieteGrupy.add(sym);
                                przeliczWidok();
                            }
                        }
                    }
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                hoveredRow = -1;
                isArrowHovered = false;
                tabela.repaint();
            }
        });

        tabela.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int r = tabela.rowAtPoint(e.getPoint());
                int c = tabela.columnAtPoint(e.getPoint());
                boolean dirty = false;
                if (r != hoveredRow) { hoveredRow = r; dirty = true; }
                boolean arrowZone = false;
                if (r >= 0 && c == 0) {
                    Rectangle cellRect = tabela.getCellRect(r, 0, false);
                    int mouseX = e.getX() - cellRect.x;
                    if (mouseX < ARROW_TOUCH_WIDTH) arrowZone = true;
                }
                if (arrowZone != isArrowHovered) { isArrowHovered = arrowZone; dirty = true; }
                if (dirty) tabela.repaint();
                if (r >= 0 && c == 0 && mapaWierszyDoAktywow.get(r) == null && isArrowHovered) tabela.setCursor(new Cursor(Cursor.HAND_CURSOR));
                else tabela.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
        });

        JScrollPane scroll = new JScrollPane(tabela);
        PortfolioManager.styleScrollPane(scroll);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setViewportBorder(null);
        scroll.getViewport().setBackground(BG_COLOR);
        scroll.getViewport().setOpaque(true);
        scroll.setBackground(BG_COLOR);
        scroll.setOpaque(true);

        JViewport hv = scroll.getColumnHeader();
        if (hv != null) { hv.setBackground(BG_COLOR); hv.setOpaque(true); }
        JPanel corner = new JPanel(); corner.setBackground(BG_COLOR); corner.setOpaque(true);
        scroll.setCorner(ScrollPaneConstants.UPPER_RIGHT_CORNER, corner);
        JPanel tableContainer = new JPanel(new BorderLayout());
        tableContainer.setBackground(BG_COLOR);
        tableContainer.setBorder(new EmptyBorder(0, 40, 0, 40));
        tableContainer.add(scroll, BorderLayout.CENTER);
        add(tableContainer, BorderLayout.CENTER);

        // --- STOPKA ---
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(CARD_BG);
        footer.setBorder(new EmptyBorder(20, 40, 20, 40));
        footer.setPreferredSize(new Dimension(0, 100));

        JPanel leftFoot = new JPanel(new GridLayout(2, 1, 0, 5));
        leftFoot.setOpaque(false);
        labelStatus = new JLabel(" Gotowy.");
        labelStatus.setForeground(TEXT_DIM);
        labelStatus.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        pasekPostepu = new JProgressBar();
        pasekPostepu.setIndeterminate(true);
        pasekPostepu.setPreferredSize(new Dimension(150, 4));
        pasekPostepu.setBackground(CARD_BG);
        pasekPostepu.setForeground(ACCENT);
        pasekPostepu.setBorderPainted(false);
        pasekPostepu.setVisible(false);
        pasekPostepu.setUI(new BasicProgressBarUI() {
            @Override protected void paintIndeterminate(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(c.getBackground());
                g2.fillRect(0, 0, c.getWidth(), c.getHeight());
                super.paintIndeterminate(g2, c);
                g2.dispose();
            }
        });
        leftFoot.add(labelStatus);
        leftFoot.add(pasekPostepu);

        JPanel rightFoot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightFoot.setOpaque(false);
        labelSuma = new JLabel("0.00 PLN");
        labelSuma.setFont(new Font("Segoe UI", Font.BOLD, 24));
        labelSuma.setForeground(ACCENT);
        przyciskOdswiez = new JButton("ODŚWIEŻ CENY");
        PortfolioManager.styleButton(przyciskOdswiez, ACCENT);
        przyciskOdswiez.addActionListener(e -> odswiezCeny());
        rightFoot.add(labelSuma);
        rightFoot.add(Box.createHorizontalStrut(20));
        rightFoot.add(przyciskOdswiez);
        footer.add(leftFoot, BorderLayout.WEST);
        footer.add(rightFoot, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        przeliczWidok();
        if (!MarketData.czyDaneZCache()) odswiezCeny();
        else labelStatus.setText(" Ostatnia aktualizacja: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    static class HeaderDragFixUI extends BasicTableHeaderUI {
        private final Color bg;
        HeaderDragFixUI(Color bg) { this.bg = bg; }
        @Override public void installUI(JComponent c) {
            super.installUI(c);
            if (rendererPane == null) { rendererPane = new CellRendererPane(); header.add(rendererPane); }
            else if (rendererPane.getParent() != header) { header.add(rendererPane); }
        }
        @Override public void paint(Graphics g, JComponent c) {
            g.setColor(bg); g.fillRect(0, 0, c.getWidth(), c.getHeight()); super.paint(g, c);
            JTableHeader h = header; if (h == null) return;
            TableColumn dragged = h.getDraggedColumn(); int dragDistance = h.getDraggedDistance();
            if (dragged == null || dragDistance == 0) return;
            TableColumnModel cm = h.getColumnModel(); int draggedIndex = -1;
            for (int i = 0; i < cm.getColumnCount(); i++) { if (cm.getColumn(i) == dragged) { draggedIndex = i; break; } }
            if (draggedIndex < 0) return;
            Rectangle r = h.getHeaderRect(draggedIndex);
            g.setColor(bg); g.fillRect(r.x, r.y, r.width, r.height);
            g.setColor(BORDER_COLOR); g.drawLine(r.x, r.y + r.height - 1, r.x + r.width, r.y + r.height - 1);
            TableCellRenderer renderer = dragged.getHeaderRenderer(); if (renderer == null) renderer = h.getDefaultRenderer();
            Component comp = renderer.getTableCellRendererComponent(h.getTable(), dragged.getHeaderValue(), false, false, -1, draggedIndex);
            if (comp instanceof JComponent jc) { jc.setOpaque(true); jc.setBackground(bg); jc.setForeground(h.getForeground()); }
            int newX = r.x + dragDistance; rendererPane.paintComponent(g, comp, h, newX, r.y, r.width, r.height, true);
        }
    }

    private void odswiezCeny() {
        przyciskOdswiez.setEnabled(false);
        przyciskOdswiez.setBackground(BORDER_COLOR);
        labelStatus.setText(" Pobieranie aktualnych cen...");
        pasekPostepu.setVisible(true);
        new SwingWorker<Void, Void>() {
            double u, e, g;
            @Override protected Void doInBackground() {
                u = MarketData.pobierzKursUSD(); e = MarketData.pobierzKursEUR(); g = MarketData.pobierzKursGBP();
                if (u == 0) u = currentUSD; if (e == 0) e = currentEUR; if (g == 0) g = currentGBP;
                for (Aktywo a : portfel) { if (a instanceof AktywoRynkowe) { a.getCenaJednostkowa(); try { Thread.sleep(50); } catch (Exception ignored) {} } }
                return null;
            }
            @Override protected void done() {
                currentUSD = u; currentEUR = e; currentGBP = g; przeliczWidok();
                labelStatus.setText(" Ostatnia aktualizacja: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                przyciskOdswiez.setEnabled(true); przyciskOdswiez.setBackground(ACCENT); pasekPostepu.setVisible(false);
            }
        }.execute();
    }

    private void przeliczWidok() {
        int sel = tabela.getSelectedRow();
        modelTabeli.setRowCount(0);
        mapaWierszyDoAktywow.clear();
        double sumaTotal = 0;
        Map<String, List<Aktywo>> grupy = portfel.stream().collect(Collectors.groupingBy(a -> a.symbol));
        List<String> klucze = new ArrayList<>(grupy.keySet());
        Collections.sort(klucze);
        int r = 0;
        for (String sym : klucze) {
            List<Aktywo> lista = grupy.get(sym);
            if (lista.size() == 1) {
                Aktywo a = lista.get(0);
                double w = a.getWartoscPLN(currentUSD, currentEUR, currentGBP);
                sumaTotal += w;
                dodajWiersz(a, r++, false);
            } else {
                double ilosc = 0, wartosc = 0, koszt = 0;
                for (Aktywo a : lista) {
                    double w = a.getWartoscPLN(currentUSD, currentEUR, currentGBP);
                    ilosc += a.ilosc; wartosc += w; sumaTotal += w;
                    if (a instanceof AktywoRynkowe) koszt += (((AktywoRynkowe) a).cenaZakupu * a.ilosc);
                    else if (a instanceof ObligacjaSkarbowa) koszt += (100.0 * a.ilosc);
                }
                double srCena = ilosc > 0 ? koszt / ilosc : 0;
                double cenaRynk = lista.get(0).getCenaJednostkowa();
                modelTabeli.addRow(new Object[]{ sym, lista.get(0).typ, lista.size() + " poz.", String.format("%.4f", ilosc), srCena > 0 ? String.format("Śr: %.2f", srCena) : "-", String.format("%.2f", cenaRynk), String.format("%,.2f zł", wartosc) });
                mapaWierszyDoAktywow.put(r++, null);
                if (rozwinieteGrupy.contains(sym)) { for (Aktywo a : lista) dodajWiersz(a, r++, true); }
            }
        }
        labelSuma.setText(String.format("%,.2f PLN", sumaTotal));
        if (sel >= 0 && sel < tabela.getRowCount()) tabela.setRowSelectionInterval(sel, sel);
    }

    private void dodajWiersz(Aktywo a, int row, boolean wciecie) {
        double w = a.getWartoscPLN(currentUSD, currentEUR, currentGBP);
        String zak = a.getCenaZakupu() > 0 ? String.format("%.2f", a.getCenaZakupu()) : "-";
        String sym = wciecie ? "      " + a.dataZakupu : a.symbol;
        if (wciecie) modelTabeli.addRow(new Object[]{ sym, "", "Zakup: " + a.dataZakupu, a.ilosc, zak, "", String.format("%,.2f zł", w) });
        else modelTabeli.addRow(new Object[]{ a.symbol, a.typ, a.dataZakupu, a.ilosc, zak, String.format("%.2f", a.getCenaJednostkowa()), String.format("%,.2f zł", w) });
        mapaWierszyDoAktywow.put(row, a);
    }

    private void usunZaznaczone() {
        int[] rows = tabela.getSelectedRows();
        if (rows.length == 0) return;
        Set<Aktywo> del = new HashSet<>();
        for (int r : rows) {
            Aktywo a = mapaWierszyDoAktywow.get(r);
            if (a != null) del.add(a);
            else {
                String s = (String) tabela.getValueAt(r, 0);
                if (s != null) { String sym = s.trim(); for (Aktywo x : portfel) if (x.symbol.equals(sym)) del.add(x); }
            }
        }
        if (del.isEmpty()) return;

        Window parent = SwingUtilities.getWindowAncestor(this);
        JDialog d = new JDialog(parent, "Potwierdź", Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        d.setSize(350, 230);
        d.setLocationRelativeTo(parent);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(CARD_BG);
        root.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        d.setContentPane(root);

        JLabel title = new JLabel("Usuwanie pozycji", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(TEXT_MAIN);
        title.setBorder(new EmptyBorder(20, 20, 10, 20));

        JLabel msg = new JLabel("<html><center>Czy na pewno chcesz usunąć<br>" + del.size() + " zaznaczonych pozycji?</center></html>", SwingConstants.CENTER);
        msg.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        msg.setForeground(TEXT_DIM);
        msg.setBorder(new EmptyBorder(0, 20, 20, 20));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 20));
        btns.setBackground(CARD_BG);

        JButton bYes = new JButton("USUŃ");
        PortfolioManager.styleButton(bYes, ACCENT_RED);
        bYes.addActionListener(e -> {
            portfel.removeAll(del);
            BazaDanych.zapisz(nazwaPortfela, portfel);
            przeliczWidok();
            d.dispose();
        });

        JButton bNo = new JButton("ANULUJ");
        bNo.setUI(new BasicButtonUI());
        bNo.setBackground(INPUT_BG);
        bNo.setForeground(TEXT_MAIN);
        bNo.setFocusPainted(false);
        bNo.setBorderPainted(false);
        bNo.setFont(new Font("Segoe UI", Font.BOLD, 14));
        bNo.setBorder(new EmptyBorder(10, 25, 10, 25));
        bNo.setCursor(new Cursor(Cursor.HAND_CURSOR));
        bNo.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { bNo.setBackground(new Color(60,60,60)); }
            public void mouseExited(MouseEvent e) { bNo.setBackground(INPUT_BG); }
        });
        bNo.addActionListener(e -> d.dispose());

        btns.add(bNo);
        btns.add(bYes);

        root.add(title, BorderLayout.NORTH);
        root.add(msg, BorderLayout.CENTER);
        root.add(btns, BorderLayout.SOUTH);

        d.setVisible(true);
    }

    private void pokazOknoImportu() {
        Window parent = SwingUtilities.getWindowAncestor(this);
        JDialog d = new JDialog(parent, "Import Danych", Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true); d.setSize(500, 420); d.setLocationRelativeTo(parent);
        JPanel root = new JPanel(new BorderLayout()); root.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1)); root.setBackground(CARD_BG); d.setContentPane(root);
        JPanel head = new JPanel(new BorderLayout()); head.setBackground(CARD_BG); head.setBorder(new EmptyBorder(15, 20, 15, 20));
        JLabel title = new JLabel("Import Danych"); title.setForeground(TEXT_MAIN); title.setFont(PortfolioManager.FONT_TITLE.deriveFont(20f));
        JButton close = new JButton("X"); PortfolioManager.styleMiniActionBtn(close, TEXT_DIM); close.addActionListener(e -> d.dispose());
        head.add(title, BorderLayout.WEST); head.add(close, BorderLayout.EAST); root.add(head, BorderLayout.NORTH);
        JPanel content = new JPanel(); content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS)); content.setBackground(CARD_BG); content.setBorder(new EmptyBorder(10, 30, 20, 30));
        JLabel lSource = new JLabel("Źródło danych:"); lSource.setForeground(TEXT_DIM); lSource.setAlignmentX(Component.CENTER_ALIGNMENT);
        JComboBox<String> comboSource = new JComboBox<>(new String[]{"XTB (Excel/CSV)", "Inne (wkrótce)"}); PortfolioManager.stylizujComboBox(comboSource);
        JPanel dropZone = new JPanel(new GridBagLayout()); dropZone.setBackground(INPUT_BG);
        dropZone.setBorder(BorderFactory.createCompoundBorder(new EmptyBorder(10, 30, 10, 30), BorderFactory.createDashedBorder(TEXT_DIM, 2, 5, 5, true)));
        dropZone.setPreferredSize(new Dimension(400, 150)); dropZone.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        JLabel info = new JLabel("<html><center>Upuść pliki tutaj<br><small style='color:#888'>.xlsx, .csv</small></center></html>");
        info.setForeground(TEXT_MAIN); info.setHorizontalAlignment(SwingConstants.CENTER); dropZone.add(info);
        dropZone.setDropTarget(new DropTarget() {
            @SuppressWarnings("unchecked") public synchronized void drop(DropTargetDropEvent evt) {
                try { evt.acceptDrop(DnDConstants.ACTION_COPY); List<File> f = (List<File>) evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor); procesujPliki(f, d); } catch (Exception ignored) {}
            }
        });
        JButton btnSelect = new JButton("WYBIERZ PLIK"); PortfolioManager.styleButton(btnSelect, PortfolioManager.ACCENT);
        btnSelect.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnSelect.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(); fc.setMultiSelectionEnabled(true); fc.setFileFilter(new FileNameExtensionFilter("Arkusze XTB", "xlsx", "xls", "csv"));
            if (fc.showOpenDialog(d) == JFileChooser.APPROVE_OPTION) { procesujPliki(Arrays.asList(fc.getSelectedFiles()), d); }
        });
        content.add(lSource); content.add(Box.createVerticalStrut(5)); content.add(comboSource); content.add(Box.createVerticalStrut(20)); content.add(dropZone); content.add(Box.createVerticalStrut(20)); content.add(btnSelect);
        root.add(content, BorderLayout.CENTER); d.setVisible(true);
    }

    private void procesujPliki(List<File> pliki, JDialog d) {
        XtbImport.ImportResult res = XtbImport.importujPliki(pliki);
        if (res.znalezionePliki > 0 && !res.aktywa.isEmpty()) {
            portfel.addAll(res.aktywa); BazaDanych.zapisz(nazwaPortfela, portfel); przeliczWidok();
            JOptionPane.showMessageDialog(d, "Zaimportowano " + res.aktywa.size() + " pozycji."); d.dispose();
        } else { JOptionPane.showMessageDialog(d, "Nie udało się zaimportować danych.\nUpewnij się, że plik zawiera arkusz OPEN POSITION."); }
    }

    private void pokazOknoDodawania() {
        Window parent = SwingUtilities.getWindowAncestor(this);
        JDialog d = new JDialog(parent, "Nowa Pozycja", Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true); d.setSize(420, 650); d.setLocationRelativeTo(parent);

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        root.setBackground(CARD_BG);
        d.setContentPane(root);

        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(CARD_BG);
        head.setBorder(new EmptyBorder(15,20,15,20));

        JLabel title = new JLabel("Dodaj Pozycję");
        title.setForeground(TEXT_MAIN);
        title.setFont(PortfolioManager.FONT_TITLE.deriveFont(20f));

        JButton close = new JButton("X");
        PortfolioManager.styleMiniActionBtn(close, TEXT_DIM);
        close.addActionListener(e->d.dispose());

        head.add(title, BorderLayout.WEST);
        head.add(close, BorderLayout.EAST);
        root.add(head, BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(CARD_BG);
        form.setBorder(new EmptyBorder(0, 30, 20, 30));

        JPanel catPanel = new JPanel(new GridLayout(1, 3, 10, 0));
        catPanel.setOpaque(false);
        catPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

        JToggleButton bAkcja = createTileBtn("Akcja", "stock.png");
        JToggleButton bKrypto = createTileBtn("Krypto", "crypto.png");
        JToggleButton bOblig = createTileBtn("Obligacja", "bond.png");

        ButtonGroup bgCat = new ButtonGroup();
        bgCat.add(bAkcja); bgCat.add(bKrypto); bgCat.add(bOblig);
        catPanel.add(bAkcja); catPanel.add(bKrypto); catPanel.add(bOblig);
        bAkcja.setSelected(true);

        JComboBox<String> sub = new JComboBox<>();
        PortfolioManager.stylizujComboBox(sub);

        JLabel lblSymbol = createLabel("Symbol (np. CDR, AAPL, BTC):");
        JTextField sym = new JTextField(); PortfolioManager.stylizujInput(sym);
        JTextField qty = new JTextField(); PortfolioManager.stylizujInput(qty);

        // --- NOWOŚĆ: JFormattedTextField zamiast JSpinnera ---
        JFormattedTextField date = null;
        try {
            MaskFormatter mask = new MaskFormatter("##.##.####");
            mask.setPlaceholderCharacter('_');
            date = new JFormattedTextField(mask);
            PortfolioManager.stylizujInput(date); // Używamy tego samego stylu co reszta inputów
            // Ustawiamy dzisiejszą datę jako domyślną
            date.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        } catch (Exception e) { e.printStackTrace(); }

        JTextField price = new JTextField(); PortfolioManager.stylizujInput(price);
        JComboBox<Waluta> curr = new JComboBox<>(Waluta.values()); PortfolioManager.stylizujComboBox(curr);

        JTextField opr1 = new JTextField(); PortfolioManager.stylizujInput(opr1);
        JTextField opr2 = new JTextField(); PortfolioManager.stylizujInput(opr2);

        JPanel pOblig = new JPanel(new GridLayout(2, 2, 10, 10)); pOblig.setOpaque(false);
        JLabel lblRate = createLabel("Oproc. 1 rok (%):");
        JLabel lblMargin = createLabel("Marża (%):");
        pOblig.add(lblRate); pOblig.add(opr1);
        pOblig.add(lblMargin); pOblig.add(opr2);
        pOblig.setVisible(false);

        // Potrzebny finalny obiekt daty do użycia w listenerze
        final JFormattedTextField fDate = date;

        ActionListener updateLogic = e -> {
            sub.removeAllItems();
            sub.setEnabled(true);
            pOblig.setVisible(false);
            curr.setEnabled(true);
            price.setVisible(true);
            price.setEnabled(true);
            price.setText("");
            opr1.setText("");
            opr2.setText("");

            for(ActionListener al : sub.getActionListeners()) sub.removeActionListener(al);

            if(bAkcja.isSelected()) {
                sub.addItem("GPW (Polska)"); sub.addItem("USA (Nasdaq/NYSE)");
                sub.addItem("Wielka Brytania"); sub.addItem("Niemcy"); sub.addItem("Inny");
                lblSymbol.setText("Symbol (np. CDR, AAPL):");
                curr.setVisible(true);
            }
            else if(bKrypto.isSelected()) {
                sub.addItem("Global");
                lblSymbol.setText("Symbol (np. BTC, ETH):");
                curr.setSelectedItem(Waluta.USDT);
                curr.setEnabled(false);
            }
            else {
                sub.addItem("Indeksowana (Inflacją)");
                sub.addItem("Stała (Stałe oprocentowanie)");
                lblSymbol.setText("Symbol Serii (np. EDO1024):");
                price.setVisible(true);
                price.setText("100.00");
                curr.setSelectedItem(Waluta.PLN);
                curr.setEnabled(false);
                pOblig.setVisible(true);
                lblMargin.setVisible(true);
                opr2.setVisible(true);
                lblRate.setText("Oproc. 1 rok (%):");

                sub.addActionListener(ev -> {
                    boolean isFixed = sub.getSelectedIndex() == 1;
                    lblMargin.setVisible(!isFixed);
                    opr2.setVisible(!isFixed);
                    lblRate.setText(isFixed ? "Oprocentowanie (%):" : "Oproc. 1 rok (%):");
                });
            }
            d.revalidate(); d.repaint();
        };

        bAkcja.addActionListener(updateLogic); bKrypto.addActionListener(updateLogic); bOblig.addActionListener(updateLogic); updateLogic.actionPerformed(null);

        form.add(createLabel("Kategoria:")); form.add(Box.createVerticalStrut(5)); form.add(catPanel); form.add(Box.createVerticalStrut(15));
        form.add(createLabel("Rynek / Typ:")); form.add(Box.createVerticalStrut(5)); form.add(sub); form.add(Box.createVerticalStrut(10));
        form.add(lblSymbol); form.add(Box.createVerticalStrut(5)); form.add(sym); form.add(Box.createVerticalStrut(10));

        JPanel rowQtyPrice = new JPanel(new GridLayout(1, 2, 10, 0)); rowQtyPrice.setOpaque(false);
        rowQtyPrice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        JPanel pQ = new JPanel(new BorderLayout()); pQ.setOpaque(false); pQ.add(createLabel("Ilość:"), BorderLayout.NORTH); pQ.add(qty, BorderLayout.CENTER);
        JPanel pP = new JPanel(new BorderLayout()); pP.setOpaque(false); pP.add(createLabel("Cena (1 szt):"), BorderLayout.NORTH); pP.add(price, BorderLayout.CENTER);
        rowQtyPrice.add(pQ); rowQtyPrice.add(pP);

        form.add(rowQtyPrice); form.add(Box.createVerticalStrut(10));

        // --- DATA Z MASKĄ ---
        form.add(createLabel("Data zakupu (dd.mm.rrrr):"));
        form.add(Box.createVerticalStrut(5));
        form.add(date);
        form.add(Box.createVerticalStrut(10));

        form.add(createLabel("Waluta:")); form.add(Box.createVerticalStrut(5)); form.add(curr); form.add(Box.createVerticalStrut(10));
        form.add(pOblig); form.add(Box.createVerticalGlue());

        JButton btnSave = new JButton("DODAJ POZYCJĘ"); PortfolioManager.styleButton(btnSave, ACCENT); btnSave.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnSave.addActionListener(ev -> {
            try {
                String rawSym = sym.getText().trim().toUpperCase();
                String s = rawSym.contains("/") ? rawSym.split("/")[0] : rawSym;

                // AUTOMATYCZNA ZAMIANA PRZECINKÓW NA KROPKI W LICZBACH
                double q = Double.parseDouble(qty.getText().replace(",","."));

                // Parsowanie daty z pola tekstowego z maską
                LocalDate ld = LocalDate.parse(fDate.getText(), DateTimeFormatter.ofPattern("dd.MM.yyyy"));

                Aktywo newAsset = null;

                if(bKrypto.isSelected()) {
                    double p = Double.parseDouble(price.getText().replace(",","."));
                    newAsset = new AktywoRynkowe(s, TypAktywa.KRYPTO, q, ld.toString(), Waluta.USDT, p);
                }
                else if(bAkcja.isSelected()) {
                    double p = Double.parseDouble(price.getText().replace(",","."));
                    String t = (String)sub.getSelectedItem();
                    if(t.contains("GPW") && !s.contains(".")) s+=".WA";
                    else if(t.contains("USA") && s.contains(".")) s=s.split("\\.")[0];
                    else if(t.contains("Wielka Brytania") && !s.endsWith(".UK")) s+=".UK";
                    else if(t.contains("Niemcy") && !s.endsWith(".DE")) s+=".DE";
                    newAsset = new AktywoRynkowe(s, t.contains("GPW")?TypAktywa.AKCJA_PL:TypAktywa.AKCJA_USA, q, ld.toString(), (Waluta)curr.getSelectedItem(), p);
                }
                else {
                    Double manualRate = null;
                    Double manualMargin = null;

                    if (!opr1.getText().trim().isEmpty()) manualRate = Double.parseDouble(opr1.getText().replace(",","."));
                    if (opr2.isVisible() && !opr2.getText().trim().isEmpty()) {
                        manualMargin = Double.parseDouble(opr2.getText().replace(",","."));
                    }

                    TypAktywa selectedType = (sub.getSelectedIndex() == 1) ? TypAktywa.OBLIGACJA_STALA : TypAktywa.OBLIGACJA_INDEKSOWANA;
                    newAsset = new ObligacjaSkarbowa(s, selectedType, q, ld.toString(), manualRate, manualMargin);
                }

                if(newAsset!=null) {
                    portfel.add(newAsset);
                    BazaDanych.zapisz(nazwaPortfela, portfel);
                    przeliczWidok();
                    d.dispose();
                }
            } catch(Exception ex) {
                JOptionPane.showMessageDialog(d, "Błąd danych: " + ex.getMessage());
            }
        });

        JPanel footer = new JPanel(); footer.setBackground(CARD_BG); footer.setBorder(new EmptyBorder(0,0,20,0)); footer.add(btnSave);
        root.add(form, BorderLayout.CENTER); root.add(footer, BorderLayout.SOUTH); d.setVisible(true);
    }

    private JLabel createLabel(String t) { JLabel l = new JLabel(t); l.setForeground(TEXT_DIM); l.setAlignmentX(Component.CENTER_ALIGNMENT); return l; }

    private JToggleButton createTileBtn(String txt, String iconName) {
        ImageIcon iconNormal = PortfolioManager.loadIcon(iconName, 32); ImageIcon iconWhite = PortfolioManager.loadIcon(iconName.replace(".png", "_w.png"), 32);
        JToggleButton b = new JToggleButton("<html><center>"+txt+"</center></html>", iconNormal);
        b.setVerticalTextPosition(SwingConstants.BOTTOM); b.setHorizontalTextPosition(SwingConstants.CENTER);
        b.setBackground(INPUT_BG); b.setForeground(TEXT_DIM); b.setFocusPainted(false);
        b.setBorder(BorderFactory.createLineBorder(BORDER_COLOR)); b.setContentAreaFilled(false); b.setOpaque(true); b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.addChangeListener(e -> { if(b.isSelected()) { b.setBackground(ACCENT); b.setForeground(Color.WHITE); b.setIcon(iconWhite); } else { b.setBackground(INPUT_BG); b.setForeground(TEXT_DIM); b.setIcon(iconNormal); } });
        return b;
    }

    private void styleMinimalistButton(JButton b) {
        b.setUI(new BasicButtonUI()); b.setBackground(BG_COLOR); b.setForeground(TEXT_MAIN);
        b.setFocusPainted(false); b.setBorderPainted(false); b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setBorder(new EmptyBorder(8, 15, 8, 15)); b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() { public void mouseEntered(MouseEvent e) { b.setBackground(new Color(40, 40, 40)); } public void mouseExited(MouseEvent e)  { b.setBackground(BG_COLOR); } });
    }

    private void styleMiniButton(JButton b) {
        b.setUI(new BasicButtonUI()); b.setBackground(BG_COLOR); b.setForeground(TEXT_DIM);
        b.setBorder(null); b.setFocusPainted(false); b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() { public void mouseEntered(MouseEvent e) { b.setForeground(Color.WHITE); } public void mouseExited(MouseEvent e)  { b.setForeground(TEXT_DIM); } });
    }

    // --- RENDERERY ---
    private void stylizujTabele() {
        tabela.setBackground(BG_COLOR); tabela.setForeground(TEXT_MAIN); tabela.setRowHeight(35);
        tabela.setFont(new Font("Segoe UI", Font.PLAIN, 13)); tabela.setShowVerticalLines(false);
        tabela.setGridColor(BORDER_COLOR); tabela.setSelectionBackground(new Color(70, 70, 70)); tabela.setSelectionForeground(Color.WHITE);
        tabela.setFillsViewportHeight(true);
        JTableHeader h = tabela.getTableHeader(); h.setBackground(BG_COLOR); h.setForeground(TEXT_DIM); h.setOpaque(true);
        h.setReorderingAllowed(true); h.setUI(new HeaderDragFixUI(BG_COLOR));
        h.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR)); h.setFont(new Font("Segoe UI", Font.BOLD, 12));
        h.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                l.setOpaque(true); l.setBackground(BG_COLOR); l.setForeground(TEXT_DIM); l.setFont(new Font("Segoe UI", Font.BOLD, 12));
                l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR), new EmptyBorder(0, 10, 0, 0)));
                return l;
            }
        });

        tabela.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean isSel, boolean foc, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, isSel, foc, r, c);
                l.setBorder(new EmptyBorder(0, 10, 0, 0));
                l.setIcon(null);
                Aktywo a = mapaWierszyDoAktywow.get(r);
                if (isSel) { l.setBackground(new Color(70, 70, 70)); l.setForeground(Color.WHITE); }
                else { if (a == null) { l.setBackground(new Color(25, 25, 25)); l.setForeground(TEXT_MAIN); l.setFont(new Font("Segoe UI", Font.BOLD, 13)); } else { l.setBackground(CARD_BG); l.setForeground(TEXT_MAIN); l.setFont(new Font("Segoe UI", Font.PLAIN, 13)); } }
                if (a == null) {
                    String sym = (String) v;
                    boolean preciseHover = (r == hoveredRow && isArrowHovered);
                    boolean showWhite = preciseHover || isSel;
                    if (rozwinieteGrupy.contains(sym)) l.setIcon(showWhite ? iconArrowDownW : iconArrowDown);
                    else l.setIcon(showWhite ? iconArrowRightW : iconArrowRight);
                }
                return l;
            }
        });

        tabela.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean isSel, boolean foc, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, isSel, foc, r, c); setBorder(new EmptyBorder(0, 10, 0, 0));
                if (isSel) { setBackground(new Color(70, 70, 70)); setForeground(Color.WHITE); }
                else { Aktywo a = mapaWierszyDoAktywow.get(r); if (a == null) { setBackground(new Color(25, 25, 25)); setForeground(TEXT_MAIN); } else { setBackground(CARD_BG); setForeground(TEXT_MAIN); } }
                return comp;
            }
        });
    }
}