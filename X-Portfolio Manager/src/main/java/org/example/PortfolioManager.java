package org.example;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PortfolioManager extends JFrame {

    // --- STAŁE KOLORYSTYCZNE ---
    public static final Color BG_COLOR = new Color(18, 18, 18);
    public static final Color CARD_BG = new Color(30, 30, 30);
    public static final Color INPUT_BG = new Color(45, 45, 45);
    public static final Color CARD_HOVER = new Color(45, 45, 45);
    public static final Color ACCENT = new Color(39, 174, 96);
    public static final Color ACCENT_RED = new Color(192, 57, 43);
    public static final Color BLUE = new Color(41, 128, 185);
    public static final Color TEXT_MAIN = new Color(220, 220, 220);
    public static final Color TEXT_DIM = new Color(150, 150, 150);
    public static final Color BORDER_COLOR = new Color(60, 60, 60);
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 24);
    public static final Font FONT_NORMAL = new Font("Segoe UI", Font.PLAIN, 14);

    private JPanel mainContainer;
    private CardLayout cardLayout;
    private JPanel dashboardPanel;
    private Main portfolioView;
    private JPanel gridPanel;
    private JLabel labelTotal;
    private int pX, pY;

    private String selectedIconTemp = "wallet.png";

    // Mapa do szybkiej aktualizacji etykiet
    private final Map<String, JLabel> valueLabelsMap = new ConcurrentHashMap<>();
    private final Map<String, JLabel> infoLabelsMap = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(PortfolioManager::pokazSplash);
    }

    private static void pokazSplash() {
        JWindow splash = new JWindow();
        splash.setSize(450, 280); splash.setLocationRelativeTo(null);
        JPanel content = new JPanel(new BorderLayout()); content.setBackground(BG_COLOR); content.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        JLabel title = new JLabel("X-Portfolio Manager", SwingConstants.CENTER); title.setFont(new Font("Segoe UI", Font.BOLD, 26)); title.setForeground(TEXT_MAIN); title.setBorder(new EmptyBorder(40, 0, 0, 0));
        JLabel sub = new JLabel("Twoje centrum inwestycyjne", SwingConstants.CENTER); sub.setFont(FONT_NORMAL); sub.setForeground(TEXT_DIM); sub.setBorder(new EmptyBorder(10, 0, 0, 0));
        JPanel txt = new JPanel(new BorderLayout()); txt.setBackground(BG_COLOR); txt.add(title, BorderLayout.NORTH); txt.add(sub, BorderLayout.CENTER);
        JLabel stat = new JLabel("Inicjalizacja...", SwingConstants.CENTER); stat.setForeground(TEXT_DIM); stat.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JProgressBar bar = new JProgressBar(0, 100); bar.setPreferredSize(new Dimension(450, 6)); bar.setBorderPainted(false); bar.setBackground(CARD_BG); bar.setForeground(ACCENT);

        bar.setUI(new BasicProgressBarUI() {
            @Override protected void paintDeterminate(Graphics g, JComponent c) {
                Graphics2D g2=(Graphics2D)g.create();
                g2.setColor(c.getBackground()); g2.fillRect(0,0,c.getWidth(),c.getHeight());
                g2.setColor(c.getForeground()); g2.fillRect(0,0,getAmountFull(new Insets(0,0,0,0),c.getWidth(),c.getHeight()),c.getHeight());
                g2.dispose();
            }
        });

        JPanel bot = new JPanel(new BorderLayout()); bot.setBackground(BG_COLOR); bot.add(stat, BorderLayout.NORTH); bot.add(bar, BorderLayout.SOUTH);
        content.add(txt, BorderLayout.CENTER); content.add(bot, BorderLayout.SOUTH); splash.add(content); splash.setVisible(true);

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                update(stat, bar, 10, "Pobieranie kursów walut...");
                MarketData.pobierzKursUSD(); MarketData.pobierzKursEUR(); MarketData.pobierzKursGBP();
                List<String> names = BazaDanych.pobierzNazwyPortfeli();
                int step = 0; int total = names.isEmpty() ? 1 : names.size();
                for(String n : names) {
                    update(stat, bar, 30 + (step*60/total), "Ładowanie: " + n);
                    for(Aktywo a : BazaDanych.wczytaj(n)) { if(a instanceof AktywoRynkowe) MarketData.pobierzCene(a.symbol, a.typ); }
                    step++;
                }
                update(stat, bar, 100, "Uruchamianie..."); Thread.sleep(300);
                return null;
            }
            private void update(JLabel l, JProgressBar b, int v, String t) { SwingUtilities.invokeLater(() -> { l.setText(t); b.setValue(v); }); }
            @Override protected void done() { splash.dispose(); new PortfolioManager().setVisible(true); }
        }.execute();
    }

    public PortfolioManager() {
        setUndecorated(true); setSize(1100, 750); setLocationRelativeTo(null); setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(BG_COLOR); getRootPane().setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

        // --- NAGŁÓWEK ---
        JPanel header = new JPanel(new BorderLayout()); header.setBackground(BG_COLOR); header.setBorder(new EmptyBorder(10, 20, 10, 10));
        JLabel appTitle = new JLabel("X-Portfolio Manager"); appTitle.setFont(new Font("Segoe UI", Font.BOLD, 16)); appTitle.setForeground(TEXT_DIM);

        JPanel controls = new JPanel(new GridLayout(1, 2, 5, 0));
        controls.setOpaque(false);
        JButton btnMin = createWinButton("_", false); btnMin.addActionListener(e -> setState(Frame.ICONIFIED));
        JButton btnClose = createWinButton("X", true); btnClose.addActionListener(e -> System.exit(0));
        controls.add(btnMin); controls.add(btnClose);

        header.add(appTitle, BorderLayout.WEST); header.add(controls, BorderLayout.EAST);

        MouseAdapter ma = new MouseAdapter() {
            public void mousePressed(MouseEvent me) { pX = me.getX(); pY = me.getY(); }
            public void mouseDragged(MouseEvent me) { setLocation(getLocation().x + me.getX() - pX, getLocation().y + me.getY() - pY); }
        };
        header.addMouseListener(ma); header.addMouseMotionListener(ma);
        add(header, BorderLayout.NORTH);

        cardLayout = new CardLayout(); mainContainer = new JPanel(cardLayout); mainContainer.setBackground(BG_COLOR);
        initDashboard();
        mainContainer.add(dashboardPanel, "DASHBOARD");
        mainContainer.add(new JPanel(), "PORTFOLIO");
        add(mainContainer, BorderLayout.CENTER);
        odswiezDashboard();
    }

    private void initDashboard() {
        dashboardPanel = new JPanel(new BorderLayout()); dashboardPanel.setBackground(BG_COLOR);

        JLabel lblTitle = new JLabel("Twoje Portfele", SwingConstants.LEFT);
        lblTitle.setFont(FONT_TITLE); lblTitle.setForeground(TEXT_MAIN); lblTitle.setBorder(new EmptyBorder(20, 40, 20, 40));
        dashboardPanel.add(lblTitle, BorderLayout.NORTH);

        gridPanel = new JPanel(new GridLayout(0, 2, 20, 20));
        gridPanel.setBackground(BG_COLOR);
        gridPanel.setBorder(new EmptyBorder(0, 40, 0, 40));

        JPanel gridWrapper = new JPanel(new BorderLayout()); gridWrapper.setBackground(BG_COLOR); gridWrapper.add(gridPanel, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(gridWrapper); scroll.setBorder(null); scroll.getViewport().setBackground(BG_COLOR); styleScrollPane(scroll);
        dashboardPanel.add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout()); footer.setBackground(CARD_BG);
        footer.setBorder(new EmptyBorder(20, 40, 20, 40));
        footer.setPreferredSize(new Dimension(0, 100)); // Stała wysokość

        JPanel sumPanel = new JPanel(new GridLayout(2, 1)); sumPanel.setOpaque(false);
        JLabel l1 = new JLabel("Majątek Całkowity:"); l1.setForeground(TEXT_DIM); l1.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        labelTotal = new JLabel("..."); labelTotal.setForeground(ACCENT); labelTotal.setFont(new Font("Segoe UI", Font.BOLD, 28));
        sumPanel.add(l1); sumPanel.add(labelTotal);

        JButton btnAdd = new JButton("+ NOWY PORTFEL"); styleButton(btnAdd, ACCENT);
        btnAdd.addActionListener(e -> pokazOknoEdycjiPortfela(null));

        footer.add(sumPanel, BorderLayout.WEST); footer.add(btnAdd, BorderLayout.EAST);
        dashboardPanel.add(footer, BorderLayout.SOUTH);
    }

    public void odswiezDashboard() {
        gridPanel.removeAll(); valueLabelsMap.clear(); infoLabelsMap.clear();
        List<String> portfele = BazaDanych.pobierzNazwyPortfeli();
        if (portfele.isEmpty()) { BazaDanych.utworzPortfel("Główny"); portfele.add("Główny"); }

        for (String pName : portfele) dodajKarteUI(pName);
        gridPanel.revalidate(); gridPanel.repaint();

        new SwingWorker<Double, Void>() {
            @Override protected Double doInBackground() {
                double sumaTotal = 0;
                double u = MarketData.pobierzKursUSD(); double e = MarketData.pobierzKursEUR(); double g = MarketData.pobierzKursGBP();
                for (String pName : portfele) {
                    List<Aktywo> aktywa = BazaDanych.wczytaj(pName);
                    double w = 0;
                    for (Aktywo a : aktywa) w += a.getWartoscPLN(u, e, g);
                    double finalW = w; int count = aktywa.size();
                    SwingUtilities.invokeLater(() -> zaktualizujKarte(pName, finalW, count));
                    sumaTotal += w;
                }
                return sumaTotal;
            }
            @Override protected void done() { try { labelTotal.setText(String.format("%,.2f PLN", get())); } catch (Exception e) {} }
        }.execute();
    }

    private void dodajKarteUI(String nazwa) {
        JPanel card = new JPanel(new BorderLayout(15, 0)); card.setBackground(CARD_BG); card.setPreferredSize(new Dimension(0, 110));
        card.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 15)); card.setCursor(new Cursor(Cursor.HAND_CURSOR));

        ImageIcon icon = loadIcon(BazaDanych.pobierzIkone(nazwa), 85);
        JLabel lIcon = new JLabel(icon); lIcon.setVerticalAlignment(SwingConstants.CENTER);

        JPanel content = new JPanel(new GridLayout(3, 1)); content.setOpaque(false);
        JLabel lName = new JLabel(nazwa); lName.setFont(new Font("Segoe UI", Font.BOLD, 18)); lName.setForeground(TEXT_MAIN);
        JLabel lInfo = new JLabel("Wczytywanie..."); lInfo.setFont(new Font("Segoe UI", Font.PLAIN, 13)); lInfo.setForeground(TEXT_DIM);
        JLabel lVal = new JLabel("..."); lVal.setFont(new Font("Segoe UI", Font.BOLD, 22)); lVal.setForeground(ACCENT);
        infoLabelsMap.put(nazwa, lInfo); valueLabelsMap.put(nazwa, lVal);
        content.add(lName); content.add(lInfo); content.add(lVal);
        card.add(lIcon, BorderLayout.WEST); card.add(content, BorderLayout.CENTER);

        JPopupMenu popup = new JPopupMenu(); stylePopupMenu(popup);
        JMenuItem itemEdit = new JMenuItem("Edytuj"); styleMenuItem(itemEdit);
        JMenuItem itemDelete = new JMenuItem("Usuń"); styleMenuItem(itemDelete);
        itemEdit.addActionListener(e -> pokazOknoEdycjiPortfela(nazwa));
        itemDelete.addActionListener(e -> {
            int c = JOptionPane.showConfirmDialog(this, "Usunąć portfel '" + nazwa + "'?", "Potwierdź", JOptionPane.YES_NO_OPTION);
            if (c == JOptionPane.YES_OPTION) { BazaDanych.usunPortfel(nazwa); odswiezDashboard(); }
        });
        popup.add(itemEdit); popup.add(new JSeparator()); popup.add(itemDelete);

        MouseAdapter mouseHandler = new MouseAdapter() {
            public void mouseReleased(MouseEvent e) { check(e); } public void mousePressed(MouseEvent e) { check(e); }
            private void check(MouseEvent e) { if (e.isPopupTrigger()) popup.show(e.getComponent(), e.getX(), e.getY()); else if (SwingUtilities.isLeftMouseButton(e) && e.getID() == MouseEvent.MOUSE_PRESSED) otworzPortfel(nazwa); }
            public void mouseEntered(MouseEvent e) { card.setBackground(CARD_HOVER); } public void mouseExited(MouseEvent e) { card.setBackground(CARD_BG); }
        };
        card.addMouseListener(mouseHandler); lName.addMouseListener(mouseHandler); lInfo.addMouseListener(mouseHandler); lVal.addMouseListener(mouseHandler); lIcon.addMouseListener(mouseHandler);
        gridPanel.add(card);
    }

    private void zaktualizujKarte(String nazwa, double wartosc, int ilosc) {
        if (valueLabelsMap.containsKey(nazwa)) valueLabelsMap.get(nazwa).setText(String.format("%,.2f zł", wartosc));
        if (infoLabelsMap.containsKey(nazwa)) infoLabelsMap.get(nazwa).setText(ilosc + " pozycji");
    }

    private void pokazOknoEdycjiPortfela(String staraNazwa) {
        boolean trybEdycji = (staraNazwa != null);
        JDialog d = new JDialog(this, trybEdycji ? "Edytuj Portfel" : "Nowy Portfel", Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        d.setSize(400, 480);
        d.setLocationRelativeTo(this);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(CARD_BG);
        root.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        d.setContentPane(root);

        // --- GÓRNY PASEK (NAGŁÓWEK) ---
        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(CARD_BG);
        head.setBorder(new EmptyBorder(15,20,15,20));

        JLabel title = new JLabel(trybEdycji ? "Edytuj Portfel" : "Nowy Portfel");
        title.setForeground(TEXT_MAIN);
        title.setFont(FONT_TITLE.deriveFont(20f));

        JButton close = new JButton("X");
        styleMiniActionBtn(close, TEXT_DIM);
        close.addActionListener(e -> d.dispose());

        head.add(title, BorderLayout.WEST);
        head.add(close, BorderLayout.EAST);
        root.add(head, BorderLayout.NORTH);

        // --- FORMULARZ ---
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(CARD_BG);
        form.setBorder(new EmptyBorder(10, 30, 20, 30));

        JTextField tfName = new JTextField(trybEdycji ? staraNazwa : "");
        stylizujInput(tfName);
        tfName.setAlignmentX(Component.CENTER_ALIGNMENT);

        // --- PANEL IKON (ZMODYFIKOWANY) ---
        JPanel iconPanel = new JPanel(new GridLayout(2, 2, 20, 20));
        iconPanel.setOpaque(false);
        iconPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        String[] icons = {"wallet.png", "crypto.png", "stock.png", "bond.png"};
        selectedIconTemp = trybEdycji ? BazaDanych.pobierzIkone(staraNazwa) : "wallet.png";

        ButtonGroup bg = new ButtonGroup();

        for(String ic : icons) {
            JToggleButton btn = new JToggleButton();

            // WAŻNE: Zapamiętujemy nazwę bazową ikony w przycisku
            btn.putClientProperty("baseIcon", ic);

            // Stylizacja przycisku
            btn.setBackground(INPUT_BG);
            btn.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
            btn.setFocusPainted(false);
            btn.setContentAreaFilled(false);
            btn.setOpaque(true);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

            // Ustawienie stanu początkowego (wybrany vs niewybrany)
            if(ic.equals(selectedIconTemp)) {
                btn.setSelected(true);
                btn.setBackground(ACCENT);
                // Dla wybranego ładujemy ikonę z dopiskiem _w
                btn.setIcon(loadIcon(ic.replace(".png", "_w.png"), 64));
            } else {
                btn.setBackground(INPUT_BG);
                // Dla zwykłego ładujemy normalną ikonę
                btn.setIcon(loadIcon(ic, 64));
            }

            // Logika kliknięcia
            btn.addActionListener(e -> {
                selectedIconTemp = ic;

                // Przelatujemy przez wszystkie przyciski w grupie, aby zaktualizować ich wygląd
                Enumeration<AbstractButton> bs = bg.getElements();
                while(bs.hasMoreElements()) {
                    AbstractButton b = bs.nextElement();
                    String base = (String) b.getClientProperty("baseIcon"); // Pobieramy nazwę bazową (np. wallet.png)

                    if (b == btn) {
                        // Ten przycisk został właśnie kliknięty -> Zielone tło, biała ikona
                        b.setBackground(ACCENT);
                        b.setIcon(loadIcon(base.replace(".png", "_w.png"), 64));
                    } else {
                        // Reszta przycisków -> Ciemne tło, zwykła ikona
                        b.setBackground(INPUT_BG);
                        b.setIcon(loadIcon(base, 64));
                    }
                }
            });

            bg.add(btn);
            iconPanel.add(btn);
        }

        form.add(createLabel("Nazwa portfela:"));
        form.add(Box.createVerticalStrut(8));
        form.add(tfName);
        form.add(Box.createVerticalStrut(20));
        form.add(createLabel("Wybierz ikonę:"));
        form.add(Box.createVerticalStrut(8));
        form.add(iconPanel);
        form.add(Box.createVerticalGlue());

        // --- PRZYCISK ZAPISU ---
        JButton btnSave = new JButton(trybEdycji ? "ZAPISZ ZMIANY" : "UTWÓRZ");
        styleButton(btnSave, ACCENT);
        btnSave.setAlignmentX(Component.CENTER_ALIGNMENT);

        btnSave.addActionListener(e -> {
            String n = tfName.getText().trim().replaceAll("[^a-zA-Z0-9żźćńółęśąŻŹĆŃÓŁĘŚĄ _-]", "");
            if(!n.isEmpty()) {
                if (trybEdycji) {
                    if (!n.equals(staraNazwa)) BazaDanych.zmienNazwePortfela(staraNazwa, n);
                } else {
                    BazaDanych.utworzPortfel(n);
                }
                BazaDanych.ustawIkone(n, selectedIconTemp);
                odswiezDashboard();
                d.dispose();
            }
        });

        JPanel footer = new JPanel();
        footer.setBackground(CARD_BG);
        footer.setBorder(new EmptyBorder(0,0,20,0));
        footer.add(btnSave);

        root.add(form, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        d.setVisible(true);
    }

    // --- UTYLITY PUBLICZNE ---
    public static ImageIcon loadIcon(String name, int size) {
        try {
            URL url = PortfolioManager.class.getResource("/icons/" + name);
            if (url == null) url = PortfolioManager.class.getResource("/icons/wallet.png");
            if (url == null) return null;
            return new ImageIcon(new ImageIcon(url).getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH));
        } catch (Exception e) { return null; }
    }
    public static void stylizujInput(JTextField tf) {
        tf.setOpaque(true); tf.setBackground(INPUT_BG); tf.setForeground(TEXT_MAIN); tf.setCaretColor(TEXT_MAIN);
        tf.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER_COLOR), new EmptyBorder(5,10,5,10)));
        tf.setFont(FONT_NORMAL); tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40)); tf.setPreferredSize(new Dimension(300, 40));
    }
    public static void styleButton(JButton b, Color bg) { b.setUI(new BasicButtonUI()); b.setBackground(bg); b.setForeground(Color.WHITE); b.setFocusPainted(false); b.setBorderPainted(false); b.setFont(new Font("Segoe UI", Font.BOLD, 14)); b.setBorder(new EmptyBorder(10, 30, 10, 30)); b.setCursor(new Cursor(Cursor.HAND_CURSOR)); }
    public static void styleMiniActionBtn(JButton b, Color col) { b.setUI(new BasicButtonUI()); b.setBorder(null); b.setContentAreaFilled(false); b.setFocusPainted(false); b.setForeground(col); b.setFont(new Font("Segoe UI Symbol", Font.BOLD, 16)); b.setCursor(new Cursor(Cursor.HAND_CURSOR)); b.addMouseListener(new MouseAdapter() { public void mouseEntered(MouseEvent e) { b.setForeground(col.brighter()); } public void mouseExited(MouseEvent e) { b.setForeground(col); } }); }
    public static void stylePopupMenu(JPopupMenu p) { p.setBackground(CARD_BG); p.setBorder(BorderFactory.createLineBorder(BORDER_COLOR)); }
    public static void styleMenuItem(JMenuItem item) { item.setBackground(CARD_BG); item.setForeground(TEXT_MAIN); item.setFont(new Font("Segoe UI", Font.PLAIN, 14)); item.setBorder(new EmptyBorder(8, 15, 8, 15)); item.setUI(new javax.swing.plaf.basic.BasicMenuItemUI() { @Override protected void paintBackground(Graphics g, JMenuItem menuItem, Color bgColor) { if (menuItem.isArmed() || menuItem.isSelected()) { g.setColor(INPUT_BG); } else { g.setColor(CARD_BG); } g.fillRect(0, 0, menuItem.getWidth(), menuItem.getHeight()); }}); }
    public static void styleScrollPane(JScrollPane sp) { sp.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0)); sp.getVerticalScrollBar().setUI(new BasicScrollBarUI() { @Override protected void configureScrollBarColors() { this.thumbColor = BORDER_COLOR; this.trackColor = BG_COLOR; } @Override protected JButton createDecreaseButton(int orientation) { return createZeroButton(); } @Override protected JButton createIncreaseButton(int orientation) { return createZeroButton(); } }); }
    private static JButton createZeroButton() { JButton b=new JButton(); b.setPreferredSize(new Dimension(0,0)); return b; }
    private JButton createWinButton(String t, boolean red) { JButton b = new JButton(t); b.setUI(new BasicButtonUI()); b.setBorder(null); b.setBackground(BG_COLOR); b.setForeground(TEXT_MAIN); b.setPreferredSize(new Dimension(45, 30)); b.setFocusPainted(false); b.addMouseListener(new MouseAdapter() { public void mouseEntered(MouseEvent e) { b.setBackground(red ? ACCENT_RED : CARD_HOVER); } public void mouseExited(MouseEvent e) { b.setBackground(BG_COLOR); } }); return b; }

    // FIX: Wyśrodkowanie etykiet
    private JLabel createLabel(String txt) { JLabel l = new JLabel(txt); l.setForeground(TEXT_DIM); l.setAlignmentX(Component.CENTER_ALIGNMENT); return l; }

    // FIX: Ulepszona stylizacja ComboBoxa (poprawiona obsługa stanu disabled)
