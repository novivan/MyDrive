package connection;

import messages.Constants;
import messages.FileHeader;
import messages.FilesMessage;
import messages.HelloAckMessage;
import messages.HelloMessage;
import messages.MessageType;
import messages.RequestFilesMessage;
import messages.SyncResultMessage;
import messages.UploadHelloMessage;
import serializer.Serializer;
import state.AppState;
import storage.Storage;

import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;

public class Connection {
    private static AppState state;
    private static Storage storage;

    private void updateState() {
        state = AppState.getInstance();
        storage = Storage.getInstance();
    }

    public Connection() {
        updateState();
    }

    private class NioClient {
        protected void connect() {
            var config = Connection.state;
            var userId = config.getUserId();
            try (SocketChannel socketChannel = SocketChannel.open()) {
                socketChannel.connect(new InetSocketAddress(config.getServerAddr(), config.getServerPort()));
                System.out.println("Connected to server");

                //сообщает свой ID
                Serializer.writeObject(socketChannel, MessageType.HELLO, new HelloMessage(userId));
                Serializer.Envelope ack = Serializer.readEnvelopeExpect(socketChannel, MessageType.HELLO_ACK);
                HelloAckMessage helloAck = (HelloAckMessage) Serializer.deserialize(ack.payload);
                int assignedId = helloAck.userId();
                if (userId == -1) {
                    System.out.printf("Сервер назначил нам id: %d\n", assignedId);
                    config.setUserId(assignedId);
                } else if (assignedId != userId) {
                    System.out.printf("Сервер изменил наш id: %d -> %d\n", userId, assignedId);
                    config.setUserId(assignedId);
                } else {
                    System.out.printf("Уже есть id: %d\n", assignedId);
                }

                // клиент сообщает серверу список файлов
                FilesMessage filesMessage = storage.prepareSyncMessage();
                Serializer.writeObject(socketChannel, MessageType.FILES_LIST, filesMessage);
                System.out.println("Sent list of files to server");

                // сервер запрашивает файлы 
                Serializer.Envelope reqEnv = Serializer.readEnvelopeExpect(socketChannel, MessageType.FILES_REQUEST);
                RequestFilesMessage request = (RequestFilesMessage) Serializer.deserialize(reqEnv.payload);
                System.out.printf("Server requested %d file(s)\n", request.filenames().size());

                // параллельная загрузка файлов
                int finalUserId = assignedId;
                long t0 = System.nanoTime();
                long totalBytes = uploadFilesInParallel(request.filenames(), finalUserId);
                long elapsedNs = System.nanoTime() - t0;

                // сообщаем серверу что закончили загрузки
                Serializer.writeEnvelope(socketChannel, MessageType.UPLOADS_DONE, new byte[0]);

                // итог
                Serializer.Envelope doneEnv = Serializer.readEnvelopeExpect(socketChannel, MessageType.SYNC_DONE);
                SyncResultMessage result = (SyncResultMessage) Serializer.deserialize(doneEnv.payload);
                System.out.printf("Sync result: %s\n", result.message());
                if (totalBytes > 0) {
                    double seconds = elapsedNs / 1_000_000_000.0;
                    double mbps = (totalBytes / (1024.0 * 1024.0)) / seconds;
                    System.out.printf("Throughput: %.2f MiB/s (%d bytes in %.3f s, threads=%d, dma=%s)\n",
                            mbps, totalBytes, seconds,
                            Math.min(state.getMaxServerConnections(), request.filenames().size()),
                            state.isDmaEnabled());
                }

                // socketChannel.close(); происходит автоматически из-за try with resources
            } catch (Exception e) {
                System.err.println(e.toString());
                e.printStackTrace();
            }
        }
    }

    private long uploadFilesInParallel(List<String> filenames, int userId) throws Exception {
        if (filenames.isEmpty()) {
            return 0L;
        }
        int max = state.getMaxServerConnections();
        int poolSize = Math.max(1, Math.min(max, filenames.size()));
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        List<Future<Long>> futures = new ArrayList<>();
        long totalBytes = 0L;
        try {
            for (String filename : filenames) {
                futures.add(pool.submit(() -> {
                    try {
                        return uploadOneFile(userId, filename);
                    } catch (Exception e) {
                        System.err.printf("\tFailed to upload \"%s\": %s\n", filename, e);
                        return 0L;
                    }
                }));
            }
            for (Future<Long> f : futures) {
                try {
                    totalBytes += f.get();
                } catch (Exception e) {
                    System.err.printf("Upload task error: %s\n", e);
                }
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(1, TimeUnit.MINUTES);
        }
        return totalBytes;
    }

    private long uploadOneFile(int userId, String filename) throws Exception {
        java.io.File file = new java.io.File(state.getSyncDirPath() + "/" + filename);
        if (!file.exists() || !file.isFile()) {
            throw new java.io.FileNotFoundException("File not found in sync dir: " + filename);
        }
        long size = file.length();
        boolean dma = state.isDmaEnabled();
        long t0 = System.nanoTime();
        try (SocketChannel ch = SocketChannel.open()) {
            ch.connect(new InetSocketAddress(state.getServerAddr(), state.getServerPort()));
            Serializer.writeObject(ch, MessageType.UPLOAD_HELLO, new UploadHelloMessage(userId, filename, dma));
            Serializer.writeObject(ch, MessageType.FILE_HEADER, new FileHeader(filename, size));

            if (dma) {
                try (RandomAccessFile raf = new RandomAccessFile(file, "r");
                     FileChannel fc = raf.getChannel()) {
                    long pos = 0;
                    while (pos < size) {
                        long n = fc.transferTo(pos, size - pos, ch);
                        if (n <= 0) {
                            throw new java.io.IOException("transferTo returned " + n);
                        }
                        pos += n;
                    }
                }
            } else {
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buf = new byte[Constants.NETWORK_CHUNK_SIZE];
                    long sent = 0;
                    int n;
                    while ((n = fis.read(buf)) > 0) {
                        if (n == buf.length) {
                            Serializer.writeEnvelope(ch, MessageType.FILE_CHUNK, buf);
                        } else {
                            byte[] tail = new byte[n];
                            System.arraycopy(buf, 0, tail, 0, n);
                            Serializer.writeEnvelope(ch, MessageType.FILE_CHUNK, tail);
                        }
                        sent += n;
                    }
                    if (sent != size) {
                        throw new java.io.IOException("File size changed during send: " + filename);
                    }
                }
                Serializer.writeEnvelope(ch, MessageType.FILE_END, new byte[0]);
            }
            Serializer.readEnvelopeExpect(ch, MessageType.UPLOAD_ACK);
            double secs = (System.nanoTime() - t0) / 1_000_000_000.0;
            double mbps = secs > 0 ? (size / (1024.0 * 1024.0)) / secs : 0.0;
            System.out.printf("\tSent file \"%s\" (%d bytes) in %.3fs => %.2f MiB/s%s\n",
                    filename, size, secs, mbps, dma ? " [DMA]" : "");
        }
        return size;
    }

    public void connect() {
        new NioClient().connect();
    }
}
