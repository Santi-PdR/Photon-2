package com.lowdragmc.photon.client;

import com.lowdragmc.photon.PhotonConfig;
import com.lowdragmc.photon.client.compat.iris.IrisCompositeMode;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.FXCompositeMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/** Forge 1.20.1 equivalent of Photon 26.2's mod-list configuration screen. */
public final class PhotonConfigScreen extends Screen {
    private static final int LEFT = 24;
    private static final int TOP = 48;
    private static final int ROW_HEIGHT = 24;
    private final @Nullable Screen parent;
    private final List<Component> labels = new java.util.ArrayList<>();

    public PhotonConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("photon.configuration.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        labels.clear();
        var config = PhotonConfig.INSTANCE;
        int controlX = width / 2;
        int controlWidth = Math.max(120, width - controlX - LEFT);
        int row = 0;

        addBoolean("photon.configuration.enable_bloom", config.enableBloom, controlX, controlWidth, row++);
        addInt("photon.configuration.bloom_mip_level", config.bloomMipLevel, 2, 10,
                controlX, controlWidth, row++);
        addDouble("photon.configuration.bloom_threshold", config.bloomThreshold, 0, 10,
                controlX, controlWidth, row++);
        addDouble("photon.configuration.bloom_intensity", config.bloomIntensity, 0, 1,
                controlX, controlWidth, row++);
        addBoolean("photon.configuration.enable_bloom_with_iris_shader", config.enableBloomWithIrisShader,
                controlX, controlWidth, row++);
        addEnum("photon.configuration.iris_composite_mode", config.irisCompositeMode,
                List.of(IrisCompositeMode.values()), IrisCompositeMode::getTranslatedName,
                controlX, controlWidth, row++);
        addBoolean("photon.configuration.iris_use_translucent_particle_program",
                config.irisUseTranslucentParticleProgram, controlX, controlWidth, row++);
        addEnum("photon.configuration.fx_composite_mode", config.fxCompositeMode,
                List.of(FXCompositeMode.VANILLA, FXCompositeMode.LATE), FXCompositeMode::getTranslatedName,
                controlX, controlWidth, row++);
        addBoolean("photon.configuration.enable_custom_effects", config.enableCustomEffects,
                controlX, controlWidth, row++);
        addBoolean("photon.configuration.enable_custom_effects_with_shader_pack",
                config.enableCustomEffectsWithShaderPack, controlX, controlWidth, row++);
        addInt("photon.configuration.postfx_pool_budget_mb", config.postFxPoolBudgetMB, 16, 4096,
                controlX, controlWidth, row);

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            PhotonConfig.CONFIG_SPEC.save();
            minecraft.setScreen(parent);
        }).bounds(width / 2 - 100, height - 32, 200, 20).build());
    }

    private void addBoolean(String key, ForgeConfigSpec.ConfigValue<Boolean> value,
                            int controlX, int controlWidth, int row) {
        addLabel(key, row);
        var button = Button.builder(booleanText(value.get()), b -> {
            value.set(!value.get());
            b.setMessage(booleanText(value.get()));
            PhotonConfig.CONFIG_SPEC.save();
        }).bounds(controlX, TOP + row * ROW_HEIGHT, controlWidth, 20).build();
        button.setTooltip(optionTooltip(key));
        addRenderableWidget(button);
    }

    private void addInt(String key, ForgeConfigSpec.ConfigValue<Integer> value, int min, int max,
                        int controlX, int controlWidth, int row) {
        addLabel(key, row);
        var slider = new ConfigSlider(controlX, TOP + row * ROW_HEIGHT, controlWidth,
                value.get(), min, max, true, next -> value.set((int) Math.rint(next)));
        slider.setTooltip(optionTooltip(key));
        addRenderableWidget(slider);
    }

    private void addDouble(String key, ForgeConfigSpec.ConfigValue<Double> value, double min, double max,
                           int controlX, int controlWidth, int row) {
        addLabel(key, row);
        var slider = new ConfigSlider(controlX, TOP + row * ROW_HEIGHT, controlWidth,
                value.get(), min, max, false, next -> value.set(next));
        slider.setTooltip(optionTooltip(key));
        addRenderableWidget(slider);
    }

    private <E> void addEnum(String key, ForgeConfigSpec.ConfigValue<E> value, List<E> options,
                             Function<E, Component> display, int controlX, int controlWidth, int row) {
        addLabel(key, row);
        var button = Button.builder(display.apply(value.get()), b -> {
            int index = options.indexOf(value.get());
            E next = options.get((index + 1 + options.size()) % options.size());
            value.set(next);
            b.setMessage(display.apply(next));
            PhotonConfig.CONFIG_SPEC.save();
        }).bounds(controlX, TOP + row * ROW_HEIGHT, controlWidth, 20).build();
        button.setTooltip(optionTooltip(key));
        addRenderableWidget(button);
    }

    private void addLabel(String key, int row) {
        labels.add(Component.translatable(key));
    }

    private static Component booleanText(boolean value) {
        return value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
    }

    private static Tooltip optionTooltip(String key) {
        return Tooltip.create(Component.translatable(key + ".tooltip"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFFFF);
        for (int row = 0; row < labels.size(); row++) {
            graphics.drawString(font, labels.get(row), LEFT, TOP + row * ROW_HEIGHT + 6, 0xFFFFFFFF, false);
        }
    }

    @Override
    public void onClose() {
        PhotonConfig.CONFIG_SPEC.save();
        minecraft.setScreen(parent);
    }

    private static final class ConfigSlider extends AbstractSliderButton {
        private final double min;
        private final double max;
        private final boolean integer;
        private final Consumer<Double> write;

        ConfigSlider(int x, int y, int width, double initial, double min, double max,
                     boolean integer, Consumer<Double> write) {
            super(x, y, width, 20, Component.empty(), (initial - min) / (max - min));
            this.min = min;
            this.max = max;
            this.integer = integer;
            this.write = write;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double selected = min + value * (max - min);
            if (integer) selected = Math.rint(selected);
            setMessage(Component.literal(integer
                    ? Integer.toString((int) selected)
                    : String.format(Locale.ROOT, "%.2f", selected)));
        }

        @Override
        protected void applyValue() {
            double selected = min + value * (max - min);
            if (integer) selected = Math.rint(selected);
            write.accept(selected);
            PhotonConfig.CONFIG_SPEC.save();
        }
    }

    private static final class CommonComponents {
        private static final Component GUI_DONE = Component.translatable("gui.done");
        private static final Component OPTION_ON = Component.translatable("options.on");
        private static final Component OPTION_OFF = Component.translatable("options.off");
    }
}
