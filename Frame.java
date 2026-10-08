import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

public class Frame {
    public static final int HEADER_SIZE = 9;
    public int length;
    public byte type;
    public byte flags;
    public int streamId;
    public byte[] payload;

    public Frame(int length, byte type, byte flags, int streamId, byte[] payload) {
        this.length = length;
        this.type = type;
        this.flags = flags;
        this.streamId = streamId;
        this.payload = payload;
    }

    public static Frame read(InputStream in) throws IOException {
        byte[] header = new byte[HEADER_SIZE];
        int read = 0;
        while (read < HEADER_SIZE) {
            int r = in.read(header, read, HEADER_SIZE - read);
            if (r == -1) {
                if (read == 0) return null; // Connection closed
                throw new EOFException("Unexpected end of stream while reading frame header");
            }
            read += r;
        }

        ByteBuffer buffer = ByteBuffer.wrap(header);
        int length = ((buffer.get() & 0xFF) << 16) | ((buffer.get() & 0xFF) << 8) | (buffer.get() & 0xFF);
        byte type = buffer.get();
        byte flags = buffer.get();
        int streamId = buffer.getInt() & 0x7FFFFFFF; // 31 bits

        byte[] payload = new byte[length];
        read = 0;
        while (read < length) {
            int r = in.read(payload, read, length - read);
            if (r == -1) throw new EOFException("Unexpected end of stream while reading payload");
            read += r;
        }

        return new Frame(length, type, flags, streamId, payload);
    }

    public void write(OutputStream out) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + length);
        buffer.put((byte) ((length >> 16) & 0xFF));
        buffer.put((byte) ((length >> 8) & 0xFF));
        buffer.put((byte) (length & 0xFF));
        buffer.put(type);
        buffer.put(flags);
        buffer.putInt(streamId & 0x7FFFFFFF);
        buffer.put(payload);
        out.write(buffer.array());
        out.flush();
    }
}
