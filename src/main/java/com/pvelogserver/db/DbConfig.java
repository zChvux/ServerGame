package com.pvelogserver.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Doc cau hinh, uu tien theo thu tu:
 *   1. File "application.properties" nam CUNG THU MUC dang chay jar (vi du
 *      /opt/pve-log-server/application.properties tren VPS) - de dang doi
 *      cau hinh (vd mat khau MySQL that) MA KHONG CAN build lai jar.
 *   2. Neu khong co file ben ngoai, moi fallback ve ban dong goi san BEN
 *      TRONG jar (src/main/resources/application.properties) - chi dung khi
 *      chay local/dev nhanh.
 */
public class DbConfig {
    private static final Properties props = new Properties();

    static {
        Path externalPath = Path.of("application.properties");

        if (Files.exists(externalPath)) {
            try (InputStream in = Files.newInputStream(externalPath)) {
                props.load(in);
                System.out.println("Da doc cau hinh tu file ben ngoai: " + externalPath.toAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException("Loi doc application.properties ben ngoai", e);
            }
        } else {
            try (InputStream in = DbConfig.class.getClassLoader()
                    .getResourceAsStream("application.properties")) {
                if (in == null) {
                    throw new RuntimeException(
                        "Khong tim thay application.properties (ca ben ngoai lan trong jar)");
                }
                props.load(in);
                System.out.println("Khong thay file ben ngoai, dung ban dong goi san trong jar.");
            } catch (IOException e) {
                throw new RuntimeException("Loi doc application.properties trong jar", e);
            }
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

    // ---- Job tu nhap lai file CSV chua vao DB (tat ca TUY CHON, co mac dinh) ----

    public static boolean isRetryJobEnabled() {
        return Boolean.parseBoolean(props.getProperty("import.retry.enabled", "true"));
    }

    /** Doi bao nhieu giay sau khi server khoi dong thi chay lan dau (cho MySQL kip len). */
    public static int getRetryInitialDelaySeconds() {
        return Integer.parseInt(props.getProperty("import.retry.initialDelaySeconds", "30"));
    }

    /** Chu ky lap lai (giay). Mac dinh 600 = 10 phut. */
    public static int getRetryIntervalSeconds() {
        return Integer.parseInt(props.getProperty("import.retry.intervalSeconds", "600"));
    }

    /** Bo qua file moi hon so giay nay (tranh dung do voi ClientHandler dang import file do). */
    public static int getRetryMinAgeSeconds() {
        return Integer.parseInt(props.getProperty("import.retry.minAgeSeconds", "60"));
    }
}