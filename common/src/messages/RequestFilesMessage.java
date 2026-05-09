package messages;

import java.io.Serializable;
import java.util.List;

public record RequestFilesMessage(List<String> filenames) implements Serializable {}
