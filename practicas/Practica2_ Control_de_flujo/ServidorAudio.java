import java.net.*;
import java.io.*;
import javazoom.jl.player.Player;

import java.util.*;

public class ServidorAudio {
    private static Map<String, ByteArrayOutputStream> transmisionesActivas = new HashMap<>();
    private static Map<String, Integer> ultimasSecuencias = new HashMap<>();
    
    public static void main(String[] args){
        try{
            int pto = 1234;
            int tamPaquete = 4096;
            int ventana = 5;

            DatagramSocket s = new DatagramSocket(pto);
            s.setReuseAddress(true);

            System.out.println("Servidor de Audio MP3 iniciado... Esperando petición de cliente...");

            DatagramPacket peticion = new DatagramPacket(new byte[20], 20);
                s.receive(peticion);
            String eleccion = new String(peticion.getData(), 0, peticion.getLength()).trim();

            System.out.println("Transmitiendo canción: " + eleccion);

            // Determinar el archivo a enviar
            String archivoMP3;
            if("1".equals(eleccion)) archivoMP3 = "cancion.mp3";
            else archivoMP3 = "cancion2.mp3";

            // Parámetros del cliente
            InetAddress dst = peticion.getAddress();
            int puertoCliente = peticion.getPort();

            // Leer archivo MP3 como bytes binarios
            FileInputStream fis = new FileInputStream(new File(archivoMP3));
            byte[] bufferAudio = new byte[tamPaquete];
            int bytesLeidos;
            int numPaquete = 0;
            int secuencia = 0;
            int base = 0;
            
            // Estructuras para retransmisión
            byte[][] ventanaPaquetes = new byte[ventana][];
            int[] secuenciasVentana = new int[ventana];

            //timeout
            s.setSoTimeout(1500); // ms
            
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
                        DatagramPacket pMeta = new DatagramPacket(bMeta, bMeta.length, dst, puertoCliente);
                        
                        // Datos MP3
                        byte[] audioData = Arrays.copyOf(bufferAudio, bytesLeidos);
                        DatagramPacket pAudio = new DatagramPacket(audioData, audioData.length, dst, puertoCliente);
                        
                        // Almacenar para retransmisión
                        ventanaPaquetes[secuencia % ventana] = audioData;
                        secuenciasVentana[secuencia % ventana] = secuencia;
                        
                        // Enviar
                        s.send(pMeta);
                        s.send(pAudio);
                        
                        System.out.println("Enviado paquete #" + numPaquete + 
                                         ", secuencia: " + secuencia + 
                                         ", bytes: " + bytesLeidos);
                        
                        // Esperar ACK
                        DatagramPacket pACK = new DatagramPacket(new byte[4], 4);
                        s.receive(pACK);
                        
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
                                DatagramPacket pMeta = new DatagramPacket(bMeta, bMeta.length, dst, puertoCliente);
                                s.send(pMeta);
                                
                                // Retransmitir audio
                                DatagramPacket pAudio = new DatagramPacket(
                                    ventanaPaquetes[i % ventana], 
                                    ventanaPaquetes[i % ventana].length, 
                                    dst, puertoCliente
                                );
                                s.send(pAudio);
                                
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
            DatagramPacket pFin = new DatagramPacket(bFin, bFin.length, dst, puertoCliente);
            s.send(pFin);
            
            System.out.println("Transmisión completada. Total paquetes: " + numPaquete);
            
            fis.close();
            s.close();

        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
