package messages;

import java.io.Serializable;
import java.util.List;

public record FilesMessage(
        List<FileInfo> files
) implements Serializable {}