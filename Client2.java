import java.net.Socket;
import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.awt.BorderLayout;

public class Client2 {
    private String username;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private JFrame frame;
    private JTextArea chatArea;
    private JTextField inputField;
    private JButton sendButton;
    private JButton startButton; // Add "Start" button

    public Client2() {
        try {
            socket = new Socket("10.210.124.160", 4414);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            username = JOptionPane.showInputDialog("Enter your username:");
            if (username == null || username.trim().isEmpty()) {
                System.exit(0);
            }
            out.println(username);

            setupGUI();

            new Thread(new ServerListener()).start();
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Unable to connect to server.");
            System.exit(0);
        }
    }

    private void setupGUI() {
        frame = new JFrame("CrazyEights Game - " + username);
        chatArea = new JTextArea(20, 50);
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);

        inputField = new JTextField(40);
        sendButton = new JButton("Send");
        startButton = new JButton("Start"); // Initialize "Start" button

        // Create a menu bar
        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("Options");
        JMenuItem rulesMenuItem = new JMenuItem("Game Rules");

        // Add action listener to display rules
        rulesMenuItem.addActionListener(e -> showGameRules());
        menu.add(rulesMenuItem);
        menuBar.add(menu);
        frame.setJMenuBar(menuBar);

        JPanel panel = new JPanel();
        panel.add(inputField);
        panel.add(sendButton);
        panel.add(startButton); // Add "Start" button to panel

        frame.getContentPane().add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.getContentPane().add(panel, BorderLayout.SOUTH);

        inputField.addActionListener(e -> sendMessage());
        sendButton.addActionListener(e -> sendMessage());
        startButton.addActionListener(e -> sendStartSignal()); // Add action listener for "Start" button

        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private void showGameRules() {
        String rules = """
            Crazy Eights Rules:
            1. Each player is dealt 5 cards.
            2. The goal is to be the first player to get rid of all your cards.
            3. On your turn, you must play a card that matches the suit or rank of the top card on the discard pile.
            4. If you cannot play, you must draw a card from the deck.
            5. Eights are wild and can be played at any time. The player who plays an eight chooses the next suit.
            6. The game continues until one player has no cards left.
            """;
        JOptionPane.showMessageDialog(frame, rules, "Game Rules", JOptionPane.INFORMATION_MESSAGE);
    }

    private void sendStartSignal() {
        out.println("START_GAME"); // Notify server that the player is ready
        out.flush();
        chatArea.append("You are ready to start the game.\n");
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (!message.isEmpty()) {
            if (message.startsWith("PLAY:") || message.equals("DRAW")) {
                out.println(message); // Send play card or draw card command
            } else {
                out.println("CHAT:" + message); // Send chat message
            }
            out.flush();
            inputField.setText("");
        }
    }

    private class ServerListener implements Runnable {
        @Override
        public void run() {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("HAND:")) {
                        String[] cards = message.substring(5).split(",");
                        chatArea.append("Your hand: " + String.join(", ", cards) + "\n");
                    } else if (message.startsWith("DRAWN_CARD:")) {
                        String card = message.substring(11);
                        chatArea.append("You drew: " + card + "\n");
                    } else if (message.equals("YOUR_TURN")) {
                        chatArea.append("It's your turn! Type 'PLAY:<card>' to play a card or 'DRAW' to draw a card.\n");
                        out.println("HAND_REQUEST"); // Request the server to send the player's hand
                        out.flush();
                    } else if (message.equals("CHOOSE_SUIT")) {
                        String suit = JOptionPane.showInputDialog(frame, "Choose a suit (Hearts, Diamonds, Clubs, Spades):");
                        out.println(suit != null ? suit : "Hearts");
                        out.flush();
                    } else if (message.equals("CLEAR_CHAT")) {
                        chatArea.setText(""); // Clear the chat area
                    } else if (message.startsWith("Current lineup:")) {
                        chatArea.append(message + "\n");
                    } else {
                        chatArea.append(message + "\n");
                    }
                    chatArea.setCaretPosition(chatArea.getDocument().getLength());
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(0);
            }
        }
    }

    public static void main(String[] args) {
        new Client2();
    }
}
