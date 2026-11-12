# برہان نیکسس اے آئی وائس اسٹوڈیو — رہنما

## 1۔ APK انسٹال کرنا
1. GitHub repo کے **Releases** میں "latest" سے `BurhanNexus-AI-Voice-Studio-debug.apk` ڈاؤن لوڈ کریں۔
2. فون پر فائل کھولیں اور "Install unknown apps" کی اجازت دیں۔
3. Android 8.0 یا اس سے نیا فون درکار ہے۔

## 2۔ Offline آواز انسٹال کرنا (Model Installation Guide)
1. ایپ کھولیں ← **AI Model Manager**۔
2. اردو کے لیے **Piper Urdu - Fasih** پر "Download & Install" دبائیں (تقریباً 65 MB، Wi-Fi بہتر ہے)۔
3. انگلش کے لیے Ryan (مرد) یا Lessac (خاتون) انسٹال کریں۔
4. Kokoro بہتر انگلش آواز ہے مگر بھاری ہے (تقریباً 305 MB، 4 GB RAM بہتر)۔
5. ہٹانے کے لیے اسی کارڈ پر "Remove"۔

فون کی اپنی آواز (Phone System TTS) بغیر ڈاؤن لوڈ کے چلتی ہے۔ اردو کے لیے Model Manager میں
"Voice data install" دبا کر Google کی اردو آواز انسٹال کریں۔

## 3۔ Online آواز اور جذبات (API Configuration Guide)
1. براؤزر میں aistudio.google.com کھولیں، Google اکاؤنٹ سے لاگ اِن کریں، "Get API key" سے مفت key بنائیں۔
2. ایپ ← **Settings** ← "API Configuration" میں key پیسٹ کریں ← Save۔
3. Model name کو ویسے ہی رہنے دیں (`gemini-3.1-flash-tts-preview`)۔ اگر Google نام بدل دے تو یہاں نیا نام لکھیں۔
4. key صرف اسی فون میں encrypt ہو کر محفوظ ہوتی ہے۔ مفت حد (روزانہ / فی منٹ) Google طے کرتا ہے۔

## 4۔ آواز بنانا (User Manual)
1. **Text to Speech** کھولیں، اسکرپٹ لکھیں یا پیسٹ کریں۔ اردو خودبخود دائیں سے بائیں ہو جاتی ہے۔
2. "Voice chunein" سے آواز منتخب کریں (Offline یا Online)۔
3. Emotion اور Intensity صرف Online (Gemini) آواز کے ساتھ کام کرتے ہیں۔
4. **Generate** دبائیں۔ فیصد، باقی وقت اور ہر حصے کی preview نظر آئے گی۔
5. Pause / Resume / Stop دستیاب ہیں۔ غلطی یا Stop کے بعد "Resume" وہیں سے جاری رکھتا ہے۔
6. مکمل ہونے پر Play، **WAV**، **MP3** اور Share کے بٹن آ جاتے ہیں۔

### Tags
```
[Happy] آج ہمارے لیے بہت خوشی کا دن ہے!
[Sad] مجھے اس بات کا بہت افسوس ہے۔
[Pause: 2 seconds]
[Whispering] میں تمہیں ایک راز بتاتا ہوں۔
```
- جذبات کا tag اپنی لائن کے آخر تک لاگو رہتا ہے۔
- `[Pause: 2 seconds]` ہر engine میں اصلی خاموشی ڈالتا ہے۔
- Offline آوازیں جذبات ادا نہیں کرتیں؛ ایپ اس کی warning دکھاتی ہے۔

## 5۔ ابھی کیا Pending ہے
Voice Cloning، Multi Speaker Studio، Audio Editor، مکمل اردو انٹرفیس، Signed Release APK۔
تفصیل: `docs/FEASIBILITY.md` اور `docs/TESTING.md`۔
