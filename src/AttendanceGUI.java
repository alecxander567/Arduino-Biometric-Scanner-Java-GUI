import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import com.fazecast.jSerialComm.SerialPort;
import java.io.*;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.table.TableCellEditor;
import javax.swing.border.*;

public class AttendanceGUI extends JFrame {
    private JTable studentTable;
    private DefaultTableModel tableModel;
    private SerialPort arduinoPort;
    private static final String DATA_FILE = "students.dat";
    private static final String LAST_RESET_FILE = "last_reset.dat";
    private JLabel statusLabel;
    private JLabel connectionLabel;
    private JLabel totalCountLabel;
    private JLabel presentCountLabel;
    private JLabel absentCountLabel;
    private volatile boolean dialogOpen = false;
    private volatile boolean processingFingerprint = false;
    private volatile int lastProcessedFingerprintID = -1;
    private JDialog progressDialog;
    private JProgressBar progressBar;
    private JLabel progressLabel;
    private Timer resetTimer;
    
    private static final Color PRIMARY_COLOR = new Color(99, 102, 241); 
    private static final Color PRIMARY_DARK = new Color(79, 70, 229);
    private static final Color SUCCESS_COLOR = new Color(16, 185, 129);  
    private static final Color DANGER_COLOR = new Color(239, 68, 68);    
    private static final Color WARNING_COLOR = new Color(245, 158, 11); 
    private static final Color INFO_COLOR = new Color(59, 130, 246);  
    private static final Color BACKGROUND_COLOR = new Color(248, 250, 252);  
    private static final Color CARD_COLOR = Color.WHITE;
    private static final Color TEXT_PRIMARY = new Color(15, 23, 42);  
    private static final Color TEXT_SECONDARY = new Color(100, 116, 139); 
    private static final Color BORDER_COLOR = new Color(226, 232, 240);  
    private static final Color HOVER_COLOR = new Color(241, 245, 249);   
    
