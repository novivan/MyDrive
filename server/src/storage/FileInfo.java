package storage;

import java.util.List;

public record FileInfo ( // все размеры в байтах
     String name,
     Long size,
     List<Integer> hashes // хочу заменить на то-то типа SHA
) {}
