import messages.DirDifference;
import messages.FilesMessage;
import serializer.Serializer;
import state.AppState;
import storage.FileInfo;
import storage.Storage;

import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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

                String message = Serializer.readString(clientSocketChannel);
                System.out.printf("Got message: \"%s\"\n", message);
                if (!(message.startsWith("id = "))) {
                    throw new Exception("Wrong idMessage format!");
                }
                message = message.substring(5);
                Integer userId = Integer.parseInt(message);
                if (userId == -1) {
                    userId = state.produceNewUserId();

                    String response = userId.toString();
                    Serializer.writeString(clientSocketChannel, response);
                    System.out.printf("Sent Id to client: \"%s\"\n", response);
                }

                // получаем файлы от клиента
                byte[] filesData = Serializer.readMessage(clientSocketChannel);
                FilesMessage filesMessage = (FilesMessage) Serializer.deserialize(filesData);

                List<messages.FileInfo> clientFiles = new ArrayList<>(filesMessage.files());
                Collections.sort(clientFiles, Comparator.comparing(messages.FileInfo::name));

                List<FileInfo> filesOnStorage = new ArrayList<>(storage.getUsersStorages().get(userId).getFiles());
                Collections.sort(filesOnStorage, Comparator.comparing(FileInfo::name));

                List<String> toRequest = new ArrayList<>();
                List<String> toDelete = new ArrayList<>();

                // сверяемся
                int storageIndex = 0, messageIndex = 0;
                while (storageIndex < filesOnStorage.size() && messageIndex < clientFiles.size()) {
                    FileInfo storageFile = filesOnStorage.get(storageIndex);
                    messages.FileInfo messageFile = clientFiles.get(messageIndex);

                    if (storageFile.name().equals(messageFile.name())) {
                        // сверяем сам файл
                        boolean isOk = true;
                        if (storageFile.size() != messageFile.size()) {
                            isOk = false;
                        }
                        for (int i = 0; i < storageFile.hashes().size() && isOk; i++) {
                            if (storageFile.hashes().get(i) != messageFile.hashes().get(i)) {
                                isOk = false;
                                break;
                            }
                        }
                        if (!isOk) {
                            toRequest.add(messageFile.name());
                            toDelete.add(storageFile.name());
                        }
                        storageIndex++;
                        messageIndex++;
                    } else {
                        // простая логика по именам
                        if (storageFile.name().compareTo(messageFile.name()) < 0) {
                            toDelete.add(storageFile.name());
                            storageIndex++;
                        } else {
                            toRequest.add(messageFile.name());
                            messageIndex++;
                        }
                    }
                }
                while (storageIndex < filesOnStorage.size()) {
                    toDelete.add(filesOnStorage.get(storageIndex).name());
                    storageIndex++;
                }
                while (messageIndex < clientFiles.size()) {
                    toRequest.add(clientFiles.get(messageIndex).name());
                    messageIndex++;
                }

                // устранияем различия
                for (String filename : toDelete) {
                    storage.deleteFile(userId, filename);
                }

                // Отправляем клиенту ответ - сколько файлов мы будем запрашивать
                Integer requestFileQuryes = toRequest.size();
                String requests = requestFileQuryes.toString();
                Serializer.writeString(clientSocketChannel, requests);
                System.out.printf("Sent amount requests (request files which server don't have) to client: \"%s\"\n", requests);
                // запрос на кажый файл
                for (int i = 0; i < toRequest.size(); i++) {
                    String filename = toRequest.get(i);
                    System.out.printf("\tRequesting file: \"%s\"\n", filename);

                    Serializer.writeString(clientSocketChannel, filename);
                    System.out.printf("\tSent filename to client: \"%s\"\n", filename);

                    byte[] fileData = Serializer.readMessage(clientSocketChannel);
                    storage.createFile(userId, filename, fileData);
                }


            }

            // clientSocketChannel.close(); происходит автоматически из-за try with resources

        } catch (Exception e) {
            System.err.println(e.toString());
            e.printStackTrace();
        }


    }
}