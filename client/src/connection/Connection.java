package connection;

import state.AppState;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class Connection {
    private static AppState state;

    private void updateState() {
        state = AppState.getInstance();
    }

    public Connection() {
        updateState();
    }

    // пока делаю самый простой, потом сделаю неблокирующий
    private class NioClient {
        protected void connect() {
            var config = Connection.state;
            var userId = config.getUserId();
                // 1) клиент подключается к серверу по протоколу TCP
            try (SocketChannel socketChannel = SocketChannel.open()) {
                socketChannel.connect(new InetSocketAddress(config.getServerAddr(), config.getServerPort()));
                System.out.println("Connected to server");

                // 2) сообщает свой ID (если id нет, получим от сервера)
                String message = "id = " + userId;
                ByteBuffer writeBuffer = ByteBuffer.wrap(message.getBytes());
                socketChannel.write(writeBuffer);
                System.out.println("Sent id to server");

                if (userId == -1) {
                    // проблема с тем, что сейчас сериализация через перевод в строку и каждая цифра занимает байт
                    // а инт можно уместить всего в 4 байта
                    ByteBuffer readBuffer = ByteBuffer.allocate(1024);
                    int bytesRead = socketChannel.read(readBuffer);
                    readBuffer.flip(); // переключение между режимами чтения и записи

                    String response = new String(readBuffer.array(), 0, bytesRead);
                    Integer id = Integer.parseInt(response);

                    System.out.printf("Сервер назанчил нам id: %d\n", id);
                    config.setUserId(id);
                } else {
                    System.out.printf("Уже есть id: %d\n", userId);
                }

                // socketChannel.close(); происходит автоматически из-за try with resources
            } catch (Exception e) {
                System.err.println(e.toString());
            }
        }
    }


    public void connect() {
        new NioClient().connect();
    }
}
