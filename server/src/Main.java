import state.AppState;
import storage.Storage;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class Main {
    public static void main(String[] args) {
        // config + storage setup
        System.out.println("Hello! I'm server");
        AppState state = AppState.getInstance();
        Storage storage = Storage.getInstance();
        System.out.printf("Last User Id = %d\n", state.getLastUserId());
        System.out.println(storage.toString());

        // соединения
        try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
            serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));
            System.out.println("Server started");

            while (true) {
                SocketChannel clientSocketChannel = serverSocketChannel.accept();
                System.out.println("Client connected");

                // проблема с тем, что сейчас сериализация через перевод в строку и каждая цифра занимает байт
                // а инт можно уместить всего в 4 байта
                ByteBuffer readBuffer = ByteBuffer.allocate(1024);
                int bytesRead = clientSocketChannel.read(readBuffer);
                readBuffer.flip(); // переключение между режимами чтения и записи

                String message = new String(readBuffer.array(), 0, bytesRead);
                System.out.printf("Got message: \"%s\"\n", message);
                if (!(message.startsWith("id = "))) {
                    throw new Exception("Wrong idMessage format!");
                }
                message = message.substring(5);
                Integer userId = Integer.parseInt(message);
                if (userId == -1) {
                    userId = state.produceNewUserId();

                    String response = userId.toString();
                    ByteBuffer writeBuffer = ByteBuffer.wrap(response.getBytes());
                    clientSocketChannel.write(writeBuffer);
                    System.out.printf("Sent Id to client: \"%s\"\n", response);
                }
            }

            // clientSocketChannel.close(); происходит автоматически из-за try with resources

        } catch (Exception e) {
            System.err.println(e.toString());
        }


    }
}