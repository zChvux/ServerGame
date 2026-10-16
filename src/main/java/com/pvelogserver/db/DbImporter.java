package com.pvelogserver.db;

import com.opencsv.CSVReader;

import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Doc 1 file CSV log cua 1 tran va insert vao MySQL (bang matches + match_events).
 *
 * Dinh dang CSV dung THEO DUNG FORMAT CORE DANG XUAT RA, dong header dau tien:
 *   Frame,PlayerPosX,PlayerPosY,PlayerEvent,BotPosX,BotPosY,BotEvent
 *
 * Vi du 1 dong du lieu that:
 *   186,1306,500.44,Spike,1850,265,None
 *
 * Luu y: file CSV goc KHONG chua match_id (Core hien tai la single-match, chua
 * co khai niem nhieu tran chay song song). match_id o day duoc client gui kem
 * qua giao thuc socket (xem ClientHandler), khong doc tu ben trong noi dung CSV.
 *
 * CHONG NHAP TRUNG: moi match_id chi duoc import dung 1 lan.
 *   - matchExists(): kiem tra nhanh truoc khi luu file / import (truong hop thuong gap:
 *     client retry gui lai 1 tran da gui thanh cong).
 *   - Bang matches co PRIMARY KEY (match_id): neu 2 lan gui den DUNG CUNG LUC
 *     va deu vuot qua buoc kiem tra tren, lan den sau se bi MySQL tu choi (loi 1062)
 *     -> nem DuplicateMatchException, transaction duoc rollback, khong co dong
 *     nao trong match_events bi chen trung.
 */
public class DbImporter {
    private static final Logger LOGGER = Logger.getLogger(DbImporter.class.getName());

    // Ma loi cua MySQL cho "Duplicate entry ... for key ..."
    private static final int MYSQL_ERR_DUPLICATE_ENTRY = 1062;

    private static final String SELECT_MATCH_SQL =
            "SELECT 1 FROM matches WHERE match_id = ?";

    // Dung cho PendingImportJob: file nay da duoc nhap chua? (theo match_id HOAC theo duong dan file)
    private static final String SELECT_IMPORTED_SQL =
            "SELECT 1 FROM matches WHERE match_id = ? OR csv_file_path = ? LIMIT 1";

    // Khong dung INSERT IGNORE: can MySQL bao loi khi trung de biet ma bo qua import events
    private static final String INSERT_MATCH_SQL =
            "INSERT INTO matches (match_id, csv_file_path) VALUES (?, ?)";

    private static final String INSERT_EVENT_SQL =
            "INSERT INTO match_events " +
            "(match_id, frame, player_pos_x, player_pos_y, player_event, bot_pos_x, bot_pos_y, bot_event) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final int BATCH_SIZE = 500;

    /** Bao hieu tran nay da duoc import truoc do (khong phai loi he thong). */
    public static class DuplicateMatchException extends Exception {
        private static final long serialVersionUID = 1L;
        public DuplicateMatchException(String matchId) {
            super("Tran '" + matchId + "' da ton tai trong DB");
        }
    }

    /** Kiem tra match_id da co trong bang matches chua. Nem exception neu khong ket noi duoc DB. */
    public static boolean matchExists(String matchId) throws Exception {
        try (Connection conn = DriverManager.getConnection(
                DbConfig.getUrl(), DbConfig.getUser(), DbConfig.getPassword());
             PreparedStatement stmt = conn.prepareStatement(SELECT_MATCH_SQL)) {

            stmt.setString(1, matchId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Tran nay (hoac chinh file CSV nay) da duoc nhap vao DB chua?
     * Kiem tra ca duong dan file de khong nhap lai 1 file da nhap duoi match_id khac.
     */
    public static boolean isAlreadyImported(String matchId, String csvFilePath) throws Exception {
        try (Connection conn = DriverManager.getConnection(
                DbConfig.getUrl(), DbConfig.getUser(), DbConfig.getPassword());
             PreparedStatement stmt = conn.prepareStatement(SELECT_IMPORTED_SQL)) {

            stmt.setString(1, matchId);
            stmt.setString(2, csvFilePath);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static void importCsvFile(String matchId, String csvFilePath) throws Exception {
        try (Connection conn = DriverManager.getConnection(
                DbConfig.getUrl(), DbConfig.getUser(), DbConfig.getPassword());
             CSVReader reader = new CSVReader(new FileReader(csvFilePath))) {

            conn.setAutoCommit(false);

            reader.readNext(); // bo qua dong header: Frame,PlayerPosX,PlayerPosY,PlayerEvent,BotPosX,BotPosY,BotEvent

            int rowCount = 0;

            try (PreparedStatement matchStmt = conn.prepareStatement(INSERT_MATCH_SQL);
                 PreparedStatement eventStmt = conn.prepareStatement(INSERT_EVENT_SQL)) {

                matchStmt.setString(1, matchId);
                matchStmt.setString(2, csvFilePath);
                try {
                    matchStmt.executeUpdate();
                } catch (SQLException e) {
                    if (e.getErrorCode() == MYSQL_ERR_DUPLICATE_ENTRY) {
                        throw new DuplicateMatchException(matchId);
                    }
                    throw e;
                }

                String[] row;
                while ((row = reader.readNext()) != null) {
                    if (row.length < 7) {
                        LOGGER.warning("Bo qua dong CSV thieu cot trong file " + csvFilePath);
                        continue;
                    }

                    eventStmt.setString(1, matchId);
                    eventStmt.setInt(2, Integer.parseInt(row[0].trim()));
                    eventStmt.setDouble(3, Double.parseDouble(row[1].trim()));
                    eventStmt.setDouble(4, Double.parseDouble(row[2].trim()));
                    eventStmt.setString(5, row[3].trim());
                    eventStmt.setDouble(6, Double.parseDouble(row[4].trim()));
                    eventStmt.setDouble(7, Double.parseDouble(row[5].trim()));
                    eventStmt.setString(8, row[6].trim());
                    eventStmt.addBatch();

                    rowCount++;
                    if (rowCount % BATCH_SIZE == 0) {
                        eventStmt.executeBatch();
                    }
                }
                eventStmt.executeBatch();
            }

            conn.commit();
            LOGGER.info("Import thanh cong tran " + matchId + " (" + rowCount + " dong) tu " + csvFilePath);

        } catch (DuplicateMatchException e) {
            throw e; // giu nguyen de ClientHandler phan biet voi loi DB that su
        } catch (Exception e) {
            throw new Exception("Loi import CSV vao DB: " + csvFilePath, e);
        }
    }
}