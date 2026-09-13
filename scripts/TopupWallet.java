import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class TopupWallet {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:15432/payment_db";
        String user = "payment_user";
        String password = "payment_pass";

        String sql = "INSERT INTO payment_schema.wallets (user_id, balance, currency, version, created_at, updated_at) " +
                "VALUES (?, ?, 'VND', 1, NOW(), NOW()) " +
                "ON CONFLICT (user_id) DO UPDATE SET " +
                "balance = ?, updated_at = NOW(), version = payment_schema.wallets.version + 1;";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Add 500k to Quoc Bao Huy (ID: 9002)
            pstmt.setLong(1, 9002);
            pstmt.setDouble(2, 500000.0);
            pstmt.setDouble(3, 500000.0);

            int rows = pstmt.executeUpdate();
            System.out.println("Cập nhật thành công! (Affected rows: " + rows + ")");

        } catch (Exception e) {
            System.out.println("Lỗi: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
