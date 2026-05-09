package messages;

public class Constants {
    private Constants() {}
    // для хешей
    public final static int CHUNK_SIZE = 1024 * 10; // 10 кб
    // для передачи больших файлов по сети
    public final static int NETWORK_CHUNK_SIZE = 1024 * 1024; // 1 мб
}
