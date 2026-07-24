// // package com.oracle.banking;

// // import java.sql.Connection;

// // import com.oracle.banking.util.DatabaseConnection;

// // public class Main {

// //     public static void main(String[] args) {

// //         Connection conn = DatabaseConnection.getConnection();

// //         if (conn != null) {
// //             System.out.println("Database Connected Successfully!");
// //         } else {
// //             System.out.println("Database Connection Failed!");
// //         }

// //     }
// // }



// package com.oracle.banking;

// import java.sql.Connection;
// import java.sql.PreparedStatement;
// import java.sql.ResultSet;

// import com.oracle.banking.util.DatabaseConnection;

// public class Main {

//     public static void main(String[] args) {

//         try {

//             Connection conn = DatabaseConnection.getConnection();

//             String sql = "SELECT ROLE_NAME FROM ROLES";

//             PreparedStatement ps = conn.prepareStatement(sql);

//             ResultSet rs = ps.executeQuery();

//             System.out.println("===== ROLES =====");

//             while (rs.next()) {

//                 System.out.println(rs.getString("ROLE_NAME"));

//             }

//             rs.close();
//             ps.close();
//             conn.close();

//         } catch (Exception e) {

//             e.printStackTrace();

//         }

//     }

// }



package com.oracle.banking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.oracle.banking.util.DatabaseConnection;

public class Main {

    public static void main(String[] args) {

        try {

            Connection conn = DatabaseConnection.getConnection();

            String sql = """
                    SELECT USERNAME,
                           EMAIL,
                           PHONE,
                           STATUS,
                           KYC_STATUS
                    FROM APP_USER
                    """;

            PreparedStatement ps = conn.prepareStatement(sql);

            ResultSet rs = ps.executeQuery();

            System.out.println("\n========= APP USERS =========\n");

            while (rs.next()) {

                System.out.println("Username : " + rs.getString("USERNAME"));
                System.out.println("Email    : " + rs.getString("EMAIL"));
                System.out.println("Phone    : " + rs.getString("PHONE"));
                System.out.println("Status   : " + rs.getString("STATUS"));
                System.out.println("KYC      : " + rs.getString("KYC_STATUS"));

                System.out.println("-----------------------------------------");
            }

            rs.close();
            ps.close();
            conn.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}