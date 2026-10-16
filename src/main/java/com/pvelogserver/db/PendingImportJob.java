package com.pvelogserver.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Job nen: quet thu muc log, nhap vao MySQL cac file CSV da luu nhung chua co trong DB.
 *
 * Tinh huong can job nay: ClientHandler luu CSV an toan roi moi import DB. Neu luc do
 * MySQL loi (treo, sai mat khau, VPS vua khoi dong lai...) server tra "OK_CSV_ONLY", client
 * coi la da gui xong va KHONG gui lai -> file nam ket tren dia, khong vao database.
 *
 * Cach hoat dong (khong doi ten, khong di chuyen, khong xoa file nao):
 *   - Moi file ten dang match_<match_id>_<8 ky tu>.csv -> lay lai match_id tu ten file.
 *   - Neu bang matches da co match_id do HOAC da co dung duong dan file do -> bo qua.
 *   - Nguoc lai -> nhap bang DbImporter.importCsvFile (1 transaction, co chong trung).
 *
 * An toan khi chay song song voi ClientHandler: file moi hon minAge giay bi bo qua
 * (ClientHandler co the dang import chinh file do); neu van dung do thi khoa chinh (PRIMARY KEY)
 * cua bang matches dam bao chi 1 ben nhap duoc, ben kia nhan DuplicateMatchException.
 *
 * Luu y: match_id lay lai tu TEN FILE la ban da duoc "lam sach" (ky tu ngoai a-zA-Z0-9_- thanh '_').
 * Client chuan (LogUploader.cs) da dung san cac ky tu nay nen khop 100% voi match_id goc.
 */
public class PendingImportJob {
    private static final Logger LOGGER = Logger.getLogger(PendingImportJob.class.getName());

    // match_<match_id>_<8 ky tu hex>.csv  (xem CsvLogWriter)
    private static final Pattern FILE_PATTERN = Pattern.compile("^match_(.+)_[0-9a-f]{8}\\.csv$");

    private final Path dir;
    private final long minAgeMillis;

    // File da bao loi roi: khong in lai stack trace moi 10 phut (tranh day log). Reset khi khoi dong lai server.
    private final Set<String> alreadyReportedBad = new HashSet<>();

    public PendingImportJob(String logDir, long minAgeSeconds) {
        this.dir = Paths.get(logDir);
        this.minAgeMillis = minAgeSeconds * 1000L;
    }

    /** Bat dau chay nen: lan dau sau initialDelaySeconds, sau do lap lai moi periodSeconds. */
    public void start(long initialDelaySeconds, long periodSeconds) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pending-import-job");
            t.setDaemon(true); // khong giu cho JVM song khi server dung
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::runSafely, initialDelaySeconds, periodSeconds, TimeUnit.SECONDS);
        LOGGER.info("Job nhap lai file CSV chua vao DB: chay sau " + initialDelaySeconds
                + "s, lap lai moi " + periodSeconds + "s (bo qua file moi hon " + (minAgeMillis / 1000) + "s)");
    }

    // Bat moi loi: scheduleWithFixedDelay se NGUNG VINH VIEN neu tac vu nem exception.
    private void runSafely() {
        try {
            runOnce();
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Job nhap lai gap loi bat ngo (se thu lai o chu ky sau)", t);
        }
    }

    /** Quet 1 luot. Tra ve so tran da nhap them duoc vao DB. (public de co the test truc tiep) */
    public int runOnce() throws IOException {
        if (!Files.isDirectory(dir)) {
            return 0;
        }

        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> FILE_PATTERN.matcher(p.getFileName().toString()).matches())
                    .sorted()
                    .collect(Collectors.toList());
        }

        long now = System.currentTimeMillis();
        int imported = 0;
        int failed = 0;

        for (Path file : files) {
            if (now - Files.getLastModifiedTime(file).toMillis() < minAgeMillis) {
                continue; // file qua moi, ClientHandler co the dang import
            }

            String name = file.getFileName().toString();
            Matcher m = FILE_PATTERN.matcher(name);
            m.matches();
            String matchId = m.group(1);
            // Cung dinh dang chuoi voi CsvLogWriter (dir.resolve(fileName).toString()) de so khop csv_file_path
            String csvPath = dir.resolve(name).toString();

            try {
                if (DbImporter.isAlreadyImported(matchId, csvPath)) {
                    continue;
                }
                DbImporter.importCsvFile(matchId, csvPath);
                imported++;
                LOGGER.info("Job nhap lai: da nhap tran '" + matchId + "' tu file " + csvPath);
            } catch (DbImporter.DuplicateMatchException dup) {
                // Vua co noi khac (ClientHandler) nhap xong -> khong sao
            } catch (Exception e) {
                failed++;
                if (alreadyReportedBad.add(csvPath)) {
                    LOGGER.log(Level.WARNING, "Job nhap lai: loi voi file " + csvPath + " (se thu lai o chu ky sau)", e);
                }
                if (isConnectionProblem(e)) {
                    // MySQL khong ket noi duoc: cac file con lai chac chan cung loi, dung luot nay
                    LOGGER.warning("Job nhap lai: khong ket noi duoc MySQL, dung luot quet nay.");
                    break;
                }
            }
        }

        if (imported > 0 || failed > 0) {
            LOGGER.info("Job nhap lai: xong 1 luot - nhap them " + imported + " tran, loi " + failed + " file.");
        }
        return imported;
    }

    // SQLState bat dau bang "08" = nhom loi KET NOI cua chuan SQL (ca MySQL Connector/J lan MariaDB driver deu theo chuan nay)
    private static boolean isConnectionProblem(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException) {
                String state = ((SQLException) t).getSQLState();
                if (state != null && state.startsWith("08")) {
                    return true;
                }
            }
        }
        return false;
    }
}
