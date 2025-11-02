import java.net.*;
import java.io.*;
import java.util.Arrays;


public class ClienteAudio {
    public static void main(String[] args) {
        try {
            if (args.length != 1) {
                System.out.println("Uso: java ClienteAudioBasico <archivo.mp3>");
                return;
            }
            
            String archivoMP3 = args[0];
            int pto = 1234;
            String dir = "127.0.0.1";
            InetAddress dst = InetAddress.getByName(dir);
            int tamPaquete = 4096;
            int ventana = 5;
            
            DatagramSocket cl = new DatagramSocket();
            cl.setSoTimeout(3000);
            
            System.out.println("Transmitiendo archivo MP3: " + archivoMP3);
            
            // Leer archivo MP3 como bytes binarios
            File file = new File(archivoMP3);
            FileInputStream fis = new FileInputStream(file);
            
            byte[] bufferAudio = new byte[tamPaquete];
            int bytesLeidos;
            int numPaquete = 0;
            int secuencia = 0;
            int base = 0;
            
            byte[][] ventanaPaquetes = new byte[ventana][];
            int[] secuenciasVentana = new int[ventana];
            
            while ((bytesLeidos = fis.read(bufferAudio)) != -1) {
                boolean transmitido = false;
                
                while (!transmitido) {
                    try {
                        // Metadatos
                        ByteArrayOutputStream baosMeta = new ByteArrayOutputStream();
                        DataOutputStream dosMeta = new DataOutputStream(baosMeta);
                        dosMeta.writeInt(numPaquete);      // Número de paquete
                        dosMeta.writeInt(bytesLeidos);     // Tamaño del audio
                        dosMeta.writeInt(secuencia);       // Número de secuencia
                        dosMeta.writeInt(0);              // Tipo: 0=datos MP3 crudos
                        dosMeta.flush();
                        
                        byte[] bMeta = baosMeta.toByteArray();
                        DatagramPacket pMeta = new DatagramPacket(bMeta, bMeta.length, dst, pto);
                        
                        // Datos MP3
                        byte[] audioData = Arrays.copyOf(bufferAudio, bytesLeidos);
                        DatagramPacket pAudio = new DatagramPacket(audioData, audioData.length, dst, pto);
                        
                        // Almacenar para retransmisión
                        ventanaPaquetes[secuencia % ventana] = audioData;
                        secuenciasVentana[secuencia % ventana] = secuencia;
                        
                        // Enviar
                        cl.send(pMeta);
                        cl.send(pAudio);
                        
                        System.out.println("Enviado paquete #" + numPaquete + 
                                         ", secuencia: " + secuencia + 
                                         ", bytes: " + bytesLeidos);
                        
                        // Esperar ACK
                        DatagramPacket pACK = new DatagramPacket(new byte[4], 4);
                        cl.receive(pACK);
                        
                        DataInputStream disACK = new DataInputStream(new ByteArrayInputStream(pACK.getData()));
                        int ackRecibido = disACK.readInt();
                        disACK.close();
                        
                        System.out.println("ACK recibido: " + ackRecibido);
                        
                        if (ackRecibido >= base) {
                            base = ackRecibido + 1;
                        }
                        
                        transmitido = true;
                        numPaquete++;
                        secuencia++;
                        
                    } catch (SocketTimeoutException e) {
                        System.out.println("Timeout - Retransmitiendo desde secuencia: " + base);
                        
                        for (int i = base; i < secuencia; i++) {
                            if (ventanaPaquetes[i % ventana] != null) {
                                // Retransmitir metadatos
                                ByteArrayOutputStream baosMeta = new ByteArrayOutputStream();
                                DataOutputStream dosMeta = new DataOutputStream(baosMeta);
                                dosMeta.writeInt(numPaquete - (secuencia - i));
                                dosMeta.writeInt(ventanaPaquetes[i % ventana].length);
                                dosMeta.writeInt(i);
                                dosMeta.writeInt(0);
                                dosMeta.flush();
                                
                                byte[] bMeta = baosMeta.toByteArray();
                                DatagramPacket pMeta = new DatagramPacket(bMeta, bMeta.length, dst, pto);
                                cl.send(pMeta);
                                
                                // Retransmitir audio
                                DatagramPacket pAudio = new DatagramPacket(
                                    ventanaPaquetes[i % ventana], 
                                    ventanaPaquetes[i % ventana].length, 
                                    dst, pto
                                );
                                cl.send(pAudio);
                                
                                System.out.println("Retransmitido paquete secuencia: " + i);
                            }
                        }
                    }
                }
                
                Arrays.fill(bufferAudio, (byte)0);
            }
            
            // Paquete de fin
            ByteArrayOutputStream baosFin = new ByteArrayOutputStream();
            DataOutputStream dosFin = new DataOutputStream(baosFin);
            dosFin.writeInt(-1);
            dosFin.writeInt(0);
            dosFin.writeInt(-1);
            dosFin.writeInt(-1);
            dosFin.flush();
            
            byte[] bFin = baosFin.toByteArray();
            DatagramPacket pFin = new DatagramPacket(bFin, bFin.length, dst, pto);
            cl.send(pFin);
            
            System.out.println("Transmisión completada. Total paquetes: " + numPaquete);
            
            fis.close();
            cl.close();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}