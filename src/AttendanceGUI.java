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
import java.time.format.DateTimeFormatter;

public class AttendanceGUI extends JFrame {
    private JTable studentTable;
    private DefaultTableModel tableModel;
    private SerialPort arduinoPort;
    private static final String DATA_FILE = "students.dat";
    private JLabel statusLabel;
    private JLabel connectionLabel;
    private volatile boolean dialogOpen = false;
    private volatile boolean processingFingerprint = false;
    private JDialog progressDialog;
    private JProgressBar progressBar;
    private JLabel progressLabel;
    private SerialPort serialPort;
    
    // Modern color scheme
    private static final Color PRIMARY_COLOR = new Color(41, 128, 185);
    private static final Color SUCCESS_COLOR = new Color(39, 174, 96);
    private static final Color DANGER_COLOR = new Color(231, 76, 60);
    private static final Color BACKGROUND_COLOR = new Color(236, 240, 241);
    private static final Color CARD_COLOR = Color.WHITE;
    private static final Color TEXT_PRIMARY = new Color(44, 62, 80);
    private static final Color TEXT_SECONDARY = new Color(127, 140, 141);
    private static final Color HEADER_COLOR = new Color(52, 73, 94);
    private static final Color TABLE_HEADER_COLOR = new Color(70, 130, 180);
    
    public AttendanceGUI() {
        setTitle("Biometric Attendance System");
        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BACKGROUND_COLOR);
        
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBackground(BACKGROUND_COLOR);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JPanel headerPanel = createHeaderPanel();
        
        tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tableModel.addColumn("Student ID");
        tableModel.addColumn("Name");
        tableModel.addColumn("Fingerprint ID");
        tableModel.addColumn("Status");
        tableModel.addColumn("Last Scan");
        
        studentTable = new JTable(tableModel);
        styleTable();
        
