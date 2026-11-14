# App Inspector (ใช้ส่วนตัว)

แอป Android (Kotlin + Jetpack Compose, Light theme, เก็บข้อมูลในเครื่องเท่านั้น ไม่มี INTERNET permission)

## วิธีสร้าง APK
1. เปิดโฟลเดอร์นี้ด้วย Android Studio (Ladybug ขึ้นไป) → รอ Gradle Sync
2. ต่อมือถือ (เปิด USB debugging) กด Run  หรือ Build > Build APK(s)
3. ไฟล์อยู่ที่ app/build/outputs/apk/debug/app-debug.apk → ส่งเข้าเครื่องแล้วติดตั้งได้เลย

## ฟีเจอร์
- App Manager + ค้นหา + กรอง ผู้ใช้/ระบบ
- Package name (แตะเพื่อคัดลอก), SDK/Min SDK, แหล่งติดตั้ง
- สำรอง APK (รวม split) → Download/AppBackup  และส่งออกไปตำแหน่งที่เลือก
- SHA-256 / SHA-1 / Subject ของใบรับรอง
- Tracker Scanner (ลายเซ็นฝังในแอป ดู Trackers.kt) + Privacy score
- Security Analyzer (สิทธิ์อ่อนไหว, Accessibility, debuggable, sideload ฯลฯ)
- Native libs แยก CPU + ตรวจ Flutter/RN/Unity ฯลฯ
- App History (บันทึกติดตั้ง/อัปเดต/ถอนเมื่อเปิดแอป)

## หมายเหตุ
- รายการ Tracker เป็นชุดย่อยที่เขียนเอง เพิ่มได้ใน Trackers.kt
- อ่าน APK ของแอปอื่นได้ แต่ไม่เห็นข้อมูลภายในแอป (sandbox)
