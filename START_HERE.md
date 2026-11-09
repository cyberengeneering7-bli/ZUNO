# APK kaise banayein (GitHub Actions se)

1. GitHub pe naya repo banao (empty, README mat add karna).
2. Is folder ke andar ki saari files repo mein upload/push karo:
   ```
   git init
   git add .
   git commit -m "first commit"
   git branch -M main
   git remote add origin https://github.com/TERA_USERNAME/TERA_REPO.git
   git push -u origin main
   ```
3. GitHub -> **Actions** tab. Build apne aap chalega (ya "Build APK" -> **Run workflow**).
4. 10-20 min baad run kholo -> neeche **Artifacts** -> `zuno-apk` download karo.
5. Zip kholo, andar `.apk` hai. Phone mein install karo.

App ka naam / ID badalna: `.github/workflows/build-apk.yml` mein
`METROLIST_APP_NAME` aur `METROLIST_APPLICATION_ID`.

Original: Meld by FrancescoGrazioso (GPL-3.0). License file mat hatana.