// FIX: Stylizacja ComboBoxa z wymuszonym ciemnym tłem dla stanu DISABLED
    public static void stylizujComboBox(JComboBox<?> combo) {
        combo.setFont(FONT_NORMAL);
        combo.setBackground(INPUT_BG);
        combo.setForeground(TEXT_MAIN);
        combo.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        combo.setOpaque(true);

        combo.setUI(new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton btn = new JButton("▼");
                btn.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
                btn.setFocusPainted(false);
                btn.setContentAreaFilled(false);
                btn.setForeground(TEXT_DIM);
                return btn;
            }

            // KLUCZOWA ZMIANA: Nadpisujemy metodę rysującą wybraną wartość.
            // Domyślna implementacja Swing pobiera kolory z UIManagera dla stanu disabled (często białe/szare).
            // Tutaj wymuszamy nasze kolory INPUT_BG i TEXT_DIM.
            @Override
            public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
                ListCellRenderer renderer = comboBox.getRenderer();
                Component c;

                if (hasFocus && !isPopupVisible(comboBox)) {
                    c = renderer.getListCellRendererComponent(listBox, comboBox.getSelectedItem(), -1, true, false);
                } else {
                    c = renderer.getListCellRendererComponent(listBox, comboBox.getSelectedItem(), -1, false, false);
                }

                c.setFont(comboBox.getFont());

                // RĘCZNE WYMUSZENIE KOLORÓW
                if (!comboBox.isEnabled()) {
                    c.setForeground(TEXT_DIM);
                    c.setBackground(INPUT_BG); // To naprawia białe tło w "Krypto"
                } else {
                    c.setForeground(TEXT_MAIN);
                    c.setBackground(INPUT_BG);
                }

                // Upewniamy się, że komponent jest nieprzezroczysty, żeby kolor tła był widoczny
                if (c instanceof JComponent) {
                    ((JComponent) c).setOpaque(true);
                }

                boolean shouldValidate = (c instanceof JPanel);
                currentValuePane.paintComponent(g, c, comboBox, bounds.x, bounds.y, bounds.width, bounds.height, shouldValidate);
            }

            @Override
            public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                g.setColor(INPUT_BG);
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
        });

        // Renderer dla listy rozwijanej (elementy w środku)
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel c = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                c.setBorder(new EmptyBorder(6, 8, 6, 8));

                if (isSelected && index != -1) {
                    c.setBackground(ACCENT);
                    c.setForeground(Color.WHITE);
                } else {
                    c.setBackground(INPUT_BG);
                    c.setForeground(TEXT_MAIN);
                }
                return c;
            }
        });
    }

    // FIX: Spinner bez białej ramki
    public static void stylizujSpinner(JSpinner spinner) {
        spinner.setFont(FONT_NORMAL);
        spinner.setBackground(INPUT_BG);
        spinner.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
            tf.setBackground(INPUT_BG);
            tf.setForeground(TEXT_MAIN);
            tf.setCaretColor(TEXT_MAIN);
            tf.setBorder(new EmptyBorder(0, 5, 0, 0));
        }

        spinner.setUI(new javax.swing.plaf.basic.BasicSpinnerUI() {
            @Override protected Component createNextButton() { return createArrowButton("▲"); }
            @Override protected Component createPreviousButton() { return createArrowButton("▼"); }

            private JButton createArrowButton(String txt) {
                JButton b = new JButton(txt);
                b.setBorder(null); b.setFocusPainted(false); b.setContentAreaFilled(false);
                b.setForeground(TEXT_DIM); b.setBackground(INPUT_BG);
                b.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 10));
                b.setPreferredSize(new Dimension(20, 10));
                b.addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) { b.setForeground(Color.WHITE); }
                    public void mouseExited(MouseEvent e) { b.setForeground(TEXT_DIM); }
                });
                return b;
            }
        });
        spinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
    }

    private void otworzPortfel(String nazwa) {
        if (portfolioView != null) mainContainer.remove(portfolioView);
        portfolioView = new Main(nazwa, this);
        mainContainer.add(portfolioView, "PORTFOLIO");
        cardLayout.show(mainContainer, "PORTFOLIO");
    }
    public void wrocDoDashboard() { cardLayout.show(mainContainer, "DASHBOARD"); odswiezDashboard(); }
}