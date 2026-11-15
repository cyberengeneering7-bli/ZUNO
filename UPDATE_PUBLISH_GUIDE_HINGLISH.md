# ZUNO update publish guide (Hinglish)

## 1. Supabase migration

Supabase SQL Editor mein `supabase/update_app.sql` ko poora paste karke run karo. Ye `app_updates` table banayega, RLS policies add karega, `devices.app_version_code` add karega aur `register_device` RPC ko backward-compatible 4th argument ke saath replace karega. Is script ko dobara run karna safe hai.

## 2. Signing key ko GitHub Secrets mein migrate karna

Current workflow cache wali signing key ko replace nahi karta; `.github/workflows/make-key.yml` existing `zuno-debug-keystore-v1` cache se same key read karke 1-day retention wala one-time artifact banata hai. GitHub mein **Actions → Migrate ZUNO signing key → Run workflow** ek baar run karo. Artifact download karke uski chaar lines ko repository Actions Secrets mein manually add karo.

Verify these four repository secrets exist:

- `ZUNO_KEYSTORE_BASE64`
- `ZUNO_KEYSTORE_PASSWORD`
- `ZUNO_KEY_ALIAS`
- `ZUNO_KEY_PASSWORD`

Secret values ko logs mein print mat karo. Secrets save karne ke baad artifact ko turant delete karo aur `make-key.yml` ko bhi delete/disable kar do; secrets ka encrypted backup zaroor rakho. Agar old cache missing ho, nayi key generate mat karna—warna existing users ke APK update nahi honge.

## 3. APK build

`main` par push ya **Actions → Build APK → Run workflow** karo. Workflow secrets se `app/zuno-release.jks` temporary decode karta hai, `ZUNO_VERSION_CODE = 100 + run_number` aur `ZUNO_VERSION_NAME = 1.<run_number>` set karta hai, phir file ko clean up karta hai. Job Summary mein APK ka SHA-256 milega.

APK ko GitHub Release ke asset ke roop mein upload karo. Asset URL HTTPS aur `.apk` par end hona chahiye, jaise:

`https://github.com/cyberengeneering7-bli/ZUNO/releases/download/v1.1/ZUNO.apk`

## 4. Panel se update publish karna

Panel mein **App update** tab kholo:

1. Version name aur version code bharna hai. Version code current latest se bada hona chahiye.
2. GitHub Release ka HTTPS `.apk` URL daalo.
3. Job Summary se SHA-256 optional field mein paste karo.
4. Release notes aur minimum version code set karo.
5. **Force update** sirf breaking/security update ke liye use karo.
6. Publish dabao. Purani active release automatically inactive ho jayegi.

Rollback newest release ko inactive karta hai. Rollback ke baad app ko server se naya active row milna chahiye; jis device par newer APK already installed hai us par update dialog nahi dikhega.

## 5. Test checklist

- Fresh install par update endpoint empty ho toh dialog na aaye.
- Older APK par valid active release ho toh dialog aaye.
- Download ke dauran percentage aur progress bar update ho.
- Download complete hone par **Install** button aaye.
- Force update mein Later button, outside dismiss aur back dismiss disabled hon.
- Non-force update mein Later dabane par same app-start session mein dialog hide ho.
- App restart ke baad non-force update dobara check ho.
- No network par app normal khule; gate/update silently fail ho.
- HTTP status error ya wrong URL par error message aur Retry aaye.
- Wrong SHA-256 par partial APK delete ho aur install block ho.
- Wrong package name ya mismatched signing certificate par install block ho.
- Android unknown-app-source permission off ho toh settings screen khule; return ke baad Install continue ho.
- Successful update ke baad next app start par `VERSION_CODE >= latest.version_code` ke karan dialog/badge na aaye.
- Panel rollback ke baad active/inactive status verify karo.
- Xiaomi/MIUI phone par battery optimization ko Unrestricted karo aur unknown-app install permission manually allow karo; MIUI kabhi-kabhi installer ko background se rok sakta hai.

## Important

GitHub Release asset ko replace karke same version code reuse mat karo. Har APK ke liye naya, higher version code use karo. APK URL aur SHA-256 ko panel mein publish karne se pehle dobara check karo.
