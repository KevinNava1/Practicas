import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Servidor {
    private static final int PUERTO = 9876;
    private static final String BASE_MULTICAST = "230.0.0.";
    private static final int BASE_PUERTO_MULTICAST = 4446;

    private DatagramSocket socket;
    private Map<String, Sala> salas;
    private int contadorSalas = 1;
    private boolean corriendo;

    public Servidor() {
        salas = new ConcurrentHashMap<>();
        corriendo = true;
    }

    public void iniciar() {
        try {
            socket = new DatagramSocket(PUERTO);
            System.out.println("🚀 Servidor iniciado en puerto " + PUERTO);
            System.out.println("📡 Usando direcciones multicast: " + BASE_MULTICAST + "X");

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

            System.out.println("📩 Recibido: " + mensaje.getTipo() +
                             " de " + mensaje.getUsuario() +
                             (mensaje.getSala() != null ? " - Sala: " + mensaje.getSala() : ""));

            switch (mensaje.getTipo()) {
                case Mensaje.CREAR_SALA:
                    crearSala(mensaje, clienteIP, clientePuerto);
                    break;

                case Mensaje.UNIRSE_SALA:
                    unirseASala(mensaje, clienteIP, clientePuerto);
                    break;

                case Mensaje.SALIR_SALA:
                    salirDeSala(mensaje);
                    break;

                case Mensaje.DESCONECTAR:
                    desconectarUsuario(mensaje);
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
            // Asignar dirección multicast única
            String dirMulticast = BASE_MULTICAST + contadorSalas;
            int puertoMulticast = BASE_PUERTO_MULTICAST + contadorSalas;
            contadorSalas++;

            Sala nuevaSala = new Sala(nombreSala, dirMulticast, puertoMulticast);
            salas.put(nombreSala, nuevaSala);

            // Enviar confirmación con info de multicast
            Mensaje respuesta = new Mensaje(Mensaje.RESPUESTA, "Servidor",
                                           nombreSala, "✅ Sala '" + nombreSala + "' creada");
            respuesta.setDireccionMulticast(dirMulticast);
            respuesta.setPuertoMulticast(puertoMulticast);
            enviarPaquete(respuesta, ip, puerto);

            System.out.println("✅ Sala creada: " + nombreSala +
                             " -> " + dirMulticast + ":" + puertoMulticast);
        } else {
            enviarRespuesta("⚠️ La sala ya existe", ip, puerto);
        }
    }

    private void unirseASala(Mensaje msg, InetAddress ip, int puerto) {
        String nombreSala = msg.getSala();
        Sala sala = salas.get(nombreSala);

        if (sala != null) {
            sala.agregarUsuario(msg.getUsuario());

            // Enviar info de la sala (dirección multicast)
            Mensaje respuesta = new Mensaje(Mensaje.INFO_SALA, "Servidor",
                                           nombreSala, "✅ Te uniste a '" + nombreSala + "'");
            respuesta.setDireccionMulticast(sala.getDireccionMulticast());
            respuesta.setPuertoMulticast(sala.getPuertoMulticast());
            enviarPaquete(respuesta, ip, puerto);

            // Notificar a todos en la sala vía multicast
            notificarViaMulticast(sala, "👋 " + msg.getUsuario() + " se unió a la sala");
            actualizarListaUsuariosMulticast(sala);

        } else {
            enviarRespuesta("❌ La sala no existe", ip, puerto);
        }
    }

    private void salirDeSala(Mensaje msg) {
        String nombreSala = msg.getSala();
        Sala sala = salas.get(nombreSala);

        if (sala != null) {
            sala.removerUsuario(msg.getUsuario());

            // Notificar a todos en la sala
            notificarViaMulticast(sala, "👋 " + msg.getUsuario() + " salió de la sala");
            actualizarListaUsuariosMulticast(sala);

            // Si la sala está vacía, eliminarla
            if (sala.estaVacia()) {
                salas.remove(nombreSala);
                System.out.println("🗑️ Sala '" + nombreSala + "' eliminada (vacía)");
            }
        }
    }

    private void desconectarUsuario(Mensaje msg) {
        String usuario = msg.getUsuario();
        System.out.println("👋 Usuario " + usuario + " desconectándose...");

        // Remover de todas las salas
        for (Sala sala : salas.values()) {
            if (sala.contieneUsuario(usuario)) {
                sala.removerUsuario(usuario);
                notificarViaMulticast(sala, "👋 " + usuario + " se desconectó");
                actualizarListaUsuariosMulticast(sala);
            }
        }

        // Limpiar salas vacías
        salas.entrySet().removeIf(entry -> entry.getValue().estaVacia());
    }

    private void actualizarListaUsuariosMulticast(Sala sala) {
        Set<String> usuarios = sala.getUsuarios();
        String lista = "USUARIOS:" + String.join(",", usuarios);
        notificarViaMulticast(sala, lista);
    }

    private void notificarViaMulticast(Sala sala, String contenido) {
        try {
            Mensaje msg = new Mensaje(Mensaje.RESPUESTA, "Servidor",
                                     sala.getNombre(), contenido);
            byte[] datos = msg.toBytes();

            InetAddress grupo = InetAddress.getByName(sala.getDireccionMulticast());
            DatagramPacket paquete = new DatagramPacket(datos, datos.length,
                                                       grupo, sala.getPuertoMulticast());

            DatagramSocket socketTemp = new DatagramSocket();
            socketTemp.send(paquete);
            socketTemp.close();

        } catch (Exception e) {
            System.err.println("Error enviando multicast: " + e.getMessage());
        }
    }

    private void enviarRespuesta(String contenido, InetAddress ip, int puerto) {
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