        JScrollPane scrollPane = new JScrollPane(studentTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(189, 195, 199), 1));
        scrollPane.getViewport().setBackground(CARD_COLOR);
        
        JPanel statusPanel = createStatusPanel();
        
        JPanel buttonPanel = createButtonPanel();
        
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setBackground(BACKGROUND_COLOR);
        centerPanel.add(buttonPanel, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(statusPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
        
        loadStudentData();
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (arduinoPort != null && arduinoPort.isOpen()) {
                    arduinoPort.closePort();
                }
                saveStudentData();
            }
        });
        
        setVisible(true);
        setupSerialPort("COM7");	
    }
    
    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout(10, 10));
        headerPanel.setBackground(CARD_COLOR);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(189, 195, 199), 1),
            BorderFactory.createEmptyBorder(20, 25, 20, 25)
        ));
        
        JLabel titleLabel = new JLabel("Biometric Attendance System");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setForeground(HEADER_COLOR);
        
        connectionLabel = new JLabel("* Connecting...");
        connectionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        connectionLabel.setForeground(TEXT_SECONDARY);
        
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(connectionLabel, BorderLayout.EAST);
        
        return headerPanel;
    }
    
    private JPanel createStatusPanel() {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(CARD_COLOR);
        statusPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(189, 195, 199), 1),
            BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));
        
        statusLabel = new JLabel("System Ready - Waiting for fingerprint scan...");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        statusLabel.setForeground(TEXT_PRIMARY);
        
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        
        return statusPanel;
    }
    
    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        buttonPanel.setBackground(CARD_COLOR);
        buttonPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(189, 195, 199), 1),
            BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));
        
        JButton refreshBtn = createStyledButton("Refresh", PRIMARY_COLOR);
        JButton exportBtn = createStyledButton("Export to CSV", SUCCESS_COLOR);
        JButton clearDataBtn = createStyledButton("Clear All Data", DANGER_COLOR);
        
        refreshBtn.addActionListener(e -> loadStudentData());
        exportBtn.addActionListener(e -> exportToCSV());
        clearDataBtn.addActionListener(e -> clearAllData());
        
        buttonPanel.add(refreshBtn);
        buttonPanel.add(exportBtn);
        buttonPanel.add(clearDataBtn);
        
        return buttonPanel;
    }
    
    private JButton createStyledButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(150, 38));
        
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
    
    private void styleTable() {
        studentTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        studentTable.setRowHeight(40);
        studentTable.setShowGrid(false);
        studentTable.setIntercellSpacing(new Dimension(0, 0));
        studentTable.setSelectionBackground(new Color(52, 152, 219, 50));
        studentTable.setSelectionForeground(TEXT_PRIMARY);
        studentTable.setBackground(CARD_COLOR);
        
        JTableHeader header = studentTable.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 13));
        header.setBackground(TABLE_HEADER_COLOR);
        header.setForeground(Color.WHITE);
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 45));
        header.setBorder(BorderFactory.createEmptyBorder());
        
        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer();
        headerRenderer.setBackground(TABLE_HEADER_COLOR);
        headerRenderer.setForeground(Color.WHITE);
        headerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        headerRenderer.setOpaque(true);
        
        for (int i = 0; i < studentTable.getColumnCount(); i++) {
            studentTable.getColumnModel().getColumn(i).setHeaderRenderer(headerRenderer);
        }
        
        studentTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        studentTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        studentTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        studentTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        studentTable.getColumnModel().getColumn(4).setPreferredWidth(180);
        
        studentTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(CENTER);
                
                if (value != null && value.toString().equals("Present")) {
                    setForeground(SUCCESS_COLOR);
                    setFont(getFont().deriveFont(Font.BOLD));
                } else {
                    setForeground(TEXT_SECONDARY);
                    setFont(getFont().deriveFont(Font.PLAIN));
                }
                
                if (!isSelected) {
                    setBackground(row % 2 == 0 ? CARD_COLOR : new Color(248, 249, 250));
                }
                
                return c;
            }
        });
        
        DefaultTableCellRenderer alternatingRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {

                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (!isSelected) {
                    setBackground(row % 2 == 0 ? CARD_COLOR : new Color(248, 249, 250));
                }

                setForeground(TEXT_PRIMARY);
                setHorizontalAlignment(CENTER);

                return c;
            }
        };

        for (int i = 0; i < studentTable.getColumnCount(); i++) {
            if (i != 3) {
                studentTable.getColumnModel().getColumn(i).setCellRenderer(alternatingRenderer);
            }
        }
    }
    
    private void showProgressDialog(String title, String message) {
        SwingUtilities.invokeLater(() -> {
            if (progressDialog != null && progressDialog.isVisible()) {
                return; 
            }
            
            progressDialog = new JDialog(this, title, false);
            progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            progressDialog.setSize(400, 150);
            progressDialog.setLocationRelativeTo(this);
            progressDialog.setResizable(false);
            
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBackground(CARD_COLOR);
            panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            
            progressLabel = new JLabel(message);
            progressLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            progressLabel.setForeground(TEXT_PRIMARY);
            progressLabel.setHorizontalAlignment(SwingConstants.CENTER);
            
            progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            progressBar.setPreferredSize(new Dimension(350, 30));
            progressBar.setForeground(PRIMARY_COLOR);
            
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
                    student.lastScan
                });
            }
            System.out.println("Loaded " + students.size() + " students from storage.");
            updateStatus("Loaded " + students.size() + " students from storage.");
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            showStyledDialog("Error loading data: " + e.getMessage(), "Load Error", JOptionPane.ERROR_MESSAGE);
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
            File file = new File(DATA_FILE);
            if (file.exists()) file.delete();

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
    
    private void updateConnectionStatus(String message, boolean connected) {
        SwingUtilities.invokeLater(() -> {
            connectionLabel.setText("* " + message);
            connectionLabel.setForeground(connected ? SUCCESS_COLOR : DANGER_COLOR);
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
        
        hideProgressDialog();
        
        showStyledDialog(
            "Attendance Marked!\n\nName: " + studentName + 
            "\nStudent ID: " + studentID + 
            "\nTime: " + currentTime,
            "Attendance Success", 
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void enrollNewStudent(int fingerprintID) {
        hideProgressDialog();

        String studentName = JOptionPane.showInputDialog(
            this,
            "Enter Student Name for Fingerprint ID: " + fingerprintID,
            "New Student Enrollment",
            JOptionPane.PLAIN_MESSAGE
        );

        if (studentName == null || studentName.trim().isEmpty()) {
            studentName = "Student " + fingerprintID;
        }

        // Auto-generate student ID based on fingerprint ID
        String studentID = "STU" + String.format("%04d", fingerprintID);

        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        tableModel.addRow(new Object[]{studentID, studentName, fingerprintID, "Present", currentTime});
        saveStudentData();

        updateStatus("New student enrolled: " + studentName);
        showStyledDialog(
            "Student Enrolled Successfully!\n\nName: " + studentName +
            "\nStudent ID: " + studentID +
            "\nFingerprint ID: " + fingerprintID,
            "Enrollment Success",
            JOptionPane.INFORMATION_MESSAGE
        );
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
            showProgressDialog("Processing Fingerprint", "Capturing fingerprint image...");
        } 
        else if (processingFingerprint) {
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
            } else if (line.contains("Found ID")) {
                updateProgressDialog("Fingerprint recognized! Marking attendance...");
            }
        }
        
        if (line.startsWith("NewID:")) {
            if (dialogOpen) {
                System.out.println("Dialog already open, ignoring...");
                hideProgressDialog();
                return;
            }
            
            try {
                String[] parts = line.split(":");
                if (parts.length < 2) {
                    System.out.println("Invalid NewID format: " + line);
                    hideProgressDialog();
                    return;
                }
                
                int fingerprintID = Integer.parseInt(parts[1].trim());
                System.out.println("Processing fingerprint ID: " + fingerprintID);
                
                SwingUtilities.invokeLater(() -> {
                    if (dialogOpen) {
                        hideProgressDialog();
                        return;
                    }
                    
                    dialogOpen = true;
                    try {
                        int existingRow = findStudentByFingerprintID(fingerprintID);
                        
                        if (existingRow != -1) {
                            markAttendance(existingRow, fingerprintID);
                        } else {
                            enrollNewStudent(fingerprintID);
                        }
                    } finally {
                        dialogOpen = false;
                    }
                });
            } catch (NumberFormatException e) {
                System.err.println("Error parsing fingerprint ID from: " + line);
                e.printStackTrace();
                updateStatus("Error processing fingerprint data");
                hideProgressDialog();
            }
        } else if (line.contains("Found fingerprint sensor")) {
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