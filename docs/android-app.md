# Android APP

## 当前实现

- 应用名：自贡能发；包名：`com.zgnf.shop`。
- Vue 商城内嵌在 Capacitor 7 原生 Android 工程中，复用真实登录、商品、购物车、下单、订单、地址和售后接口。
- 返回键：二级页面返回，底部导航主页面确认退出；页面可以通过取消 `app:back` 事件优先关闭编辑器。
- 原生网络状态提示、品牌启动页、状态栏、键盘和底部安全区域适配。
- 正式包禁止 HTTP、演示模式和跳过登录；调试包可明确开启 HTTP 联调。
- 访问令牌仍使用现有会话存储，不增加持久化登录；重启后可能需要重新登录。

## 环境

安装 Node.js 22.12+、JDK 21、Android Studio、Android SDK Platform 35 和对应 Build Tools。在 Android Studio SDK Manager 安装并接受 Android SDK 许可。将 `JAVA_HOME` 指向 JDK 21，将 `ANDROID_HOME` 指向 SDK；也可在 `frontend/android/local.properties` 设置 `sdk.dir`。目前 Gradle 8.11.1 不支持使用 JDK 25 运行。

支持将 JDK 解压到 `frontend/.jdk/<JDK目录>/`，将 SDK 安装到 `frontend/.android-sdk/`。打包脚本优先使用这两个项目本地目录，不修改系统环境变量。工具目录、下载包和本机 SDK 路径不会提交到 Git。

本机已安装 SDK Platform 35、Build Tools 35.0.0、Platform Tools 和 Microsoft OpenJDK 21；Gradle 8.11.1 已用官方 SHA-256 校验并解压到 `frontend/.gradle-runtime/gradle-8.11.1/`，打包脚本会优先使用该目录。

## 本次调试包验证（2026-11-09）

已成功生成 `frontend/android/app/build/outputs/apk/debug/app-debug.apk`，版本 1.0，包名 `com.zgnf.shop`，最低 Android 6.0（API 23），目标 API 35。APK v1/v2 签名校验通过，大小 4,539,823 字节。

此包临时使用 `http://10.0.2.2:8081/api/v1`，仅供 Android 模拟器连接电脑本机后端；没有开启演示模式或跳过登录。尚未进行真机安装和业务联调，真机使用前需要填入可访问的实际 API 地址并重新打包。

首次构建遇到 Windows 长路径下 Gradle 资源缓存目录移动失败，补齐对应缓存后编译成功。若其他 Windows 机器出现相同错误，应优先将项目放在更短的路径，检查文件占用，并以较低并发重试；不要删除业务文件。

## 连接后端

复制 `frontend/.env.android.example` 为 `frontend/.env.android.local`，填写真实 API 地址。例如：

```dotenv
VITE_API_BASE_URL=https://实际域名/api/v1
VITE_DEMO_MODE=false
VITE_LOGIN_DISABLED=false
ANDROID_ALLOW_HTTP=false
```

Android WebView 本地来源为 `https://localhost`。后端默认 CORS 已包含该来源；如果部署环境覆盖 `app.cors.allowed-origins`，需要自行将其加入允许列表。图片、上传文件和地图服务地址也必须可从手机访问，正式环境使用 HTTPS。前端构建变量是公开配置，不得填写商户私钥。

模拟器连接电脑后端可用 `http://10.0.2.2:8081/api/v1`，真机使用电脑局域网 IP，并设置 `ANDROID_ALLOW_HTTP=true`。电脑和手机需在同一网络，后端端口需要允许访问。这仅用于调试，正式包始终禁止 HTTP。

```powershell
cd frontend
npm ci
npm run app:sync
npm run app:android
# 或直接构建调试安装包
npm run app:apk
```

调试包输出：`frontend/android/app/build/outputs/apk/debug/app-debug.apk`。连接设备后可用 `adb install -r` 安装。`app:sync` 仅同步资源，`app:apk` 执行 Gradle 构建。

## 正式签名

使用 Android Studio 的 Generate Signed Bundle / APK 创建并妥善保存签名密钥。在 `frontend/android/keystore.properties` 填写：

```properties
storeFile=签名文件的绝对路径（Windows 建议使用正斜杠）
storePassword=实际密码
keyAlias=实际别名
keyPassword=实际密码
```

运行 `npm run app:release`，输出 `android/app/build/outputs/bundle/release/app-release.aab`。密钥和密码配置已被 Git 忽略；后续更新必须沿用相同签名。每次发布递增 `android/app/build.gradle` 中的 `versionCode` 并更新 `versionName`。不同商店的目标 SDK、隐私政策及备案要求需按提交时规则处理。

## 联调与发布前验收

使用真实测试账号和设备检查：注册登录、登录过期、商品图片、购物车、地址、新建订单、取消订单、确认收货、售后申请；同时测试返回键、软键盘、断网恢复、后台恢复及不同屏幕尺寸。定位地图需配置高德 Key 并允许 WebView 来源，现有地址表单可手动填写。

当前没有接入原生微信/支付宝支付、渠道退款和推送。接入这些能力需要实际支付商户、应用注册信息、签名指纹、服务端支付下单/回调和推送服务配置；不能以本地按钮或模拟成功代替真实交易。发布前还需提供实际运营主体、隐私政策和用户协议，并完成真机业务联调。
