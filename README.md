# YourApp — Kotlin Multiplatform

跨平台架构：Android + Desktop（含 macOS）共享一套 Kotlin 业务核心。

## 架构

```
your-app/
├── shared/                    ← 核心逻辑（KMP commonMain）
│   ├── domain/                实体定义（User, Task）
│   ├── data/                    Repository 接口
│   └── usecases/                业务用例（平台无关）
│
├── androidApp/                ← Android 端
│   └── ui/                      Jetpack Compose UI
│
└── desktopApp/                ← Desktop / macOS 端
    └── Main.kt                  Compose Desktop UI
```

**原则**：`shared` 写一次，两端复用。UI 各自平台自己画。

## 运行

### Android
```bash
./gradlew :androidApp:installDebug
```

### Desktop / macOS
```bash
./gradlew :desktopApp:run
```
打包 macOS `.dmg`：
```bash
./gradlew :desktopApp:packageDmg
```

## 后续加 Rust

当某个用例性能不够时（比如重计算、编解码）：

1. Rust 写核心算法 → 编译为动态库
2. `shared` 模块通过 JNI/JNA 调用
3. 接口不变，只换实现

参考注释见 `shared/build.gradle.kts` 中 iOS/macOS Native targets。

## Git

```bash
git log --oneline --graph
```

所有业务逻辑变更都在 `shared/` 提交，UI 变更各平台独立提交，历史清晰。
