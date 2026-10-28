package studio.modroll.horseman.client;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.config.ConfigEditor;
import studio.modroll.horseman.config.ConfigSettings.Kind;
import studio.modroll.horseman.config.ConfigSettings.Setting;

/**
 * Edits both config files on one page. Save writes them and reloads the config, so the game uses the new values at
 * once. Opened from NeoForge's mod list and from Mod Menu on Fabric.
 *
 * <p>On a server the server's own file decides and never reaches this game, so the screen says so and shows only the
 * client file's settings.
 */
public final class HorsemanConfigScreen extends Screen {

    private static final String LANG = "critfall_horseman.config.";
    private static final int ROW_WIDTH = 310;
    private static final int CONTROL_WIDTH = 120;
    private static final int ROW_HEIGHT = 24;
    private static final int CONTROL_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 48;
    private static final int FOOTER_HEIGHT = 33;
    private static final int SPACING = 4;
    private static final int BUTTON_WIDTH = 150;
    private static final int LABEL_GAP = 8;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int DIM_COLOR = 0xA0A0A0;
    private static final int INVALID_COLOR = 0xFF5555;
    private static final int FIELD_LENGTH = 10;

    private final Screen parent;
    /** Null on a server. */
    private final ConfigEditor server;

    private final ConfigEditor client;
    private final Set<Setting> invalid = new HashSet<>();

    private Component status;
    private HeaderAndFooterLayout layout;
    private SettingsList list;
    private Button save;

    public HorsemanConfigScreen(Screen parent, boolean onServer) {
        super(Component.translatable(LANG + "title"));
        this.parent = parent;
        this.server = onServer ? null : ConfigEditor.server(CritfallHorseman.configFile());
        this.client = ConfigEditor.client(CritfallHorseman.clientConfigFile());
        boolean unreadable = client.unreadable() || (server != null && server.unreadable());
        if (onServer) {
            status = Component.translatable(LANG + "status.server");
        } else if (unreadable) {
            status = Component.translatable(LANG + "status.unreadable").withStyle(ChatFormatting.RED);
        } else {
            status = Component.translatable(LANG + "status.applies");
        }
    }

    /** Connected to a server that isn't this game's own, the server's file decides. */
    public static Screen forCurrentGame(Screen parent) {
        Minecraft minecraft = Minecraft.getInstance();
        return new HorsemanConfigScreen(
                parent, minecraft.getConnection() != null && minecraft.getSingleplayerServer() == null);
    }

