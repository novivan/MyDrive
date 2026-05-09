package state;

import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.util.concurrent.atomic.AtomicInteger;

import java.util.Properties;

public class AppState {
    private static AppState instance;
    Properties properties;

    private final String LAST_USER_ID = "last.user.id";
    private AtomicInteger lastUserId;

    private AppState() {
        properties = new Properties();
        try (FileInputStream fis = new FileInputStream("properties/state.properties")) {
            properties.load(fis);
        } catch (Exception e) {
            System.err.println(e.toString());
            e.printStackTrace();
        }


        lastUserId = new AtomicInteger(Integer.parseInt(properties.getProperty(LAST_USER_ID)));
    }

    public static synchronized AppState getInstance() {
        if (instance == null) {
            instance = new AppState();
        }
        return instance;
    }

    private synchronized void saveToFile() {
        try (FileOutputStream fos = new FileOutputStream("properties/state.properties")) {
            properties.store(fos, null);
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
        properties.setProperty(LAST_USER_ID, String.valueOf(lastUserId.get()));
        saveToFile();
    }

    public synchronized void incrementLastUserId() {
        lastUserId.incrementAndGet();
        properties.setProperty(LAST_USER_ID, String.valueOf(lastUserId.get()));
        saveToFile();
    }

    public synchronized Integer produceNewUserId() {
        incrementLastUserId();
        return lastUserId.get();
    }
}
