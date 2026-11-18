TRỢ LÝ BACCARAT (DEMO) - BẢN ANDROID

CÁCH LẤY FILE APK (chọn 1 trong 2)

A) Android Studio (trên máy tính)
   1. Mở Android Studio > File > Open > chọn thư mục BaccaratAssistant này.
   2. Chờ Gradle đồng bộ xong (lần đầu sẽ tự tải thư viện).
   3. Menu Build > Build Bundle(s) / APK(s) > Build APK(s).
   4. File nằm ở app/build/outputs/apk/debug/app-debug.apk -> chép sang điện thoại và cài.

B) Không cần cài gì (GitHub Actions)
   1. Tạo repo mới trên github.com, tải toàn bộ thư mục này lên (giữ nguyên thư mục .github).
   2. Vào tab Actions > "Build APK" > Run workflow.
   3. Khi chạy xong, mở lần chạy đó > mục Artifacts > tải "BaccaratAssistant-apk".

CÁCH DÙNG TRÊN ĐIỆN THOẠI
   1. Cài APK (cho phép "cài từ nguồn không xác định" nếu máy hỏi).
   2. Mở ứng dụng > bấm "Bật trợ lý" > cho phép "Hiển thị trên các ứng dụng khác" > cho phép quay màn hình.
   3. Mở game baccarat, bấm "Bắt đầu xem màn hình" ở trợ lý nổi (kéo avatar để đổi chỗ).
   4. Dừng: bấm "Dừng". Tắt hẳn: bấm "Thoát trợ lý".

BẢN 1.4: SỬA LỖI TRÊN ĐIỆN THOẠI
   - Giao diện gọn hơn (nhỏ hơn khoảng 20%); bấm "Bắt đầu xem màn hình" thì tự thu về thanh gọn gồm avatar và chữ CÁI/CON,
     chạm avatar để mở rộng/thu nhỏ. Vùng của trợ lý khít với phần nhìn thấy, không còn vùng trong suốt chặn cảm ứng game.
   - Sửa lỗi bộ đọc "bám nhầm" nhóm chấm sai (kể cả các chấm tròn của chính giao diện) và không thoát ra được;
     chỉ nhận nhóm chấm có dạng bảng cầu; vị trí lần trước chỉ là ưu tiên nhẹ.
   - Chỉ bắt đầu theo dõi khi bảng cầu có ≥ 8 ván; chỉ đổi bàn khi lệch liên tục 3 lần đọc.
   - Chỉ chấm đúng/sai khi có 1-2 ván mới; bỏ lỡ nhiều ván cùng lúc thì không chấm để khỏi ra kết quả sai.
   - Ván Hòa ở ô kế tiếp chỉ nhận chấm xanh thuần (không còn "ván ma").

BẢN 1.3: PHÂN TÍCH SÂU (trong bảng "📚 Xem các bàn đã học" bấm "🔬 Phân tích sâu")
   - Xác suất thật mỗi ván (Cái 45,86% · Con 44,62% · Hòa 9,52%) và kỳ vọng âm của từng cửa cược.
   - Kiểm định ngẫu nhiên có hiệu chỉnh khi thử nhiều phương án; bảng chấm điểm từng phương án cầu.
   - Biểu đồ hiệu chỉnh: mức "nghiêng" của mô hình thực tế đúng bao nhiêu %.
   - Nhóm đối chứng bằng dữ liệu ngẫu nhiên thật; mô phỏng rủi ro vốn.
   - Thẻ dự đoán không còn ghi "nghiêng 89%" (dễ hiểu nhầm là xác suất) mà ghi xác suất thật mỗi ván.

BẢN 1.2: GIAO DIỆN MỚI + SỬA LỖI VÁN HÒA
   - Giao diện mới: thẻ tối bo tròn, nhân vật bồng bềnh, vòng sáng quay khi tìm bảng cầu, vòng tiến trình khi phân tích,
     nút CÁI (đỏ)/CON (xanh) có vệt sáng, thanh "đồng thuận" (ví dụ 6/9), chuỗi chấm đúng/sai, pháo giấy khi đoán đúng,
     nhân vật lắc khi đoán sai. Kéo avatar/tiêu đề để di chuyển; chạm avatar để thu nhỏ/mở rộng.
   - Sửa lỗi không đọc được ván Hòa: nới dải màu xanh lá (xanh chanh -> xanh ngọc) và kiểm tra thêm các ô trong lưới
     mà bước dò chấm bỏ sót (chấm Hòa dạng vòng rỗng, màu lệch, hoặc ở ô mới nhất cuối bảng).
   - Ván Hòa không tính vào đúng/sai; chấm nhiều màu đặc biệt (hổ/rồng) vẫn ghi là "không rõ".

TÍNH NĂNG XEM LẠI CÁC BÀN ĐÃ HỌC (bản 1.1)
   - Trong trợ lý nổi bấm "📚 Xem các bàn đã học".
   - Hiện bàn hiện tại và từng bàn cũ: bảng cầu vẽ lại, số Cái/Con/Hòa, chuỗi bệt dài nhất, kiểm định ngẫu nhiên.
   - "Đọc lại từng ván": tool đoán lại từng ván của bàn đó rồi so với kết quả thật.
     Mỗi bàn chỉ được dùng kiến thức học từ các bàn trước nó, nên con số là kết quả thật, không bị "ăn gian".
   - Bấm vào tiêu đề của một bàn để mở/đóng; có nút xóa từng bàn khỏi bộ nhớ học.
   - Khi mở bảng này, trợ lý tạm ngừng đọc màn hình; đóng lại thì đọc tiếp.

CẬP NHẬT TỪ BẢN CŨ: trên GitHub, vào repo > Add file > Upload files, kéo thư mục "app" và file README.txt
   mới vào (ghi đè), bấm Commit. Workflow sẽ tự chạy lại; tải APK mới ở tab Actions như lần trước.

GHI CHÚ
   - Giữ điện thoại ở chế độ dọc. Xoay ngang thì dừng rồi bật lại trợ lý.
   - Dự đoán chỉ mang tính demo: mỗi ván baccarat là ngẫu nhiên độc lập, tỉ lệ đúng thật quanh 50%.
     Nút "Mô phỏng kiểm chứng" trong trợ lý cho bạn tự kiểm tra con số này.
