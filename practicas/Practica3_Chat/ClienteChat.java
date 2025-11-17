import javax.swing.*;
import javax.swing.border.*;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClienteChat extends JFrame {
    private static final String SERVIDOR_IP = "localhost";
    private static final int SERVIDOR_PUERTO = 9876;
    private static final int CLIENTE_PUERTO = 5000;

    private DatagramSocket socket;
    private String nombreUsuario;

    // Gestión de múltiples salas
    private Map<String, InfoSala> salasActivas; // nombre sala -> info
    private String salaActualSeleccionada;

    // Componentes GUI
    private JList<String> listaSalas;
    private DefaultListModel<String> modeloSalas;
    private JTextArea areaChat;
    private JTextField campoMensaje;
    private JButton btnEnviar;
    private JButton btnCrearSala;
    private JButton btnUnirse;
    private JButton btnSalir;
    private JList<String> listaUsuarios;
    private DefaultListModel<String> modeloUsuarios;
    private JComboBox<String> comboStickers;
    private JLabel labelSalaActual;
    private JButton btnGrabarAudio;
    private JButton btnDetenerGrabacion;

    // Audio
    private AudioRecorder audioRecorder;
    private boolean grabando = false;

    public ClienteChat() {
        salasActivas = new ConcurrentHashMap<>();

        nombreUsuario = JOptionPane.showInputDialog(this, "Ingresa tu nombre de usuario:");
        if (nombreUsuario == null || nombreUsuario.trim().isEmpty()) {
            System.exit(0);
        }

        configurarVentana();
        configurarSocket();
        iniciarHiloReceptor();

        // Manejar cierre de ventana
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                desconectar();
                System.exit(0);
            }
        });
    }

    private void configurarVentana() {
        setTitle("💬 Chat - Usuario: " + nombreUsuario);
        setSize(1100, 650);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Panel superior - Controles
        JPanel panelSuperior = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        panelSuperior.setBorder(new EmptyBorder(5, 5, 5, 5));

        btnCrearSala = new JButton("➕ Crear Sala");
        btnUnirse = new JButton("🚪 Unirse");
        btnSalir = new JButton("🚫 Salir de Sala");
        labelSalaActual = new JLabel("Sin sala seleccionada");
        labelSalaActual.setFont(new Font("Arial", Font.BOLD, 14));
        labelSalaActual.setForeground(Color.GRAY);

        panelSuperior.add(btnCrearSala);
        panelSuperior.add(btnUnirse);
        panelSuperior.add(btnSalir);
        panelSuperior.add(new JSeparator(SwingConstants.VERTICAL));
        panelSuperior.add(new JLabel("📍 Sala actual: "));
        panelSuperior.add(labelSalaActual);

        // Panel izquierdo - Lista de salas del usuario
        JPanel panelIzquierdo = new JPanel(new BorderLayout(5, 5));
        panelIzquierdo.setPreferredSize(new Dimension(180, 0));
        panelIzquierdo.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            "Mis Salas",
            TitledBorder.CENTER,
            TitledBorder.TOP));

        modeloSalas = new DefaultListModel<>();
        listaSalas = new JList<>(modeloSalas);
        listaSalas.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listaSalas.setFont(new Font("Arial", Font.PLAIN, 13));
        listaSalas.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                cambiarSalaSeleccionada();
            }
        });

        JScrollPane scrollSalas = new JScrollPane(listaSalas);
        panelIzquierdo.add(scrollSalas, BorderLayout.CENTER);

        // Panel central - Chat
        JPanel panelCentral = new JPanel(new BorderLayout(5, 5));

        areaChat = new JTextArea();
        areaChat.setEditable(false);
        areaChat.setLineWrap(true);
        areaChat.setWrapStyleWord(true);
        areaChat.setFont(new Font("Monospaced", Font.PLAIN, 12));
        areaChat.setBackground(new Color(245, 245, 245));
        JScrollPane scrollChat = new JScrollPane(areaChat);

        panelCentral.add(scrollChat, BorderLayout.CENTER);

        // Panel derecho - Lista de usuarios
        JPanel panelDerecho = new JPanel(new BorderLayout(5, 5));
        panelDerecho.setPreferredSize(new Dimension(180, 0));
        panelDerecho.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            "Participantes",
            TitledBorder.CENTER,
            TitledBorder.TOP));

        modeloUsuarios = new DefaultListModel<>();
        listaUsuarios = new JList<>(modeloUsuarios);
        listaUsuarios.setFont(new Font("Arial", Font.PLAIN, 13));
        JScrollPane scrollUsuarios = new JScrollPane(listaUsuarios);

        panelDerecho.add(scrollUsuarios, BorderLayout.CENTER);

        // Panel inferior - Envío de mensajes
        JPanel panelInferior = new JPanel(new BorderLayout(5, 5));
        panelInferior.setBorder(new EmptyBorder(5, 5, 5, 5));

        // Panel de herramientas
        JPanel panelHerramientas = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));

        String[] stickers = {"😀", "😂", "❤️", "👍", "🎉", "🔥", "💯", "✨", "🎵", "💬"};
        comboStickers = new JComboBox<>(stickers);
        JButton btnSticker = new JButton("Sticker");
        JButton btnPrivado = new JButton("💌 Privado");
        btnGrabarAudio = new JButton("🎤 Grabar");
        btnDetenerGrabacion = new JButton("⏹️ Detener");
        btnDetenerGrabacion.setEnabled(false);
        btnDetenerGrabacion.setBackground(Color.RED);

        panelHerramientas.add(comboStickers);
        panelHerramientas.add(btnSticker);
        panelHerramientas.add(new JSeparator(SwingConstants.VERTICAL));
        panelHerramientas.add(btnPrivado);
        panelHerramientas.add(new JSeparator(SwingConstants.VERTICAL));
        panelHerramientas.add(btnGrabarAudio);
        panelHerramientas.add(btnDetenerGrabacion);

        // Campo de texto y botón enviar
        JPanel panelEnvio = new JPanel(new BorderLayout(5, 5));
        campoMensaje = new JTextField();
        campoMensaje.setFont(new Font("Arial", Font.PLAIN, 13));
        btnEnviar = new JButton("📤 Enviar");
        btnEnviar.setPreferredSize(new Dimension(100, 30));

        panelEnvio.add(campoMensaje, BorderLayout.CENTER);
        panelEnvio.add(btnEnviar, BorderLayout.EAST);

        panelInferior.add(panelHerramientas, BorderLayout.NORTH);
        panelInferior.add(panelEnvio, BorderLayout.CENTER);

        // Agregar paneles a la ventana
        add(panelSuperior, BorderLayout.NORTH);
        add(panelIzquierdo, BorderLayout.WEST);
        add(panelCentral, BorderLayout.CENTER);
        add(panelDerecho, BorderLayout.EAST);
        add(panelInferior, BorderLayout.SOUTH);

        // Eventos
        btnCrearSala.addActionListener(e -> crearSala());
        btnUnirse.addActionListener(e -> unirseASala());
        btnSalir.addActionListener(e -> salirDeSala());
        btnEnviar.addActionListener(e -> enviarMensaje());
        btnSticker.addActionListener(e -> enviarSticker());
        btnPrivado.addActionListener(e -> enviarMensajePrivado());
        btnGrabarAudio.addActionListener(e -> iniciarGrabacion());
        btnDetenerGrabacion.addActionListener(e -> detenerGrabacion());

        campoMensaje.addActionListener(e -> enviarMensaje());

        audioRecorder = new AudioRecorder();

        setVisible(true);
    }

    private void configurarSocket() {
        try {
            int puerto = CLIENTE_PUERTO + new Random().nextInt(1000);
            socket = new DatagramSocket(puerto);
            System.out.println("✅ Cliente iniciado en puerto " + puerto);
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
        if (mensaje.getTipo().equals(Mensaje.INFO_SALA)) {
            // El servidor nos envió info para unirnos a una sala
            String nombreSala = mensaje.getSala();
            String dirMulticast = mensaje.getDireccionMulticast();
            int puertoMulticast = mensaje.getPuertoMulticast();

            unirseAGrupoMulticast(nombreSala, dirMulticast, puertoMulticast);
            mostrarMensajeEnSala(nombreSala, mensaje.getContenido());

        } else if (mensaje.getTipo().equals(Mensaje.RESPUESTA)) {
            // Mensaje de confirmación del servidor o de una sala
            if (mensaje.getDireccionMulticast() != null) {
                // Es respuesta de crear sala, unirse automáticamente
                String nombreSala = mensaje.getSala();
                String dirMulticast = mensaje.getDireccionMulticast();
                int puertoMulticast = mensaje.getPuertoMulticast();

                unirseAGrupoMulticast(nombreSala, dirMulticast, puertoMulticast);
                mostrarMensajeEnSala(nombreSala, mensaje.getContenido());
            } else {
                // Mensaje general
                mostrarMensajeGeneral(mensaje.getContenido());
            }
        }
    }

    private void unirseAGrupoMulticast(String nombreSala, String direccion, int puerto) {
        try {
            if (salasActivas.containsKey(nombreSala)) {
                return; // Ya estamos en esta sala
            }

            InfoSala infoSala = new InfoSala(nombreSala, direccion, puerto);
            salasActivas.put(nombreSala, infoSala);

            // Agregar a la lista visual
            if (!modeloSalas.contains(nombreSala)) {
                modeloSalas.addElement(nombreSala);
            }

            // Iniciar hilo receptor para esta sala
            iniciarReceptorMulticast(infoSala);

            System.out.println("✅ Unido a grupo multicast: " + direccion + ":" + puerto);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error uniéndose al grupo multicast: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void iniciarReceptorMulticast(InfoSala infoSala) {
        Thread hiloMulticast = new Thread(() -> {
            MulticastSocket multicastSocket = null;
            try {
                multicastSocket = new MulticastSocket(infoSala.puertoMulticast);
                InetAddress grupo = InetAddress.getByName(infoSala.direccionMulticast);

                // Unirse al grupo
                InetSocketAddress grupoAddress = new InetSocketAddress(grupo, infoSala.puertoMulticast);
                NetworkInterface netIf = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());

                multicastSocket.joinGroup(grupoAddress, netIf);
                infoSala.socket = multicastSocket;

                while (!multicastSocket.isClosed() && salasActivas.containsKey(infoSala.nombreSala)) {
                    byte[] buffer = new byte[65535];
                    DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                    multicastSocket.receive(paquete);

                    byte[] datos = Arrays.copyOf(paquete.getData(), paquete.getLength());
                    Mensaje mensaje = Mensaje.fromBytes(datos);

                    SwingUtilities.invokeLater(() ->
                        procesarMensajeMulticast(infoSala.nombreSala, mensaje));
                }

            } catch (Exception e) {
                if (multicastSocket != null && !multicastSocket.isClosed()) {
                    System.err.println("Error en receptor multicast de " +
                                     infoSala.nombreSala + ": " + e.getMessage());
                }
            }
        });
        hiloMulticast.setDaemon(true);
        hiloMulticast.start();
    }

    private void procesarMensajeMulticast(String nombreSala, Mensaje mensaje) {
        String contenido = mensaje.getContenido();

        // Actualizar lista de usuarios si es una actualización
        if (contenido.startsWith("USUARIOS:")) {
            String listaStr = contenido.substring(9);
            if (nombreSala.equals(salaActualSeleccionada)) {
                actualizarListaUsuarios(listaStr);
            }
        } else {
            // Mostrar mensaje en el área de chat de la sala
            mostrarMensajeEnSala(nombreSala, contenido);
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

    private void cambiarSalaSeleccionada() {
        String salaSeleccionada = listaSalas.getSelectedValue();
        if (salaSeleccionada != null && !salaSeleccionada.equals(salaActualSeleccionada)) {
            salaActualSeleccionada = salaSeleccionada;
            labelSalaActual.setText(salaSeleccionada);
            labelSalaActual.setForeground(new Color(0, 100, 200));

            // Cargar el historial de chat de esta sala
            InfoSala infoSala = salasActivas.get(salaSeleccionada);
            if (infoSala != null) {
                areaChat.setText(infoSala.historialChat.toString());
                areaChat.setCaretPosition(areaChat.getDocument().getLength());

                // Solicitar lista de usuarios actualizada
                // (se actualizará automáticamente vía multicast)
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
            if (salasActivas.containsKey(nombreSala)) {
                JOptionPane.showMessageDialog(this, "Ya estás en esta sala");
                return;
            }
            Mensaje msg = new Mensaje(Mensaje.UNIRSE_SALA, nombreUsuario, nombreSala, "");
            enviarAlServidor(msg);
        }
    }

    private void salirDeSala() {
        if (salaActualSeleccionada != null) {
            int confirm = JOptionPane.showConfirmDialog(this,
                "¿Salir de la sala '" + salaActualSeleccionada + "'?",
                "Confirmar", JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                Mensaje msg = new Mensaje(Mensaje.SALIR_SALA, nombreUsuario,
                                         salaActualSeleccionada, "");
                enviarAlServidor(msg);

                // Cerrar socket multicast
                InfoSala infoSala = salasActivas.get(salaActualSeleccionada);
                if (infoSala != null && infoSala.socket != null) {
                    infoSala.socket.close();
                }

                // Remover de la lista
                salasActivas.remove(salaActualSeleccionada);
                modeloSalas.removeElement(salaActualSeleccionada);

                // Limpiar interfaz
                salaActualSeleccionada = null;
                labelSalaActual.setText("Sin sala seleccionada");
                labelSalaActual.setForeground(Color.GRAY);
                areaChat.setText("");
                modeloUsuarios.clear();
            }
        } else {
            JOptionPane.showMessageDialog(this, "Selecciona una sala primero");
        }
    }

    private void enviarMensaje() {
        String texto = campoMensaje.getText().trim();
        if (!texto.isEmpty() && salaActualSeleccionada != null) {
            InfoSala infoSala = salasActivas.get(salaActualSeleccionada);
            if (infoSala != null) {
                Mensaje msg = new Mensaje(Mensaje.MENSAJE_SALA, nombreUsuario,
                                         salaActualSeleccionada, texto);
                enviarMulticast(msg, infoSala);
                campoMensaje.setText("");
            }
        } else if (salaActualSeleccionada == null) {
            JOptionPane.showMessageDialog(this, "Selecciona una sala primero");
        }
    }

    private void enviarSticker() {
        if (salaActualSeleccionada != null) {
            String sticker = (String) comboStickers.getSelectedItem();
            InfoSala infoSala = salasActivas.get(salaActualSeleccionada);
            if (infoSala != null) {
                Mensaje msg = new Mensaje(Mensaje.ENVIAR_STICKER, nombreUsuario,
                                         salaActualSeleccionada, sticker);
                enviarMulticast(msg, infoSala);
            }
        } else {
            JOptionPane.showMessageDialog(this, "Selecciona una sala primero");
        }
    }

    private void enviarMensajePrivado() {
        if (salaActualSeleccionada != null && modeloUsuarios.getSize() > 0) {
            String destinatario = (String) JOptionPane.showInputDialog(
                this,
                "Selecciona el usuario:",
                "Mensaje Privado",
                JOptionPane.QUESTION_MESSAGE,
                null,
                modeloUsuarios.toArray(),
                modeloUsuarios.getElementAt(0)
            );

            if (destinatario != null && !destinatario.equals(nombreUsuario)) {
                String texto = JOptionPane.showInputDialog(this,
                    "Mensaje privado para " + destinatario + ":");
                if (texto != null && !texto.trim().isEmpty()) {
                    InfoSala infoSala = salasActivas.get(salaActualSeleccionada);
                    if (infoSala != null) {
                        Mensaje msg = new Mensaje(Mensaje.MENSAJE_PRIVADO, nombreUsuario,
                                                 salaActualSeleccionada, texto);
                        msg.setDestinatario(destinatario);
                        enviarMulticast(msg, infoSala);

                        // Mostrar en nuestro chat
                        mostrarMensajeEnSala(salaActualSeleccionada,
                            "[PRIVADO a " + destinatario + "] " + texto);
                    }
                }
            }
        } else {
            JOptionPane.showMessageDialog(this, "Selecciona una sala con usuarios");
        }
    }

    private void iniciarGrabacion() {
        if (salaActualSeleccionada == null) {
            JOptionPane.showMessageDialog(this, "Selecciona una sala primero");
            return;
        }

        grabando = true;
        btnGrabarAudio.setEnabled(false);
        btnDetenerGrabacion.setEnabled(true);
        audioRecorder.iniciarGrabacion();
        mostrarMensajeEnSala(salaActualSeleccionada, "🎤 Grabando audio...");
    }

    private void detenerGrabacion() {
        if (grabando) {
            grabando = false;
            btnGrabarAudio.setEnabled(true);
            btnDetenerGrabacion.setEnabled(false);

            byte[] audioData = audioRecorder.detenerGrabacion();

            if (audioData != null && audioData.length > 0) {
                InfoSala infoSala = salasActivas.get(salaActualSeleccionada);
                if (infoSala != null) {
                    Mensaje msg = new Mensaje(Mensaje.ENVIAR_AUDIO, nombreUsuario,
                                             salaActualSeleccionada,
                                             "Audio (" + audioData.length + " bytes)");
                    msg.setDatos(audioData);
                    enviarMulticast(msg, infoSala);

                    mostrarMensajeEnSala(salaActualSeleccionada,
                        "📤 Audio enviado (" + audioData.length + " bytes)");
                }
            }
        }
    }

    private void enviarMulticast(Mensaje mensaje, InfoSala infoSala) {
        try {
            byte[] datos = mensaje.toBytes();
            InetAddress grupo = InetAddress.getByName(infoSala.direccionMulticast);
            DatagramPacket paquete = new DatagramPacket(datos, datos.length,
                                                       grupo, infoSala.puertoMulticast);

            DatagramSocket socketTemp = new DatagramSocket();
            socketTemp.send(paquete);
            socketTemp.close();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error enviando mensaje: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void enviarAlServidor(Mensaje mensaje) {
        try {
            byte[] datos = mensaje.toBytes();
            InetAddress direccion = InetAddress.getByName(SERVIDOR_IP);
            DatagramPacket paquete = new DatagramPacket(datos, datos.length,
                                                       direccion, SERVIDOR_PUERTO);
            socket.send(paquete);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error comunicándose con el servidor: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void mostrarMensajeEnSala(String nombreSala, String mensaje) {
        InfoSala infoSala = salasActivas.get(nombreSala);
        if (infoSala != null) {
            infoSala.historialChat.append(mensaje).append("\n");

            // Si es la sala actual, actualizar el área de chat
            if (nombreSala.equals(salaActualSeleccionada)) {
                areaChat.append(mensaje + "\n");
                areaChat.setCaretPosition(areaChat.getDocument().getLength());
            }
        }
    }

    private void mostrarMensajeGeneral(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Servidor",
                                     JOptionPane.INFORMATION_MESSAGE);
    }

    private void desconectar() {
        System.out.println("👋 Desconectando usuario " + nombreUsuario);

        // Notificar al servidor
        Mensaje msg = new Mensaje(Mensaje.DESCONECTAR, nombreUsuario, "", "");
        enviarAlServidor(msg);

        // Cerrar todos los sockets multicast
        for (InfoSala infoSala : salasActivas.values()) {
            if (infoSala.socket != null) {
                infoSala.socket.close();
            }
        }

        // Cerrar socket principal
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    // Clase interna para información de sala
    private class InfoSala {
        String nombreSala;
        String direccionMulticast;
        int puertoMulticast;
        MulticastSocket socket;
        StringBuilder historialChat;

        InfoSala(String nombre, String direccion, int puerto) {
            this.nombreSala = nombre;
            this.direccionMulticast = direccion;
            this.puertoMulticast = puerto;
            this.historialChat = new StringBuilder();
        }
    }

    // Clase para grabar y reproducir audio
    private class AudioRecorder {
        private TargetDataLine lineaEntrada;
        private ByteArrayOutputStream streamSalida;
        private AudioFormat formato;
        private boolean grabando;

        public AudioRecorder() {
            // Formato de audio: 16 kHz, 16 bits, mono
            formato = new AudioFormat(16000, 16, 1, true, true);
        }

        public void iniciarGrabacion() {
            try {
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, formato);
                lineaEntrada = (TargetDataLine) AudioSystem.getLine(info);
                lineaEntrada.open(formato);
                lineaEntrada.start();

                streamSalida = new ByteArrayOutputStream();
                grabando = true;

                Thread hiloGrabacion = new Thread(() -> {
                    byte[] buffer = new byte[4096];
                    while (grabando) {
                        int bytesLeidos = lineaEntrada.read(buffer, 0, buffer.length);
                        if (bytesLeidos > 0) {
                            streamSalida.write(buffer, 0, bytesLeidos);
                        }
                    }
                });
                hiloGrabacion.start();

            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                    "Error iniciando grabación: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        public byte[] detenerGrabacion() {
            grabando = false;

            if (lineaEntrada != null) {
                lineaEntrada.stop();
                lineaEntrada.close();
            }

            if (streamSalida != null) {
                return streamSalida.toByteArray();
            }
            return null;
        }

        public void reproducirAudio(byte[] audioData) {
            try {
                ByteArrayInputStream streamEntrada = new ByteArrayInputStream(audioData);
                AudioInputStream audioStream = new AudioInputStream(streamEntrada,
                                                                   formato,
                                                                   audioData.length / formato.getFrameSize());

                DataLine.Info info = new DataLine.Info(SourceDataLine.class, formato);
                SourceDataLine lineaSalida = (SourceDataLine) AudioSystem.getLine(info);
                lineaSalida.open(formato);
                lineaSalida.start();

                byte[] buffer = new byte[4096];
                int bytesLeidos;
                while ((bytesLeidos = audioStream.read(buffer)) != -1) {
                    lineaSalida.write(buffer, 0, bytesLeidos);
                }

                lineaSalida.drain();
                lineaSalida.close();

            } catch (Exception e) {
                System.err.println("Error reproduciendo audio: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> new ClienteChat());
    }
}
