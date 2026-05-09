package state;

import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import java.util.Properties;

public class AppState {
    private static AppState instance;
    Properties properties;

    private final String SYNC_DIR_PATH = "sync.dir.path";
    private final String SERVER_ADDR = "server.addr";
    private final String SERVER_PORT = "server.port";
    private final String MAX_SERVER_CONNECTIONS = "max.server.connections";
    private final String USER_ID = "user.id";

    private final AtomicReference<String> syncDirPath;
    private final AtomicReference<String> serverAddr;
    private final AtomicInteger serverPort;
    private final AtomicInteger maxServerConnections;
    private final AtomicInteger userId;

    private AppState() {
        properties = new Properties();
        try (FileInputStream fis = new FileInputStream("properties/config.properties")) {
            properties.load(fis);
        } catch (Exception e) {
            System.err.println(e.toString());
            e.printStackTrace();
        }

        syncDirPath = new AtomicReference<>(properties.getProperty(SYNC_DIR_PATH));
        serverAddr = new AtomicReference<>(properties.getProperty(SERVER_ADDR));
        serverPort = new AtomicInteger(Integer.parseInt(properties.getProperty(SERVER_PORT)));
        maxServerConnections = new AtomicInteger(Integer.parseInt(properties.getProperty(MAX_SERVER_CONNECTIONS)));
        // TODO: при первом запуске userId хочу оставить пустым, отправить запрос серваку, взять первый свободный id
        // пока что просто заглушка, ну или кстати можно будет подняться на уровеь выше и понять, что -1 нехорошо
        userId = new AtomicInteger(Integer.parseInt(Optional.ofNullable(properties.getProperty(USER_ID)).orElse("-1")));
    }

    public static synchronized AppState getInstance() {
        if (instance == null) {
            instance = new AppState();
        }
        return instance;
    }

    private synchronized void saveToFile() {
        try (FileOutputStream fos = new FileOutputStream("properties/config.properties")) {
            properties.store(fos, null);
        } catch (Exception e) {
            System.err.println(e.toString());
            e.printStackTrace();
        }
    }

    public String getSyncDirPath() {
        return syncDirPath.get();
    }

    public String getServerAddr() {
        return serverAddr.get();
    }

    public Integer getServerPort() {
        return serverPort.get();
    }

    public int getMaxServerConnections() {
        return maxServerConnections.get();
    }

    public int getUserId() {
        return userId.get();
    }

    public void setUserId(int id) {
        userId.set(id);
        properties.setProperty(USER_ID, String.valueOf(userId.get()));
        saveToFile();
    }
}
