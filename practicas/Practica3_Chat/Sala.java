import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Sala {
    private String nombre;
    private Set<String> usuarios; // Solo nombres de usuarios
    private String direccionMulticast;
    private int puertoMulticast;

    public Sala(String nombre, String direccionMulticast, int puertoMulticast) {
        this.nombre = nombre;
        this.usuarios = ConcurrentHashMap.newKeySet();
        this.direccionMulticast = direccionMulticast;
        this.puertoMulticast = puertoMulticast;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDireccionMulticast() {
        return direccionMulticast;
    }

    public int getPuertoMulticast() {
        return puertoMulticast;
    }

    public void agregarUsuario(String usuario) {
        usuarios.add(usuario);
        System.out.println("Usuario " + usuario + " unido a sala " + nombre);
    }

    public void removerUsuario(String usuario) {
        usuarios.remove(usuario);
        System.out.println("Usuario " + usuario + " salió de sala " + nombre);
    }

    public Set<String> getUsuarios() {
        return new HashSet<>(usuarios);
    }

    public boolean estaVacia() {
        return usuarios.isEmpty();
    }

    public boolean contieneUsuario(String usuario) {
        return usuarios.contains(usuario);
    }
}

