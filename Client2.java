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
    private String currentSuit; // Track the current suit in play

    public Client2() {
        try {
            socket = new Socket("192.168.0.239", 4414);
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
                    
                    // Determine card color based on suit
                    if (currentCardOnDeck.contains(" of ")) {
                        String suit = currentCardOnDeck.split(" of ")[1].trim();
                        if (suit.equals("Hearts") || suit.equals("Diamonds")) {
                            g.setColor(Color.RED); // Red for Hearts and Diamonds
                        } else {
                            g.setColor(Color.BLACK); // Black for Clubs and Spades
                        }
                    } else {
                        g.setColor(Color.BLACK); // Default color
                    }
                    
                    g.drawString(currentCardOnDeck, 20, 70); // Draw card text
                    
                    // Display current suit if it's been set after playing an 8
                    if (currentSuit != null && currentCardOnDeck.startsWith("8")) {
                        g.setColor(Color.BLUE);
                        g.drawString("Current Suit: " + currentSuit, 20, 100);
                    }
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
        System.out.println("Updating card panel with " + hand.size() + " cards");
        
        for (String card : hand) {
            JPanel cardGraphic = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    g.setColor(Color.WHITE); // Card background
                    g.fillRoundRect(10, 10, 80, 120, 15, 15); // Draw card shape
                    g.setColor(Color.BLACK); // Card border
                    g.drawRoundRect(10, 10, 80, 120, 15, 15);
                    
                    // Determine card color based on suit
                    if (card.contains(" of ")) {
                        String suit = card.split(" of ")[1].trim();
                        if (suit.equals("Hearts") || suit.equals("Diamonds")) {
                            g.setColor(Color.RED); // Red for Hearts and Diamonds
                        } else {
                            g.setColor(Color.BLACK); // Black for Clubs and Spades
                        }
                    } else {
                        g.setColor(Color.BLACK); // Default color
                    }
                    
                    g.drawString(card, 20, 70); // Draw card text
                }
            };
            cardGraphic.setPreferredSize(new Dimension(100, 140)); // Set card size
            cardGraphic.setToolTipText("Click to play this card: " + card); // Add tooltip
            cardGraphic.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    System.out.println("Card clicked: " + card);
                    
                    if (isValidPlay(card)) {
                        System.out.println("Valid play detected - playing card: " + card);
                        playCard(card); // Play the selected card
                    } else {
                        System.out.println("Invalid play detected for card: " + card);
                        JOptionPane.showMessageDialog(frame, 
                            "Invalid move! Card does not match the current suit or rank.\n" +
                            "Current card: " + currentCardOnDeck + "\n" +
                            "Current suit: " + (currentSuit != null ? currentSuit : getCardSuit(currentCardOnDeck)) + "\n" +
                            "Your card: " + card, 
                            "Invalid Move", 
                            JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
            cardPanel.add(cardGraphic); // Add the card graphic to the card panel
        }
        cardPanel.revalidate();
        cardPanel.repaint();
    }

    // Helper method to get the suit of a card
    private String getCardSuit(String card) {
        if (card != null && card.contains(" of ")) {
            return card.split(" of ")[1].trim();
        }
        return null;
    }
    
    // Helper method to get the rank of a card
    private String getCardRank(String card) {
        if (card != null && card.contains(" of ")) {
            return card.split(" of ")[0].trim();
        }
        return null;
    }

    // IMPROVED isValidPlay with proper suit tracking
    private boolean isValidPlay(String card) {
        System.out.println("\n--- VALIDATING PLAY ---");
        System.out.println("Card to play: " + card);
        System.out.println("Current card on deck: " + currentCardOnDeck);
        System.out.println("Current suit: " + currentSuit);
        
        // Check if there's a current card
        if (currentCardOnDeck == null || currentCardOnDeck.isEmpty()) {
            System.out.println("ERROR: No current card on deck!");
            return false;
        }
        
        try {
            // Split the cards into rank and suit
            String playRank = getCardRank(card);
            String playSuit = getCardSuit(card);
            String currentRank = getCardRank(currentCardOnDeck);
            
            // If currentSuit is explicitly set (after playing an 8), use that instead of the card's suit
            String effectiveSuit = currentSuit != null ? currentSuit : getCardSuit(currentCardOnDeck);
            
            System.out.println("Current card: " + currentRank + " of " + effectiveSuit);
            System.out.println("Playing card: " + playRank + " of " + playSuit);
            
            // Check if this is an eight (wild card)
            boolean isEight = playRank.equals("8");
            // Check if ranks match
            boolean ranksMatch = playRank.equals(currentRank);
            // Check if suits match with the EFFECTIVE suit (which might be from an 8)
            boolean suitsMatch = playSuit.equals(effectiveSuit);
            
            System.out.println("Is eight? " + isEight);
            System.out.println("Ranks match? " + ranksMatch);
            System.out.println("Suits match? " + suitsMatch);
            
            // Valid if any condition is met
            boolean isValid = isEight || ranksMatch || suitsMatch;
            System.out.println("VALIDATION RESULT: " + (isValid ? "VALID PLAY" : "INVALID PLAY"));
            return isValid;
            
        } catch (Exception e) {
            System.out.println("ERROR in validation: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // IMPROVED playCard
    private void playCard(String card) {
        System.out.println("Playing card: " + card);
        
        // Remove card from hand
        hand.remove(card);
        
        // Update UI
        updateCardPanel();
        
        // Send to server
        System.out.println("Sending PLAY command to server: " + card);
        out.println("PLAY:" + card);
        out.flush();
        
        // If we're playing an 8, we'll need to update the currentSuit later when the server asks
        if (card.startsWith("8")) {
            System.out.println("Playing an 8 - will be prompted for suit selection");
        }
        
        // Update the current card on deck
        updateDeckCard(card);
    }

    private void drawCard() {
        System.out.println("Drawing card from server");
        out.println("DRAW"); // Send the draw command to the server
        out.flush();
    }

    // IMPROVED updateDeckCard - with suit tracking
    private void updateDeckCard(String card) {
        System.out.println("Updating current deck card to: " + card);
        currentCardOnDeck = card; // Update the current card on the discard pile
        
        // Reset current suit if not playing on an 8
        if (!card.startsWith("8")) {
            currentSuit = null;
        }
        
        deckPanel.repaint(); // Repaint the deck panel to reflect the new card
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (!message.isEmpty()) {
            if (message.equalsIgnoreCase("restart")) {
                out.println("RESTART");
                out.flush();
            } else {
                out.println("CHAT:" + message); // Send chat message
                out.flush();
            }
            inputField.setText(""); // Clear the input field after sending
        }
    }

    // IMPROVED ServerListener - with proper suit handling
    private class ServerListener implements Runnable {
        @Override
        public void run() {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("SERVER → CLIENT: " + message);
                    
                    if (message.startsWith("HAND:")) {
                        hand.clear();
                        String[] cards = message.substring(5).split(",");
                        for (String card : cards) {
                            if (!card.trim().isEmpty()) {
                                hand.add(card.trim());
                            }
                        }
                        System.out.println("Hand updated: " + hand);
                        SwingUtilities.invokeLater(() -> updateCardPanel());
                        
                    } else if (message.startsWith("DRAWN_CARD:")) {
                        String card = message.substring(11).trim();
                        hand.add(card);
                        System.out.println("Card drawn: " + card);
                        chatArea.append("You drew: " + card + "\n");
                        SwingUtilities.invokeLater(() -> updateCardPanel());
                        
                    } else if (message.startsWith("CURRENT_CARD:")) {
                        String card = message.substring(13).trim();
                        System.out.println("Current card updated to: " + card);
                        currentCardOnDeck = card;
                        SwingUtilities.invokeLater(() -> deckPanel.repaint());
                        chatArea.append("Current card: " + card + "\n");
                        
                    } else if (message.equals("YOUR_TURN")) {
                        System.out.println("It's now this player's turn");
                        chatArea.append("It's your turn! Click a card to play or press 'Draw Card'.\n");
                        
                    } else if (message.equals("CHOOSE_SUIT")) {
                        String[] suits = {"Hearts", "Diamonds", "Clubs", "Spades"};
                        String suit = (String) JOptionPane.showInputDialog(
                            frame, 
                            "Choose a suit:", 
                            "Suit Selection", 
                            JOptionPane.QUESTION_MESSAGE, 
                            null, 
                            suits, 
                            suits[0]);
                        
                        if (suit != null) {
                            System.out.println("Chosen suit: " + suit);
                            // Update the current suit locally
                            currentSuit = suit;
                            // Send the suit choice to the server
                            out.println("SUIT:" + suit);
                            out.flush();
                            chatArea.append("You chose suit: " + suit + "\n");
                        } else {
                            // Default to Hearts if canceled
                            System.out.println("Dialog canceled, defaulting to Hearts");
                            currentSuit = "Hearts";
                            out.println("SUIT:Hearts");
                            out.flush();
                            chatArea.append("You chose suit: Hearts\n");
                        }
                        deckPanel.repaint(); // Repaint to show the new suit
                        
                    } else if (message.startsWith("CHOSEN_SUIT:")) {
                        // Update when server tells us another player has chosen a suit
                        String suit = message.substring(12).trim();
                        System.out.println("Server notified of chosen suit: " + suit);
                        currentSuit = suit;
                        chatArea.append("Current suit changed to: " + currentSuit + "\n");
                        deckPanel.repaint(); // Repaint to show the new suit
                        
                    } else {
                        chatArea.append(message + "\n");
                        
                        // End game screen if someone wins
                        if (message.contains("wins the game!")) {
                            String winner = message.split(" ")[0];
                            SwingUtilities.invokeLater(() -> {
                                JOptionPane.showMessageDialog(frame, winner + " wins the game!\nType 'restart' to play again.", "Game Over", JOptionPane.INFORMATION_MESSAGE);
                            });
                        }
                        
                        // Check if a message contains suit selection from another player
                        if (message.contains(" chose suit: ")) {
                            String suit = message.substring(message.indexOf("suit: ") + 6).trim();
                            System.out.println("Detected suit change in chat: " + suit);
                            currentSuit = suit;
                            deckPanel.repaint();
                        }
                    }
                    chatArea.setCaretPosition(chatArea.getDocument().getLength());
                }
            } catch (IOException e) {
                System.out.println("Connection error: " + e.getMessage());
                chatArea.append("Connection to server lost: " + e.getMessage() + "\n");
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Client2());
    }
}