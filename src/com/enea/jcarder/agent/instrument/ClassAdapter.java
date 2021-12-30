/*
 * JCarder -- cards Java programs to keep threads disentangled
 *
 * Copyright (C) 2006-2007 Enea AB
 * Copyright (C) 2007 Ulrik Svensson
 * Copyright (C) 2007 Joel Rosdahl
 *
 * This program is made available under the GNU GPL version 2, with a special
 * exception for linking with JUnit. See the accompanying file LICENSE.txt for
 * details.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.enea.jcarder.agent.instrument;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.TryCatchBlockSorter;
import net.jcip.annotations.NotThreadSafe;

import com.enea.jcarder.util.logging.Logger;

import static org.objectweb.asm.Opcodes.*;

/**
 * Each instance of this class is responsible for instrumenting a class. It uses
 * the MonitorMethodAdapter for instrumenting each method in the class.
 */
@NotThreadSafe
class ClassAdapter extends ClassVisitor {
    private final InstrumentationContext mContext;

    private final InstrumentConfig mInstrumentConfig;
    private final Logger mLogger;
    private final String mClassName;

    private boolean mInterface;
    private boolean classInitPresent = false;

    ClassAdapter(InstrumentConfig instrumentConfig, Logger logger, ClassVisitor visitor, String className, int version) {
        super(Opcodes.ASM7, visitor);
        mInstrumentConfig = instrumentConfig;
        mLogger = logger;
        mClassName = className.replace('.', '/');

        mContext = new InstrumentationContext(className, version);
        mLogger.fine("Instrumenting class " + className);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        mInterface = (access & Opcodes.ACC_INTERFACE) > 0;
        super.visit(version, access, name, signature, superName, interfaces);
        super.visitAttribute(new InstrumentedAttribute("DeadLock"));
    }

    @Override
    public void visitSource(String source, String debug) {
        mContext.setSourceFile(source);
        super.visitSource(source, debug);
    }

    @Override
    public MethodVisitor visitMethod(final int access,
                                     final String methodName,
                                     final String descriptor,
                                     final String signature,
                                     final String[] exceptions) {
        final boolean isSynchronized = (access & ACC_SYNCHRONIZED) != 0;
        final boolean isNative = (access & ACC_NATIVE) != 0;
        final boolean isStatic = (access & ACC_STATIC) != 0;
        final int manipulatedArg = access & ~ACC_SYNCHRONIZED;
        if (isNative) {
            mLogger.finer("Can't instrument native method "
                          + mContext.getClassName() + "." + methodName);
            return super.visitMethod(access,
                                     methodName,
                                     descriptor,
                                     signature,
                                     exceptions);
        } else {
            mContext.setMethodName(methodName + ":" + descriptor);
            mContext.setLineNumber(-1);

            MethodVisitor mv = super.visitMethod(manipulatedArg,
                                                       methodName,
                                                       descriptor,
                                                       signature,
                                                       exceptions);

            if (!mInterface && mInstrumentConfig.getClassInitLock() && methodName.equals("<clinit>")) {
                classInitPresent = true;
                mv = new ClassInitMethodAdapter(mClassName, mv, mContext, access, methodName, descriptor);
            }

            final MonitorEnterMethodAdapter dlma =
                new MonitorEnterMethodAdapter(mv, mContext);
            final LockClassSubstituterAdapter lcsa =
              new LockClassSubstituterAdapter(dlma, mContext);

            final StackAnalyzeMethodVisitor stackAnalyzer =
                new StackAnalyzeMethodVisitor(mLogger, lcsa, isStatic);
            dlma.setStackAnalyzer(stackAnalyzer);
            lcsa.setStackAnalyzer(stackAnalyzer);


            final MethodVisitor lineNumberWatcher =
                mContext.getLineNumberWatcherAdapter(stackAnalyzer);

            if (isSynchronized) {
                /*
                 * We want to be able to get an event before a synchronized
                 * method is entered and BEFORE it has taken the lock in order
                 * to notice deadlocks before they actually happen. Therefore we
                 * replace the synchronized declaration of the method with
                 * explicit monitorEnter and monitorExit bytecodes in the
                 * beginning of the method and at each possible exit (by normal
                 * return and by exception) of the method.
                 */
                mLogger.fine("Instrumenting synchronized method "
                        + mContext.getClassName() + "." + methodName);
                TryCatchBlockSorter tryCatchBlockSorter = new TryCatchBlockSorter(lineNumberWatcher, access, methodName, descriptor, signature, exceptions);
                return new SimulateMethodSyncMethodAdapter(tryCatchBlockSorter,
                                                           mContext,
                                                           isStatic);
            } else {
                return lineNumberWatcher;
            }
        }
    }

    @Override
    public void visitEnd() {
        if (classInitPresent)
            super.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, ClassInitMethodAdapter.CLASS_INIT_LOCK_FIELD, Type.getDescriptor(Object.class), null, null).visitEnd();

        super.visitEnd();
    }
}
