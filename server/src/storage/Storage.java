package storage;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private Map<Long, UserStorage> usersStorages;
    private final Long CHUNK_SIZE = 8192L; // пока так, потом мб сделаю для каждого файла свой подсчет

    private Storage() {
        usersStorages = new HashMap<>();
        File storageDir = new File("storage");

        var usersDirs = storageDir.listFiles();
        for (int i = 0; i < usersDirs.length; i++) {
            File userDir = usersDirs[i];

            Long userId = Long.parseLong(userDir.getName());
            UserStorage userStorage = new UserStorage(userId);

            var userFiles = userDir.listFiles();
            for (int j = 0; j < userFiles.length; j++) {
                File file = userFiles[j];
                Long fileLength = file.length();
                Long chanksAmount = (fileLength + CHUNK_SIZE - 1) / CHUNK_SIZE;
                FileInfo fileInfo = new FileInfo(
                        file.getName(),
                        fileLength,
                        CHUNK_SIZE,
                        chanksAmount,
                        getFileChunksHashes(file, chanksAmount)
                );
                userStorage.addFile(fileInfo);
            }
            usersStorages.put(userId, userStorage);
        }
    }

    // переделать на норм хэши
    private List<Long> getFileChunksHashes(File file, Long chunksAmount) {
        new ArrayList<Long>(1);
        var ret = new ArrayList<Long>(chunksAmount.intValue());
        for (int i = 0; i < chunksAmount; i++) {
            ret.add(0L /* TODO: переделать */);
        }
        return ret;
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
