// for terminal implementation - static



import java.io.*;
import java.sql.*;

public class SOCProject {

    public static void main(String[] args) {
        // Full paths for files in main SOC folder
        String csvFile = "C:\\Desktop\\SOC\\fake_soc_logs.csv";
        String dbFile = "C:\\Desktop\\SOC\\soc.db";

        try {
            // Load SQL JDBC driver
            Class.forName("org.sqlite.JDBC");

            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);

            String createTable = "CREATE TABLE IF NOT EXISTS incidents (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "type TEXT," +
                    "sourceIP TEXT," +
                    "status TEXT," +
                    "timestamp TEXT," +
                    "severity TEXT)";
            conn.createStatement().execute(createTable);

            // Read CSV and insert into DB
            BufferedReader br = new BufferedReader(new FileReader(csvFile));
            br.readLine(); 
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 3) continue;
                String type = parts[0];
                String ip = parts[1];
                String timestamp = parts[2];

                String severity = getSeverity(type);

                String sql = "INSERT INTO incidents(type, sourceIP, status, timestamp, severity) VALUES(?,?,?,?,?)";
                PreparedStatement pstmt = conn.prepareStatement(sql);
                pstmt.setString(1, type);
                pstmt.setString(2, ip);
                pstmt.setString(3, "New");
                pstmt.setString(4, timestamp);
                pstmt.setString(5, severity);
                pstmt.executeUpdate();
            }
            br.close();
            System.out.println("CSV Imported Successfully!");

            // Generate alerts
            generateAlerts(conn);

            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getSeverity(String type) {
        switch (type) {
            case "Failed Login": return "Medium";
            case "Unauthorized Access":
            case "Suspicious File Access": return "High";
            default: return "Low";
        }
    }

    private static void generateAlerts(Connection conn) throws SQLException {
        // Multiple failed logins
        String sql1 = "SELECT sourceIP, COUNT(*) AS cnt FROM incidents WHERE type='Failed Login' GROUP BY sourceIP";
        ResultSet rs1 = conn.createStatement().executeQuery(sql1);
        while (rs1.next()) {
            String ip = rs1.getString("sourceIP");
            int count = rs1.getInt("cnt");
            if (count >= 5) {
                System.out.println("ALERT: Multiple failed logins from IP " + ip + " (" + count + " times)");
            }
        }


        String sql2 = "SELECT type, sourceIP, timestamp FROM incidents WHERE severity='High'";
        ResultSet rs2 = conn.createStatement().executeQuery(sql2);
        while (rs2.next()) {
            System.out.println("ALERT: " + rs2.getString("type") + " detected from IP " +
                    rs2.getString("sourceIP") + " at " + rs2.getString("timestamp"));
        }

        System.out.println("All alerts generated!");
    }
}


