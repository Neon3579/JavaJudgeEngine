package algobench.gui;

import algobench.domain.Problem;
import algobench.loader.ProblemLoader;
import java.awt.BorderLayout;
import java.awt.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * 좌측 문제 선택 사이드바 — {@code problems/} 의 {@code *.txt} 스캔 + 임의 파일 열기.
 *
 * <p>선택 시 코어 {@link ProblemLoader}로 로드해 콜백에 전달한다. 파싱 실패는 다이얼로그로 격리.
 */
final class ProblemListPanel extends JPanel {

    private final ProblemLoader loader = new ProblemLoader();
    private final DefaultListModel<Entry> model = new DefaultListModel<>();
    private final JList<Entry> list = new JList<>(model);
    private final Consumer<Problem> onProblemSelected;

    ProblemListPanel(Consumer<Problem> onProblemSelected) {
        super(new BorderLayout(0, 8));
        this.onProblemSelected = onProblemSelected;
        setBackground(FlatTheme.SURFACE);
        setBorder(FlatTheme.cardBorder(12));

        JLabel title = new JLabel("문제");
        FlatTheme.styleSection(title);
        add(title, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(FlatTheme.SURFACE);
        list.setFixedCellHeight(30);
        list.setCellRenderer(new EntryRenderer());
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelected();
            }
        });
        add(FlatTheme.scroll(list), BorderLayout.CENTER);

        JButton open = FlatTheme.ghostButton("문제 열기…");
        open.addActionListener(e -> openFromDisk());
        add(open, BorderLayout.SOUTH);

        scanProblemsDir();
    }

    /** {@code problems/} 디렉토리의 *.txt 를 목록에 채운다. */
    private void scanProblemsDir() {
        Path dir = Path.of("problems");
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".txt"))
                    .sorted()
                    .forEach(files::add);
        } catch (IOException ignore) {
            // 디렉토리 접근 실패 — 목록 비움
        }
        for (Path p : files) {
            model.addElement(new Entry(p));
        }
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    private void openFromDisk() {
        JFileChooser fc = new JFileChooser("problems");
        fc.setFileFilter(new FileNameExtensionFilter("문제 파일 (*.txt)", "txt"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Entry entry = new Entry(fc.getSelectedFile().toPath());
            model.addElement(entry);
            list.setSelectedValue(entry, true);
        }
    }

    private void loadSelected() {
        Entry entry = list.getSelectedValue();
        if (entry == null) {
            return;
        }
        try {
            Problem problem = loader.loadProblem(entry.path.toString());
            onProblemSelected.accept(problem);
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this,
                    "문제를 불러오지 못했습니다:\n" + ex.getMessage(),
                    "로드 실패", JOptionPane.ERROR_MESSAGE);
            onProblemSelected.accept(null);
        }
    }

    /** 목록 항목 — 파일 경로 + 표시 이름. */
    private static final class Entry {
        final Path path;

        Entry(Path path) {
            this.path = path;
        }

        @Override
        public String toString() {
            return path.getFileName().toString();
        }
    }

    /** 항목 좌측 여백 + 선택 강조 색을 입힌 렌더러. */
    private static final class EntryRenderer extends javax.swing.DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
            label.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            label.setBackground(selected ? FlatTheme.SELECTION : FlatTheme.SURFACE);
            label.setForeground(FlatTheme.TEXT);
            return label;
        }
    }
}
