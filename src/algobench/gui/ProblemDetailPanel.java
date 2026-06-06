package algobench.gui;

import algobench.domain.Problem;
import algobench.domain.TestCase;
import java.awt.BorderLayout;
import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * 선택된 문제의 메타데이터 + 테스트 케이스 표(읽기 전용)를 보여준다.
 */
final class ProblemDetailPanel extends JPanel {

    private final JLabel title = new JLabel("문제를 선택하세요");
    private final JLabel meta = new JLabel(" ");
    private final DefaultTableModel caseModel = new DefaultTableModel(
            new Object[] {"번호", "입력", "기대 출력"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable caseTable = new JTable(caseModel);

    ProblemDetailPanel() {
        super(new BorderLayout(0, 8));
        setBackground(FlatTheme.SURFACE);
        setBorder(FlatTheme.cardBorder(12));

        JPanel head = new JPanel(new BorderLayout(0, 2));
        head.setOpaque(false);
        title.setFont(FlatTheme.TITLE_FONT);
        title.setForeground(FlatTheme.TEXT);
        meta.setForeground(FlatTheme.TEXT_MUTED);
        head.add(title, BorderLayout.NORTH);
        head.add(meta, BorderLayout.SOUTH);
        add(head, BorderLayout.NORTH);

        styleTable(caseTable);
        caseTable.getColumnModel().getColumn(0).setMaxWidth(56);
        add(FlatTheme.scroll(caseTable), BorderLayout.CENTER);
    }

    /** 문제 표시. {@code null}이면 빈 상태로 초기화. */
    void show(Problem problem) {
        caseModel.setRowCount(0);
        if (problem == null) {
            title.setText("문제를 선택하세요");
            meta.setText(" ");
            return;
        }
        title.setText(problem.getTitle());
        meta.setText(String.format("시간 제한 %d ms   ·   메모리 %d MB   ·   케이스 %d개",
                problem.getTimeLimit().toMillis(), problem.getMemoryLimitMb(),
                problem.getTestCases().size()));
        for (TestCase tc : problem.getTestCases()) {
            caseModel.addRow(new Object[] {tc.getIndex(), inline(tc.getInput()), inline(tc.getExpectedOutput())});
        }
    }

    static String inline(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.replace("\r", "").replace("\n", " ⏎ ");
    }

    static void styleTable(JTable table) {
        table.setRowHeight(26);
        table.setFont(FlatTheme.MONO_FONT);
        table.setForeground(FlatTheme.TEXT);
        table.setBackground(FlatTheme.SURFACE);
        table.setGridColor(FlatTheme.BORDER);
        table.setShowVerticalLines(false);
        table.setSelectionBackground(FlatTheme.SELECTION);
        table.setSelectionForeground(FlatTheme.TEXT);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setFont(FlatTheme.UI_BOLD);
        // 셀 전체 텍스트를 툴팁으로
        DefaultTableCellRenderer tip = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                    boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                setToolTipText(v == null ? null : v.toString());
                setBorder(FlatTheme.pad(0, 8, 0, 8));
                return c;
            }
        };
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(tip);
        }
    }
}
