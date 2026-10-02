package net.coreprotect.model;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

import org.bukkit.block.Sign;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.utility.BlockUtils;

public final class SignState {
    private final String[] lines;
    private final int color;
    private final int colorSecondary;
    private final boolean frontGlowing;
    private final boolean backGlowing;
    private final boolean waxed;

    public SignState(String[] lines, int color, int colorSecondary, boolean frontGlowing, boolean backGlowing, boolean waxed) {
        if (lines.length != 8) {
            throw new IllegalArgumentException("A sign snapshot requires eight lines");
        }
        this.lines = lines.clone();
        this.color = color;
        this.colorSecondary = colorSecondary;
        this.frontGlowing = frontGlowing;
        this.backGlowing = backGlowing;
        this.waxed = waxed;
    }

    public static SignState read(ResultSet result) throws SQLException {
        String[] lines = new String[8];
        for (int index = 0; index < lines.length; index++) {
            lines[index] = result.getString("line_" + (index + 1));
        }
        int data = result.getInt("data");
        return new SignState(lines, result.getInt("color"), result.getInt("color_secondary"), BlockUtils.isSideGlowing(true, data), BlockUtils.isSideGlowing(false, data), result.getInt("waxed") == 1);
    }

    public static SignState capture(Sign sign) {
        String[] lines = new String[8];
        for (int index = 0; index < lines.length; index++) {
            lines[index] = PaperAdapter.ADAPTER.getLine(sign, index);
        }
        return new SignState(lines, BukkitAdapter.ADAPTER.getColor(sign, true), BukkitAdapter.ADAPTER.getColor(sign, false), BukkitAdapter.ADAPTER.isGlowing(sign, true), BukkitAdapter.ADAPTER.isGlowing(sign, false), BukkitAdapter.ADAPTER.isWaxed(sign));
    }

    public String getLine(int index) {
        return lines[index] == null ? "" : lines[index];
    }

    public int getColor(boolean front) {
        return front ? color : colorSecondary;
    }

    public boolean isGlowing(boolean front) {
        return front ? frontGlowing : backGlowing;
    }

    public boolean isWaxed() {
        return waxed;
    }

    public void apply(Sign sign) {
        for (int index = 0; index < lines.length; index++) {
            BukkitAdapter.ADAPTER.setLine(sign, index, getLine(index));
        }
        for (int face = 0; face < 2; face++) {
            boolean front = face == 0;
            if (getColor(front) > 0) {
                BukkitAdapter.ADAPTER.setColor(sign, front, getColor(front));
            }
            BukkitAdapter.ADAPTER.setGlowing(sign, front, isGlowing(front));
        }
        BukkitAdapter.ADAPTER.setWaxed(sign, waxed);
    }

    public boolean hasChanges(SignState other, boolean front) {
        int firstLine = front ? 0 : 4;
        for (int index = firstLine; index < firstLine + 4; index++) {
            if (!Objects.equals(getLine(index), other.getLine(index))) {
                return true;
            }
        }
        return getColor(front) != other.getColor(front) || isGlowing(front) != other.isGlowing(front) || waxed != other.waxed;
    }

    public boolean matchesChanges(SignState source, SignState target, boolean front) {
        int firstLine = front ? 0 : 4;
        for (int index = firstLine; index < firstLine + 4; index++) {
            if (!Objects.equals(source.getLine(index), target.getLine(index)) && !Objects.equals(getLine(index), source.getLine(index))) {
                return false;
            }
        }
        return (source.getColor(front) == target.getColor(front) || getColor(front) == source.getColor(front))
                && (source.isGlowing(front) == target.isGlowing(front) || isGlowing(front) == source.isGlowing(front))
                && (source.waxed == target.waxed || waxed == source.waxed);
    }

    public void applyChanges(Sign sign, SignState source, boolean front) {
        int firstLine = front ? 0 : 4;
        for (int index = firstLine; index < firstLine + 4; index++) {
            if (!Objects.equals(source.getLine(index), getLine(index))) {
                BukkitAdapter.ADAPTER.setLine(sign, index, getLine(index));
            }
        }
        if (source.getColor(front) != getColor(front) && getColor(front) > 0) {
            BukkitAdapter.ADAPTER.setColor(sign, front, getColor(front));
        }
        if (source.isGlowing(front) != isGlowing(front)) {
            BukkitAdapter.ADAPTER.setGlowing(sign, front, isGlowing(front));
        }
        if (source.waxed != waxed) {
            BukkitAdapter.ADAPTER.setWaxed(sign, waxed);
        }
    }
}
