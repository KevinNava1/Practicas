import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClienteChat extends JFrame {
    private static final String SERVIDOR_IP = "localhost";
    private static final int SERVIDOR_PUERTO = 6000;
    private static final int PUERTO_MULTICAST = 6789;
    private static final int CLIENTE_PUERTO = 5000;

    private DatagramSocket socketCliente;
    private String nombreUsuario;

    private Map<String, SalaInfo> salasActivas;
    private String salaSeleccionada;
    private ManejadorAudio manejadorAudio;
    private boolean grabandoAudio = false;

    private byte[] ultimoAudioRecibido;
    private String usuarioAudioRecibido;

    // Componentes GUI
    private JTextArea areaChat;
    private JTextField campoMensaje;
    private JButton btnEnviar;
    private JButton btnCrearSala;
    private JButton btnUnirse;
    private JButton btnSalir;
    private JButton btnListarUsuarios;
    private DefaultListModel<String> modeloSalas;
    private JList<String> listaSalas;
    private JList<String> listaUsuarios;
    private DefaultListModel<String> modeloUsuarios;
    private JComboBox<String> comboStickers;
    private JLabel labelSalaActual;
    private JButton btnGrabarAudio;
    private JButton btnReproducirAudio;

    public ClienteChat() {
        nombreUsuario = JOptionPane.showInputDialog(this, "Ingresa tu nombre de usuario:");
        if (nombreUsuario == null || nombreUsuario.trim().isEmpty()) {
            System.exit(0);
        }

        salasActivas = new ConcurrentHashMap<>();
        manejadorAudio = new ManejadorAudio();
        verificarAudio();
        configurarVentana();
        configurarSocket();
        iniciarHiloReceptorServidor();
        
        // NUEVO: Agregar shutdown hook para limpieza
        agregarShutdownHook();
    }

    // NUEVO: Método para limpiar al cerrar
    private void agregarShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println(" Cerrando cliente...");
            limpiarRecursos();
        }));
    }

    // NUEVO: Limpiar todos los recursos
    private void limpiarRecursos() {
        try {
            // Salir de todas las salas
            for (String sala : new ArrayList<>(salasActivas.keySet())) {
                salirDeSalaSilenciosamente(sala);
            }
            
            // Cerrar socket principal
            if (socketCliente != null && !socketCliente.isClosed()) {
                socketCliente.close();
            }
            
            System.out.println("Recursos limpiados correctamente");
        } catch (Exception e) {
            System.err.println(" Error limpiando recursos: " + e.getMessage());
        }
    }

    // NUEVO: Salir de sala sin mostrar mensajes en GUI
    private void salirDeSalaSilenciosamente(String nombreSala) {
        SalaInfo sala = salasActivas.get(nombreSala);
        if (sala != null) {
            try {
                // Salir del grupo multicast
                sala.socket.leaveGroup(sala.grupo);
                sala.socket.close();

                // Notificar al servidor
                Mensaje msg = new Mensaje(Mensaje.SALIR_SALA, nombreUsuario, nombreSala, "");
                enviarAlServidor(msg);

                System.out.println("✅ Salió silenciosamente de la sala: " + nombreSala);
            } catch (Exception e) {
                System.err.println("❌ Error saliendo de sala " + nombreSala + ": " + e.getMessage());
            }
        }
    }

    // NUEVO: Override del método cerrar ventana
    @Override
    public void dispose() {
        limpiarRecursos();
        super.dispose();
    }

    private void verificarAudio() {
        boolean audioDisponible = ManejadorAudio.verificarAudioDisponible();
        if (!audioDisponible) {
            JOptionPane.showMessageDialog(this, 
                "⚠️ El audio no está disponible en este sistema.\n" +
                "La funcionalidad de audio podría no funcionar correctamente.",
                "Advertencia de Audio", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void configurarVentana() {
        setTitle("💬 Chat - Usuario: " + nombreUsuario);
        setSize(1100, 600);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Cambiado para manejar el cierre
        
        // NUEVO: Listener para cerrar ventana
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                cerrarAplicacion();
            }
        });
        
        setLayout(new BorderLayout(10, 10));

        // Panel Izquierdo: Lista de Salas
        JPanel panelIzquierdo = new JPanel(new BorderLayout());
        panelIzquierdo.setBorder(BorderFactory.createTitledBorder("🏠 Mis Salas"));
        panelIzquierdo.setPreferredSize(new Dimension(200, 0));

        modeloSalas = new DefaultListModel<>();
        listaSalas = new JList<>(modeloSalas);
        listaSalas.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listaSalas.setFont(new Font("Arial", Font.PLAIN, 13));

        listaSalas.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                cambiarSalaActiva();
            }
        });

        JScrollPane scrollSalas = new JScrollPane(listaSalas);
        panelIzquierdo.add(scrollSalas, BorderLayout.CENTER);

        // Panel Superior: Controles
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

        // Panel Central: Chat
        areaChat = new JTextArea();
        areaChat.setEditable(false);
        areaChat.setLineWrap(true);
        areaChat.setWrapStyleWord(true);
        areaChat.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollChat = new JScrollPane(areaChat);

        // Panel Derecho: Usuarios
        modeloUsuarios = new DefaultListModel<>();
        listaUsuarios = new JList<>(modeloUsuarios);
        listaUsuarios.setBorder(BorderFactory.createTitledBorder("👥 Usuarios"));
        JScrollPane scrollUsuarios = new JScrollPane(listaUsuarios);
        scrollUsuarios.setPreferredSize(new Dimension(180, 0));

        // Panel Inferior: Envío
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

        btnReproducirAudio = new JButton("🔊 Reproducir Audio");
        btnReproducirAudio.setBackground(Color.BLUE);
        btnReproducirAudio.setForeground(Color.WHITE);
        btnReproducirAudio.setEnabled(false);

        panelExtras.add(new JLabel("Stickers:"));
        panelExtras.add(comboStickers);
        panelExtras.add(btnSticker);
        panelExtras.add(btnPrivado);
        panelExtras.add(btnGrabarAudio);
        panelExtras.add(btnReproducirAudio);

        panelInferior.add(campoMensaje, BorderLayout.CENTER);
        panelInferior.add(btnEnviar, BorderLayout.EAST);
        panelInferior.add(panelExtras, BorderLayout.NORTH);

        // Agregar componentes
        add(panelIzquierdo, BorderLayout.WEST);
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
        btnGrabarAudio.addActionListener(e -> manejarGrabacionAudio());
        btnReproducirAudio.addActionListener(e -> reproducirUltimoAudio());

        setVisible(true);
    }

    // NUEVO: Método para cerrar la aplicación correctamente
    private void cerrarAplicacion() {
        int opcion = JOptionPane.showConfirmDialog(this,
            "¿Estás seguro de que quieres salir del chat?",
            "Confirmar salida",
            JOptionPane.YES_NO_OPTION);
            
        if (opcion == JOptionPane.YES_OPTION) {
            limpiarRecursos();
            System.exit(0);
        }
    }

    private void configurarSocket() {
        try {
            int puerto = CLIENTE_PUERTO + new Random().nextInt(1000);
            socketCliente = new DatagramSocket(puerto);
            mostrarMensaje("Cliente iniciado en puerto " + puerto);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error al iniciar cliente: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

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

        if (contenido.startsWith("AUDIO:")) {
            String usuarioAudio = contenido.substring(6);
            usuarioAudioRecibido = usuarioAudio;
            ultimoAudioRecibido = mensaje.getDatos();
            
            mostrarMensaje("Audio recibido de " + usuarioAudio + " (" + 
                          (ultimoAudioRecibido != null ? ultimoAudioRecibido.length / 1024 : 0) + " KB)");
            
            btnReproducirAudio.setEnabled(true);
            btnReproducirAudio.setBackground(Color.GREEN);
            return;
        }

        if (mensaje.getDireccionMulticast() != null) {
            String sala = mensaje.getSala();
            if (sala == null || sala.isEmpty()) {
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

    private void unirseAGrupoMulticast(String nombreSala, String direccionMulticast) {
        if (salasActivas.containsKey(nombreSala)) {
            mostrarMensaje(" Ya estás en la sala " + nombreSala);
            return;
        }

        try {
            MulticastSocket socketMulticast = new MulticastSocket(PUERTO_MULTICAST);
            InetAddress grupo = InetAddress.getByName(direccionMulticast);

            socketMulticast.joinGroup(grupo);

            SalaInfo salaInfo = new SalaInfo(nombreSala, direccionMulticast,
                                            socketMulticast, grupo);
            salasActivas.put(nombreSala, salaInfo);

            modeloSalas.addElement(nombreSala);

            iniciarReceptorMulticast(salaInfo);

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

        if (contenido.startsWith("AUDIO:")) {
            String usuarioAudio = contenido.substring(6);
            usuarioAudioRecibido = usuarioAudio;
            ultimoAudioRecibido = mensaje.getDatos();
            
            mostrarMensajeSala("🎧 Audio recibido de " + usuarioAudio + " (" + 
                              (ultimoAudioRecibido != null ? ultimoAudioRecibido.length / 1024 : 0) + " KB)");
            
            btnReproducirAudio.setEnabled(true);
            btnReproducirAudio.setBackground(Color.GREEN);
            return;
        }

        if (contenido.startsWith("USUARIOS:")) {
            if (nombreSala.equals(salaSeleccionada)) {
                String listaStr = contenido.substring(9);
                actualizarListaUsuarios(listaStr);
            }
        } else {
            SalaInfo sala = salasActivas.get(nombreSala);
            if (sala != null) {
                sala.agregarMensaje("[" + nombreSala + "] " + contenido);

                if (nombreSala.equals(salaSeleccionada)) {
                    mostrarMensajeSala(contenido);
                }
            }
        }
    }

    private void reproducirUltimoAudio() {
        if (ultimoAudioRecibido != null && ultimoAudioRecibido.length > 0) {
            try {
                mostrarMensaje("🔊 Reproduciendo audio de " + usuarioAudioRecibido + "...");
                ManejadorAudio.reproducirAudio(ultimoAudioRecibido);
            } catch (Exception e) {
                mostrarMensaje(" Error reproduciendo audio: " + e.getMessage());
            }
        } else {
            mostrarMensaje(" No hay audio para reproducir");
        }
    }

    private void cambiarSalaActiva() {
        String seleccion = listaSalas.getSelectedValue();
        if (seleccion != null && !seleccion.equals(salaSeleccionada)) {
            salaSeleccionada = seleccion;
            labelSalaActual.setText(salaSeleccionada);
            labelSalaActual.setForeground(Color.BLUE);

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
                    sala.socket.leaveGroup(sala.grupo);
                    sala.socket.close();

                    Mensaje msg = new Mensaje(Mensaje.SALIR_SALA, nombreUsuario,
                                             salaSeleccionada, "");
                    enviarAlServidor(msg);

                    salasActivas.remove(salaSeleccionada);
                    modeloSalas.removeElement(salaSeleccionada);

                    salaSeleccionada = null;
                    labelSalaActual.setText("Sin sala");
                    labelSalaActual.setForeground(Color.BLACK);
                    areaChat.setText("");
                    modeloUsuarios.clear();

                    mostrarMensaje("✅ Saliste de la sala");

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
                mostrarMensaje(" Audio enviado (" + (datosAudio.length / 1024) + " KB)");
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
            areaChat.append(mensaje.substring(salaSeleccionada.length() + 3) + "\n");
        }
        areaChat.setCaretPosition(areaChat.getDocument().getLength());
    }

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
