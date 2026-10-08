import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class HPACK {
    public static final String[] STATIC_TABLE = {
        "", // 1-indexed
        ":method",
        ":path",
        ":status",
        "content-type",
        "content-length",
        "host",
        "user-agent",
        "accept",
        "server",
        "connection"
    };

    public static byte[] encodeHeaders(Map<String, String> headers) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String name = entry.getKey().toLowerCase();
            String value = entry.getValue();

            int index = getStaticIndex(name);
            if (index > 0) {
                baos.write(index);
            } else {
                baos.write(0);
                byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
                baos.write(nameBytes.length);
                baos.write(nameBytes);
            }

            byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
            baos.write((valueBytes.length >> 8) & 0xFF);
            baos.write(valueBytes.length & 0xFF);
            baos.write(valueBytes);
        }
        return baos.toByteArray();
    }

    public static Map<String, String> decodeHeaders(byte[] payload) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        ByteArrayInputStream bais = new ByteArrayInputStream(payload);

        while (bais.available() > 0) {
            int nameIndex = bais.read();
            String name;
            if (nameIndex > 0 && nameIndex < STATIC_TABLE.length) {
                name = STATIC_TABLE[nameIndex];
            } else if (nameIndex == 0) {
                int nameLen = bais.read();
                byte[] nameBytes = new byte[nameLen];
                if (bais.read(nameBytes) != nameLen) throw new IOException("Bad header name length");
                name = new String(nameBytes, StandardCharsets.UTF_8);
            } else {
                throw new IOException("Invalid header index: " + nameIndex);
            }

            int valLenHigh = bais.read();
            int valLenLow = bais.read();
            if (valLenHigh == -1 || valLenLow == -1) throw new IOException("Unexpected EOF reading value length");
            int valLen = (valLenHigh << 8) | valLenLow;

            byte[] valBytes = new byte[valLen];
            if (bais.read(valBytes) != valLen) throw new IOException("Bad header value length");
            String value = new String(valBytes, StandardCharsets.UTF_8);

            headers.put(name, value);
        }
        return headers;
    }

    private static int getStaticIndex(String name) {
        for (int i = 1; i < STATIC_TABLE.length; i++) {
            if (STATIC_TABLE[i].equals(name)) return i;
        }
        return 0;
    }
}
