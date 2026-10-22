# 🐾 Kurogram

Kurogram is a third-party Telegram client for Android, forked from [Nekogram](https://github.com/Nekogram/Nekogram) (which is itself based on the official Telegram for Android).

## What's different

- **Theme Store & skin engine:** themes live in a database, not in the app. Download, preview, apply and roll back live, with colours, shapes, chat bubbles, fonts and a few native settings (blur, glass tab bar) driven by a versioned JSON schema.
- **Sticker Store:** browse, rate, like and install community sticker packs.
- **Profiles & community:** owner profiles, ratings, likes and reporting for themes and sticker packs.
- Everything from Nekogram.

Store features talk to a private backend, so a plain build runs without them.

## Compilation Guide

1. Clone the source: `git clone --recursive --shallow-submodules https://github.com/thant393/Kurogram.git`
1. Fill out storeFile, storePassword, keyAlias, keyPassword in `local.properties` to use your release keystore.
1. Create a Firebase project with Android apps for your application IDs, enable Firebase Messaging and put `google-services.json` in the `TMessagesProj` folder.
1. Open the project in Android Studio (open it, don't import it).
1. Fill out the values in `TMessagesProj/src/main/java/tw/nekomimi/nekogram/Extra.java`; each variable has a link showing where to get it.
1. Build.

## API, Protocol documentation

Telegram API manuals: https://core.telegram.org/api

MTProto protocol manuals: https://core.telegram.org/mtproto

## Credits & License

Kurogram builds on [Nekogram](https://github.com/Nekogram/Nekogram) and [Telegram for Android](https://github.com/DrKLO/Telegram). See `LICENSE` for the license terms.
