import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.awt.Robot;
import java.awt.AWTException;
import java.io.File;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

public class AutoTyper extends JFrame {
    private JTextArea textArea;
    private JButton startButton;
    private volatile boolean isTyping = false;

    public AutoTyper() {
        // Set up the main window
        setTitle("AutoTyper (by Sandesh)");
        setAppIcon();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        // Add outer margin so components are not glued to the frame edges
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(15, 15, 15, 15));

        // Create the text area for user input
        textArea = new HintTextArea("There is 3s delay", 10, 30);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        // Add inner padding so text is not flush with edges
        textArea.setMargin(new Insets(10, 10, 10, 10));
        JScrollPane scrollPane = new JScrollPane(textArea);
        // Rounded edge for text field container
        scrollPane.setBorder(new LineBorder(new Color(170, 170, 170), 1, true));
        add(scrollPane, BorderLayout.CENTER);

        // Create the start button
        startButton = new JButton("Start Typing (Or Ctrl+0)");
        startButton.setBackground(Color.BLACK);
        startButton.setForeground(Color.WHITE);
        startButton.setOpaque(true);
        startButton.setBorderPainted(false);
        // Slightly rounded edge for button
        startButton.setBorder(new LineBorder(Color.BLACK, 1, true));
        // Increase button height by 5px
        Dimension preferredSize = startButton.getPreferredSize();
        startButton.setPreferredSize(new Dimension(preferredSize.width, preferredSize.height + 5));
        startButton.addActionListener(new StartTypingListener());
        // Put button in its own panel to keep space above it
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        buttonPanel.add(startButton, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // Add Ctrl+0 hotkey to start typing
        setupHotkey();

        // Set default window size and center it
        setSize(600, 600);
        setLocationRelativeTo(null);
    }

    private void setAppIcon() {
        // Load icon from Image_asset folder (one level above this project folder)
        File iconFile = new File("..\\Image_asset\\autot_icon.ico");
        if (iconFile.exists()) {
            Image iconImage = Toolkit.getDefaultToolkit().getImage(iconFile.getPath());
            setIconImage(iconImage);
        }
    }

    private void setupHotkey() {
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getRootPane().getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK), "startTypingHotkey");
        actionMap.put("startTypingHotkey", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startTypingIfPossible();
            }
        });
    }

    private void startTypingIfPossible() {
        String text = textArea.getText().trim();
        if (text.isEmpty()) {
            // Do nothing if text area is empty
            return;
        }
        if (isTyping) {
            // Prevent multiple simultaneous typing triggers
            return;
        }
        isTyping = true;

        // Start typing in a separate thread to avoid freezing the UI
        new Thread(new TypingTask(text)).start();
    }

    private class StartTypingListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            startTypingIfPossible();
        }
    }

    private class TypingTask implements Runnable {
        private String text;

        public TypingTask(String text) {
            this.text = text;
        }

        @Override
        public void run() {
            try {
                // Wait 3 seconds to allow user to switch window
                Thread.sleep(3000);

                // Create Robot instance for simulating keyboard input
                Robot robot = new Robot();

                // Type each character with 20ms delay
                for (char c : text.toCharArray()) {
                    typeCharacter(robot, c);
                    Thread.sleep(20); // Fixed typing delay of 20ms per character
                }
            } catch (AWTException ex) {
                // Handle Robot creation error
                JOptionPane.showMessageDialog(AutoTyper.this, "Error creating Robot: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            } catch (InterruptedException ex) {
                // Handle thread interruption
                Thread.currentThread().interrupt();
            } finally {
                // Reset typing flag
                isTyping = false;
            }
        }

        private void typeCharacter(Robot robot, char c) {
            // Get the key code for the character (convert to lowercase for mapping)
            int keyCode = KeyEvent.getExtendedKeyCodeForChar(Character.toLowerCase(c));

            if (Character.isUpperCase(c)) {
                // Press shift for uppercase letters
                robot.keyPress(KeyEvent.VK_SHIFT);
                robot.keyPress(keyCode);
                robot.keyRelease(keyCode);
                robot.keyRelease(KeyEvent.VK_SHIFT);
            } else {
                // Press the key for lowercase letters and other characters
                robot.keyPress(keyCode);
                robot.keyRelease(keyCode);
            }
        }
    }

    public static void main(String[] args) {
        // Run the GUI on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            new AutoTyper().setVisible(true);
        });
    }

    private static class HintTextArea extends JTextArea {
        private final String hint;

        public HintTextArea(String hint, int rows, int columns) {
            super(rows, columns);
            this.hint = hint;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (getText().isEmpty()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(150, 150, 150));
                g2.setFont(getFont().deriveFont(Font.ITALIC));
                Insets insets = getInsets();
                FontMetrics fm = g2.getFontMetrics();
                int x = insets.left + 2;
                int y = insets.top + fm.getAscent();
                g2.drawString(hint, x, y);
                g2.dispose();
            }
        }
    }
}