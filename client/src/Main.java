import state.AppState;
import java.lang.StringBuilder;
import connection.Connection;

public class Main {
    public static void main(String[] args) {
        AppState state = AppState.getInstance();

        System.out.println("Hello! I'm client");
        String stateStr = new StringBuilder()
                .append("Sync dir path: ").append(state.getSyncDirPath())
                .append("\nServer addr: ").append(state.getServerAddr())
                .append("\nServer Port: ").append(state.getServerPort())
                .append("\nMax server connections: ").append(state.getMaxServerConnections())
                .append("\nUser ID: ").append(state.getUserId())
                .append("\n")
                .toString();

        System.out.println(stateStr);

        Connection connection = new Connection();
        connection.connect();
    }
}