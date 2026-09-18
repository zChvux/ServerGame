package com.pvelogserver;

import com.pvelogserver.db.DbConfig;
import com.pvelogserver.socket.SocketServer;

public class Main {
    public static void main(String[] args) {
        int port = DbConfig.getPort();
        int maxClients = DbConfig.getMaxConcurrentClients();

        System.out.println("Khoi dong PVE Log Server tren port " + port
                + ", toi da " + maxClients + " ket noi dong thoi...");

        SocketServer server = new SocketServer(port, maxClients);
        server.start();
    }
}
