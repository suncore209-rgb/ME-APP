# 📱 মিরন ইলেকট্রনিক্স — Android APK

বিজ্ঞাপনমুক্ত, সম্পূর্ণ বিনামূল্যে Android APK।

---

## ⚡ সবচেয়ে সহজ পদ্ধতি — GitHub দিয়ে Auto Build

### ধাপ ১ — URL বসান

`app/src/main/java/com/miron/electronics/MainActivity.java` ফাইল খুলুন।

এই লাইনটি খুঁজুন:
```java
private static final String APP_URL = "https://YOUR-APP.vercel.app";
```

আপনার Vercel URL দিন:
```java
private static final String APP_URL = "https://miron-app.vercel.app";
```

### ধাপ ২ — GitHub Repository তৈরি করুন

1. **github.com** → New Repository → নাম দিন: `miron-android`
2. **Public** রাখুন (Private হলে Actions কাজ করবে)

### ধাপ ৩ — Code Upload করুন

```bash
cd miron-android
git init
git add .
git commit -m "first commit"
git branch -M main
git remote add origin https://github.com/আপনার-username/miron-android.git
git push -u origin main
```

### ধাপ ৪ — APK Download করুন

GitHub push করার **৫-৭ মিনিট** পর:

```
আপনার Repository → Actions tab → সবচেয়ে নতুন workflow
→ "MironElectronics-APK" artifact → Download
```

অথবা **Releases** section থেকেও পাবেন।

---

## 📲 APK Install করুন

1. ZIP extract করুন → `MironElectronics.apk` পাবেন
2. Phone এ পাঠান (WhatsApp / USB / Google Drive)
3. Phone Settings → **"Unknown sources"** বা **"Install unknown apps"** চালু করুন
4. APK তে tap করুন → Install

---

## 🔄 App Update করতে

শুধু code change করে GitHub এ push করুন:
```bash
git add .
git commit -m "update"
git push
```
**৫-৭ মিনিটে নতুন APK তৈরি হবে।**

---

## ✨ Features

- ✅ কোনো বিজ্ঞাপন নেই
- ✅ Pull-to-refresh (নিচে টানলে reload)
- ✅ Back button কাজ করে
- ✅ Internet না থাকলে বাংলায় message
- ✅ Retry button
- ✅ Full screen (action bar নেই)

---

## 📁 Project Structure

```
miron-android/
├── .github/workflows/build.yml  ← Auto APK builder
├── app/src/main/
│   ├── java/com/miron/electronics/
│   │   └── MainActivity.java    ← 👈 URL এখানে বদলান
│   ├── res/
│   │   ├── mipmap-*/            ← App icons
│   │   └── values/strings.xml  ← App name
│   └── AndroidManifest.xml
├── build.gradle
├── settings.gradle
└── gradlew
```
