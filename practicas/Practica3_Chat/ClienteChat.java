import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.util.*;

public class ClienteChat extends JFrame {
    private static final String SERVIDOR_IP = "localhost";
    private static final int SERVIDOR_PUERTO = 9876;
    private static final int CLIENTE_PUERTO = 5000; // Base, se incrementará

    private DatagramSocket socket;
    private String nombreUsuario;
    private String salaActual;

    // Componentes GUI
    private JTextArea areaChat;
    private JTextField campoMensaje;
    private JButton btnEnviar;
    private JButton btnCrearSala;
    private JButton btnUnirse;
    private JButton btnSalir;
    private JButton btnListarUsuarios;
    private JList<String> listaUsuarios;
    private DefaultListModel<String> modeloUsuarios;
    private JComboBox<String> comboStickers;
    private JLabel labelSalaActual;

    public ClienteChat() {
        // Solicitar nombre de usuario
        nombreUsuario = JOptionPane.showInputDialog(this, "Ingresa tu nombre de usuario:");
        if (nombreUsuario == null || nombreUsuario.trim().isEmpty()) {
            System.exit(0);
        }

        configurarVentana();
        configurarSocket();
        iniciarHiloReceptor();
    }

    private void configurarVentana() {
        setTitle("💬 Chat - Usuario: " + nombreUsuario);
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Panel superior - Controles de sala
        JPanel panelSuperior = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panelSuperior.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        btnCrearSala = new JButton("➕ Crear Sala");
        btnUnirse = new JButton("🚪 Unirse a Sala");
        btnSalir = new JButton("🚫 Salir de Sala");
        btnListarUsuarios = new JButton("👥 Listar Usuarios");
        labelSalaActual = new JLabel("Sin sala");
        labelSalaActual.setFont(new Font("Arial", Font.BOLD, 14));

        panelSuperior.add(btnCrearSala);
        panelSuperior.add(btnUnirse);
        panelSuperior.add(btnSalir);
        panelSuperior.add(btnListarUsuarios);
        panelSuperior.add(new JLabel(" | Sala actual: "));
        panelSuperior.add(labelSalaActual);

        // Panel central - Chat
        areaChat = new JTextArea();
        areaChat.setEditable(false);
        areaChat.setLineWrap(true);
        areaChat.setWrapStyleWord(true);
        areaChat.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollChat = new JScrollPane(areaChat);

        // Panel derecho - Lista de usuarios
        modeloUsuarios = new DefaultListModel<>();
        listaUsuarios = new JList<>(modeloUsuarios);
        listaUsuarios.setBorder(BorderFactory.createTitledBorder("Usuarios en sala"));
        JScrollPane scrollUsuarios = new JScrollPane(listaUsuarios);
        scrollUsuarios.setPreferredSize(new Dimension(200, 0));

        // Panel inferior - Envío de mensajes
        JPanel panelInferior = new JPanel(new BorderLayout(5, 5));
        panelInferior.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        campoMensaje = new JTextField();
        campoMensaje.setFont(new Font("Arial", Font.PLAIN, 13));
        btnEnviar = new JButton("📤 Enviar");

        // Panel de extras (stickers, etc)
        JPanel panelExtras = new JPanel(new FlowLayout(FlowLayout.LEFT));
        String[] stickers = {"😀", "😂", "❤️", "👍", "🎉", "🔥", "💯", "✨"};
        comboStickers = new JComboBox<>(stickers);
        JButton btnSticker = new JButton("Enviar Sticker");
        JButton btnPrivado = new JButton("💌 Mensaje Privado");

        panelExtras.add(new JLabel("Stickers:"));
        panelExtras.add(comboStickers);
        panelExtras.add(btnSticker);
        panelExtras.add(btnPrivado);

        panelInferior.add(campoMensaje, BorderLayout.CENTER);
        panelInferior.add(btnEnviar, BorderLayout.EAST);
        panelInferior.add(panelExtras, BorderLayout.NORTH);

        // Agregar componentes
        add(panelSuperior, BorderLayout.NORTH);
        add(scrollChat, BorderLayout.CENTER);
        add(scrollUsuarios, BorderLayout.EAST);
        add(panelInferior, BorderLayout.SOUTH);

        // Eventos
        btnCrearSala.addActionListener(e -> crearSala());
        btnUnirse.addActionListener(e -> unirseASala());
        btnSalir.addActionListener(e -> salirDeSala());
        btnListarUsuarios.addActionListener(e -> listarUsuarios());
        btnEnviar.addActionListener(e -> enviarMensaje());
        btnSticker.addActionListener(e -> enviarSticker());
        btnPrivado.addActionListener(e -> enviarMensajePrivado());

        campoMensaje.addActionListener(e -> enviarMensaje());

        setVisible(true);
    }

