import java.io.*;
import java.net.*;
import java.util.*;
import javazoom.jl.player.Player;

public class ClienteMulticast {
    private static final String GRUPO_MULTICAST = "230.0.0.1";
    private static final int PUERTO_MULTICAST = 7777;
    private static final int PUERTO_CONTROL = 7778;
    private static final String SERVIDOR = "127.0.0.1";
    
    private static String idCliente = "CLI_" + (new Random().nextInt(9000) + 1000);
    private static Scanner scanner = new Scanner(System.in);
    
    public static void main(String[] args){
        try{
            System.out.println("========================================");
            System.out.println("   CLIENTE MULTICAST CONTINUO");
            System.out.println("========================================");
            System.out.println("ID: " + idCliente);
            System.out.println("Grupo: " + GRUPO_MULTICAST + ":" + PUERTO_MULTICAST);
            System.out.println("Conectando automaticamente...");
            
            // Conectar inmediatamente
            conectarServidor();
            
            // Bucle principal - escuchar continuamente
            escucharContinuamente();
            
            scanner.close();
            System.out.println("Cliente finalizado");
            
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    private static void conectarServidor() {
        try {
            DatagramSocket socketControl = new DatagramSocket();
            InetAddress servidor = InetAddress.getByName(SERVIDOR);
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeUTF(idCliente);
            dos.writeInt(0);
            dos.flush();
            
            byte[] datos = baos.toByteArray();
            DatagramPacket p = new DatagramPacket(datos, datos.length, servidor, PUERTO_CONTROL);
            socketControl.send(p);
            
            socketControl.close();
            System.out.println("Conectado al servidor multicast");
            
        } catch (Exception e) {
            System.err.println("Error conectando: " + e.getMessage());
        }
    }
    
    private static void escucharContinuamente() {
        MulticastSocket cl = null;
        DatagramSocket socketControl = null;
        
        try {
            // Crear sockets una sola vez
            cl = new MulticastSocket(PUERTO_MULTICAST);
            cl.setReuseAddress(true);
            
            InetAddress gpo = InetAddress.getByName(GRUPO_MULTICAST);
            cl.joinGroup(gpo);
            
            socketControl = new DatagramSocket();
            
            System.out.println("Escuchando transmisiones...");
            System.out.println("Presiona Ctrl+C para salir");
            System.out.println("Esperando datos del servidor...");
            
            // Bucle infinito de escucha
            while (true) {
                int ultimaSecuencia = -1;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                boolean transmisionCompleta = false;
                
                System.out.println("\n--- Esperando nueva transmision ---");
                
                // Escuchar una transmision completa
                while (!transmisionCompleta) {
                    DatagramPacket p = new DatagramPacket(new byte[65535], 65535);
                    cl.receive(p);
                    
                    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(p.getData()));
                    int secuencia = dis.readInt();
                    int numPaquete = dis.readInt();
                    int tamDatos = dis.readInt();
                    
                    if (secuencia == -1) {
                        System.out.println("Fin de transmision recibido");
                        transmisionCompleta = true;
                        break;
                    }
                    
                    System.out.println("Paquete #" + numPaquete + ", Sec: " + secuencia + ", Bytes: " + tamDatos);
                    
                    // Control de ventana deslizante
                    if (secuencia == ultimaSecuencia + 1) {
                        byte[] datos = new byte[tamDatos];
                        dis.readFully(datos);
                        
                        baos.write(datos);
                        ultimaSecuencia = secuencia;
                        
                        enviarACK(socketControl, ultimaSecuencia);
                        
                    } else if (secuencia > ultimaSecuencia + 1) {
                        System.out.println("Paquete fuera de orden: " + secuencia);
                        enviarACK(socketControl, ultimaSecuencia);
                    }
                    
                    dis.close();
                }
                
                // Reproducir cuando tengamos todos los datos
                if (baos.size() > 0) {
                    System.out.println("Transmision completa - Reproduciendo archivo...");
                    System.out.println("Tamaño total recibido: " + baos.size() + " bytes");
                    reproducirMP3Completo(baos.toByteArray());
                }
                
                // Preguntar si quiere seguir escuchando
                System.out.print("\n¿Esperar otra transmision? (s/n): ");
                String respuesta = scanner.nextLine();
                
                if (respuesta.equalsIgnoreCase("n")) {
                    System.out.println("Saliendo...");
                    break;
                }
                
                System.out.println("Continuando escucha...");
            }
            
        } catch (Exception e) {
            System.err.println("Error en escucha continua: " + e.getMessage());
        } finally {
            // Cerrar sockets al final
            try {
                if (cl != null) {
                    InetAddress gpo = InetAddress.getByName(GRUPO_MULTICAST);
                    cl.leaveGroup(gpo);
                    cl.close();
                }
                if (socketControl != null) {
                    socketControl.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    private static void enviarACK(DatagramSocket socketControl, int ack) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeUTF(idCliente);
            dos.writeInt(ack);
            dos.flush();
            
            byte[] datos = baos.toByteArray();
            InetAddress servidor = InetAddress.getByName(SERVIDOR);
            DatagramPacket p = new DatagramPacket(datos, datos.length, servidor, PUERTO_CONTROL);
            socketControl.send(p);
            
        } catch (Exception e) {
            System.err.println("Error enviando ACK: " + e.getMessage());
        }
    }
    
    private static void reproducirMP3Completo(byte[] audioData) {
        try {
            System.out.println("Iniciando reproduccion de archivo completo...");
            ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
            Player player = new Player(bais);
            
            Thread reproductorThread = new Thread(() -> {
                try {
                    player.play();
                    System.out.println("Reproduccion completada");
                } catch (Exception e) {
                    System.err.println("Error durante la reproduccion: " + e.getMessage());
                }
            });
            
            reproductorThread.start();
            
            // Esperar a que termine la reproduccion
            reproductorThread.join();
            
        } catch (Exception e) {
            System.err.println("Error al reproducir MP3: " + e.getMessage());
        }
    }
}