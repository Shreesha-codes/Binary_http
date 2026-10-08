# Binary HTTP Protocol Specification

## 1. Introduction
This document describes a simple binary protocol for HTTP-like communication over TCP, serving as an educational implementation.

## 2. Framing
All communication is done via frames. Each frame has a fixed 9-byte header followed by a variable-length payload.

### 2.1. Frame Header
The frame header consists of 9 bytes (72 bits):
```text
 +-----------------------------------------------+
 |                 Length (24)                   |
 +---------------+---------------+---------------+
 |   Type (8)    |   Flags (8)   |
 +---------------+---------------+-------------------------------+
 |                   Stream Identifier (32)                      |
 +---------------------------------------------------------------+
```

**Defense of Header Field Widths:**
*   **Length (24 bits)**: Allows a maximum payload size of 16.7MB per frame. This is a perfect balance—it's large enough to avoid extreme framing overhead for large files, but small enough to prevent a single frame from monopolizing memory or the network interface buffer.
*   **Type (8 bits)**: Supports up to 256 distinct frame types. This provides ample room for future protocol extensions without inflating the header size unnecessarily.
*   **Flags (8 bits)**: Allows up to 8 boolean flags per frame type (e.g., `END_STREAM`, `END_HEADERS`). This allows encoding metadata directly into the header without eating into the payload.
*   **Stream Identifier (32 bits)**: Provides over 2 billion independent streams per connection, enabling massive multiplexing over a single long-lived TCP connection without running out of IDs.

### 2.2. Unknown Frame Types
**CRITICAL RULE**: A receiver meeting a frame type it does not know MUST cleanly skip it. This is done by reading and discarding `Length` bytes of the payload.

## 3. Frame Types

### 3.1. REQUEST Frame (Type=0x01)
Sent by the client to initiate a request.
Payload format:
*   Method (1 byte index from Static Table, or 0x00 followed by length-prefixed string)
*   Path (Length-prefixed string: 2-byte length, then bytes)

### 3.2. RESPONSE Frame (Type=0x02)
Sent by the server to send the response status.
Payload format:
*   Status Code (2 bytes, unsigned integer, e.g., 200, 404)

### 3.3. HEADERS Frame (Type=0x03)
Used to send headers. Can be sent by client or server.
Flags:
*   `0x01` END_HEADERS: Indicates this is the last header frame for the request/response.
Payload format: A sequence of header fields. Each field is:
*   Name Index (1 byte): If 1-10, it maps to the Static Table. If 0x00, it is followed by a length-prefixed string (1-byte length, then bytes) for a custom header name.
*   Value Length (2 bytes)
*   Value (bytes)

### 3.4. DATA Frame (Type=0x04)
Contains application data (body).
Flags:
*   `0x01` END_STREAM: Indicates this is the last frame for the stream.
Payload: The raw bytes.

## 4. Header Compression (Static Table)
To minimize overhead, the following 10 header names are pre-numbered:
1. `:method`
2. `:path`
3. `:status`
4. `content-type`
5. `content-length`
6. `host`
7. `user-agent`
8. `accept`
9. `server`
10. `connection`

When sending one of these names, its index (1-10) is sent as a single byte. For other names, `0x00` is sent, followed by a 1-byte length and the name string.

## 5. Flow
**Client**:
1. Connects to the server.
2. Sends REQUEST frame (e.g., Method=GET, Path=/index.html).
3. (Optional) Sends HEADERS frame(s) with END_HEADERS=1.
4. (Optional) Sends DATA frame(s) if there is a body, ending with END_STREAM=1.
5. Keeps connection open.

**Server**:
1. Accepts connection.
2. Reads frames until an END_HEADERS flag or REQUEST is complete.
3. Looks up the requested path in the specified root directory.
4. If found, sends RESPONSE frame (Status=200).
5. Sends HEADERS frame (Content-Length, Content-Type) with END_HEADERS=1.
6. Sends DATA frame(s) with the file contents, ending with END_STREAM=1.
7. If file not found, sends RESPONSE(404) and END_STREAM=1 in a DATA frame (or END_HEADERS=1).
8. If the frame is malformed, sends RESPONSE(400) and closes connection.
9. Keeps connection open for further requests on new stream IDs.
