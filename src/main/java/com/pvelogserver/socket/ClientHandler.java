package com.pvelogserver.socket;

import com.pvelogserver.db.DbImporter;
import com.pvelogserver.storage.CsvLogWriter;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Xu ly 1 ket noi client: client gui toan bo log CSV cua 1 tran (1 lan, luc ket
 * thuc tran), server luu ra file roi import vao MySQL.
 *
 * Giao thuc (length-prefixed):
 *   [4 byte int, big-endian - do dai match_id tinh bang byte]
 *   [match_id - vi du ten file goc "2026-08-06_15-46-15", encode UTF-8]
 *   [4 byte int, big-endian - do dai payload CSV tinh bang byte]
 *   [payload - noi dung CSV, encode UTF-8]
 *
 * Ly do can them match_id rieng: file CSV goc Core xuat ra CHI co du lieu
 * frame/vi tri/event, khong tu chua ma tran (Core hien tai la single-match).
 * Client can tu sinh 1 id duy nhat cho moi tran (vi du dung timestamp luc
 * bat dau tran) va gui kem, thay vi server phai doan/tu sinh.
 *
 * Sau khi gui xong, client co the doc 1 dong phan hoi roi dong ket noi:
 *   "OK"            - da luu CSV va import DB thanh cong
 *   "OK_DUPLICATE"  - tran nay (match_id) DA duoc nhan truoc do, khong import lai
 *   "OK_CSV_ONLY"   - da luu CSV an toan nhung import DB loi
 *   "ERROR ..."     - du lieu gui len khong hop le
 * Quy uoc cho client: moi phan hoi BAT DAU bang "OK" deu coi la server da nhan
 * duoc tran nay -> co the danh dau file la "da gui", khong can gui lai.
 */
public class ClientHandler implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

    // Gioi han do dai match_id va kich thuoc 1 tran gui len,
    // tranh client loi/gian lan gui du lieu qua lon
    private static final int MAX_MATCH_ID_BYTES = 256;
    private static final int MAX_PAYLOAD_BYTES = 10 * 1024 * 1024; // 10 MB

    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        String remote = socket.getRemoteSocketAddress().toString();

        try (Socket s = socket;
             DataInputStream in = new DataInputStream(s.getInputStream());
             OutputStream out = s.getOutputStream()) {

            String matchId = readMatchId(in, remote, out);
            if (matchId == null) {
                return; // da gui ERROR va tra ve trong readMatchId
            }

            int payloadLength = in.readInt();
            if (payloadLength <= 0 || payloadLength > MAX_PAYLOAD_BYTES) {
                LOGGER.warning("Payload khong hop le tu " + remote + ": " + payloadLength + " bytes");
                sendResponse(out, "ERROR invalid_payload_length");
                return;
            }

            byte[] payload = new byte[payloadLength];
            in.readFully(payload);
            String csvContent = new String(payload, StandardCharsets.UTF_8);

            // Chong nhap trung: neu tran nay da co trong DB (vd client retry gui lai
            // sau khi lan truoc da thanh cong nhung mat ket noi luc cho phan hoi)
            // thi khong luu them file CSV, khong import lai, bao cho client biet da nhan roi.
            boolean alreadyExists = false;
            try {
                alreadyExists = DbImporter.matchExists(matchId);
            } catch (Exception checkEx) {
                // Khong kiem tra duoc (DB loi) -> khong chan, cu luu CSV de khong mat du lieu
                LOGGER.log(Level.WARNING, "Khong kiem tra duoc tran da ton tai (DB loi?), tiep tuc luu CSV", checkEx);
            }
            if (alreadyExists) {
                LOGGER.info("Tran '" + matchId + "' da ton tai trong DB (gui trung tu " + remote + "), bo qua");
                sendResponse(out, "OK_DUPLICATE");
                return;
            }

            String savedFilePath = CsvLogWriter.saveMatchCsv(matchId, csvContent);
            LOGGER.info("Da luu log tran '" + matchId + "' tu " + remote + " -> " + savedFilePath);

            try {
                DbImporter.importCsvFile(matchId, savedFilePath);
                sendResponse(out, "OK");
            } catch (DbImporter.DuplicateMatchException dupEx) {
                // 2 lan gui den gan nhu cung luc, lan kia da import xong truoc.
                // (File CSV vua luu o tren la ban trung - hiem khi xay ra, co the xoa tay)
                LOGGER.info("Tran '" + matchId + "' vua duoc import boi 1 ket noi khac, bo qua lan nay");
                sendResponse(out, "OK_DUPLICATE");
            } catch (Exception dbEx) {
                // CSV da an toan tren dia - loi DB khong lam mat du lieu goc,
                // co the viet them 1 job quet lai cac file loi de import lai sau
                LOGGER.log(Level.WARNING, "Import DB that bai, file CSV van con: " + savedFilePath, dbEx);
                sendResponse(out, "OK_CSV_ONLY");
            }

        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Loi xu ly ket noi tu " + remote, e);
        }
    }

    private String readMatchId(DataInputStream in, String remote, OutputStream out) throws IOException {
        int matchIdLength = in.readInt();
        if (matchIdLength <= 0 || matchIdLength > MAX_MATCH_ID_BYTES) {
            LOGGER.warning("match_id khong hop le tu " + remote + ": " + matchIdLength + " bytes");
            sendResponse(out, "ERROR invalid_match_id_length");
            return null;
        }
        byte[] matchIdBytes = new byte[matchIdLength];
        in.readFully(matchIdBytes);
        return new String(matchIdBytes, StandardCharsets.UTF_8);
    }

    private void sendResponse(OutputStream out, String message) throws IOException {
        out.write((message + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}