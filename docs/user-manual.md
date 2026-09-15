# JFA 用户手册

## 定位

JFA 分析**已有** Java 服务的本地证据，输出研发可直接据此修改的报告。主价值链不是托管启动，也不是强制追加 JVM 参数。

## 权限

`discover` 需要能读 `/proc`。`jstack` / `jcmd` / `jmap` 通常要求与目标 JVM **同一操作系统用户**（或具备等价 attach 权限）。无权时返回 `E_PERM_DISCOVERY` / `E_PERM_ATTACH`，并提示换用户，不会静默失败。

## 证据与报告目录

`diagnose` / `analyze` **只**把本轮证据与报告写到安装目录下：

```
<install>/reportfile/pid_<pid>/<yyyyMMdd-HHmmss>/
  heap/
  threads/
  gc/
  samples/
  logs/
  diagnose-<timestamp>.md
  diagnose-<timestamp>.json
```

无 pid 时使用 `pid_offline`（或从证据路径解析出的 pid）。`cover.file=true` 时会先清空该 pid 目录再写本轮；`false` 保留旧时间戳目录。

复用外部已有 hprof / GC / thread dump / 应用日志时，会 **copy 或硬链接** 进本轮目录，报告路径指向运行目录内文件。`retention.days` 只清理 `reportfile` 下产品托管的运行目录，不删除应用自己的 dump/GC。

登记服务的 `meta.json` 默认在 `<install>/registry/<service_id>/`（可用 `--evidence-dir` 指向应用侧目录，仅作只读索引）。

## 报告

闭环短报告结构：

1. 结论（线程 + 内存 + 日志窗口 + 采样 + 堆对比）
2. 内存采样表 + 判读（若执行）
3. 日志倒查（若执行）
4. 堆结构要点 / 堆对比（若有 hprof 对比）
5. 证据清单（均在本轮运行目录）

健康体检保持短（结论 + 时间线 + 证据）。所有报告都没有第 7 节免责声明。故障报告不堆“请研发自行…”清单；建议必须是产品可执行动作或紧扣产品发现。

- schema：`report_schema_version: "2.1"`
- 双格式：text + json
- 分析模式：`memory` | `thread` | `auto`
- 报告模式：`fault` | `health_check`
- 有 hprof（E3）必须可行动；禁止把外部 hprof GUI 写成下一步
- `fabricated_root_cause` 恒为 false；缺证据不编造业务根因

## 证据级别（Java 堆）

| 级别 | 条件 | 产出 |
|------|------|------|
| E3 | 有 hprof | Top 类、保留路径嫌疑、可执行修改建议 |
| E2 | 仅 GC 日志 | 趋势；明确不能精确对象归因 |
| E1 | 仅 OOM 栈 | 弱结论 + 缺失清单 |
| E0 | 无有效证据 | 否定/缺失 + 下一步最小操作 |

## 产品执行的采样、日志倒查与堆对比

活体 `--pid` 的 memory/auto 诊断会跑短时 `jstat -gcutil`（`sample.interval.seconds` × `sample.count`，默认 5s×8），原始输出在 `samples/`，报告解释 Eden/Survivor/Old（及 Metaspace）趋势。

应用日志倒查默认从分析时刻往前 10 分钟（`log.lookback.minutes`）。路径来自 `--app-log`、已登记服务或 JVM 命令行；解析不到时只提示补 `--app-log`。

双 hprof 对比：

```bash
jfa diagnose --pid <pid> --type memory --compare-after 15m --confirm
jfa analyze --hprof newer.hprof --hprof-prev older.hprof --type memory
```

活体路径：dump1（复用已有可用 hprof，否则采集）→ 采样+日志倒查 → 等待 → dump2 → 本产品 diff。一次 `--confirm` 覆盖两次 dump。进程在等待中退出时保留 dump1 并按单快照分析。

## 活体取证

默认对 heap dump 先打印 STW、磁盘与服务影响说明，并要求输入 `y`/`n`。脚本与 CI 可传 `--confirm`（等同于回答 `y`）。thread dump 与诊断内短时 jstat 采样不走该危险确认。不依赖交易时段配置。GC/hprof 从不替代 thread dump。

## 磁盘与清理

默认保留 7 天。空间不足时拒绝 heap dump（`E_DISK_FULL`），对已有小证据的只读分析仍进行。

```bash
jfa evidence gc --service order-svc --dry-run
jfa evidence gc --service order-svc
```

## 错误码

见 CLI：`ERROR <code>: <message>`。JSON 模式下为 `{"error_code","message"}`。常用：`E_PID_NOT_FOUND=10`，`E_CONFIRM_REQUIRED=30`，`E_NO_THREAD_DUMP=40`，`E_DISK_FULL=50`。

部分成功（例如线程侧成功、内存侧证据弱）时退出码为 0，报告内 `sections[].status` 分段标注。

## 数据范围

JFA 仅在本机读写证据与报告，无上传、无外发能力。

## 测试数据

仓库 `testdata/` 含死锁 jstack、GC 螺旋日志、OOM 栈、健康 dump 与分级证据目录。E3 hprof 由测试在 JDK 8 上现场生成无界缓存样例。
