import connection.ClientHandler;
import connection.UploadHandler;
import messages.HelloMessage;
import messages.MessageType;
import messages.UploadHelloMessage;
import serializer.Serializer;
import state.AppState;
import storage.Storage;

import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) {
        // config + storage setup
        System.out.println("Hello! I'm server");
        AppState state = AppState.getInstance();
        Storage storage = Storage.getInstance();
        System.out.printf("Last User Id = %d\n", state.getLastUserId());
        System.out.println(storage.toString());

        ExecutorService workers = Executors.newCachedThreadPool();

        try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
            serverSocketChannel.bind(new InetSocketAddress(state.getServerHost(), state.getServerPort()));
            System.out.printf("Server started on %s:%d (storage='%s')\n",
                    state.getServerHost(), state.getServerPort(), state.getStorageDir());

            while (true) {
                SocketChannel clientSocketChannel = serverSocketChannel.accept();
                workers.submit(() -> dispatch(clientSocketChannel));
            }
        } catch (Exception e) {
            System.err.println(e.toString());
            e.printStackTrace();
        } finally {
            workers.shutdown();
        }
    }

    private static void dispatch(SocketChannel ch) {
        try {
            Serializer.Envelope first = Serializer.readEnvelope(ch);
            if (first.type == MessageType.HELLO) {
                HelloMessage hello = (HelloMessage) Serializer.deserialize(first.payload);
                new ClientHandler(ch, hello).run();
            } else if (first.type == MessageType.UPLOAD_HELLO) {
                UploadHelloMessage hello = (UploadHelloMessage) Serializer.deserialize(first.payload);
                new UploadHandler(ch, hello).run();
            } else {
                System.err.printf("Unexpected first message: %s, closing\n", first.type);
                try { ch.close(); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.err.printf("Dispatch error: %s\n", e);
            try { ch.close(); } catch (Exception ignored) {}
        }
    }
}
