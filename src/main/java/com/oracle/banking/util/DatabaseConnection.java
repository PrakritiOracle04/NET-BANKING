package com.oracle.banking.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final String URL =
            "jdbc:oracle:thin:@localhost:1521/FREEPDB1";

    private static final String USERNAME = "BANKING";

    private static final String PASSWORD = "Banking123";

    public static Connection getConnection() {

        try {

            Connection connection = DriverManager.getConnection(
                    URL,
                    USERNAME,
                    PASSWORD
            );

            System.out.println("Connected to Oracle Database!");

            return connection;

        } catch (SQLException e) {

            System.out.println("Connection Failed!");
            e.printStackTrace();

            return null;
        }

    }

}