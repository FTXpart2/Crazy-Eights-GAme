import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import javax.swing.DefaultListModel;

public class Server {
    static class UserScore {
        String username;
        int score;
        UserScore(String username, int score) {
            this.username = username;
            this.score = score;
        }
    }

    private static DLList<ClientHandler> clients = new DLList<>();
    private static volatile boolean isRunning = true;
    private static int currentPlayerIndex = -1;
    private static final DLList<UserScore> userScores = new DLList<>();
    private static final DLList<String> readyPlayers = new DLList<>();
    private static DLList<String> deck = new DLList<>();
    private static DLList<String> discardPile = new DLList<>();
    private static String currentSuit = null;
    private static boolean gameStarted = false;
    private static ClientHandler waitingForSuitPlayer = null;
    private static String waitingForSuitCard = null;

    private static void push(DLList<String> stack, String value) { stack.add(value); }
    private static String pop(DLList<String> stack) { String v = stack.get(stack.size()-1); stack.remove(v); return v; }
    private static String peek(DLList<String> stack) { return stack.get(stack.size()-1); }
    private static void clear(DLList<?> list) { while (!list.isEmpty()) list.remove(0); }

    private static boolean readyPlayersContains(String name) { return readyPlayers.contains(name); }
    private static void readyPlayersAdd(String name) { if (!readyPlayers.contains(name)) readyPlayers.add(name); }
    private static void readyPlayersClear() { clear(readyPlayers); }

    private static void userScoresPutIfAbsent(String username, int score) {
        for (UserScore us : userScores) {
            if (us.username.equals(username)) return;
        }
        userScores.add(new UserScore(username, score));
    }
    private static int userScoresGet(String username) {
        for (UserScore us : userScores) {
            if (us.username.equals(username)) return us.score;
        }
        return 0;
    }
    private static void userScoresSet(String username, int score) {
        for (UserScore us : userScores) {
            if (us.username.equals(username)) { us.score = score; return; }
        }
        userScores.add(new UserScore(username, score));
    }
    private static Iterable<UserScore> userScoresEntrySet() { return userScores; }

    private static void shuffle(DLList<String> list) {
        java.util.Random rand = new java.util.Random();
        for (int i = list.size() - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            String temp = list.get(i);
            list.remove(temp);
            list.add(j, temp);
        }
    }

