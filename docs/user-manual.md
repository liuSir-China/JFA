# JFA 用户手册

面向客户内网、**JDK 8** Linux。分析已有 Java 服务的本地证据，输出可据此改代码/配置的报告。数据不出域，无上传。

## 1. 安装包命令一览

解压 `jfa-1.0.0-linux-x86_64.tar.gz` 后，日常只用 `bin/` 下这些入口（不要直接跑 `jfa-launcher.sh`，那是五个命令共用的启动脚本）：

| 命令 | 作用 |
|------|------|
| `./jfa` | 列出可分析的 Java 进程（原 `discover`） |
| `./jfa help` / `./jfa --help` | 推荐 JVM 配置（原 `help config` / `config recommend`） |
| `./jfa-analyze` | 活体诊断（原 `diagnose`） |
| `./jfa-file-analyze` | 离线分析落盘证据（原 `analyze`） |
| `./jfa-collect` | 只采集、不自动分析（原 `collect`） |
| `./jfa-config` | 与 `./jfa help` 相同：推荐 JVM 配置 |
| `./jfa start` / `./jfa stop` | 启停 Web 控制台 |

空跑 `./jfa-analyze`、`./jfa-file-analyze`、`./jfa-collect` 会打印各自**完整用法与参数中文说明**（先空两行，标题居中），不会直接报缺少 `--pid`。

## 2. 权限

发现进程需要能读 `/proc`。`jstack` / `jcmd` / `jmap` 通常要求与目标 JVM **同一操作系统用户**（或具备等价 attach 权限）。无权时返回 `E_PERM_DISCOVERY` / `E_PERM_ATTACH`，并提示换用户，不会静默失败。

## 3. 证据与报告目录

每轮 `jfa-analyze` / `jfa-file-analyze` 的证据与报告都落在安装目录下：

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

无 pid 时使用 `pid_offline`（或从证据路径解析）。`cover.file=true` 时先清空该 pid 下旧目录再写本轮；`false` 则保留旧时间戳目录。

复用外部已有 hprof / GC / thread dump / 应用日志时，会 **copy 或硬链接** 进本轮目录。`retention.days` 只清理 `reportfile` 下产品托管的运行目录，不删除应用自己的 dump/GC。

登记服务的 `meta.json` 默认在 `<install>/registry/<service_id>/`（可用 `--evidence-dir` 指向应用侧目录作只读索引）。

## 4. 报告

闭环短报告结构：

1. 结论（线程 + 内存 + 日志窗口 + 采样 + 堆对比）
2. 内存采样表 + 判读（若执行）
3. 日志倒查（若执行）
4. 堆结构要点 / 堆对比（若有 hprof 对比）
5. 证据清单（均在本轮运行目录）

健康体检保持短（结论 + 时间线 + 证据）。所有报告都没有第 7 节免责声明。故障报告不堆“请研发自行…”清单。

- schema：`report_schema_version: "2.1"`
- 双格式：text + json
- 分析模式：`memory` | `thread` | `auto`
- 报告模式：`fault` | `health_check`
- 有 hprof（E3）必须给出可行动建议；禁止把外部 hprof GUI 写成下一步
- `fabricated_root_cause` 恒为 false；缺证据不编造业务根因

控制台**不打印报告正文**：结束后只打印绝对路径（`报告文件` / `JSON 报告`）。

## 5. 证据级别（Java 堆）

| 级别 | 条件 | 产出 |
|------|------|------|
| E3 | 有 hprof | Top 类、保留路径嫌疑、可执行修改建议 |
| E2 | 有 GC 日志 | 趋势；明确不能精确对象归因 |
| E1 | 有 OOM 栈 | 弱结论 + 缺失清单 |
| E0 | 无有效证据 | 否定/缺失 + 下一步最小操作 |

## 6. 采样、日志倒查与堆对比

活体 `--pid` 且 memory/auto 时会跑短时 `jstat -gcutil`（`sample.interval.seconds` × `sample.count`，默认 5s×8），原始输出在 `samples/`。

