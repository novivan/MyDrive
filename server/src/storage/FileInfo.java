package storage;

import java.util.List;

public record FileInfo ( // все размеры в байтах
     String name,
     Long size,
     Long chunkSize,
     Long chunksAmount,
     List<Long> hashes // хочу заменить на то-то типа SHA
) {}
