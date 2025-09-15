import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.sql.*;

public class SOCGUI {

    private static JTable table;
    private static DefaultTableModel model;
    private static Connection conn;

    public static void main(String[] args) {
        JFrame frame = new JFrame("SOC Dashboard");
        frame.setSize(1000, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        String[] columns = {"ID", "Type", "Source IP", "Status", "Timestamp", "Severity"};
        model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; 
            }
        };

        table = new JTable(model);

        // Severity-based coloring
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (isSelected) {
                    c.setBackground(table.getSelectionBackground());
                    c.setForeground(table.getSelectionForeground());
                } else {
                    String severity = (String) table.getValueAt(row, 5);
                    if ("High".equals(severity)) {
                        c.setBackground(Color.RED);
                        c.setForeground(Color.WHITE);
                    } else if ("Medium".equals(severity)) {
                        c.setBackground(Color.ORANGE);
                        c.setForeground(Color.BLACK);
                    } else {
                        c.setBackground(Color.GREEN);
                        c.setForeground(Color.BLACK);
                    }
                }
                return c;
            }
        });

        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        JScrollPane scrollPane = new JScrollPane(table);

        //  "Load CSV" button
        JPanel topPanel = new JPanel();
        JButton loadCsvBtn = new JButton("Load CSV");
        topPanel.add(loadCsvBtn);
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);

        // SQLite DB connection
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:../soc.db");
            Statement stmt = conn.createStatement();
            stmt.execute("CREATE TABLE IF NOT EXISTS incidents (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "type TEXT," +
                    "sourceIP TEXT," +
                    "status TEXT," +
                    "timestamp TEXT," +
                    "severity TEXT)");
            stmt.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Load CSV action
        loadCsvBtn.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int option = fileChooser.showOpenDialog(frame);
            if (option == JFileChooser.APPROVE_OPTION) {
                File csvFile = fileChooser.getSelectedFile();
                importCsv(csvFile);
                loadTableData();
            }
        });

        //  to show details
        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                int row = table.rowAtPoint(evt.getPoint());
                if (row >= 0) {
                    showIncidentDetails(row);
                }
            }
        });

        frame.setVisible(true);
    }

    private static void importCsv(File csvFile) {
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            br.readLine(); 
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 3) continue;

                String type = parts[0];
                String ip = parts[1];
                String timestamp = parts[2];
                String severity = getSeverity(type);

                PreparedStatement pstmt = conn.prepareStatement(
                        "INSERT INTO incidents(type, sourceIP, status, timestamp, severity) VALUES(?,?,?,?,?)");
                pstmt.setString(1, type);
                pstmt.setString(2, ip);
                pstmt.setString(3, "New");
                pstmt.setString(4, timestamp);
                pstmt.setString(5, severity);
                pstmt.executeUpdate();
                pstmt.close();
            }
            JOptionPane.showMessageDialog(null, "CSV Imported Successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getSeverity(String type) {
        if (type == null || type.isEmpty()) return "Low";

        type = type.toLowerCase();

        // high severity keywords
        String[] highKeywords = {"unauthorized", "suspicious", "malware", "ransomware", "breach", "exploit"};
        for (String keyword : highKeywords) {
            if (type.contains(keyword)) return "High";
        }

        // medium severity keywords
        String[] mediumKeywords = {"login failed", "failed login", "access denied", "password"};
        for (String keyword : mediumKeywords) {
            if (type.contains(keyword)) return "Medium";
        }

        return "Low";
    }

    private static void loadTableData() {
        try {
            model.setRowCount(0); // clear existing
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM incidents");

            while (rs.next()) {
                Object[] row = {
                        rs.getInt("id"),
                        rs.getString("type"),
                        rs.getString("sourceIP"),
                        rs.getString("status"),
                        rs.getString("timestamp"),
                        rs.getString("severity")
                };
                model.addRow(row);
            }

            rs.close();
            stmt.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void showIncidentDetails(int row) {
        int id = (int) table.getValueAt(row, 0);
        String type = (String) table.getValueAt(row, 1);
        String ip = (String) table.getValueAt(row, 2);
        String status = (String) table.getValueAt(row, 3);
        String timestamp = (String) table.getValueAt(row, 4);
        String severity = (String) table.getValueAt(row, 5);

        String message = "ID: " + id +
                         "\nType: " + type +
                         "\nSource IP: " + ip +
                         "\nStatus: " + status +
                         "\nTimestamp: " + timestamp +
                         "\nSeverity: " + severity;

        int option = JOptionPane.showOptionDialog(null, message, "Incident Details",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                new String[]{"Close", "Mark as Resolved"}, "Close");

        if (option == 1) {
            markIncidentResolved(id);
            table.setValueAt("Resolved", row, 3);
        }
    }

    private static void markIncidentResolved(int id) {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "UPDATE incidents SET status='Resolved' WHERE id=?")) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
