package org.benf.cfr.reader.bytecode.opcode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op01WithProcessedDataAndByteJumps;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.util.bytestream.ByteData;

import java.util.List;

public class OperationFactoryLookupSwitch extends OperationFactoryDefault {

    // offsets relative to computed start of default
    private static final int OFFSET_OF_NPAIRS = 4;
    private static final int OFFSET_OF_OFFSETS = 8;


    @Override
    public Op01WithProcessedDataAndByteJumps createOperation(JVMInstr instr, ByteData bd, ConstantPool cp, int offset) {
        int curoffset = offset + 1;
        // We need to align the next byte to a 4 byte boundary relative to the start of the method.
        int overflow = (curoffset % 4);
        overflow = overflow > 0 ? 4 - overflow : 0;
        int startdata = 1 + overflow;
        int npairs = bd.getS4At(startdata + OFFSET_OF_NPAIRS);
        int size = overflow + OFFSET_OF_OFFSETS + 8 * npairs;
        byte[] rawData = bd.getBytesAt(size, 1);

        DecodedSwitch dts = new DecodedLookupSwitch(rawData, offset);
        List<DecodedSwitchEntry> targets = dts.getJumpTargets();
        int[] targetOffsets = new int[targets.size()];
        int out = 0;
        for (DecodedSwitchEntry target : targets) {
            targetOffsets[out++] = target.getBytecodeTarget();
        }

        return new Op01WithProcessedDataAndByteJumps(instr, rawData, targetOffsets, offset);
    }

}
