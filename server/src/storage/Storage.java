package storage;

import serializer.Serializer;
import state.AppState;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Storage {
    private static volatile Storage instance;

    public static synchronized Storage getInstance() {
        if (instance == null) {
            instance = new Storage();
        }
        return instance;
    }

    private final Map<Integer, UserStorage> usersStorages;
    private final Map<Integer, Lock> userLocks;
    private final String storageRoot;

    private Storage() {
        usersStorages = new ConcurrentHashMap<>();
        userLocks = new ConcurrentHashMap<>();
        storageRoot = AppState.getInstance().getStorageDir();

        File storageDir = new File(storageRoot);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        var usersDirs = storageDir.listFiles();
        if (usersDirs == null) {
            return;
        }
        for (int i = 0; i < usersDirs.length; i++) {
            File userDir = usersDirs[i];
            if (!userDir.isDirectory()) continue;

            Integer userId = null;
            try {
                userId = Integer.parseInt(userDir.getName());
            } catch (NumberFormatException e) {
                continue;
            }
            UserStorage userStorage = new UserStorage(userId);

            var userFiles = userDir.listFiles();
            if (userFiles != null) {
                for (int j = 0; j < userFiles.length; j++) {
                    File file = userFiles[j];
                    if (!file.isFile()) continue;
                    Long fileLength = file.length();
                    FileInfo fileInfo = new FileInfo(
                            file.getName(),
                            fileLength,
                            Serializer.getFileHashses(file)
                    );
                    userStorage.addFile(fileInfo);
                }
            }
            usersStorages.put(userId, userStorage);
        }
    }

    public Map<Integer, UserStorage> getUsersStorages() {
        return usersStorages;
    }

    public UserStorage getOrCreateUserStorage(Integer userId) {
        return usersStorages.computeIfAbsent(userId, id -> {
            File userDir = new File(storageRoot + "/" + id.toString());
            if (!userDir.exists() && !userDir.mkdirs()) {
                System.err.println("Failed to create user dir: " + userDir.getAbsolutePath());
            }
            return new UserStorage(id);
        });
    }

    public Lock lockFor(Integer userId) {
        return userLocks.computeIfAbsent(userId, id -> new ReentrantLock());
    }

    public void createFile(Integer userId, String filename, byte[] arr) {
        String newFilePath = storageRoot + "/" + userId.toString() + "/" + filename;
        try (FileOutputStream fos = new FileOutputStream(newFilePath)) {
            fos.write(arr);
            File file = new File(newFilePath);
            getOrCreateUserStorage(userId).addFile(
                    new FileInfo(filename, (long) arr.length, Serializer.getFileHashses(file)));
        } catch (Exception e) {
            System.err.println(e.toString());
            e.printStackTrace();
        }
    }

    public Path getTempPath(Integer userId, String filename) {
        return Path.of(storageRoot, userId.toString(), filename + ".part");
    }

    public Path getFinalPath(Integer userId, String filename) {
        return Path.of(storageRoot, userId.toString(), filename);
    }

    public FileOutputStream openTempForWrite(Integer userId, String filename) throws IOException {
        Path tmp = getTempPath(userId, filename);
        Files.createDirectories(tmp.getParent());
        Files.deleteIfExists(tmp);
        return new FileOutputStream(tmp.toFile());
    }

    public void commitFile(Integer userId, String filename, long expectedSize) throws IOException {
        Path tmp = getTempPath(userId, filename);
        Path target = getFinalPath(userId, filename);
        long actual = Files.size(tmp);
        if (actual != expectedSize) {
            Files.deleteIfExists(tmp);
            throw new IOException("Size mismatch for '" + filename + "': expected="
                    + expectedSize + ", actual=" + actual);
        }
        try {
            Files.move(tmp, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception atomicFailed) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        File file = target.toFile();
        getOrCreateUserStorage(userId).addFile(
                new FileInfo(filename, expectedSize, Serializer.getFileHashses(file)));
    }

    public void abortFile(Integer userId, String filename) {
        try {
            Files.deleteIfExists(getTempPath(userId, filename));
        } catch (IOException ignored) {}
    }

    public void deleteFile(Integer userId, String filename) {
        UserStorage us = usersStorages.get(userId);
        if (us != null) us.deleteFile(filename);
        try {
            Files.deleteIfExists(getFinalPath(userId, filename));
        } catch (IOException e) {
            System.err.println("Failed to delete file: " + e);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Storage:\n{");
        for (var entry: usersStorages.entrySet()) {
            var userId = entry.getKey();
            var userStorage = entry.getValue();
            sb.append(userId).append("=").append(userStorage.toString()).append(", ");
        }
        sb.append("}\n");
        return sb.toString();
    }
}
