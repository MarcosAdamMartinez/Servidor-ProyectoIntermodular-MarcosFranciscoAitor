package conexiones;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Servidor {
    private final static int PUERTO = 6667;

    public static void main(String[] args) {
        try {
            ServerSocket socket = new ServerSocket(PUERTO);

            while (true) {
                Socket jugador = socket.accept();

                Jugador jug = new Jugador(jugador);
                jug.start();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
