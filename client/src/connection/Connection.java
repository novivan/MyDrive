package connection;

import messages.FilesMessage;
import serializer.Serializer;
import state.AppState;
import storage.Storage;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class Connection {
    private static AppState state;
    private static Storage storage;

    private void updateState() {
        state = AppState.getInstance();
        storage = Storage.getInstance();
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
                Serializer.writeString(socketChannel, message);
                System.out.println("Sent id to server");

                if (userId == -1) {
                    String response = Serializer.readString(socketChannel);
                    Integer id = Integer.parseInt(response);

                    System.out.printf("Сервер назанчил нам id: %d\n", id);
                    config.setUserId(id);
                } else {
                    System.out.printf("Уже есть id: %d\n", userId);
                }

                // 3) клиент сообщает серверу список файлов
                FilesMessage filesMessage = storage.prepareSyncMessage();
                Serializer.writeMessage(socketChannel, Serializer.serialize(filesMessage));
                System.out.println("Sent list of files to server");

                // 4) сервер запрашивает файлы по одному, клиент отвечает
                int requestsCount = Integer.parseInt(Serializer.readString(socketChannel));
                System.out.printf("Server requested %d file(s)\n", requestsCount);
                for (int i = 0; i < requestsCount; i++) {
                    String filename = Serializer.readString(socketChannel);
                    System.out.printf("\tServer requested file: \"%s\"\n", filename);

                    byte[] fileData = storage.readFileBytes(filename);
                    Serializer.writeMessage(socketChannel, fileData);
                    System.out.printf("\tSent file \"%s\" (%d bytes)\n", filename, fileData.length);
                }

                // socketChannel.close(); происходит автоматически из-за try with resources
            } catch (Exception e) {
                System.err.println(e.toString());
                e.printStackTrace();
            }
        }
    }


    public void connect() {
        new NioClient().connect();
    }
}
