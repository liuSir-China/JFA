# JFA（JVM Forensics & Diagnostics Assistant）

## 五个命令（Linux 安装包 bin/）

| 命令 | 作用 |
| --- | --- |
| `jfa` | 列出可分析的 Java 进程（原 `jfa discover`）。`jfa --help` / `jfa help` → 推荐 JVM 配置。`jfa start` / `jfa stop` → Web 控制台 |
| `jfa-analyze` | 活体诊断（原 `jfa diagnose`）。无参数打印中文参数说明 |
| `jfa-file-analyze` | 离线分析（原 `jfa analyze`）。无参数打印中文参数说明 |
| `jfa-collect` | 采集 heapdump/sample/threaddump。无参数打印中文参数说明 |
| `jfa-config` | 推荐 JVM 配置（原 `jfa help config`） |

诊断/分析结束后控制台**不**打印报告正文，只打印：

```
======== 报告已写入 ========
报告文件: <绝对路径>.md
JSON 报告: <绝对路径>.json
```

可选 Web 控制台（监控卡片）：

```bash
./jfa start
# 浏览器 http://<ip>:<port>/jfa   （conf/jfa.properties 顶部 console.port / console.bind）
./jfa stop
```

内网 JVM 故障诊断助手。面向 **JDK 8** Linux 现场：对**已运行或已故障**的 Java 服务采集/利用本地证据，输出可执行的研发修改建议（text + JSON）。覆盖 **Java 堆 OOM** 与 **线程死锁**，并支持无异常时的一键健康体检。数据默认不出域。

有 hprof 时本产品独立给出可行动结论，不以任何外部 hprof 查看工具为交付补充。

## 主路径（第一步）

不要先 start、不要先改启动参数。直接诊断已有进程：

```bash
jfa discover
jfa diagnose --pid <pid>
```

无 OOM / 无死锁时，默认全量诊断即为健康体检（内存 + 线程），结论为否定故障 + 采样/日志窗口判读。产品自己采样、倒查日志、对比 hprof。

## 三种分析模式

| 命令 | 行为 |
|------|------|
| `jfa diagnose --pid <pid>` | 默认全量 = memory + thread（与 `--type auto` 等价） |
| `jfa diagnose --pid <pid> --type memory` | 只分析内存 |
| `jfa diagnose --pid <pid> --type thread` | 只分析线程（含死锁） |

进程已死、仅有落盘证据：

```bash
jfa analyze --evidence-dir /var/jfa/order-svc --type auto
jfa analyze --hprof /path/a.hprof --gc-log /path/gc.log --type memory
jfa analyze --hprof /path/newer.hprof --hprof-prev /path/older.hprof --type memory
jfa analyze --thread-dump /path/td.txt --type thread
jfa diagnose --pid <pid> --type memory --compare-after 15m --confirm
```

## 构建（JDK 8）

```bash
export JAVA_HOME=/path/to/jdk8
mvn test package
./scripts/package-linux.sh
./dist/jfa-linux/bin/jfa help
```

源码与字节码均为 **1.8**。运行分析端与目标进程均以 JDK 8 为主。

## 推荐 JVM 配置（可选证据增强，不是使用前提）

```bash
jfa help config
jfa config recommend
```

对已运行的 JDK 8 进程**不能**动态追加与启动参数等价的完整 GC 文件日志，也**不能**动态打开与 `HeapDumpOnOutOfMemoryError` 等价的未来自动 dump。补参数只提高**下次**事后归因上限。

## 模块

- `jfa-common` — 错误码、报告 schema 2.1、配置
- `jfa-core` — 发现/登记/采集/死锁引擎/OOM E0–E3/采样/日志倒查/堆对比/报告
- `jfa-cli` — Linux CLI 与内置 Web 控制台（`com.sun.net.httpserver.HttpServer`）

## 文档

- [快速开始](docs/quickstart.md)
- [用户手册](docs/user-manual.md)

## 许可

仅供客户内网交付与现场诊断使用。
