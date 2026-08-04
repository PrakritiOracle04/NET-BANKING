package com.oracle.banking.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final String URL = requiredEnvironmentVariable("AUTH_DB_URL");
    private static final String USERNAME = requiredEnvironmentVariable("AUTH_DB_USERNAME");
    private static final String PASSWORD = requiredEnvironmentVariable("AUTH_DB_PASSWORD");

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

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required environment variable is missing: " + name);
        }
        return value;
    }

}