应用日志倒查默认从分析时刻往前 10 分钟（`log.lookback.minutes`）。路径来自 `--app-log`、已登记服务或 JVM 命令行；解析不到时只提示补 `--app-log`。

双 hprof 对比：

```bash
./jfa-analyze --pid <pid> --type memory --compare-after 15m --confirm
./jfa-file-analyze --hprof newer.hprof --hprof-prev older.hprof --type memory
```

活体路径：dump1（复用已有或采集）→ 采样+日志倒查 → 等待 → dump2 → 本产品 diff。一次 `--confirm` 覆盖两次 dump。

## 7. 控制台进度

默认在 **stderr** 打印 `[JFA]` 分步进度（解析目标、运行目录、日志倒查、jstat、hprof 复用/采集、线程分析、compare 等待、写报告）。`--format json` 时 stdout 仍是纯 JSON，进度与路径在 stderr。`--quiet` 只关闭中途步骤，仍打印最终报告路径。`--verbose` 可附加更细的路径/体积信息。

## 8. 活体取证

heap dump 前会说明 STW、磁盘与服务影响，并要求输入 `y`/`n`。脚本/CI 用 `--confirm`（等同于 `y`）。thread dump 与诊断内短时 jstat 采样不走该危险确认。GC/hprof 从不替代 thread dump。优先复用目标 JVM 已有 hprof/GC，证据不足才活体采集。

## 9. 磁盘与清理

默认保留 7 天。空间不足时拒绝 heap dump（`E_DISK_FULL`）；对已有小证据的只读分析仍可进行。

```bash
# java -jar 兼容子命令（测试/脚本）
java -jar lib/jfa.jar evidence gc --service order-svc --dry-run
```

## 10. 错误码

纯 CLI：`ERROR <code>: <message>`。JSON 模式为 `{"error_code","message"}`。常用：`E_PID_NOT_FOUND=10`，`E_CONFIRM_REQUIRED=30`，`E_NO_THREAD_DUMP=40`，`E_DISK_FULL=50`，`E_USAGE`（参数不完整）。

部分成功（例如线程侧成功、内存侧证据弱）时退出码仍为 0，报告内 `sections[].status` 分段标注。

## 11. Web 控制台

```bash
./jfa start
# 浏览器：http://<服务器IP>:<port>/jfa
./jfa stop
```

配置在 `conf/jfa.properties` 顶部：

| 配置 | 含义 |
|------|------|
| `console.port=8080` | HTTP 端口 |
| `console.bind=0.0.0.0` | 监听地址。`0.0.0.0` = 所有网卡（局域网可用服务器 IP 访问）；`127.0.0.1` = 仅本机 |

PID 文件：`<install>/run/jfa-console.pid`。页面顶部显示「已分析 N · 未分析 M」；蓝卡打开最新报告；灰卡确认后等价于对该 pid 执行 `jfa-analyze`（UI 确认 = `--confirm`）；分析中卡片转圈且不可点。

## 12. 配置项摘要（`conf/jfa.properties`）

| 配置 | 作用 |
|------|------|
| `console.port` / `console.bind` | Web 控制台端口与监听地址 |
| `reportfile.root`（可选） | 报告根目录，默认 `<install>/reportfile` |
| `retention.days` | 只清理 JFA 自己的 reportfile 运行目录 |
| `min.free.bytes` / `min.free.ratio` | 磁盘守卫，不足时拒绝大写入（heap dump） |
| `cover.file` | `true` 清空同 pid 旧目录；`false` 按新时间戳保留 |
| `sample.interval.seconds` / `sample.count` | jstat 采样间隔与次数 |
| `log.lookback.minutes` | 应用日志倒查窗口 |
| `compare.top.n` | 双 hprof 对比表展示的 Top N 类 |

推荐 JVM 启动参数请直接看：`./jfa help` 或 `./jfa-config`。

## 13. 测试数据

仓库 `testdata/` 含死锁 jstack、GC 螺旋日志、OOM 栈、健康 dump 与分级证据目录。E3 hprof 由测试在 JDK 8 上现场生成。