# JFA 用户手册

## 定位

JFA 分析**已有** Java 服务的本地证据，输出研发可直接据此修改的报告。主价值链不是托管启动，也不是强制追加 JVM 参数。

## 权限

`discover` 需要能读 `/proc`。`jstack` / `jcmd` / `jmap` 通常要求与目标 JVM **同一操作系统用户**（或具备等价 attach 权限）。无权时返回 `E_PERM_DISCOVERY` / `E_PERM_ATTACH`，并提示换用户，不会静默失败。

## 证据与报告目录

登记服务时的 JFA 工作区（`evidence.root`，不是应用的 HeapDumpPath）：

```
{evidence_root}/{service_id}/
  meta.json
  gc/
  heap/
  threads/
  reports/
  samples/
```

`diagnose` / `analyze` 默认把本轮证据与报告写到安装目录下：

```
<install>/reportfile/pid_<pid>/<yyyyMMdd-HHmmss>/
```

无 pid 时使用 `pid_offline`（或从证据路径解析出的 pid）。`cover.file=true` 时会先清空该 pid 目录再写本轮。

外部大文件可在 `meta.json` 中记录绝对路径，分析时只读打开，不强制拷贝。应用自己的 dump/GC 不在 JFA 管理范围内，retention 不会删除它们。

## 报告

- schema：`report_schema_version: "2.1"`
- 双格式：text + json
- 分析模式：`memory` | `thread` | `auto`
- 报告模式：`fault` | `health_check`
- 建议四类：代码 / 配置 / 容量 / 运维；每条含 what / why / how_to_verify
- 有 hprof（E3）必须可行动；禁止把外部 hprof GUI 写成下一步
- `fabricated_root_cause` 恒为 false；缺证据不编造业务根因

## 证据级别（Java 堆）

| 级别 | 条件 | 产出 |
|------|------|------|
| E3 | 有 hprof | Top 类、保留路径嫌疑、可执行修改建议 |
| E2 | 仅 GC 日志 | 趋势；明确不能精确对象归因 |
| E1 | 仅 OOM 栈 | 弱结论 + 缺失清单 |
| E0 | 无有效证据 | 否定/缺失 + 下一步最小操作 |

## 活体取证

默认对 heap dump / jstat 采样先打印 STW、磁盘与服务影响说明，并要求输入 `y`/`n`。脚本与 CI 可传 `--confirm`（等同于回答 `y`）。thread dump 采集不走该危险确认。不依赖交易时段配置。

## 磁盘与清理

默认保留 7 天。空间不足时拒绝 heap dump（`E_DISK_FULL`），对已有小证据的只读分析仍进行。

```bash
jfa evidence gc --service order-svc --dry-run
jfa evidence gc --service order-svc
```

## 错误码

见 CLI：`ERROR <code>: <message>`。JSON 模式下为 `{"error_code","message"}`。常用：`E_PID_NOT_FOUND=10`，`E_CONFIRM_REQUIRED=30`，`E_NO_THREAD_DUMP=40`，`E_DISK_FULL=50`。

部分成功（例如线程侧成功、内存侧证据弱）时退出码为 0，报告内 `sections[].status` 分段标注。

## 不出域

默认 `outbound.enabled=false`。诊断流程不上传 dump。可选导出仅打本地包。

## 测试数据

仓库 `testdata/` 含死锁 jstack、GC 螺旋日志、OOM 栈、健康 dump 与分级证据目录。E3 hprof 由测试在 JDK 8 上现场生成无界缓存样例。
