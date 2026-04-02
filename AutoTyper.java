import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import javax.swing.*;
import java.awt.Robot;
import java.awt.AWTException;
import java.io.File;
import java.net.URL;
import java.util.Random;
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
        textArea = new HintTextArea("There is 3s delay. Use the 3s time to move your cursor to where you want your text autotyped.", 10, 30);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        // Add inner padding so text is not flush with edges
        textArea.setMargin(new Insets(10, 10, 10, 10));
        JScrollPane scrollPane = new JScrollPane(textArea);
        // Rounded edge for text field container
        scrollPane.setBorder(new LineBorder(new Color(170, 170, 170), 1, true));
        add(scrollPane, BorderLayout.CENTER);

        // Create the start button
        startButton = new JButton("Start(Ctrl+0)");
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

        // Create the stop button
        JButton stopButton = new JButton("Stop");
        stopButton.setBackground(Color.RED);
        stopButton.setForeground(Color.WHITE);
        stopButton.setOpaque(true);
        stopButton.setBorderPainted(false);
        stopButton.setBorder(new LineBorder(Color.BLACK, 1, true));
        stopButton.setPreferredSize(new Dimension(stopButton.getPreferredSize().width, preferredSize.height + 5));
        stopButton.addActionListener(e -> isTyping = false);

        // Put button in its own panel to keep space above it
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        buttonPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // Add Ctrl+0 hotkey to start typing
        setupHotkey();

        // Set default window size and center it
        setSize(500, 550);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation(screenSize.width - getWidth(), 0);
    }

    private void setAppIcon() {
        // First try loading icon from inside the jar (works after packaging).
        URL resourceUrl = AutoTyper.class.getResource("/Image_asset/autot_icon.ico");
        if (resourceUrl != null) {
            Image iconImage = Toolkit.getDefaultToolkit().getImage(resourceUrl);
            setIconImage(iconImage);
            return;
        }

        // Fallback: load from project folders (when running from source).
        File iconFile1 = new File("..\\Image_asset\\autot_icon.ico");
        if (iconFile1.exists()) {
            Image iconImage = Toolkit.getDefaultToolkit().getImage(iconFile1.getPath());
            setIconImage(iconImage);
            return;
        }

        File iconFile2 = new File("Image_asset\\autot_icon.ico");
        if (iconFile2.exists()) {
            Image iconImage = Toolkit.getDefaultToolkit().getImage(iconFile2.getPath());
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

        // ── Human-typing model constants ──────────────────────────────────────
        // Target speed: ~150 WPM  →  avg inter-key interval ≈ 80 ms
        // (1 word = 5 chars, 150 WPM → 750 chars/min → 80 ms/char)
        // Fast typists have tighter, more consistent rhythm → smaller jitter.
        private static final double BASE_DELAY_MS      =  70.0; // median delay (ms)
        private static final double JITTER_STD_MS      =  20.0; // Gaussian std-dev (ms) — tighter for fast typists
        private static final double MIN_DELAY_MS        =  22.0; // floor so we never go robotic-fast

        // After a space or newline we pause a bit longer (word-boundary hesitation)
        private static final double WORD_PAUSE_EXTRA_MS   = 30.0;  // extra ms added after space/newline
        private static final double WORD_PAUSE_SKIP_PROB   = 0.35;  // 35 % chance to skip word pause ("in the zone")

        // After heavy punctuation (., !, ?, ;, :) we pause even longer
        private static final double PUNCT_PAUSE_EXTRA_MS   = 65.0;
        private static final double PUNCT_PAUSE_SKIP_PROB  = 0.20;  // 20 % chance to skip punct pause

        // Burst typing: fast typists enter "flow" more often.
        private static final double BURST_PROBABILITY   = 0.25; // 25 % chance to enter burst mode
        private static final int    BURST_MAX_CHARS     =  6;   // max chars in a burst streak
        private static final double BURST_SPEED_FACTOR  = 0.55; // burst delay = base × this factor

        // Rare hesitation: simulates a brief think/correction pause.
        private static final double HESITATION_PROBABILITY = 0.010; // ~1 % per character
        private static final int    HESITATION_MIN_MS      = 200;
        private static final int    HESITATION_MAX_MS      = 500;

        private final Random rng = new Random();

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

                char[] chars = text.toCharArray();
                int burstRemaining = 0; // how many chars left in current burst streak

                for (int i = 0; i < chars.length; i++) {
                    if (!isTyping) break; // Check for stop
                    char c = chars[i];

                    // ── Type the character ──────────────────────────────────
                    try {
                        typeCharacter(robot, c);
                    } catch (IllegalArgumentException ex) {
                        pasteChar(robot, c);
                    }

                    // ── Calculate human-like delay before next character ────

                    // 1. Base delay with Gaussian jitter
                    double delay = BASE_DELAY_MS + rng.nextGaussian() * JITTER_STD_MS;

                    // 2. Apply burst-mode speed-up
                    if (burstRemaining > 0) {
                        delay *= BURST_SPEED_FACTOR;
                        burstRemaining--;
                    } else if (rng.nextDouble() < BURST_PROBABILITY) {
                        // Enter a new burst streak
                        delay *= BURST_SPEED_FACTOR;
                        burstRemaining = 1 + rng.nextInt(BURST_MAX_CHARS);
                    }

                    // 3. Extra pause after word/sentence boundaries (randomly skippable)
                    if (c == ' ' || c == '\n' || c == '\r') {
                        if (rng.nextDouble() < WORD_PAUSE_SKIP_PROB) {
                            // "In the zone" — skip word pause, burst can carry across boundary
                        } else {
                            delay += WORD_PAUSE_EXTRA_MS + rng.nextDouble() * 40;
                            burstRemaining = 0; // pause breaks the burst flow
                        }
                    } else if (c == '.' || c == '!' || c == '?' || c == ';' || c == ':') {
                        if (rng.nextDouble() < PUNCT_PAUSE_SKIP_PROB) {
                            // Skip punct pause — rare but happens on familiar phrases
                        } else {
                            delay += PUNCT_PAUSE_EXTRA_MS + rng.nextDouble() * 80;
                            burstRemaining = 0;
                        }
                    }

                    // 4. Enforce a sensible floor
                    delay = Math.max(delay, MIN_DELAY_MS);

                    // 5. Rare hesitation pause (separate from per-char delay)
                    if (rng.nextDouble() < HESITATION_PROBABILITY) {
                        int hesitation = HESITATION_MIN_MS + rng.nextInt(HESITATION_MAX_MS - HESITATION_MIN_MS);
                        Thread.sleep(hesitation);
                    }

                    Thread.sleep((long) delay);
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
            // Handle common control characters explicitly.
            if (c == '\n' || c == '\r') {
                robot.keyPress(KeyEvent.VK_ENTER);
                robot.keyRelease(KeyEvent.VK_ENTER);
                return;
            }
            if (c == '\t') {
                robot.keyPress(KeyEvent.VK_TAB);
                robot.keyRelease(KeyEvent.VK_TAB);
                return;
            }
            if (c == ' ') {
                robot.keyPress(KeyEvent.VK_SPACE);
                robot.keyRelease(KeyEvent.VK_SPACE);
                return;
            }

            // Uppercase letters require shift, but many punctuation/symbols (like '(' and ')')
            // also require shift on a typical US keyboard.
            boolean needsShift = Character.isUpperCase(c) || requiresShiftForChar(c);

            // For letter mappings, KeyEvent expects a lowercase char when we press shift manually.
            char mappingChar = Character.isUpperCase(c) ? Character.toLowerCase(c) : c;

            int keyCode = KeyEvent.getExtendedKeyCodeForChar(mappingChar);
            if (keyCode == KeyEvent.VK_UNDEFINED) {
                // Fallback for characters that don't map to a valid keycode on this platform/key layout.
                pasteChar(robot, c);
                return;
            }

            if (needsShift) {
                robot.keyPress(KeyEvent.VK_SHIFT);
            }

            try {
                robot.keyPress(keyCode);
                robot.keyRelease(keyCode);
            } finally {
                if (needsShift) {
                    robot.keyRelease(KeyEvent.VK_SHIFT);
                }
            }
        }

        private boolean requiresShiftForChar(char c) {
            // Characters that usually require shift on the same physical key.
            // This specifically covers '(' and ')', plus other shifted symbols.
            switch (c) {
                case '!':
                case '@':
                case '#':
                case '$':
                case '%':
                case '^':
                case '&':
                case '*':
                case '(':
                case ')':
                case '_':
                case '+':
                case '{':
                case '}':
                case '|':
                case ':':
                case '"':
                case '<':
                case '>':
                case '?':
                    return true;
                default:
                    return false;
            }
        }

        private void pasteChar(Robot robot, char c) {
            try {
                Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
                StringSelection selection = new StringSelection(String.valueOf(c));
                clipboard.setContents(selection, selection);

                robot.keyPress(KeyEvent.VK_CONTROL);
                robot.keyPress(KeyEvent.VK_V);
                robot.keyRelease(KeyEvent.VK_V);
                robot.keyRelease(KeyEvent.VK_CONTROL);
            } catch (Exception ex) {
                // If paste also fails, we can't do much. Swallow to avoid stopping the entire typing task.
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