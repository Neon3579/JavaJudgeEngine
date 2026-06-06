package algobench.gui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JButton;

/**
 * 플랫 디자인 버튼 — 둥근 모서리, 채워진 면, hover/pressed 상태색, 포커스 테두리 없음.
 *
 * <p>Nimbus의 기본 그라데이션 버튼 대신 직접 페인트해 모던/심플한 외형을 만든다.
 */
final class FlatButton extends JButton {

    private final Color base;
    private final Color hover;
    private final Color pressed;
    private static final int ARC = 12;

    FlatButton(String text, Color base, Color hover, Color pressed, Color fg) {
        super(text);
        this.base = base;
        this.hover = hover;
        this.pressed = pressed;
        setForeground(fg);
        setFont(FlatTheme.UI_BOLD);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(FlatTheme.pad(9, 18, 9, 18));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color fill;
        if (!isEnabled()) {
            fill = new Color(0xD0D7DE);
        } else if (getModel().isPressed()) {
            fill = pressed;
        } else if (getModel().isRollover()) {
            fill = hover;
        } else {
            fill = base;
        }
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);
        g2.dispose();
        super.paintComponent(g); // 텍스트/아이콘
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = Math.max(d.height, 34);
        return d;
    }
}
