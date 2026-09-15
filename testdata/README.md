# testdata

Fixtures for JFA engines and CLI tests (no production dumps).

- `threads/deadlock-jstack.txt` — JVM deadlock ring (TransferService / AccountService)
- `threads/healthy-jstack.txt` — no deadlock
- `gc/old-gen-spiral.log` — JDK 8 Full GC / old-gen rise
- `gc/healthy.log` — quiet GC
- `app/oom-heap-space.log` — Java heap space stack
- `evidence/order-svc-e3|e2|e1|e0` — evidence-level layouts
- `evidence/health-check` — healthy thread dump only

E3 binary hprof is generated at test time (unbounded ConcurrentHashMap + byte[]).
