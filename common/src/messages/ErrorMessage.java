package messages;

import java.io.Serializable;

public record ErrorMessage(String text) implements Serializable {}
