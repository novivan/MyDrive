package storage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class UserStorage {
    private final Integer userId; // по этому же id будем называть папку пользователя
    private final List<FileInfo> files;

    public UserStorage(Integer userId) {
        this.userId = userId;
        this.files = new CopyOnWriteArrayList<>();
    }

    public void addFile(FileInfo fileInfo) {
        this.files.removeIf(fi -> fi.name().equals(fileInfo.name()));
        this.files.add(fileInfo);
    }

    public List<FileInfo> getFiles() {
        return this.files;
    }

    public void deleteFile(String filename) {
        this.files.removeIf(fileInfo -> fileInfo.name().equals(filename));
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("UserStorage: {");
        sb.append("userId=").append(userId);
        sb.append(", files=").append(files);
        sb.append("}");
        return sb.toString();
    }
}