    public AttendanceGUI() {
        setTitle("Biometric Attendance System");
        setSize(1400, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BACKGROUND_COLOR);
        
        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBackground(BACKGROUND_COLOR);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
        
        JPanel headerPanel = createModernHeaderPanel();
        JPanel statsPanel = createStatsPanel();
        JPanel contentPanel = createContentPanel();
        JPanel statusPanel = createModernStatusPanel();
        
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        
        JPanel centerContainer = new JPanel(new BorderLayout(20, 20));
        centerContainer.setBackground(BACKGROUND_COLOR);
        centerContainer.add(statsPanel, BorderLayout.NORTH);
        centerContainer.add(contentPanel, BorderLayout.CENTER);
        
        mainPanel.add(centerContainer, BorderLayout.CENTER);
        mainPanel.add(statusPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
        
        loadStudentData();
        checkAndResetAttendance();
        startDailyResetTimer();
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (resetTimer != null) {
                    resetTimer.cancel();
                }
                if (arduinoPort != null && arduinoPort.isOpen()) {
                    arduinoPort.closePort();
                }
                saveStudentData();
            }
        });
        
        setVisible(true);
        setupSerialPort("COM7");	
    }
    
    private JPanel createModernHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout(15, 15));
        headerPanel.setBackground(CARD_COLOR);
        headerPanel.setBorder(createCardBorder());
        
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        leftPanel.setBackground(CARD_COLOR);
        
        JLabel iconLabel = new JLabel("🔐");
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 36));
        iconLabel.setVisible(false); 
        
        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setBackground(CARD_COLOR);
        
        JLabel titleLabel = new JLabel("Biometric Attendance System");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setForeground(TEXT_PRIMARY);
        
        JLabel subtitleLabel = new JLabel("Real-time fingerprint authentication & tracking");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        subtitleLabel.setForeground(TEXT_SECONDARY);
        
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(5));
        titlePanel.add(subtitleLabel);
        
        leftPanel.add(iconLabel);
        leftPanel.add(titlePanel);
        
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightPanel.setBackground(CARD_COLOR);
        
        connectionLabel = new JLabel("Connecting...");
        connectionLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        connectionLabel.setForeground(TEXT_SECONDARY);
        connectionLabel.setOpaque(true);
        connectionLabel.setBackground(HOVER_COLOR);
        connectionLabel.setBorder(BorderFactory.createCompoundBorder(
            createRoundedBorder(HOVER_COLOR, 8),
            BorderFactory.createEmptyBorder(8, 16, 8, 16)
        ));
        
        rightPanel.add(connectionLabel);
        
        headerPanel.add(leftPanel, BorderLayout.WEST);
        headerPanel.add(rightPanel, BorderLayout.EAST);
        
        return headerPanel;
    }
    
    private JPanel createStatsPanel() {
        JPanel statsPanel = new JPanel(new GridLayout(1, 3, 15, 0));
        statsPanel.setBackground(BACKGROUND_COLOR);
        
        JPanel totalCard = createStatCard("Total Students", "0", PRIMARY_COLOR, "👥");
        JPanel presentCard = createStatCard("Present Today", "0", SUCCESS_COLOR, "✓");
        JPanel absentCard = createStatCard("Absent Today", "0", DANGER_COLOR, "✗");
        
        statsPanel.add(totalCard);
        statsPanel.add(presentCard);
        statsPanel.add(absentCard);
        
        return statsPanel;
    }
    
    private JPanel createStatCard(String title, String value, Color accentColor, String icon) {
        JPanel card = new JPanel(new BorderLayout(15, 10));
        card.setBackground(CARD_COLOR);
        card.setBorder(createCardBorder());
        
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 40));
        iconLabel.setOpaque(true);
        iconLabel.setBackground(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 30));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setPreferredSize(new Dimension(80, 80));
        iconLabel.setBorder(createRoundedBorder(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 30), 12));
        
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBackground(CARD_COLOR);
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        titleLabel.setForeground(TEXT_SECONDARY);
        
        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 32));
        valueLabel.setForeground(accentColor);
        
        if (title.equals("Total Students")) {
            totalCountLabel = valueLabel;
        } else if (title.equals("Present Today")) {
            presentCountLabel = valueLabel;
        } else if (title.equals("Absent Today")) {
            absentCountLabel = valueLabel;
        }
        
        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(5));
        textPanel.add(valueLabel);
        
        card.add(iconLabel, BorderLayout.WEST);
        card.add(textPanel, BorderLayout.CENTER);
        
        return card;
    }
    
    private JPanel createContentPanel() {
        JPanel contentPanel = new JPanel(new BorderLayout(0, 15));
        contentPanel.setBackground(BACKGROUND_COLOR);
        
        JPanel actionPanel = createModernButtonPanel();
        
        tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 5; 
            }
        };
        tableModel.addColumn("Student ID");
        tableModel.addColumn("Name");
        tableModel.addColumn("Fingerprint ID");
        tableModel.addColumn("Status");
        tableModel.addColumn("Last Scan");
        tableModel.addColumn("Actions");
        
        studentTable = new JTable(tableModel);
        styleModernTable();
        
        JScrollPane scrollPane = new JScrollPane(studentTable);
        scrollPane.setBorder(createCardBorder());
        scrollPane.getViewport().setBackground(CARD_COLOR);
        
        contentPanel.add(actionPanel, BorderLayout.NORTH);
        contentPanel.add(scrollPane, BorderLayout.CENTER);
        
        return contentPanel;
    }
    
    private JPanel createModernButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        buttonPanel.setBackground(CARD_COLOR);
        buttonPanel.setBorder(createCardBorder());
        
        JButton markAbsentBtn = createModernButton("Mark All Absent", WARNING_COLOR);
        JButton refreshBtn = createModernButton("Refresh", INFO_COLOR);
        JButton exportBtn = createModernButton("Export CSV", SUCCESS_COLOR);
        JButton clearDataBtn = createModernButton("Clear All", DANGER_COLOR);
        
        markAbsentBtn.addActionListener(e -> markAllAbsent());
        refreshBtn.addActionListener(e -> loadStudentData());
        exportBtn.addActionListener(e -> exportToCSV());
        clearDataBtn.addActionListener(e -> clearAllData());
        
        buttonPanel.add(markAbsentBtn);
        buttonPanel.add(refreshBtn);
        buttonPanel.add(exportBtn);
        buttonPanel.add(clearDataBtn);
        
        return buttonPanel;
    }
    
    private JButton createModernButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
            createRoundedBorder(color, 8),
            BorderFactory.createEmptyBorder(12, 24, 12, 24)
        ));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(color.darker());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(color);
            }
        });
        
        return button;
    }
    
    private JPanel createModernStatusPanel() {
        JPanel statusPanel = new JPanel(new BorderLayout(15, 0));
        statusPanel.setBackground(CARD_COLOR);
        statusPanel.setBorder(createCardBorder());
        
        JPanel leftStatus = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftStatus.setBackground(CARD_COLOR);
        
        statusLabel = new JLabel("System Ready - Waiting for fingerprint scan...");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        statusLabel.setForeground(TEXT_PRIMARY);
        
        leftStatus.add(statusLabel);
        
        statusPanel.add(leftStatus, BorderLayout.WEST);
        
        return statusPanel;
    }
    
    private void styleModernTable() {
        studentTable.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        studentTable.setRowHeight(65);
        studentTable.setShowGrid(false);
        studentTable.setIntercellSpacing(new Dimension(0, 1));
        studentTable.setSelectionBackground(new Color(PRIMARY_COLOR.getRed(), PRIMARY_COLOR.getGreen(), PRIMARY_COLOR.getBlue(), 30));
        studentTable.setSelectionForeground(TEXT_PRIMARY);
        studentTable.setBackground(CARD_COLOR);
        
        JTableHeader header = studentTable.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 14));
        header.setBackground(BACKGROUND_COLOR);
        header.setForeground(TEXT_PRIMARY);
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 55));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, BORDER_COLOR));
        
        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setBackground(BACKGROUND_COLOR);
                setForeground(TEXT_SECONDARY);
                setFont(new Font("Segoe UI", Font.BOLD, 13));
                setHorizontalAlignment(LEFT);
                setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
                return c;
            }
        };
        
        for (int i = 0; i < studentTable.getColumnCount(); i++) {
            studentTable.getColumnModel().getColumn(i).setHeaderRenderer(headerRenderer);
        }
        
        studentTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        studentTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        studentTable.getColumnModel().getColumn(2).setPreferredWidth(130);
        studentTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        studentTable.getColumnModel().getColumn(4).setPreferredWidth(180);
        studentTable.getColumnModel().getColumn(5).setPreferredWidth(200);

        studentTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                
                JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 18));
                panel.setOpaque(true);
                panel.setBackground(row % 2 == 0 ? CARD_COLOR : HOVER_COLOR);
                
                if (value != null) {
                    JLabel badge = new JLabel(value.toString());
                    badge.setFont(new Font("Segoe UI", Font.BOLD, 13));
                    badge.setOpaque(true);
                    badge.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
                    
                    if (value.toString().equals("Present")) {
                        badge.setForeground(Color.WHITE);
                        badge.setBackground(SUCCESS_COLOR);
                    } else if (value.toString().equals("Absent")) {
                        badge.setForeground(Color.WHITE);
                        badge.setBackground(DANGER_COLOR);
                    } else {
                        badge.setForeground(TEXT_SECONDARY);
                        badge.setBackground(HOVER_COLOR);
                    }
                    
                    badge.setBorder(createRoundedBorder(badge.getBackground(), 6));
                    panel.add(badge);
                }
                
                return panel;
            }
        });
        
        studentTable.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                
                JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 15));
                panel.setOpaque(true);
                panel.setBackground(row % 2 == 0 ? CARD_COLOR : HOVER_COLOR);
                
                JButton editBtn = new JButton("Edit");
                styleActionButton(editBtn, INFO_COLOR);
                
                JButton deleteBtn = new JButton("Delete");
                styleActionButton(deleteBtn, DANGER_COLOR);
                
                final int currentRow = row;
                editBtn.addActionListener(e -> editStudent(currentRow));
                deleteBtn.addActionListener(e -> deleteStudent(currentRow));
                
                panel.add(editBtn);
                panel.add(deleteBtn);
                
                return panel;
            }
        });
        
        DefaultTableCellRenderer cellRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
                setHorizontalAlignment(LEFT);
                
                if (!isSelected) {
                    setBackground(row % 2 == 0 ? CARD_COLOR : HOVER_COLOR);
                }
                
                setForeground(TEXT_PRIMARY);
                return c;
            }
        };
        
        for (int i = 0; i < 5; i++) {
            if (i != 3) {
                studentTable.getColumnModel().getColumn(i).setCellRenderer(cellRenderer);
            }
        }
        
        studentTable.getColumnModel().getColumn(5).setCellEditor(new ActionButtonEditor());
    }
    
    private void styleActionButton(JButton button, Color color) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setBorder(createRoundedBorder(color, 6));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(90, 32));
        
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            Color originalColor = color;
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(color.darker());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(originalColor);
            }
        });
    }
    
    private Border createCardBorder() {
        return BorderFactory.createCompoundBorder(
            new RoundedBorder(BORDER_COLOR, 12, 1),
            BorderFactory.createEmptyBorder(20, 20, 20, 20)
        );
    }
    
    private Border createRoundedBorder(Color color, int radius) {
        return new RoundedBorder(color, radius, 0);
    }

    private static class RoundedBorder extends AbstractBorder {
        private Color color;
        private int radius;
        private int thickness;
        
        RoundedBorder(Color color, int radius, int thickness) {
            this.color = color;
            this.radius = radius;
            this.thickness = thickness;
        }
        
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            if (thickness > 0) {
                g2.setStroke(new BasicStroke(thickness));
                g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            }
            g2.dispose();
        }
        
        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(thickness, thickness, thickness, thickness);
        }
    }
    
    private class ActionButtonEditor extends AbstractCellEditor implements TableCellEditor {
        private final JPanel panel;
        private final JButton editBtn;
        private final JButton deleteBtn;
        private int currentRow;

        public ActionButtonEditor() {
            panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 15));

            editBtn = new JButton("Edit");
            styleActionButton(editBtn, INFO_COLOR);

            deleteBtn = new JButton("Delete");
            styleActionButton(deleteBtn, DANGER_COLOR);

            editBtn.addActionListener(e -> {
                fireEditingStopped();
                editStudent(currentRow);
            });

            deleteBtn.addActionListener(e -> {
                fireEditingStopped();
                deleteStudent(currentRow);
            });

            panel.add(editBtn);
            panel.add(deleteBtn);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {
            currentRow = row;
            panel.setBackground(row % 2 == 0 ? CARD_COLOR : HOVER_COLOR);
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            return null;
        }
    }
    
    private void startDailyResetTimer() {
        resetTimer = new Timer(true);
        resetTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkAndResetAttendance();
            }
        }, 0, 60000);
    }
    
    private void checkAndResetAttendance() {
        LocalDateTime now = LocalDateTime.now();
        LocalTime currentTime = now.toLocalTime();
        LocalTime resetTime = LocalTime.of(9, 0);
        
        if (currentTime.isAfter(resetTime)) {
            try {
                File lastResetFile = new File(LAST_RESET_FILE);
                LocalDateTime lastReset = null;
                
                if (lastResetFile.exists()) {
                    try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(LAST_RESET_FILE))) {
                        lastReset = (LocalDateTime) ois.readObject();
                    }
                }
                
                if (lastReset == null || lastReset.toLocalDate().isBefore(now.toLocalDate())) {
                    SwingUtilities.invokeLater(() -> {
                        autoMarkAllAbsent();
                        saveLastResetTime(now);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    private void saveLastResetTime(LocalDateTime time) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(LAST_RESET_FILE))) {
            oos.writeObject(time);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void autoMarkAllAbsent() {
        int absentCount = 0;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setValueAt("Absent", i, 3);
            tableModel.setValueAt("-", i, 4);
            absentCount++;
        }
        saveStudentData();
        updateAttendanceCounts();
        updateStatus("Auto-reset: All " + absentCount + " students marked as Absent for new day.");
        System.out.println("Daily auto-reset completed at " + LocalDateTime.now());
    }
    
    private void editStudent(int row) {
        if (row < 0 || row >= tableModel.getRowCount()) return;
        
        String currentName = tableModel.getValueAt(row, 1).toString();
        String studentID = tableModel.getValueAt(row, 0).toString();
        
        String newName = JOptionPane.showInputDialog(this, "Edit Student Name:", currentName);
        
        if (newName != null && !newName.trim().isEmpty() && !newName.equals(currentName)) {
            tableModel.setValueAt(newName.trim(), row, 1);
            saveStudentData();
            updateStatus("Updated student name: " + studentID + " to " + newName);
            showStyledDialog(
                "Student name updated successfully!\n\nStudent ID: " + studentID + "\nNew Name: " + newName,
                "Edit Success",
                JOptionPane.INFORMATION_MESSAGE
            );
        }
    }
    
    private void deleteStudent(int row) {
        if (row < 0 || row >= tableModel.getRowCount()) return;
        
        String studentID = tableModel.getValueAt(row, 0).toString();
        String studentName = tableModel.getValueAt(row, 1).toString();
        int fingerprintID = Integer.parseInt(tableModel.getValueAt(row, 2).toString());
        
        int confirm = JOptionPane.showConfirmDialog(
            this,
            "Delete this student?\n\n" +
            "Student ID: " + studentID + "\n" +
            "Name: " + studentName + "\n" +
            "Fingerprint ID: " + fingerprintID + "\n\n" +
            "This will also delete their fingerprint from the sensor.",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (confirm == JOptionPane.YES_OPTION) {
            if (arduinoPort != null && arduinoPort.isOpen()) {
                try {
                    String command = "DELETEFP:" + fingerprintID + "\n";
                    arduinoPort.getOutputStream().write(command.getBytes());
                    arduinoPort.getOutputStream().flush();
                    System.out.println("Sent delete command for fingerprint ID: " + fingerprintID);
                } catch (IOException e) {
                    e.printStackTrace();
                    showStyledDialog(
                        "Failed to delete fingerprint from sensor: " + e.getMessage(),
                        "Sensor Error",
                        JOptionPane.ERROR_MESSAGE
                    );
                }
            }
            
            tableModel.removeRow(row);
            saveStudentData();
            updateAttendanceCounts();
            
            updateStatus("Deleted student: " + studentName + " (ID: " + studentID + ")");
            showStyledDialog(
                "Student deleted successfully!\n\n" +
                "Student ID: " + studentID + "\n" +
                "Name: " + studentName + "\n" +
                "Fingerprint ID " + fingerprintID + " can now be reused.",
                "Delete Success",
                JOptionPane.INFORMATION_MESSAGE
            );
        }
    }
    
    private void showProgressDialog(String title, String message) {
        SwingUtilities.invokeLater(() -> {
            if (progressDialog != null && progressDialog.isVisible()) {
                return; 
            }
            
            progressDialog = new JDialog(this, title, false);
            progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            progressDialog.setSize(450, 180);
            progressDialog.setLocationRelativeTo(this);
            progressDialog.setResizable(false);
            
            JPanel panel = new JPanel(new BorderLayout(15, 15));
            panel.setBackground(CARD_COLOR);
            panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
            
            progressLabel = new JLabel(message);
            progressLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            progressLabel.setForeground(TEXT_PRIMARY);
            progressLabel.setHorizontalAlignment(SwingConstants.CENTER);
            
            progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            progressBar.setPreferredSize(new Dimension(400, 35));
            progressBar.setForeground(PRIMARY_COLOR);
            progressBar.setBackground(HOVER_COLOR);
            
            panel.add(progressLabel, BorderLayout.NORTH);
            panel.add(progressBar, BorderLayout.CENTER);
            
            progressDialog.add(panel);
            progressDialog.setVisible(true);
        });
    }
    
    private void updateProgressDialog(String message) {
        SwingUtilities.invokeLater(() -> {
            if (progressLabel != null && progressDialog != null && progressDialog.isVisible()) {
                progressLabel.setText(message);
            }
        });
    }
    
    private void hideProgressDialog() {
        SwingUtilities.invokeLater(() -> {
            if (progressDialog != null) {
                progressDialog.setVisible(false);
                progressDialog.dispose();
                progressDialog = null;
                progressLabel = null;
                progressBar = null;
            }
            processingFingerprint = false;
        });
    }
    
    private static class Student implements Serializable {
        String studentID;
        String name;
        int fingerprintID;
        String status;
        String lastScan;
        
        Student(String studentID, String name, int fingerprintID, String status, String lastScan) {
            this.studentID = studentID;
            this.name = name;
            this.fingerprintID = fingerprintID;
            this.status = status;
            this.lastScan = lastScan;
        }
    }
    
    private void saveStudentData() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            ArrayList<Student> students = new ArrayList<>();
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                students.add(new Student(
                    tableModel.getValueAt(i, 0).toString(),
                    tableModel.getValueAt(i, 1).toString(),
                    Integer.parseInt(tableModel.getValueAt(i, 2).toString()),
                    tableModel.getValueAt(i, 3).toString(),
                    tableModel.getValueAt(i, 4).toString()
                ));
            }
            oos.writeObject(students);
            System.out.println("Data saved successfully!");
        } catch (IOException e) {
            e.printStackTrace();
            showStyledDialog("Error saving data: " + e.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    @SuppressWarnings("unchecked")
    private void loadStudentData() {
        File file = new File(DATA_FILE);
        if (!file.exists()) {
            System.out.println("No saved data found. Starting fresh.");
            updateAttendanceCounts();
            return;
        }
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(DATA_FILE))) {
            ArrayList<Student> students = (ArrayList<Student>) ois.readObject();
            tableModel.setRowCount(0);
            for (Student student : students) {
                tableModel.addRow(new Object[]{
                    student.studentID, 
                    student.name, 
                    student.fingerprintID, 
                    student.status,
                    student.lastScan,
                    ""
                });
            }
            System.out.println("Loaded " + students.size() + " students from storage.");
            updateStatus("Loaded " + students.size() + " students from storage.");
            updateAttendanceCounts();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            showStyledDialog("Error loading data: " + e.getMessage(), "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void markAllAbsent() {
        int confirm = JOptionPane.showConfirmDialog(this, 
            "Mark all students as ABSENT?\n\nThis will reset attendance for a new session.", 
            "Start New Attendance Session", 
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);
        
        if (confirm == JOptionPane.YES_OPTION) {
            int absentCount = 0;
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                tableModel.setValueAt("Absent", i, 3);
                tableModel.setValueAt("-", i, 4);
                absentCount++;
            }
            saveStudentData();
            saveLastResetTime(LocalDateTime.now());
            updateAttendanceCounts();
            updateStatus("All " + absentCount + " students marked as Absent. Ready for new attendance session.");
            showStyledDialog(
                "Attendance Reset!\n\n" + absentCount + " students marked as Absent.\n" +
                "Students will be marked Present when they scan their fingerprint.",
                "Session Started", 
                JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void clearAllData() {
        int confirm = JOptionPane.showConfirmDialog(this, 
            "Are you sure you want to delete ALL student + fingerprint data?", 
            "Confirm Delete", 
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        
        if (confirm == JOptionPane.YES_OPTION) {
            tableModel.setRowCount(0);
            saveStudentData();
            updateAttendanceCounts();
            
            File file = new File(DATA_FILE);
            if (file.exists()) file.delete();
            
            File resetFile = new File(LAST_RESET_FILE);
            if (resetFile.exists()) resetFile.delete();

            if (arduinoPort != null && arduinoPort.isOpen()) {
                try {
                    arduinoPort.getOutputStream().write("CLEARFP\n".getBytes());
                    arduinoPort.getOutputStream().flush();
                } catch (IOException e) {
                    e.printStackTrace();
                    showStyledDialog("Failed to send CLEARFP command: " + e.getMessage(), "Serial Error", JOptionPane.ERROR_MESSAGE);
                }
            }

            updateStatus("All local data + fingerprint data cleared.");
        }
    }
    
    private void exportToCSV() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save as CSV");
        fileChooser.setSelectedFile(new File("attendance_data.csv"));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try (PrintWriter writer = new PrintWriter(file)) {
                writer.println("Student ID,Name,Fingerprint ID,Status,Last Scan");
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    writer.println(String.format("%s,%s,%s,%s,%s",
                        tableModel.getValueAt(i, 0),
                        tableModel.getValueAt(i, 1),
                        tableModel.getValueAt(i, 2),
                        tableModel.getValueAt(i, 3),
                        tableModel.getValueAt(i, 4)
                    ));
                }
                showStyledDialog("Data exported successfully!", "Export Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                showStyledDialog("Error exporting: " + e.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void updateStatus(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
    }
    
    private void updateAttendanceCounts() {
        SwingUtilities.invokeLater(() -> {
            int total = tableModel.getRowCount();
            int present = 0;
            int absent = 0;
            
            for (int i = 0; i < total; i++) {
                String status = tableModel.getValueAt(i, 3).toString();
                if (status.equals("Present")) {
                    present++;
                } else if (status.equals("Absent")) {
                    absent++;
                }
            }
            
            totalCountLabel.setText(String.valueOf(total));
            presentCountLabel.setText(String.valueOf(present));
            absentCountLabel.setText(String.valueOf(absent));
        });
    }
    
    private void updateConnectionStatus(String message, boolean connected) {
        SwingUtilities.invokeLater(() -> {
            connectionLabel.setText("⬤ " + message);
            connectionLabel.setForeground(Color.WHITE);
            connectionLabel.setBackground(connected ? SUCCESS_COLOR : DANGER_COLOR);
            connectionLabel.setBorder(BorderFactory.createCompoundBorder(
                createRoundedBorder(connected ? SUCCESS_COLOR : DANGER_COLOR, 8),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)
            ));
        });
    }

    private int findStudentByFingerprintID(int fingerprintID) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (Integer.parseInt(tableModel.getValueAt(i, 2).toString()) == fingerprintID) {
                return i;
            }
        }
        return -1;
    }

    private void markAttendance(int row, int fingerprintID) {
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        tableModel.setValueAt("Present", row, 3);
        tableModel.setValueAt(currentTime, row, 4);
        
        String studentName = tableModel.getValueAt(row, 1).toString();
        String studentID = tableModel.getValueAt(row, 0).toString();
        
        updateStatus("Attendance marked for: " + studentName + " (ID: " + studentID + ")");
        saveStudentData();
        updateAttendanceCounts();
        
        hideProgressDialog();
        
        showStyledDialog(
            "Attendance Marked Successfully!\n\n" +
            "Name: " + studentName + 
            "\nStudent ID: " + studentID + 
            "\nTime: " + currentTime,
            "Attendance Success", 
            JOptionPane.INFORMATION_MESSAGE);
    }

    private boolean enrollNewStudent(int fingerprintID) {
        hideProgressDialog();

        String studentName = JOptionPane.showInputDialog(
            this,
            "Enter Student Name for Fingerprint ID: " + fingerprintID,
            "New Student Enrollment",
            JOptionPane.PLAIN_MESSAGE
        );

        if (studentName == null) {
            updateStatus("Enrollment cancelled by user");
            return false;
        }
        
        if (studentName.trim().isEmpty()) {
            studentName = "Student " + fingerprintID;
        }

        String studentID = "STU" + String.format("%04d", fingerprintID);

        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        tableModel.addRow(new Object[]{studentID, studentName, fingerprintID, "Present", currentTime, ""});
        saveStudentData();
        updateAttendanceCounts();

        updateStatus("New student enrolled: " + studentName);
        showStyledDialog(
            "Student Enrolled Successfully!\n\n" +
            "Name: " + studentName +
            "\nStudent ID: " + studentID +
            "\nFingerprint ID: " + fingerprintID +
            "\n\nAttendance: Present",
            "Enrollment Success",
            JOptionPane.INFORMATION_MESSAGE
        );
        
        return true;
    }

    private void showStyledDialog(String message, String title, int messageType) {
        JOptionPane.showMessageDialog(this, message, title, messageType);
    }

    private void setupSerialPort(String portName) {
        arduinoPort = SerialPort.getCommPort(portName);
        arduinoPort.setBaudRate(9600);
        arduinoPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);
        
        if (arduinoPort.openPort()) {
            System.out.println("Port " + portName + " opened successfully!");
            updateConnectionStatus("Connected to " + portName, true);
            updateStatus("Connected to Arduino on " + portName);
        } else {
            System.out.println("Failed to open port " + portName);
            updateConnectionStatus("Connection Failed", false);
            updateStatus("Failed to connect to Arduino on " + portName);
            showStyledDialog(
                "Failed to open serial port: " + portName + 
                "\n\nPlease check:\n1. COM port is correct\n2. Arduino is connected\n3. No other program is using the port",
                "Connection Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        new Thread(() -> {
            byte[] readBuffer = new byte[1024];
            StringBuilder messageBuffer = new StringBuilder();
            
            while (arduinoPort.isOpen()) {
                try {
                    int numRead = arduinoPort.readBytes(readBuffer, readBuffer.length);
                    if (numRead > 0) {
                        String data = new String(readBuffer, 0, numRead);
                        messageBuffer.append(data);
                        
                        String bufferContent = messageBuffer.toString();
                        int newlineIndex;
                        while ((newlineIndex = bufferContent.indexOf('\n')) != -1) {
                            String line = bufferContent.substring(0, newlineIndex).trim();
                            bufferContent = bufferContent.substring(newlineIndex + 1);
                            messageBuffer = new StringBuilder(bufferContent);
                            
                            if (!line.isEmpty()) {
                                System.out.println("Arduino: " + line);
                                processArduinoMessage(line);
                            }
                        }
                    }
                    Thread.sleep(10);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void processArduinoMessage(String line) {
        if (line.contains("Image taken") && !processingFingerprint) {
            processingFingerprint = true;
            lastProcessedFingerprintID = -1;
            showProgressDialog("Processing Fingerprint", "Capturing fingerprint image...");
        } else if (processingFingerprint) {
            if (line.contains("enrolling new fingerprint")) {
                updateProgressDialog("Enrolling new fingerprint...");
            } else if (line.contains("Remove finger")) {
                updateProgressDialog("Please remove your finger...");
            } else if (line.contains("Place same finger again")) {
                updateProgressDialog("Please place the same finger again...");
            } else if (line.contains("Creating model")) {
                updateProgressDialog("Creating fingerprint model...");
            } else if (line.contains("Storing model")) {
                updateProgressDialog("Storing fingerprint data...");
            } else if (line.contains("Enrollment successful")) {
                updateProgressDialog("Enrollment complete!");
            }
        }

        if (line.startsWith("NewID:")) {
            try {
                int fingerprintID = Integer.parseInt(line.split(":")[1].trim());

                if (fingerprintID <= 0) {
                    updateStatus("Enrollment failed. Try again.");
                    hideProgressDialog();
                    processingFingerprint = false;
                    return;
                }

                if (fingerprintID == lastProcessedFingerprintID) {
                    System.out.println("Ignoring duplicate NewID message for fingerprint " + fingerprintID);
                    return;
                }

                SwingUtilities.invokeLater(() -> {
                    if (dialogOpen || fingerprintID == lastProcessedFingerprintID) {
                        System.out.println("Dialog already open or fingerprint already processed, skipping");
                        return;
                    }

                    dialogOpen = true;
                    lastProcessedFingerprintID = fingerprintID;

                    try {
                        int existingRow = findStudentByFingerprintID(fingerprintID);

                        if (existingRow != -1) {
                            markAttendance(existingRow, fingerprintID);
                        } else {
                            boolean enrolled = enrollNewStudent(fingerprintID);
                            if (!enrolled) {
                                lastProcessedFingerprintID = -1;
                                return;
                            }
                        }
                    } finally {
                        dialogOpen = false;
                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                lastProcessedFingerprintID = -1;
                            }
                        }, 2000);
                    }
                });
            } catch (NumberFormatException e) {
                System.err.println("Invalid fingerprint ID: " + line);
                e.printStackTrace();
                hideProgressDialog();
            }
        }

        if (line.contains("Found fingerprint sensor")) {
            updateConnectionStatus("Sensor Ready", true);
            updateStatus("Fingerprint sensor connected and ready");
        } else if (line.contains("Waiting for valid finger")) {
            updateStatus("System ready - Place finger on scanner");
            processingFingerprint = false;
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(AttendanceGUI::new);
    }
}