import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class BServe {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java BServe <docroot> <port>");
            System.exit(1);
        }

        String docroot = args[0];
        int port = Integer.parseInt(args[1]);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Listening on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket, docroot)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private File rootDir;

    public ClientHandler(Socket socket, String docroot) {
        this.socket = socket;
        this.rootDir = new File(docroot);
    }

    @Override
    public void run() {
        try {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            while (true) {
                Frame frame;
                try {
                    frame = Frame.read(in);
                } catch (EOFException e) {
                    break;
                }
                if (frame == null) break;

                if (frame.type == 0x01) { // REQUEST frame
                    handleRequest(frame, out);
                } else if (frame.type == 0x03) { // HEADERS (ignore for now)
                    // we could accumulate headers, but for simple request we just rely on REQUEST frame
                } else if (frame.type == 0x04) { // DATA (ignore for now)
                } else {
                    // "A receiver meeting a frame type it does not know MUST skip it cleanly."
                    // Frame.read already skips it by reading the payload, we just do nothing here.
                }
            }
        } catch (Exception e) {
            System.err.println("Client error: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException e) {}
        }
    }

    private void handleRequest(Frame reqFrame, OutputStream out) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(reqFrame.payload);
        int methodIndex = bais.read();
        String method;
        if (methodIndex > 0) {
            method = HPACK.STATIC_TABLE[methodIndex];
        } else {
            int methodLen = bais.read();
            byte[] methodBytes = new byte[methodLen];
            bais.read(methodBytes);
            method = new String(methodBytes, "UTF-8");
        }

        int pathLen = (bais.read() << 8) | bais.read();
        byte[] pathBytes = new byte[pathLen];
        bais.read(pathBytes);
        String path = new String(pathBytes, "UTF-8");

        System.out.println("Received Request: " + method + " " + path);

        if (!method.equalsIgnoreCase(":method") && !method.equalsIgnoreCase("GET")) {
             sendResponse(out, reqFrame.streamId, 400, "Bad Request");
             return;
        }

        File targetFile = new File(rootDir, path);
        try {
            if (!targetFile.getCanonicalPath().startsWith(rootDir.getCanonicalPath())) {
                 sendResponse(out, reqFrame.streamId, 400, "Bad Request: Directory Traversal Attempt");
                 return;
            }
        } catch (IOException e) {
            sendResponse(out, reqFrame.streamId, 400, "Bad Request: Invalid Path");
            return;
        }

        if (!targetFile.exists() || targetFile.isDirectory()) {
             sendResponse(out, reqFrame.streamId, 404, "Not Found");
             return;
        }

        byte[] content = Files.readAllBytes(targetFile.toPath());
        sendResponseOk(out, reqFrame.streamId, content, getContentType(targetFile.getName()));
    }

    private void sendResponse(OutputStream out, int streamId, int status, String message) throws IOException {
        // RESPONSE Frame
        byte[] statusBytes = new byte[] { (byte)((status >> 8) & 0xFF), (byte)(status & 0xFF) };
        Frame respFrame = new Frame(2, (byte)0x02, (byte)0, streamId, statusBytes);
        respFrame.write(out);

        // DATA frame with message
        byte[] msgBytes = message.getBytes("UTF-8");
        Frame dataFrame = new Frame(msgBytes.length, (byte)0x04, (byte)0x01, streamId, msgBytes); // END_STREAM
        dataFrame.write(out);
    }

    private void sendResponseOk(OutputStream out, int streamId, byte[] content, String contentType) throws IOException {
        // RESPONSE Frame (200 OK)
        byte[] statusBytes = new byte[] { (byte)((200 >> 8) & 0xFF), (byte)(200 & 0xFF) };
        Frame respFrame = new Frame(2, (byte)0x02, (byte)0, streamId, statusBytes);
        respFrame.write(out);

        // HEADERS Frame
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", contentType);
        headers.put("content-length", String.valueOf(content.length));
        headers.put("server", "BServe/1.0");
        byte[] headersPayload = HPACK.encodeHeaders(headers);
        Frame headersFrame = new Frame(headersPayload.length, (byte)0x03, (byte)0x01, streamId, headersPayload); // END_HEADERS
        headersFrame.write(out);

        // DATA Frame
        Frame dataFrame = new Frame(content.length, (byte)0x04, (byte)0x01, streamId, content); // END_STREAM
        dataFrame.write(out);
    }

    private String getContentType(String filename) {
        if (filename.endsWith(".html")) return "text/html";
        if (filename.endsWith(".css")) return "text/css";
        if (filename.endsWith(".js")) return "application/javascript";
        if (filename.endsWith(".png")) return "image/png";
        if (filename.endsWith(".jpg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
