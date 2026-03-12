package com.arknova.bot.renderer;

import com.arknova.bot.model.PlayerState;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Renders a player's zoo board as a {@link BufferedImage} for posting to Discord.
 *
 * <h2>Layout (900 × 820 px)</h2>
 * <pre>
 *  0– 58   Header bar           (player name, map ID)
 * 64–274   Info panel           (resources, tracks, action card strip)
 * 277–820  Zoo grid             (flat-top hex grid, offset-column layout)
 * </pre>
 *
 * <h2>Hex geometry</h2>
 * <b>Flat-top</b> orientation — each hex has a flat horizontal edge at top and bottom,
 * with pointed vertices on the left and right sides. This matches the physical Ark Nova
 * player mat. Hexes tile in <b>offset-column</b> layout: adjacent columns step right
 * by 3R/2 and odd-numbered columns are shifted downward by half a hex-height (√3R/2).
 *
 * <h2>Grid coordinates</h2>
 * {@code col} 0–8 runs left→right; {@code row} 0–6 runs top→bottom within each column.
 * The board is not a full rectangle — a {@link #COL_ROW_RANGE} mask clips the four
 * corners to give the rounded-hexagonal outline of the physical player mat.
 *
 * <h2>Enclosures</h2>
 * Each enclosure occupies {@code size} consecutive hexes at columns
 * {@code col}, {@code col+1}, …, {@code col+size-1} in the same {@code row}.
 * Because odd columns are offset down, multi-hex enclosures have a slight visual
 * zigzag, which mirrors the physical tile placement on the board.
 *
 * <h2>Enclosure colours</h2>
 * <ul>
 *   <li>No tags — warm tan</li>
 *   <li>WATER     — steel blue</li>
 *   <li>ROCK      — stone grey</li>
 *   <li>WATER+ROCK— blue-grey</li>
 *   <li>Any with animal placed — darker variant</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ZooBoardRenderer {

  private static final Logger log = LoggerFactory.getLogger(ZooBoardRenderer.class);

  private final ObjectMapper objectMapper;

  // ── Canvas ────────────────────────────────────────────────────────────────
  private static final int W = 900;
  private static final int H = 800;

  // ── Flat-top hex geometry (offset-COLUMN layout) ──────────────────────────
  //
  //  Flat-top hex with circumradius R:
  //    width  = 2R          (far-left vertex to far-right vertex)
  //    height = √3·R        (top flat-edge to bottom flat-edge)
  //  Column-to-column step = 3R/2  (neighbouring columns share two diagonal edges)
  //  Row-to-row step       = √3·R  (within the same column, hexes share flat edges)
  //  Odd-column offset     = √3·R/2 downward
  //
  private static final int    ROWS        = 7;
  private static final int    COLS        = 9;
  private static final int    HEX_R       = 37;
  private static final double HEX_W       = 2.0  * HEX_R;              // = 74 px
  private static final double HEX_H       = Math.sqrt(3.0) * HEX_R;    // ≈ 64.1 px
  private static final double H_STEP      = HEX_R * 1.5;               // = 55.5 px
  private static final double V_STEP      = HEX_H;                     // ≈ 64.1 px
  private static final double ODD_COL_OFF = HEX_H * 0.5;               // ≈ 32.1 px
  private static final int    COL_LBL     = 22;                        // left-label px

  private static final int GRID_W_PX = (int)(COL_LBL + (COLS - 1) * H_STEP + HEX_W + 4);
  private static final int GRID_X    = (W - GRID_W_PX) / 2;
  private static final int GRID_Y    = 285;

  /**
   * Board-shape mask: valid row range {@code [lo, hi]} (inclusive) per column.
   * All 9 columns span all 7 rows — the board is a full 7×9 rectangle of hexes.
   * The natural offset-column zigzag at the top and bottom edges is inherent to
   * the flat-top hex grid and does not represent missing cells.
   */
  private static final int[][] COL_ROW_RANGE = {
      {0, 6},  // col 0 — 7 cells
      {0, 6},  // col 1 — 7 cells  (odd, shifted down)
      {0, 6},  // col 2 — 7 cells
      {0, 6},  // col 3 — 7 cells
      {0, 6},  // col 4 — 7 cells
      {0, 6},  // col 5 — 7 cells
      {0, 6},  // col 6 — 7 cells
      {0, 6},  // col 7 — 7 cells
      {0, 6},  // col 8 — 7 cells
  };

  private static boolean isValidCell(int row, int col) {
    if (col < 0 || col >= COLS) return false;
    return row >= COL_ROW_RANGE[col][0] && row <= COL_ROW_RANGE[col][1];
  }

  // ── Palette ───────────────────────────────────────────────────────────────
  private static final Color BG          = new Color( 34,  85,  34);
  private static final Color PANEL_BG    = new Color(245, 230, 195);
  private static final Color HEADER_BG   = new Color( 20,  60,  20);
  private static final Color ACCENT      = new Color( 60, 120,  60);
  private static final Color TEXT        = new Color( 30,  30,  30);
  private static final Color TRACK_FILL  = new Color(180, 130,  50);
  private static final Color TRACK_EMPTY = new Color(180, 175, 155);
  private static final Color CELL_EMPTY  = new Color(220, 205, 165);
  private static final Color CELL_LINE   = new Color(160, 140, 110);
  private static final Color ENC_PLAIN   = new Color(196, 137,  60);
  private static final Color ENC_WATER   = new Color( 80, 150, 200);
  private static final Color ENC_ROCK    = new Color(150, 150, 120);
  private static final Color ENC_BOTH    = new Color(100, 140, 170);
  private static final Color ENC_BORDER  = new Color( 50,  35,  15);

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Renders the player's zoo board. Never returns null — falls back to an error image on failure.
   */
  public BufferedImage render(PlayerState player) {
    try {
      return renderBoard(player);
    } catch (Exception e) {
      log.error("Render failed for player {}", player.getDiscordName(), e);
      return errorImage(player.getDiscordName());
    }
  }

  // ── Main render ───────────────────────────────────────────────────────────

  private BufferedImage renderBoard(PlayerState player) {
    BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    setupHints(g);

    // Background
    g.setColor(BG);
    g.fillRect(0, 0, W, H);

    // Header bar
    g.setColor(HEADER_BG);
    g.fillRect(0, 0, W, 58);
    g.setColor(Color.WHITE);
    g.setFont(font(Font.BOLD, 24));
    g.drawString(player.getDiscordName() + "'s Zoo", 18, 38);
    g.setColor(Color.LIGHT_GRAY);
    g.setFont(font(Font.PLAIN, 13));
    g.drawString("Map: " + player.getMapId(), W - 115, 38);

    // Info panel
    g.setColor(PANEL_BG);
    g.fillRoundRect(14, 64, W - 28, 210, 12, 12);
    drawInfoPanel(g, player);

    // Grid section header
    sectionHeader(g, "Zoo Board  (col 0–" + (COLS - 1) + ", row 0–" + (ROWS - 1) + ")",
        GRID_X, GRID_Y - 10);

    // Grid background panel
    int gridH = (int)(HEX_H + (ROWS - 1) * V_STEP + ODD_COL_OFF) + 14;
    g.setColor(PANEL_BG);
    g.fillRoundRect(GRID_X - 4, GRID_Y - 4, GRID_W_PX + 8, gridH, 8, 8);

    // Column labels (above first valid row of each column)
    g.setColor(new Color(80, 60, 30));
    g.setFont(font(Font.BOLD, 11));
    for (int c = 0; c < COLS; c++) {
      double[] ctr = hexCenter(COL_ROW_RANGE[c][0], c);
      String lbl = String.valueOf(c);
      FontMetrics fm = g.getFontMetrics();
      int tw = fm.stringWidth(lbl);
      g.drawString(lbl, (int)(ctr[0] - tw * 0.5), (int)(ctr[1] - HEX_H * 0.5) - 4);
    }

    // Empty hex cells
    for (int c = 0; c < COLS; c++) {
      for (int r = COL_ROW_RANGE[c][0]; r <= COL_ROW_RANGE[c][1]; r++) {
        double[] ctr = hexCenter(r, c);
        Polygon hex = hexPolygon(ctr[0], ctr[1]);
        g.setColor(CELL_EMPTY);
        g.fillPolygon(hex);
        g.setColor(CELL_LINE);
        g.drawPolygon(hex);
      }
    }

    // Row labels (next to each row of column 2, which spans rows 0–6)
    g.setColor(new Color(80, 60, 30));
    g.setFont(font(Font.BOLD, 11));
    for (int r = 0; r < ROWS; r++) {
      double[] ctr = hexCenter(r, 2);
      g.drawString(String.valueOf(r), GRID_X + 3, (int)(ctr[1] + 4));
    }

    // Placed enclosures (drawn on top of empty cells)
    for (PlacedEnclosure enc : parseBoardState(player.getBoardState())) {
      drawEnclosure(g, enc);
    }

    g.dispose();
    return img;
  }

  // ── Hex geometry helpers ──────────────────────────────────────────────────

  /** Returns the pixel centre [x, y] of the hex at board position (row, col). */
  private static double[] hexCenter(int row, int col) {
    double x = GRID_X + COL_LBL + col * H_STEP + HEX_R;
    double y = GRID_Y + HEX_H * 0.5 + row * V_STEP + (col % 2 == 1 ? ODD_COL_OFF : 0);
    return new double[]{x, y};
  }

  /**
   * Builds a <b>flat-top</b> hexagon {@link Polygon} centred at (cx, cy).
   * Vertices are at angles 0°, 60°, 120°, 180°, 240°, 300° from the positive x-axis,
   * giving a flat horizontal edge at the top (between the 60° and 120° vertices)
   * and a flat horizontal edge at the bottom (between the 240° and 300° vertices).
   */
  private static Polygon hexPolygon(double cx, double cy) {
    Polygon poly = new Polygon();
    for (int i = 0; i < 6; i++) {
      double a = Math.toRadians(i * 60.0);
      poly.addPoint((int) Math.round(cx + HEX_R * Math.cos(a)),
                    (int) Math.round(cy + HEX_R * Math.sin(a)));
    }
    return poly;
  }

  // ── Enclosure drawing ─────────────────────────────────────────────────────

  private void drawEnclosure(Graphics2D g, PlacedEnclosure enc) {
    // Validate all hexes in the enclosure
    for (int c = enc.col(); c < enc.col() + enc.size(); c++) {
      if (!isValidCell(enc.row(), c)) {
        log.warn("Enclosure {} has invalid cell (row={} col={})", enc.id(), enc.row(), c);
        return;
      }
    }

    Set<String> tags = Set.of(enc.tags());
    boolean hasWater  = tags.contains("WATER");
    boolean hasRock   = tags.contains("ROCK");
    boolean hasAnimal = !enc.animalCardIds().isEmpty();

    Color fill;
    if      (hasWater && hasRock) fill = ENC_BOTH;
    else if (hasWater)            fill = ENC_WATER;
    else if (hasRock)             fill = ENC_ROCK;
    else                          fill = ENC_PLAIN;
    if (hasAnimal) fill = fill.darker();

    // Build the union of all hex shapes so shared edges disappear
    Area encArea = new Area();
    for (int c = enc.col(); c < enc.col() + enc.size(); c++) {
      double[] ctr = hexCenter(enc.row(), c);
      encArea.add(new Area(hexPolygon(ctr[0], ctr[1])));
    }

    g.setColor(fill);
    g.fill(encArea);
    g.setColor(ENC_BORDER);
    g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.draw(encArea);
    g.setStroke(new BasicStroke(1f));

    // Label anchors
    double[] first = hexCenter(enc.row(), enc.col());
    double[] last  = hexCenter(enc.row(), enc.col() + enc.size() - 1);
    double midX    = (first[0] + last[0]) * 0.5;
    double midY    = (first[1] + last[1]) * 0.5;

    // Enclosure ID — upper-left of first hex
    g.setColor(Color.WHITE);
    g.setFont(font(Font.BOLD, 11));
    g.drawString(enc.id(),
        (int)(first[0] - HEX_R * 0.8),
        (int)(first[1] - HEX_H * 0.28));

    // Size — lower-left of first hex
    g.setColor(new Color(255, 255, 200, 220));
    g.setFont(font(Font.PLAIN, 9));
    g.drawString("sz" + enc.size(),
        (int)(first[0] - HEX_R * 0.8),
        (int)(first[1] + HEX_H * 0.25));

    // Tag badges — upper-right of last hex
    int bx = (int)(last[0] + HEX_R) - 2;
    int by = (int)(last[1] - HEX_H * 0.38);
    if (hasRock) {
      bx -= 26;
      badge(g, "RCK", new Color(160, 160, 110, 200), bx, by);
    }
    if (hasWater) {
      bx -= 26;
      badge(g, "WTR", new Color(80, 160, 220, 200), bx, by);
    }

    // Animal label — centred over the enclosure
    if (hasAnimal) {
      String label = enc.animalCardIds().size() == 1
          ? enc.animalCardIds().get(0)
          : enc.animalCardIds().size() + " animals";
      g.setColor(Color.WHITE);
      g.setFont(font(Font.ITALIC, 10));
      FontMetrics fm = g.getFontMetrics();
      int lw = fm.stringWidth(label);
      g.drawString(label, (int)(midX - lw * 0.5), (int)(midY + 4));
    }
  }

  // ── Info panel ────────────────────────────────────────────────────────────

  private void drawInfoPanel(Graphics2D g, PlayerState player) {
    final int TOP = 72;

    sectionHeader(g, "Resources", 26, TOP);
    int ry = TOP + 24;
    resource(g, "Money",   String.valueOf(player.getMoney()),                              26,  ry);
    resource(g, "X",       String.valueOf(player.getXTokens()),                           160, ry);
    resource(g, "Rep",     String.valueOf(player.getReputation()),                         285, ry);
    resource(g, "Workers",
        player.getAssocWorkersAvailable() + "/" + player.getAssocWorkers(),                415, ry);

    sectionHeader(g, "Tracks", 545, TOP);
    compactTrackBar(g, "Appeal",       player.getAppeal(),       113, 220, 545, TOP + 24);
    compactTrackBar(g, "Conservation", player.getConservation(),  80, 220, 545, TOP + 52);

    sectionHeader(g, "Action Cards", 26, TOP + 110);
    actionCardStrip(g, player, 26, TOP + 128);
  }

  // ── Component helpers ─────────────────────────────────────────────────────

  private void compactTrackBar(Graphics2D g, String label, int value, int max,
      int barW, int x, int y) {
    double pct = max > 0 ? Math.min((double) value / max, 1.0) : 0;
    int barH = 17;
    g.setColor(TRACK_EMPTY);
    g.fillRoundRect(x + 100, y, barW, barH, 4, 4);
    g.setColor(TRACK_FILL);
    if (pct > 0) g.fillRoundRect(x + 100, y, (int)(barW * pct), barH, 4, 4);
    g.setColor(TEXT);
    g.setFont(font(Font.BOLD, 13));
    g.drawString(label + ":", x, y + 13);
    g.setFont(font(Font.PLAIN, 12));
    g.drawString(value + "/" + max, x + 100 + barW + 6, y + 13);
  }

  private void resource(Graphics2D g, String label, String value, int x, int y) {
    g.setColor(new Color(100, 80, 40));
    g.setFont(font(Font.PLAIN, 12));
    g.drawString(label, x, y);
    g.setColor(TEXT);
    g.setFont(font(Font.BOLD, 20));
    g.drawString(value, x, y + 22);
  }

  private void actionCardStrip(Graphics2D g, PlayerState player, int x, int y) {
    var cardOrder = player.getActionCardOrder();
    int cardW = 152, cardH = 58, gap = 10;
    for (int i = 0; i < 5; i++) {
      var card = cardOrder.getOrder().get(i);
      int strength = i + 1;
      int cx = x + i * (cardW + gap);
      int green = 70 + strength * 22;
      g.setColor(new Color(25, green, 25));
      g.fillRoundRect(cx, y, cardW, cardH, 8, 8);
      if (cardOrder.isUpgraded(card)) {
        g.setColor(new Color(255, 200, 0));
        g.fillOval(cx + cardW - 17, y + 4, 13, 13);
        g.setColor(Color.BLACK);
        g.setFont(font(Font.BOLD, 9));
        g.drawString("★", cx + cardW - 14, y + 14);
      }
      g.setColor(Color.WHITE);
      g.setFont(font(Font.BOLD, 13));
      g.drawString(card.emoji(), cx + 7, y + 24);
      g.setFont(font(Font.BOLD, 11));
      g.drawString(card.displayName(), cx + 7, y + 42);
      g.setColor(new Color(180, 255, 180));
      g.setFont(font(Font.BOLD, 17));
      g.drawString(String.valueOf(strength), cx + cardW - 22, y + 42);
    }
  }

  private void sectionHeader(Graphics2D g, String title, int x, int y) {
    FontMetrics fm = g.getFontMetrics(font(Font.BOLD, 12));
    int tw = fm.stringWidth(title);
    g.setColor(ACCENT);
    g.fillRoundRect(x, y - 16, tw + 16, 20, 4, 4);
    g.setColor(Color.WHITE);
    g.setFont(font(Font.BOLD, 12));
    g.drawString(title, x + 8, y - 2);
  }

  private void badge(Graphics2D g, String text, Color bg, int x, int y) {
    g.setColor(bg);
    g.fillRoundRect(x, y, 24, 13, 3, 3);
    g.setColor(Color.WHITE);
    g.setFont(font(Font.BOLD, 8));
    g.drawString(text, x + 2, y + 10);
  }

  // ── JSON parsing ──────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private List<PlacedEnclosure> parseBoardState(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      Map<String, Object> board = objectMapper.readValue(json, new TypeReference<>() {});
      List<Map<String, Object>> encs =
          (List<Map<String, Object>>) board.getOrDefault("enclosures", List.of());
      List<PlacedEnclosure> out = new ArrayList<>(encs.size());
      for (Map<String, Object> e : encs) {
        out.add(new PlacedEnclosure(
            strVal(e.get("id")),
            numVal(e.get("size"),  1),
            numVal(e.get("row"),   0),
            numVal(e.get("col"),   0),
            toStringArray(e.get("tags")),
            toStringList(e.get("animalCardIds"))));
      }
      return out;
    } catch (Exception ex) {
      log.warn("Could not parse boardState JSON: {}", ex.getMessage());
      return List.of();
    }
  }

  // ── Error fallback ────────────────────────────────────────────────────────

  private BufferedImage errorImage(String playerName) {
    BufferedImage img = new BufferedImage(400, 80, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(Color.DARK_GRAY);
    g.fillRect(0, 0, 400, 80);
    g.setColor(Color.RED);
    g.setFont(font(Font.BOLD, 15));
    g.drawString("Render failed for " + playerName, 10, 48);
    g.dispose();
    return img;
  }

  // ── Utilities ─────────────────────────────────────────────────────────────

  private static Font font(int style, int size) {
    return new Font("SansSerif", style, size);
  }

  private static String strVal(Object v) {
    return v == null ? "?" : v.toString();
  }

  private static int numVal(Object v, int def) {
    if (v instanceof Number n) return n.intValue();
    return def;
  }

  @SuppressWarnings("unchecked")
  private static String[] toStringArray(Object v) {
    if (v instanceof List<?> l) return l.stream().map(Object::toString).toArray(String[]::new);
    return new String[0];
  }

  @SuppressWarnings("unchecked")
  private static List<String> toStringList(Object v) {
    if (v instanceof List<?> l) return l.stream().map(Object::toString).toList();
    return List.of();
  }

  private static void setupHints(Graphics2D g) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
  }

  // ── Value objects ─────────────────────────────────────────────────────────

  private record PlacedEnclosure(
      String id, int size, int row, int col,
      String[] tags, List<String> animalCardIds) {}
}
