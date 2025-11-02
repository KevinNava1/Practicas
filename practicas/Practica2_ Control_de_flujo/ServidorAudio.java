import java.net.*;
import java.io.*;
import javax.sound.sampled.*;
import javazoom.jl.player.Player;
import java.io.ByteArrayInputStream;

/**
 *
 * @author axele
 */
public class ServidorAudio {
    public static void main(String[] args){
        try{
            int pto = 1234;
            DatagramSocket s = new DatagramSocket(pto);
            s.setReuseAddress(true);
            System.out.println("Servidor de Audio MP3 iniciado... esperando datagramas...");
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int ultimaSecuencia = -1;
            
            for(;;){
                // Recibir paquete con metadatos
                DatagramPacket pMetadatos = new DatagramPacket(new byte[20], 20);
                s.receive(pMetadatos);
                
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(pMetadatos.getData()));
                int numPaquete = dis.readInt();
                int tamAudio = dis.readInt();
                int secuencia = dis.readInt();
                int tipo = dis.readInt();
                dis.close();
                
                if (numPaquete == -1) {
                    System.out.println("Fin de transmisión recibido");
                    
                    // Reproducir el archivo MP3 completo recibido
                    if (baos.size() > 0) {
                        System.out.println("Reproduciendo archivo MP3 recibido...");
                        reproducirMP3(baos.toByteArray());
                    }
                    
                    baos.reset();
                    continue;
                }
                
                // Recibir datos de audio
                DatagramPacket pAudio = new DatagramPacket(new byte[tamAudio], tamAudio);
                s.receive(pAudio);
                
                System.out.println("Paquete recibido: #" + numPaquete + 
                                 ", secuencia: " + secuencia + 
                                 ", bytes: " + tamAudio);
                
                // Almacenar datos en orden
                if (secuencia > ultimaSecuencia) {
                    baos.write(pAudio.getData(), 0, pAudio.getLength());
                    ultimaSecuencia = secuencia;
                }
                
                // Enviar ACK
                ByteArrayOutputStream baosACK = new ByteArrayOutputStream();
                DataOutputStream dosACK = new DataOutputStream(baosACK);
                dosACK.writeInt(secuencia);
                dosACK.flush();
                
                byte[] bACK = baosACK.toByteArray();
                DatagramPacket pACK = new DatagramPacket(bACK, bACK.length, 
                                                        pMetadatos.getAddress(), 
                                                        pMetadatos.getPort());
                s.send(pACK);
                
                dosACK.close();
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    private static void reproducirMP3(byte[] audioData) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
            Player player = new Player(bais);
            
            // Reproducir en un hilo separado para no bloquear
            Thread playbackThread = new Thread() {
                public void run() {
                    try {
                        player.play();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            playbackThread.start();
            
        } catch (Exception e) {
            System.err.println("Error al reproducir MP3: " + e.getMessage());
            e.printStackTrace();
        }
    }
}