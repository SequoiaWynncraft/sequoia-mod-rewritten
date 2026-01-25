package star.sequoia2.client.types.text;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wynntils.utils.colors.CustomColor;
import com.wynntils.utils.wynn.WynnUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public final class StyledTextPart {
    private final String text;
    private final PartStyle style;
    private final StyledText parent;

    public StyledTextPart(String text, Style style, StyledText parent, Style parentStyle) {
        this.parent = parent;
        this.text = text;
        this.style = PartStyle.fromStyle(style, this, parentStyle);
    }

    StyledTextPart(StyledTextPart part, StyledText parent) {
        this.text = part.text;
        this.style = new PartStyle(part.style, this);
        this.parent = parent;
    }

    private StyledTextPart(StyledTextPart part, PartStyle style, StyledText parent) {
        this.text = part.text;
        this.style = style;
        this.parent = parent;
    }

    static List<StyledTextPart> fromCodedString(String codedString, Style style, StyledText parent, Style parentStyle) {
        List<StyledTextPart> parts = new ArrayList();
        Style currentStyle = style;
        StringBuilder currentString = new StringBuilder();
        boolean nextIsFormatting = false;
        StringBuilder hexColorFormatting = new StringBuilder();
        boolean clickEventPrefix = false;
        boolean hoverEventPrefix = false;
        String eventIndexString = "";

        for(char current : codedString.toCharArray()) {
            if (nextIsFormatting) {
                nextIsFormatting = false;
                if (parent != null) {
                    if (current == '[') {
                        clickEventPrefix = true;
                        continue;
                    }

                    if (current == '<') {
                        hoverEventPrefix = true;
                        continue;
                    }
                }

                if (current == '#') {
                    hexColorFormatting.append(current);
                } else {
                    Formatting formatting = Formatting.byCode(current);
                    if (formatting == null) {
                        currentString.append('§');
                        currentString.append(current);
                    } else {
                        if (!currentString.isEmpty()) {
                            if (style != Style.EMPTY) {
                                currentStyle = currentStyle.withClickEvent(style.getClickEvent()).withHoverEvent(style.getHoverEvent());
                            }

                            parts.add(new StyledTextPart(currentString.toString(), currentStyle, (StyledText)null, parentStyle));
                            currentString = new StringBuilder();
                        }

                        if (formatting.isColor()) {
                            currentStyle = Style.EMPTY.withColor(formatting);
                        } else {
                            currentStyle = currentStyle.withFormatting(formatting);
                        }
                    }
                }
            } else if (!clickEventPrefix && !hoverEventPrefix) {
                if (!hexColorFormatting.isEmpty()) {
                    hexColorFormatting.append(current);
                    if (hexColorFormatting.length() == 9) {
                        CustomColor customColor = CustomColor.fromHexString(hexColorFormatting.toString());
                        if (customColor == CustomColor.NONE) {
                            currentString.append(hexColorFormatting);
                        } else if (!currentString.isEmpty()) {
                            if (style != Style.EMPTY) {
                                currentStyle = currentStyle.withClickEvent(style.getClickEvent()).withHoverEvent(style.getHoverEvent());
                            }

                            parts.add(new StyledTextPart(currentString.toString(), currentStyle, (StyledText)null, parentStyle));
                            currentString = new StringBuilder();
                        }

                        currentStyle = currentStyle.withColor(customColor.asInt());
                        hexColorFormatting = new StringBuilder();
                    }
                } else if (current == 167) {
                    nextIsFormatting = true;
                } else {
                    currentString.append(current);
                }
            } else if (Character.isDigit(current)) {
                eventIndexString = eventIndexString + current;
            } else {
                Style oldStyle = null;
                if (clickEventPrefix && current == ']') {
                    ClickEvent clickEvent = parent.getClickEvent(Integer.parseInt(eventIndexString));
                    if (clickEvent != null) {
                        oldStyle = currentStyle;
                        currentStyle = currentStyle.withClickEvent(clickEvent);
                        clickEventPrefix = false;
                        eventIndexString = "";
                    }
                }

                if (hoverEventPrefix && current == '>') {
                    HoverEvent hoverEvent = parent.getHoverEvent(Integer.parseInt(eventIndexString));
                    if (hoverEvent != null) {
                        oldStyle = currentStyle;
                        currentStyle = currentStyle.withHoverEvent(hoverEvent);
                        hoverEventPrefix = false;
                        eventIndexString = "";
                    }
                }

                if (oldStyle != null) {
                    if (!currentString.isEmpty()) {
                        if (style != Style.EMPTY) {
                            currentStyle = currentStyle.withClickEvent(style.getClickEvent()).withHoverEvent(style.getHoverEvent());
                        }

                        parts.add(new StyledTextPart(currentString.toString(), oldStyle, (StyledText)null, parentStyle));
                        currentString = new StringBuilder();
                    }
                } else {
                    currentString.append((char)(clickEventPrefix ? '[' : '<'));
                    currentString.append(eventIndexString);
                    currentString.append(current);
                    clickEventPrefix = false;
                    hoverEventPrefix = false;
                    eventIndexString = "";
                }
            }
        }

        if (!currentString.isEmpty()) {
            if (style != Style.EMPTY) {
                currentStyle = currentStyle.withClickEvent(style.getClickEvent()).withHoverEvent(style.getHoverEvent());
            }

            parts.add(new StyledTextPart(currentString.toString(), currentStyle, (StyledText)null, parentStyle));
        }

        return parts;
    }

    static List<StyledTextPart> fromJson(JsonArray jsonArray) {
        if (jsonArray.isEmpty()) {
            return List.of(new StyledTextPart("", Style.EMPTY, (StyledText)null, Style.EMPTY));
        } else {
            List<StyledTextPart> parts = new ArrayList();

            for(JsonElement element : jsonArray) {
                if (element.isJsonObject()) {
                    Style style = Style.EMPTY;
                    JsonObject jsonObject = element.getAsJsonObject();
                    String text = jsonObject.get("text").getAsString();
                    if (jsonObject.has("bold")) {
                        style = style.withBold(true);
                    }

                    if (jsonObject.has("italic")) {
                        style = style.withItalic(true);
                    }

                    if (jsonObject.has("underline")) {
                        style = style.withUnderline(true);
                    }

                    if (jsonObject.has("strikethrough")) {
                        style = style.withStrikethrough(true);
                    }

                    if (jsonObject.has("font")) {
                        style = style.withFont(new StyleSpriteSource.Font(Identifier.ofVanilla(jsonObject.get("font").getAsString())));
                    }

                    if (jsonObject.has("color")) {
                        style = style.withColor(CustomColor.fromHexString(jsonObject.get("color").getAsString()).asInt());
                    }

                    if (jsonObject.has("margin-left")) {
                        String marginType = jsonObject.get("margin-left").getAsString();
                        if (marginType.equals("thin")) {
                            text = "À" + text;
                        } else if (marginType.equals("large")) {
                            text = "ÀÀÀÀ" + text;
                        }
                    }

                    parts.add(new StyledTextPart(text, style, (StyledText)null, Style.EMPTY));
                }
            }

            return parts;
        }
    }

    public String getString(PartStyle previousStyle, PartStyle.StyleType type) {
        String var10000 = this.style.asString(previousStyle, type);
        return var10000 + this.text;
    }

    public StyledText getParent() {
        return this.parent;
    }

    public PartStyle getPartStyle() {
        return this.style;
    }

    public StyledTextPart withStyle(PartStyle style) {
        return new StyledTextPart(this, style, this.parent);
    }

    public StyledTextPart withStyle(Function<PartStyle, PartStyle> function) {
        return this.withStyle((PartStyle)function.apply(this.style));
    }

    public MutableText getComponent() {
        return Text.literal(this.text).fillStyle(this.style.getStyle());
    }

    StyledTextPart asNormalized() {
        return new StyledTextPart(WynnUtils.normalizeBadString(this.text), this.style.getStyle(), this.parent, (Style)null);
    }

    StyledTextPart stripLeading() {
        return new StyledTextPart(this.text.stripLeading(), this.style.getStyle(), this.parent, (Style)null);
    }

    StyledTextPart stripTrailing() {
        return new StyledTextPart(this.text.stripTrailing(), this.style.getStyle(), this.parent, (Style)null);
    }

    boolean isEmpty() {
        return this.text.isEmpty();
    }

    boolean isBlank() {
        return this.text.isBlank();
    }

    public int length() {
        return this.text.length();
    }

    public String toString() {
        String var10000 = this.text;
        return "StyledTextPart[text=" + var10000 + ", style=" + String.valueOf(this.style) + "]";
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            StyledTextPart that = (StyledTextPart)o;
            return Objects.equals(this.text, that.text) && Objects.equals(this.style, that.style);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return Objects.hash(new Object[]{this.text, this.style});
    }
}

