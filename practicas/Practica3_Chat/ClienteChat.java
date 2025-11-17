import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClienteChat extends JFrame {
    private static final String SERVIDOR_IP = "localhost";
    private static final int SERVIDOR_PUERTO = 9876;
    private static final int PUERTO_MULTICAST = 6789;
    private static final int CLIENTE_PUERTO = 5000;

    private DatagramSocket socketCliente; // Para comunicación con servidor
    private String nombreUsuario;

    // NUEVO: Manejo de múltiples salas
    private Map<String, SalaInfo> salasActivas; // nombre -> info de la sala
    private String salaSeleccionada; // Sala que se está viendo actualmente
    private ManejadorAudio manejadorAudio;
    private boolean grabandoAudio = false;

    // Componentes GUI
    private JTextArea areaChat;
    private JTextField campoMensaje;
    private JButton btnEnviar;
    private JButton btnCrearSala;
    private JButton btnUnirse;
    private JButton btnSalir;
    private JButton btnListarUsuarios;
    private DefaultListModel<String> modeloSalas; // NUEVO: Lista de salas
    private JList<String> listaSalas; // NUEVO: JList para salas
    private JList<String> listaUsuarios;
    private DefaultListModel<String> modeloUsuarios;
    private JComboBox<String> comboStickers;
    private JLabel labelSalaActual;
    private JButton btnGrabarAudio;

    public ClienteChat() {
        nombreUsuario = JOptionPane.showInputDialog(this, "Ingresa tu nombre de usuario:");
        if (nombreUsuario == null || nombreUsuario.trim().isEmpty()) {
            System.exit(0);
        }

        salasActivas = new ConcurrentHashMap<>();
        manejadorAudio = new ManejadorAudio();
        configurarVentana();
        configurarSocket();
        iniciarHiloReceptorServidor();
    }

    private void configurarVentana() {
        setTitle("💬 Chat - Usuario: " + nombreUsuario);
        setSize(1100, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // ========== PANEL IZQUIERDO: LISTA DE SALAS ==========
        JPanel panelIzquierdo = new JPanel(new BorderLayout());
        panelIzquierdo.setBorder(BorderFactory.createTitledBorder("🏠 Mis Salas"));
        panelIzquierdo.setPreferredSize(new Dimension(200, 0));

        modeloSalas = new DefaultListModel<>();
        listaSalas = new JList<>(modeloSalas);
        listaSalas.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listaSalas.setFont(new Font("Arial", Font.PLAIN, 13));

        // Listener para cambiar de sala al hacer clic
        listaSalas.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                cambiarSalaActiva();
            }
        });

        JScrollPane scrollSalas = new JScrollPane(listaSalas);
        panelIzquierdo.add(scrollSalas, BorderLayout.CENTER);

        // ========== PANEL SUPERIOR: CONTROLES ==========
        JPanel panelSuperior = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panelSuperior.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        btnCrearSala = new JButton("➕ Crear Sala");
        btnUnirse = new JButton("🚪 Unirse");
        btnSalir = new JButton("🚫 Salir de Sala");
        btnListarUsuarios = new JButton("👥 Usuarios");
        labelSalaActual = new JLabel("Sin sala seleccionada");
        labelSalaActual.setFont(new Font("Arial", Font.BOLD, 14));

        panelSuperior.add(btnCrearSala);
        panelSuperior.add(btnUnirse);
        panelSuperior.add(btnSalir);
        panelSuperior.add(btnListarUsuarios);
        panelSuperior.add(new JLabel(" | Sala: "));
        panelSuperior.add(labelSalaActual);

        // ========== PANEL CENTRAL: CHAT ==========
        areaChat = new JTextArea();
        areaChat.setEditable(false);
        areaChat.setLineWrap(true);
        areaChat.setWrapStyleWord(true);
        areaChat.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollChat = new JScrollPane(areaChat);

        // ========== PANEL DERECHO: USUARIOS ==========
        modeloUsuarios = new DefaultListModel<>();
        listaUsuarios = new JList<>(modeloUsuarios);
        listaUsuarios.setBorder(BorderFactory.createTitledBorder("👥 Usuarios"));
        JScrollPane scrollUsuarios = new JScrollPane(listaUsuarios);
        scrollUsuarios.setPreferredSize(new Dimension(180, 0));

        // ========== PANEL INFERIOR: ENVÍO ==========
        JPanel panelInferior = new JPanel(new BorderLayout(5, 5));
        panelInferior.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        campoMensaje = new JTextField();
        campoMensaje.setFont(new Font("Arial", Font.PLAIN, 13));
        btnEnviar = new JButton("📤 Enviar");

        JPanel panelExtras = new JPanel(new FlowLayout(FlowLayout.LEFT));
        String[] stickers = {"😀", "😂", "❤️", "👍", "🎉", "🔥", "💯", "✨"};
        comboStickers = new JComboBox<>(stickers);
        JButton btnSticker = new JButton("Sticker");
        JButton btnPrivado = new JButton("💌 Privado");
        btnGrabarAudio = new JButton("🎤 Grabar Audio");
        btnGrabarAudio.setBackground(Color.RED);
        btnGrabarAudio.setForeground(Color.WHITE);

        panelExtras.add(new JLabel("Stickers:"));
        panelExtras.add(comboStickers);
        panelExtras.add(btnSticker);
        panelExtras.add(btnPrivado);
        panelExtras.add(btnGrabarAudio);

        panelInferior.add(campoMensaje, BorderLayout.CENTER);
        panelInferior.add(btnEnviar, BorderLayout.EAST);
        panelInferior.add(panelExtras, BorderLayout.NORTH);

        // ========== AGREGAR COMPONENTES ==========
        add(panelIzquierdo, BorderLayout.WEST); // NUEVO: Panel de salas
        add(panelSuperior, BorderLayout.NORTH);
        add(scrollChat, BorderLayout.CENTER);
        add(scrollUsuarios, BorderLayout.EAST);
        add(panelInferior, BorderLayout.SOUTH);

        // ========== EVENTOS ==========
        btnCrearSala.addActionListener(e -> crearSala());
        btnUnirse.addActionListener(e -> unirseASala());
        btnSalir.addActionListener(e -> salirDeSala());
        btnListarUsuarios.addActionListener(e -> listarUsuarios());
        btnEnviar.addActionListener(e -> enviarMensaje());
        btnSticker.addActionListener(e -> enviarSticker());
        btnPrivado.addActionListener(e -> enviarMensajePrivado());
        campoMensaje.addActionListener(e -> enviarMensaje());
        btnGrabarAudio.addActionListener(e -> manejarGrabacionAudio());

        setVisible(true);
    }

    private void configurarSocket() {
        try {
            int puerto = CLIENTE_PUERTO + new Random().nextInt(1000);
            socketCliente = new DatagramSocket(puerto);
            mostrarMensaje("✅ Cliente iniciado en puerto " + puerto);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error al iniciar cliente: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    // Hilo para recibir respuestas del servidor (comunicación directa)
    private void iniciarHiloReceptorServidor() {
        Thread hilo = new Thread(() -> {
            try {
                while (!socketCliente.isClosed()) {
                    byte[] buffer = new byte[65535];
                    DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                    socketCliente.receive(paquete);

                    byte[] datos = Arrays.copyOf(paquete.getData(), paquete.getLength());
                    Mensaje mensaje = Mensaje.fromBytes(datos);

                    SwingUtilities.invokeLater(() -> procesarRespuestaServidor(mensaje));
                }
            } catch (Exception e) {
                if (!socketCliente.isClosed()) {
                    System.err.println("Error en receptor: " + e.getMessage());
                }
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    private void procesarRespuestaServidor(Mensaje mensaje) {
        String contenido = mensaje.getContenido();

        // Si incluye dirección multicast, es respuesta de crear/unirse a sala
        if (mensaje.getDireccionMulticast() != null) {
            String sala = mensaje.getSala();
            if (sala == null || sala.isEmpty()) {
                // Es respuesta de CREAR_SALA, extraer nombre
                if (contenido.contains("'")) {
                    int inicio = contenido.indexOf("'") + 1;
                    int fin = contenido.lastIndexOf("'");
                    sala = contenido.substring(inicio, fin);
                }
            }

            if (sala != null && !sala.isEmpty()) {
                unirseAGrupoMulticast(sala, mensaje.getDireccionMulticast());
            }
        }

        mostrarMensaje(contenido);
    }

    // NUEVO: Unirse a un grupo multicast para escuchar mensajes de la sala
    private void unirseAGrupoMulticast(String nombreSala, String direccionMulticast) {
        if (salasActivas.containsKey(nombreSala)) {
            mostrarMensaje("⚠️ Ya estás en la sala " + nombreSala);
            return;
        }

        try {
            MulticastSocket socketMulticast = new MulticastSocket(PUERTO_MULTICAST);
            InetAddress grupo = InetAddress.getByName(direccionMulticast);

            // Unirse al grupo multicast
            socketMulticast.joinGroup(grupo);

            // Crear info de la sala
            SalaInfo salaInfo = new SalaInfo(nombreSala, direccionMulticast,
                                            socketMulticast, grupo);
            salasActivas.put(nombreSala, salaInfo);

            // Agregar a la lista visual
            modeloSalas.addElement(nombreSala);

            // Iniciar hilo receptor para esta sala
            iniciarReceptorMulticast(salaInfo);

            // Seleccionar esta sala automáticamente
            listaSalas.setSelectedValue(nombreSala, true);

            mostrarMensaje("🎉 Conectado a grupo multicast de " + nombreSala);

            Mensaje confirmacion = new Mensaje(Mensaje.CONFIRMAR_UNION, nombreUsuario, nombreSala, "");
            enviarAlServidor(confirmacion);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error al unirse al grupo multicast: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // NUEVO: Hilo para escuchar mensajes multicast de una sala específica
    private void iniciarReceptorMulticast(SalaInfo salaInfo) {
        Thread hilo = new Thread(() -> {
            try {
                while (!salaInfo.socket.isClosed()) {
                    byte[] buffer = new byte[65535];
                    DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                    salaInfo.socket.receive(paquete);

                    byte[] datos = Arrays.copyOf(paquete.getData(), paquete.getLength());
                    Mensaje mensaje = Mensaje.fromBytes(datos);

                    SwingUtilities.invokeLater(() ->
                        procesarMensajeMulticast(mensaje, salaInfo.nombre));
                }
            } catch (Exception e) {
                if (!salaInfo.socket.isClosed()) {
                    System.err.println("Error en receptor multicast: " + e.getMessage());
                }
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    private void procesarMensajeMulticast(Mensaje mensaje, String nombreSala) {
        String contenido = mensaje.getContenido();

        // Actualizar lista de usuarios si es necesario
        if (contenido.startsWith("USUARIOS:")) {
            if (nombreSala.equals(salaSeleccionada)) {
                String listaStr = contenido.substring(9);
                actualizarListaUsuarios(listaStr);
            }
        } else {
            // Guardar mensaje en el historial de la sala
            SalaInfo sala = salasActivas.get(nombreSala);
            if (sala != null) {
                sala.agregarMensaje("[" + nombreSala + "] " + contenido);

                // Si esta es la sala activa, mostrar el mensaje
                if (nombreSala.equals(salaSeleccionada)) {
                    mostrarMensajeSala(contenido);
                }
            }
        }
    }

    // NUEVO: Cambiar la sala activa cuando se selecciona en la lista
    private void cambiarSalaActiva() {
        String seleccion = listaSalas.getSelectedValue();
        if (seleccion != null && !seleccion.equals(salaSeleccionada)) {
            salaSeleccionada = seleccion;
            labelSalaActual.setText(salaSeleccionada);
            labelSalaActual.setForeground(Color.BLUE);

            // Limpiar y mostrar historial de la sala
            areaChat.setText("");
            SalaInfo sala = salasActivas.get(salaSeleccionada);
            if (sala != null) {
                for (String msg : sala.historialMensajes) {
                    mostrarMensajeSala(msg);
                }
            }
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
            enviarAlServidor(msg);
        }
    }

    private void unirseASala() {
        String nombreSala = JOptionPane.showInputDialog(this, "Nombre de la sala:");
        if (nombreSala != null && !nombreSala.trim().isEmpty()) {
            Mensaje msg = new Mensaje(Mensaje.UNIRSE_SALA, nombreUsuario, nombreSala, "");
            enviarAlServidor(msg);
        }
    }

    private void salirDeSala() {
        if (salaSeleccionada != null) {
            SalaInfo sala = salasActivas.get(salaSeleccionada);
            if (sala != null) {
                try {
                    // Salir del grupo multicast
                    sala.socket.leaveGroup(sala.grupo);
                    sala.socket.close();

                    // Notificar al servidor
                    Mensaje msg = new Mensaje(Mensaje.SALIR_SALA, nombreUsuario,
                                             salaSeleccionada, "");
                    enviarAlServidor(msg);

                    // Remover de la lista
                    salasActivas.remove(salaSeleccionada);
                    modeloSalas.removeElement(salaSeleccionada);

                    // Limpiar selección
                    salaSeleccionada = null;
                    labelSalaActual.setText("Sin sala");
                    labelSalaActual.setForeground(Color.BLACK);
                    areaChat.setText("");
                    modeloUsuarios.clear();

                } catch (Exception e) {
                    mostrarMensaje("Error al salir: " + e.getMessage());
                }
            }
        } else {
            JOptionPane.showMessageDialog(this, "Selecciona una sala primero");
        }
    }

    private void listarUsuarios() {
        if (salaSeleccionada != null) {
            Mensaje msg = new Mensaje(Mensaje.LISTAR_USUARIOS, nombreUsuario,
                                     salaSeleccionada, "");
            enviarAlServidor(msg);
        }
    }

    private void enviarMensaje() {
        String texto = campoMensaje.getText().trim();
        if (!texto.isEmpty() && salaSeleccionada != null) {
            Mensaje msg = new Mensaje(Mensaje.MENSAJE_SALA, nombreUsuario,
                                     salaSeleccionada, texto);
            enviarAlServidor(msg);
            campoMensaje.setText("");
        } else if (salaSeleccionada == null) {
            JOptionPane.showMessageDialog(this, "Selecciona una sala");
        }
    }

    private void enviarSticker() {
        if (salaSeleccionada != null) {
            String sticker = (String) comboStickers.getSelectedItem();
            Mensaje msg = new Mensaje(Mensaje.ENVIAR_STICKER, nombreUsuario,
                                     salaSeleccionada, sticker);
            enviarAlServidor(msg);
        } else {
            JOptionPane.showMessageDialog(this, "Selecciona una sala");
        }
    }

    private void enviarMensajePrivado() {
        if (salaSeleccionada != null && modeloUsuarios.getSize() > 0) {
            String destinatario = (String) JOptionPane.showInputDialog(
                this, "Selecciona el usuario:", "Mensaje Privado",
                JOptionPane.QUESTION_MESSAGE, null,
                modeloUsuarios.toArray(), modeloUsuarios.getElementAt(0));

            if (destinatario != null) {
                String texto = JOptionPane.showInputDialog(this,
                    "Mensaje para " + destinatario + ":");
                if (texto != null && !texto.trim().isEmpty()) {
                    Mensaje msg = new Mensaje(Mensaje.MENSAJE_PRIVADO, nombreUsuario,
                                             salaSeleccionada, texto);
                    msg.setDestinatario(destinatario);
                    enviarAlServidor(msg);
                    mostrarMensaje("[PRIVADO a " + destinatario + "] " + texto);
                }
            }
        } else {
            JOptionPane.showMessageDialog(this, "Necesitas estar en una sala con usuarios");
        }
    }

    private void manejarGrabacionAudio() {
        if (salaSeleccionada == null) {
            JOptionPane.showMessageDialog(this, "Selecciona una sala primero");
            return;
        }
        if(!grabandoAudio) {
            try {
                manejadorAudio.iniciarGrabacion();
                grabandoAudio = true;
                btnGrabarAudio.setText("◼ Detener");
                btnGrabarAudio.setBackground(Color.GREEN);
                mostrarMensaje("🎤 Grabando audio...");
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Error al acceder al micrófono: " + e.getMessage(), 
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            byte[] datosAudio = manejadorAudio.detenerGrabacion();
            grabandoAudio = false;
            btnGrabarAudio.setText("🎤 Grabar Audio");
            btnGrabarAudio.setBackground(Color.RED);

            if(datosAudio.length > 1000) {
                Mensaje msg = new Mensaje(Mensaje.ENVIAR_AUDIO, nombreUsuario, salaSeleccionada, "");
                msg.setDatos(datosAudio);
                enviarAlServidor(msg);
                mostrarMensaje("✅ Audio enviado (" + (datosAudio.length / 1024) + " KB)");
            } else {
                JOptionPane.showMessageDialog(this, "Audio muy corto. Intenta grabar más tiempo.");
            }
        }
    }

    private void enviarAlServidor(Mensaje mensaje) {
        try {
            byte[] datos = mensaje.toBytes();
            InetAddress direccion = InetAddress.getByName(SERVIDOR_IP);
            DatagramPacket paquete = new DatagramPacket(datos, datos.length,
                                                       direccion, SERVIDOR_PUERTO);
            socketCliente.send(paquete);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error enviando: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void mostrarMensaje(String mensaje) {
        areaChat.append(mensaje + "\n");
        areaChat.setCaretPosition(areaChat.getDocument().getLength());
    }

    private void mostrarMensajeSala(String mensaje) {
        if (!mensaje.startsWith("[" + salaSeleccionada + "]")) {
            areaChat.append(mensaje + "\n");
        } else {
            // Ya tiene el prefijo de sala
            areaChat.append(mensaje.substring(salaSeleccionada.length() + 3) + "\n");
        }
        areaChat.setCaretPosition(areaChat.getDocument().getLength());
    }

    // NUEVA CLASE: Información de cada sala
    private class SalaInfo {
        String nombre;
        String direccionMulticast;
        MulticastSocket socket;
        InetAddress grupo;
        java.util.List<String> historialMensajes;

        public SalaInfo(String nombre, String direccionMulticast,
                       MulticastSocket socket, InetAddress grupo) {
            this.nombre = nombre;
            this.direccionMulticast = direccionMulticast;
            this.socket = socket;
            this.grupo = grupo;
            this.historialMensajes = new ArrayList<>();
        }

        public void agregarMensaje(String mensaje) {
            historialMensajes.add(mensaje);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClienteChat());
    }
}

