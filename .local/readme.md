```bash
git config user.email "malocnet@gmail.com"
git config user.name "malocnet"
git config credential.helper /usr/local/bin/git-credential-libsecret
read -s -p "Enter token: " TOKEN && git clone https://malocnet:$TOKEN@github.com/malocnet/TextPad.git
```

# Test:
```bash
./gradlew test
```

# Apk Debug:
```bash
./gradlew assembleDebug
```
Sau khi màn hình báo BUILD SUCCESSFUL, file APK của bạn sẽ nằm tại đường dẫn:
[Thư-mục-dự-án]/app/build/outputs/apk/debug/app-debug.apk
```bash
cp /mnt/data/hoainh/Documents/TextPad/app/build/outputs/apk/debug/app-debug.apk /mnt/data/hoainh/Documents/TextPad/TextPad-v0.0.3.apk
```
=========================================
Vậy tóm tắt thiết kế cuối cùng:

Luồng hoạt động:

Tab chính (edit)     → luôn chỉ có 1
Tab phụ (read-only)  → tối đa 4 tab
                     → tổng cộng 5 tab

Nút “Edit” trên tab read-only:

1. Đóng tab read-only hiện tại
2. Kiểm tra tab chính có unsaved changes không
   → Nếu có: hỏi "Save trước không?"
     Yes    → save → mở file mới trong tab chính
     No     → discard → mở file mới trong tab chính
     Cancel → hủy, không làm gì
   → Nếu không: mở file mới trong tab chính luôn

Bắt đầu implement từ đâu?

Bước 1: Thêm flag READ_ONLY vào EditorActivity, disable edit
Bước 2: Thêm UI tab bar để chuyển giữa các Activity
Bước 3: Thêm nút “Edit” trên tab read-only, xử lý luồng trên
Bước 4: Giới hạn tối đa 5 tab