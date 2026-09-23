# MHE Miles

A business mileage log for **MHE Onsite Training**, in the company's brand colours and logo.
It's an Android app that records car trips by itself and gets your mileage ready for your Self Assessment tax return.

## How it works

1. **Trip starts.** Your phone connects to the Nissan head unit over Bluetooth **and** is on charge.
   The app then records the trip with GPS in the background. You don't need to open it.
2. **Trip ends.** When the car's Bluetooth disconnects (engine off), the trip is saved with the
   start and end addresses, times and miles.
3. **"Was this trip for work?"** A notification asks straight away. Tap **Work** or **Personal**.
   You can also sort trips later in the app and add a purpose, such as the client or course.
4. **Tax report.** Pick a UK tax year (6 April to 5 April), custom dates or all time. Filter to
   work, personal or all trips. You see total business miles and the HMRC mileage allowance
   (45p a mile for the first 10,000 business miles, 25p after). **Save CSV** or **Share / email**
   exports a spreadsheet for Excel, Google Sheets or your accountant.

You can also start or end a trip by hand, add a trip you forgot, and edit or delete any trip.

## Install

Every push builds an installable APK on GitHub Actions:

1. Open the repo's **Actions** tab, pick the latest **Build app** run, and download the
   **MHE-Miles-apk** artifact (a zip containing the `.apk`).
2. Copy it to your phone and open it. Allow "install unknown apps" when Android asks.

### Signing

Builds are signed with `signing/mhe-miles.p12`, so each new APK installs over the old one and
keeps your trips. The key is locked with a password stored as the repository secret
`SIGNING_PASSWORD` (Settings → Secrets and variables → Actions). Keep a copy of that password:
if it's lost, the next version can only be installed after uninstalling, which deletes your trips.

## First-time setup (in the app's **Setup** tab)

1. Pair your phone with the Nissan as normal (Phone settings → Bluetooth).
2. **Choose car Bluetooth**, then pick the Nissan. Head units usually show up as `MY-CAR`,
   `NissanConnect` or the model name.
3. Allow each permission in the list. Everything must show **✓ Done** for trips to record while the app is closed:
   - Nearby devices (Bluetooth)
   - Location, then Location **Allow all the time**
   - Notifications
   - Battery **Unrestricted**. Without this, Android 12+ may block recording from starting. You would then get a
     "Car connected – tap to start recording" notification instead.

   - **Link car to app**. Android shows a "link device" dialog; approve it. The phone then treats
     the car like a paired watch, so the app can start recording from the background.

## If a trip didn't record

- Open **Setup → Right now** while sitting in the car. It shows whether the phone can see the car,
  whether it's charging, and whether it's recording. Opening the app in the car also starts
  recording if it was missed.
- **Setup → Activity log** lists every Bluetooth connection the phone reported, and whether
  recording started or what stopped it. Tap **Share** to send it on.

Some phones (Samsung, Xiaomi, Huawei and others) have extra battery savers. If trips don't start by themselves, also
set MHE Miles to *Unrestricted* / *Never sleeping* in the phone's battery settings.

## Project layout

| Path | What's there |
| --- | --- |
| `core/` | Plain Kotlin: UK tax years, HMRC rate calculation, GPS distance filtering, CSV export, plus unit tests |
| `app/src/main/java/.../tracking/` | Bluetooth trigger, foreground GPS service, end-of-trip notification |
| `app/src/main/java/.../data/` | Room database of trips, settings |
| `app/src/main/java/.../ui/` | Jetpack Compose screens and the brand theme (`Theme.kt`) |

To change the brand colours, edit `Brand` in `ui/Theme.kt` and `res/values/colors.xml`. The logo is
`res/drawable-nodpi/mhe_logo.png`. The font is Montserrat (SIL Open Font Licence).

Build locally with Android Studio, or run:

```sh
./gradlew -p core test          # business logic tests
./gradlew :app:assembleRelease  # APK in app/build/outputs/apk/release/
```

*This app keeps records. It isn't tax advice. Check anything unusual with your accountant.*
