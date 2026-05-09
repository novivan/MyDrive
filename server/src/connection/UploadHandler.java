package connection;

import messages.FileHeader;
import messages.MessageType;
import messages.UploadHelloMessage;
import serializer.Serializer;
import state.AppState;
import storage.Storage;

import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.nio.file.Paths;

public class UploadHandler implements Runnable {
    private final SocketChannel channel;
    private final UploadHelloMessage hello;
    private final Storage storage;

    public UploadHandler(SocketChannel channel, UploadHelloMessage hello) {
        this.channel = channel;
        this.hello = hello;
        this.storage = Storage.getInstance();
    }

    @Override
    public void run() {
        Integer userId = hello.userId();
        String filename = hello.filename();
        boolean dma = hello.dma();
        String remote;
        try {
            remote = channel.getRemoteAddress().toString();
        } catch (Exception e) {
            remote = "unknown";
        }

        long t0 = System.nanoTime();
        try (SocketChannel ch = channel) {
            storage.getOrCreateUserStorage(userId);

            Serializer.Envelope hdrEnv = Serializer.readEnvelopeExpect(ch, MessageType.FILE_HEADER);
            FileHeader header = (FileHeader) Serializer.deserialize(hdrEnv.payload);
            if (!header.name().equals(filename)) {
                throw new IllegalStateException("UPLOAD_HELLO/FILE_HEADER mismatch: '"
                        + filename + "' vs '" + header.name() + "'");
            }
            long expectedSize = header.size();

            if (dma) {
                Path partPath = Paths.get(AppState.getInstance().getStorageDir(),
                        String.valueOf(userId), filename + ".part");
                java.nio.file.Files.createDirectories(partPath.getParent());
                try (RandomAccessFile raf = new RandomAccessFile(partPath.toFile(), "rw");
                    FileChannel fc = raf.getChannel()) {
                    long pos = 0;
                    while (pos < expectedSize) {
                        long n = fc.transferFrom(ch, pos, expectedSize - pos);
                        if (n <= 0) {
                            throw new java.io.IOException("transferFrom returned " + n);
                        }
                        pos += n;
                    }
                }
            } else {
                try (FileOutputStream fos = storage.openTempForWrite(userId, filename)) {
                    long received = 0;
                    while (true) {
                        Serializer.Envelope env = Serializer.readEnvelope(ch);
                        if (env.type == MessageType.FILE_END) {
                            if (env.payload.length != 0) {
                                throw new IllegalStateException("FILE_END with non-empty payload");
                            }
                            break;
                        }
                        if (env.type != MessageType.FILE_CHUNK) {
                            throw new IllegalStateException(
                                    "Expected FILE_CHUNK or FILE_END, got " + env.type);
                        }
                        fos.write(env.payload);
                        received += env.payload.length;
                        if (received > expectedSize) {
                            throw new IllegalStateException(
                                    "Received more bytes than expected for '" + filename + "'");
                        }
                    }
                    if (received != expectedSize) {
                        throw new IllegalStateException("Size mismatch for '" + filename
                                + "': expected=" + expectedSize + ", actual=" + received);
                    }
                }
            }

            storage.commitFile(userId, filename, expectedSize);
            Serializer.writeEnvelope(ch, MessageType.UPLOAD_ACK, new byte[0]);
            double secs = (System.nanoTime() - t0) / 1_000_000_000.0;
            double mbps = secs > 0 ? (expectedSize / (1024.0 * 1024.0)) / secs : 0.0;
            System.out.printf("[%s][upload uid=%d] Received file: \"%s\" (%d bytes) in %.3fs => %.2f MiB/s%s\n",
                    remote, userId, filename, expectedSize, secs, mbps, dma ? " [DMA]" : "");
        } catch (Exception e) {
            System.err.printf("[%s][upload uid=%d] Failed to receive '%s': %s\n",
                    remote, userId, filename, e);
            storage.abortFile(userId, filename);
        }
    }
}
