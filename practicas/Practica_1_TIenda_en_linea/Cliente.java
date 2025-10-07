import java.net.*;
import java.io.*;
import java.util.*;
import org.json.*;

/**
 * Cliente de venta de artículos
 * - Menú en línea de comandos
 * - Permite buscar, listar por tipo, agregar varios artículos al carrito
 * - Genera ticket con subtotal y total
 */
public class Cliente {
    private static final String HOST = "127.0.0.1";
    private static final int PUERTO = 8000;

    public static void main(String[] args) {
        List<JSONObject> carrito = new ArrayList<>();
        try (Socket socket = new Socket(HOST, PUERTO);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner sc = new Scanner(System.in)) {

            int opcion = 0;
            while (opcion != 5) {
                System.out.println("\n=== MENÚ DE COMPRA ===");
                System.out.println("1. Buscar artículo por nombre o marca");
                System.out.println("2. Listar artículos por tipo");
                System.out.println("3. Agregar artículo al carrito");
                System.out.println("4. Ver/Editar carrito");
                System.out.println("5. Finalizar compra y generar ticket");
                System.out.print("Seleccione una opción: ");
                opcion = sc.nextInt(); sc.nextLine();

                switch (opcion) {
                    case 1:
                        System.out.print("Ingrese nombre o marca: ");
                        String busqueda = sc.nextLine();
                        out.println("buscar " + busqueda);
                        imprimirArticulos(in.readLine());
                        break;

                    case 2:
                        out.println("listar_tipos");
                        JSONArray tiposArr = new JSONArray(in.readLine());
                        System.out.println("Tipos disponibles:");
                        for (int i = 0; i < tiposArr.length(); i++) System.out.println("- " + tiposArr.getString(i));

                        System.out.print("Ingrese tipo de artículo a listar: ");
                        String tipo = sc.nextLine();
                        out.println("listar " + tipo);
                        imprimirArticulos(in.readLine());
                        break;

                    case 3:
                        System.out.print("Ingrese el artículo a agregar: ");
                        String art = sc.nextLine();
                        System.out.print("Ingrese la cantidad: ");
                        int cantidad = sc.nextInt(); sc.nextLine();

                        out.println("agregar " + art + "|" + cantidad);
                        JSONObject respObj = new JSONObject(in.readLine());
                        System.out.println(respObj.getString("mensaje"));
                        if (respObj.optBoolean("success", false)) {
                            carrito.add(new JSONObject().put("nombre", art).put("cantidad", cantidad));
                        }
                        break;

                    case 4:
                        if(carrito.isEmpty()) {
                            System.out.println("Carrito vacío.");
                        } else {
                            System.out.println("Carrito actual:");
                            for (JSONObject item : carrito) {
                                System.out.println("- " + item.getString("nombre") + " x" + item.getInt("cantidad"));
                            }
                            System.out.print("Desea eliminar algún artículo? (si/no): ");
                            String resp = sc.nextLine();
                            if(resp.equalsIgnoreCase("si")) {
                                System.out.print("Ingrese nombre del artículo a eliminar: ");
                                String elim = sc.nextLine();
                                carrito.removeIf(i -> i.getString("nombre").equalsIgnoreCase(elim));
                            }
                        }
                        break;

                    case 5:
                        if(carrito.isEmpty()){
                            System.out.println("El carrito está vacío.");
                            break;
                        }
                        JSONArray jsonCarrito = new JSONArray(carrito);
                        out.println("comprar " + jsonCarrito.toString());

                        JSONObject ticketObj = new JSONObject(in.readLine());
                        JSONArray detalles = ticketObj.getJSONArray("detalles");
                        double total = ticketObj.getDouble("total");

                        System.out.println("\n=== Ticket de compra ===");
                        for (int i = 0; i < detalles.length(); i++) {
                            JSONObject item = detalles.getJSONObject(i);
                            System.out.println("- " + item.getString("nombre") +
                                    " | Marca: " + item.getString("marca") +
                                    " | Cantidad: " + item.getInt("cantidad") +
                                    " | Precio unitario: $" + item.getDouble("precio_unitario") +
                                    " | Subtotal: $" + item.getDouble("subtotal"));
                        }
                        System.out.println("TOTAL: $" + total);
                        carrito.clear();
                        out.println("salir");
                        break;

                    default:
                        System.out.println("Opción no válida.");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Mostrar lista de artículos recibida del servidor */
    private static void imprimirArticulos(String jsonStr) {
        JSONArray arr = new JSONArray(jsonStr);
        if (arr.length() == 0) {
            System.out.println("No se encontraron artículos.");
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject art = arr.getJSONObject(i);
            System.out.println("- " + art.getString("nombre") +
                    " | Marca: " + art.getString("marca") +
                    " | Tipo: " + art.getString("tipo") +
                    " | Precio: $" + art.getDouble("precio") +
                    " | Stock: " + art.getInt("existencias"));
        }
    }
}
