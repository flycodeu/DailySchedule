# 日常计划 (DailySchedule)

轻量、简洁的 Android 阶段日程记录与提醒工具，支持每日多时段提醒确认、多阶段递增/递减/休整序列、月历视图与 GitHub Release 自动检查更新。

## 核心特性

- **通用多阶段模型**：可记录任意按天、按周、按月划分的日常安排（习惯、补剂、服药、训练等）。
- **序列快填**：支持 `2, 2, 2, 2, 0`、`2, 1, 1, 1, 0`、`2, 0, 2, 0` 等快速模板：
  - **每步用量**：每阶段使用固定时段，剂量随序列变化，`0` 自动识别为休整/停用阶段（不提醒）。
  - **每步次数**：每日提醒次数随序列变化。
  - **单日时段**：同一天内不同时间段不同数量（如早 2、午 0、晚 2、睡前 0）。
  - 支持**单次完成**或**循环重复**，以及按**天 / 周 / 月（自然月）**跨度。
- **日历与确认**：
  - 月历视图直观标记每天完成状态（全部完成 / 部分或待补 / 已排期 / 休息 0）。
  - 通知栏直接提供「已确认打卡」和「延后 15 分」操作按钮。
- **GitHub Release 通知与更新**：
  - 后台定期 + 手动检查 `releases/latest`，有新版本时发送通知。
  - App 内直接下载 APK（支持可选加速前缀）并调起系统安装。

## GitHub Release 自动发布说明

推送任意 `v*` 标签（例如 `git tag v1.0.0 && git push origin v1.0.0`）或在 Actions 中手动触发 **Android Release**，即可自动运行单元测试、编译压缩版 APK 并发布到 GitHub Releases。

### 配置固定签名（推荐，确保后续版本可直接覆盖安装）

Android 要求新旧版本 APK 签名一致才能直接升级覆盖。建议生成一次签名证书并写入仓库 Secrets：

```bash
keytool -genkeypair -v -keystore release.jks -alias dailyschedule -keyalg RSA -keysize 2048 -validity 10000
```

在 GitHub 仓库 `Settings -> Secrets and variables -> Actions` 添加以下 Secrets：
- `SIGNING_KEYSTORE_BASE64`：`release.jks` 的 Base64 编码
- `SIGNING_STORE_PASSWORD`：keystore 密码
- `SIGNING_KEY_ALIAS`：`dailyschedule`
- `SIGNING_KEY_PASSWORD`：key 密码

## 本地编译

```powershell
$env:JAVA_HOME = "D:\Developer Tools\JDK17"
.\gradlew.bat testDebugUnitTest assembleRelease
```
