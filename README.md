# PVE Log Server

Server TCP nhận log của 1 trận đấu (gửi 1 lần khi kết thúc trận), lưu ra file
CSV, sau đó import vào MySQL. Chạy nền 24/7, xử lý được ~20 kết nối đồng thời.

## Kiến trúc

```
Client (game) --TCP--> SocketServer --> ClientHandler --> CsvLogWriter (ghi file .csv)
                                                       --> DbImporter (insert vào MySQL)
```

## Giao thức gửi log (client cần tuân theo)

Kết nối TCP tới server, gửi theo đúng thứ tự:

1. **4 byte** (kiểu `int`, big-endian) = độ dài chuỗi `match_id` tính bằng byte.
2. **match_id** = chuỗi định danh trận đấu, encode UTF-8 (khuyến nghị: dùng
   đúng tên file Core đang xuất ra, ví dụ `2026-08-06_15-46-15`).
3. **4 byte** (kiểu `int`, big-endian) = độ dài của phần payload CSV tính bằng byte.
4. **Payload** = toàn bộ nội dung file CSV của trận đấu, encode UTF-8.

> Vì sao cần gửi `match_id` riêng: file CSV Core xuất ra **không tự chứa** mã
> trận (Core hiện tại là single-match, chưa có khái niệm nhiều trận song
> song) — nên client phải tự gán 1 id duy nhất cho mỗi trận và gửi kèm, thay
> vì server đọc từ trong nội dung CSV.

Sau khi gửi xong, có thể đọc 1 dòng phản hồi từ server:
- `OK` — đã lưu CSV và import DB thành công.
- `OK_CSV_ONLY` — đã lưu CSV an toàn, nhưng import DB lỗi (sẽ cần job import lại sau).
- `ERROR ...` — match_id hoặc payload không hợp lệ.

### Định dạng CSV thực tế (đúng theo format Core đang xuất ra)

Dòng đầu là header, các dòng sau theo đúng thứ tự cột:

```
Frame,PlayerPosX,PlayerPosY,PlayerEvent,BotPosX,BotPosY,BotEvent
61,977,265,MoveRight,1850,265,None
186,1306,500.44,Spike,1850,265,None
270,428,265,Serve,1850,265,None
```

Ghi chú:
- Đây là log **PvE (Player vs Bot)** — chỉ 2 thực thể, không có ball/reward
  riêng trong log này.
- `Frame` không nhất thiết tăng liên tục từng dòng (chỉ ghi khi có thay đổi),
  không cần lo việc "thiếu số" khi import.
- `PlayerEvent`/`BotEvent` là `None` khi không có action nào đang diễn ra ở
  frame đó.
- Nếu sau này cần tính `reward` để train AI, đây là bước xử lý **sau khi lấy
  dữ liệu ra khỏi DB** (ví dụ trong script Python phân tích), không phải thứ
  Core ghi sẵn trong log thô này.

## Cài đặt & chạy

### 1. Tạo database

```bash
mysql -u root -p < deploy/schema.sql
```

### 2. Cấu hình

Sửa `src/main/resources/application.properties`:

```properties
server.port=9000
server.maxConcurrentClients=20
db.url=jdbc:mysql://localhost:3306/pve_logs?useSSL=false&serverTimezone=UTC
db.user=root
db.password=your_password_here
```

### 3. Build

```bash
mvn clean package
```

Sẽ tạo ra file `target/pve-log-server-jar-with-dependencies.jar` (đã gộp sẵn
driver MySQL + OpenCSV, chỉ cần 1 file để chạy).

### 4. Chạy thử (foreground)

```bash
java -jar target/pve-log-server-jar-with-dependencies.jar
```

### 5. Chạy 24/7 bằng systemd (Linux VPS)

```bash
sudo mkdir -p /opt/pve-log-server
sudo cp target/pve-log-server-jar-with-dependencies.jar /opt/pve-log-server/pve-log-server.jar
sudo cp deploy/pve-log-server.service /etc/systemd/system/

sudo systemctl daemon-reload
sudo systemctl enable pve-log-server
sudo systemctl start pve-log-server

# Kiểm tra trạng thái / xem log
sudo systemctl status pve-log-server
journalctl -u pve-log-server -f
```

Với cấu hình `Restart=always`, server sẽ tự khởi động lại nếu bị crash — đáp
ứng yêu cầu chạy 24/7.

### 6. Test thử với file log thật (dùng SendLogTestClient)

Có sẵn 1 client test đơn giản trong `test-client/` (không cần dependency
ngoài, chỉ cần JDK) để gửi thử 1 file CSV log thật qua server.

```bash
# Terminal 1: chạy server (đã làm ở bước 4)
java -jar target/pve-log-server-jar-with-dependencies.jar

# Terminal 2: build và chạy client test
cd test-client
javac SendLogTestClient.java
java SendLogTestClient localhost 9000 /duong/dan/toi/2026-08-06_15-46-15.csv
```

Kết quả mong đợi:
- Terminal 1 (server) in ra dòng log kiểu:
  `Da luu log tran 'match_2026-08-06_15-46-15' ... -> logs/matches/match_2026-08-06_15-46-15_xxxxxxxx.csv`
- Terminal 2 (client) in ra: `Server tra ve: OK`
- Kiểm tra file đã lưu: `ls logs/matches/`
- Kiểm tra dữ liệu trong MySQL:
  ```sql
  SELECT * FROM matches;
  SELECT * FROM match_events LIMIT 20;
  SELECT COUNT(*) FROM match_events; -- phải khớp số dòng dữ liệu (không tính header) trong file CSV gốc
  ```

### Test nhiều kết nối đồng thời (để kiểm tra đúng yêu cầu ~20 socket)

Chạy nhiều lần `SendLogTestClient` song song (khác `match_id` để không trùng),
ví dụ bằng vòng lặp bash:

```bash
for i in $(seq 1 20); do
  java SendLogTestClient localhost 9000 /duong/dan/toi/2026-08-06_15-46-15.csv "test_match_$i" &
done
wait
```

Nếu tất cả đều nhận được `OK` và MySQL có đủ 20 `match_id` khác nhau trong
bảng `matches` — server đã đáp ứng đúng yêu cầu xử lý đồng thời.

## Các điểm mở rộng gợi ý sau này

- **Job quét lại file `OK_CSV_ONLY`**: viết thêm 1 scheduled task quét thư mục
  `logs/matches/`, so sánh với bảng `matches` trong DB, import lại các file
  chưa có trong DB (phòng trường hợp DB down tạm thời lúc nhận log).
- **Giới hạn số kết nối đồng thời chặt hơn**: hiện tại dùng
  `Executors.newFixedThreadPool(maxConcurrentClients + 5)` — đã đủ cho ~20
  client, nhưng nếu cần từ chối kết nối khi vượt quá (thay vì xếp hàng chờ),
  có thể thêm `Semaphore` để kiểm soát rõ ràng hơn.
- **TLS**: nếu log chứa thông tin nhạy cảm và gửi qua mạng công cộng, nên bọc
  `SSLServerSocket` thay vì `ServerSocket` thường.
