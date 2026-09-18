package com.pvelogserver.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Ghi log 1 tran dau ra file CSV rieng, giu lai nhu ban goc/backup
 * truoc khi thu import vao MySQL. Neu MySQL loi, file nay van con nguyen.
 */
public class CsvLogWriter {
    private static final String LOG_DIR = "logs/matches";

    /**
     * @param matchId id tran do client gui len (vd ten file goc Core xuat ra,
     *                dang "2026-08-06_15-46-15"). Duoc lam sach de dung an
     *                toan lam ten file tren dia.
     */
    public static String saveMatchCsv(String matchId, String csvContent) throws IOException {
        Path dir = Paths.get(LOG_DIR);
        Files.createDirectories(dir);

        String safeMatchId = matchId.replaceAll("[^a-zA-Z0-9_-]", "_");
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String fileName = "match_" + safeMatchId + "_" + uniqueSuffix + ".csv";
        Path filePath = dir.resolve(fileName);

        Files.writeString(filePath, csvContent);
        return filePath.toString();
    }
}
