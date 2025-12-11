import javax.swing.*;
import javax.swing.table.DefaultTableModel;
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
	    private volatile boolean dialogOpen = false;
    
    public AttendanceGUI() {
        setTitle("Biometric Attendance System");
        setSize(800, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
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
        studentTable.setRowHeight(25);
        studentTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        studentTable.getColumnModel().getColumn(4).setPreferredWidth(150);
        JScrollPane scrollPane = new JScrollPane(studentTable);
        
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("System Ready - Waiting for fingerprint scan...");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton clearDataBtn = new JButton("Clear All Data");
        JButton refreshBtn = new JButton("Refresh");
        JButton exportBtn = new JButton("Export to CSV");
        
        clearDataBtn.addActionListener(e -> clearAllData());
        refreshBtn.addActionListener(e -> loadStudentData());
        exportBtn.addActionListener(e -> exportToCSV());
        
        buttonPanel.add(refreshBtn);
        buttonPanel.add(exportBtn);
        buttonPanel.add(clearDataBtn);
        
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(statusPanel, BorderLayout.SOUTH);
        mainPanel.add(buttonPanel, BorderLayout.NORTH);
        
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
            JOptionPane.showMessageDialog(this, "Error saving data: " + e.getMessage(), 
                "Save Error", JOptionPane.ERROR_MESSAGE);
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
            JOptionPane.showMessageDialog(this, "Error loading data: " + e.getMessage(), 
                "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void clearAllData() {
        int confirm = JOptionPane.showConfirmDialog(this, 
            "Are you sure you want to delete all student data?", 
            "Confirm Delete", 
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        
        if (confirm == JOptionPane.YES_OPTION) {
            tableModel.setRowCount(0);
            saveStudentData();
            File file = new File(DATA_FILE);
            if (file.exists()) {
                file.delete();
            }
            updateStatus("All data cleared.");
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
                JOptionPane.showMessageDialog(this, "Data exported successfully!", 
                    "Export Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error exporting: " + e.getMessage(), 
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void updateStatus(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
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
        
        JOptionPane.showMessageDialog(this, 
            "Attendance Marked!\n\nName: " + studentName + 
            "\nStudent ID: " + studentID + 
            "\nTime: " + currentTime,
            "Attendance Success", 
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void enrollNewStudent(int fingerprintID) {
        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        JTextField nameField = new JTextField(20);
        JTextField idField = new JTextField(20);
        
        panel.add(new JLabel("Student Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Student ID:"));
        panel.add(idField);
        
        int result = JOptionPane.showConfirmDialog(this, panel, 
            "New Fingerprint Detected - Enroll Student (FP ID: " + fingerprintID + ")", 
            JOptionPane.OK_CANCEL_OPTION, 
            JOptionPane.PLAIN_MESSAGE);
        
        if (result == JOptionPane.OK_OPTION) {
            String studentName = nameField.getText().trim();
            String studentID = idField.getText().trim();
            
            if (studentName.isEmpty()) {
                studentName = "Student " + fingerprintID;
            }
            if (studentID.isEmpty()) {
                studentID = "STU" + String.format("%04d", fingerprintID);
            }
            
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            tableModel.addRow(new Object[]{studentID, studentName, fingerprintID, "Present", currentTime});
            saveStudentData();
            
            updateStatus("New student enrolled: " + studentName);
            JOptionPane.showMessageDialog(this, 
                "Student Enrolled Successfully!\n\nName: " + studentName + 
                "\nStudent ID: " + studentID + 
                "\nFingerprint ID: " + fingerprintID,
                "Enrollment Success", 
                JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void setupSerialPort(String portName) {
        arduinoPort = SerialPort.getCommPort(portName);
        arduinoPort.setBaudRate(9600); 
        arduinoPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);
        
        if (arduinoPort.openPort()) {
            System.out.println("Port " + portName + " opened successfully!");
            updateStatus("Connected to Arduino on " + portName);
        } else {
            System.out.println("Failed to open port " + portName);
            updateStatus("Failed to connect to Arduino on " + portName);
            JOptionPane.showMessageDialog(this, 
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
        if (line.startsWith("NewID:")) {
            if (dialogOpen) {
                System.out.println("Dialog already open, ignoring...");
                return;
            }
            
            try {
                String[] parts = line.split(":");
                if (parts.length < 2) {
                    System.out.println("Invalid NewID format: " + line);
                    return;
                }
                
                int fingerprintID = Integer.parseInt(parts[1].trim());
                System.out.println("Processing fingerprint ID: " + fingerprintID);
                
                SwingUtilities.invokeLater(() -> {
                    if (dialogOpen) return; 
                    
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
            }
        } else if (line.contains("Found fingerprint sensor")) {
            updateStatus("Fingerprint sensor connected and ready");
        } else if (line.contains("Waiting for valid finger")) {
            updateStatus("System ready - Place finger on scanner");
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