    @Override
    protected void init() {
        // Fields are rebuilt from the editor's values, which are all valid.
        invalid.clear();
        double scroll = list == null ? 0 : list.getScrollAmount();
        layout = new HeaderAndFooterLayout(this, HEADER_HEIGHT, FOOTER_HEIGHT);

        LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(SPACING));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(title, font));
        header.addChild(new MultiLineTextWidget(status, font)
                .setMaxWidth(ROW_WIDTH)
                .setCentered(true)
                .setColor(DIM_COLOR));

        list = layout.addToContents(new SettingsList(minecraft, width, layout));
        if (server != null) {
            addRows(server);
        }
        addRows(client);

        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(LABEL_GAP));
        save = footer.addChild(Button.builder(Component.translatable(LANG + "save"), button -> save())
                .width(BUTTON_WIDTH)
                .tooltip(Tooltip.create(Component.translatable(LANG + "save.tooltip")))
                .build());
        footer.addChild(Button.builder(Component.translatable(LANG + "cancel"), button -> onClose())
                .width(BUTTON_WIDTH)
                .build());

        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
        list.setScrollAmount(scroll);
    }

    private void addRows(ConfigEditor editor) {
        String section = null;
        for (Setting setting : editor.settings()) {
            if (!setting.section().equals(section)) {
                section = setting.section();
                list.add(new SectionRow(
                        Component.translatable(LANG + "section." + section).withStyle(ChatFormatting.YELLOW)));
            }
            list.add(new SettingRow(setting, control(editor, setting)));
        }
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        list.updateSize(width, layout);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private AbstractWidget control(ConfigEditor editor, Setting setting) {
        Component label = label(setting);
        Tooltip tooltip = Tooltip.create(tooltip(setting));
        String value = editor.value(setting);
        if (setting.kind() == Kind.TOGGLE) {
            return CycleButton.booleanBuilder(Component.translatable(LANG + "on"), Component.translatable(LANG + "off"))
                    .withInitialValue(Boolean.parseBoolean(value))
                    .displayOnlyValue()
                    .withTooltip(on -> tooltip)
                    .create(
                            0,
                            0,
                            CONTROL_WIDTH,
                            CONTROL_HEIGHT,
                            label,
                            (button, on) -> editor.set(setting, on.toString()));
        }
        return field(editor, setting, label, tooltip, value);
    }

    /** A text field; input the loader would reject turns red, isn't kept, and holds Save back. */
    private EditBox field(ConfigEditor editor, Setting setting, Component label, Tooltip tooltip, String value) {
        EditBox box = new EditBox(font, 0, 0, CONTROL_WIDTH, CONTROL_HEIGHT, label);
        box.setMaxLength(FIELD_LENGTH);
        box.setValue(value);
        box.setTooltip(tooltip);
        box.setResponder(text -> {
            if (editor.set(setting, text)) {
                invalid.remove(setting);
                box.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
                box.setTooltip(tooltip);
            } else {
                invalid.add(setting);
                box.setTextColor(INVALID_COLOR);
                String problem = setting.kind() == Kind.DATE ? "invalid.date" : "invalid.chance";
                box.setTooltip(
                        Tooltip.create(Component.translatable(LANG + problem).withStyle(ChatFormatting.RED)));
            }
            save.active = invalid.isEmpty();
        });
        return box;
    }

    private void save() {
        try {
            if (server != null) {
                server.save(CritfallHorseman.configFile());
            }
            client.save(CritfallHorseman.clientConfigFile());
        } catch (IOException e) {
            CritfallHorseman.LOG.error("Could not save the config", e);
            status = Component.translatable(LANG + "status.save_failed").withStyle(ChatFormatting.RED);
            rebuildWidgets();
            return;
        }
        CritfallHorseman.reloadConfig();
        minecraft.setScreen(parent);
    }

    private static Component label(Setting setting) {
        return Component.translatable(LANG + "key." + setting.key());
    }

    private static Component tooltip(Setting setting) {
        return Component.translatable(LANG + "key." + setting.key() + ".tooltip");
    }

    /** The scrolling rows between the header and the footer. */
    private static final class SettingsList extends ContainerObjectSelectionList<Row> {

        SettingsList(Minecraft minecraft, int width, HeaderAndFooterLayout layout) {
            super(minecraft, width, layout.getContentHeight(), layout.getHeaderHeight(), ROW_HEIGHT);
            centerListVertically = false;
        }

        void add(Row row) {
            addEntry(row);
        }

        @Override
        public int getRowWidth() {
            return ROW_WIDTH;
        }
    }

    private abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {}

    private final class SectionRow extends Row {

        private final Component name;

        SectionRow(Component name) {
            this.name = name;
        }

        @Override
        public void render(
                GuiGraphics graphics,
                int index,
                int top,
                int left,
                int width,
                int height,
                int mouseX,
                int mouseY,
                boolean hovering,
                float partialTick) {
            graphics.drawCenteredString(font, name, left + width / 2, top + (height - font.lineHeight) / 2, TEXT_COLOR);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    /** The setting's name on the left, its control on the right; hovering the name shows the tooltip too. */
    private final class SettingRow extends Row {

        private final Setting setting;
        private final AbstractWidget control;

        SettingRow(Setting setting, AbstractWidget control) {
            this.setting = setting;
            this.control = control;
        }

        @Override
        public void render(
                GuiGraphics graphics,
                int index,
                int top,
                int left,
                int width,
                int height,
                int mouseX,
                int mouseY,
                boolean hovering,
                float partialTick) {
            int labelWidth = width - CONTROL_WIDTH - LABEL_GAP;
            int labelY = top + (CONTROL_HEIGHT - font.lineHeight) / 2;
            graphics.drawString(font, fitted(font, label(setting), labelWidth), left, labelY, TEXT_COLOR);
            control.setPosition(left + width - CONTROL_WIDTH, top);
            control.render(graphics, mouseX, mouseY, partialTick);
            if (hovering && mouseX < left + labelWidth && mouseY < top + CONTROL_HEIGHT) {
                setTooltipForNextRenderPass(Tooltip.create(tooltip(setting)).toCharSequence(minecraft));
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(control);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(control);
        }
    }

    /** The label, cut short with an ellipsis when a long translation would run under the control. */
    private static FormattedCharSequence fitted(Font font, Component label, int width) {
        if (font.width(label) <= width) {
            return label.getVisualOrderText();
        }
        String ellipsis = "...";
        String cut = font.plainSubstrByWidth(label.getString(), width - font.width(ellipsis));
        return Component.literal(cut + ellipsis).getVisualOrderText();
    }
}
