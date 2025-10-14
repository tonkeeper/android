# SignerApp

Open source secure android app for cold managing [TON crypto](https://ton.org/) wallet keys and signing blockchain transactions, designed to operate completely offline.

## Features

- Operates offline, compatible with [Tonkeeper](https://tonkeeper.com/).
- Supports multiple keys.
- Enables transaction signing directly within safe area in SignerApp.
- Encrypts keys using your personal password for maximum security.

## Privacy friendly

- No internet access required, ensuring no data is upload.
- Does not collect analytics or log user activity.
- Encrypts all secure data with your password, making it accessible only to you.
- Private keys never leave app, exported, copied, etc.

Our developers cannot access your data.

## Security

> **⚠️ Important! Devices that:**
> - not protected by biometrics or password
> - [rooted](https://en.wikipedia.org/wiki/Rooting_(Android))
> - [ADB-enabled](https://developer.android.com/tools/adb)
>
> **does not cancel security but reduces it.**

- Eschews store passwords or [password hashes](https://en.wikipedia.org/wiki/Cryptographic_hash_function). Only stores cryptographic [salt](https://en.wikipedia.org/wiki/Salt_(cryptography)) and a part of hash for password verification, excluding encryption use.
- Uses the [Argon2](https://en.wikipedia.org/wiki/Argon2) algorithm for password hashing with a 256-bit salt, generated using [SecureRandom](https://developer.android.com/reference/java/security/SecureRandom).
- Applies [AES256-GCM](https://en.wikipedia.org/wiki/Advanced_Encryption_Standard) for encryption of private keys, paired with a random 128-bit [initialization vector](https://en.wikipedia.org/wiki/Initialization_vector), also [SecureRandom](https://developer.android.com/reference/java/security/SecureRandom)-generated.
- All data pertinent to keys, salt, etc., are additionally/wrapped again securely encrypted and stored, leveraging a key from the [Android KeyStore](https://developer.android.com/privacy-and-security/keystore) within a [Trusted Execution Environment (TEE)](https://source.android.com/docs/security/features/trusty), by [androidx.security.crypto](https://developer.android.com/jetpack/androidx/releases/security) library.
- If your device supports [StrongBox](https://developer.android.com/privacy-and-security/keystore#HardwareSecurityModule), SignerApp using it to enhance security.


## Verify build

Build APK from source code and verify it with the APK from the [Google Play Store](https://play.google.com/store/apps/details?id=com.tonapps.signer).

```shell
$ python apkfrombundle.py signer_from_source.aab signer_from_googleplay.apk
```

or

```shell
$ python apkdiff.py signer_from_source.apk signer_from_googleplay.apk
```

If you have any issues or questions, please contact us at [@help_tonkeeper_bot](https://t.me/help_tonkeeper_bot).





