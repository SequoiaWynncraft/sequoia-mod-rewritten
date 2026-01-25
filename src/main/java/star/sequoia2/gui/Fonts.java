package star.sequoia2.gui;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.logging.LogUtils;
import star.sequoia2.accessors.ConfigurationAccessor;
import star.sequoia2.settings.types.Option;
import net.minecraft.client.font.*;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.util.Identifier;
import org.apache.commons.compress.utils.FileNameUtils;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

import static star.sequoia2.client.SeqClient.mc;

public class Fonts implements ConfigurationAccessor {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final LinkedHashMap<String, TextRenderer> fontRenderers = new LinkedHashMap<>();

    public LinkedHashSet<Font> fonts() {
        return fontRenderers.entrySet().stream().map(entry -> new Font(entry.getKey(), entry.getValue())).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void initializeFonts() throws IOException {
        fontRenderers.put("Minecraft", mc.textRenderer);
        loadFontFromResource("Arial", "/assets/seq/arialmdm.ttf", 8.5f, 2.0f, TrueTypeFontLoader.Shift.NONE, "");

        File fontsDirectory = new File(configuration().configDirectory(), "fonts");
        if (!fontsDirectory.exists()) {
            if (!fontsDirectory.mkdirs()) {
                throw new RuntimeException("Failed to create directory \"" + fontsDirectory.getName() + "\"");
            }
        }

        File[] files = fontsDirectory.listFiles();
        if (files == null) {
            return;
        }
        Arrays.stream(files)
                .filter(file -> file.isFile() && FileNameUtils.getExtension(file.toPath()).equals("ttf"))
                .forEach(file -> {
                    java.awt.Font font;
                    try {
                        font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, file);
                        LOGGER.info("Loaded a font.");
                    } catch (Throwable e) {
                        LOGGER.warn("Failed to load font at {}", file.getAbsolutePath(), e);
                        return;
                    }
                    loadFont(font.getFontName(), file, 8.5f, 2.0f, TrueTypeFontLoader.Shift.NONE, "");
                });
    }

