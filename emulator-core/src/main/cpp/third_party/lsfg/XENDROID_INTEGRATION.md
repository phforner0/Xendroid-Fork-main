# LSFG port provenance and distribution

Upstream host engine: Bannerlator `15d4f73c079824d54c468f079361ce56d88a7c2e`,
`app/src/main/cpp/winlator/lsfg`. Source headers and GPL notices are preserved.
The user approved GPL-3.0 integration for the combined APK on 2026-09-30.
Original Xenia BSD and Win-FG MIT files keep their original licenses; distributing
a combined build with this GPL engine requires the corresponding source and notices.

`Lossless.dll` and extracted proprietary shaders are NEVER checked into this project
or embedded in an APK. They are imported and translated locally at runtime.

XenDroid changes: direct dispatch binding (no Bannerlator compositor types),
bounded PE/cache import, explicit pacing clock for host tests, JNI import and a
shared Xenia presenter scheduler. 2x is default; 3x/4x are explicit experimental
choices and are tested with software synthesis. Hardware cadence, FP16 preset
selection and performance superiority still require device measurement.
