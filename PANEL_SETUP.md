# ZUNO Panel - setup (Hinglish)

## 1. Supabase project (free)
1. https://supabase.com -> New project (naam: zuno, ek password rakh lo).
2. Left menu **SQL Editor** -> New query -> `supabase/schema.sql` ka poora text paste -> **Run**.
3. **Authentication -> Users -> Add user** -> apni email + password (Auto Confirm ON).
4. SQL Editor mein ye chalao (email apni daalo):
   ```sql
   insert into public.admins(user_id, email)
   select id, email from auth.users where email = 'TERI_EMAIL@gmail.com';
   ```
5. (Recommended) **Authentication -> Sign In / Providers -> Email** mein
   "Allow new users to sign up" band kar do.

## 2. Keys
**Project Settings -> API Keys** (ya upar **Connect** button):
- Project URL  (https://xxxx.supabase.co)
- **Publishable key** (sb_publishable_...)  [purane projects mein "anon" key bhi chalti hai]
(Secret key / service_role key kabhi kisi file / app / panel mein mat daalna.)

## 3. App
`app/src/main/kotlin/com/metrolist/music/zuno/ZunoPanel.kt` mein upar:
```kotlin
const val SUPABASE_URL = "https://xxxx.supabase.co"
const val SUPABASE_ANON_KEY = "eyJ..."
```
phir GitHub pe push karo, APK build ho jayega.
(Khali chhodoge toh app bina panel ke normal chalega.)

## 4. Panel website
`panel/index.html` ek hi file hai.
- Phone/PC mein seedha kholo, ya free hosting pe daalo: GitHub Pages / Netlify / Cloudflare Pages.
- Pehli baar URL + anon key poochega, phir email/password se login.

## Panel mein kya hai
Dashboard (active users) - About page (links + developer) - Banners (unlimited, pin, schedule, image upload) - Songs (link se auto-fill, bulk add, check links) -
Categories (Home sections) - Broken songs (auto-hide + bulk delete) - Users (block/unblock) - Announcements - Together servers - Settings (maintenance).

## App mein kya dikhta hai
- Home: banners (cache hote hain, turant dikhte hain) + categories ke sections
- Search: ZUNO ke gaane sabse upar
- Banner tap: playlist khulti hai / gaana chalta hai / link khulta hai
- Block, maintenance message aur announcement popup app khulte hi
- Gaana retry ke baad bhi na chale toh panel ko report jata hai

## Update (About page links)
Agar `schema.sql` pehle chala chuke ho, toh sirf `supabase/update_about.sql` chalao
(SQL Editor -> New query -> paste -> Run). Naye project mein `schema.sql` mein ye pehle se hai.
