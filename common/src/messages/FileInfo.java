package messages;

import java.io.Serializable;
import java.util.List;

public record FileInfo (
        String name,
        Long size,
        List<Integer> hashes
) implements Serializable {}
