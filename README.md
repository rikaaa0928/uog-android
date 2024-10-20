# UOG Android

UOG Android 是一个基于 gRPC 的 UDP 代理应用程序,专为 Android 设备设计。

## 功能特性

- 在 Android 设备上运行 UDP over gRPC 代理
- 支持多个配置管理
- 前台服务保持长期运行
- 网络状态监控和自动重连

## 技术栈

- Kotlin
- Android SDK
- gRPC
- Rust (用于核心代理逻辑)
- JNI (Java Native Interface)

## 项目结构

项目主要包含以下部分:

1. Android 应用程序 (Kotlin)
2. Rust 库 (核心代理逻辑)
3. JNI 绑定

主要的 Android 代码文件包括 MainActivity.kt, UotGrpc.kt 和 UogClient.kt。

## 构建和运行

### 前提条件

- Android Studio
- Rust 工具链
- NDK (Native Development Kit)
- Java Development Kit (JDK) 17

### 构建步骤

1. 克隆仓库:
   ```
   git clone --recurse-submodules https://github.com/your-repo/uog-android.git
   ```

2. 安装 Rust 工具链和目标:
   ```
   rustup target add aarch64-linux-android
   ```

3. 安装必要的 Cargo 工具:
   ```
   cargo install cargo-ndk
   cargo install cross --git https://github.com/cross-rs/cross
   ```

4. 构建 Rust 库:
   ```
   cd lib-src/uog
   cross build --target aarch64-linux-android --release --lib
   ```

5. 复制编译好的库文件:
   ```
   mkdir -p ../../app/src/main/jniLibs/arm64-v8a
   cp target/aarch64-linux-android/release/libuog.so ../../app/src/main/jniLibs/arm64-v8a/
   cd ../..
   ```

6. 在 Android Studio 中打开项目

7. 构建 APK:
   ```
   ./gradlew assembleRelease
   ```

8. 签名 APK (需要配置签名密钥):
   使用 Android Studio 或者命令行工具进行 APK 签名

### 自动构建

本项目使用 GitHub Actions 进行自动构建和发布。当推送带有 'v*' 标签的提交时,会触发构建流程:

- 检出代码和子模块
- 安装 Rust 和 NDK
- 构建 Rust 库
- 设置 JDK 环境
- 构建 Android APK
- 签名 APK
- 创建 GitHub Release 并上传签名后的 APK

详细的自动构建配置请参考项目根目录下的 `.github/workflows/apk.yml` 文件。

注意: 自动构建和发布需要正确配置 GitHub Secrets 以提供签名所需的密钥和密码。

## 配置

应用程序支持多个代理配置。每个配置包含以下字段:

- 监听端口
- 密码
- gRPC 端点

用户可以通过应用程序界面添加、编辑和删除配置。

## 贡献

欢迎提交 Pull Requests 来改进这个项目。对于重大变更,请先开 issue 讨论您想要改变的内容。

## 许可证

Apache 许可证 2.0

## 联系方式

rikaaa0928@gmail.com

