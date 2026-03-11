package com.arknova.bot.renderer;

import com.arknova.bot.model.PlayerState;
import java.awt.*;
import java.awt.image.BufferedImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Renders a player's zoo board as a {@link BufferedImage} for posting to Discord.
 *
 * <p><strong>Current state (Phase 2 stub)</strong>: Generates a placeholder image with player
 * name, track values, resources, and action card order as text. This is sufficient for the alpha
 * and validates the image pipeline (Java2D → PNG bytes → Discord embed attachment).
 *
 * <p><strong>Phase 3</strong>: Replace {@link #renderPlaceholder} with
 * {@link #renderFullBoard} that composites enclosure tiles over the base zoo map image.
 */
@Component
public class ZooBoardRenderer {

  private static final Logger log = LoggerFactory.getLogger(ZooBoardRenderer.class);

  private static final int WIDTH = 900;
  private static final int HEIGHT = 600;

  private static final Color BG_COLOR = new Color(34, 85, 34);          // dark green
  private static final Color PANEL_COLOR = new Color(245, 230, 195);    // parchment
  private static final Color ACCENT_COLOR = new Color(60, 120, 60);     // medium green
  private static final Color TEXT_COLOR = new Color(30, 30, 30);
  private static final Color TRACK_COLOR = new Color(180, 130, 50);     // amber
  private static final Color HEADER_COLOR = new Color(20, 60, 20);

  /**
   * Renders the player's board as a PNG-ready {@link BufferedImage}.
   *
   * <p>During Phase 2, this delegates to {@link #renderPlaceholder}. Swap in
   * {@link #renderFullBoard} during Phase 3.
   */
  public BufferedImage render(PlayerState player) {
    try {
      return renderPlaceholder(player);
    } catch (Exception e) {
      log.error("Failed to render board for player {}", player.getDiscordName(), e);
      return renderErrorImage(player.getDiscordName());
    }
  }

  // ── Placeholder Renderer (Phase 2) ──────────────────────────────────────────

  private BufferedImage renderPlaceholder(PlayerState player) {
    BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    setupRenderingHints(g);

    // Background
    g.setColor(BG_COLOR);
    g.fillRect(0, 0, WIDTH, HEIGHT);

    // Header bar
    g.setColor(HEADER_COLOR);
    g.fillRect(0, 0, WIDTH, 70);

    // Player name
    g.setColor(Color.WHITE);
    g.setFont(new Font("SansSerif", Font.BOLD, 26));
    g.drawString(player.getDiscordName() + "'s Zoo", 20, 45);

    g.setColor(Color.LIGHT_GRAY);
    g.setFont(new Font("SansSerif", Font.PLAIN, 14));
    g.drawString("Map: " + player.getMapId(), WIDTH - 120, 45);

    // Main panel
    g.setColor(PANEL_COLOR);
    g.fillRoundRect(15, 85, WIDTH - 30, HEIGHT - 100, 15, 15);

    // ── Tracks section ───────────────────────────────────────────────────────
    drawSectionHeader(g, "Tracks", 30, 120);

    drawTrackBar(g, "Appeal", player.getAppeal(), 113, 30, 145);
    drawTrackBar(g, "Conservation", player.getConservation(), 80, 30, 185);

    // ── Resources section ────────────────────────────────────────────────────
    drawSectionHeader(g, "Resources", 30, 240);

    drawResource(g, "Money", String.valueOf(player.getMoney()), 30, 265);
    drawResource(g, "X Tokens", String.valueOf(player.getXTokens()), 200, 265);
    drawResource(g, "Reputation", String.valueOf(player.getReputation()), 370, 265);
    drawResource(g, "Assoc. Workers",
        player.getAssocWorkersAvailable() + "/" + player.getAssocWorkers(), 540, 265);

    // ── Action cards section ──────────────────────────────────────────────────
    drawSectionHeader(g, "Action Cards (left=1, right=5)", 30, 320);
    drawActionCardStrip(g, player, 30, 345);

    // ── Zoo board grid (placeholder) ─────────────────────────────────────────
    drawSectionHeader(g, "Zoo Board", 30, 430);
    g.setColor(new Color(200, 215, 200));
    g.fillRoundRect(30, 450, WIDTH - 60, 120, 8, 8);
    g.setColor(new Color(150, 160, 150));
    g.setFont(new Font("SansSerif", Font.ITALIC, 16));
    g.drawString("(Full board rendering coming in Phase 3)", WIDTH / 2 - 160, 515);

    g.dispose();
    return img;
  }

  private void drawTrackBar(Graphics2D g, String label, int value, int max, int x, int y) {
    int barWidth = 580;
    int barHeight = 22;
    double pct = Math.min((double) value / max, 1.0);

    g.setColor(new Color(180, 180, 160));
    g.fillRoundRect(x + 150, y, barWidth, barHeight, 5, 5);

    g.setColor(TRACK_COLOR);
    g.fillRoundRect(x + 150, y, (int) (barWidth * pct), barHeight, 5, 5);

    g.setColor(TEXT_COLOR);
    g.setFont(new Font("SansSerif", Font.BOLD, 14));
    g.drawString(label + ":", x, y + 16);
    g.drawString(value + "/" + max, x + 150 + barWidth + 10, y + 16);
  }

  private void drawResource(Graphics2D g, String label, String value, int x, int y) {
    g.setColor(new Color(100, 80, 40));
    g.setFont(new Font("SansSerif", Font.PLAIN, 13));
    g.drawString(label, x, y);
    g.setColor(TEXT_COLOR);
    g.setFont(new Font("SansSerif", Font.BOLD, 22));
    g.drawString(value, x, y + 25);
  }

  private void drawActionCardStrip(Graphics2D g, PlayerState player, int x, int y) {
    var cardOrder = player.getActionCardOrder();
    int cardW = 150;
    int cardH = 60;
    int gap = 12;

    for (int i = 0; i < 5; i++) {
      var card = cardOrder.getOrder().get(i);
      int strength = i + 1;
      int cx = x + i * (cardW + gap);

      // Card background — stronger cards are more saturated green
      int greenIntensity = 80 + (strength * 25);
      g.setColor(new Color(30, greenIntensity, 30));
      g.fillRoundRect(cx, y, cardW, cardH, 8, 8);

      // Upgraded marker
      if (cardOrder.isUpgraded(card)) {
        g.setColor(new Color(255, 200, 0));
        g.fillOval(cx + cardW - 18, y + 4, 14, 14);
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.drawString("★", cx + cardW - 15, y + 15);
      }

      // Card name
      g.setColor(Color.WHITE);
      g.setFont(new Font("SansSerif", Font.BOLD, 14));
      g.drawString(card.emoji(), cx + 8, y + 26);
      g.setFont(new Font("SansSerif", Font.BOLD, 12));
      g.drawString(card.displayName(), cx + 8, y + 44);

      // Strength indicator
      g.setColor(new Color(200, 255, 200));
      g.setFont(new Font("SansSerif", Font.BOLD, 18));
      g.drawString(String.valueOf(strength), cx + cardW - 24, y + 44);
    }
  }

  private void drawSectionHeader(Graphics2D g, String title, int x, int y) {
    g.setColor(ACCENT_COLOR);
    g.fillRoundRect(x, y - 18, 200, 22, 4, 4);
    g.setColor(Color.WHITE);
    g.setFont(new Font("SansSerif", Font.BOLD, 13));
    g.drawString(title, x + 8, y - 2);
  }

  // ── Full Board Renderer (Phase 3 stub) ────────────────────────────────────

  @SuppressWarnings("unused") // Activated in Phase 3
  private BufferedImage renderFullBoard(PlayerState player) {
    // TODO Phase 3:
    // 1. Load base map image: resourceLoader.getResource("classpath:maps/" + player.getMapId() + ".png")
    // 2. Paint enclosures from player.getBoardState() JSON
    // 3. Overlay animal card thumbnails
    // 4. Draw action card strip, track markers, resource panel
    throw new UnsupportedOperationException("Full board rendering not yet implemented");
  }

  // ── Error Image ────────────────────────────────────────────────────────────

  private BufferedImage renderErrorImage(String playerName) {
    BufferedImage img = new BufferedImage(400, 100, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(Color.DARK_GRAY);
    g.fillRect(0, 0, 400, 100);
    g.setColor(Color.RED);
    g.setFont(new Font("SansSerif", Font.BOLD, 16));
    g.drawString("Render failed for " + playerName, 10, 55);
    g.dispose();
    return img;
  }

  private void setupRenderingHints(Graphics2D g) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
  }
}
