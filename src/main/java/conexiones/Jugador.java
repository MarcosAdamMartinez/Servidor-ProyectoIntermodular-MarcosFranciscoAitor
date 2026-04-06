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

                partes = accion.split(":");

                if (partes.length == 4) {
                    if (partes[0].equals("sbsc")) {
                        try (Connection conexion = conectar()) {
                            PreparedStatement getIdUser = conexion.prepareStatement("SELECT id FROM usuarios WHERE username = ?");
                            getIdUser.setString(1, partes[1]);
                            ResultSet rs = getIdUser.executeQuery();
                            if (rs.next()) {
                                int id = rs.getInt("id");

                                PreparedStatement getMaxScore = conexion.prepareStatement("SELECT max_score, level_max FROM estadisticas WHERE id = ?");
                                getMaxScore.setInt(1, id);
                                ResultSet rs2 = getMaxScore.executeQuery();

                                if (rs2.next()) {
                                    int puntuacion = rs2.getInt("max_score");
                                    int level = rs2.getInt("level_max");

                                    if (level < Integer.parseInt(partes[2])) {
                                        PreparedStatement updateLevel = conexion.prepareStatement("UPDATE estadisticas SET level_max = ? WHERE id = ?");
                                        updateLevel.setInt(1, Integer.parseInt(partes[2]));
                                        updateLevel.setInt(2, id);
                                        updateLevel.executeUpdate();
                                        System.out.println("Nivel maximo actualizado");
                                    }

                                    if (puntuacion < Integer.parseInt(partes[3])) {
                                        PreparedStatement updateScore = conexion.prepareStatement("UPDATE estadisticas SET max_score = ? WHERE id = ?");
                                        updateScore.setInt(1, Integer.parseInt(partes[3]));
                                        updateScore.setInt(2, id);
                                        updateScore.executeUpdate();
                                        System.out.println("Score maximo actualizado");
                                    }

                                } else {
                                    PreparedStatement insertScore = conexion.prepareStatement("INSERT INTO estadisticas (id, username, level_max, max_score) VALUES (?, ?, ?, ?)");
                                    insertScore.setInt(1, id);
                                    insertScore.setString(2, partes[1]);
                                    insertScore.setInt(3, Integer.parseInt(partes[2]));
                                    insertScore.setInt(4, Integer.parseInt(partes[3]));
                                    insertScore.executeUpdate();
                                    System.out.println("El usuario ha registrado una puntuacion");
                                }
                            }
                        }
                    }
                } else {
                    if (partes[0].equals("basc")) {
                        try (Connection con = conectar();
                             PreparedStatement checkBestScores = con.prepareStatement(
                                     "SELECT username, max_score FROM estadisticas ORDER BY max_score DESC LIMIT 10")) {

                            ResultSet rs = checkBestScores.executeQuery();
                            StringBuilder respuesta = new StringBuilder("basc");

                            while (rs.next()) {
                                respuesta.append(":").append(rs.getString("username"))
                                        .append(",").append(rs.getInt("max_score"));
                            }

                            salida.println(respuesta.toString());
                            System.out.println("Enviando scoreboard a " + this.getName());

                        } catch (SQLException e) {
                            System.out.println("Error al obtener scoreboard: " + e.getMessage());
                        }
                    }
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
