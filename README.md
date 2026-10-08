# Binary HTTP Protocol Implementation

This project is a from-scratch implementation of a custom binary HTTP protocol designed for efficiency, multiplexing, and future extensibility. The project features a two-track architecture (Server and Client) and establishes a clear, rigorous protocol specification that bridges the two.

## Project Structure

*   **`spec.md`**: The formal protocol specification. It details the 9-byte (72-bit) frame header layout (inspired by HTTP/2), the supported frame types, and my justification for the chosen bit-widths. 
*   **`BServe.java`**: The Server implementation. It listens on a TCP socket, maps binary request frames to a local docroot, and streams back binary responses. It features robust 404 handling and security protections against directory traversal (400 Bad Request).
*   **`BCurl.java`**: The Client implementation. A command-line tool that constructs and sends binary request frames, reads the raw response payload to standard output, and implements a verbose `-v` mode for full diagnostic hexdumps.
*   **`Frame.java`**: The core data structure handling binary serialization and deserialization of the protocol frames.
*   **`HPACK.java`**: An implementation of header compression mapping the 10 most common HTTP headers to single-byte indices, while length-prefixing custom strings, saving significant network overhead.
*   **`hexdump.txt`**: A fully annotated trace of a complete Request/Response cycle proving the binary mechanisms work over the wire.

## Key Protocol Mechanisms Implemented

1. **Defended Header Framing**: I designed and defended a `24 / 8 / 8 / 31` framing layout:
   *   `Length (24)`: Balances memory footprint with generous 16MB payload limits.
   *   `Type (8) & Flags (8)`: Ample metadata overhead without eating payload bandwidth.
   *   `StreamID (31)`: Allows over 2 billion concurrent multiplexed streams over a single connection.
2. **HPACK Header Compression**: Significantly shrinks payload sizes by mapping frequent pseudo-headers (like `:method`, `:status`, `content-type`) into static 1-byte indices. 
3. **Forward Compatibility (Clean Skips)**: A strict rule of the spec is that unknown frames must be cleanly skipped. My client implementation explicitly injects a dummy `0xFF` (Unknown) frame during verbose execution to test and prove that the server safely jumps over it without crashing, leaving the door open for a "Version 2".

## How to Run

Ensure you have Java installed on your system.

**1. Compile the code:**
```bash
javac *.java
```

**2. Start the Server:**
Run the server pointing to a document root (e.g., `./www`) and a port (e.g., `9000`).
*On Windows:*
```cmd
.\bserve.bat .\www 9000
```
*On Unix/Linux:*
```bash
./bserve ./www 9000
```

**3. Run the Client:**
Execute a request against the server. The `-v` flag enables a full visual hexdump of the frames sent and received.
*On Windows:*
```cmd
.\bcurl.bat -v localhost:9000/index.html
```
*On Unix/Linux:*
```bash
./bcurl -v localhost:9000/index.html
```
