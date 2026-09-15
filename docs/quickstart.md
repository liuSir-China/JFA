# JFA 快速开始

面向客户内网、JDK 8 Linux。数据不出域。

## 1. 诊断已有进程（第一步）

不要执行 start，不要先改启动脚本。

```bash
jfa discover
jfa diagnose --pid <pid>
```

默认全量（内存 + 线程）。当前无 OOM、无死锁时，报告为**健康体检**：否定结论 + 可选风险提示，不会硬编根因。

只看一侧：

```bash
jfa diagnose --pid <pid> --type memory
jfa diagnose --pid <pid> --type thread
```

## 2. 按服务名（可选登记，不托管生命周期）

```bash
jfa register --name order-svc --pid <pid> --app-log /var/log/order-svc/app.log
jfa diagnose --service order-svc
```

`lifecycle_managed_by_jfa` 恒为 false。登记不会启动或停止目标进程。

## 3. 只有落盘证据（进程已死）

```bash
jfa analyze --evidence-dir /var/jfa/order-svc
jfa analyze --hprof ./heap/java_pid.hprof --gc-log ./gc/gc.log --type memory
jfa analyze --thread-dump ./threads/td.txt --type thread
```

有 hprof 时报告必须给出可行动修改建议（改什么 / 为什么 / 验证方式）。

## 4. 活体 dump（需确认）

heap dump 有 STW / 磁盘 / 服务影响。交互终端会询问 y/n；脚本请使用 `--confirm`（等同于回答 y）：

```bash
jfa collect threaddump --pid <pid>
jfa collect heapdump --pid <pid> --confirm
jfa collect sample --pid <pid> --interval 5s --duration 60s --confirm
```

无确认时不会生成 hprof、不会附加 jstat。`diagnose`/`analyze` 对活体 pid 会优先复用目标 JVM 已有 hprof/GC，证据不足才采集。

## 5. 抄作业：推荐 JVM 配置（可选，非前提）

```bash
jfa help
jfa help config
jfa config recommend
```

输出含 JDK 8 滚动 GC 文件日志、`HeapDumpOnOutOfMemoryError`、`HeapDumpPath` 以及 bash 启动脚本示例（按配置项分组表格）。

## 6. 打包布局

```bash
./scripts/package-linux.sh
./dist/jfa-linux/bin/jfa help
```
