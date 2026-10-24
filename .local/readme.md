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
./gradlew clean assembleDebug
```
Sau khi màn hình báo BUILD SUCCESSFUL, file APK của bạn sẽ nằm tại đường dẫn:
[Thư-mục-dự-án]/app/build/outputs/apk/debug/app-debug.apk
```bash
cp /mnt/data/hoainh/Documents/TextPad/app/build/outputs/apk/debug/app-debug.apk /mnt/data/hoainh/Documents/TextPad/TextPad-v0.0.3.apk
```



# Apk Relaese
```bash
keytool -genkeypair -v \
  -keystore release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias textpadalias \
  -storepass TextPadPass123@MyStrong \
  -keypass TextPadPass123@MyStrong \
  -dname "CN=malocnet, OU=None, O=None, L=Ho Chi Minh City, ST=Ho Chi Minh, C=VN"
```

```bash
export ANDROID_KEYSTORE=./release-key.jks
export KEYSTORE_PASSWORD=TextPadPass123@MyStrong
export KEY_ALIAS=textpadalias
export KEY_PASSWORD=TextPadPass123@MyStrong
```

```bash
./gradlew clean assembleRelease
```

Nếu muốn verify APK đã ký đúng chưa:

```bash
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

Hoặc xem thông tin ký:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Sau khi màn hình báo BUILD SUCCESSFUL, file APK của bạn sẽ nằm tại đường dẫn:
[Thư-mục-dự-án]app/build/outputs/apk/release/app-release.apk

```bash
cp app/build/outputs/apk/release/app-release.apk /mnt/data/hoainh/Documents/TextPad/TextPad-v0.0.1.apk
```
=========================================
Vậy tóm tắt thiết kế cuối cùng:

Luồng hoạt động:

Tab chính (edit)     → luôn chỉ có 1, ten file mac dinh formart newfile-yyyymmddhhmmss.txt
Tab phụ (read-only)  → tối đa 4 tab, thứ 5 dưới dạng read-only-> Hỏi user chọn tab đóng, ko tab read-only nao dong -> báo lỗi và từ chối mở.
                     → tổng cộng 5 tab
Nút “Edit” trên tab read-only:

1. Kiểm tra tab chính có unsaved changes không
   → Nếu có: hỏi dialog
     Yes    → save → đóng tab read-only → mở file vào tab chính
     No     → discard → đóng tab read-only → mở file vào tab chính
     Cancel → không làm gì cả (tab read-only vẫn còn nguyên)
   → Nếu không: đóng tab read-only → mở file vào tab chính

Bắt đầu implement từ đâu?

Bước 1: Thêm flag READ_ONLY vào EditorActivity, disable edit
Bước 2: danh sach tab trong menu "tab list"
Bước 3: Thêm nút “Edit” trên tab read-only, xử lý luồng trên
Bước 4: Giới hạn tối đa 5 tab

Bước 1: EditorState — đóng gói toàn bộ state của 1 tab
Bước 2: TabManager — quản lý list EditorState, switch logic
Bước 3: Migrate EditorActivity — dùng TabManager thay vì field trực tiếp
Bước 4: UI tab list trong menu
Bước 5: Flag READ_ONLY, disable edit, nút "Edit" + luồng confirm
Bước 6: Giới hạn 5 tab + dialog chọn tab đóng

EditorActivity (1 instance duy nhất)
    │
    ├── TabManager (singleton hoặc field của Activity)
    │       ├── Tab 0: edit tab  (luôn tồn tại)
    │       ├── Tab 1: read-only
    │       ├── Tab 2: read-only
    │       ├── Tab 3: read-only
    │       └── Tab 4: read-only
    │
    └── EditorState (data class, mỗi tab giữ 1 instance)
            ├── urlFilename
            ├── content (text)
            ├── cursorStart / cursorEnd
            ├── scrollPosition
            ├── changed
            ├── isReadOnly
            ├── recoveryKey
            └── originalContentSha256 / originalSize / ...
            
========================================================================
Kiến trúc

Single EditorActivity + TabManager field. Switch tab = lưu state tab hiện tại → load state tab mới vào mText.

EditorActivity (1 instance)
├── TabManager
│   ├── Tab 0: edit (luôn tồn tại, default: newfile-yyyymmddhhmmss.txt)
│   ├── Tab 1-4: read-only (tối đa 4)
│   └── activeIndex
└── EditorState (mỗi tab 1 instance)
    ├── urlFilename, isReadOnly
    ├── content, cursorStart, cursorEnd, scrollPosition
    ├── changed, recoveryKey
    └── originalContentSha256, originalSize, originalLastModified

Luồng “Edit” từ read-only tab
Tab chính changed?
├── Yes → dialog "Save changes?"
│   ├── Yes    → save → đóng read-only tab → load file vào tab chính
│   ├── No     → discard → đóng read-only tab → load file vào tab chính
│   └── Cancel → không làm gì
└── No → đóng read-only tab → load file vào tab chính

Giới hạn tab
Tổng 5 tab: 1 edit + 4 read-only
Mở read-only thứ 5: hỏi user chọn tab nào đóng
User không chọn → báo lỗi, từ chối mở

Thứ tự implement
Bước 1: EditorState  — data class, đóng gói state 1 tab
Bước 2: TabManager   — list EditorState, switch logic
Bước 3: Migrate EditorActivity — dùng TabManager thay field trực tiếp
Bước 4: UI tab list trong menu
Bước 5: READ_ONLY flag, disable edit, nút "Edit" + luồng confirm
Bước 6: Giới hạn 5 tab + dialog chọn đóng tab


Folder structure
simpletexteditor/
├── temp/          ← new files chưa save (newfile-yyyyMMddHHmmss-uuid.txt)
├── autosave/      ← (reserved, chưa dùng trong flow này)
└── notsave/       ← file đang edit có thay đổi khi mở file khác
Logic flow
New:
  - không có nội dung → clearFile() luôn
  - có nội dung → save vào temp/ → clearFile()

Open:
  - không có thay đổi → load file bình thường
  - có thay đổi → save vào notsave/tenfile-uuid.ext → load file mới

Mở file khác (recent, open in new tab nếu còn):
  - có thay đổi → save vào notsave/tenfile-uuid.ext → load file mới