package serializer;

import messages.Constants;
import messages.MessageType;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Serializer {
    public static byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    public static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            return ois.readObject();
        }
    }

    private static void readFully(SocketChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            int n = channel.read(buffer);
            if (n < 0) {
                throw new EOFException("Channel closed before reading expected bytes (still " + buffer.remaining() + " left)");
            }
        }
    }

    private static void writeFully(SocketChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    public static final class Envelope {
        public final MessageType type;
        public final byte[] payload;

        public Envelope(MessageType type, byte[] payload) {
            this.type = type;
            this.payload = payload;
        }
    }

    public static void writeEnvelope(SocketChannel channel, MessageType type, byte[] payload) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(5);
        header.put(type.code());
        header.putInt(payload.length);
        header.flip();
        writeFully(channel, header);
        if (payload.length > 0) {
            writeFully(channel, ByteBuffer.wrap(payload));
        }
    }

    public static Envelope readEnvelope(SocketChannel channel) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(5);
        readFully(channel, header);
        header.flip();
        MessageType type = MessageType.fromCode(header.get());
        int length = header.getInt();
        if (length < 0) {
            throw new IOException("Negative envelope length: " + length);
        }
        byte[] payload = new byte[length];
        if (length > 0) {
            ByteBuffer buf = ByteBuffer.wrap(payload);
            readFully(channel, buf);
        }
        return new Envelope(type, payload);
    }

    public static void writeObject(SocketChannel channel, MessageType type, Object obj) throws IOException {
        writeEnvelope(channel, type, serialize(obj));
    }

    public static Envelope readEnvelopeExpect(SocketChannel channel, MessageType expected) throws IOException {
        Envelope env = readEnvelope(channel);
        if (env.type != expected) {
            throw new IOException("Protocol error: expected " + expected + " but got " + env.type);
        }
        return env;
    }


    public static List<Integer> getFileHashses(File file) {
        if (!file.isFile()) {
            return new ArrayList<>();
        }
        long len = file.length();
        if (len == 0) {
            return new ArrayList<>();
        }

        int chunksAmount = (int) ((len + Constants.CHUNK_SIZE - 1) / Constants.CHUNK_SIZE);
        List<Integer> hashes = new ArrayList<>(chunksAmount);

        try (FileInputStream fis = new FileInputStream(file.getAbsolutePath())) {
            long remaining = len;
            byte[] chunkBuffer = new byte[Constants.CHUNK_SIZE];
            for (int i = 0; i < chunksAmount; i++) {
                int expectedLen = (int) Math.min(Constants.CHUNK_SIZE, remaining);
                int read = 0;
                while (read < expectedLen) {
                    int n = fis.read(chunkBuffer, read, expectedLen - read);
                    if (n < 0) {
                        throw new IOException("Unexpected EOF while hashing chunk " + i);
                    }
                    read += n;
                }
                if (expectedLen == Constants.CHUNK_SIZE) {
                    hashes.add(Arrays.hashCode(chunkBuffer));
                } else {
                    byte[] tail = Arrays.copyOf(chunkBuffer, expectedLen);
                    hashes.add(Arrays.hashCode(tail));
                }
                remaining -= expectedLen;
            }
            return hashes;
        } catch (Exception exc) {
            System.err.println(exc.toString());
        }
        return new ArrayList<>();
    }
}
