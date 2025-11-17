import javax.sound.sampled.*;
import java.io.*;

public class ManejadorAudio {
    private static final AudioFormat formato = new AudioFormat(16000, 8, 1, true, true);
    private TargetDataLine lineaEntrada;
    private boolean grabando = false;
    private ByteArrayOutputStream streamAudio;

    // Iniciar grabación
    public void iniciarGrabacion() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, formato);

        if(!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("Micrófono no disponible");
        }

        lineaEntrada = (TargetDataLine) AudioSystem.getLine(info);
        lineaEntrada.open(formato);
        lineaEntrada.start();

        grabando = true;
        streamAudio = new ByteArrayOutputStream();

        // Hilo para capturar audioi
        Thread hiloCaptura = new Thread(() -> {
            byte[] buffer = new byte[4096];
            while(grabando) {
                int bytesLeidos = lineaEntrada.read(buffer, 0, buffer.length);
                streamAudio.write(buffer, 0, bytesLeidos);
            }
        });
        hiloCaptura.start();
    }

    // Detener grabación y obtener bytes
    public byte[] detenerGrabacion() {
        grabando = false;
        if(lineaEntrada != null) {
            lineaEntrada.stop();
            lineaEntrada.close();
        }
        return streamAudio.toByteArray();
    }
    
    // Reproducri audio desde bytes
    public static void reproducriAudio(byte[] datosAudio) {
        try{
            ByteArrayInputStream bais = new ByteArrayInputStream(datosAudio);
            AudioInputStream audioInputStream = new AudioInputStream(bais, formato, datosAudio.length);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, formato);
            SourceDataLine lineaSalida = (SourceDataLine) AudioSystem.getLine(info);
            lineaSalida.start();

            // Reproducir en un hilo separado
            new Thread(() -> {
                try {
                    byte[] buffer = new byte[4096];
                    int bytesLeidos;
                    while((bytesLeidos = audioInputStream.read(buffer, 0, buffer.length)) != -1) {
                        lineaSalida.write(buffer, 0, bytesLeidos);
                    }
                    lineaSalida.drain();
                    lineaSalida.close();
                } catch (IOException e) {
                    System.err.println("Error reproduciendo audio: " + e.getMessage());
                }
            }).start();
        } catch (Exception e) {
            System.err.println("Error al reproducir: " + e.getMessage());
        }
    }
}