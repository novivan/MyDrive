package messages;

import java.util.List;

public record DirDifference(
        List<String> toDeleteFilenames,
        List<String> toRequestFilenames
) {
}
