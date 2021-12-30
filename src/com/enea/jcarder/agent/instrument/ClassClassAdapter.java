package com.enea.jcarder.agent.instrument;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

public class ClassClassAdapter extends ClassVisitor {

    public ClassClassAdapter(ClassVisitor visitor) {
        super(Opcodes.ASM7, visitor);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);

        if (name.equals("forName"))
            methodVisitor = new ForNameMethodVisitor(api, methodVisitor, access, name, descriptor);

        return methodVisitor;
    }

    private static class ForNameMethodVisitor extends AdviceAdapter {
        public ForNameMethodVisitor(int api, MethodVisitor methodVisitor, int access, String name, String descriptor) {
            super(api, methodVisitor, access, name, descriptor);
        }

        @Override
        protected void onMethodExit(int opcode) {
            if(opcode != ATHROW) {
                visitInsn(DUP);
                visitLdcInsn("java.lang.Class.forName");
                visitMethodInsn(INVOKESTATIC, Type.getInternalName(BootstrapInitializeClassConsumer.class), "onClassInitialize", "(Ljava/lang/Class;Ljava/lang/String;)V", true);
            }
        }
    }
}
