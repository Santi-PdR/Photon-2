package com.lowdragmc.photon.command;

import com.lowdragmc.photon.client.fx.FXHelper;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.concurrent.CompletableFuture;

/**
 * @author KilaBash
 * @date 2023/6/12
 * @implNote FxLocationArgument
 */
public class FxLocationArgument extends ResourceLocationArgument {
    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        // Delay the resource scan behind Forge's physical-side gate; this argument type is also
        // registered during common setup on dedicated servers.
        var clientSuggestions = DistExecutor.unsafeCallWhenOn(Dist.CLIENT,
                () -> () -> SharedSuggestionProvider.suggestResource(FXHelper.listAllFX(), builder));
        if (clientSuggestions != null) return clientSuggestions;
        return super.listSuggestions(context, builder);
    }
}
