package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

public class AttributeUnknown extends Attribute {
    private static final long OFFSET_OF_ATTRIBUTE_LENGTH = 2;
    private static final long OFFSET_OF_REMAINDER = 6;

    private final int length;
    private final String name;

    public AttributeUnknown(ByteData raw, String name) {
        this.length = raw.getS4At(OFFSET_OF_ATTRIBUTE_LENGTH);
        this.name = name;
    }

    @Override
    public String getRawName() {
        return name;
    }

    @Override
    public Dumper dump(Dumper d) {
        return d.print("Unknown Attribute : " + name);
    }

    @Override
    public long getRawByteLength() {
        return OFFSET_OF_REMAINDER + length;
    }

    @Override
    public String toString() {
        return "Unknown Attribute : " + name;
    }
}
