package storage;

import messages.FilesMessage;
import messages.FileInfo;
import serializer.Serializer;
import state.AppState;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Storage {
    private static Storage instance;

    private final String path;
    private final File syncDir;
    private final List<File> files;


    private Storage(String syncDirPath) {
        path = syncDirPath;
        syncDir = new File(path);
        files = new ArrayList<>();

        for (File file: Objects.requireNonNull(syncDir.listFiles())) {
            if (file.isFile()) {
                files.add(file);
            }
        }
    }

    public static synchronized Storage getInstance() {
        if (instance == null) {
            instance = new Storage(AppState.getInstance().getSyncDirPath());
        }
        return instance;
    }

    public FilesMessage prepareSyncMessage() {
        return new FilesMessage(
                files.stream()
                        .map(fl -> new FileInfo(
                                fl.getName(),
                                fl.length(),
                                Serializer.getFileHashses(fl)
                        )).toList()
        );
    }

    public byte[] readFileBytes(String filename) throws IOException {
        return Files.readAllBytes(Path.of(path, filename));
    }
}