    public void loadFontFromResource(String fontKey, String resourcePath, float size, float oversample, TrueTypeFontLoader.Shift shift, String skip) {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                LOGGER.warn("Could not find resource: {}", resourcePath);
                return;
            }
            ByteBuffer byteBuffer = TextureUtil.readResource(inputStream);
            byteBuffer.flip();
            TrueTypeFont ttfFont;
            FT_Face ftFace = null;
            try {
                synchronized(FreeTypeUtil.LOCK) {
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        PointerBuffer pointerBuffer = stack.mallocPointer(1);
                        FreeTypeUtil.checkFatalError(FreeType.FT_New_Memory_Face(FreeTypeUtil.initialize(), byteBuffer, 0L, pointerBuffer), "Initializing font face");
                        ftFace = FT_Face.create(pointerBuffer.get());
                    }
                    String fontFormat = FreeType.FT_Get_Font_Format(ftFace);
                    if (!"TrueType".equals(fontFormat)) {
                        throw new IOException("Font is not in TTF format, was " + fontFormat);
                    }
                    FreeTypeUtil.checkFatalError(FreeType.FT_Select_Charmap(ftFace, FreeType.FT_ENCODING_UNICODE), "Finding Unicode charmap");
                    ttfFont = new TrueTypeFont(byteBuffer, ftFace, size, oversample, shift.x(), shift.y(), skip);
                }
            } catch (Exception ex) {
                synchronized(FreeTypeUtil.LOCK) {
                    if (ftFace != null) {
                        FreeType.FT_Done_Face(ftFace);
                    }
                }
                MemoryUtil.memFree(byteBuffer);
                throw ex;
            }

            List<net.minecraft.client.font.Font.FontFilterPair> filters = new ArrayList<>();
            filters.add(new net.minecraft.client.font.Font.FontFilterPair(ttfFont, FontFilterType.FilterMap.NO_FILTER));

            Identifier fontId = Identifier.of("seq", "font/" + fontKey.toLowerCase());
            GlyphBaker glyphBaker = new GlyphBaker(mc.getTextureManager(), fontId);
            FontStorage fontStorage = new FontStorage(glyphBaker);
            fontStorage.setFonts(filters, Set.of());

            TextRenderer.GlyphsProvider provider = new TextRenderer.GlyphsProvider() {
                private final GlyphProvider glyphProvider = fontStorage.getGlyphs(false);
                private final EffectGlyph rectangle = fontStorage.getRectangleBakedGlyph();

                @Override
                public GlyphProvider getGlyphs(StyleSpriteSource source) {
                    return glyphProvider;
                }

                @Override
                public EffectGlyph getRectangleGlyph() {
                    return rectangle;
                }
            };

            TextRenderer textRenderer = new TextRenderer(provider);
            fontRenderers.put(fontKey, textRenderer);
            LOGGER.info("Loaded bundled font '{}' from resource {}", fontKey, resourcePath);
        } catch (IOException e) {
            LOGGER.warn("Failed to load font from resource {}", resourcePath, e);
        }
    }


    public void loadFont(String fontName, File fontFile, float size, float oversample, TrueTypeFontLoader.Shift shift, String skip) {
        net.minecraft.client.font.Font font;
        List<net.minecraft.client.font.Font.FontFilterPair> list = new ArrayList<net.minecraft.client.font.Font.FontFilterPair>();
        try {
            font = createTTF(fontFile, size, oversample, TrueTypeFontLoader.Shift.NONE, "");
            list.add(new net.minecraft.client.font.Font.FontFilterPair(font, FontFilterType.FilterMap.NO_FILTER));
        } catch (IOException e) {
            LOGGER.warn("Failed to load font at {}", fontFile.getAbsolutePath(), e);
            return;
        }
        String safeName = StringUtils.replace(FileNameUtils.getBaseName(fontFile.toPath()), " ", "");
        safeName = StringUtils.replace(safeName, "-", "").toLowerCase();
        Identifier fontId = Identifier.of("seq", "font/" + safeName);
        GlyphBaker glyphBaker = new GlyphBaker(mc.getTextureManager(), fontId);
        FontStorage fontStorage = new FontStorage(glyphBaker);
        fontStorage.setFonts(list, Set.of());
        TextRenderer.GlyphsProvider provider = new TextRenderer.GlyphsProvider() {
            private final GlyphProvider glyphProvider = fontStorage.getGlyphs(false);
            private final EffectGlyph rectangle = fontStorage.getRectangleBakedGlyph();

            @Override
            public GlyphProvider getGlyphs(StyleSpriteSource source) {
                return glyphProvider;
            }

            @Override
            public EffectGlyph getRectangleGlyph() {
                return rectangle;
            }
        };
        TextRenderer textRenderer = new TextRenderer(provider);
        fontRenderers.put(fontName, textRenderer);
    }

    private TrueTypeFont createTTF(File fontFile, float size, float oversample, TrueTypeFontLoader.Shift shift, String skip) throws IOException {
        FT_Face fT_Face = null;
        ByteBuffer byteBuffer = null;
        try {
            InputStream inputStream = new FileInputStream(fontFile);
            TrueTypeFont font;
            try {
                byteBuffer = TextureUtil.readResource(inputStream);
                byteBuffer.flip();
                synchronized(FreeTypeUtil.LOCK) {
                    try (MemoryStack memoryStack = MemoryStack.stackPush()) {
                        PointerBuffer pointerBuffer = memoryStack.mallocPointer(1);
                        FreeTypeUtil.checkFatalError(FreeType.FT_New_Memory_Face(FreeTypeUtil.initialize(), byteBuffer, 0L, pointerBuffer), "Initializing font face");
                        fT_Face = FT_Face.create(pointerBuffer.get());
                    }
                    String string = FreeType.FT_Get_Font_Format(fT_Face);
                    if (!"TrueType".equals(string)) {
                        throw new IOException("Font is not in TTF format, was " + string);
                    }
                    FreeTypeUtil.checkFatalError(FreeType.FT_Select_Charmap(fT_Face, FreeType.FT_ENCODING_UNICODE), "Find unicode charmap");
                    font = new TrueTypeFont(byteBuffer, fT_Face, size, oversample, shift.x(), shift.y(), skip);
                }
            } finally {
                inputStream.close();
            }

            return font;
        } catch (Exception ex) {
            synchronized(FreeTypeUtil.LOCK) {
                if (fT_Face != null) {
                    FreeType.FT_Done_Face(fT_Face);
                }
            }
            MemoryUtil.memFree(byteBuffer);
            throw ex;
        }
    }

    public record Font(String name, TextRenderer renderer) implements Option {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Font font = (Font) o;
            return Objects.equals(name, font.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }
}
