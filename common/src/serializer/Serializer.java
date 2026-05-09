package serializer;

import messages.Constants;

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

    public static void writeMessage(SocketChannel channel, byte[] payload) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(4).putInt(payload.length);
        header.flip();
        writeFully(channel, header);
        writeFully(channel, ByteBuffer.wrap(payload));
    }

    public static byte[] readMessage(SocketChannel channel) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(4);
        readFully(channel, header);
        header.flip();
        int length = header.getInt();
        if (length < 0) {
            throw new IOException("Negative message length: " + length);
        }
        ByteBuffer payload = ByteBuffer.allocate(length);
        readFully(channel, payload);
        return payload.array();
    }

    public static void writeString(SocketChannel channel, String s) throws IOException {
        writeMessage(channel, s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static String readString(SocketChannel channel) throws IOException {
        return new String(readMessage(channel), java.nio.charset.StandardCharsets.UTF_8);
    }

    public static List<Integer> getFileHashses(File file) {
        if (!file.isFile()) {
            return new ArrayList<>();
        }
        long len = file.length();

        int chunksAmount = (int)((len + Constants.CHUNK_SIZE - 1) / Constants.CHUNK_SIZE);
        List<Integer> hashes = new ArrayList<>((int)chunksAmount);


        try (FileInputStream fis = new FileInputStream(file.getAbsolutePath())) {
            for (int i = 0; i < chunksAmount; i++) {
                int expectedLen = (int)(i < (chunksAmount - 1) ? Constants.CHUNK_SIZE : len % Constants.CHUNK_SIZE);
                byte[] chunkBuffer = new byte[expectedLen];
                if (fis.read(chunkBuffer) != expectedLen) {
                    throw new Exception("Ошибка при чтении файла по чанкам");
                }

                hashes.add(Arrays.hashCode(chunkBuffer));
            }
            return hashes;
        } catch (Exception exc) {
            System.err.println(exc.toString());
        }
        return new ArrayList<>();
    }
}
