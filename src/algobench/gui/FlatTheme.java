package algobench.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.FontUIResource;

/**
 * 단일 스타일 소스 — 색 팔레트 + 폰트 + 컴포넌트 팩토리.
 *
 * <p>순수 Java SE만 사용한다(외부 LAF 라이브러리 없음). Nimbus를 베이스로 깔고 라이트 플랫 팔레트로
 * 덮어쓴 뒤, 카드/구분선/모노스페이스 에디터 등은 컴포넌트별로 직접 스타일링한다.
 */
final class FlatTheme {

    private FlatTheme() {
    }

    // ── 팔레트 (라이트 플랫) ──
    static final Color BG = new Color(0xF4F5F7);          // 창 배경
    static final Color SURFACE = new Color(0xFFFFFF);     // 카드/패널
    static final Color SURFACE_ALT = new Color(0xFAFBFC); // 입력/표 헤더
    static final Color BORDER = new Color(0xE1E4E8);      // 경계선
    static final Color TEXT = new Color(0x1F2328);        // 본문
    static final Color TEXT_MUTED = new Color(0x6E7781);  // 보조
    static final Color ACCENT = new Color(0x2563EB);      // 주 강조(파랑)
    static final Color ACCENT_HOVER = new Color(0x1D4ED8);
    static final Color ACCENT_PRESSED = new Color(0x1E40AF);
    static final Color ON_ACCENT = Color.WHITE;
    static final Color PASS = new Color(0x16A34A);        // 통과(초록)
    static final Color FAIL = new Color(0xDC2626);        // 실패(빨강)
    static final Color WARN = new Color(0xD97706);        // 경고(주황)
    static final Color SELECTION = new Color(0xDCEAFE);   // 선택 배경(연파랑)

    // ── 폰트 ──
    // 한글 글리프 필수: "Segoe UI"/"Consolas"는 한글 미포함(tofu). Malgun Gothic은
    // 라틴+한글 모두 렌더, 코드용 모노는 논리 폰트 "Monospaced"(한글 fallback 보장).
    static final String UI_FAMILY = pickUiFamily();
    static final String MONO_FAMILY = "Monospaced"; // 논리 폰트 — 한글 fallback 합성
    static final Font UI_FONT = new Font(UI_FAMILY, Font.PLAIN, 13);
    static final Font UI_BOLD = UI_FONT.deriveFont(Font.BOLD);
    static final Font TITLE_FONT = new Font(UI_FAMILY, Font.BOLD, 18);
    static final Font SECTION_FONT = new Font(UI_FAMILY, Font.BOLD, 13);
    static final Font MONO_FONT = new Font(MONO_FAMILY, Font.PLAIN, 13);

    /** 한글 렌더 가능한 UI 폰트 선택 — Malgun Gothic 우선, 없으면 논리 폰트 Dialog. */
    private static String pickUiFamily() {
        Font malgun = new Font("Malgun Gothic", Font.PLAIN, 13);
        return malgun.canDisplay('가') ? "Malgun Gothic" : "Dialog";
    }

    /** Nimbus 설치 + 라이트 플랫 오버라이드. main 진입 시 1회 호출. */
    static void install() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignore) {
            // Nimbus 없으면 기본 LAF로 진행 — 색 오버라이드는 여전히 적용
        }

        UIManager.put("control", SURFACE);
        UIManager.put("background", BG);
        UIManager.put("nimbusBase", new Color(0x3B4252));
        UIManager.put("nimbusBlueGrey", new Color(0xD0D7DE));
        UIManager.put("nimbusLightBackground", SURFACE);
        UIManager.put("text", TEXT);
        UIManager.put("nimbusFocus", ACCENT);
        UIManager.put("nimbusSelectionBackground", ACCENT);
        UIManager.put("nimbusSelection", ACCENT);
        UIManager.put("List.background", SURFACE);
        UIManager.put("Table.background", SURFACE);
        UIManager.put("Table.alternateRowColor", SURFACE_ALT);
        UIManager.put("ScrollPane.background", SURFACE);
        UIManager.put("TextArea.background", SURFACE);
        UIManager.put("Panel.background", BG);

        FontUIResource ui = new FontUIResource(UI_FONT);
        for (String key : new String[] {
                "Label.font", "Button.font", "ToggleButton.font", "List.font",
                "Table.font", "TableHeader.font", "ComboBox.font", "TextField.font",
                "TextArea.font", "TabbedPane.font", "TitledBorder.font", "Panel.font",
                "OptionPane.font", "ToolTip.font", "CheckBox.font"}) {
            UIManager.put(key, ui);
        }
    }

    // ── 컴포넌트 팩토리 ──

    /** 주 강조 버튼 (채워진 둥근 버튼). */
    static FlatButton primaryButton(String text) {
        return new FlatButton(text, ACCENT, ACCENT_HOVER, ACCENT_PRESSED, ON_ACCENT);
    }

    /** 보조 버튼 (연한 면 + 진한 글씨). */
    static FlatButton ghostButton(String text) {
        return new FlatButton(text, SURFACE_ALT, new Color(0xEDEFF2), new Color(0xE1E4E8), TEXT);
    }

    /** 카드 테두리 (1px 선 + 내부 패딩). */
    static Border cardBorder(int pad) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(pad, pad, pad, pad));
    }

    /** 균일 여백. */
    static Border pad(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    /** 테두리 없는 스크롤 패널(플랫). */
    static JScrollPane scroll(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(BorderFactory.createLineBorder(BORDER, 1, true));
        sp.getViewport().setBackground(SURFACE);
        sp.setBackground(SURFACE);
        return sp;
    }

    /** 섹션 제목 라벨로 꾸민다(굵게 + 살짝 muted). */
    static void styleSection(JComponent label) {
        label.setFont(SECTION_FONT);
        label.setForeground(TEXT);
    }
}
