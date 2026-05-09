import connection.Connection;
import state.AppState;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.StringBuilder;

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

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.println("Commands: sync | exit");
            String line;
            while (true) {
                System.out.print("> ");
                System.out.flush();
                line = reader.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.equals("exit") || line.equals("quit")) {
                    break;
                } else if (line.equals("sync")) {
                    connection.connect();
                } else {
                    System.out.println("Unknown command: " + line + " (use 'sync' or 'exit' or 'quit'')");
                }
            }
        } catch (Exception e) {
            System.err.println(e.toString());
        }
    }
}
