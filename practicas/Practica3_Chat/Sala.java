import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Sala {
    private String nombre;
    private String direccionMulticast; // NUEVO: cada sala tiene su IP multicast
    private Map<String, UsuarioInfo> usuarios;

    public Sala(String nombre, String direccionMulticast) {
        this.nombre = nombre;
        this.direccionMulticast = direccionMulticast;
        this.usuarios = new ConcurrentHashMap<>();
    }

    public String getNombre() {
        return nombre;
    }

    public String getDireccionMulticast() {
        return direccionMulticast;
    }

    public void agregarUsuario(String usuario, InetAddress ip, int puerto) {
        usuarios.put(usuario, new UsuarioInfo(ip, puerto));
        System.out.println("Usuario " + usuario + " unido a sala " + nombre);
    }

    public void removerUsuario(String usuario) {
        usuarios.remove(usuario);
        System.out.println("Usuario " + usuario + " salió de sala " + nombre);
    }

    public Set<String> getUsuarios() {
        return usuarios.keySet();
    }

    public UsuarioInfo getUsuarioInfo(String usuario) {
        return usuarios.get(usuario);
    }

    public List<UsuarioInfo> getAllUsuarios() {
        return new ArrayList<>(usuarios.values());
    }

    public boolean estaVacia() {
        return usuarios.isEmpty();
    }

    public static class UsuarioInfo {
        private InetAddress ip;
        private int puerto;

        public UsuarioInfo(InetAddress ip, int puerto) {
            this.ip = ip;
            this.puerto = puerto;
        }

        public InetAddress getIp() { return ip; }
        public int getPuerto() { return puerto; }
    }
}

