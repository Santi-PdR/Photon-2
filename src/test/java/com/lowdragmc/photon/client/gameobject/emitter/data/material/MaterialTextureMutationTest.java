package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialTextureMutationTest {
    @Test
    void removingCurveInvalidatesItsUploadedTexture() throws ReflectiveOperationException {
        var texture = new CurveTexture(4, 1);
        var curve = new Curve();
        texture.addCurve(curve);
        setDirty(texture, false); // model an already uploaded texture

        texture.removeCurve(curve);

        assertTrue(isDirty(texture));
    }

    @Test
    void removingGradientInvalidatesItsUploadedTexture() throws ReflectiveOperationException {
        var texture = new GradientTexture(4, 1);
        var gradient = new GradientColor();
        texture.addGradient(gradient);
        setDirty(texture, false); // model an already uploaded texture

        texture.removeGradient(gradient);

        assertTrue(isDirty(texture));
    }

    @Test
    void removingAnEntryThatIsNotPresentDoesNotInvalidateTexture() throws ReflectiveOperationException {
        var texture = new CurveTexture(4, 1);
        setDirty(texture, false);

        texture.removeCurve(new Curve());

        assertFalse(isDirty(texture));
    }

    private static boolean isDirty(Object texture) throws ReflectiveOperationException {
        return dirtyField(texture).getBoolean(texture);
    }

    private static void setDirty(Object texture, boolean dirty) throws ReflectiveOperationException {
        dirtyField(texture).setBoolean(texture, dirty);
    }

    private static Field dirtyField(Object texture) throws NoSuchFieldException {
        var field = texture.getClass().getDeclaredField("isDirty");
        field.setAccessible(true);
        return field;
    }
}
