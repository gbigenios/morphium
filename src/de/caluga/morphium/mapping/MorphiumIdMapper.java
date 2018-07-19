package de.caluga.morphium.mapping;

import de.caluga.morphium.BinarySerializedObject;
import de.caluga.morphium.ObjectMapperImpl;
import de.caluga.morphium.TypeMapper;
import de.caluga.morphium.driver.MorphiumId;
import org.bson.types.ObjectId;
import sun.misc.BASE64Decoder;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.Map;

public class MorphiumIdMapper implements TypeMapper<MorphiumId> {

    @Override
    public Object marshall(MorphiumId o) {
        return new ObjectId(o.toString());
    }

    @Override
    public MorphiumId unmarshall(Object d) {
        if (d == null) return null;
        if (d instanceof Map) {
            try {
                BinarySerializedObject obj = new ObjectMapperImpl().unmarshall(BinarySerializedObject.class, (Map) d);
                BASE64Decoder dec = new BASE64Decoder();
                ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(dec.decodeBuffer(obj.getB64Data())));
                return (MorphiumId) in.readObject();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if (d instanceof String) return new MorphiumId(d.toString());
        if (d instanceof MorphiumId) return (MorphiumId) d;
        return new MorphiumId(((ObjectId) d).toByteArray());
    }

    @Override
    public boolean matches(Object value) {
        return value != null && value instanceof ObjectId;
    }
}
