package messages;

import java.io.Serializable;

public record UploadHelloMessage(int userId, String filename, boolean dma) implements Serializable {}
