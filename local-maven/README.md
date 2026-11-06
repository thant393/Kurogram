# local-maven

Prebuilt libraries built from our own repositories, used as a Maven repository by `settings.gradle`.

| Artifact | Built from |
|---|---|
| `tw.kurocore.kurogram:gifski:1.0.2` | https://github.com/thant393/kuro-gif (workflow "Build AAR", branch `aar`), commit f12143d |

To update: run the workflow, copy `gifski-release.aar` from the `aar` branch to `gifski/<version>/gifski-<version>.aar` and bump the version here and in `TMessagesProj/build.gradle`.
