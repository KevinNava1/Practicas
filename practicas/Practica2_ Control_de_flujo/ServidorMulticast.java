import java.net.*;
import java.io.*;
import java.util.*;

public class ServidorMulticast {
    private static final String GRUPO_MULTICAST = "230.0.0.1";
    private static final int PUERTO_MULTICAST = 7777;
    private static final int PUERTO_CONTROL = 7778;
    private static final int TAMANO_PAQUETE = 16384;
    private static final int TAMANO_VENTANA = 4;
    
    private static Map<String, Integer> acksClientes = Collections.synchronizedMap(new HashMap<>());
    private static boolean transmisionActiva = false;
    private static Scanner scanner = new Scanner(System.in);
    
    public static void main(String[] args){
        try{
            MulticastSocket s = new MulticastSocket();
            s.setReuseAddress(true);
            s.setTimeToLive(1);
            
            DatagramSocket socketControl = new DatagramSocket(PUERTO_CONTROL);
            socketControl.setSoTimeout(1000);
            
            InetAddress gpo = InetAddress.getByName(GRUPO_MULTICAST);
            
            System.out.println("========================================");
            System.out.println("   SERVIDOR MULTICAST CON MENU");
            System.out.println("========================================");
            System.out.println("Grupo: " + GRUPO_MULTICAST + ":" + PUERTO_MULTICAST);
            System.out.println("Control: " + PUERTO_CONTROL);
            System.out.println("Ventana: " + TAMANO_VENTANA + " paquetes");
            System.out.println("Servicio iniciado...");
            
            // Hilo para ACKs
            Thread hiloACKs = new Thread(() -> manejarACKs(socketControl));
            hiloACKs.start();
            
            // Menú principal
            mostrarMenu(s, gpo, socketControl);
            
            s.close();
            socketControl.close();
            scanner.close();
            
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    private static void mostrarMenu(MulticastSocket s, InetAddress gpo, DatagramSocket socketControl) {
        while (true) {
            System.out.println("\n=== MENÚ SERVIDOR ===");
            System.out.println("1. Transmitir canción MP3");
            System.out.println("2. Ver clientes conectados");
            System.out.println("3. Limpiar lista de clientes");
            System.out.println("4. Salir");
            System.out.print("Seleccione opción: ");
            
            try {
                int opcion = scanner.nextInt();
                scanner.nextLine(); // Limpiar buffer
                
                switch (opcion) {
                    case 1:
                        transmitirCancion(s, gpo, socketControl);
                        break;
                    case 2:
                        verClientesConectados();
                        break;
                    case 3:
                        limpiarClientes();
                        break;
                    case 4:
                        System.out.println("Saliendo del servidor...");
                        return;
                    default:
                        System.out.println("Opción inválida");
                }
            } catch (Exception e) {
                System.out.println("Error en menú: " + e.getMessage());
                scanner.nextLine(); // Limpiar buffer en caso de error
            }
        }
    }
    
    private static void transmitirCancion(MulticastSocket s, InetAddress gpo, DatagramSocket socketControl) {
        System.out.print("Ingrese el nombre del archivo MP3: ");
        String archivoMP3 = scanner.nextLine();
        
        File file = new File(archivoMP3);
        if (!file.exists()) {
            System.out.println("El archivo '" + archivoMP3 + "' no existe");
            System.out.println("Archivos MP3 disponibles:");
            listarArchivosMP3();
            return;
        }
        
        System.out.println(" Transmitiendo: " + archivoMP3);
        System.out.println("¿Iniciar transmisión? (s/n): ");
        String confirmacion = scanner.nextLine();
        
        if (confirmacion.equalsIgnoreCase("s")) {
            transmitirArchivo(s, gpo, archivoMP3);
        } else {
            System.out.println("Transmisión cancelada");
        }
    }
    
    private static void listarArchivosMP3() {
        File directorio = new File(".");
        File[] archivos = directorio.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp3"));
        
        if (archivos == null || archivos.length == 0) {
            System.out.println("No se encontraron archivos MP3");
            return;
        }
        
        for (int i = 0; i < archivos.length; i++) {
            System.out.println("   " + (i + 1) + ". " + archivos[i].getName() + 
                             " (" + archivos[i].length() + " bytes)");
        }
    }
    
    private static void manejarACKs(DatagramSocket socketControl) {
        byte[] buffer = new byte[256];
        
        while (true) {
            try {
                DatagramPacket p = new DatagramPacket(buffer, buffer.length);
                socketControl.receive(p);
                
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(p.getData()));
                String idCliente = dis.readUTF();
                int ack = dis.readInt();
                dis.close();
                
                String clave = p.getAddress() + ":" + idCliente;
                
                synchronized (acksClientes) {
                    acksClientes.put(clave, ack);
                }
                
                System.out.println("ACK de " + idCliente + ": " + ack);
                
            } catch (SocketTimeoutException e) {
                // Timeout normal
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    private static void transmitirArchivo(MulticastSocket s, InetAddress gpo, String archivoMP3) {
        try {
            File file = new File(archivoMP3);
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[TAMANO_PAQUETE];
            int bytesLeidos;
            int numPaquete = 0;
            int secuencia = 0;
            int base = 0;
            
            byte[][] ventana = new byte[TAMANO_VENTANA][];
            int[] secuencias = new int[TAMANO_VENTANA];
            
            transmisionActiva = true;
            
            System.out.println(" INICIANDO TRANSMISIÓN...");
            System.out.println(" Tamaño: " + file.length() + " bytes");
            System.out.println(" Clientes conectados: " + acksClientes.size());
            
            while (transmisionActiva && (bytesLeidos = fis.read(buffer)) != -1) {
                boolean transmitido = false;
                int intentos = 0;
                
                while (!transmitido && intentos < 3) {
                    try {
                        // Crear paquete
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        DataOutputStream dos = new DataOutputStream(baos);
                        dos.writeInt(secuencia);
                        dos.writeInt(numPaquete);
                        dos.writeInt(bytesLeidos);
                        dos.write(buffer, 0, bytesLeidos);
                        dos.flush();
                        
                        byte[] datos = baos.toByteArray();
                        
                        ventana[secuencia % TAMANO_VENTANA] = datos;
                        secuencias[secuencia % TAMANO_VENTANA] = secuencia;
                        
                        // Enviar
                        DatagramPacket p = new DatagramPacket(datos, datos.length, gpo, PUERTO_MULTICAST);
                        s.send(p);
                        
                        System.out.println("Enviado - Paq: " + numPaquete + ", Sec: " + secuencia + ", Bytes: " + bytesLeidos);
                        
                        Thread.sleep(50);
                        
                        if (puedeAvanzar(base)) {
                            base++;
                            System.out.println("Ventana avanzada a: " + base);
                        }
                        
                        transmitido = true;
                        numPaquete++;
                        secuencia++;
                        
                    } catch (Exception e) {
                        intentos++;
                        System.out.println(" Timeout #" + intentos + " - ACTIVANDO RETROCEDER N");
                        retrocederN(s, gpo, ventana, secuencias, base, secuencia);
                    }
                }
                
                if (!transmitido) {
                    System.err.println(" Error en paquete " + numPaquete);
                    break;
                }
                
                Arrays.fill(buffer, (byte)0);
            }
            
            enviarFin(s, gpo);
            
            System.out.println("========================================");
            System.out.println(" TRANSMISIÓN COMPLETADA");
            System.out.println(" Total paquetes: " + numPaquete);
            System.out.println(" Clientes finales: " + acksClientes.size());
            System.out.println("========================================");
            
            fis.close();
            transmisionActiva = false;
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static boolean puedeAvanzar(int base) {
        synchronized (acksClientes) {
            if (acksClientes.isEmpty()) return true;
            for (int ack : acksClientes.values()) {
                if (ack < base) return false;
            }
            return true;
        }
    }
    
    private static void retrocederN(MulticastSocket s, InetAddress gpo, byte[][] ventana, 
                                  int[] secuencias, int base, int secuenciaActual) {
        try {
            System.out.println(" RETROCEDER N: Retransmitiendo desde " + base);
            
            for (int i = base; i < secuenciaActual; i++) {
                byte[] paquete = ventana[i % TAMANO_VENTANA];
                if (paquete != null) {
                    DatagramPacket p = new DatagramPacket(paquete, paquete.length, gpo, PUERTO_MULTICAST);
                    s.send(p);
                    System.out.println(" Retransmitido secuencia: " + i);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void enviarFin(MulticastSocket s, InetAddress gpo) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(-1);
            dos.writeInt(-1);
            dos.writeInt(0);
            dos.flush();
            
            byte[] datos = baos.toByteArray();
            DatagramPacket p = new DatagramPacket(datos, datos.length, gpo, PUERTO_MULTICAST);
            s.send(p);
            
            System.out.println(" Paquete FIN enviado");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void verClientesConectados() {
        synchronized (acksClientes) {
            System.out.println("\n=== CLIENTES CONECTADOS ===");
            if (acksClientes.isEmpty()) {
                System.out.println("No hay clientes conectados");
            } else {
                for (Map.Entry<String, Integer> entry : acksClientes.entrySet()) {
                    System.out.println("🔹 " + entry.getKey() + " - Último ACK: " + entry.getValue());
                }
                System.out.println("Total: " + acksClientes.size() + " clientes");
            }
        }
    }
    
    private static void limpiarClientes() {
        synchronized (acksClientes) {
            acksClientes.clear();
            System.out.println(" Lista de clientes limpiada");
        }
    }
}