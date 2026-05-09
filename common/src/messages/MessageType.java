package messages;


public enum MessageType {
    HELLO,
    HELLO_ACK,
    FILES_LIST,
    FILES_REQUEST,
    FILE_HEADER,
    FILE_CHUNK,
    FILE_END,
    SYNC_DONE,
    ERROR,
    // заголовки для соединений с загрузкой на сервак
    UPLOAD_HELLO,
    UPLOAD_ACK,
    UPLOADS_DONE;

    private static final MessageType[] VALUES = values();

    public byte code() {
        return (byte) ordinal();
    }

    public static MessageType fromCode(byte code) {
        int idx = code & 0xFF;
        if (idx < 0 || idx >= VALUES.length) {
            throw new IllegalArgumentException("Unknown message type code: " + code);
        }
        return VALUES[idx];
    }
}
