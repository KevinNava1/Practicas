import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.json.*;

/**
 * Servidor de venta de artículos en línea
 * - (socket bloqueante)
 * - Inventario guardado en articulos.json
 * - Permite buscar, listar por tipo, agregar al carrito y generar ticket
 */
public class Servidor {
    private static final int PUERTO = 8000;
    private static JSONArray articulos = new JSONArray();

    public static void main(String[] args) {
        cargarArticulos(); // cargar inventario desde JSON

        try (ServerSocket server = new ServerSocket(PUERTO)) {
            System.out.println("Servidor iniciado en puerto " + PUERTO);

            while (true) {
                System.out.println("Esperando cliente...");
                try (Socket cliente = server.accept();
                     BufferedReader in = new BufferedReader(new InputStreamReader(cliente.getInputStream()));
                     PrintWriter out = new PrintWriter(cliente.getOutputStream(), true)) {

                    System.out.println("Cliente conectado desde " + cliente.getInetAddress());

                    String comando;
                    while ((comando = in.readLine()) != null) {
                        if (comando.equalsIgnoreCase("salir")) break;

                        String respuesta = procesarComando(comando);
                        out.println(respuesta);
                    }

                    System.out.println("Cliente desconectado.\n");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Cargar inventario desde articulos.json */
    private static void cargarArticulos() {
        try {
            String jsonStr = new String(Files.readAllBytes(Paths.get("articulos.json")));
            articulos = new JSONArray(jsonStr);
            System.out.println("Artículos cargados: " + articulos.length());
        } catch (Exception e) {
            System.out.println("Error al cargar articulos.json: " + e.getMessage());
        }
    }

    /** Procesar comando enviado por el cliente */
    private static String procesarComando(String comando) {
        String[] partes = comando.split(" ", 2);
        String accion = partes[0].toLowerCase();
        String arg = partes.length > 1 ? partes[1] : "";

        switch (accion) {
            case "buscar":
                return buscarArticulo(arg).toString();
            case "listar":
                return listarPorTipo(arg).toString();
            case "listar_tipos":
                return listarTipos().toString();
            case "agregar": // agregar al carrito: nombre|cantidad
                String[] datos = arg.split("\\|");
                String nombre = datos[0];
                int cantidad = Integer.parseInt(datos[1]);
                return agregarAlCarrito(nombre, cantidad);
            case "comprar":
                JSONArray carrito = new JSONArray(arg);
                return procesarCompra(carrito);
            default:
                JSONObject msg = new JSONObject();
                msg.put("error", "Comando no reconocido");
                return msg.toString();
        }
    }

    /** Buscar artículos por nombre o marca, solo con stock > 0 */
    private static JSONArray buscarArticulo(String nombre) {
        JSONArray res = new JSONArray();
        for (int i = 0; i < articulos.length(); i++) {
            JSONObject art = articulos.getJSONObject(i);
            if ((art.getString("nombre").toLowerCase().contains(nombre.toLowerCase()) ||
                 art.getString("marca").toLowerCase().contains(nombre.toLowerCase()))
                 && art.getInt("existencias") > 0) {
                res.put(art);
            }
        }
        return res;
    }

    /** Listar artículos por tipo, solo con stock > 0 */
    private static JSONArray listarPorTipo(String tipo) {
        JSONArray res = new JSONArray();
        for (int i = 0; i < articulos.length(); i++) {
            JSONObject art = articulos.getJSONObject(i);
            if (art.getString("tipo").equalsIgnoreCase(tipo) && art.getInt("existencias") > 0) {
                res.put(art);
            }
        }
        return res;
    }

    /** Listar tipos disponibles */
    private static JSONArray listarTipos() {
        Set<String> tiposSet = new HashSet<>();
        for (int i = 0; i < articulos.length(); i++) {
            JSONObject art = articulos.getJSONObject(i);
            if (art.getInt("existencias") > 0) tiposSet.add(art.getString("tipo"));
        }
        JSONArray res = new JSONArray();
        for (String t : tiposSet) res.put(t);
        return res;
    }

    /** Agregar artículos al carrito y actualizar stock inmediatamente */
    private static String agregarAlCarrito(String nombre, int cantidad) {
        JSONObject respuesta = new JSONObject();
        for (int i = 0; i < articulos.length(); i++) {
            JSONObject art = articulos.getJSONObject(i);
            if (art.getString("nombre").equalsIgnoreCase(nombre)) {
                int stock = art.getInt("existencias");
                if (stock >= cantidad) {
                    art.put("existencias", stock - cantidad); // ✅ stock actualizado
                    respuesta.put("success", true);
                    respuesta.put("mensaje", cantidad + " " + nombre + " agregado(s) al carrito");
                } else if (stock > 0) {
                    respuesta.put("success", false);
                    respuesta.put("mensaje", "Solo quedan " + stock + " unidades disponibles");
                } else {
                    respuesta.put("success", false);
                    respuesta.put("mensaje", "Artículo sin stock");
                }
                return respuesta.toString();
            }
        }
        respuesta.put("success", false);
        respuesta.put("mensaje", "Artículo no encontrado");
        return respuesta.toString();
    }

    /** Generar ticket final y total de la compra */
    private static String procesarCompra(JSONArray carrito) {
        JSONArray ticket = new JSONArray();
        double total = 0.0;

        for (int i = 0; i < carrito.length(); i++) {
            JSONObject item = carrito.getJSONObject(i);
            String nombre = item.getString("nombre");
            int cantidad = item.getInt("cantidad");

            for (int j = 0; j < articulos.length(); j++) {
                JSONObject art = articulos.getJSONObject(j);
                if (art.getString("nombre").equalsIgnoreCase(nombre)) {
                    double precio = art.getDouble("precio");
                    JSONObject t = new JSONObject();
                    t.put("nombre", nombre);
                    t.put("marca", art.getString("marca"));
                    t.put("cantidad", cantidad);
                    t.put("precio_unitario", precio);
                    t.put("subtotal", precio * cantidad);
                    total += precio * cantidad;
                    ticket.put(t);
                    break;
                }
            }
        }

        JSONObject resumen = new JSONObject();
        resumen.put("total", total);
        resumen.put("detalles", ticket);

        // Guardar inventario actualizado
        try (FileWriter fw = new FileWriter("articulos.json")) {
            fw.write(articulos.toString(4));
        } catch (Exception e) { e.printStackTrace(); }

        return resumen.toString();
    }
}
