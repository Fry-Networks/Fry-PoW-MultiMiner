# Building the bundled miner binaries

The APK ships four CPU miners as `lib*.so` under `app/src/main/jniLibs/<abi>/`.
They are unmodified upstream sources, cross-compiled for Android with the NDK.
This directory documents exactly how, both so the build is reproducible and to
satisfy the GPL obligation to make the corresponding source available.

Everything below is toolchain and configure-flag level only, **except the
armeabi-v7a VerusHash miner**, which needs a three-line alignment fix. That patch
is committed verbatim at `patches/0001-verus-armv7-16byte-alignment.patch` and is
the corresponding source for the binary we ship — see "VerusHash on 32-bit ARM"
below.

| Binary | Upstream | Tag built | Licence |
|---|---|---|---|
| `libxmrig.so` | https://github.com/xmrig/xmrig | `v6.26.0` | GPLv3 |
| `libxlarig.so` | https://github.com/scala-network/XLArig | `v5.2.4` | GPLv3 |
| `libcpuminer.so` | https://github.com/tpruvot/cpuminer-multi | `1.3.7` | GPLv2 |
| `libverus.so` | https://github.com/monkins1010/ccminer (CPU VerusHash 2.2.2) | `3.8.3` | GPLv2 |

Toolchain: Android NDK **r26b** (26.1.10909125), clang 17.0.2, `ANDROID_PLATFORM=android-24`,
ABIs `arm64-v8a` and `armeabi-v7a`. Built inside a clean `ubuntu:24.04` container.

## Why the binaries are named `lib*.so`

Android extracts files matching `lib/<abi>/lib*.so` into the app's
`nativeLibraryDir`, which since Android 10 is the only app-owned directory
permitted to contain executable files. The app runs them from there with
`ProcessBuilder`. This also requires `packaging { jniLibs { useLegacyPackaging = true } }`
in `app/build.gradle.kts`: without it the libraries stay compressed inside the
APK and there is no real file on disk to execute.

## Two non-obvious fixes

1. **bionic has no separate `libpthread` / `librt`.** Both are folded into libc, so
   linking fails with `unable to find library -lpthread`. Empty stub archives on
   the link path satisfy the reference:
   ```sh
   mkdir -p /build/stubs/arm64 /build/stubs/armv7
   llvm-ar rcs /build/stubs/arm64/libpthread.a; llvm-ar rcs /build/stubs/arm64/librt.a
   llvm-ar rcs /build/stubs/armv7/libpthread.a; llvm-ar rcs /build/stubs/armv7/librt.a
   ```
2. **`CMAKE_BUILD_TYPE=Release` breaks xmrig's configure step** — a Release-only
   `POST_BUILD` strip command fails with `No TARGET 'xmrig' has been created`.
   Building as `RelWithDebInfo` with `-O3 -DNDEBUG` produces the same optimised
   code; strip afterwards with `llvm-strip`.

## Prerequisites

```sh
apt-get install -y build-essential cmake ninja-build autoconf automake \
                   libtool-bin pkg-config git curl unzip file
curl -fsSLO https://dl.google.com/android/repository/android-ndk-r26b-linux.zip
unzip -q android-ndk-r26b-linux.zip     # -> /build/android-ndk-r26b
```

## libuv (xmrig and XLArig depend on it)

```sh
git clone --depth 1 --branch v1.51.0 https://github.com/libuv/libuv.git
cd libuv
for ABI in arm64-v8a armeabi-v7a; do
  cmake -S . -B build-$ABI -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=$ABI -DANDROID_PLATFORM=android-24 \
    -DCMAKE_BUILD_TYPE=Release -DBUILD_TESTING=OFF -DLIBUV_BUILD_SHARED=OFF
  cmake --build build-$ABI -j"$(nproc)"
done      # -> build-<abi>/libuv.a
```

## XMRig

TLS is off, which keeps OpenSSL off the critical path. The cost is that
`stratum+ssl://` pools cannot be used; the app rejects them with a specific
message rather than failing obscurely.

```sh
git clone --depth 1 --branch v6.26.0 https://github.com/xmrig/xmrig.git
cd xmrig
# arm64-v8a: swap ARM_V8=ON -> "ARM_V7=ON -DARM_TARGET=7", the stub dir and the
# libuv path for armeabi-v7a.
cmake -S . -B build-arm64 -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24 \
  -DCMAKE_BUILD_TYPE=RelWithDebInfo \
  -DCMAKE_C_FLAGS_RELWITHDEBINFO="-O3 -DNDEBUG" \
  -DCMAKE_CXX_FLAGS_RELWITHDEBINFO="-O3 -DNDEBUG" \
  -DCMAKE_EXE_LINKER_FLAGS="-L/build/stubs/arm64" \
  -DARM_V8=ON -DBUILD_STATIC=OFF \
  -DWITH_HWLOC=OFF -DWITH_TLS=OFF -DWITH_HTTP=ON \
  -DWITH_OPENCL=OFF -DWITH_CUDA=OFF -DWITH_NVML=OFF -DWITH_ADL=OFF \
  -DWITH_MSR=OFF -DWITH_ASM=ON -DWITH_RANDOMX=ON -DWITH_ARGON2=ON \
  -DWITH_KAWPOW=OFF -DWITH_GHOSTRIDER=OFF \
  -DUV_INCLUDE_DIR=/build/libuv/include \
  -DUV_LIBRARY=/build/libuv/build-arm64-v8a/libuv.a
cmake --build build-arm64 -j"$(nproc)"
llvm-strip build-arm64/xmrig-notls
```

