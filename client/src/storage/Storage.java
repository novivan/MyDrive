package storage;

import messages.FilesMessage;
import messages.FileInfo;
import serializer.Serializer;
import state.AppState;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Storage {
    private static Storage instance;

    private final String path;
    private final File syncDir;

    private Storage(String syncDirPath) {
        path = syncDirPath;
        syncDir = new File(path);
        if (!syncDir.exists() && !syncDir.mkdirs()) {
            System.err.println("Failed to create sync dir: " + syncDir.getAbsolutePath());
        }
    }

    public static synchronized Storage getInstance() {
        if (instance == null) {
            instance = new Storage(AppState.getInstance().getSyncDirPath());
        }
        return instance;
    }

    public String getPath() {
        return path;
    }


    public FilesMessage prepareSyncMessage() {
        List<File> files = new ArrayList<>();
        File[] listed = syncDir.listFiles();
        if (listed != null) {
            for (File file : listed) {
                if (file.isFile()) {
                    files.add(file);
                }
            }
        }
        return new FilesMessage(
                files.stream()
                        .map(fl -> new FileInfo(
                                fl.getName(),
                                fl.length(),
                                Serializer.getFileHashses(fl)
                        )).toList()
        );
    }
}
