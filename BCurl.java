import java.io.*;
import java.net.*;
import java.util.*;

public class BCurl {
    public static void main(String[] args) {
        boolean verbose = false;
        String urlString = null;

        for (String arg : args) {
            if (arg.equals("-v")) {
                verbose = true;
            } else {
                urlString = arg;
            }
        }

        if (urlString == null) {
            System.err.println("Usage: java BCurl [-v] <host:port/path>");
            System.exit(1);
        }

        try {
            int slashIdx = urlString.indexOf('/');
            String hostPort = slashIdx != -1 ? urlString.substring(0, slashIdx) : urlString;
            String path = slashIdx != -1 ? urlString.substring(slashIdx) : "/";

            int colonIdx = hostPort.indexOf(':');
            String host = colonIdx != -1 ? hostPort.substring(0, colonIdx) : hostPort;
            int port = colonIdx != -1 ? Integer.parseInt(hostPort.substring(colonIdx + 1)) : 80;

            try (Socket socket = new Socket(host, port)) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();

                int streamId = 1;

                ByteArrayOutputStream reqPayload = new ByteArrayOutputStream();
                reqPayload.write(1); // :method index
                byte[] pathBytes = path.getBytes("UTF-8");
                reqPayload.write((pathBytes.length >> 8) & 0xFF);
                reqPayload.write(pathBytes.length & 0xFF);
                reqPayload.write(pathBytes);
                
                Frame reqFrame = new Frame(reqPayload.size(), (byte)0x01, (byte)0, streamId, reqPayload.toByteArray());
                reqFrame.write(out);
                if (verbose) {
                    System.err.println(">> Sent REQUEST frame");
                    printHexDump(reqFrame);
                }

                if (verbose) {
                    Frame unknownFrame = new Frame(4, (byte)0xFF, (byte)0, streamId, new byte[]{1,2,3,4});
                    unknownFrame.write(out);
                    System.err.println(">> Sent UNKNOWN frame (Type 0xFF)");
                    printHexDump(unknownFrame);
                }

                boolean endStream = false;
                int exitCode = 0;

                while (!endStream) {
                    Frame frame = Frame.read(in);
                    if (frame == null) {
                        System.err.println("Connection closed prematurely.");
                        System.exit(2);
                    }

                    if (verbose) {
                        System.err.println("<< Received frame Type=" + frame.type + " Flags=" + frame.flags + " Len=" + frame.length);
                        printHexDump(frame);
                    }

                    if (frame.type == 0x02) { // RESPONSE
                        int status = ((frame.payload[0] & 0xFF) << 8) | (frame.payload[1] & 0xFF);
                        if (verbose) {
                            System.err.println("Status: " + status);
                        }
                        if (status >= 400) {
                            exitCode = 1;
                        }
                    } else if (frame.type == 0x03) { // HEADERS
                        Map<String, String> headers = HPACK.decodeHeaders(frame.payload);
                        if (verbose) {
                            for (Map.Entry<String, String> entry : headers.entrySet()) {
                                System.err.println(entry.getKey() + ": " + entry.getValue());
                            }
                        }
                    } else if (frame.type == 0x04) { // DATA
                        System.out.write(frame.payload);
                        System.out.flush();
                        if ((frame.flags & 0x01) != 0) {
                            endStream = true;
                        }
                    } else {
                        if (verbose) {
                            System.err.println("Skipping unknown frame type: " + frame.type);
                        }
                    }
                }
                
                System.exit(exitCode);
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
    }

    private static void printHexDump(Frame frame) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            frame.write(baos);
            byte[] bytes = baos.toByteArray();

            for (int i = 0; i < bytes.length; i += 16) {
                System.err.printf("%04x  ", i);
                for (int j = 0; j < 16; j++) {
                    if (i + j < bytes.length) {
                        System.err.printf("%02x ", bytes[i + j]);
                    } else {
                        System.err.print("   ");
                    }
                }
                System.err.print(" |");
                for (int j = 0; j < 16; j++) {
                    if (i + j < bytes.length) {
                        char c = (char) bytes[i + j];
                        if (c >= 32 && c < 127) {
                            System.err.print(c);
                        } else {
                            System.err.print('.');
                        }
                    }
                }
                System.err.println("|");
            }
            System.err.println();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
