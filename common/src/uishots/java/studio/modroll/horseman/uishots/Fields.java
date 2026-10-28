package studio.modroll.horseman.uishots;

import java.lang.reflect.Field;

/** Reflection for private vanilla fields with no accessor. Dev runs use Mojang names on both loaders. */
final class Fields {

    private Fields() {}

    static void set(Class<?> owner, Object target, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("UI shots cannot set " + owner.getSimpleName() + "." + name, e);
        }
    }
}
