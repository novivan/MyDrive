package messages;

import java.io.Serializable;

public record HelloMessage(int userId) implements Serializable {}
