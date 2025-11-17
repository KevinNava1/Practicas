import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Servidor {
    private static final int PUERTO = 9876;
    private DatagramSocket socket;
    private Map<String, Sala> salas;
    private boolean corriendo;

    public Servidor() {
        salas = new ConcurrentHashMap<>();
        corriendo = true;
    }

    public void iniciar() {
        try {
            socket = new DatagramSocket(PUERTO);
            System.out.println("🚀 Servidor iniciado en puerto " + PUERTO);

            while (corriendo) {
                // Buffer para recibir datos
                byte[] buffer = new byte[65535];
                DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);

                // Recibir paquete
                socket.receive(paquete);

                // Procesar en un hilo separado
                new Thread(() -> procesarMensaje(paquete)).start();
            }

        } catch (Exception e) {
            System.err.println("Error en servidor: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    private void procesarMensaje(DatagramPacket paquete) {
        try {
            // Convertir bytes a Mensaje
            byte[] datos = Arrays.copyOf(paquete.getData(), paquete.getLength());
            Mensaje mensaje = Mensaje.fromBytes(datos);

            InetAddress clienteIP = paquete.getAddress();
            int clientePuerto = paquete.getPort();

            System.out.println("📩 Recibido: " + mensaje.getTipo() +
                             " de " + mensaje.getUsuario());

            // Procesar según el tipo de mensaje
            switch (mensaje.getTipo()) {
                case Mensaje.CREAR_SALA:
                    crearSala(mensaje, clienteIP, clientePuerto);
                    break;

                case Mensaje.UNIRSE_SALA:
                    unirseASala(mensaje, clienteIP, clientePuerto);
                    break;

                case Mensaje.SALIR_SALA:
                    salirDeSala(mensaje, clienteIP, clientePuerto);
                    break;

                case Mensaje.MENSAJE_SALA:
                    enviarMensajeASala(mensaje);
                    break;

                case Mensaje.MENSAJE_PRIVADO:
                    enviarMensajePrivado(mensaje);
                    break;

                case Mensaje.LISTAR_USUARIOS:
                    listarUsuarios(mensaje, clienteIP, clientePuerto);
                    break;

                case Mensaje.ENVIAR_STICKER:
                    enviarStickerASala(mensaje);
                    break;

                case Mensaje.ENVIAR_AUDIO:
                    enviarAudioASala(mensaje);
                    break;
            }

        } catch (Exception e) {
            System.err.println("Error procesando mensaje: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void crearSala(Mensaje msg, InetAddress ip, int puerto) {
        String nombreSala = msg.getSala();

        if (!salas.containsKey(nombreSala)) {
            Sala nuevaSala = new Sala(nombreSala);
            salas.put(nombreSala, nuevaSala);
            enviarRespuesta(msg.getUsuario(), "✅ Sala '" + nombreSala + "' creada", ip, puerto);
        } else {
            enviarRespuesta(msg.getUsuario(), "⚠️ La sala ya existe", ip, puerto);
        }
    }

    private void unirseASala(Mensaje msg, InetAddress ip, int puerto) {
        String nombreSala = msg.getSala();
        Sala sala = salas.get(nombreSala);

        if (sala != null) {
            sala.agregarUsuario(msg.getUsuario(), ip, puerto);

            // Notificar a todos en la sala
            notificarASala(nombreSala, "👋 " + msg.getUsuario() + " se unió a la sala");

            // Enviar confirmación al usuario
            enviarRespuesta(msg.getUsuario(), "✅ Te uniste a '" + nombreSala + "'", ip, puerto);

            // Enviar lista de usuarios actualizada
            actualizarListaUsuarios(nombreSala);
        } else {
            enviarRespuesta(msg.getUsuario(), "❌ La sala no existe", ip, puerto);
        }
    }

    private void salirDeSala(Mensaje msg, InetAddress ip, int puerto) {
        String nombreSala = msg.getSala();
        Sala sala = salas.get(nombreSala);

        if (sala != null) {
            sala.removerUsuario(msg.getUsuario());

            // Notificar a todos en la sala
            notificarASala(nombreSala, "👋 " + msg.getUsuario() + " salió de la sala");

            // Actualizar lista de usuarios
            actualizarListaUsuarios(nombreSala);

            // Si la sala está vacía, eliminarla
            if (sala.estaVacia()) {
                salas.remove(nombreSala);
                System.out.println("Sala '" + nombreSala + "' eliminada (vacía)");
            }

            enviarRespuesta(msg.getUsuario(), "✅ Saliste de '" + nombreSala + "'", ip, puerto);
        }
    }

    private void enviarMensajeASala(Mensaje msg) {
        Sala sala = salas.get(msg.getSala());
        if (sala != null) {
            String mensajeFormato = msg.getUsuario() + ": " + msg.getContenido();
            notificarASala(msg.getSala(), mensajeFormato);
        }
    }

    private void enviarMensajePrivado(Mensaje msg) {
        Sala sala = salas.get(msg.getSala());
        if (sala != null) {
            Sala.UsuarioInfo destinatarioInfo = sala.getUsuarioInfo(msg.getDestinatario());

            if (destinatarioInfo != null) {
                String mensajeFormato = "[PRIVADO] " + msg.getUsuario() + ": " + msg.getContenido();
                Mensaje respuesta = new Mensaje(Mensaje.RESPUESTA, "Servidor",
                                                msg.getSala(), mensajeFormato);
                enviarPaquete(respuesta, destinatarioInfo.getIp(), destinatarioInfo.getPuerto());
            }
        }
    }

    private void enviarStickerASala(Mensaje msg) {
        Sala sala = salas.get(msg.getSala());
        if (sala != null) {
            String mensajeFormato = msg.getUsuario() + " envió: " + msg.getContenido();
            notificarASala(msg.getSala(), mensajeFormato);
        }
    }

    private void enviarAudioASala(Mensaje msg) {
        Sala sala = salas.get(msg.getSala());
        if (sala != null) {
            String mensajeFormato = "🎤 " + msg.getUsuario() + " envió un audio";
            notificarASala(msg.getSala(), mensajeFormato);
        }
    }

    private void listarUsuarios(Mensaje msg, InetAddress ip, int puerto) {
        Sala sala = salas.get(msg.getSala());
        if (sala != null) {
            Set<String> usuarios = sala.getUsuarios();
            String lista = "👥 Usuarios en " + msg.getSala() + ":\n" +
                          String.join(", ", usuarios);
            enviarRespuesta(msg.getUsuario(), lista, ip, puerto);
        }
    }

    private void actualizarListaUsuarios(String nombreSala) {
        Sala sala = salas.get(nombreSala);
        if (sala != null) {
            Set<String> usuarios = sala.getUsuarios();
            String lista = "USUARIOS:" + String.join(",", usuarios);
            notificarASala(nombreSala, lista);
        }
    }

    private void notificarASala(String nombreSala, String mensaje) {
        Sala sala = salas.get(nombreSala);
        if (sala != null) {
            Mensaje msg = new Mensaje(Mensaje.RESPUESTA, "Servidor", nombreSala, mensaje);

            for (Sala.UsuarioInfo usuario : sala.getAllUsuarios()) {
                enviarPaquete(msg, usuario.getIp(), usuario.getPuerto());
            }
        }
    }

    private void enviarRespuesta(String usuario, String contenido, InetAddress ip, int puerto) {
        Mensaje respuesta = new Mensaje(Mensaje.RESPUESTA, "Servidor", "", contenido);
        enviarPaquete(respuesta, ip, puerto);
    }

    private void enviarPaquete(Mensaje mensaje, InetAddress ip, int puerto) {
        try {
            byte[] datos = mensaje.toBytes();
            DatagramPacket paquete = new DatagramPacket(datos, datos.length, ip, puerto);
            socket.send(paquete);
        } catch (Exception e) {
            System.err.println("Error enviando paquete: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new Servidor().iniciar();
    }
}
