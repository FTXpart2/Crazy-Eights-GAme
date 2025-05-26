import java.net.Socket;
import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.awt.*;
import javax.sound.sampled.*;
import java.io.File;
import java.io.InputStream;

public class Client {
    private String username;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private JFrame frame;
    private JTextArea chatArea;
    private JTextField inputField;
    private JButton drawButton;
    private JButton startGameButton;
    private JButton musicToggleButton; // New button to toggle music
    private JPanel cardPanel;
    private DLList<String> hand = new DLList<>();
    private JPanel deckPanel;
    private String currentCardOnDeck;
    private String currentSuit;
    private boolean myTurn = false;
    
    // Music components
    private Clip gameplayMusicClip;
    private Clip endGameMusicClip;
    private boolean musicEnabled = true;
    private boolean gameplayMusicPlaying = false;

    public Client() {
        try {
            socket = new Socket("192.168.0.239", 4414);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            username = JOptionPane.showInputDialog("Enter your username:");
            if (username == null || username.trim().isEmpty()) {
                System.exit(0);
            }
            out.println(username);

            // Initialize music
            initializeMusic();
            
            setupGUI();

            new Thread(new ServerListener()).start();
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Unable to connect to server.");
            System.exit(0);
        }
    }

    private void initializeMusic() {
        try {
            // Initialize gameplay music with a simple generated tone
            gameplayMusicClip = generateGameplayMusic();
            
            // Initialize end game music with a different tone
            endGameMusicClip = generateEndGameMusic();
            
        } catch (Exception e) {
            musicEnabled = false;
        }
    }

    private Clip generateGameplayMusic() throws LineUnavailableException {
        int sampleRate = 44100;
        int duration = 16; // seconds
        byte[] buffer = new byte[sampleRate * duration * 2];

        // Gentle, slow, original melody inspired by 'Unchained Melody' style (C major)
        double[] melody = {
            261.63, 293.66, 329.63, 349.23, 392.00, 349.23, 329.63, 293.66, // C D E F G F E D
            261.63, 329.63, 392.00, 440.00, 392.00, 349.23, 329.63, 293.66, // C E G A G F E D
            261.63, 293.66, 329.63, 349.23, 392.00, 349.23, 329.63, 293.66, // repeat
            261.63, 0, 261.63, 0, 261.63, 0, 261.63, 0 // C (rests for gentle effect)
        };

        double noteDuration = (double) duration / melody.length;
        int samplesPerNote = (int) (sampleRate * noteDuration);

        int bufferIndex = 0;
        for (int noteIndex = 0; noteIndex < melody.length; noteIndex++) {
            double frequency = melody[noteIndex];
            for (int i = 0; i < samplesPerNote && bufferIndex < buffer.length - 1; i++) {
                double t = (double) i / sampleRate;
                double amplitude = 0.18;

                // ADSR envelope (attack, decay, sustain, release)
                double env;
                double attack = 0.08, decay = 0.12, release = 0.18;
                double pos = (double) i / samplesPerNote;
                if (pos < attack) env = pos / attack;
                else if (pos < attack + decay) env = 1.0 - (pos - attack) / decay * 0.3;
                else if (pos < 1.0 - release) env = 0.7;
                else env = 0.7 * (1.0 - (pos - (1.0 - release)) / release);

                double wave = 0;
                if (frequency > 0) {
                    // Sine wave with gentle harmonics
                    wave += amplitude * env * Math.sin(2 * Math.PI * frequency * t);
                    wave += 0.10 * amplitude * env * Math.sin(2 * Math.PI * frequency * 2 * t);
                }
                short sample = (short) (wave * 32767);
                buffer[bufferIndex++] = (byte) (sample & 0xFF);
                buffer[bufferIndex++] = (byte) ((sample >> 8) & 0xFF);
            }
        }

        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(Clip.class, format);
        Clip clip = (Clip) AudioSystem.getLine(info);
        clip.open(format, buffer, 0, bufferIndex);
        return clip;
    }

