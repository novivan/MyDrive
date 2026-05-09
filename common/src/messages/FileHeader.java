package messages;

import java.io.Serializable;

public record FileHeader(String name, long size) implements Serializable {}
