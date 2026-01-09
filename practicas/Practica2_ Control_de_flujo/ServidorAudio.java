import java.net.*;
import java.io.*;
import java.util.*;

public class ServidorAudio {

    static final int PUERTO = 1234;
    static final int TAM = 4096;
    static final int VENTANA = 5;
    static final int TIMEOUT = 1500;

    public static void main(String[] args) {

        try {
            DatagramSocket socket = new DatagramSocket(PUERTO);
            socket.setReuseAddress(true);

            System.out.println("Servidor iniciado, esperando cliente...");

            // 🔹 ESPERAR CLIENTE SIN TIMEOUT
            DatagramPacket peticion = new DatagramPacket(new byte[10], 10);
            socket.receive(peticion);

            InetAddress clienteIP = peticion.getAddress();
            int clientePuerto = peticion.getPort();

            String eleccion = new String(
                    peticion.getData(), 0, peticion.getLength()).trim();

            String archivo = eleccion.equals("1") ?
                    "cancion.mp3" : "cancion2.mp3";

            System.out.println("Cliente conectado. Enviando: " + archivo);

            // 🔹 AHORA SÍ activar timeout para Go-Back-N
            socket.setSoTimeout(TIMEOUT);

            FileInputStream fis = new FileInputStream(archivo);

            Map<Integer, byte[]> bufferVentana = new HashMap<>();

            int base = 0;
            int siguiente = 0;
            boolean finArchivo = false;

            byte[] buffer = new byte[TAM];

            while (!finArchivo || !bufferVentana.isEmpty()) {

                // Enviar mientras haya espacio en ventana
                while (!finArchivo && siguiente < base + VENTANA) {

                    int leidos = fis.read(buffer);
                    if (leidos == -1) {
                        finArchivo = true;
                        break;
                    }

                    byte[] audio = Arrays.copyOf(buffer, leidos);

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(baos);
                    dos.writeInt(siguiente); // secuencia
                    dos.writeInt(leidos);    // tamaño
                    dos.write(audio);        // datos
                    dos.flush();

                    byte[] paquete = baos.toByteArray();

                    DatagramPacket dp = new DatagramPacket(
                            paquete, paquete.length,
                            clienteIP, clientePuerto
                    );

                    socket.send(dp);
                    bufferVentana.put(siguiente, paquete);

                    System.out.println("Enviado paquete secuencia=" + siguiente);
                    siguiente++;
                }

                try {
                    DatagramPacket ack = new DatagramPacket(new byte[4], 4);
                    socket.receive(ack);

                    DataInputStream dis = new DataInputStream(
                            new ByteArrayInputStream(ack.getData()));

                    int ackNum = dis.readInt();
                    System.out.println("ACK recibido: " + ackNum);

                    if (ackNum >= base) {
                        for (int i = base; i <= ackNum; i++) {
                            bufferVentana.remove(i);
                        }
                        base = ackNum + 1;
                    }

                } catch (SocketTimeoutException e) {
                    System.out.println("Timeout → Go-Back-N desde secuencia " + base);

                    for (int i = base; i < siguiente; i++) {
                        byte[] pkt = bufferVentana.get(i);
                        if (pkt != null) {
                            socket.send(new DatagramPacket(
                                    pkt, pkt.length,
                                    clienteIP, clientePuerto
                            ));
                            System.out.println("Retransmitido secuencia=" + i);
                        }
                    }
                }
            }

            // 🔹 PAQUETE FIN
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(-1);
            dos.writeInt(0);
            dos.flush();

            DatagramPacket fin = new DatagramPacket(
                    baos.toByteArray(),
                    baos.size(),
                    clienteIP, clientePuerto
            );
            socket.send(fin);

            System.out.println("Servidor terminó transmisión correctamente");

            fis.close();
            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
