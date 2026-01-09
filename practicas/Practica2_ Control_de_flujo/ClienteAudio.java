import java.net.*;
import java.io.*;
import java.util.*;
import javazoom.jl.player.Player;

public class ClienteAudio {

    static volatile boolean pausa = false;
    static volatile boolean fin = false;
    static final Object lock = new Object();

    static int esperado = 0;

    public static void main(String[] args) {

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setReuseAddress(true);
            InetAddress serverIP = InetAddress.getByName("127.0.0.1");

            Scanner sc = new Scanner(System.in);
            System.out.print("Elige canción (1 o 2): ");
            String eleccion = sc.nextLine();

            socket.send(new DatagramPacket(
                    eleccion.getBytes(), eleccion.length(), serverIP, 1234));

            // PIPE para streaming
            PipedOutputStream pos = new PipedOutputStream();
            PipedInputStream pis = new PipedInputStream(pos, 65536);

            // HILO REPRODUCTOR
            Thread reproductor = new Thread(() -> {
                try {
                    Player player = new Player(pis);
                    player.play();
                    System.out.println("Reproducción terminada");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            reproductor.start();

            // HILO CONTROL
            new Thread(() -> {
                Scanner in = new Scanner(System.in);
                while (true) {
                    String cmd = in.nextLine();
                    synchronized (lock) {
                        if (cmd.equalsIgnoreCase("p")) {
                            pausa = true;
                            System.out.println("PAUSA");
                        } else if (cmd.equalsIgnoreCase("r")) {
                            pausa = false;
                            lock.notifyAll();
                            System.out.println("REANUDAR");
                        }
                    }
                }
            }).start();

            // RECEPCIÓN
            while (!fin) {
                DatagramPacket dp = new DatagramPacket(new byte[8192], 8192);
                socket.receive(dp);

                DataInputStream dis = new DataInputStream(
                        new ByteArrayInputStream(dp.getData(), 0, dp.getLength()));

                int secuencia = dis.readInt();
                int tam = dis.readInt();

                if (secuencia == -1) {
                    fin = true;
                    System.out.println("Fin de transmisión recibido");
                    break;
                }

                byte[] audio = new byte[tam];
                dis.readFully(audio);

                System.out.println("Recibido secuencia=" + secuencia);

                if (secuencia == esperado) {

                    synchronized (lock) {
                        while (pausa) lock.wait();

                        pos.write(audio);
                        pos.flush();

                        // ACK acumulativo
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        DataOutputStream dos = new DataOutputStream(baos);
                        dos.writeInt(secuencia);
                        dos.flush();

                        socket.send(new DatagramPacket(
                                baos.toByteArray(),
                                baos.size(),
                                dp.getAddress(),
                                dp.getPort()
                        ));
                    }

                    esperado++;
                }
            }

            // Esperar a que termine de reproducir
            while (reproductor.isAlive()) {
                Thread.sleep(100);
            }

            pos.close();
            pis.close();
            socket.close();
            sc.close();

            System.out.println("Cliente cerrado correctamente");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
