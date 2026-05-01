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
        // Configuración para tu instancia EC2
        String url = "jdbc:postgresql://localhost:5432/game_db";
        String usuario = "tfg_user";
        String password = "Clave_00";

        return DriverManager.getConnection(url, usuario, password);
    }

    @Override
    public void run() {
        try {
            String mensaje = entrada.readLine();
            if (mensaje == null) return;

            String[] partes = mensaje.split(":");
            if (partes.length < 1) return;

            // --- SCOREBOARD PÚBLICO (sin login) ---
            if (partes[0].equals("basc_guest")) {
                try (Connection conexion = conectar()) {
                    String sqlRank = "SELECT u.username, s.max_score FROM usuarios u " +
                            "JOIN estadisticas s ON u.id = s.usuario_id ORDER BY s.max_score DESC LIMIT 10";
                    PreparedStatement psRank = conexion.prepareStatement(sqlRank);
                    ResultSet rsRank = psRank.executeQuery();
                    StringBuilder sb = new StringBuilder("basc");
                    while (rsRank.next()) {
                        sb.append(":").append(rsRank.getString("username")).append(",").append(rsRank.getInt("max_score"));
                    }
                    salida.println(sb.toString());
                } catch (SQLException ex) {
                    salida.println("basc");
                }
                return;
            }

            if (partes.length < 3) return;

            try (Connection conexion = conectar()) {

                // --- SINCRONIZACIÓN OFFLINE ---
                if (partes[0].equals("offline_sync") && partes.length == 4) {
                    String offUser  = partes[1];
                    String offPass  = partes[2];
                    int    offScore;
                    try { offScore = Integer.parseInt(partes[3]); } catch (NumberFormatException e) { offScore = 0; }

                    // ¿Existe el usuario?
                    PreparedStatement checkUser = conexion.prepareStatement(
                            "SELECT id FROM usuarios WHERE username = ?");
                    checkUser.setString(1, offUser);
                    ResultSet rsCheck = checkUser.executeQuery();

                    if (rsCheck.next()) {
                        // Existe → verificar contraseña
                        PreparedStatement checkPass = conexion.prepareStatement(
                                "SELECT id FROM usuarios WHERE username = ? AND password_hash = crypt(?, password_hash)");
                        checkPass.setString(1, offUser);
                        checkPass.setString(2, offPass);
                        ResultSet rsPass = checkPass.executeQuery();

                        if (rsPass.next()) {
                            int uid = rsPass.getInt("id");
                            // Actualizar puntuación si es mayor
                            PreparedStatement upS = conexion.prepareStatement(
                                    "UPDATE estadisticas SET max_score = ? WHERE usuario_id = ? AND max_score < ?");
                            upS.setInt(1, offScore);
                            upS.setInt(2, uid);
                            upS.setInt(3, offScore);
                            upS.executeUpdate();
                            salida.println("SYNC_OK");
                        } else {
                            // Contraseña incorrecta → no hacer nada
                            salida.println("SYNC_WRONG_PASS");
                        }
                    } else {
                        // No existe → registrar con su puntuación
                        String sqlInsert = "INSERT INTO usuarios (username, password_hash) VALUES (?, crypt(?, gen_salt('bf'))) RETURNING id";
                        PreparedStatement insertUser = conexion.prepareStatement(sqlInsert);
                        insertUser.setString(1, offUser);
                        insertUser.setString(2, offPass);
                        ResultSet rsInsert = insertUser.executeQuery();
                        if (rsInsert.next()) {
                            int nuevoId = rsInsert.getInt("id");
                            PreparedStatement initStats = conexion.prepareStatement(
                                    "INSERT INTO estadisticas (usuario_id, max_score) VALUES (?, ?)");
                            initStats.setInt(1, nuevoId);
                            initStats.setInt(2, offScore);
                            initStats.executeUpdate();
                            System.out.println("Registro offline - Usuario: " + offUser + " Score: " + offScore);
                            salida.println("SYNC_REGISTERED");
                        }
                    }
                    socket.close();
                    return;
                }

                // --- LÓGICA DE LOGIN ---
                if (partes[0].equals("l")) {
                    // La base de datos comprueba el hash usando el Salt guardado automáticamente
                    String sql = "SELECT id FROM usuarios WHERE username = ? AND password_hash = crypt(?, password_hash)";
                    PreparedStatement checkUser = conexion.prepareStatement(sql);
                    checkUser.setString(1, partes[1]);
                    checkUser.setString(2, partes[2]);
                    ResultSet rs = checkUser.executeQuery();

                    if (rs.next()) {
                        System.out.println("Login exitoso - Usuario: " + partes[1]);
                        salida.println("ENTRAR");
                    } else {
                        System.out.println("Login fallido - Usuario: " + partes[1]);
                        salida.println("INCORRECTO");
                        socket.close();
                        return;
                    }
                }

                // --- LÓGICA DE REGISTRO ---
                if (partes[0].equals("r")) {
                    // 1. Comprobar si existe
                    PreparedStatement checkExist = conexion.prepareStatement("SELECT id FROM usuarios WHERE username = ?");
                    checkExist.setString(1, partes[1]);
                    if (checkExist.executeQuery().next()) {
                        salida.println("EXISTENTE");
                        socket.close();
                        return;
                    }

                    // 2. Insertar con gen_salt('bf') para máxima seguridad
                    String sqlInsert = "INSERT INTO usuarios (username, password_hash) VALUES (?, crypt(?, gen_salt('bf'))) RETURNING id";
                    PreparedStatement insertUser = conexion.prepareStatement(sqlInsert);
                    insertUser.setString(1, partes[1]);
                    insertUser.setString(2, partes[2]);
                    ResultSet rsInsert = insertUser.executeQuery();

                    if (rsInsert.next()) {
                        int nuevoId = rsInsert.getInt("id");
                        // Crear automáticamente la fila de estadísticas para este usuario
                        PreparedStatement initStats = conexion.prepareStatement("INSERT INTO estadisticas (usuario_id) VALUES (?)");
                        initStats.setInt(1, nuevoId);
                        initStats.executeUpdate();

                        System.out.println("Registro exitoso - Usuario: " + partes[1]);
                        salida.println("ENTRAR");
                    }
                }

                // --- BUCLE DE JUEGO (PUNTUACIONES) ---
                while (true) {
                    String accion = entrada.readLine();
                    if (accion == null) break;

                    partes = accion.split(":");

                    // Subir puntuación (sbsc)
                    if (partes[0].equals("sbsc") && partes.length == 4) {
                        // Buscamos las estadísticas unidas al ID del usuario
                        String sqlGet = "SELECT s.max_score, s.level_max, u.id FROM usuarios u " +
                                "JOIN estadisticas s ON u.id = s.usuario_id WHERE u.username = ?";
                        PreparedStatement psGet = conexion.prepareStatement(sqlGet);
                        psGet.setString(1, partes[1]);
                        ResultSet rsStats = psGet.executeQuery();

                        if (rsStats.next()) {
                            int uid = rsStats.getInt("id");
                            int scoreDb = rsStats.getInt("max_score");
                            int levelDb = rsStats.getInt("level_max");

                            int newLevel = Integer.parseInt(partes[2]);
                            int newScore = Integer.parseInt(partes[3]);

                            if (newLevel > levelDb) {
                                PreparedStatement upL = conexion.prepareStatement("UPDATE estadisticas SET level_max = ? WHERE usuario_id = ?");
                                upL.setInt(1, newLevel);
                                upL.setInt(2, uid);
                                upL.executeUpdate();
                            }
                            if (newScore > scoreDb) {
                                PreparedStatement upS = conexion.prepareStatement("UPDATE estadisticas SET max_score = ? WHERE usuario_id = ?");
                                upS.setInt(1, newScore);
                                upS.setInt(2, uid);
                                upS.executeUpdate();
                            }
                        }
                    }

                    // Obtener Ranking (basc)
                    if (partes[0].equals("basc")) {
                        String sqlRank = "SELECT u.username, s.max_score FROM usuarios u " +
                                "JOIN estadisticas s ON u.id = s.usuario_id ORDER BY s.max_score DESC LIMIT 10";
                        PreparedStatement psRank = conexion.prepareStatement(sqlRank);
                        ResultSet rsRank = psRank.executeQuery();

                        StringBuilder sb = new StringBuilder("basc");
                        while (rsRank.next()) {
                            sb.append(":").append(rsRank.getString("username")).append(",").append(rsRank.getInt("max_score"));
                        }
                        salida.println(sb.toString());
                    }
                }

            } catch (SQLException ex) {
                System.out.println("Error SQL: " + ex.getMessage());
                salida.println("ERROR_SERVIDOR");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException e) { e.printStackTrace(); }
        }
    }
}