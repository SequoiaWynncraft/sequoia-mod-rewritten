package star.sequoia2.client.types.text;

import com.wynntils.utils.colors.CustomColor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class PartStyle {

    private static final Int2ObjectMap<Formatting> INTEGER_TO_CHATFORMATTING_MAP =
            Arrays.stream(Formatting.values())
                    .filter(Formatting::isColor)
                    .collect(
                            () -> new Int2ObjectOpenHashMap<>(Formatting.values().length),
                            (map, cf) -> map.put(cf.getColorValue() | 0xFF000000, cf),
                            Map::putAll
                    );

    private final StyledTextPart owner;
    @Getter
    private final CustomColor color;
    private final CustomColor shadowColor;
    private final boolean obfuscated;
    private final boolean bold;
    private final boolean strikethrough;
    private final boolean underlined;
    private final boolean italic;
    @Getter
    private final ClickEvent clickEvent;
    @Getter
    private final HoverEvent hoverEvent;
    private final StyleSpriteSource font;

    public enum StyleType { INCLUDE_EVENTS, DEFAULT, NONE }

    private PartStyle(
            StyledTextPart owner,
            CustomColor color,
            CustomColor shadowColor,
            boolean obfuscated,
            boolean bold,
            boolean strikethrough,
            boolean underlined,
            boolean italic,
            ClickEvent clickEvent,
            HoverEvent hoverEvent,
            StyleSpriteSource font
    ) {
        this.owner = owner;
        this.color = color;
        this.shadowColor = shadowColor;
        this.obfuscated = obfuscated;
        this.bold = bold;
        this.strikethrough = strikethrough;
        this.underlined = underlined;
        this.italic = italic;
        this.clickEvent = clickEvent;
        this.hoverEvent = hoverEvent;
        this.font = font;
    }

    PartStyle(PartStyle other, StyledTextPart owner) {
        this(
                owner,
                other.color,
                other.shadowColor,
                other.obfuscated,
                other.bold,
                other.strikethrough,
                other.underlined,
                other.italic,
                other.clickEvent,
                other.hoverEvent,
                other.font
        );
    }

    static PartStyle fromStyle(Style style, StyledTextPart owner, Style parentStyle) {
        Style inherited = (parentStyle == null) ? style : style.withParent(parentStyle);

        return new PartStyle(
                owner,
                inherited.getColor() == null ? CustomColor.NONE : CustomColor.fromInt(inherited.getColor().getRgb() | 0xFF000000),
                inherited.getShadowColor() == null ? CustomColor.NONE : CustomColor.fromInt(inherited.getShadowColor() | 0xFF000000),
                inherited.isObfuscated(),
                inherited.isBold(),
                inherited.isStrikethrough(),
                inherited.isUnderlined(),
                inherited.isItalic(),
                inherited.getClickEvent(),
                inherited.getHoverEvent(),
                inherited.getFont()
        );
    }

    /** Convert this PartStyle into a Minecraft Style */
    public Style getStyle() {
        Style s = Style.EMPTY;

        if (color != CustomColor.NONE)
            s = s.withColor(TextColor.fromRgb(color.asInt() & 0xFFFFFF));

        if (shadowColor != CustomColor.NONE)
            s = s.withShadowColor(shadowColor.asInt() & 0xFFFFFF);

        if (bold)         s = s.withBold(true);
        if (italic)       s = s.withItalic(true);
        if (underlined)   s = s.withUnderline(true);
        if (strikethrough)s = s.withStrikethrough(true);
        if (obfuscated)   s = s.withObfuscated(true);

        if (clickEvent != null) s = s.withClickEvent(clickEvent);
        if (hoverEvent != null) s = s.withHoverEvent(hoverEvent);
        if (font != null)       s = s.withFont(font);

        return s;
    }

    public String asString(PartStyle prev, StyleType type) {
        if (type == StyleType.NONE) return "";

        StringBuilder sb = new StringBuilder();
        boolean skipFormatting = false;

        if (prev != null && (this.color == CustomColor.NONE || this.color.equals(prev.color))) {
            String diff = tryConstructDifference(prev, type == StyleType.INCLUDE_EVENTS);
            if (diff != null) {
                sb.append(diff);
                skipFormatting = true;
            } else {
                sb.append('§').append(Formatting.RESET.getCode());
            }
        }

        if (!skipFormatting) {
            if (color != CustomColor.NONE) {
                Formatting base = INTEGER_TO_CHATFORMATTING_MAP.get(color.asInt());
                if (base != null) sb.append('§').append(base.getCode());
                else sb.append('§').append(color.toHexString());
            }

            if (obfuscated)      sb.append('§').append(Formatting.OBFUSCATED.getCode());
            if (bold)            sb.append('§').append(Formatting.BOLD.getCode());
            if (strikethrough)   sb.append('§').append(Formatting.STRIKETHROUGH.getCode());
            if (underlined)      sb.append('§').append(Formatting.UNDERLINE.getCode());
            if (italic)          sb.append('§').append(Formatting.ITALIC.getCode());

            if (type == StyleType.INCLUDE_EVENTS) {
                if (clickEvent != null)
                    sb.append('§').append("[").append(owner.getParent().getClickEventIndex(clickEvent)).append("]");

                if (hoverEvent != null)
                    sb.append('§').append("<").append(owner.getParent().getHoverEventIndex(hoverEvent)).append(">");
            }
        }
        return sb.toString();
    }

    private String tryConstructDifference(PartStyle old, boolean includeEvents) {
        StringBuilder add = new StringBuilder();

        int oldColor = old.color.asInt();
        int newColor = this.color.asInt();

        if (oldColor == -1) {
            if (newColor != -1) {
                Optional<Formatting> fmt = Arrays.stream(Formatting.values())
                        .filter(f -> f.isColor() && newColor == (f.getColorValue() | 0xFF000000))
                        .findFirst();
                fmt.ifPresent(add::append);
            }
        } else if (oldColor != newColor) {
            return null;
        }

        if (old.obfuscated != this.obfuscated) {
            if (!old.obfuscated && this.obfuscated) add.append(Formatting.OBFUSCATED);
            else return null;
        }
        if (old.bold != this.bold) {
            if (!old.bold && this.bold) add.append(Formatting.BOLD);
            else return null;
        }
        if (old.strikethrough != this.strikethrough) {
            if (!old.strikethrough && this.strikethrough) add.append(Formatting.STRIKETHROUGH);
            else return null;
        }
        if (old.underlined != this.underlined) {
            if (!old.underlined && this.underlined) add.append(Formatting.UNDERLINE);
            else return null;
        }
        if (old.italic != this.italic) {
            if (!old.italic && this.italic) add.append(Formatting.ITALIC);
            else return null;
        }

        if (includeEvents) {
            if (!Objects.equals(old.clickEvent, this.clickEvent)) {
                if (old.clickEvent != null && this.clickEvent == null) return null;
                add.append('§').append("[").append(owner.getParent().getClickEventIndex(this.clickEvent)).append("]");
            }
            if (!Objects.equals(old.hoverEvent, this.hoverEvent)) {
                if (old.hoverEvent != null && this.hoverEvent == null) return null;
                add.append('§').append("<").append(owner.getParent().getHoverEventIndex(this.hoverEvent)).append(">");
            }
        }
        return add.toString();
    }

    public PartStyle withFont(Identifier id) {
        StyleSpriteSource f = (id == null) ? null : new StyleSpriteSource.Font(id);
        return new PartStyle(owner, color, shadowColor, obfuscated, bold, strikethrough, underlined,
                italic, clickEvent, hoverEvent, f);
    }

    public PartStyle withShadowColor(CustomColor shadow) {
        return new PartStyle(owner, color, shadow, obfuscated, bold, strikethrough, underlined,
                italic, clickEvent, hoverEvent, font);
    }

    public PartStyle withBold(boolean b) { return new PartStyle(owner, color, shadowColor, obfuscated, b, strikethrough, underlined, italic, clickEvent, hoverEvent, font); }
    public PartStyle withItalic(boolean i) { return new PartStyle(owner, color, shadowColor, obfuscated, bold, strikethrough, underlined, i, clickEvent, hoverEvent, font); }
    public PartStyle withUnderlined(boolean u){ return new PartStyle(owner, color, shadowColor, obfuscated, bold, strikethrough, u, italic, clickEvent, hoverEvent, font); }
    public PartStyle withStrikethrough(boolean s){ return new PartStyle(owner, color, shadowColor, obfuscated, bold, s, underlined, italic, clickEvent, hoverEvent, font); }
    public PartStyle withObfuscated(boolean o){ return new PartStyle(owner, color, shadowColor, o, bold, strikethrough, underlined, italic, clickEvent, hoverEvent, font); }

    public PartStyle withColor(CustomColor c){ return new PartStyle(owner, c, shadowColor, obfuscated, bold, strikethrough, underlined, italic, clickEvent, hoverEvent, font); }

    public PartStyle withClickEvent(ClickEvent e){ return new PartStyle(owner, color, shadowColor, obfuscated, bold, strikethrough, underlined, italic, e, hoverEvent, font); }
    public PartStyle withHoverEvent(HoverEvent e){ return new PartStyle(owner, color, shadowColor, obfuscated, bold, strikethrough, underlined, italic, clickEvent, e, font); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PartStyle that)) return false;
        return obfuscated == that.obfuscated &&
                bold == that.bold &&
                strikethrough == that.strikethrough &&
                underlined == that.underlined &&
                italic == that.italic &&
                Objects.equals(color, that.color) &&
                Objects.equals(clickEvent, that.clickEvent) &&
                Objects.equals(hoverEvent, that.hoverEvent) &&
                Objects.equals(font, that.font);
    }

    @Override
    public int hashCode() {
        return Objects.hash(color, obfuscated, bold, strikethrough, underlined, italic, clickEvent, hoverEvent, font);
    }

    @Override
    public String toString() {
        return "PartStyle{" +
                "color=" + color +
                ", bold=" + bold +
                ", italic=" + italic +
                ", underlined=" + underlined +
                ", strikethrough=" + strikethrough +
                ", obfuscated=" + obfuscated +
                ", click=" + clickEvent +
                ", hover=" + hoverEvent +
                ", font=" + font +
                '}';
    }
}
