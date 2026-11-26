# haven-claude-setup

让运行在手机上的 Claude Code 控制这台手机本身：Android 上的 [Haven](https://github.com/GlassHaven/Haven) 终端 → Debian (proot) → Claude Code → [Shizuku](https://github.com/RikkaApps/Shizuku)。**不需要 root，不需要电脑。**

## 包含什么

| 目录 | 内容 |
|---|---|
| `pill/` | **Claude Pill**：显示 Claude 状态的悬浮胶囊（可拖动、可收纳到侧边） |
| `touchlock/` | Claude 操作屏幕时独占触摸屏，手指失效，模拟点击照常 |
| `bin/` | `status.py`（hooks 入口）、`phone-shot`（不带胶囊的截屏）、`phone-adb`（adb 重连）、`ui-texts` |
| `claude/` | hooks 配置、状态栏脚本、自动启动片段、记忆笔记模板 |
| `docs/` | [配置步骤](docs/setup.md)、[胶囊与锁屏](docs/pill.md)、[踩过的坑](docs/pitfalls.md) |
| `tests/` | 开发时用的手动测试脚本（会真的操作屏幕，坐标按 1080×2400 竖屏写的） |

## 安装

先按 [docs/setup.md](docs/setup.md) 装好并启动 Shizuku，然后：

```bash
git clone https://github.com/kurashizu/haven-claude-setup
cd haven-claude-setup
./install.sh                 # 胶囊、锁屏、hooks
./install.sh --statusline --autostart --memory   # 可选项
```

安装后重启 Claude Code。仓库目录就是运行目录，装好后不要移动或删除。

## 安全须知（请先读）

- Shizuku 运行时，这个 Debian 里的**任何程序**都能取得 Android shell 权限（截屏、模拟点击、装卸应用、改系统设置）。
- 锁屏功能会让触摸屏失灵。保险：**任意音量键立即解锁**，每步最长 90 秒自动解锁。请先确认音量键解锁有效。
- 开机自启 Shizuku 会自动打开无线调试。
- 仅用于你自己的设备。按原样提供，风险自负。

## 验证状态

只在一台 Android 16 手机上开发和使用过。

- **`install.sh` 还没有在全新环境完整运行过。**
- 手机重启后 Shizuku 自启：未验证。
- 往左侧收纳、收纳震动、锁定状态下按小圆按钮：未单独验证。
- 横屏时，锁定状态下的按钮点击区域可能对不上（用音量键）。
