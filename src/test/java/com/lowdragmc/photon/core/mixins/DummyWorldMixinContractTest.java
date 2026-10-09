package com.lowdragmc.photon.core.mixins;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DummyWorldMixinContractTest {
    @Test
    void mixinTargetsLdLib2DummyWorldAndIsRequiredByPhotonConfig() throws Exception {
        var classNode = new ClassNode();
        try (var stream = DummyWorldMixin.class.getResourceAsStream("DummyWorldMixin.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(classNode, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        var mixin = allAnnotations(classNode).stream()
                .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))
                .findFirst()
                .orElseThrow();
        var targets = classValues(mixin, "value");
        assertTrue(targets.contains(Type.getType("Lcom/lowdragmc/lowdraglib2/utils/virtuallevel/DummyWorld;")));

        try (var stream = getClass().getResourceAsStream("/photon.mixins.json")) {
            assertNotNull(stream);
            var config = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(config.contains("\"required\": true"));
            assertTrue(config.contains("\"DummyWorldMixin\""));
        }
    }

    @Test
    void redirectsBothSyntheticForgeLevelTickEvents() {
        var targets = Arrays.stream(DummyWorldMixin.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Redirect.class))
                .filter(Objects::nonNull)
                .filter(redirect -> Arrays.asList(redirect.method()).contains("tickWorld"))
                .map(redirect -> redirect.at().target())
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                "Lnet/minecraftforge/event/ForgeEventFactory;onPreLevelTick(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V",
                "Lnet/minecraftforge/event/ForgeEventFactory;onPostLevelTick(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V"), targets);
    }

    private static List<AnnotationNode> allAnnotations(ClassNode classNode) {
        var annotations = new ArrayList<AnnotationNode>();
        if (classNode.visibleAnnotations != null) annotations.addAll(classNode.visibleAnnotations);
        if (classNode.invisibleAnnotations != null) annotations.addAll(classNode.invisibleAnnotations);
        return annotations;
    }

    private static List<?> classValues(AnnotationNode annotation, String name) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (name.equals(annotation.values.get(i))) {
                var value = annotation.values.get(i + 1);
                if (value instanceof List<?> values) return values;
            }
        }
        return List.of();
    }
}
