package messages;

import java.io.Serializable;

public record HelloAckMessage(int userId) implements Serializable {}
