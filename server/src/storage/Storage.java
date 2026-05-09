package storage;

import messages.Constants;
import serializer.Serializer;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

public class Storage {
    private static Storage instance;

    public static Storage getInstance() {
        if (instance == null) {
            instance = new Storage();
        }
        return instance;
    }

    // тут нужно будет хорошенько расписать про то, как парсить папку storage при старте
    private Map<Integer, UserStorage> usersStorages;

    private Storage() {
        usersStorages = new HashMap<>();
        File storageDir = new File("storage");

        var usersDirs = storageDir.listFiles();
        for (int i = 0; i < usersDirs.length; i++) {
            File userDir = usersDirs[i];
            
            // на случай, если редактор добавляет рандомные файлы
            Integer userId = null;
            try {
                userId = Integer.parseInt(userDir.getName());
            } catch (NumberFormatException e) {
                continue;
            }
            UserStorage userStorage = new UserStorage(userId);

            var userFiles = userDir.listFiles();
            for (int j = 0; j < userFiles.length; j++) {
                File file = userFiles[j];
                Long fileLength = file.length();
                FileInfo fileInfo = new FileInfo(
                        file.getName(),
                        fileLength,
                        Serializer.getFileHashses(file)
                );
                userStorage.addFile(fileInfo);
            }
            usersStorages.put(userId, userStorage);
        }
    }

    public Map<Integer, UserStorage> getUsersStorages() {
        return usersStorages;
    }

    public void createFile(Integer userId, String filename, byte[] arr) {
        String newFilePath = "storage/" + userId.toString() + "/" + filename;
        try (FileOutputStream fos = new FileOutputStream(newFilePath)) {
            fos.write(arr);
            File file = new File(newFilePath);
            usersStorages.get(userId).addFile(new FileInfo(filename, (long)arr.length, Serializer.getFileHashses(file)));
        } catch (Exception e) {
            System.err.println(e.toString());
            e.printStackTrace();
        }
    }

    public void deleteFile(Integer userId, String filename) {
        usersStorages.get(userId).deleteFile(filename);
        // реально удаляем
        File storageDir = new File("storage/" + userId.toString());
        var usersDirs = storageDir.listFiles();
        for (int i = 0; i < usersDirs.length; i++) {
            File userDir = usersDirs[i];
            if (userDir.getName().equals(filename)) {
                userDir.delete();
                break;
            }
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
