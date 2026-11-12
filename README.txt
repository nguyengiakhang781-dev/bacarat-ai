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
