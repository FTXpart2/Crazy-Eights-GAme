import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.HashSet;
import java.util.List; 
import java.util.Arrays;
import java.util.Random;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Stack;
import javax.swing.DefaultListModel; // For managing the client list
import java.util.ArrayList;

public class Server3 {
    private static CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private static volatile boolean isRunning = true; // Flag to control the server loop
    private static int currentPlayerIndex = -1;
    private static String currentAnimal = null; // Track the current animal being described
    private static final Map<String, Integer> userScores = new HashMap<>(); // Track user scores
    private static final Set<String> readyPlayers = new HashSet<>(); // Track players who pressed "Start"
    private static Stack<String> deck = new Stack<>();
    private static Stack<String> discardPile = new Stack<>();
    private static String currentSuit = null; // Track the current suit in play
    private static boolean gameStarted = false;

    public static void main(String[] args) {
        int portNumber = 4414;

        // Server UI components
        JFrame frame = new JFrame("Server Chat");
        JTextArea chatArea = new JTextArea(20, 50);
        JTextField inputField = new JTextField(40);
        JButton sendButton = new JButton("Send");
        JButton startGameButton = new JButton("Start Game"); // Add "Start Game" button
        JList<String> clientList = new JList<>(new DefaultListModel<>()); // List of connected clients
        JPanel panel = new JPanel();

        chatArea.setEditable(false);
        panel.add(inputField);
        panel.add(sendButton);
        panel.add(startGameButton); // Add "Start Game" button to panel

        frame.setLayout(new BorderLayout());
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(clientList), BorderLayout.EAST); // Add client list to the right
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        // ActionListener for the "Send" button
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String message = inputField.getText();
                if (!message.isEmpty()) {
                    chatArea.append("Server: " + message + "\n");
                    broadcastToAllClients("Server: " + message);
                    inputField.setText("");
                }
            }
        });

        // ActionListener for the "Start Game" button
        startGameButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startGame();
            }
        });

        try (ServerSocket serverSocket = new ServerSocket(portNumber)) {
            chatArea.append("Server started. Waiting for clients on port " + portNumber + "...\n");
            chatArea.append("Server is now listening for connections on IP: " + InetAddress.getLocalHost().getHostAddress() + "\n");

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    chatArea.append("New client connected from " + clientSocket.getInetAddress() + "\n");
                    ClientHandler clientHandler = new ClientHandler(clientSocket, chatArea, clientList);
                    clients.add(clientHandler);
                    new Thread(clientHandler).start();
                } catch (IOException e) {
                    if (isRunning) {
                        chatArea.append("Error accepting client connection: " + e.getMessage() + "\n");
                    }
                }
            }
        } catch (IOException e) {
            chatArea.append("Exception caught when trying to listen on port " + portNumber + " or listening for a connection\n");
            chatArea.append(e.getMessage() + "\n");
        } finally {
            isRunning = false;
        }
    }

    private static synchronized void startGame() {
        if (readyPlayers.size() < clients.size()) {
            broadcastToAllClients("Waiting for all players to press 'Start'...");
            return;
        }

        // Clear the chat area for all clients
        broadcastToAllClients("CLEAR_CHAT");
        broadcastToAllClients("Game is starting...");

        initializeDeck();
        dealCards();
        discardPile.push(deck.pop());
        currentSuit = getCardSuit(discardPile.peek());
        broadcastToAllClients("First card: " + discardPile.peek());
        gameStarted = true;
        nextTurn();
    }

    private static void initializeDeck() {
        String[] suits = {"Hearts", "Diamonds", "Clubs", "Spades"};
        String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "Jack", "Queen", "King", "Ace"};
        for (String suit : suits) {
            for (String rank : ranks) {
                deck.push(rank + " of " + suit);
            }
        }
        Collections.shuffle(deck);
    }

    private static void dealCards() {
        for (ClientHandler client : clients) {
            for (int i = 0; i < 5; i++) {
                client.addCardToHand(deck.pop());
            }
            client.sendHand();
        }
    }

    private static void nextTurn() {
        currentPlayerIndex = (currentPlayerIndex + 1) % clients.size();
        ClientHandler currentPlayer = clients.get(currentPlayerIndex);
        broadcastToAllClients("It's " + currentPlayer.getUsername() + "'s turn.");
        currentPlayer.sendMessage("YOUR_TURN");
        currentPlayer.sendHand(); // Send the player's hand when it's their turn
    }

    private static String getCardSuit(String card) {
        return card.split(" of ")[1];
    }

    private static void handlePlayerMove(ClientHandler player, String card) {
        if (card.equals("DRAW")) {
            if (!deck.isEmpty()) {
                String drawnCard = deck.pop();
                player.addCardToHand(drawnCard);
                player.sendMessage("DRAWN_CARD:" + drawnCard);
            } else {
                player.sendMessage("NO_CARDS_LEFT: The deck is empty.");
            }
            return;
        }

        if (!player.getHand().contains(card)) {
            player.sendMessage("Invalid move: You don't have that card.");
            return;
        }

        String cardSuit = getCardSuit(card);
        if (cardSuit.equals(currentSuit) || card.startsWith("8")) {
            player.removeCardFromHand(card);
            discardPile.push(card);
            currentSuit = card.startsWith("8") ? player.chooseSuit() : cardSuit;
            broadcastToAllClients(player.getUsername() + " played: " + card);
            broadcastLineup();
            if (player.getHand().isEmpty()) {
                broadcastToAllClients(player.getUsername() + " wins the game!");
                gameStarted = false;
                return;
            }
            nextTurn();
        } else {
            player.sendMessage("Invalid move: Card does not match the current suit.");
        }
    }

    private static void broadcastLineup() {
        StringBuilder lineup = new StringBuilder("Current lineup:\n");
        for (ClientHandler client : clients) {
            lineup.append(client.getUsername()).append(": ").append(client.getHand().size()).append(" cards\n");
        }
        broadcastToAllClients(lineup.toString());
    }

    private static void displayFinalScores() {
        broadcastToAllClients("Final Scores:");
        for (Map.Entry<String, Integer> entry : userScores.entrySet()) {
            broadcastToAllClients(entry.getKey() + ": " + entry.getValue() + " points");
        }
    }

    private static void broadcastToAllClients(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message); 
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private JTextArea chatArea;
        private JList<String> clientList;
        private PrintWriter out;
        private BufferedReader in;
        private String username;
        private List<String> hand = new ArrayList<>();

        public ClientHandler(Socket socket, JTextArea chatArea, JList<String> clientList) {
            this.socket = socket;
            this.chatArea = chatArea;
            this.clientList = clientList;
            try {
                this.out = new PrintWriter(socket.getOutputStream(), true);
                this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                chatArea.append("Error setting up client streams: " + e.getMessage() + "\n");
            }
        }

        @Override
        public void run() {
            try {
                username = in.readLine();
                userScores.putIfAbsent(username, 0);
                broadcastToAllClients(username + " has joined the game!");
                updateClientList();

                // Send game rules to the newly joined client
                sendMessage("""
                    Welcome to Crazy Eights!
                    Rules:
                    1. Each player is dealt 5 cards.
                    2. The goal is to be the first player to get rid of all your cards.
                    3. On your turn, you must play a card that matches the suit or rank of the top card on the discard pile.
                    4. If you cannot play, you must draw a card from the deck.
                    5. Eights are wild and can be played at any time. The player who plays an eight chooses the next suit.
                    6. The game continues until one player has no cards left.
                    """);

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.equals("START_GAME")) {
                        synchronized (readyPlayers) {
                            readyPlayers.add(username);
                        }
                        broadcastToAllClients(username + " is ready to start!");
                        if (!gameStarted) startGame();
                    } else if (message.startsWith("PLAY:")) {
                        String card = message.substring(5);
                        handlePlayerMove(this, card);
                    } else if (message.equals("DRAW")) {
                        handlePlayerMove(this, "DRAW");
                    } else if (message.startsWith("CHAT:")) {
                        String chatMessage = message.substring(5);
                        broadcastToAllClients(username + ": " + chatMessage);
                    }
                }
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    chatArea.append("Error reading from client (" + socket.getInetAddress() + "): " + e.getMessage() + "\n");
                }
            } finally {
                cleanup();
            }
        }

        public String getUsername() {
            return username;
        }

        public void sendMessage(String message) {
            try {
                out.println(message); 
            } catch (Exception e) {
                chatArea.append("Error sending message to client: " + e.getMessage() + "\n");
            }
        }

        public void addCardToHand(String card) {
            hand.add(card);
        }

        public void removeCardFromHand(String card) {
            hand.remove(card);
        }

        public List<String> getHand() {
            return hand;
        }

        public void sendHand() {
            sendMessage("HAND:" + String.join(",", hand));
        }

        public void sendLineup() {
            broadcastLineup();
        }

        public String chooseSuit() {
            sendMessage("CHOOSE_SUIT");
            try {
                return in.readLine(); // Wait for the player to choose a suit
            } catch (IOException e) {
                return "Hearts"; // Default suit in case of error
            }
        }

        private void updateClientList() {
            DefaultListModel<String> model = (DefaultListModel<String>) clientList.getModel();
            model.clear();
            for (ClientHandler client : clients) {
                model.addElement(client.getUsername());
            }
        }

        private void cleanup() {
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                chatArea.append("Error closing client socket: " + e.getMessage() + "\n");
            } finally {
                clients.remove(this);
                updateClientList();
                chatArea.append("Client disconnected.\n");
            }
        }
    }
}
