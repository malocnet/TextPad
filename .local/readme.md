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
