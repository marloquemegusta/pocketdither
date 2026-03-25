# Release signing

`assembleRelease` works out of the box and produces an unsigned APK:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

To generate a signed installable release APK, add a local `keystore.properties` file at the repository root.

## 1. Create or choose a keystore

Example command:

```powershell
keytool -genkeypair `
  -v `
  -keystore pocketdither-upload.jks `
  -alias pocketdither `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000
```

Keep the `.jks` file outside Git or in a folder already ignored by `.gitignore`.

## 2. Create `keystore.properties`

Start from [keystore.properties.example](../keystore.properties.example) and fill in your local values:

```properties
storeFile=keystore/pocketdither-upload.jks
storePassword=your-store-password
keyAlias=pocketdither
keyPassword=your-key-password
```

`storeFile` can be relative to the repository root or an absolute path.

## 3. Build the signed release

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:GRADLE_USER_HOME="$PWD\.gradle"
.\gradlew.bat assembleRelease
```

If `keystore.properties` exists and is valid, Gradle signs the release automatically.

## 4. Output path

```text
app/build/outputs/apk/release/
```

Depending on the Android Gradle Plugin version and whether the APK is signed inside Gradle or afterwards, you may see either `app-release.apk` or `app-release-unsigned.apk` plus a separately signed copy. If you want a renamed artifact, that can be added as a follow-up task.
