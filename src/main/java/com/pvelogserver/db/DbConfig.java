package com.pvelogserver.db;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Doc cau hinh tu file application.properties (nam trong src/main/resources).
 * Dung 1 lan khi class duoc load, dung chung cho toan bo app.
 */
public class DbConfig {
    private static final Properties props = new Properties();

    static {
        try (InputStream in = DbConfig.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in == null) {
                throw new RuntimeException("Khong tim thay application.properties trong classpath");
            }
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Loi doc application.properties", e);
        }
    }

    public static String getUrl() {
        return props.getProperty("db.url");
    }

    public static String getUser() {
        return props.getProperty("db.user");
    }

    public static String getPassword() {
        return props.getProperty("db.password");
    }

    public static int getPort() {
        return Integer.parseInt(props.getProperty("server.port", "9000"));
    }

    public static int getMaxConcurrentClients() {
        return Integer.parseInt(props.getProperty("server.maxConcurrentClients", "20"));
    }
}
