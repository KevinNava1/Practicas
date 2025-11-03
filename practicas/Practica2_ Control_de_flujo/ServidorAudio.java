import java.net.*;
import java.io.*;
import javazoom.jl.player.Player;
import java.io.ByteArrayInputStream;
import java.util.*;

public class ServidorAudio {
    private static Map<String, ByteArrayOutputStream> transmisionesActivas = new HashMap<>();
    private static Map<String, Integer> ultimasSecuencias = new HashMap<>();
    
    public static void main(String[] args){
        try{
            int pto = 1234;
            DatagramSocket s = new DatagramSocket(pto);
            s.setReuseAddress(true);
            System.out.println("Servidor de Audio MP3 iniciado... esperando datagramas...");
            
            for(;;){
                // Recibir paquete con metadatos
                DatagramPacket pMetadatos = new DatagramPacket(new byte[20], 20);
                s.receive(pMetadatos);
                
                String clienteId = pMetadatos.getAddress().toString() + ":" + pMetadatos.getPort();
                
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(pMetadatos.getData()));
                int numPaquete = dis.readInt();
                int tamAudio = dis.readInt();
                int secuencia = dis.readInt();
                int tipo = dis.readInt();
                dis.close();
                
                if (numPaquete == -1) {
                    System.out.println("Fin de transmisión recibido de " + clienteId);
                    
                    // Verificar si tenemos datos para este cliente
                    if (transmisionesActivas.containsKey(clienteId) && 
                        transmisionesActivas.get(clienteId).size() > 0) {
                        
                        byte[] audioData = transmisionesActivas.get(clienteId).toByteArray();
                        System.out.println("Reproduciendo archivo MP3 recibido (" + audioData.length + " bytes)...");
                        
                        // Reproducir SIN hilos
                        reproducirMP3(audioData);
                        
                        // Limpiar datos de este cliente
                        transmisionesActivas.remove(clienteId);
                        ultimasSecuencias.remove(clienteId);
                    }
                    
                    continue;
                }
                
                // Inicializar almacenamiento para nuevo cliente
                if (!transmisionesActivas.containsKey(clienteId)) {
                    transmisionesActivas.put(clienteId, new ByteArrayOutputStream());
                    ultimasSecuencias.put(clienteId, -1);
                }
                
                // Recibir datos de audio
                DatagramPacket pAudio = new DatagramPacket(new byte[tamAudio], tamAudio);
                s.receive(pAudio);
                
                System.out.println("Paquete recibido de " + clienteId + 
                                 ": #" + numPaquete + ", secuencia: " + secuencia + 
                                 ", bytes: " + tamAudio);
                
                // Almacenar datos en orden
                if (secuencia > ultimasSecuencias.get(clienteId)) {
                    transmisionesActivas.get(clienteId).write(pAudio.getData(), 0, pAudio.getLength());
                    ultimasSecuencias.put(clienteId, secuencia);
                }
                
                // Enviar ACK
                enviarACK(s, secuencia, pMetadatos.getAddress(), pMetadatos.getPort());
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    private static void enviarACK(DatagramSocket s, int secuencia, InetAddress address, int port) {
        try {
            ByteArrayOutputStream baosACK = new ByteArrayOutputStream();
            DataOutputStream dosACK = new DataOutputStream(baosACK);
            dosACK.writeInt(secuencia);
            dosACK.flush();
            
            byte[] bACK = baosACK.toByteArray();
            DatagramPacket pACK = new DatagramPacket(bACK, bACK.length, address, port);
            s.send(pACK);
            
            dosACK.close();
        } catch (Exception e) {
            System.err.println("Error enviando ACK: " + e.getMessage());
        }
    }
    
    private static void reproducirMP3(byte[] audioData) {
        try {
            System.out.println("Iniciando reproducción de " + audioData.length + " bytes...");
            ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
            Player player = new Player(bais);
            
            // Reproducción SIN hilos
            player.play();
            System.out.println("Reproducción finalizada");
            
        } catch (Exception e) {
            System.err.println("Error al reproducir MP3: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
