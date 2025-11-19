import javax.sound.sampled.*;
import java.io.*;

public class ManejadorAudio {
    // Formato de audio compatible con Java Sound
    private static final AudioFormat formato = new AudioFormat(16000, 16, 1, true, false);
    private TargetDataLine lineaEntrada;
    private boolean grabando = false;
    private ByteArrayOutputStream streamAudio;

    // Iniciar grabación
    public void iniciarGrabacion() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, formato);

        if(!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("Micrófono no disponible o formato no soportado");
        }

        lineaEntrada = (TargetDataLine) AudioSystem.getLine(info);
        lineaEntrada.open(formato);
        lineaEntrada.start();

        grabando = true;
        streamAudio = new ByteArrayOutputStream();

        // Hilo para capturar audio
        Thread hiloCaptura = new Thread(() -> {
            byte[] buffer = new byte[4096];
            while(grabando) {
                int bytesLeidos = lineaEntrada.read(buffer, 0, buffer.length);
                if (bytesLeidos > 0) {
                    streamAudio.write(buffer, 0, bytesLeidos);
                }
            }
        });
        hiloCaptura.setDaemon(true);
        hiloCaptura.start();
        
        System.out.println("🎤 Grabación iniciada...");
    }

    // Detener grabación y obtener bytes
    public byte[] detenerGrabacion() {
        grabando = false;
        if(lineaEntrada != null) {
            lineaEntrada.stop();
            lineaEntrada.close();
        }
        
        byte[] audioData = streamAudio.toByteArray();
        System.out.println("⏹️ Grabación detenida: " + audioData.length + " bytes");
        return audioData;
    }
    
    // Reproducir audio desde bytes usando Java Sound
    public static void reproducirAudio(byte[] datosAudio) {
        if (datosAudio == null || datosAudio.length == 0) {
            System.err.println("❌ No hay datos de audio para reproducir");
            return;
        }
        
        System.out.println("🔊 Intentando reproducir audio: " + datosAudio.length + " bytes");
        
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(datosAudio);
            AudioInputStream audioInputStream = new AudioInputStream(bais, formato, 
                datosAudio.length / formato.getFrameSize());
            
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, formato);
            
            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("❌ Línea de audio no soportada para reproducción");
                return;
            }
            
            SourceDataLine lineaSalida = (SourceDataLine) AudioSystem.getLine(info);
            lineaSalida.open(formato);
            lineaSalida.start();

            System.out.println("▶️ Reproduciendo audio...");

            // Reproducir en un hilo separado
            Thread hiloReproduccion = new Thread(() -> {
                try {
                    byte[] buffer = new byte[4096];
                    int bytesLeidos;
                    while((bytesLeidos = audioInputStream.read(buffer)) != -1) {
                        if(bytesLeidos > 0) {
                            lineaSalida.write(buffer, 0, bytesLeidos);
                        }
                    }
                    lineaSalida.drain();
                    lineaSalida.close();
                    audioInputStream.close();
                    System.out.println("✅ Audio reproducido correctamente");
                } catch (IOException e) {
                    System.err.println("❌ Error reproduciendo audio: " + e.getMessage());
                }
            });
            hiloReproduccion.setDaemon(true);
            hiloReproduccion.start();
            
        } catch (LineUnavailableException e) {
            System.err.println("❌ Línea de audio no disponible: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("❌ Error al reproducir audio: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Método para verificar disponibilidad de audio
    public static boolean verificarAudioDisponible() {
        try {
            // Verificar grabación
            AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            boolean puedeGrabar = AudioSystem.isLineSupported(info);
            
            // Verificar reproducción
            info = new DataLine.Info(SourceDataLine.class, format);
            boolean puedeReproducir = AudioSystem.isLineSupported(info);
            
            System.out.println("🎤 Puede grabar: " + puedeGrabar);
            System.out.println("🔊 Puede reproducir: " + puedeReproducir);
            
            return puedeGrabar && puedeReproducir;
        } catch (Exception e) {
            System.err.println("❌ Error verificando audio: " + e.getMessage());
            return false;
        }
    }
}