    private Clip generateEndGameMusic() throws LineUnavailableException {
        int sampleRate = 44100;
        int duration = 4; // 4 seconds
        byte[] buffer = new byte[sampleRate * duration * 2];

        int bufferIndex = 0;

        // 1. Rising glissando (slot machine effect)
        double startFreq = 400, endFreq = 1800;
        int glissSamples = sampleRate * 2; // 2 seconds
        for (int i = 0; i < glissSamples && bufferIndex < buffer.length - 1; i++) {
            double t = (double) i / sampleRate;
            double freq = startFreq + (endFreq - startFreq) * ((double) i / glissSamples);
            double env = Math.min(1.0, t / 0.2); // Quick attack
            double wave = 0.35 * env * Math.sin(2 * Math.PI * freq * t);
            short sample = (short) (wave * 32767);
            buffer[bufferIndex++] = (byte) (sample & 0xFF);
            buffer[bufferIndex++] = (byte) ((sample >> 8) & 0xFF);
        }

        // 2. Bell-like tones (winning chimes)
        double[] bellFreqs = { 1318.5, 1567.98, 1760.00, 2093.00 }; // E6, G6, A6, C7
        int bellSamples = sampleRate / 2; // 0.5s per bell
        for (double freq : bellFreqs) {
            for (int i = 0; i < bellSamples && bufferIndex < buffer.length - 1; i++) {
                double t = (double) i / sampleRate;
                double env = Math.exp(-3 * t); // Fast decay
                double wave = 0.5 * env * Math.sin(2 * Math.PI * freq * t);
                // Add some overtones for bell effect
                wave += 0.2 * env * Math.sin(2 * Math.PI * freq * 2 * t);
                wave += 0.1 * env * Math.sin(2 * Math.PI * freq * 3 * t);
                short sample = (short) (wave * 32767);
                buffer[bufferIndex++] = (byte) (sample & 0xFF);
                buffer[bufferIndex++] = (byte) ((sample >> 8) & 0xFF);
            }
        }

        // 3. Final celebratory chord (C major)
        double[] chordFreqs = { 523.25, 659.25, 783.99 }; // C5, E5, G5
        int chordSamples = sampleRate / 2; // 0.5s
        for (int i = 0; i < chordSamples && bufferIndex < buffer.length - 1; i++) {
            double t = (double) i / sampleRate;
            double env = Math.exp(-2 * t); // Decay
            double wave = 0;
            for (double freq : chordFreqs) {
                wave += (0.25 * env * Math.sin(2 * Math.PI * freq * t));
            }
            short sample = (short) (wave * 32767);
            buffer[bufferIndex++] = (byte) (sample & 0xFF);
            buffer[bufferIndex++] = (byte) ((sample >> 8) & 0xFF);
        }

        // Fill the rest with silence
        while (bufferIndex < buffer.length) {
            buffer[bufferIndex++] = 0;
            buffer[bufferIndex++] = 0;
        }

        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(Clip.class, format);
        Clip clip = (Clip) AudioSystem.getLine(info);
        clip.open(format, buffer, 0, buffer.length);
        return clip;
    }

    private void playGameplayMusic() {
        if (!musicEnabled || gameplayMusicClip == null || gameplayMusicPlaying) return;
        
        try {
            // Set volume for background music (quieter than victory music)
            if (gameplayMusicClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gainControl = (FloatControl) gameplayMusicClip.getControl(FloatControl.Type.MASTER_GAIN);
                gainControl.setValue(gainControl.getMaximum() * 0.3f); // 30% of max volume
            }
            
            gameplayMusicClip.setFramePosition(0);
            gameplayMusicClip.loop(Clip.LOOP_CONTINUOUSLY);
            gameplayMusicPlaying = true;
            
            SwingUtilities.invokeLater(() -> {
                
            });
        } catch (Exception e) {
        }
    }

    private void stopGameplayMusic() {
        if (gameplayMusicClip != null && gameplayMusicPlaying) {
            gameplayMusicClip.stop();
            gameplayMusicPlaying = false;
        }
    }