## XLArig

An xmrig 5.x fork, so the same recipe applies, with two differences that matter:

```sh
git clone --depth 1 --branch v5.2.4 https://github.com/scala-network/XLArig.git
cd XLArig
cmake -S . -B build-armv7 -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=armeabi-v7a -DANDROID_PLATFORM=android-24 \
  -DCMAKE_BUILD_TYPE=RelWithDebInfo \
  -DCMAKE_C_FLAGS_RELWITHDEBINFO="-O3 -DNDEBUG" \
  -DCMAKE_CXX_FLAGS_RELWITHDEBINFO="-O3 -DNDEBUG" \
  -DCMAKE_EXE_LINKER_FLAGS="-L/build/stubs/armv7" \
  -DARM_TARGET=7 -DBUILD_STATIC=OFF \
  -DWITH_HWLOC=OFF -DWITH_TLS=OFF -DWITH_HTTP=ON \
  -DWITH_OPENCL=OFF -DWITH_CUDA=OFF -DWITH_MSR=OFF -DWITH_ASM=ON \
  -DUV_INCLUDE_DIR=/build/libuv/include \
  -DUV_LIBRARY=/build/libuv/build-armeabi-v7a/libuv.a
cmake --build build-armv7 -j"$(nproc)"
```

1. **Do not pass `-DWITH_RANDOMX=OFF`.** It defaults ON and is what pulls in
   `src/crypto/randomx/panthera/*`. Turning it off silently drops panthera, which
   is the only reason this fork exists.
2. **Pass `-DARM_TARGET=7` explicitly for armeabi-v7a.** XLArig's `cpu.cmake` only
   auto-sets it from a `CMAKE_SYSTEM_PROCESSOR` regex match, so relying on
   auto-detection is fragile across NDK versions. On arm64 auto-detection from
   `aarch64` is reliable. `ARM_V8`/`WITH_CUDA`/`WITH_OPENCL` are accepted but
   unused here — this fork does not define those options.

As with xmrig, RandomX gets a real JIT on arm64 and falls back to the portable
interpreter on armeabi-v7a, so panthera hashes far slower on the 32-bit box.

## cpuminer-multi and the VerusHash miner

Both are autotools rather than CMake. Configure them with the NDK clang wrappers
and `--host=aarch64-linux-android` / `--host=armv7a-linux-androideabi`, adding
`-L/build/stubs/<abi>` to `LDFLAGS` if the pthread/rt link error appears.

VerusHash is `monkins1010/ccminer` branch `Verus2.2` at commit `e28e183`, with its
`verus/sse2neon` submodule. arm64 builds with `-march=armv8-a+crypto+aes`, which
gives real AES instructions. armv7 builds with `-march=armv7-a -mfpu=neon
-mfloat-abi=softfp -fno-strict-aliasing`.

## VerusHash on 32-bit ARM

The armeabi-v7a build needs `patches/0001-verus-armv7-16byte-alignment.patch`.
Without it the miner dies the instant a worker thread starts:

```
Fatal signal 7 (SIGBUS), code 1 (BUS_ADRALN), fault addr 0xed402428
```

`scanhash_verus()` allocates its key buffer with plain `malloc` and casts it to
`u128*` (`__m128i`, 16-byte aligned). On LP64 bionic `malloc` happens to return
16-byte-aligned blocks so arm64 is fine, but **on ILP32 it only guarantees 8** —
and the fault address above is 8 mod 16. The compiler, entitled to assume the
declared alignment, emits NEON accesses carrying `:128` qualifiers, and the
binary's `Tag_CPU_unaligned_access` covers plain LDR/STR only. The patch switches
that allocation to `posix_memalign(..., 16, ...)` and marks the two stack buffers
that feed the same hash core `__attribute__((aligned(16)))`.

No compiler flag fixes this: `-mno-unaligned-access` changes scalar codegen only
and does not affect NEON intrinsics or alignment inferred from a pointer's type.

**Do not try to build armv7 with `-march=armv8-a+crypto`** even though the X96Q's
Cortex-A53 reports `aes pmull` in `/proc/cpuinfo`. Defining `__ARM_FEATURE_CRYPTO`
makes the pinned sse2neon take its hardware-CLMUL path, which calls `vmull_p64` —
an intrinsic clang exposes only on AArch64 — and compilation fails. armv7
therefore runs sse2neon's software AES, measured at **57 kH/s per thread** on the
X96Q (`ccminer -a verus --benchmark -t 4`) versus hardware-AES speeds on arm64.

## Verifying a build before shipping it

Android refuses to execute a non-PIE binary, so check the ELF type and then run
it on the target device before wiring it into Gradle:

```sh
llvm-readelf -h build-arm64/xmrig-notls | grep -E 'Type:|Machine:'   # must be DYN
adb -s <device> push xmrig-arm64 /data/local/tmp/x
adb -s <device> shell 'chmod 755 /data/local/tmp/x; /data/local/tmp/x --version'
```

After installing the APK, confirm the packaged copy runs as the app itself,
which is the path the app actually uses:

```sh
adb -s <device> shell run-as com.frynetworks.pow \
  /data/app/<...>/lib/arm/libxmrig.so --version
```
