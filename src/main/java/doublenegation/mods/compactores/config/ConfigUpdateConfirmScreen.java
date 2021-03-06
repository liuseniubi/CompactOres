package doublenegation.mods.compactores.config;

import doublenegation.mods.compactores.CompactOres;
import doublenegation.mods.compactores.Utils;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.ArrayList;
import java.util.List;

public class ConfigUpdateConfirmScreen extends Utils.ReturningScreen {

    private static final String I18N_KEY_BASE = "gui." + CompactOres.MODID + ".configupdate.";

    private final ITextComponent message;
    private final ITextComponent buttonTextYes;
    private final ITextComponent buttonTextNo;
    private final Button.IPressable onConfirm;
    private final Button.IPressable onDeny;

    private final List<String> wrappedMessage = new ArrayList<>();

    protected ConfigUpdateConfirmScreen(String configCreateVer, String currentVer, Button.IPressable onConfirm, Button.IPressable onDeny) {
        super(new TranslationTextComponent(I18N_KEY_BASE + "title"));
        message = new TranslationTextComponent(I18N_KEY_BASE + "message", currentVer, configCreateVer);
        buttonTextYes = new TranslationTextComponent(I18N_KEY_BASE + "confirm");
        buttonTextNo = new TranslationTextComponent(I18N_KEY_BASE + "deny");
        this.onConfirm = onConfirm;
        this.onDeny = onDeny;
    }

    @Override
    protected void init() {
        super.init();
        wrappedMessage.clear();
        wrappedMessage.addAll(this.font.listFormattedStringToWidth(message.getFormattedText(), this.width - 50));
        int i = (wrappedMessage.size() + 1) * 9 - 80;
        this.addButton(new Button(this.width / 2 - 150, i + 100, 300, 20, buttonTextYes.getFormattedText(), onConfirm));
        this.addButton(new Button(this.width / 2 - 150, i + 124, 300, 20, buttonTextNo.getFormattedText(), onDeny));
    }

    @Override
    public void render(int p_render_1_, int p_render_2_, float p_render_3_) {
        this.renderBackground();
        this.drawCenteredString(this.font, this.title.getFormattedText(), this.width / 2, 9, 0xFFFFFF);
        int y = 3 * 9;
        for(String s : wrappedMessage) {
            this.drawCenteredString(this.font, s, this.width / 2, y, 0xFFFFFF);
            y += 9;
        }
        super.render(p_render_1_, p_render_2_, p_render_3_);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

}
