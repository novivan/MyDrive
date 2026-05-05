import state.AppState;
import storage.Storage;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello! I'm server");

        AppState state = AppState.getInstance();
        Storage storage = Storage.getInstance();

        for (int i = 0; i < 15; i++) {
            System.out.println(state.getLastUserId());
            state.incrementLastUserId();
        }

        System.out.println(storage.toString());
    }
}