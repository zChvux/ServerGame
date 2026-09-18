package com.pvelogserver.db;

import com.opencsv.CSVReader;

import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
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
 * co khai niem nhieu tran chay song song). match_id o day duoc server gan tu
 * ben ngoai (client gui kem qua giao thuc socket - xem ClientHandler), khong
 * doc tu ben trong noi dung CSV.
 */
public class DbImporter {
    private static final Logger LOGGER = Logger.getLogger(DbImporter.class.getName());

    private static final String INSERT_MATCH_SQL =
            "INSERT IGNORE INTO matches (match_id, csv_file_path) VALUES (?, ?)";

    private static final String INSERT_EVENT_SQL =
            "INSERT INTO match_events " +
            "(match_id, frame, player_pos_x, player_pos_y, player_event, bot_pos_x, bot_pos_y, bot_event) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final int BATCH_SIZE = 500;

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
                matchStmt.executeUpdate();

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

        } catch (Exception e) {
            throw new Exception("Loi import CSV vao DB: " + csvFilePath, e);
        }
    }
}
