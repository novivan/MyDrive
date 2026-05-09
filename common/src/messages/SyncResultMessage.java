package messages;

import java.io.Serializable;

public record SyncResultMessage(int filesUploaded, int filesDeleted, String message)
        implements Serializable {}
