package algobench.gui;

import algobench.gui.JavaSourceCompiler.CompileException;
import algobench.gui.JavaSourceCompiler.CompileResult;
import algobench.solution.ExternalProcessSolution;
import algobench.solution.JavaJarSolution;
import algobench.solution.Solution;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * 풀이 1개 입력 패널 — 유형(에디터 Java / 파일 / 외부 명령) 선택 + 입력 위젯 + 상태 표시.
 *
 * <p>Swing 컴포넌트 접근은 EDT에서만. 채점은 백그라운드에서 일어나므로
 * {@link #toSpec(String)}으로 EDT에서 값을 스냅샷한 뒤, {@link Spec#buildSolution()}을
 * 백그라운드에서 호출해 컴파일/생성한다.
 */
final class SubmissionPanel extends JPanel {

    enum Kind {
        EDITOR_JAVA("Java 에디터"),
        FILE("파일 (.java/.class/.jar)"),
        COMMAND("외부 명령 (Python 등)");

        final String label;

        Kind(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final String TEMPLATE =
            "import java.util.Scanner;\n\n"
            + "public class Main {\n"
            + "    public static void main(String[] args) {\n"
            + "        Scanner sc = new Scanner(System.in);\n"
            + "        int a = sc.nextInt(), b = sc.nextInt();\n"
            + "        System.out.println(a + b);\n"
            + "    }\n"
            + "}\n";

    private final JComboBox<Kind> kindCombo = new JComboBox<>(Kind.values());
    private final JTextField nameField = new JTextField(14);
    private final JTextArea editor = new JTextArea(TEMPLATE);
    private final JTextField fileField = new JTextField();
    private final JTextField commandField = new JTextField("python solutions/python/correct_solution.py");
    private final JLabel status = new JLabel(" ");
    private final CardLayout cards = new CardLayout();
    private final JPanel cardHost = new JPanel(cards);

    SubmissionPanel() {
        super(new BorderLayout(0, 8));
        setBackground(FlatTheme.SURFACE);
        setBorder(FlatTheme.cardBorder(12));

        add(buildHeader(), BorderLayout.NORTH);
        cardHost.setOpaque(false);
        cardHost.add(buildEditorCard(), Kind.EDITOR_JAVA.name());
        cardHost.add(buildFileCard(), Kind.FILE.name());
        cardHost.add(buildCommandCard(), Kind.COMMAND.name());
        add(cardHost, BorderLayout.CENTER);

        status.setForeground(FlatTheme.TEXT_MUTED);
        status.setBorder(FlatTheme.pad(2, 2, 0, 2));
        add(status, BorderLayout.SOUTH);

        kindCombo.addActionListener(e -> cards.show(cardHost, selectedKind().name()));
        cards.show(cardHost, Kind.EDITOR_JAVA.name());
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        header.setOpaque(false);
        JLabel typeLabel = new JLabel("유형");
        typeLabel.setForeground(FlatTheme.TEXT_MUTED);
        JLabel nameLabel = new JLabel("이름");
        nameLabel.setForeground(FlatTheme.TEXT_MUTED);
        styleField(nameField);
        kindCombo.setMaximumRowCount(3);
        header.add(typeLabel);
        header.add(kindCombo);
        header.add(nameLabel);
        header.add(nameField);
        return header;
    }

    private JPanel buildEditorCard() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        editor.setFont(FlatTheme.MONO_FONT);
        editor.setTabSize(4);
        editor.setForeground(FlatTheme.TEXT);
        editor.setCaretColor(FlatTheme.ACCENT);
        editor.setBorder(FlatTheme.pad(8, 10, 8, 10));
        p.add(FlatTheme.scroll(editor), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildFileCard() {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        styleField(fileField);
        fileField.setToolTipText(".java(컴파일) / .class · .jar(직접 로드)");
        JButton browse = FlatTheme.ghostButton("찾아보기…");
        browse.addActionListener(e -> chooseFile());
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.add(fileField, BorderLayout.CENTER);
        row.add(browse, BorderLayout.EAST);
        p.add(row, BorderLayout.NORTH);
        JLabel hint = new JLabel(".java 는 컴파일 후 격리 실행, .class/.jar 는 코어 JavaJarSolution 로드");
        hint.setForeground(FlatTheme.TEXT_MUTED);
        hint.setBorder(FlatTheme.pad(8, 2, 0, 2));
        p.add(hint, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildCommandCard() {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        styleField(commandField);
        commandField.setToolTipText("예: python \"sol.py\"  /  node sol.js  /  a.out");
        JButton pick = FlatTheme.ghostButton("스크립트…");
        pick.addActionListener(e -> chooseScript());
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.add(commandField, BorderLayout.CENTER);
        row.add(pick, BorderLayout.EAST);
        p.add(row, BorderLayout.NORTH);
        JLabel hint = new JLabel("stdin/stdout 계약을 따르는 임의 실행 명령 (공백 경로는 \"\" 로 감쌀 것)");
        hint.setForeground(FlatTheme.TEXT_MUTED);
        hint.setBorder(FlatTheme.pad(8, 2, 0, 2));
        p.add(hint, BorderLayout.CENTER);
        return p;
    }

    private void chooseFile() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Java 풀이 (*.java, *.class, *.jar)", "java", "class", "jar"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            fileField.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void chooseScript() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            String name = f.getName().toLowerCase(Locale.ROOT);
            String path = '"' + f.getAbsolutePath() + '"';
            if (name.endsWith(".py")) {
                commandField.setText("python " + path);
            } else if (name.endsWith(".js")) {
                commandField.setText("node " + path);
            } else {
                commandField.setText(path);
            }
        }
    }

    private static void styleField(JTextField field) {
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(FlatTheme.BORDER, 1, true),
                FlatTheme.pad(6, 8, 6, 8)));
        field.setBackground(FlatTheme.SURFACE_ALT);
    }

    private Kind selectedKind() {
        return (Kind) kindCombo.getSelectedItem();
    }

    /** EDT에서 호출 — 패널 상태를 변경 불가 스냅샷으로 캡처. */
    Spec toSpec(String fallbackName) {
        String name = nameField.getText().isBlank() ? fallbackName : nameField.getText().trim();
        return new Spec(selectedKind(), name, editor.getText(),
                fileField.getText().trim(), commandField.getText().trim());
    }

    /** EDT에서 호출 — 컴파일/빌드 결과 메시지 표시. */
    void setStatus(String message, boolean error) {
        status.setText(message == null || message.isBlank() ? " " : oneLine(message));
        status.setForeground(error ? FlatTheme.FAIL : FlatTheme.TEXT_MUTED);
        status.setToolTipText(message);
    }

    private static String oneLine(String s) {
        String flat = s.replace("\r", "").replace("\n", "  ·  ");
        return flat.length() > 140 ? flat.substring(0, 140) + "…" : flat;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = Math.max(d.height, 260);
        return d;
    }

    /** EDT 밖(백그라운드)에서 안전하게 Solution을 빌드하기 위한 불변 스냅샷. */
    static final class Spec {
        private final Kind kind;
        final String name;
        private final String source;
        private final String filePath;
        private final String command;

        Spec(Kind kind, String name, String source, String filePath, String command) {
            this.kind = kind;
            this.name = name;
            this.source = source;
            this.filePath = filePath;
            this.command = command;
        }

        /** 백그라운드 호출 — 컴파일/생성. 실패 시 예외(메시지는 사용자 표시용). */
        Solution buildSolution() throws Exception {
            switch (kind) {
                case EDITOR_JAVA: {
                    CompileResult cr = JavaSourceCompiler.compileSource(source);
                    return javaProcess(cr);
                }
                case FILE:
                    return buildFromFile();
                case COMMAND:
                    if (command.isBlank()) {
                        throw new IllegalArgumentException("실행 명령이 비어 있습니다.");
                    }
                    return new ExternalProcessSolution(command, name);
                default:
                    throw new IllegalStateException("알 수 없는 유형: " + kind);
            }
        }

        private Solution buildFromFile() throws Exception {
            if (filePath.isBlank()) {
                throw new IllegalArgumentException("파일 경로가 비어 있습니다.");
            }
            String lower = filePath.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".java")) {
                CompileResult cr = JavaSourceCompiler.compileFile(Path.of(filePath));
                return javaProcess(cr);
            }
            if (lower.endsWith(".class") || lower.endsWith(".jar")) {
                // 첨부 산출물 — 코어 설계 경로(같은 JVM, STREAM_LOCK 직렬화)
                return new JavaJarSolution(Path.of(filePath), name);
            }
            throw new CompileException("지원하지 않는 파일: " + filePath + " (.java/.class/.jar 만)");
        }

        /** 컴파일 산출물을 격리 외부 JVM으로 실행 — 공백 경로 대비 classpath는 따옴표로 감쌈. */
        private Solution javaProcess(CompileResult cr) {
            String cmd = "java -cp \"" + cr.classesDir() + "\" " + cr.mainClassFqn();
            return new ExternalProcessSolution(cmd, name);
        }
    }
}