    private void configurarSocket() {
        try {
            // Intentar crear socket en un puerto disponible
            int puerto = CLIENTE_PUERTO + new Random().nextInt(1000);
            socket = new DatagramSocket(puerto);
            mostrarMensaje("✅ Cliente iniciado en puerto " + puerto);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error al iniciar cliente: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void iniciarHiloReceptor() {
        Thread hiloReceptor = new Thread(() -> {
            try {
                while (!socket.isClosed()) {
                    byte[] buffer = new byte[65535];
                    DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                    socket.receive(paquete);

                    byte[] datos = Arrays.copyOf(paquete.getData(), paquete.getLength());
                    Mensaje mensaje = Mensaje.fromBytes(datos);

                    // Procesar mensaje recibido
                    SwingUtilities.invokeLater(() -> procesarMensajeRecibido(mensaje));
                }
            } catch (Exception e) {
                if (!socket.isClosed()) {
                    System.err.println("Error en hilo receptor: " + e.getMessage());
                }
            }
        });
        hiloReceptor.setDaemon(true);
        hiloReceptor.start();
    }

    private void procesarMensajeRecibido(Mensaje mensaje) {
        String contenido = mensaje.getContenido();

        // Si es actualización de usuarios
        if (contenido.startsWith("USUARIOS:")) {
            String listaStr = contenido.substring(9);
            actualizarListaUsuarios(listaStr);
        } else {
            mostrarMensaje(contenido);
        }
    }

    private void actualizarListaUsuarios(String lista) {
        modeloUsuarios.clear();
        if (!lista.isEmpty()) {
            String[] usuarios = lista.split(",");
            for (String usuario : usuarios) {
                if (!usuario.trim().isEmpty()) {
                    modeloUsuarios.addElement(usuario.trim());
                }
            }
        }
    }

    private void crearSala() {
        String nombreSala = JOptionPane.showInputDialog(this, "Nombre de la sala:");
        if (nombreSala != null && !nombreSala.trim().isEmpty()) {
            Mensaje msg = new Mensaje(Mensaje.CREAR_SALA, nombreUsuario, nombreSala, "");
            enviarMensaje(msg);
        }
    }

    private void unirseASala() {
        String nombreSala = JOptionPane.showInputDialog(this, "Nombre de la sala:");
        if (nombreSala != null && !nombreSala.trim().isEmpty()) {
            Mensaje msg = new Mensaje(Mensaje.UNIRSE_SALA, nombreUsuario, nombreSala, "");
            enviarMensaje(msg);
            salaActual = nombreSala;
            labelSalaActual.setText(nombreSala);
            labelSalaActual.setForeground(Color.BLUE);
        }
    }

    private void salirDeSala() {
        if (salaActual != null) {
            Mensaje msg = new Mensaje(Mensaje.SALIR_SALA, nombreUsuario, salaActual, "");
            enviarMensaje(msg);
            salaActual = null;
            labelSalaActual.setText("Sin sala");
            labelSalaActual.setForeground(Color.BLACK);
            modeloUsuarios.clear();
        } else {
            JOptionPane.showMessageDialog(this, "No estás en ninguna sala");
        }
    }

    private void listarUsuarios() {
        if (salaActual != null) {
            Mensaje msg = new Mensaje(Mensaje.LISTAR_USUARIOS, nombreUsuario, salaActual, "");
            enviarMensaje(msg);
        } else {
            JOptionPane.showMessageDialog(this, "Debes estar en una sala");
        }
    }

    private void enviarMensaje() {
        String texto = campoMensaje.getText().trim();
        if (!texto.isEmpty() && salaActual != null) {
            Mensaje msg = new Mensaje(Mensaje.MENSAJE_SALA, nombreUsuario, salaActual, texto);
            enviarMensaje(msg);
            campoMensaje.setText("");
        }
        else if(salaActual == null){
            JOptionPane.showMessageDialog(this, "Debes estar en una sala");
        }
        else if(texto.isEmpty()){
            JOptionPane.showMessageDialog(this, "No puedes enviar un mensaje vacío");
        }
    }

    private void enviarSticker() {
        if (salaActual != null) {
            String sticker = (String) comboStickers.getSelectedItem();
            Mensaje msg = new Mensaje(Mensaje.ENVIAR_STICKER, nombreUsuario, salaActual, sticker);
            enviarMensaje(msg);
        } else {
            JOptionPane.showMessageDialog(this, "Debes estar en una sala");
        }
    }

    private void enviarMensajePrivado() {
        if (salaActual != null && modeloUsuarios.getSize() > 0) {
            String destinatario = (String) JOptionPane.showInputDialog(
                this,
                "Selecciona el usuario:",
                "Mensaje Privado",
                JOptionPane.QUESTION_MESSAGE,
                null,
                modeloUsuarios.toArray(),
                modeloUsuarios.getElementAt(0)
            );

            if (destinatario != null) {
                String texto = JOptionPane.showInputDialog(this, "Mensaje privado para " + destinatario + ":");
                if (texto != null && !texto.trim().isEmpty()) {
                    Mensaje msg = new Mensaje(Mensaje.MENSAJE_PRIVADO, nombreUsuario,
                                              salaActual, texto);
                    msg.setDestinatario(destinatario);
                    enviarMensaje(msg);
                    mostrarMensaje("[PRIVADO a " + destinatario + "] " + texto);
                }
            }
        } else {
            JOptionPane.showMessageDialog(this, "Debes estar en una sala con otros usuarios");
        }
    }

    private void enviarMensaje(Mensaje mensaje) {
        try {
            byte[] datos = mensaje.toBytes();
            InetAddress direccion = InetAddress.getByName(SERVIDOR_IP);
            DatagramPacket paquete = new DatagramPacket(datos, datos.length,
                                                       direccion, SERVIDOR_PUERTO);
            socket.send(paquete);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error enviando mensaje: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void mostrarMensaje(String mensaje) {
        areaChat.append(mensaje + "\n");
        areaChat.setCaretPosition(areaChat.getDocument().getLength());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClienteChat());
    }
}

