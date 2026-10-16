package com.pvelogserver;

import com.pvelogserver.db.DbConfig;
import com.pvelogserver.db.PendingImportJob;
import com.pvelogserver.socket.SocketServer;
import com.pvelogserver.storage.CsvLogWriter;

public class Main {
    public static void main(String[] args) {
        int port = DbConfig.getPort();
        int maxClients = DbConfig.getMaxConcurrentClients();

        System.out.println("Khoi dong PVE Log Server tren port " + port
                + ", toi da " + maxClients + " ket noi dong thoi...");

        // Job nen: tu nhap lai vao MySQL cac file CSV da luu nhung chua vao DB
        // (vi du luc truoc MySQL bi loi -> server tra OK_CSV_ONLY).
        if (DbConfig.isRetryJobEnabled()) {
            PendingImportJob job = new PendingImportJob(CsvLogWriter.LOG_DIR, DbConfig.getRetryMinAgeSeconds());
            job.start(DbConfig.getRetryInitialDelaySeconds(), DbConfig.getRetryIntervalSeconds());
        }

        SocketServer server = new SocketServer(port, maxClients);
        server.start(); // ham nay chan (chay mai) cho toi khi server dung
    }
}