package com.lowdragmc.photon.core.mixins.accessor;

import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the Photon editor detach a project if LDLib2 cleanup aborts before clearing its private state. */
@Mixin(value = Editor.class, remap = false)
public interface EditorProjectStateAccessor {
    @Accessor("currentProject")
    void photon$setCurrentProject(@Nullable IProject project);
}
