# Security

TE cvb is a self-lock / kiosk tool. It is intended for devices you own.

## 远程模块（4.0 已加固）

4.0 之前存在的三个问题，现在都改了：

- **SSH 密码、私钥明文落盘** → 改为用 Android Keystore 里的 AES-256-GCM
  密钥加密后再写 SharedPreferences（`SecretVault`）。旧版本留下的明文值在
  第一次保存时自动升级成密文；密钥库不可用时退化成 `pl1:` 弱加密并在界面上
  明确告警。
- **不校验 SSH 主机公钥** → 改为 TOFU：第一次连接记录服务器公钥的
  SHA-256 指纹，之后每次连接都比对，公钥变了直接拒绝并提示。设置页可以
  查看指纹、手动清除（确认服务器真换过密钥时用）。
- **请求可以被服务器上的任何写入方伪造** → 请求和回复都带
  HMAC-SHA256 签名，密钥是被控端的 6 位配对码，由用户自己口头交换。没签名
  或签名对不上的请求仍会弹出，但会标成「未配对」，同意按钮也改成
  「仍然同意并锁机」，用户看得见风险。

同时收紧的：

- 设备 ID、请求 ID 一律过白名单校验（`[A-Za-z0-9_-]{1,48}`），不能再通过
  SFTP 路径穿越写到 `.tecvb/` 之外。
- 请求 ID 从 12 位十六进制（48 bit）提升到 32 位（128 bit），猜不出来。
- 已处理的请求 ID 记进「最近 32 条」的集合，重放同一请求不再生效。
- 被控端每分钟最多弹 3 条请求通知，防止刷请求轰炸。

## 仍然存在的风险

- 配对码是 6 位数字，只能防「服务器上的其他设备乱发」，不能防能读到你
  屏幕或暴力试的人。介意的话每次用完换一个。
- 所有设备共用一个 SSH 账号，服务器管理员能看到 `.tecvb/` 里的所有
  JSON（里面没有密码，只有设备名、时间戳和请求）。要更隔离得给每台设备
  单独建号。
- 冻结应用仍然用 `pm disable-user`。TE cvb 被卸载或清数据时，被冻结的应用
  可能留在禁用状态。
- 设备所有者模式仍然阻止卸载、隐藏桌面和最近任务，退出需要 ADB 或恢复出厂。
- Shizuku / root 模式会执行 shell 命令，只给信任的构建授予。

## Reporting

Open a private security advisory on GitHub, or contact the maintainers through
the community links in README.md. Do not include passwords, private keys, or
device identifiers in public issues.
