package algobench.gui;

import algobench.result.BenchmarkResult;
import algobench.result.TestCaseResult;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;
import java.util.Locale;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * 채점 결과 — 좌측 풀이별 요약표, 우측 선택 풀이의 케이스 표 + 케이스 상세(기대/실제/사유).
 */
final class ResultsPanel extends JPanel {

    private final DefaultTableModel solModel = readOnlyModel("풀이", "결과", "통과", "총 시간(ms)", "첫 실패 사유");
    private final JTable solTable = new JTable(solModel);

    private final DefaultTableModel caseModel = readOnlyModel("케이스", "결과", "시간(ms)", "사유");
    private final JTable caseTable = new JTable(caseModel);

    private final JTextArea detail = new JTextArea();

    private List<BenchmarkResult> results = List.of();
    private List<TestCaseResult> currentCases = List.of();

    ResultsPanel() {
        super(new BorderLayout(0, 8));
        setBackground(FlatTheme.SURFACE);
        setBorder(FlatTheme.cardBorder(12));

        JLabel title = new JLabel("결과");
        FlatTheme.styleSection(title);
        add(title, BorderLayout.NORTH);

        ProblemDetailPanel.styleTable(solTable);
        ProblemDetailPanel.styleTable(caseTable);
        solTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        caseTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        VerdictRenderer verdict = new VerdictRenderer();
        solTable.getColumnModel().getColumn(1).setCellRenderer(verdict);
        caseTable.getColumnModel().getColumn(1).setCellRenderer(verdict);

        solTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showCases(solTable.getSelectedRow());
            }
        });
        caseTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showDetail(caseTable.getSelectedRow());
            }
        });

        detail.setEditable(false);
        detail.setFont(FlatTheme.MONO_FONT);
        detail.setForeground(FlatTheme.TEXT);
        detail.setBackground(FlatTheme.SURFACE_ALT);
        detail.setBorder(FlatTheme.pad(8, 10, 8, 10));
        detail.setText("풀이를 채점하면 여기에 결과가 표시됩니다.");

        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                FlatTheme.scroll(caseTable), FlatTheme.scroll(detail));
        right.setResizeWeight(0.6);
        right.setBorder(null);

        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                FlatTheme.scroll(solTable), right);
        main.setResizeWeight(0.5);
        main.setBorder(null);
        add(main, BorderLayout.CENTER);
    }

    /** EDT에서 호출 — 결과 표시. */
    void show(List<BenchmarkResult> results) {
        this.results = results == null ? List.of() : results;
        solModel.setRowCount(0);
        caseModel.setRowCount(0);
        detail.setText("");
        for (BenchmarkResult r : this.results) {
            solModel.addRow(new Object[] {
                    r.getSolutionName(),
                    r.isAllPassed() ? "ALL PASS" : "FAIL",
                    r.getPassedCount() + " / " + r.getTotalCount(),
                    ms(r.getTotalExecutionTime().toNanos()),
                    firstFailure(r)});
        }
        if (!this.results.isEmpty()) {
            solTable.setRowSelectionInterval(0, 0);
        }
    }

    private void showCases(int row) {
        caseModel.setRowCount(0);
        detail.setText("");
        if (row < 0 || row >= results.size()) {
            currentCases = List.of();
            return;
        }
        currentCases = results.get(row).getCaseResults();
        for (TestCaseResult c : currentCases) {
            caseModel.addRow(new Object[] {
                    c.getTestCaseIndex(),
                    c.isPassed() ? "PASS" : "FAIL",
                    ms(c.getExecutionTime().toNanos()),
                    oneLine(c.getErrorMessage())});
        }
        if (!currentCases.isEmpty()) {
            caseTable.setRowSelectionInterval(0, 0);
        }
    }

    private void showDetail(int row) {
        if (row < 0 || row >= currentCases.size()) {
            detail.setText("");
            return;
        }
        TestCaseResult c = currentCases.get(row);
        StringBuilder sb = new StringBuilder();
        sb.append("케이스 ").append(c.getTestCaseIndex())
                .append("   ").append(c.isPassed() ? "PASS" : "FAIL")
                .append(String.format(Locale.ROOT, "   (%.3f ms)", c.getExecutionTime().toNanos() / 1_000_000.0))
                .append("\n\n");
        sb.append("─ 기대 출력 ─\n").append(nullToEmpty(c.getExpectedOutput())).append("\n\n");
        sb.append("─ 실제 출력 ─\n").append(nullToEmpty(c.getActualOutput()));
        if (!c.getErrorMessage().isEmpty()) {
            sb.append("\n\n─ 사유 ─\n").append(c.getErrorMessage());
        }
        detail.setText(sb.toString());
        detail.setCaretPosition(0);
    }

    private static String firstFailure(BenchmarkResult r) {
        for (TestCaseResult c : r.getCaseResults()) {
            if (!c.isPassed()) {
                return "케이스 " + c.getTestCaseIndex() + ": " + oneLine(c.getErrorMessage());
            }
        }
        return "";
    }

    private static String ms(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    private static String oneLine(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String flat = s.replace("\r", "");
        int nl = flat.indexOf('\n');
        if (nl >= 0) {
            flat = flat.substring(0, nl);
        }
        return flat.length() > 100 ? flat.substring(0, 100) + "…" : flat;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static DefaultTableModel readOnlyModel(Object... cols) {
        return new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
    }

    /** PASS=초록, FAIL=빨강으로 판정 셀을 칠한다. */
    private static final class VerdictRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                boolean foc, int row, int col) {
            Component c = super.getTableCellRendererComponent(t, v, sel, foc, row, col);
            String s = v == null ? "" : v.toString();
            boolean pass = s.contains("PASS");
            c.setForeground(pass ? FlatTheme.PASS : FlatTheme.FAIL);
            setFont(FlatTheme.UI_BOLD);
            setBorder(FlatTheme.pad(0, 8, 0, 8));
            return c;
        }
    }
}
