//package com.nssi.salangikopu.Connection;
//
//import java.sql.Connection;
//import java.sql.DriverManager;
//
//public class DBConnection {
//
//    public static Connection getConnection() {
//        try {
//            Class.forName("com.mysql.jdbc.Driver"); // 5.x driver
//            return DriverManager.getConnection(ENV.DB_URL, ENV.DB_USER, ENV.DB_PASSWORD);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null;
//        }
//    }
//}