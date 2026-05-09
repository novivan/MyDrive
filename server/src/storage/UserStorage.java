package storage;

import java.util.ArrayList;
import java.util.List;

public class UserStorage {
    private final Integer userId; // по этому же id будем называть папку пользователя
    private List<FileInfo> files; //тут вот хз список или мапу сделать, вопрос в сопоставлении с реальной папкой будет. Мб можно по имени файла посортить

    public UserStorage(Integer userId) {
        this.userId = userId;
        this.files = new ArrayList<>();
    }

    public void addFile(FileInfo fileInfo) {
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
