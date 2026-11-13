# TE cvb

一个黑白主题的 Android 锁机 / 自律 / kiosk 工具。  
A black-and-white Android lock, self-discipline and kiosk app.

> **仅限自己的设备使用。** 不要用它锁别人的手机。  
> **For your own devices only.** Do not use it to lock someone else's phone.

## 功能

- 黑白全屏锁机界面，自定义 4–8 位数字密码
- 开始测试 / 模式选择 / 密码 / 悬浮窗一键锁机
- 锁机模式：
  - 普通锁机（应用内锁屏 + 屏幕固定）
  - 无障碍锁机（全屏覆盖，阻止 Home/最近任务/通知栏）
  - 悬浮窗锁机
  - Shizuku 锁机（自动开无障碍/悬浮窗、切桌面、冻结用户 App）
  - root 锁机（同上，使用 root）
  - 设备所有者锁机（官方 kiosk，防 Home/最近任务/卸载）
- 全冻结：Shizuku/root 锁机时冻结所有用户 App，解锁恢复
- SSH/SFTP 远程锁机（实验性）
- 社区入口

## 设备所有者 kiosk

设备所有者模式需要一台可以恢复出厂的测试机：

1. 备份并恢复出厂
2. 安装 APK，先不要登录账号
3. 开启 USB 调试，连接电脑
4. 执行：

```bash
adb shell dpm set-device-owner com.dsh.lock/.AdminReceiver
```

5. 打开 TE cvb → 模式 → 设备所有者锁机

彻底移除：

```bash
adb shell dpm remove-active-admin com.dsh.lock/.AdminReceiver
```

## 远程锁机（实验性）

两端使用**同一个 SSH 服务器、同一个账号、同一个 home 目录**，通过 SFTP 下的
`~/.tecvb` 目录交换文件：

- `devices/<id>.json`：在线状态
- `requests/<controlled-id>.json`：锁机请求
- `responses/<controlled-id>.json`：同意/拒绝结果

控制端扫描在线被锁机，保存后选择设备，再点“开始测试”发起请求。被锁机端必须
保持“允许被远程锁机”的前台服务运行，并手动同意请求。

限制：

- 远程功能尚未在真实 SSH 服务器上完整实测
- 在线判断依赖服务器文件时间与手机时钟，时钟偏差过大会误判离线
- 没有推送，靠前台服务轮询
- SSH 密码明文存储，主机密钥未校验
- 服务被系统杀掉后无法接收请求

## 构建

需要：

- JDK 8+（推荐 17）
- Android SDK Platform 30
- Android Build-Tools 30.0.3 和 34.0.0（34 的 d8 用于 JSch 0.2.26）
- Python 3

```bash
./build.sh
```

输出：`build/TE-cvb-unsigned.apk`

自行签名：

```bash
apksigner sign \
  --ks /path/to/your.keystore \
  --ks-key-alias your_alias \
  --out TE-cvb.apk \
  build/TE-cvb-unsigned.apk
```

GitHub Actions 也会构建未签名 APK，作为 artifact 上传。

## 项目结构

```text
src/                 Java 源码
res/                 资源
libs/shizuku/        Shizuku API / provider / AIDL / shared
libs/jsch-0.2.26-android.jar
AndroidManifest.xml
build.sh
.github/workflows/build.yml
```

## 已知限制

- 远程锁机是实验性功能，不是生产级远程控制
- Shizuku/root 模式依赖对应环境和授权
- 全冻结使用 `pm disable-user`；如果 TE cvb 被卸载/清数据，冻结的 App 可能保持禁用
- 设备所有者模式会阻止卸载，恢复需要 ADB
- 没有 root 系统分区、init.d、禁用 SystemUI/安装器等恶意锁机能力

## 社区

- QQ 官方群聊：201943803
- QQ 官方频道：pd17789932

## 许可证

MIT。第三方组件许可见 [NOTICE](NOTICE)。

本项目按“现状”提供，不提供任何担保。使用风险自负。
