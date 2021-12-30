package com.enea.jcarder.agent.instrument;

import com.enea.jcarder.agent.StaticEventListener;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

import java.lang.reflect.Field;
import java.util.function.BiConsumer;

import static com.enea.jcarder.agent.instrument.InstrumentationUtilities.getInternalName;

public class ClassInitMethodAdapter extends AdviceAdapter {
    static final String CLASS_INIT_LOCK_FIELD = "$$CLASS_INIT_LOCK";
    private static final String CALLBACK_CLASS_NAME = getInternalName(StaticEventListener.class);

    private final String mClassName;
    private final InstrumentationContext mContext;

//    private final Label startFinally = new Label();
//    private final Label endFinally = new Label();

    public ClassInitMethodAdapter(String className, MethodVisitor mv, final InstrumentationContext context, int acc, String name, String desc) {
        super(Opcodes.ASM7, mv, acc, name, desc);
        mClassName = className;
        mContext = context;
    }

    public static BiConsumer<Class<?>, String> createLockHandler() {
        return (clazz, methodWithClass) -> {
            try {
                Field classInitLockField = clazz.getDeclaredField(CLASS_INIT_LOCK_FIELD);
                classInitLockField.setAccessible(true);
                Object classInitLock = classInitLockField.get(null);
                String lockReference = clazz.getName() + "." + CLASS_INIT_LOCK_FIELD;
                StaticEventListener.beforeMonitorEnter(classInitLock, lockReference, methodWithClass);
                StaticEventListener.beforeMonitorExit(classInitLock, lockReference, methodWithClass);
            } catch (NoSuchFieldException e) {
                // that's OK
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        };
    }

    @Override
    protected void onMethodEnter()
    {
        mv.visitTypeInsn(NEW, Type.getInternalName(Object.class));
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(Object.class), "<init>", "()V", false);
        mv.visitFieldInsn(PUTSTATIC, mClassName, CLASS_INIT_LOCK_FIELD, Type.getDescriptor(Object.class));

        mv.visitFieldInsn(GETSTATIC, mClassName, CLASS_INIT_LOCK_FIELD, Type.getDescriptor(Object.class));
        mv.visitLdcInsn(mContext.convertFromJvmInternalNames(mClassName) + "." + CLASS_INIT_LOCK_FIELD);
        mv.visitLdcInsn(mContext.getCallContextString());
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                CALLBACK_CLASS_NAME,
                "beforeMonitorEnter",
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V",
                false);

//        visitTryCatchBlock(startFinally, endFinally, endFinally, null);
//        visitLabel(startFinally);
    }


    @Override
    protected void onMethodExit(int opcode)
    {
        if(opcode != ATHROW)
            onFinally();
    }

    private void onFinally() {
        mv.visitFieldInsn(GETSTATIC, mClassName, CLASS_INIT_LOCK_FIELD, Type.getDescriptor(Object.class));
        mv.visitLdcInsn(mContext.convertFromJvmInternalNames(mClassName) + "." + CLASS_INIT_LOCK_FIELD);
        mv.visitLdcInsn(mContext.getCallContextString());
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                CALLBACK_CLASS_NAME,
                "beforeMonitorExit",
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V",
                false);
    }
}
