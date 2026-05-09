package connection;

import messages.FilesMessage;
import messages.HelloAckMessage;
import messages.HelloMessage;
import messages.MessageType;
import messages.RequestFilesMessage;
import messages.SyncResultMessage;
import serializer.Serializer;
import state.AppState;
import storage.FileInfo;
import storage.Storage;
import storage.UserStorage;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;

public class ClientHandler implements Runnable {
    private final SocketChannel channel;
    private final HelloMessage hello;
    private final Storage storage;
    private final AppState state;

    public ClientHandler(SocketChannel channel, HelloMessage hello) {
        this.channel = channel;
        this.hello = hello;
        this.storage = Storage.getInstance();
        this.state = AppState.getInstance();
    }

    @Override
    public void run() {
        String remote;
        try {
            remote = channel.getRemoteAddress().toString();
        } catch (Exception e) {
            remote = "unknown";
        }
        System.out.printf("[%s] Client connected\n", remote);

        try (SocketChannel ch = channel) {
            // 1) получаем id пользователя
            Integer userId = hello.userId();
            if (userId == -1) {
                userId = state.produceNewUserId();
            }
            Serializer.writeObject(ch, MessageType.HELLO_ACK, new HelloAckMessage(userId));
            System.out.printf("[%s] Handshake done, userId=%d\n", remote, userId);

            storage.getOrCreateUserStorage(userId);

            // 2) получаем список файлов
            Serializer.Envelope filesEnv = Serializer.readEnvelopeExpect(ch, MessageType.FILES_LIST);
            FilesMessage filesMessage = (FilesMessage) Serializer.deserialize(filesEnv.payload);

            // 3) синхронизируем
            Lock userLock = storage.lockFor(userId);
            userLock.lock();
            try {
                processSync(ch, remote, userId, filesMessage);
            } finally {
                userLock.unlock();
            }
        } catch (Exception e) {
            System.err.printf("[%s] Handler error: %s\n", remote, e);
            e.printStackTrace();
        }
    }

    private void processSync(SocketChannel ch,
                             String remote,
                             Integer userId,
                             FilesMessage filesMessage) throws Exception {
        UserStorage userStorage = storage.getOrCreateUserStorage(userId);

        List<messages.FileInfo> clientFiles = new ArrayList<>(filesMessage.files());
        Collections.sort(clientFiles, Comparator.comparing(messages.FileInfo::name));

        List<FileInfo> filesOnStorage = new ArrayList<>(userStorage.getFiles());
        Collections.sort(filesOnStorage, Comparator.comparing(FileInfo::name));

        List<String> toRequest = new ArrayList<>();
        List<String> toDelete = new ArrayList<>();

        int storageIndex = 0, messageIndex = 0;
        while (storageIndex < filesOnStorage.size() && messageIndex < clientFiles.size()) {
            FileInfo storageFile = filesOnStorage.get(storageIndex);
            messages.FileInfo messageFile = clientFiles.get(messageIndex);

            int cmp = storageFile.name().compareTo(messageFile.name());
            if (cmp == 0) {
                boolean isOk = storageFile.size().equals(messageFile.size())
                        && storageFile.hashes().equals(messageFile.hashes());
                if (!isOk) {
                    toRequest.add(messageFile.name());
                    toDelete.add(storageFile.name());
                }
                storageIndex++;
                messageIndex++;
            } else if (cmp < 0) {
                toDelete.add(storageFile.name());
                storageIndex++;
            } else {
                toRequest.add(messageFile.name());
                messageIndex++;
            }
        }
        while (storageIndex < filesOnStorage.size()) {
            toDelete.add(filesOnStorage.get(storageIndex++).name());
        }
        while (messageIndex < clientFiles.size()) {
            toRequest.add(clientFiles.get(messageIndex++).name());
        }

        for (String filename : toDelete) {
            storage.deleteFile(userId, filename);
        }

        // 4) запрашиваем файлы
        Serializer.writeObject(ch, MessageType.FILES_REQUEST, new RequestFilesMessage(toRequest));
        System.out.printf("[%s] Requesting %d file(s)\n", remote, toRequest.size());

        // 5) ждём загрузки
        if (!toRequest.isEmpty()) {
            Serializer.readEnvelopeExpect(ch, MessageType.UPLOADS_DONE);
        } else {
            Serializer.readEnvelopeExpect(ch, MessageType.UPLOADS_DONE);
        }

        // 6) считаем сколько файлов реально оказалось в хранилище из запрошенных
        Set<String> nowOnStorage = new HashSet<>();
        for (FileInfo fi : userStorage.getFiles()) {
            nowOnStorage.add(fi.name());
        }
        int uploaded = 0;
        for (String name : toRequest) {
            if (nowOnStorage.contains(name)) {
                uploaded++;
            }
        }

        SyncResultMessage result = new SyncResultMessage(uploaded, toDelete.size(),
                "OK; uploaded=" + uploaded + "/" + toRequest.size()
                        + ", deleted=" + toDelete.size());
        Serializer.writeObject(ch, MessageType.SYNC_DONE, result);

        System.out.printf("[%s] Sync finished. Uploaded=%d/%d, Deleted=%d\n",
                remote, uploaded, toRequest.size(), toDelete.size());
    }
}
