# Android GPU feasibility, 1 Oct 2026

GPU decoding is a plausible next experiment, not a demonstrated solution for this unspecified iQOO. The delivered CPU test build does not enable GPU. Exact model/chipset, Android driver capabilities, thermal behavior and sustained end-to-end caption latency must be measured on that device.

Upstream whisper.cpp documents a Vulkan backend (`-DGGML_VULKAN=1`). Its current Vulkan build requires Vulkan and the glslc shader compiler; cross-compiling Android needs host shader compilation plus Android Vulkan linking and driver testing. Turning `use_gpu` on in JNI alone does not package this backend. Source: https://github.com/ggml-org/whisper.cpp and https://github.com/ggml-org/whisper.cpp/blob/master/ggml/src/ggml-vulkan/CMakeLists.txt

Community evidence supports feasibility, not an iQOO guarantee: a July 17, 2026 report uses an Honor Magic 8 Pro, Adreno 840, Termux and the Mesa Turnip driver. It reports 120 seconds of audio with Small in 27.8 seconds GPU versus 120.8 seconds CPU (about 4.3x). Different phone, different driver/runtime, Small rather than Turbo, and self-reported. Do not extrapolate that speedup to this APK or to Turbo. It also describes a shader compiler workaround. Source: https://github.com/ggml-org/whisper.cpp/discussions/3943

The upstream Android example recommends Tiny/Base, not Turbo, for its simple sample. This is guidance, not a hard memory or speed limit. Source: https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android

An older 2024 upstream CLBlast/OpenCL Android experiment exists, but it predates current backend changes and quantized Turbo. It does not establish current shipping compatibility. Source: https://github.com/ggerganov/whisper.cpp/pull/1809

A downstream fork has Adreno Vulkan fixes, further evidence that driver-specific compatibility work matters. It is not validation of our pinned upstream source. Source: https://github.com/tetherto/qvac-ext-lib-whisper.cpp/pull/30

Next GPU gate: identify the exact phone, package a separate arm64 Vulkan build with CPU fallback, verify the backend actually offloads on that driver, use the same weight files/passage/settings for CPU and GPU, measure warm-up and 2-minute continuous camera+audio load, memory, heat, end-to-end delay and backlog. Only call it live-capable if captions keep up without growing delay. No GPU speed or heat measurement has been made here.