    private void playEndGameMusic() {
        if (!musicEnabled || endGameMusicClip == null) return;
        
        try {
            stopGameplayMusic(); // Stop background music first
            
            // Make sure the clip is stopped and reset
            if (endGameMusicClip.isRunning()) {
                endGameMusicClip.stop();
            }
            endGameMusicClip.setFramePosition(0);
            
            // Set volume to maximum for victory music
            if (endGameMusicClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gainControl = (FloatControl) endGameMusicClip.getControl(FloatControl.Type.MASTER_GAIN);
                gainControl.setValue(gainControl.getMaximum() * 0.8f); // 80% of max volume
            }
            
            endGameMusicClip.start();
            
            // Also show a visual indicator
            SwingUtilities.invokeLater(() -> {
                
            });
            
        } catch (Exception e) {
        }
    }

    private void toggleMusic() {
        musicEnabled = !musicEnabled;
        if (!musicEnabled) {
            stopGameplayMusic();
            if (endGameMusicClip != null) {
                endGameMusicClip.stop();
            }
            musicToggleButton.setText("Music: OFF");
        } else {
            musicToggleButton.setText("Music: ON");
            // Restart gameplay music if game is in progress
            if (gameplayMusicPlaying) {
                playGameplayMusic();
            }
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
        startGameButton = new JButton("Start Game");
        musicToggleButton = new JButton("Music: ON"); // Initialize music toggle button

        // Card panel for graphical card display
        cardPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // Draw green felt background
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 100, 0)); // Casino green
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 40, 40);
                // Optional: Draw a gold border
                g2.setColor(new Color(218, 165, 32)); // Gold
                g2.setStroke(new BasicStroke(6f));
                g2.drawRoundRect(3, 3, getWidth()-6, getHeight()-6, 40, 40);
            }
        };
        cardPanel.setOpaque(false);
        cardPanel.setLayout(new FlowLayout());

        // Deck panel to display the current card on the discard pile
        deckPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (currentCardOnDeck != null) {
                    g.setColor(Color.WHITE);
                    g.fillRoundRect(10, 10, 80, 120, 15, 15);
                    g.setColor(Color.BLACK);
                    g.drawRoundRect(10, 10, 80, 120, 15, 15);

                    String display = getCardSymbol(currentCardOnDeck);
                    String suit = getCardSuit(currentCardOnDeck);
                    if (suit != null && (suit.equals("Hearts") || suit.equals("Diamonds"))) {
                        g.setColor(Color.RED);
                    } else {
                        g.setColor(Color.BLACK);
                    }
                    g.setFont(new Font("SansSerif", Font.BOLD, 32));
                    g.drawString(display, 25, 80);

                    if (currentSuit != null && currentCardOnDeck.startsWith("8")) {
                        g.setColor(Color.BLUE);
                        g.setFont(new Font("SansSerif", Font.PLAIN, 24));
                        g.drawString(getSuitSymbol(currentSuit), 55, 110);
                    }
                }
            }
        };
        deckPanel.setPreferredSize(new Dimension(100, 140));

        // Add components to the frame
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BorderLayout());
        bottomPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        JPanel inputPanel = new JPanel();
        inputPanel.add(inputField);
        inputPanel.add(drawButton);
        inputPanel.add(startGameButton);
        inputPanel.add(musicToggleButton); // Add music toggle button
        bottomPanel.add(inputPanel, BorderLayout.SOUTH);

        JPanel centerPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // Draw green felt background for the whole center panel
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 100, 0));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        centerPanel.setOpaque(false);
        centerPanel.add(cardPanel, BorderLayout.CENTER);
        centerPanel.add(deckPanel, BorderLayout.SOUTH);

        frame.add(centerPanel, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // Add action listeners
        inputField.addActionListener(e -> sendMessage());
        drawButton.addActionListener(e -> drawCard());
        startGameButton.addActionListener(e -> showStartMenu());
        musicToggleButton.addActionListener(e -> toggleMusic()); // Add music toggle listener

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setVisible(true);
    }

    private void showStartMenu() {
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
            out.println("START_GAME");
            out.flush();
        }
    }

    private void updateCardPanel() {
        cardPanel.removeAll();
        for (String card : hand) {
            JPanel cardGraphic = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    g.setColor(Color.WHITE);
                    g.fillRoundRect(10, 10, 80, 120, 15, 15);
                    g.setColor(Color.BLACK);
                    g.drawRoundRect(10, 10, 80, 120, 15, 15);
                    String display = getCardSymbol(card);
                    String suit = getCardSuit(card);
                    if (suit != null && (suit.equals("Hearts") || suit.equals("Diamonds"))) {
                        g.setColor(Color.RED);
                    } else {
                        g.setColor(Color.BLACK);
                    }
                    g.setFont(new Font("SansSerif", Font.BOLD, 32));
                    g.drawString(display, 25, 80);
                }
            };
            cardGraphic.setPreferredSize(new Dimension(100, 140));
            cardGraphic.setToolTipText("Click to play this card: " + getCardSymbol(card));
            cardGraphic.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (!myTurn) {
                        JOptionPane.showMessageDialog(frame, "It's not your turn!", "Wait", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    if (isValidPlay(card)) {
                        playCard(card);
                        myTurn = false;
                    } else {
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
            cardPanel.add(cardGraphic);
        }
        cardPanel.revalidate();
        cardPanel.repaint();
    }

    private String getCardSuit(String card) {
        if (card != null && card.contains(" of ")) {
            return card.split(" of ")[1].trim();
        }
        return null;
    }
    
    private String getCardRank(String card) {
        if (card != null && card.contains(" of ")) {
            return card.split(" of ")[0].trim();
        }
        return null;
    }

    private String getCardSymbol(String card) {
        if (card == null || !card.contains(" of ")) return card;
        String rank = getCardRank(card);
        String suit = getCardSuit(card);
        String symbol = "";
        if (suit != null) {
            switch (suit) {
                case "Hearts": symbol = "\u2665"; break; // ♥
                case "Diamonds": symbol = "\u2666"; break; // ♦
                case "Clubs": symbol = "\u2663"; break; // ♣
                case "Spades": symbol = "\u2660"; break; // ♠
                default: symbol = suit;
            }
        }
        // Use only first letter for face cards (J, Q, K, A), else number
        if (rank.equals("Jack")) rank = "J";
        else if (rank.equals("Queen")) rank = "Q";
        else if (rank.equals("King")) rank = "K";
        else if (rank.equals("Ace")) rank = "A";
        return rank + symbol;
    }

    private String getSuitSymbol(String suit) {
        if (suit == null) return "";
        switch (suit) {
            case "Hearts": return "\u2665"; // ♥
            case "Diamonds": return "\u2666"; // ♦
            case "Clubs": return "\u2663"; // ♣
            case "Spades": return "\u2660"; // ♠
            default: return suit;
        }
    }

    private boolean isValidPlay(String card) {
        if (currentCardOnDeck == null || currentCardOnDeck.isEmpty()) {
            return false;
        }
        try {
            String playRank = getCardRank(card);
            String playSuit = getCardSuit(card);
            String currentRank = getCardRank(currentCardOnDeck);
            
            String effectiveSuit = currentSuit != null ? currentSuit : getCardSuit(currentCardOnDeck);
            
            boolean isEight = playRank.equals("8");
            boolean ranksMatch = playRank.equals(currentRank);
            boolean suitsMatch = playSuit.equals(effectiveSuit);
            
            boolean isValid = isEight || ranksMatch || suitsMatch;
            return isValid;
            
        } catch (Exception e) {
            return false;
        }
    }

    private void playCard(String card) {
        hand.remove(card);
        updateCardPanel();
        
        out.println("PLAY:" + card);
        out.flush();
        
        if (card.startsWith("8")) {
        }
        
        updateDeckCard(card);
        myTurn = false;
    }

    private void drawCard() {
        if (!myTurn) {
            JOptionPane.showMessageDialog(frame, "It's not your turn!", "Wait", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        out.println("DRAW");
        out.flush();
    }

    private void updateDeckCard(String card) {
        currentCardOnDeck = card;
        
        if (!card.startsWith("8")) {
            currentSuit = null;
        }
        
        deckPanel.repaint();
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (!message.isEmpty()) {
            if (message.equalsIgnoreCase("restart")) {
                out.println("RESTART");
                out.flush();
            } else {
                out.println("CHAT:" + message);
                out.flush();
            }
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
                        hand.clear();
                        String[] cards = message.substring(5).split(",");
                        for (String card : cards) {
                            if (!card.trim().isEmpty()) {
                                hand.add(card.trim());
                            }
                        }
                        SwingUtilities.invokeLater(() -> updateCardPanel());
                        
                    } else if (message.startsWith("DRAWN_CARD:")) {
                        String card = message.substring(11).trim();
                        hand.add(card);
                        chatArea.append("You drew: " + card + "\n");
                        SwingUtilities.invokeLater(() -> updateCardPanel());
                        
                    } else if (message.startsWith("CURRENT_CARD:")) {
                        String card = message.substring(13).trim();
                        currentCardOnDeck = card;
                        SwingUtilities.invokeLater(() -> deckPanel.repaint());
                        chatArea.append("Current card: " + card + "\n");
                        
                    } else if (message.equals("YOUR_TURN")) {
                        chatArea.append("It's your turn! Click a card to play or press 'Draw Card'.\n");
                        myTurn = true;
                        
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
                            currentSuit = suit;
                            out.println("SUIT:" + suit);
                            out.flush();
                            chatArea.append("You chose suit: " + getSuitSymbol(suit) + "\n");
                        } else {
                            currentSuit = "Hearts";
                            out.println("SUIT:Hearts");
                            out.flush();
                            chatArea.append("You chose suit: " + getSuitSymbol("Hearts") + "\n");
                        }
                        deckPanel.repaint();
                        
                    } else if (message.startsWith("CHOSEN_SUIT:")) {
                        String suit = message.substring(12).trim();
                        currentSuit = suit;
                        chatArea.append("Current suit changed to: " + getSuitSymbol(currentSuit) + "\n");
                        deckPanel.repaint();
                        
                    } else if (message.startsWith("GAME_OVER:Server")) {
                        SwingUtilities.invokeLater(() -> {
                            stopGameplayMusic();
                            JOptionPane.showMessageDialog(frame, "Game ended by server.\nType 'restart' to play again.", "Game Over", JOptionPane.INFORMATION_MESSAGE);
                        });
                        myTurn = false;
                        
                    } else if (message.equals("Game is starting...")) {
                        // Start gameplay music when game begins
                        SwingUtilities.invokeLater(() -> playGameplayMusic());
                        chatArea.append(message + "\n");
                        
                    } else {
                        chatArea.append(message + "\n");
                        
                        // End game screen if someone wins
                        if (message.contains("wins the game!")) {
                            String winner = message.split(" ")[0];
                            SwingUtilities.invokeLater(() -> {
                                playEndGameMusic(); // Play victory music
                                JOptionPane.showMessageDialog(frame, winner + " wins the game!\nType 'restart' to play again.", "Game Over", JOptionPane.INFORMATION_MESSAGE);
                            });
                        }
                        
                        // Check if a message contains suit selection from another player
                        if (message.contains(" chose suit: ")) {
                            String suit = message.substring(message.indexOf("suit: ") + 6).trim();
                            currentSuit = suit;
                            deckPanel.repaint();
                        }
                    }
                    chatArea.setCaretPosition(chatArea.getDocument().getLength());
                }
            } catch (IOException e) {
                chatArea.append("Connection to server lost: " + e.getMessage() + "\n");
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Client());
    }
}