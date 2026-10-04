import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test tai: mo N ket noi socket DONG THOI (that su song song, dung nhieu
 * thread trong 1 chuong trinh Java, khong phu thuoc he dieu hanh/shell).
 *
 * Cach chay:
 *   javac ConcurrentLoadTest.java
 *   java ConcurrentLoadTest <host> <port> <file_csv> <so_luong_ket_noi>
 *
 * Vi du:
 *   java ConcurrentLoadTest localhost 9000 "D:\...\2026-08-06_15-46-15.csv" 20
 */
public class ConcurrentLoadTest {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("Dung: java ConcurrentLoadTest <host> <port> <file_csv> <so_luong_ket_noi>");
            return;
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        Path csvPath = Path.of(args[2]);
        int numClients = Integer.parseInt(args[3]);

        byte[] csvBytes = Files.readAllBytes(csvPath);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(numClients);

        System.out.println("Bat dau gui " + numClients + " ket noi DONG THOI toi "
                + host + ":" + port + " ...");
        long start = System.currentTimeMillis();

        for (int i = 1; i <= numClients; i++) {
            final int clientIndex = i;
            Thread t = new Thread(() -> {
                String matchId = "loadtest_match_" + clientIndex + "_" + System.nanoTime();
                try {
                    sendOne(host, port, matchId, csvBytes);
                    successCount.incrementAndGet();
                    System.out.println("[Client " + clientIndex + "] OK - match_id=" + matchId);
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("[Client " + clientIndex + "] LOI: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
            t.start();
        }

        latch.await(); // cho tat ca 20 thread gui xong
        long durationMs = System.currentTimeMillis() - start;

        System.out.println("---------------------------------------------");
        System.out.println("Tong so ket noi: " + numClients);
        System.out.println("Thanh cong (OK): " + successCount.get());
        System.out.println("That bai: " + failCount.get());
        System.out.println("Tong thoi gian: " + durationMs + " ms");
    }

    private static void sendOne(String host, int port, String matchId, byte[] csvBytes) throws Exception {
        byte[] matchIdBytes = matchId.getBytes(StandardCharsets.UTF_8);

        try (Socket socket = new Socket(host, port);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            out.writeInt(matchIdBytes.length);
            out.write(matchIdBytes);
            out.writeInt(csvBytes.length);
            out.write(csvBytes);
            out.flush();

            String response = in.readLine();
            if (response == null || !response.startsWith("OK")) {
                throw new RuntimeException("Server tra ve: " + response);
            }
        }
    }
}
