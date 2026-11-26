# 配置步骤

前提：Android 11+（开机自启需要 13+），Haven 里装好 Debian，Debian 里装好 Claude Code。

## 1. 无线调试配对（adb 连自己）

1. 设置 → 关于手机 → 连点“版本号”，打开开发者选项。
2. 打开“无线调试”。
3. `apt install adb`
4. 点“使用配对码配对设备”（用分屏，弹窗关掉配对码就失效）：
   ```bash
   adb pair 127.0.0.1:<配对端口> <配对码>
   adb connect 127.0.0.1:<主页面上的端口>     # 两个端口不一样
   ```
5. 可选：开发者选项里打开“停用 ADB 授权超时”。

`bin/phone-adb <端口>` 会连接、把端口固定为 5555，并在需要时重新启动 Shizuku。
注意：5555 端口对同一网络可见（仍需授权密钥），重启手机后失效。

## 2. Shizuku

1. 从[官方发布页](https://github.com/RikkaApps/Shizuku/releases)安装（开发时用的是 13.6.0）。
2. 在 Shizuku 里点 “Pairing”，按通知提示输入配对码，然后点 “Start”。
3. 开机自启（免 root）需要同时满足：Android 13+、连着受信任的 Wi-Fi、Shizuku 自己配对过、上次以 adb 方式启动、设置里开着开机启动，以及：
   ```bash
   adb shell pm grant moe.shizuku.privileged.api android.permission.WRITE_SECURE_SETTINGS
   ```
4. Haven 的终端里自带 `rish`，验证：`rish -c id` 应显示 `uid=2000(shell)`。

## 3. 本仓库

`./install.sh`，见 README。

## 4. 可选：Haven MCP

Haven 在本机提供 MCP 服务（读通知、传感器、终端会话、文件传输等）：

```bash
claude mcp add --transport http -s user haven http://127.0.0.1:8730/mcp
```

权限范围很大（含邮件、TOTP 验证码、相机、定位），按需接入。

## 撤销

```bash
rish -c '/data/local/tmp/touchlock unlock; pm uninstall com.local.claudepill'
rish -c 'settings delete global adb_allowed_connection_time'
claude mcp remove -s user haven
```

再从 `~/.claude/settings.json` 删掉 hooks（安装时备份为 `settings.json.bak`）。
