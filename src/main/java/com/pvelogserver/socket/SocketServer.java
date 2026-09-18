package com.pvelogserver.socket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Server socket TCP, moi ket noi client duoc giao cho 1 thread rieng xu ly
 * (thread-per-connection). O quy mo ~20 ket noi dong thoi nhu yeu cau, cach
 * nay du dung, khong can toi framework async phuc tap.
 */
public class SocketServer {
    private static final Logger LOGGER = Logger.getLogger(SocketServer.class.getName());

    private final int port;
    private final ExecutorService clientPool;

    public SocketServer(int port, int maxConcurrentClients) {
        this.port = port;
        // +5 thread du phong ngoai so socket toi da, tranh nghen khi co client
        // dang dong ket noi / doi luot ma thread pool da full dung so luong client hien tai
        this.clientPool = Executors.newFixedThreadPool(maxConcurrentClients + 5);
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            LOGGER.info("Log server dang lang nghe tai port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                clientPool.submit(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Khong mo duoc server socket tai port " + port, e);
        }
    }
}
