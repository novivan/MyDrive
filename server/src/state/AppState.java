package state;

import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import java.util.Properties;

public class AppState {
    private static AppState instance;

    private final Properties stateProps;
    private final Properties serverProps;

    private static final String STATE_FILE = "properties/state.properties";
    private static final String SERVER_CONFIG_FILE = "properties/server.properties";

    private static final String LAST_USER_ID = "last.user.id";
    private static final String STORAGE_DIR = "storage.dir";
    private static final String SERVER_HOST = "server.host";
    private static final String SERVER_PORT = "server.port";

    private final AtomicInteger lastUserId;

    private final AtomicReference<String> storageDir;
    private final AtomicReference<String> serverHost;
    private final AtomicInteger serverPort;

    private AppState() {
        stateProps = new Properties();
        try (FileInputStream fis = new FileInputStream(STATE_FILE)) {
            stateProps.load(fis);
        } catch (Exception e) {
            System.err.println(e.toString());
            e.printStackTrace();
        }

        serverProps = new Properties();
        try (FileInputStream fis = new FileInputStream(SERVER_CONFIG_FILE)) {
            serverProps.load(fis);
        } catch (Exception e) {
            System.err.println(e.toString());
            e.printStackTrace();
        }

        lastUserId = new AtomicInteger(Integer.parseInt(stateProps.getProperty(LAST_USER_ID, "0")));

        storageDir = new AtomicReference<>(serverProps.getProperty(STORAGE_DIR, "storage"));
        serverHost = new AtomicReference<>(serverProps.getProperty(SERVER_HOST, "localhost"));
        serverPort = new AtomicInteger(Integer.parseInt(serverProps.getProperty(SERVER_PORT, "9000")));
    }

    public static synchronized AppState getInstance() {
        if (instance == null) {
            instance = new AppState();
        }
        return instance;
    }

    private synchronized void saveStateToFile() {
        try (FileOutputStream fos = new FileOutputStream(STATE_FILE)) {
            stateProps.store(fos, null);
        } catch (Exception e) {
            System.err.println(e.toString());
            e.printStackTrace();
        }
    }

    public int getLastUserId() {
        return lastUserId.get();
    }

    public synchronized void setLastUserId(int id) {
        lastUserId.set(id);
        stateProps.setProperty(LAST_USER_ID, String.valueOf(lastUserId.get()));
        saveStateToFile();
    }

    public synchronized void incrementLastUserId() {
        lastUserId.incrementAndGet();
        stateProps.setProperty(LAST_USER_ID, String.valueOf(lastUserId.get()));
        saveStateToFile();
    }

    public synchronized Integer produceNewUserId() {
        incrementLastUserId();
        return lastUserId.get();
    }

    public String getStorageDir() {
        return storageDir.get();
    }

    public String getServerHost() {
        return serverHost.get();
    }

    public int getServerPort() {
        return serverPort.get();
    }
}
