package com.schlaikjer.music.model;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Packet {

    private static final String TAG = Packet.class.getSimpleName();

    // General packet format:
    // u32 nonce
    // u32 cmd
    // u32 data len
    // u8[data len] data

    public static final int HEADER_SIZE = 3 * 4;

    public int nonce;
    public int opcode;
    public byte[] data;


    public Packet(int nonce, int opcode, byte[] data) {
        this.nonce = nonce;
        this.opcode = opcode;
        this.data = data;
    }

    public static Packet deserialize(ByteArrayOutputStream is) throws IOException {
        // Are there even enough bytes for a header
        Log.d(TAG, "Attempt to deserialize is with " + is.size() + " bytes");
        if (is.size() < HEADER_SIZE) {
            return null;
        }

        // Get the backing data of the stream
        byte[] data = is.toByteArray();
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int nonce = buffer.getInt(0);
        int opcode = buffer.getInt(4);
        int data_len = buffer.getInt(8);
        Log.d(TAG, "Packet header: nonce: " + nonce + " opcode: " + opcode + " data len: " + data_len);

        // Is there enough data in this stream to populate the packet?
        if (data.length < data_len + HEADER_SIZE) {
            return null;
        }

        // If there is enough data, consume
        Packet p = new Packet(nonce, opcode, new byte[data_len]);
        System.arraycopy(data, 12, p.data, 0, data_len);
        Log.d(TAG, "Decoded packet with len " + data_len);

        // Carve the read data off the front of the input stream
        is.reset();
        is.write(data, data_len + HEADER_SIZE, data.length - data_len - HEADER_SIZE);
        Log.d(TAG, "Input stream size now " + is.size());

        return p;
    }

    public void serialize(OutputStream os) throws IOException {
        // Allocate a byte buffer to pack the fixed size header
        ByteBuffer bb = ByteBuffer.allocate(4 * 3).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(this.nonce);
        bb.putInt(this.opcode);
        bb.putInt(this.data.length);
        os.write(bb.array());

        // Write the variable-size data
        os.write(data);
    }
}