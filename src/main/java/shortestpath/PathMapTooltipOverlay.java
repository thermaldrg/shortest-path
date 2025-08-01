package shortestpath;

import com.google.inject.Inject;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class PathMapTooltipOverlay extends Overlay {
    private static final int TOOLTIP_OFFSET_HEIGHT = 25;
    private static final int TOOLTIP_OFFSET_WIDTH = 15;
    private static final int TOOLTIP_PADDING_HEIGHT = 1;
    private static final int TOOLTIP_PADDING_WIDTH = 2;
    private static final int TOOLTIP_TEXT_OFFSET_HEIGHT = -2;

    private final Client client;
    private final ShortestPathPlugin plugin;

    @Inject
    private PathMapTooltipOverlay(Client client, ShortestPathPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(Overlay.PRIORITY_HIGHEST);
        setLayer(OverlayLayer.MANUAL);
        drawAfterInterface(InterfaceID.WORLD_MAP);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!plugin.drawMap || client.getWidget(ComponentID.WORLD_MAP_MAPVIEW) == null) {
            return null;
        }

        if (plugin.getPathfinder() != null) {
            List<Integer> path = plugin.getPathfinder().getPath();
            Point cursorPos = client.getMouseCanvasPosition();
            int finalDestination = path.size() > 0 ? path.get(path.size() - 1) : WorldPointUtil.UNDEFINED;
            for (int i = 0; i < path.size(); i++) {
                int nextPoint = WorldPointUtil.UNDEFINED;
                if (path.size() > i + 1) {
                    nextPoint = path.get(i + 1);
                }
                if (drawTooltip(graphics, cursorPos, path.get(i), nextPoint, i + 1, finalDestination)) {
                    return null;
                }
            }
            for (int target : plugin.getPathfinder().getTargets()) {
                if (path.size() > 0 && target != path.get(path.size() - 1)) {
                    drawTooltip(graphics, cursorPos, target, WorldPointUtil.UNDEFINED, -1, finalDestination);
                }
            }
        }

        return null;
    }

    private boolean drawTooltip(Graphics2D graphics, Point cursorPos, int point, int nextPoint, int n, int finalDestination) {
        int offsetPoint = WorldPointUtil.dxdy(point, 1, -1);
        int startX = plugin.mapWorldPointToGraphicsPointX(point);
        int startY = plugin.mapWorldPointToGraphicsPointY(point);
        int endX = plugin.mapWorldPointToGraphicsPointX(offsetPoint);
        int endY = plugin.mapWorldPointToGraphicsPointY(offsetPoint);

        if (startX == Integer.MIN_VALUE || startY == Integer.MIN_VALUE ||
                endX == Integer.MIN_VALUE || endY == Integer.MIN_VALUE) {
            return false;
        }

        int width = endX - startX;

        if (cursorPos.getX() < (startX - width / 2) || cursorPos.getX() > (endX - width / 2) ||
                cursorPos.getY() < (startY - width / 2) || cursorPos.getY() > (endY - width / 2)) {
            return false;
        }

        List<String> rows = new ArrayList<>(Arrays.asList("Shortest path:",
                n < 0 ? "Unused target" : ("Step " + n + " of " + plugin.getPathfinder().getPath().size())));
        if (nextPoint != WorldPointUtil.UNDEFINED) {
            for (Transport transport : plugin.getTransports().getOrDefault(point, new HashSet<>())) {
                if (nextPoint == transport.getDestination()
                        && transport.getDisplayInfo() != null && !transport.getDisplayInfo().isEmpty()) {
                    String transportText = transport.getDisplayInfo();


                    if (plugin.showTilesSaved && TransportType.isResourceMovement(transport.getType())) {
                        Player localPlayer = client.getLocalPlayer();
                        if (localPlayer != null) {
                            int currentLocation = WorldPointUtil.fromLocalInstance(client, localPlayer.getLocalLocation());
                            List<Integer> path = plugin.getPathfinder().getPath();

                            int remainingPathDistance = calculatePathDistance(path, transport.getDestination(), finalDestination);

                            int walkingDistance;
                            if (plugin.showBothPaths && plugin.getWalkingPathfinder() != null &&
                                    plugin.getWalkingPathfinder().isDone() && plugin.getWalkingPathfinder().getPath() != null) {
                                // Use actual walking path distance
                                List<Integer> walkingPath = plugin.getWalkingPathfinder().getPath();
                                walkingDistance = calculatePathDistance(walkingPath, currentLocation, finalDestination);
                                if (walkingDistance == -1) {
                                    // Fallback to straight-line if point not found in walking path
                                    walkingDistance = WorldPointUtil.distanceBetween(currentLocation, finalDestination);
                                }
                            } else {
                                // Use straight-line distance as approximation
                                walkingDistance = WorldPointUtil.distanceBetween(currentLocation, finalDestination);
                            }

                            if (remainingPathDistance != -1 && walkingDistance > remainingPathDistance) {
                                int tilesSaved = walkingDistance - remainingPathDistance;
                                if (tilesSaved > 0) {
                                    transportText += " (" + tilesSaved + " tiles saved)";
                                }
                            }
                        }
                    }

                    rows.add(transportText);
                    break;
                }
            }
        }

        graphics.setFont(FontManager.getRunescapeFont());
        FontMetrics fm = graphics.getFontMetrics();
        int tooltipHeight = fm.getHeight();
        int tooltipWidth = rows.stream().map(fm::stringWidth).max(Integer::compareTo).get();

        int clippedHeight = tooltipHeight * rows.size() + TOOLTIP_PADDING_HEIGHT * 2;
        int clippedWidth = tooltipWidth + TOOLTIP_PADDING_WIDTH * 2;

        Rectangle worldMapBounds = client.getWidget(ComponentID.WORLD_MAP_MAPVIEW).getBounds();
        int worldMapRightBoundary = worldMapBounds.width + worldMapBounds.x;
        int worldMapBottomBoundary = worldMapBounds.height + worldMapBounds.y;

        int drawPointX = startX + TOOLTIP_OFFSET_WIDTH;
        int drawPointY = startY;
        if (drawPointX + clippedWidth > worldMapRightBoundary) {
            drawPointX = worldMapRightBoundary - clippedWidth;
        }
        if (drawPointY + clippedHeight > worldMapBottomBoundary) {
            drawPointY = startY - clippedHeight;
        }
        drawPointY += TOOLTIP_OFFSET_HEIGHT;

        int tooltipRectX = drawPointX - TOOLTIP_PADDING_WIDTH;
        int tooltipRectY = drawPointY - TOOLTIP_PADDING_HEIGHT;

        graphics.setColor(JagexColors.TOOLTIP_BACKGROUND);
        graphics.fillRect(tooltipRectX, tooltipRectY, clippedWidth, clippedHeight);

        graphics.setColor(JagexColors.TOOLTIP_BORDER);
        graphics.drawRect(tooltipRectX, tooltipRectY, clippedWidth, clippedHeight);

        graphics.setColor(JagexColors.TOOLTIP_TEXT);
        for (int i = 0; i < rows.size(); i++) {
            graphics.drawString(rows.get(i), drawPointX, drawPointY + TOOLTIP_TEXT_OFFSET_HEIGHT + (i + 1) * tooltipHeight);
        }

        return true;
    }

    private int calculatePathDistance(List<Integer> path, int startPoint, int endPoint) {
        int startIndex = -1;
        int endIndex = -1;

        for (int i = 0; i < path.size(); i++) {
            if (path.get(i) == startPoint) {
                startIndex = i;
            }
            if (path.get(i) == endPoint) {
                endIndex = i;
            }
        }

        if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
            return -1;
        }

        int totalDistance = 0;
        for (int i = startIndex + 1; i <= endIndex; i++) {
            totalDistance += WorldPointUtil.distanceBetween(path.get(i - 1), path.get(i));
        }

        return totalDistance;
    }
}
