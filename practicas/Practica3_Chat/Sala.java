import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Sala {
    private String nombre;
    private Map<String, UsuarioInfo> usuarios; // usuario -> info

    public Sala(String nombre) {
        this.nombre = nombre;
        this.usuarios = new ConcurrentHashMap<>();
    }

    public String getNombre() {
        return nombre;
    }

    // Agregar usuario a la sala
    public void agregarUsuario(String usuario, InetAddress ip, int puerto) {
        usuarios.put(usuario, new UsuarioInfo(ip, puerto));
        System.out.println("Usuario " + usuario + " unido a sala " + nombre);
    }

    // Remover usuario de la sala
    public void removerUsuario(String usuario) {
        usuarios.remove(usuario);
        System.out.println("Usuario " + usuario + " salió de sala " + nombre);
    }

    // Obtener todos los usuarios
    public Set<String> getUsuarios() {
        return usuarios.keySet();
    }

    // Obtener información de un usuario específico
    public UsuarioInfo getUsuarioInfo(String usuario) {
        return usuarios.get(usuario);
    }

    // Obtener todas las direcciones de los usuarios (para broadcast)
    public List<UsuarioInfo> getAllUsuarios() {
        return new ArrayList<>(usuarios.values());
    }

    public boolean estaVacia() {
        return usuarios.isEmpty();
    }

    // Clase interna para almacenar información del usuario
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