    public static void main(String[] args) {
        int portNumber = 4414;
        JFrame frame = new JFrame("Server Chat");
        JTextArea chatArea = new JTextArea(20, 50);
        JTextField inputField = new JTextField(40);
        JButton sendButton = new JButton("Send");
        JButton startGameButton = new JButton("Start Game");
        JList<String> clientList = new JList<>(new DefaultListModel<>());
        JPanel panel = new JPanel();

        chatArea.setEditable(false);
        panel.add(inputField);
        panel.add(sendButton);
        panel.add(startGameButton);
        JLabel infoLabel = new JLabel("Type 'y' in the chat to end the game.");
        panel.add(infoLabel);

        frame.setLayout(new BorderLayout());
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(clientList), BorderLayout.EAST);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String message = inputField.getText();
                if (!message.isEmpty()) {
                    if (message.trim().equalsIgnoreCase("y")) {
                        broadcastToAllClients("GAME_OVER:Server");
                        gameStarted = false;
                        waitingForSuitPlayer = null;
                        waitingForSuitCard = null;
                        chatArea.append("Game ended by server.\n");
                    } else {
                        chatArea.append("Server: " + message + "\n");
                        broadcastToAllClients("Server: " + message);
                    }
                    inputField.setText("");
                }
            }
        });

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
        broadcastToAllClients("CLEAR_CHAT");
        broadcastToAllClients("Game is starting...");
        initializeDeck();
        dealCards();
        push(discardPile, pop(deck));
        currentSuit = getCardSuit(peek(discardPile));
        broadcastToAllClients("First card: " + peek(discardPile));
        broadcastToAllClients("CURRENT_CARD:" + peek(discardPile));
        gameStarted = true;
        nextTurn();
    }

    private static synchronized void restartGame() {
        clear(deck);
        clear(discardPile);
        currentSuit = null;
        gameStarted = false;
        waitingForSuitPlayer = null;
        waitingForSuitCard = null;
        readyPlayersClear();
        for (ClientHandler client : clients) {
            client.hand.clear();
        }
        broadcastToAllClients("Game is restarting...");
        for (ClientHandler client : clients) {
            readyPlayersAdd(client.getUsername());
        }
        startGame();
    }

    private static void initializeDeck() {
        String[] suits = {"Hearts", "Diamonds", "Clubs", "Spades"};
        String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "Jack", "Queen", "King", "Ace"};
        for (String suit : suits) {
            for (String rank : ranks) {
                deck.add(rank + " of " + suit);
            }
        }
        shuffle(deck);
    }

    private static void dealCards() {
        for (ClientHandler client : clients) {
            for (int i = 0; i < 5; i++) {
                client.addCardToHand(pop(deck));
            }
            client.sendHand();
        }
    }

    private static void nextTurn() {
        currentPlayerIndex = (currentPlayerIndex + 1) % clients.size();
        ClientHandler currentPlayer = clients.get(currentPlayerIndex);
        currentPlayer.sendMessage("CURRENT_CARD:" + peek(discardPile));
        broadcastToAllClients("It's " + currentPlayer.getUsername() + "'s turn.");
        currentPlayer.sendMessage("YOUR_TURN");
        currentPlayer.sendHand();
    }

    private static String getCardSuit(String card) {
        return card.split(" of ")[1];
    }

    private static void handlePlayerMove(ClientHandler player, String card) {
        if (waitingForSuitPlayer != null) {
            player.sendMessage("Please choose a suit before making another move.");
            return;
        }
        if (card.equals("DRAW")) {
            if (!deck.isEmpty()) {
                String drawnCard = pop(deck);
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
        String currentCardRank = peek(discardPile).split(" of ")[0];
        if (cardSuit.equals(currentSuit) || card.startsWith("8") || card.startsWith(currentCardRank)) {
            player.removeCardFromHand(card);
            push(discardPile, card);
            if (card.startsWith("8")) {
                waitingForSuitPlayer = player;
                waitingForSuitCard = card;
                player.sendMessage("CHOOSE_SUIT");
                return;
            } else {
                currentSuit = cardSuit;
            }
            broadcastToAllClients(player.getUsername() + " played: " + card);
            broadcastToAllClients("CURRENT_CARD:" + card);
            if (player.getHand().isEmpty()) {
                broadcastToAllClients(player.getUsername() + " wins the game!");
                gameStarted = false;
                waitingForSuitPlayer = null;
                waitingForSuitCard = null;
                return;
            }
            nextTurn();
        } else {
            player.sendMessage("Invalid move: Card does not match the current suit or rank.");
        }
    }

    private static void displayFinalScores() {
        broadcastToAllClients("Final Scores:");
        for (UserScore entry : userScoresEntrySet()) {
            broadcastToAllClients(entry.username + ": " + entry.score + " points");
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
        private DLList<String> hand = new DLList<>();

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
                userScoresPutIfAbsent(username, 0);
                broadcastToAllClients(username + " has joined the game!");
                updateClientList();
                String message;
                while ((message = in.readLine()) != null) {
                    if (message.equals("START_GAME")) {
                        synchronized (readyPlayers) {
                            readyPlayersAdd(username);
                        }
                        broadcastToAllClients(username + " is ready to start!");
                        if (!gameStarted) startGame();
                    } else if (message.equalsIgnoreCase("RESTART")) {
                        broadcastToAllClients(username + " requested a restart!");
                        restartGame();
                    } else if (message.startsWith("PLAY:")) {
                        String card = message.substring(5);
                        handlePlayerMove(this, card);
                    } else if (message.equals("DRAW")) {
                        handlePlayerMove(this, "DRAW");
                    } else if (message.startsWith("CHAT:")) {
                        String chatMessage = message.substring(5);
                        broadcastToAllClients(username + ": " + chatMessage);
                    } else if (message.startsWith("SUIT:")) {
                        String suit = message.substring(5).trim();
                        if (waitingForSuitPlayer == this && waitingForSuitCard != null) {
                            currentSuit = suit;
                            broadcastToAllClients(username + " chose suit: " + suit);
                            broadcastToAllClients("CURRENT_CARD:" + waitingForSuitCard);
                            if (hand.isEmpty()) {
                                broadcastToAllClients(username + " wins the game!");
                                gameStarted = false;
                            } else {
                                nextTurn();
                            }
                            waitingForSuitPlayer = null;
                            waitingForSuitCard = null;
                        } else {
                            sendMessage("Not expecting a suit selection from you.");
                        }
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

        public DLList<String> getHand() {
            return hand;
        }

        public void sendHand() {
            StringBuilder sb = new StringBuilder();
            for (String c : hand) {
                if (sb.length() > 0) sb.append(",");
                sb.append(c);
            }
            sendMessage("HAND:" + sb.toString());
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
