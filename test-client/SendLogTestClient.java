import java.io.DataOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client test don gian: gui 1 file CSV log co san qua socket theo dung giao
 * thuc cua ClientHandler (khong can dependency ngoai, chi dung JDK thuan).
 *
 * Cach chay:
 *   javac SendLogTestClient.java
 *   java SendLogTestClient <host> <port> <duong_dan_file_csv> [match_id]
 *
 * Vi du:
 *   java SendLogTestClient localhost 9000 /path/to/2026-08-06_15-46-15.csv
 *   (neu khong truyen match_id, se tu lay ten file lam match_id)
 */
public class SendLogTestClient {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Dung: java SendLogTestClient <host> <port> <file_csv> [match_id]");
            return;
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        Path csvPath = Path.of(args[2]);

        String matchId = args.length >= 4
                ? args[3]
                : csvPath.getFileName().toString().replace(".csv", "");

        byte[] csvBytes = Files.readAllBytes(csvPath);
        byte[] matchIdBytes = matchId.getBytes(StandardCharsets.UTF_8);

        System.out.println("Ket noi toi " + host + ":" + port + " ...");
        try (Socket socket = new Socket(host, port);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            // 1. Gui match_id (4 byte do dai + noi dung)
            out.writeInt(matchIdBytes.length);
            out.write(matchIdBytes);

            // 2. Gui payload CSV (4 byte do dai + noi dung)
            out.writeInt(csvBytes.length);
            out.write(csvBytes);
            out.flush();

            System.out.println("Da gui match_id='" + matchId + "', " + csvBytes.length + " byte CSV.");
            System.out.println("Cho phan hoi tu server...");

            String response = in.readLine();
            System.out.println("Server tra ve: " + response);
        }
    }
}
