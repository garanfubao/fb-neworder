# Fubao Order Display

Màn hình đơn hàng cho bếp/quầy, chạy trên máy **SUNMI D3 MINI**. Nhận đơn realtime từ
workflow n8n, hiện dạng bảng và **kêu chuông khi có đơn mới — kể cả khi máy đang mở app khác**.

```
n8n (node "Notify Desktop App")
        │  POST /orders  (kèm header X-Api-Key)
        ▼
   Server trên Render  ──────────  giữ đơn + đẩy realtime
        │  WebSocket /ws?key=...
        ▼
   App Android trên D3 MINI  ──►  Bảng đơn + chuông báo (luồng ALARM) + rung + banner
```

Gồm 2 phần:
- `server/` — Node.js đặt trên Render (bạn đã trả phí $25).
- `android/` — app Kotlin cài vào máy D3 MINI.

---

## A. Dựng server trên Render

1. Đẩy cả thư mục này lên 1 repo GitHub (ví dụ `fubao-order-display`).
2. Render → **New → Blueprint** → chọn repo. File `render.yaml` đã cấu hình sẵn:
   - Web Service, thư mục gốc `server/`, chạy `npm install` rồi `npm start`.
   - Health check `/healthz`. Gắn sẵn 1 disk 1GB để đơn không mất khi restart.
3. Khi Render hỏi biến `API_KEY`: nhập **một chuỗi bí mật tự đặt** (ví dụ
   `fubao-7hK2p...`). **Nhớ chuỗi này** — sẽ dùng lại y hệt ở n8n và ở app.
4. Deploy xong bạn sẽ có domain kiểu `https://fubao-order-server.onrender.com`.
   Mở thử domain đó → thấy dòng "Fubao Order Server dang chay" là OK.

> Không muốn dùng Blueprint? Tạo tay 1 Web Service: Root Directory = `server`,
> Build = `npm install`, Start = `npm start`, và thêm biến `API_KEY`.

**Test nhanh server** (thay domain + key của bạn):
```bash
curl -X POST https://fubao-order-server.onrender.com/orders \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: DAN_KEY_CUA_BAN" \
  -d '{"items":"2x Cơm Gà Kem Péo","address":"Sảnh S10.06","phone":"0813014333","paymentMethod":"COD","totalPrice":"120.000đ"}'
```
Trả về `{"ok":true,...}` là chạy.

---

## B. Nối n8n vào server

Trong workflow, mở node **"Notify Desktop App"** (node HTTP Request cuối cùng):

1. **URL**: đổi placeholder thành:
   `https://fubao-order-server.onrender.com/orders`
2. **Thêm header xác thực**: bật **Send Headers** → thêm 1 header:
   - Name: `X-Api-Key`
   - Value: đúng chuỗi `API_KEY` đã đặt ở Render.
3. Body giữ nguyên (đã gửi customerName, phone, address, items, totalPrice, paymentMethod).

### (Tuỳ chọn nhưng nên làm) Thêm cột "Ghi chú"
Workflow hiện chưa tách riêng field `note`, nên app sẽ để trống dòng ghi chú. Muốn có:

- Node **Order Chat Agent** → sửa system message: trong đoạn mô tả format JSON, thêm
  field `"note"` và một câu như:
  *"note = ghi chú đặc biệt của khách (VD: không hành, ít cay, giao lúc 19h), không có thì để null"*.
- Node **Notify Desktop App** → trong `jsonBody`, thêm:
  `note: $('Parse AI Output').item.json.note`
- (Không bắt buộc) Node **Parse AI Output** → thêm `note: null` vào object mặc định.

Server và app đã sẵn sàng nhận `note` nếu có.

### (Mới) Tự nhắn khách "đang giao hàng" khi bấm **Xong**
Khi bếp bấm **Xong** trên máy D3, server sẽ nhờ n8n nhắn Messenger cho đúng khách:
> *"Bếp đang trên đường giao hàng, mình xuống sảnh nhận cơm giúp em nha"*

Cần làm **2 việc**:

**1) Gửi PSID (ID Messenger của khách) kèm theo đơn.** Mở node **"Notify Desktop App"**,
trong `jsonBody` thêm 1 dòng lấy ID người gửi từ Messenger Trigger, ví dụ:
```
psid: $('Messenger Trigger').item.json.entry[0].messaging[0].sender.id
```
(Tên node và đường dẫn tuỳ workflow của bạn — cốt lấy đúng `sender.id` của khách. Không
có PSID thì server sẽ bỏ qua bước nhắn tin, các phần khác vẫn chạy bình thường.)

**2) Tạo webhook n8n để gửi tin.** Thêm 1 flow nhỏ trong n8n:
- **Webhook** (Method `POST`, ví dụ path `fubao-delivery`) → nhận `{ psid, message, orderId, ... }`.
- Nối sang node gửi Messenger (dùng **credential Facebook có sẵn** mà chatbot đang trả lời khách):
  gửi tới người nhận `={{ $json.body.psid }}`, nội dung `={{ $json.body.message }}`.
  (Nếu dùng HTTP Request tới Graph API: `POST https://graph.facebook.com/v21.0/me/messages`
  body `{"recipient":{"id":"{{ $json.body.psid }}"},"messaging_type":"RESPONSE","message":{"text":"{{ $json.body.message }}"}}`.)
- Server gửi kèm header `X-Api-Key` = đúng `API_KEY`; muốn chắc thì cho webhook kiểm tra header này.

**3) Bật ở Render.** Vào Environment của server, thêm biến:
- `N8N_DELIVERY_WEBHOOK_URL` = URL production của webhook vừa tạo.
- (tuỳ chọn) `DELIVERY_MESSAGE` = đổi nội dung tin nhắn mà không cần sửa code.

