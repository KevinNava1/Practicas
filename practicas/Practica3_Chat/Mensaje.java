import java.io.*;

public class Mensaje implements Serializable {
    // Tipos de mensaje
    public static final String CREAR_SALA = "CREAR_SALA";
    public static final String UNIRSE_SALA = "UNIRSE_SALA";
    public static final String SALIR_SALA = "SALIR_SALA";
    public static final String MENSAJE_SALA = "MENSAJE_SALA";
    public static final String MENSAJE_PRIVADO = "MENSAJE_PRIVADO";
    public static final String LISTAR_USUARIOS = "LISTAR_USUARIOS";
    public static final String ENVIAR_STICKER = "ENVIAR_STICKER";
    public static final String ENVIAR_AUDIO = "ENVIAR_AUDIO";
    public static final String RESPUESTA = "RESPUESTA";

    private String tipo;
    private String usuario;
    private String sala;
    private String contenido;
    private String destinatario; // Para mensajes privados
    private byte[] datos; // Para stickers y audios

    public Mensaje(String tipo, String usuario, String sala, String contenido) {
        this.tipo = tipo;
        this.usuario = usuario;
        this.sala = sala;
        this.contenido = contenido;
    }

    // Getters y Setters
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }

    public String getSala() { return sala; }
    public void setSala(String sala) { this.sala = sala; }

    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }

    public String getDestinatario() { return destinatario; }
    public void setDestinatario(String destinatario) { this.destinatario = destinatario; }

    public byte[] getDatos() { return datos; }
    public void setDatos(byte[] datos) { this.datos = datos; }

    // Convierte el mensaje a bytes para enviar por UDP
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(this);
        oos.flush();
        return bos.toByteArray();
    }

    // Convierte bytes recibidos a Mensaje
    public static Mensaje fromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bis);
        return (Mensaje) ois.readObject();
    }
}

