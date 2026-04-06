package conexiones;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.*;

public class Jugador extends Thread {

    private Socket socket;
    private BufferedReader entrada;
    private PrintWriter salida;

    public Jugador(Socket socket) {
        this.socket = socket;
        try {
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            salida = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Connection conectar() throws SQLException {
        String url = "jdbc:postgresql://localhost:5433/user_password";
        String usuario = "postgres";
        String password = "root";

        return DriverManager.getConnection(url, usuario, password);
    }

    @Override
    public void run() {
        try {

            String mensaje = entrada.readLine();

            if (mensaje == null) {
                return;
            }

            String[] partes = mensaje.split(":");

            try (Connection conexion = conectar()) {
                if (partes.length == 3) {
                    if (partes[0].equals("l")) {
                        PreparedStatement checkUser = conexion.prepareStatement("SELECT password_hash FROM usuarios WHERE username = ?");
                        checkUser.setString(1, partes[1]);
                        ResultSet rs = checkUser.executeQuery();

                        if (rs.next()) {
                            String dbPass = rs.getString("password_hash");
                            if (partes.length == 3) {
                                if (dbPass.equals(partes[2])) {
                                    System.out.println("Intento de login - Usuario: " + partes[1] + " SI exitoso");
                                    salida.println("ENTRAR");
                                } else {
                                    salida.println("INCORRECTO");
                                    socket.close();
                                    System.out.println("Intento de login - Usuario: " + partes[1] + " no exitoso");
                                    return;
                                }
                            } else {
                                salida.println("INSUFICIENTE");
                                socket.close();
                                System.out.println("Intento de login - Usuario: " + partes[1] + " no exitoso");
                                return;
                            }
                        } else {
                            salida.println("INEXISTENTE");
                            socket.close();
                            System.out.println("Intento de login - Usuario: " + partes[1] + " no exitoso");
                            return;
                        }
                    }

                    if (partes[0].equals("r")) {

                        PreparedStatement checkUser = conexion.prepareStatement("SELECT password_hash FROM usuarios WHERE username = ?");
                        checkUser.setString(1, partes[1]);
                        ResultSet rs = checkUser.executeQuery();

                        if (rs.next()) {
                            salida.println("EXISTENTE");
                            socket.close();
                            System.out.println("Intento de registro - Usuario: " + partes[1] + " no exitoso, el usuario ya existe");
                            return;
                        }

                        if (partes.length == 3) {
                            PreparedStatement insertUser = conexion.prepareStatement("INSERT INTO usuarios (username, password_hash) VALUES (?, ?)");
                            insertUser.setString(1, partes[1]);
                            insertUser.setString(2, partes[2]);
                            insertUser.executeUpdate();

                            PreparedStatement checkInsert = conexion.prepareStatement("SELECT password_hash FROM usuarios WHERE username = ?");
                            checkInsert.setString(1, partes[1]);
                            ResultSet rs2 = checkInsert.executeQuery();

                            if (rs2.next()) {
                                String dbPass = rs2.getString("password_hash");

                                if (dbPass.equals(partes[2])) {
                                    System.out.println("Intento de registro - Usuario: " + partes[1] + " SI exitoso");
                                    salida.println("ENTRAR");
                                }

                            }

                        } else {
                            salida.println("INSUFICIENTE");
                            socket.close();
                            System.out.println("Intento de registro - Usuario: " + partes[1] + " no exitoso");
                            return;
                        }
                    }
                } else {
                    salida.println("INSUFICIENTE");
                    socket.close();
                    System.out.println("Intento de registro - Usuario: " + partes[1] + " no exitoso");
                    return;
                }

            } catch (SQLException ex) {
                System.out.println("Error de base de datos: " + ex.getMessage());
                salida.println("ERROR_SERVIDOR");
                return;
            } catch (ArrayIndexOutOfBoundsException a) {
                return;
            }

            while (true) {
                String accion = entrada.readLine();

                if (accion == null) {
                    break;
                }
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                socket.close();
                System.out.println("Se ha finalizado la conexion");
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Jugador desconectado");
        }
    }

}
