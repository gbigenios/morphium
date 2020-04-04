package de.caluga.morphium;/**
 * Created by stephan on 25.11.15.
 */

import de.caluga.morphium.driver.MorphiumId;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Utility class
 **/
@SuppressWarnings("WeakerAccess")
public class Utils {

    public static final String[] hexChars = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F",};


    public static String toJsonString(Object o) {
        StringWriter sw = new StringWriter();
        try {
            writeJson(o, sw);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sw.toString();
    }

    @SuppressWarnings({"unchecked", "UnusedDeclaration"})
    public static void writeJson(Object o, Writer out) throws IOException {
        if (o == null) return;

        boolean comma = false;
        if (o instanceof Collection) {
            out.write(" [ ");

            for (Object obj : ((Collection) o)) {
                if (comma) {
                    out.write(", ");
                }
                comma = true;
                writeJson(obj, out);
            }
            out.write("]");
            return;
        } else if (o.getClass().isArray()) {
            out.write(" [ ");

            int length = Array.getLength(o);
            for (int i = 0; i < length; i++) {
                Object obj = Array.get(o, i);
                if (comma) {
                    out.write(", ");
                }
                comma = true;
                writeJson(obj, out);
            }
            out.write("]");
            return;
        } else if ((o instanceof String) || (o instanceof MorphiumId) || (o instanceof ObjectId) || (o.getClass().isEnum())) {
            out.write("\"");
            out.write(o.toString());
            out.write("\"");
            return;
        } else if (!(o instanceof Map)) {
            out.write(o.toString());
            return;
        }
        Map<String, Object> db = (Map<String, Object>) o;

        out.write("{ ");
        comma = false;
        for (Map.Entry<String, Object> e : db.entrySet()) {
            if (comma) {
                out.write(", ");
            }

            comma = true;
            out.write("\"");
            out.write(e.getKey());
            out.write("\"");

            out.write(" : ");
            if (e.getValue() == null) {
                out.write(" null");
            } else if (e.getValue() instanceof String) {
                out.write("\"");
                out.write((String) e.getValue());
                out.write("\"");
            } else if (e.getValue().getClass().isEnum()) {
                out.write("\"");
                out.write(e.getValue().toString());
                out.write("\"");
            } else {
                writeJson(e.getValue(), out);
            }

        }
        out.write(" } ");
    }

    public static <K, V> Map<K, V> getMap(K key, V value) {
        LinkedHashMap<K, V> ret = new LinkedHashMap<>();
        ret.put(key, value);
        return ret;
    }

    public static Map<String, Integer> getIntMap(String key, Integer value) {
        HashMap<String, Integer> ret = new LinkedHashMap<>();
        ret.put(key, value);
        return ret;
    }


    public static String getHex(long i) {
        return (getHex((byte) (i >> 56 & 0xff)) + getHex((byte) (i >> 48 & 0xff)) + getHex((byte) (i >> 40 & 0xff)) + getHex((byte) (i >> 32 & 0xff)) + getHex((byte) (i >> 24 & 0xff)) + getHex((byte) (i >> 16 & 0xff)) + getHex((byte) (i >> 8 & 0xff)) + getHex((byte) (i & 0xff)));
    }

    public static String getHex(int i) {
        return (getHex((byte) (i >> 24 & 0xff)) + getHex((byte) (i >> 16 & 0xff)) + getHex((byte) (i >> 8 & 0xff)) + getHex((byte) (i & 0xff)));
    }

    public static String getHex(byte[] b) {
        return getHex(b, -1);
    }


    public static String getHex(byte[] b, int sz) {
        StringBuilder sb = new StringBuilder();

        int mainIdx = 0;

        int end = b.length;
        if (sz > 0 && sz < b.length) {
            end = sz;
        }
        while (mainIdx < end) {
            sb.append(getHex((byte) (mainIdx >> 24 & 0xff)));
            sb.append(getHex((byte) (mainIdx >> 16 & 0xff)));
            sb.append(getHex((byte) (mainIdx >> 8 & 0xff)));
            sb.append(getHex((byte) (mainIdx & 0xff)));

            sb.append(":  ");
            for (int i = mainIdx; i < mainIdx + 16 && i < b.length; i++) {
                byte by = b[i];
                sb.append(getHex(by));
                sb.append(" ");
            }

            int l = 16;
            if (mainIdx + 16 > b.length) {
                l = b.length - mainIdx;
            }

            byte[] sr = new byte[l];
            int n = 0;
            for (int j = mainIdx; j < mainIdx + l; j++) {
                if (b[j] > 63) {
                    sr[n] = b[j];
                } else if (b[j] == 0) {
                    sr[n] = '-';
                } else {
                    sr[n] = '.';
                }
                n++;
            }
            String str = new String(sr, 0, l, StandardCharsets.UTF_8);
            sb.append("    ");
            sb.append(str);
            sb.append("\n");
            mainIdx += 16;
        }
        return sb.toString();

    }

    public static String getHex(byte by) {
        String ret = "";
        int idx = (by >>> 4) & 0x0f;
        ret += hexChars[idx];
        idx = by & 0x0f;
        ret += hexChars[idx];
        return ret;
    }

    public static String getCacheKey(Class type, Map<String, Object> qo, Map<String, Integer> sort, Map<String, Object> projection, String collection, int skip, int limit, AnnotationAndReflectionHelper anHelper) {

        StringBuilder b = new StringBuilder();
        b.append(qo.toString());
        b.append(" c:").append(collection);
        b.append(" l:");
        b.append(limit);
        b.append(" s:");
        b.append(skip);
        if (sort != null) {
            b.append(" sort:{");
            for (Map.Entry<String, Integer> s : sort.entrySet()) {
                b.append(" ").append(s.getKey()).append(":").append(s.getValue());
            }
            b.append("}");
        }
        if (projection != null) {
            List<Field> fields = anHelper.getAllFields(type);
            boolean addProjection = false;
            if (projection.size() == fields.size()) {
                for (Field f : fields) {
                    if (!projection.containsKey(anHelper.getFieldName(type, f.getName()))) {
                        addProjection = true;
                        break;
                    }
                }
            } else {
                addProjection = true;
            }

            if (addProjection) {
                b.append(" project:{");
                for (Map.Entry<String, Object> s : projection.entrySet()) {
                    b.append(" ").append(s.getKey()).append(":").append(s.getValue());
                }
                b.append("}");
            }
        }
        return b.toString();
    }


    public static Object replaceMorphiumIds(Map m) {
        if (m == null) return null;
        Map toSet = new LinkedHashMap();
        //noinspection unchecked
        for (Map.Entry e : (Set<Map.Entry>) m.entrySet()) {
            if (e.getValue() == null) {
                //noinspection unchecked
                toSet.put(e.getKey(), null);
            } else if (e.getKey().equals("morphium id")) {
                //identifier!
                return new ObjectId(e.getValue().toString());
            } else if (e.getKey().equals("date field")) {
                return new Date((Long) e.getValue());
            } else if (e.getValue() instanceof Map) {
                //noinspection unchecked
                toSet.put(e.getKey(), replaceMorphiumIds((Map) e.getValue()));
            } else if (e.getValue() instanceof Collection) {
                //noinspection unchecked
                toSet.put(e.getKey(), replaceMorphiumIds((Collection) e.getValue()));
            } else {
                //noinspection unchecked
                toSet.put(e.getKey(), e.getValue());
            }
        }
        return toSet;
    }

    public static Collection replaceMorphiumIds(Collection value) {
        if (value == null) return null;
        Collection ret = new ArrayList();
        for (Object o : value) {
            if (o == null) {
                //noinspection unchecked
                ret.add(null);
            } else if (o instanceof Map && ((Map) o).containsKey("morphium id")) {
                //noinspection unchecked
                ret.add(new ObjectId((String) ((Map) o).get("morphium id")));
            } else if (o instanceof Map && ((Map) o).containsKey("date field")) {
                //noinspection unchecked
                ret.add(new Date((Long) ((Map) o).get("date field")));
            } else if (o instanceof Map) {
                //noinspection unchecked
                ret.add(replaceMorphiumIds((Map) o));
            } else {
                //noinspection unchecked
                ret.add(o);
            }
        }
        return ret;
    }

}
