import java.net.Socket;
import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Client2 {
    private String username;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private JFrame frame;
    private JTextArea chatArea;
    private JTextField inputField;
    private JButton drawButton;
    private JButton startGameButton; // Add "Start Game" button
    private JPanel cardPanel; // Panel to display cards graphically
    private List<String> hand = new ArrayList<>(); // Player's hand
    private JPanel deckPanel; // Panel to display the current card on the discard pile
    private String currentCardOnDeck; // Track the current card on the discard pile

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
        frame.setLayout(new BorderLayout());

        // Chat area for optional text input
        chatArea = new JTextArea(5, 50);
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);

        inputField = new JTextField(40);
        drawButton = new JButton("Draw Card");
        startGameButton = new JButton("Start Game"); // Initialize "Start Game" button

        // Card panel for graphical card display
        cardPanel = new JPanel();
        cardPanel.setLayout(new FlowLayout());

        // Deck panel to display the current card on the discard pile
        deckPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (currentCardOnDeck != null) {
                    g.setColor(Color.WHITE); // Card background
                    g.fillRoundRect(10, 10, 80, 120, 15, 15); // Draw card shape
                    g.setColor(Color.BLACK); // Card border
                    g.drawRoundRect(10, 10, 80, 120, 15, 15);
                    g.setColor(Color.RED); // Card text color
                    g.drawString(currentCardOnDeck, 20, 70); // Draw card text
                }
            }
        };
        deckPanel.setPreferredSize(new Dimension(100, 140)); // Set deck panel size

        // Add components to the frame
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BorderLayout());
        bottomPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        JPanel inputPanel = new JPanel();
        inputPanel.add(inputField);
        inputPanel.add(drawButton);
        inputPanel.add(startGameButton); // Add "Start Game" button to input panel
        bottomPanel.add(inputPanel, BorderLayout.SOUTH);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(cardPanel, BorderLayout.CENTER);
        centerPanel.add(deckPanel, BorderLayout.SOUTH); // Add deck panel below the player's cards

        frame.add(centerPanel, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // Add action listeners
        inputField.addActionListener(e -> sendMessage());
        drawButton.addActionListener(e -> drawCard());
        startGameButton.addActionListener(e -> showStartMenu()); // Add action listener for "Start Game"

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setVisible(true);
    }

    private void showStartMenu() {
        // Display a graphical start menu dialog
        String message = """
            Welcome to Crazy Eights!
            Rules:
            1. Each player is dealt 5 cards.
            2. The goal is to be the first player to get rid of all your cards.
            3. On your turn, you must play a card that matches the suit or rank of the top card on the discard pile.
            4. If you cannot play, you must draw a card from the deck.
            5. Eights are wild and can be played at any time. The player who plays an eight chooses the next suit.
            6. The game continues until one player has no cards left.
            """;
        int response = JOptionPane.showConfirmDialog(frame, message, "Start Game", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (response == JOptionPane.OK_OPTION) {
            out.println("START_GAME"); // Notify the server that the player is ready
            out.flush();
        }
    }

    private void updateCardPanel() {
        cardPanel.removeAll(); // Clear existing cards
        for (String card : hand) {
            JPanel cardGraphic = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    g.setColor(Color.WHITE); // Card background
                    g.fillRoundRect(10, 10, 80, 120, 15, 15); // Draw card shape
                    g.setColor(Color.BLACK); // Card border
                    g.drawRoundRect(10, 10, 80, 120, 15, 15);
                    g.setColor(Color.BLUE); // Card text color
                    g.drawString(card, 20, 70); // Draw card text
                }
            };
            cardGraphic.setPreferredSize(new Dimension(100, 140)); // Set card size
            cardGraphic.setToolTipText("Click to play this card"); // Add tooltip
            cardGraphic.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (isValidPlay(card)) {
                        playCard(card); // Play the selected card
                    } else {
                        JOptionPane.showMessageDialog(frame, "Invalid move! Card does not match the current suit or rank.", "Invalid Move", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
            cardPanel.add(cardGraphic); // Add the card graphic to the card panel
        }
        cardPanel.revalidate();
        cardPanel.repaint();
    }

    private boolean isValidPlay(String card) {
        if (currentCardOnDeck == null) return false; // No card in play yet
        String[] currentCardParts = currentCardOnDeck.split(" of ");
        String[] cardParts = card.split(" of ");
        // Allow play if the rank matches, the suit matches, or the card is an "8" (wild card)
        return cardParts[0].equals(currentCardParts[0]) || cardParts[1].equals(currentCardParts[1]) || cardParts[0].equals("8");
    }

    private void playCard(String card) {
        if (isValidPlay(card)) {
            hand.remove(card); // Remove the card from the hand
            updateCardPanel(); // Update the card panel
            out.println("PLAY:" + card); // Send the play command to the server
            out.flush();
        } else {
            JOptionPane.showMessageDialog(frame, "Invalid move! Card does not match the current suit or rank.", "Invalid Move", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void drawCard() {
        out.println("DRAW"); // Send the draw command to the server
        out.flush();
    }

    private void updateDeckCard(String card) {
        currentCardOnDeck = card; // Update the current card on the discard pile
        deckPanel.repaint(); // Repaint the deck panel to reflect the new card
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (!message.isEmpty()) {
            out.println("CHAT:" + message); // Send chat message
            out.flush();
            inputField.setText(""); // Clear the input field after sending
        }
    }

    private class ServerListener implements Runnable {
        @Override
        public void run() {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("HAND:")) {
                        hand.clear();
                        String[] cards = message.substring(5).split(",");
                        for (String card : cards) {
                            hand.add(card);
                        }
                        updateCardPanel(); // Update the card panel with the new hand
                    } else if (message.startsWith("DRAWN_CARD:")) {
                        String card = message.substring(11);
                        hand.add(card); // Add the drawn card to the hand
                        updateCardPanel(); // Update the card panel
                    } else if (message.startsWith("CURRENT_CARD:")) {
                        String card = message.substring(13);
                        updateDeckCard(card); // Update the current card on the discard pile
                    } else if (message.equals("YOUR_TURN")) {
                        chatArea.append("It's your turn! Click a card to play or press 'Draw Card'.\n");
                    } else if (message.equals("CHOOSE_SUIT")) {
                        String suit = JOptionPane.showInputDialog(frame, "Choose a suit (Hearts, Diamonds, Clubs, Spades):");
                        out.println(suit != null ? suit : "Hearts");
                        out.flush();
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
