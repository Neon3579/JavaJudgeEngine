package algobench.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * 여러 {@link SubmissionPanel}을 탭으로 관리한다 — 풀이 추가/삭제, 채점용 스냅샷 수집.
 *
 * <p>다중 비교가 핵심: 같은 문제에 풀이를 여러 개 올려 한 번에 채점한다.
 */
final class SubmissionsPanel extends JPanel {

    private final JTabbedPane tabs = new JTabbedPane();
    private int created = 0;

    SubmissionsPanel() {
        super(new BorderLayout(0, 8));
        setOpaque(false);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("풀이");
        FlatTheme.styleSection(title);
        header.add(title, BorderLayout.WEST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton add = FlatTheme.ghostButton("+ 풀이 추가");
        add.addActionListener(e -> addSubmission());
        JButton remove = FlatTheme.ghostButton("− 현재 탭 삭제");
        remove.addActionListener(e -> removeCurrent());
        buttons.add(remove);
        buttons.add(add);
        header.add(buttons, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        addSubmission(); // 기본 탭 1개
    }

    private void addSubmission() {
        created++;
        SubmissionPanel panel = new SubmissionPanel();
        tabs.addTab("풀이 " + created, panel);
        tabs.setSelectedComponent(panel);
    }

    private void removeCurrent() {
        if (tabs.getTabCount() <= 1) {
            return; // 최소 1개 유지
        }
        int idx = tabs.getSelectedIndex();
        if (idx >= 0) {
            tabs.removeTabAt(idx);
        }
    }

    /** EDT에서 호출 — 모든 풀이 패널과 스냅샷 스펙을 짝지어 반환. */
    List<PanelSpec> snapshot() {
        List<PanelSpec> list = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            SubmissionPanel panel = (SubmissionPanel) tabs.getComponentAt(i);
            String fallback = tabs.getTitleAt(i);
            list.add(new PanelSpec(panel, panel.toSpec(fallback)));
        }
        return list;
    }

    /** 패널 + 스냅샷 — done()에서 패널별 상태 라벨을 갱신하기 위해 패널 참조를 유지. */
    record PanelSpec(SubmissionPanel panel, SubmissionPanel.Spec spec) {
    }
}
