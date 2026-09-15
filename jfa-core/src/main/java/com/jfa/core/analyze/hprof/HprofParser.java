package com.jfa.core.analyze.hprof;

import com.jfa.common.ErrorCode;
import com.jfa.common.JfaException;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HotSpot HPROF 1.0.1 / 1.0.2 reader: histogram, static-field hints, leak-pattern suspects.
 */
public class HprofParser {
    private static final int STRING = 0x01;
    private static final int LOAD_CLASS = 0x02;
    private static final int HEAP_DUMP = 0x0C;
    private static final int HEAP_DUMP_SEGMENT = 0x1C;
    private static final int HEAP_DUMP_END = 0x2C;
    private static final int CLASS_DUMP = 0x20;
    private static final int INSTANCE_DUMP = 0x21;
    private static final int OBJ_ARRAY = 0x22;
    private static final int PRIM_ARRAY = 0x23;

    public HprofSummary parse(File file) {
        if (file == null || !file.isFile()) {
            throw new JfaException(ErrorCode.E_HPROF_INVALID, "hprof 不存在: " + file);
        }
        if (file.length() < 32) {
            throw new JfaException(ErrorCode.E_HPROF_INVALID, "hprof 过小或损坏: " + file);
        }
        DataInputStream in = null;
        try {
            in = new DataInputStream(new BufferedInputStream(new FileInputStream(file), 1 << 16));
            return parseStream(in, file.length());
        } catch (JfaException e) {
            throw e;
        } catch (Exception e) {
            throw new JfaException(ErrorCode.E_HPROF_INVALID, "无法解析 hprof: " + e.getMessage(), e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
    }

    private HprofSummary parseStream(DataInputStream in, long fileLen) throws IOException {
        StringBuilder header = new StringBuilder();
        int b;
        while ((b = in.read()) > 0) {
            header.append((char) b);
        }
        if (b < 0) {
            throw new IOException("truncated header");
        }
        String h = header.toString();
        if (!h.startsWith("JAVA PROFILE")) {
            throw new JfaException(ErrorCode.E_HPROF_INVALID, "不是 HotSpot hprof 文件（缺 JAVA PROFILE 头）");
        }
        int idSize = in.readInt();
        if (idSize != 4 && idSize != 8) {
            throw new JfaException(ErrorCode.E_HPROF_INVALID, "不支持的 identifier size: " + idSize);
        }
        in.readInt();
        in.readInt();
        Ctx ctx = new Ctx(idSize, fileLen);
        try {
            while (true) {
                int tag;
                try {
                    tag = in.readUnsignedByte();
                } catch (EOFException eof) {
                    break;
                }
                in.readInt(); // time
                int length = in.readInt();
                if (length < 0) {
                    break;
                }
                if (tag == STRING) {
                    long id = readId(in, idSize);
                    byte[] bytes = new byte[length - idSize];
                    in.readFully(bytes);
                    ctx.strings.put(id, new String(bytes, StandardCharsets.UTF_8));
                } else if (tag == LOAD_CLASS) {
                    in.readInt();
                    long classObj = readId(in, idSize);
                    in.readInt();
                    long nameId = readId(in, idSize);
                    ctx.classNames.put(classObj, nameId);
                } else if (tag == HEAP_DUMP || tag == HEAP_DUMP_SEGMENT) {
                    parseHeap(in, length, ctx);
                } else {
                    skip(in, length);
                }
                if (tag == HEAP_DUMP_END) {
                    break;
                }
            }
        } catch (EOFException eof) {
            // truncated but maybe usable
        }
        return ctx.toSummary();
    }

    private void parseHeap(DataInputStream in, int length, Ctx ctx) throws IOException {
        int remaining = length;
        int idSize = ctx.idSize;
        while (remaining > 0) {
            int rec = in.readUnsignedByte();
            remaining--;
            switch (rec) {
                case 0xFF:
                    remaining -= skipId(in, idSize);
                    break;
                case 0x01:
                    remaining -= skipId(in, idSize);
                    remaining -= skipId(in, idSize);
                    break;
                case 0x02:
                    remaining -= skipId(in, idSize);
                    remaining -= 8;
                    in.readInt();
                    in.readInt();
                    break;
                case 0x03:
                    remaining -= skipId(in, idSize);
                    remaining -= 8;
                    in.readInt();
                    in.readInt();
                    break;
                case 0x04:
                    remaining -= skipId(in, idSize);
                    remaining -= 4;
                    in.readInt();
                    break;
                case 0x05:
                    remaining -= skipId(in, idSize);
                    break;
                case 0x06:
                    remaining -= skipId(in, idSize);
                    remaining -= 4;
                    in.readInt();
                    break;
                case 0x07:
                    remaining -= skipId(in, idSize);
                    break;
                case 0x08:
                    remaining -= skipId(in, idSize);
                    remaining -= 8;
                    in.readInt();
                    in.readInt();
                    break;
                case CLASS_DUMP:
                    remaining -= readClassDump(in, ctx);
                    break;
                case INSTANCE_DUMP:
                    remaining -= readInstanceDump(in, ctx);
                    break;
                case OBJ_ARRAY:
                    remaining -= readObjArray(in, ctx);
                    break;
                case PRIM_ARRAY:
                    remaining -= readPrimArray(in, ctx);
                    break;
                default:
                    skip(in, remaining);
                    remaining = 0;
                    break;
            }
        }
    }

    private int readClassDump(DataInputStream in, Ctx ctx) throws IOException {
        int used = 0;
        int idSize = ctx.idSize;
        long objId = readId(in, idSize);
        used += idSize;
        in.readInt();
        used += 4;
        long superId = readId(in, idSize);
        used += idSize;
        skipId(in, idSize);
        used += idSize; // class loader
        skipId(in, idSize);
        used += idSize;
        skipId(in, idSize);
        used += idSize;
        skipId(in, idSize);
        used += idSize;
        skipId(in, idSize);
        used += idSize;
        int instanceSize = in.readInt();
        used += 4;
        int constPool = in.readUnsignedShort();
        used += 2;
        for (int i = 0; i < constPool; i++) {
            in.readUnsignedShort();
            used += 2;
            int type = in.readUnsignedByte();
            used += 1;
            int vs = valueSize(type, idSize);
            skip(in, vs);
            used += vs;
        }
        int statics = in.readUnsignedShort();
        used += 2;
        List<String> staticNames = new ArrayList<String>();
        for (int i = 0; i < statics; i++) {
            long nameId = readId(in, idSize);
            used += idSize;
            int type = in.readUnsignedByte();
            used += 1;
            int vs = valueSize(type, idSize);
            if (type == 2) {
                readId(in, idSize);
            } else {
                skip(in, vs);
            }
            used += vs;
            String nm = ctx.strings.get(nameId);
            if (nm != null) {
                staticNames.add(nm);
            }
        }
        int fields = in.readUnsignedShort();
        used += 2;
        for (int i = 0; i < fields; i++) {
            skipId(in, idSize);
            used += idSize;
            in.readUnsignedByte();
            used += 1;
        }
        ClassDef def = new ClassDef();
        def.objectId = objId;
        def.superId = superId;
        def.instanceSize = instanceSize;
        def.staticFieldNames = staticNames;
        Long nameId = ctx.classNames.get(objId);
        if (nameId != null) {
            def.name = ctx.strings.get(nameId);
        }
        if (def.name == null) {
            def.name = "class@" + Long.toHexString(objId);
        }
        ctx.classes.put(objId, def);
        ctx.nameOfClass.put(objId, def.name);
        return used;
    }

    private int readInstanceDump(DataInputStream in, Ctx ctx) throws IOException {
        int idSize = ctx.idSize;
        int used = 0;
        skipId(in, idSize);
        used += idSize;
        in.readInt();
        used += 4;
        long classId = readId(in, idSize);
        used += idSize;
        int len = in.readInt();
        used += 4;
        skip(in, len);
        used += len;
        String name = ctx.nameOfClass.get(classId);
        if (name == null) {
            Long nid = ctx.classNames.get(classId);
            name = nid == null ? "unknown" : ctx.strings.get(nid);
            if (name == null) {
                name = "unknown";
            }
        }
        ClassDef def = ctx.classes.get(classId);
        long shallow = def == null ? Math.max(len, 16) : def.instanceSize;
        ctx.add(name, 1, shallow);
        return used;
    }

    private int readObjArray(DataInputStream in, Ctx ctx) throws IOException {
        int idSize = ctx.idSize;
        int used = 0;
        skipId(in, idSize);
        used += idSize;
        in.readInt();
        used += 4;
        int n = in.readInt();
        used += 4;
        long classId = readId(in, idSize);
        used += idSize;
        skip(in, n * idSize);
        used += n * idSize;
        String name = ctx.nameOfClass.get(classId);
        if (name == null) {
            Long nid = ctx.classNames.get(classId);
            name = nid == null ? "java.lang.Object[]" : ctx.strings.get(nid);
            if (name == null) {
                name = "java.lang.Object[]";
            }
        }
        ctx.add(name, 1, (long) n * idSize + 16);
        return used;
    }

    private int readPrimArray(DataInputStream in, Ctx ctx) throws IOException {
        int idSize = ctx.idSize;
        int used = 0;
        skipId(in, idSize);
        used += idSize;
        in.readInt();
        used += 4;
        int n = in.readInt();
        used += 4;
        int type = in.readUnsignedByte();
        used += 1;
        int es = primitiveSize(type);
        long body = (long) n * es;
        skip(in, body);
        used += (int) body;
        String name = primitiveArrayName(type);
        ctx.add(name, 1, body + 16);
        ctx.primArrayBytes.put(name,
                (ctx.primArrayBytes.containsKey(name) ? ctx.primArrayBytes.get(name) : 0L) + body);
        return used;
    }

    private static String primitiveArrayName(int type) {
        switch (type) {
            case 4:
                return "boolean[]";
            case 5:
                return "char[]";
            case 6:
                return "float[]";
            case 7:
                return "double[]";
            case 8:
                return "byte[]";
            case 9:
                return "short[]";
            case 10:
                return "int[]";
            case 11:
                return "long[]";
            default:
                return "primitive[]";
        }
    }

    private static int primitiveSize(int type) {
        switch (type) {
            case 4:
            case 8:
                return 1;
            case 5:
            case 9:
                return 2;
            case 6:
            case 10:
                return 4;
            case 7:
            case 11:
                return 8;
            default:
                return 1;
        }
    }

    private static int valueSize(int type, int idSize) {
        switch (type) {
            case 2:
                return idSize;
            case 4:
            case 8:
                return 1;
            case 5:
            case 9:
                return 2;
            case 6:
            case 10:
                return 4;
            case 7:
            case 11:
                return 8;
            default:
                return 1;
        }
    }

    private static long readId(DataInputStream in, int idSize) throws IOException {
        if (idSize == 4) {
            return in.readInt() & 0xffffffffL;
        }
        return in.readLong();
    }

    private static int skipId(DataInputStream in, int idSize) throws IOException {
        skip(in, idSize);
        return idSize;
    }

    private static void skip(DataInputStream in, long n) throws IOException {
        long left = n;
        while (left > 0) {
            long skipped = in.skip(left);
            if (skipped <= 0) {
                if (in.read() < 0) {
                    throw new EOFException();
                }
                left--;
            } else {
                left -= skipped;
            }
        }
    }

    private static final class ClassDef {
        long objectId;
        long superId;
        int instanceSize;
        String name;
        List<String> staticFieldNames = new ArrayList<String>();
    }

    private static final class Hist {
        String name;
        long instances;
        long bytes;
    }

    private static final class Ctx {
        final int idSize;
        final long fileLen;
        final Map<Long, String> strings = new HashMap<Long, String>();
        final Map<Long, Long> classNames = new HashMap<Long, Long>();
        final Map<Long, ClassDef> classes = new HashMap<Long, ClassDef>();
        final Map<Long, String> nameOfClass = new HashMap<Long, String>();
        final Map<String, Hist> hist = new LinkedHashMap<String, Hist>();
        final Map<String, Long> primArrayBytes = new HashMap<String, Long>();

        Ctx(int idSize, long fileLen) {
            this.idSize = idSize;
            this.fileLen = fileLen;
        }

        void add(String name, long inst, long bytes) {
            Hist h = hist.get(name);
            if (h == null) {
                h = new Hist();
                h.name = name;
                hist.put(name, h);
            }
            h.instances += inst;
            h.bytes += bytes;
        }

        HprofSummary toSummary() {
            HprofSummary s = new HprofSummary();
            s.fileBytes = fileLen;
            List<Hist> list = new ArrayList<Hist>(hist.values());
            Collections.sort(list, new Comparator<Hist>() {
                @Override
                public int compare(Hist a, Hist b) {
                    return Long.compare(b.bytes, a.bytes);
                }
            });
            long total = 0;
            for (Hist h : list) {
                total += h.bytes;
            }
            s.approxUsedBytes = total;
            for (int i = 0; i < list.size(); i++) {
                Hist h = list.get(i);
                HprofSummary.ClassStat st = new HprofSummary.ClassStat();
                st.className = h.name;
                st.instances = h.instances;
                st.retainedBytes = h.bytes;
                st.ratio = total == 0 ? 0d : (double) h.bytes / (double) total;
                s.allClasses.add(st);
                if (s.topClasses.size() < 20) {
                    s.topClasses.add(st);
                }
            }
            for (ClassDef def : classes.values()) {
                if (def.name != null && looksCacheHolder(def.name) && def.staticFieldNames != null) {
                    s.staticHolders.add(def.name + " statics=" + def.staticFieldNames);
                    if (s.primaryHolder == null) {
                        s.primaryHolder = def.name;
                        s.primaryHolderFields = def.staticFieldNames;
                    }
                }
            }
            if (s.primaryHolder == null) {
                for (ClassDef def : classes.values()) {
                    if (def.name != null && def.name.contains("Cache")) {
                        s.primaryHolder = def.name;
                        s.primaryHolderFields = def.staticFieldNames;
                        s.staticHolders.add(def.name + " statics=" + def.staticFieldNames);
                        break;
                    }
                }
            }
            s.truncated = fileLen > 2L * 1024 * 1024 * 1024;
            return s;
        }
    }

    static boolean looksCacheHolder(String name) {
        String n = name.toLowerCase();
        return n.contains("cache") || n.contains("bufferpool") || n.contains("listenerregistry");
    }

    public static class HprofSummary {
        public long fileBytes;
        public long approxUsedBytes;
        public boolean truncated;
        public String primaryHolder;
        public List<String> primaryHolderFields = new ArrayList<String>();
        public final List<String> staticHolders = new ArrayList<String>();
        public final List<ClassStat> topClasses = new ArrayList<ClassStat>();
        public final List<ClassStat> allClasses = new ArrayList<ClassStat>();

        public static class ClassStat {
            public String className;
            public long instances;
            public long retainedBytes;
            public double ratio;
        }

        public ClassStat top(String contains) {
            for (ClassStat c : topClasses) {
                if (c.className != null && c.className.contains(contains)) {
                    return c;
                }
            }
            return null;
        }
    }
}
