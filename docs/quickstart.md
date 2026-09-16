# JFA 快速开始

面向客户内网、JDK 8 Linux。数据不出域。

## 1. 诊断已有进程（第一步）

不必先改启动脚本。

```bash
./jfa                         # 列出可分析 PID
./jfa-analyze                 # 空跑：打印完整用法
./jfa-analyze --pid <pid>     # 默认全量 = 内存 + 线程
```

只看一侧：

```bash
./jfa-analyze --pid <pid> --type memory
./jfa-analyze --pid <pid> --type thread
./jfa-analyze --pid <pid> --type memory --compare-after 15m --confirm
```

## 2. 按服务名（可选登记，不托管生命周期）

```bash
java -jar lib/jfa.jar register --name order-svc --pid <pid> --app-log /var/log/order-svc/app.log
./jfa-analyze --service order-svc
```

登记不会启动或停止目标进程。

## 3. 只有落盘证据（进程已死）

```bash
./jfa-file-analyze            # 空跑：打印完整用法
./jfa-file-analyze --evidence-dir /path/to/evidence
./jfa-file-analyze --hprof ./heap/java_pid.hprof --gc-log ./gc/gc.log --type memory
./jfa-file-analyze --hprof ./heap/newer.hprof --hprof-prev ./heap/older.hprof --type memory
./jfa-file-analyze --thread-dump ./threads/td.txt --type thread
```

## 4. 活体采集（需确认）

```bash
./jfa-collect                 # 空跑：打印完整用法
./jfa-collect threaddump --pid <pid>
./jfa-collect heapdump --pid <pid> --confirm
./jfa-collect sample --pid <pid> --interval 5s --duration 60s --confirm
```

无确认时不会生成 hprof。诊断会优先复用目标 JVM 已有 hprof/GC，证据不足才采集。

## 5. 推荐 JVM 配置（可选，非使用前提）

```bash
./jfa help
./jfa-config
```

输出含 JDK 8 滚动 GC、`HeapDumpOnOutOfMemoryError`、`HeapDumpPath` 及 bash 启动示例（按配置项分组表格）。

## 6. Web 控制台（可选）

```bash
./jfa start
# 浏览器：http://<服务器IP>:<port>/jfa
# console.bind=0.0.0.0 表示监听所有网卡；127.0.0.1 仅本机
./jfa stop
```

## 7. 本地从源码打 Linux 包（Windows）

```bat
build-linux-package.bat
```

产物：`dist\jfa-1.0.0-linux-x86_64.tar.gz`，并复制到上一级目录。