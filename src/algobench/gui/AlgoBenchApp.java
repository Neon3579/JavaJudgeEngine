package algobench.gui;

import algobench.compare.ExactOutputComparator;
import algobench.compare.OutputComparator;
import algobench.compare.WhitespaceNormalizingComparator;
import algobench.domain.Problem;
import algobench.result.BenchmarkResult;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

/**
 * AlgoBench Swing GUI 진입점 — 백준식 "문제 선택 → 소스 작성/첨부 → 채점" 워크플로.
 *
 * <p>채점 로직은 전부 기존 AlgoBench 코어({@code ProblemLoader}/{@code Solution}/{@code JudgeEngine})를
 * 호출한다. 이 클래스는 순수 프레젠테이션 조립 + 이벤트 배선만 담당한다.
 *
 * <pre>java -cp out algobench.gui.AlgoBenchApp</pre>
 */
public final class AlgoBenchApp {

    private final JFrame frame = new JFrame("AlgoBench");
    private final ProblemDetailPanel detailPanel = new ProblemDetailPanel();
    private final SubmissionsPanel submissionsPanel = new SubmissionsPanel();
    private final ResultsPanel resultsPanel = new ResultsPanel();
    private final JComboBox<String> comparatorCombo =
            new JComboBox<>(new String[] {"정확 일치 (Exact)", "공백 무시 (Whitespace)"});
    private final FlatButton runButton = FlatTheme.primaryButton("채점 ▶");
    private final JLabel status = new JLabel("문제를 선택하세요");

    private Problem currentProblem;

    public static void main(String[] args) {
        FlatTheme.install();
        SwingUtilities.invokeLater(() -> new AlgoBenchApp().show());
    }

    private void show() {
        ProblemListPanel problemList = new ProblemListPanel(this::onProblemSelected);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(buildHeader(), BorderLayout.NORTH);

        JSplitPane workSplit = verticalSplit(submissionsPanel, resultsPanel, 0.5);
        JSplitPane rightSplit = verticalSplit(detailPanel, workSplit, 0.28);
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, problemList, rightSplit);
        mainSplit.setResizeWeight(0.0);
        mainSplit.setDividerLocation(240);
        mainSplit.setBorder(null);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(FlatTheme.BG);
        content.setBorder(FlatTheme.pad(12, 12, 12, 12));
        content.add(mainSplit, BorderLayout.CENTER);
        frame.add(content, BorderLayout.CENTER);

        runButton.setEnabled(false);
        runButton.addActionListener(e -> runBenchmark());

        frame.setSize(1180, 760);
        frame.setMinimumSize(new Dimension(900, 600));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(FlatTheme.SURFACE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, FlatTheme.BORDER),
                FlatTheme.pad(12, 16, 12, 16)));

        JPanel brand = new JPanel(new BorderLayout(0, 2));
        brand.setOpaque(false);
        JLabel logo = new JLabel("AlgoBench");
        logo.setFont(FlatTheme.TITLE_FONT);
        logo.setForeground(FlatTheme.ACCENT);
        JLabel sub = new JLabel("로컬 알고리즘 벤치마크 — 문제를 고르고 풀이를 채점·비교");
        sub.setForeground(FlatTheme.TEXT_MUTED);
        brand.add(logo, BorderLayout.NORTH);
        brand.add(sub, BorderLayout.SOUTH);
        header.add(brand, BorderLayout.WEST);

        JPanel actions = new JPanel(new BorderLayout(12, 0));
        actions.setOpaque(false);
        JPanel comparatorBox = new JPanel(new BorderLayout(6, 0));
        comparatorBox.setOpaque(false);
        JLabel cmpLabel = new JLabel("채점 기준");
        cmpLabel.setForeground(FlatTheme.TEXT_MUTED);
        comparatorBox.add(cmpLabel, BorderLayout.WEST);
        comparatorBox.add(comparatorCombo, BorderLayout.CENTER);

        status.setForeground(FlatTheme.TEXT_MUTED);
        status.setHorizontalAlignment(JLabel.RIGHT);

        JPanel right = new JPanel(new BorderLayout(12, 0));
        right.setOpaque(false);
        right.add(comparatorBox, BorderLayout.WEST);
        right.add(runButton, BorderLayout.EAST);

        JPanel stack = new JPanel(new BorderLayout(0, 4));
        stack.setOpaque(false);
        stack.add(right, BorderLayout.NORTH);
        stack.add(status, BorderLayout.SOUTH);

        actions.add(stack, BorderLayout.EAST);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private void onProblemSelected(Problem problem) {
        currentProblem = problem;
        detailPanel.show(problem);
        runButton.setEnabled(problem != null);
        if (problem != null) {
            setStatus("준비됨 — \"" + problem.getTitle() + "\"", false);
        } else {
            setStatus("문제를 선택하세요", false);
        }
    }

    private void runBenchmark() {
        if (currentProblem == null) {
            return;
        }
        List<SubmissionsPanel.PanelSpec> specs = submissionsPanel.snapshot();
        OutputComparator comparator = selectedComparator();

        runButton.setEnabled(false);
        setStatus("채점 중…", false);

        new BenchmarkRunner(currentProblem, specs, comparator, new BenchmarkRunner.Hooks() {
            @Override
            public void panelStatus(SubmissionPanel panel, String message, boolean error) {
                panel.setStatus(message, error);
            }

            @Override
            public void finished(List<BenchmarkResult> results) {
                resultsPanel.show(results);
                long passed = results.stream().filter(BenchmarkResult::isAllPassed).count();
                setStatus("완료 — " + results.size() + "개 풀이 채점 (전체 통과 " + passed + ")", false);
                runButton.setEnabled(true);
            }

            @Override
            public void failed(String message) {
                setStatus(message, true);
                runButton.setEnabled(true);
            }
        }).execute();
    }

    private OutputComparator selectedComparator() {
        return comparatorCombo.getSelectedIndex() == 1
                ? new WhitespaceNormalizingComparator()
                : new ExactOutputComparator();
    }

    private void setStatus(String message, boolean error) {
        status.setText(message);
        status.setForeground(error ? FlatTheme.FAIL : FlatTheme.TEXT_MUTED);
    }

    private static JSplitPane verticalSplit(java.awt.Component top, java.awt.Component bottom, double weight) {
        JSplitPane sp = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        sp.setResizeWeight(weight);
        sp.setBorder(null);
        return sp;
    }
}
