# ZUNO Admin Panel + Supabase Setup (Hinglish)

Is repository mein admin panel ka web page `panel/index.html` hai. Ye Supabase mein banners, songs, categories, announcements, devices, About page aur Listen Together server settings manage karta hai. Android app isi Supabase project se data padhta hai.

## 1. Supabase project aur schema

1. [supabase.com](https://supabase.com) par project banao.
2. Supabase **SQL Editor → New query** kholo.
3. Is repo ki `supabase/schema.sql` file poori paste karke **Run** karo. Isse required tables/RLS, image bucket, admin song pin, aur Global Top 20 play-count RPCs setup honge.
4. Agar tumhara Supabase project pehle se bana hua hai, to **updated `schema.sql` ko phir se poora run** karo. Ye added column/table/functions ko `IF NOT EXISTS` / `CREATE OR REPLACE` ke zariye apply karega; purane songs delete nahi honge.

## 2. Admin user banao

1. **Authentication → Users → Add user** mein apna admin email/password banao; email confirm/auto-confirm karo.
2. SQL Editor mein neeche apni email rakh ke run karo:

   ```sql
   insert into public.admins(user_id, email)
   select id, email
   from auth.users
   where email = 'YOUR_EMAIL@example.com'
   on conflict (user_id) do nothing;
   ```

3. Login verify karne ke baad **Authentication → Sign In / Providers → Email** mein public sign-ups band kar do.

## 3. Admin panel host aur login

1. `panel/index.html` tumhare diye hue `zuno-panel.html` se update kiya gaya hai.
2. Netlify Drop par repository ka `panel/` folder upload karo (ya ise apni static hosting par publish karo). Hosting ka HTTPS URL kholo.
3. Supabase **Project Settings → API Keys** se **Project URL** aur **Publishable key** lo. Panel mein dono enter karke Connect dabao, phir apne admin email/password se login karo.
4. Ye connection us browser ki local storage mein rahega; naye browser/device par URL/key dobara enter karna hoga.

## 4. Songs, pin aur Global Top 20

- Admin panel ke **Songs** tab se song add/bulk-add karo. Sirf active/live songs Top 20 pool mein aa sakte hain.
- **Pin banner / Unpin banner** dabane se song ko priority milti hai. Home carousel pehle pinned songs dikhata hai; baaki songs pichhle **30 din ke global play count** ke hisaab se dikhte hain. Tie hone par recently added song ko pehle rakha jata hai.
- Home carousel mein zyada se zyada **20 items** hote hain. Admin-created pinned banners bhi priority par hain; ordinary manual banners Top songs ke baad remaining space mein aate hain.
- App play reports sirf ZUNO banner/Home picks/ZUNO Search ke song playback path se bhejti hai. Reports mein Supabase raw device ID nahi rakhta: server daily hashed device key use karta hai, aur ek device/song ko din mein 20 counts tak limit karta hai.

## 5. App ko Supabase se connect karo

`app/src/main/kotlin/com/metrolist/music/zuno/ZunoPanel.kt` mein `ZunoPanelConfig` ke andar ye values set karo:

```kotlin
const val SUPABASE_URL = "https://YOUR_PROJECT_ID.supabase.co"
const val SUPABASE_ANON_KEY = "YOUR_PUBLISHABLE_KEY"
```

Phir nayi APK build/install karo. Sirf admin panel ko host karne se pehle se installed APK ko Supabase key nahi milti. **Secret key / `service_role` key kabhi HTML, APK, GitHub, Netlify ya chat mein mat daalna.**

## 6. Offline behavior

- App last-synced banners aur Global Top 20 list ko phone par cache karta hai; startup par cache pehle load hota hai. Internet band hone par pehle se sync hua text/list nahi gayab hona chahiye.
- Banner artwork Coil disk cache mein save karne ki request karta hai. Image offline tabhi milegi jab woh is phone par pehle load hui ho aur app/cache data clear ya evict na hua ho.
- Naya admin banner/song doosre phone ko internet se sync hone ke baad hi milega. **Fresh install jo pehli baar hi offline ho**, uske paas Supabase ka song catalog/cache nahi hota; us situation mein dynamic Top 20 dikhana mumkin nahi jab tak ek baar online sync na ho.
- Offline song-play reports locally queue hoti hain aur app next refresh par online aane ke baad bhejne ki koshish karta hai. Shared/global rank sab devices ke next successful refresh par badlega.

## 7. Quick test

1. Supabase mein schema run karo, admin user allow karo, app constants set karke APK banao.
2. Panel ke **Songs** tab se kam az kam 20 active songs add karo. Kuch songs ko pin karke dekho.
3. App ko internet ke saath Home khol kar ek baar sync karo; pinned songs aur Top 20 load ho jayenge.
4. ZUNO Home picks/Search se kuch songs play karo. Next refresh ke baad play-count order server se update hoga.
5. Internet band karke app/Home dubara kholo: last-synced list aur cached artwork (agar device cache mein hai) dikhni chahiye.

### Troubleshooting

- **“Access nahi hai”**: Supabase Auth user banao aur usi email ko `public.admins` mein insert karo.
- **`is_pinned_banner` ya RPC error**: updated `supabase/schema.sql` poori run karo; uske baad app/panel dobara try karo.
- **App mein data nahi**: `ZunoPanel.kt` URL/key, internet, song `Live` state, RLS aur nayi APK check karo.
- **Image upload nahi hoti**: `zuno-images` bucket/policies schema se bani hon aur panel admin se logged in ho.
