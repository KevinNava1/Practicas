import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Servidor {
    private static final int PUERTO = 6000;
    private static final int PUERTO_MULTICAST = 6789; // Puerto para mensajes multicast
    private static final String BASE_MULTICAST = "230.0.0."; // Rango multicast

    private DatagramSocket socket;
    private Map<String, Sala> salas;
    private AtomicInteger contadorSalas; // Para asignar IPs multicast únicas
    private boolean corriendo;

    public Servidor() {
        salas = new ConcurrentHashMap<>();
        contadorSalas = new AtomicInteger(1);
        corriendo = true;
    }

    public void iniciar() {
        try {
            socket = new DatagramSocket(PUERTO);
            System.out.println(" Servidor iniciado en puerto " + PUERTO);
            System.out.println(" Modo multicast activado");

            while (corriendo) {
                byte[] buffer = new byte[65535];
                DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                socket.receive(paquete);
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
            byte[] datos = Arrays.copyOf(paquete.getData(), paquete.getLength());
            Mensaje mensaje = Mensaje.fromBytes(datos);

            InetAddress clienteIP = paquete.getAddress();
            int clientePuerto = paquete.getPort();

            System.out.println(" Recibido: " + mensaje.getTipo() +
                             " de " + mensaje.getUsuario() +
                             " Datos: " + (mensaje.getDatos() != null ? mensaje.getDatos().length + " bytes" : "null"));

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
                case Mensaje.CONFIRMAR_UNION:
                    confirmarUnionSala(mensaje, clienteIP, clientePuerto);
                    break;
                case Mensaje.ENVIAR_AUDIO:
                    enviarAudioASala(mensaje); // CORREGIDO: Ahora pasa el mensaje completo
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
            // Generar dirección multicast única para esta sala
            String direccionMulticast = BASE_MULTICAST + contadorSalas.getAndIncrement();
            Sala nuevaSala = new Sala(nombreSala, direccionMulticast);
            salas.put(nombreSala, nuevaSala);

            // Enviar respuesta con la dirección multicast
            Mensaje respuesta = new Mensaje(Mensaje.RESPUESTA, "Servidor", "",
                                           " Sala '" + nombreSala + "' creada");
            respuesta.setDireccionMulticast(direccionMulticast);
            enviarPaquete(respuesta, ip, puerto);

            System.out.println("Sala creada: " + nombreSala + " -> " + direccionMulticast);
        } else {
            enviarRespuesta(msg.getUsuario(), " La sala ya existe", ip, puerto);
        }
    }

    private void unirseASala(Mensaje msg, InetAddress ip, int puerto) {
        String nombreSala = msg.getSala();
        Sala sala = salas.get(nombreSala);

        if (sala != null) {
            sala.agregarUsuario(msg.getUsuario(), ip, puerto);

            // Enviar dirección multicast al cliente
            Mensaje respuesta = new Mensaje(Mensaje.RESPUESTA, "Servidor", nombreSala,
                                           "Conectando a '" + nombreSala + "'...");
            respuesta.setDireccionMulticast(sala.getDireccionMulticast());
            enviarPaquete(respuesta, ip, puerto);
        } else {
            enviarRespuesta(msg.getUsuario(), " La sala no existe", ip, puerto);
        }
    }

    private void confirmarUnionSala(Mensaje msg, InetAddress ip, int puerto) {
        String nombreSala = msg.getSala();
        Sala sala = salas.get(nombreSala);

        if(sala != null){
            sala.agregarUsuario(msg.getUsuario(), ip, puerto);
            // Notificar a toda la sala usando multicast
            notificarASalaMulticast(nombreSala, "👋 " + msg.getUsuario() + " se unió a la sala");
            actualizarListaUsuarios(nombreSala);
        }
    }

    private void salirDeSala(Mensaje msg, InetAddress ip, int puerto) {
        String nombreSala = msg.getSala();
        Sala sala = salas.get(nombreSala);

        if (sala != null) {
            sala.removerUsuario(msg.getUsuario());
            notificarASalaMulticast(nombreSala, "👋 " + msg.getUsuario() + " salió de la sala");
            actualizarListaUsuarios(nombreSala);

            if (sala.estaVacia()) {
                salas.remove(nombreSala);
                System.out.println("Sala '" + nombreSala + "' eliminada (vacía)");
            }

            enviarRespuesta(msg.getUsuario(), " Saliste de '" + nombreSala + "'", ip, puerto);
        }
    }

    private void enviarMensajeASala(Mensaje msg) {
        String mensajeFormato = msg.getUsuario() + ": " + msg.getContenido();
        notificarASalaMulticast(msg.getSala(), mensajeFormato);
    }

    private void enviarMensajePrivado(Mensaje msg) {
        Sala sala = salas.get(msg.getSala());
        if (sala != null) {
            Sala.UsuarioInfo destinatarioInfo = sala.getUsuarioInfo(msg.getDestinatario());
            if (destinatarioInfo != null) {
                String mensajeFormato = "[PRIVADO de " + msg.getUsuario() + "] " + msg.getContenido();
                Mensaje respuesta = new Mensaje(Mensaje.RESPUESTA, "Servidor",
                                                msg.getSala(), mensajeFormato);
                enviarPaquete(respuesta, destinatarioInfo.getIp(), destinatarioInfo.getPuerto());
            }
        }
    }

    private void enviarStickerASala(Mensaje msg) {
        String mensajeFormato = msg.getUsuario() + " envió: " + msg.getContenido();
        notificarASalaMulticast(msg.getSala(), mensajeFormato);
    }

    private void enviarAudioASala(Mensaje msg) {
        Sala sala = salas.get(msg.getSala());
        if(sala != null && msg.getDatos() != null && msg.getDatos().length > 0) {
            try {
                // CORREGIDO: Enviar el mensaje completo con los datos de audio
                Mensaje msgAudio = new Mensaje(Mensaje.RESPUESTA, msg.getUsuario(), msg.getSala(), "AUDIO:" + msg.getUsuario());
                msgAudio.setDatos(msg.getDatos()); // IMPORTANTE: Conservar los datos de audio
                
                byte[] datos = msgAudio.toBytes();
                InetAddress grupoMulticast = InetAddress.getByName(sala.getDireccionMulticast());
                DatagramPacket paquete = new DatagramPacket(datos, datos.length, grupoMulticast, PUERTO_MULTICAST);
                socket.send(paquete);
                System.out.println("🎤 Audio enviado de " + msg.getUsuario() + " a sala " + msg.getSala() + " (" + msg.getDatos().length + " bytes)");
            } catch (Exception e) {
                System.err.println("Error enviando audio: " + e.getMessage());
            }
        } else {
            System.err.println("❌ Error: Audio vacío o sala no encontrada");
            if (msg.getDatos() == null) {
                System.err.println("Datos de audio son null");
            } else if (msg.getDatos().length == 0) {
                System.err.println("Datos de audio están vacíos");
            }
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
            notificarASalaMulticast(nombreSala, lista);
        }
    }

    // MÉTODO NUEVO: Enviar mensaje usando MULTICAST
    private void notificarASalaMulticast(String nombreSala, String mensaje) {
        Sala sala = salas.get(nombreSala);
        if (sala != null) {
            try {
                Mensaje msg = new Mensaje(Mensaje.RESPUESTA, "Servidor", nombreSala, mensaje);
                byte[] datos = msg.toBytes();

                // Enviar al grupo multicast de la sala
                InetAddress grupoMulticast = InetAddress.getByName(sala.getDireccionMulticast());
                DatagramPacket paquete = new DatagramPacket(datos, datos.length,
                                                           grupoMulticast, PUERTO_MULTICAST);

                // Usamos el socket normal para enviar al grupo multicast
                socket.send(paquete);

            } catch (Exception e) {
                System.err.println("Error enviando multicast: " + e.getMessage());
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