> Đơn nằm trong 24h kể từ lúc khách nhắn nên tin gửi bình thường (đúng cửa sổ nhắn tin của
> Messenger). Tin chỉ gửi **1 lần** cho máy thực sự bấm Xong đầu tiên.

---

## C. Tạo file APK

App chưa build sẵn (cần môi trường Android). Chọn **1 trong 2 cách**:

### Cách 1 — GitHub Actions (không cần cài gì trên máy) ✅ khuyên dùng
1. Đẩy repo lên GitHub (đã có sẵn `.github/workflows/build-apk.yml`).
2. Vào tab **Actions** → workflow **Build APK** → **Run workflow** (hoặc nó tự chạy khi push).
3. Chạy xong (~3–5 phút), mở lần chạy đó → mục **Artifacts** → tải
   `fubao-order-display-apk` → giải nén ra file `.apk`.

### Cách 2 — Android Studio (trên máy Mac)
1. Cài Android Studio → **Open** thư mục `android/`. Để nó tự tải SDK/Gradle.
2. Menu **Build → Build App Bundle(s)/APK(s) → Build APK(s)**.
3. File nằm ở `android/app/build/outputs/apk/release/app-release.apk`.

> APK được ký bằng debug key nên cài trực tiếp được ngay (không lên chợ ứng dụng).

### Cấu hình server/key cho app
Có 2 chỗ, chọn 1:
- **Trước khi build**: sửa `android/app/src/main/java/com/fubao/orderdisplay/Config.kt`:
  ```kotlin
  const val WS_URL = "wss://fubao-order-server.onrender.com/ws"   // nhớ: wss:// và /ws
  const val API_KEY = "DAN_KEY_CUA_BAN"
  ```
- **Sau khi cài, ngay trong app**: bấm nút **⚙** (góc trên phải) → nhập WS URL + API key →
  *Lưu & kết nối lại*. Cách này đổi được mà không cần build lại.

---

## D. Cài & cấu hình trên máy D3 MINI

1. Chép file `.apk` vào máy (USB / Google Drive / Zalo...) rồi mở để cài.
   - Nếu máy chặn: bật **Cài đặt → Bảo mật → Cho phép cài từ nguồn này**.
2. Mở app **Đơn Fubao**. Lần đầu nó sẽ xin:
   - **Quyền thông báo** → Cho phép (để banner "Đơn mới" hiện lên).
   - **Bỏ tối ưu pin** → chọn **Cho phép / Không tối ưu** (rất quan trọng để chạy ngầm 24/7).
3. Nếu cần, kiểm tra thanh trạng thái trong app hiện **"Đã kết nối ✓"**.
4. Nên set app **tự mở khi bật máy**: nhiều máy SUNMI có mục *Auto-start / Khởi động cùng
   hệ thống* trong Cài đặt — bật cho app này. (App cũng đã tự bật lại service sau khi reboot.)

**Test toàn tuyến**: chạy lại lệnh `curl` ở phần A (hoặc chat 1 đơn thật qua Messenger cho
tới khi `orderComplete=true`) → máy D3 phải **kêu chuông + rung + hiện đơn** ngay, dù đang
mở app khác.

---

## Dừng app cho đỡ tốn pin (đóng quán)
Quán chỉ mở 09:00–22:00, không cần chạy 24/24. Cuối ngày bấm nút **⏻ Dừng** (thanh trên
cùng) → app **ngắt kết nối + tắt hẳn** (không còn giữ màn hình sáng, không còn wakelock) nên
gần như không tốn pin. Sáng mở quán chỉ cần **mở lại app** là tự kết nối nhận đơn tiếp.
(Không cần vào Cài đặt → Buộc dừng nữa.)

> Muốn app **tự tắt 22:00 / tự bật 09:00** không cần bấm tay thì báo tôi làm thêm hẹn giờ.

## Chuông báo hoạt động thế nào
- Khi có đơn mới, app phát chuông **lặp lại theo luồng ALARM** → to, nghe rõ trong bếp và
  kêu **kể cả khi bạn đang ở app POS khác**.
- Chuông **tự tắt sau 60 giây**, hoặc bấm **🔕 Tắt chuông** trong app để tắt ngay.
- Tiếng chuông là file `android/app/src/main/res/raw/order_alarm.mp3` (đã nhúng sẵn trong
  app). Muốn đổi: thay file mp3 đó bằng file khác **cùng tên** `order_alarm.mp3` rồi build lại APK.

## Khách sửa món thì sao?
Khi khách chỉnh đơn, chatbot gửi đơn lần 2. Server tự **thay đơn cũ bằng đơn mới** nếu đơn
cũ **cùng khách** (trùng `psid`; chưa gắn psid thì trùng SĐT) và **chưa bấm Xong** — nên bảng
chỉ còn **1 thẻ đúng** chứ không bị nhân đôi. Đơn đổi sẽ **kêu chuông lại** để bếp biết có
thay đổi. (Nếu đơn cũ đã bấm Xong rồi thì đơn mới coi như đơn mới hoàn toàn.)

> Muốn dedup chính xác nhất, nên gắn `psid` như mục B ở trên (SĐT chỉ là khoá dự phòng).

## Ghi chú kỹ thuật
- Server dùng WebSocket (`ws`), giữ 100 đơn gần nhất. Máy mới kết nối sẽ nhận 50 đơn gần
  nhất để bảng không trống (không kêu chuông cho mấy đơn cũ này).
- App dùng **foreground service** + wake lock nên không bị Android kill khi chuyển app.
  Mất mạng thì tự kết nối lại mỗi 3 giây.
- Bảng hiện: **Món** (nổi bật), 📍 Địa chỉ, 📞 SĐT, 📝 Ghi chú, 💵 Giá/Thanh toán, giờ nhận.
```
