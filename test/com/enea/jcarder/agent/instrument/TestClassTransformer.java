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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.After;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import com.enea.jcarder.testclasses.instrumentation.SynchronizedField;
import com.enea.jcarder.util.logging.Logger;

public final class TestClassTransformer {
    private static final String TEST_CLASS =
        "com/enea/jcarder/testclasses/instrumentation/SynchronizedField";

    private static final Runnable DEFAULT_SHUTDOWN_ACTION =
        ClassTransformer.shutdownAction;

    @After
    public void restoreShutdownAction() {
        ClassTransformer.shutdownAction = DEFAULT_SHUTDOWN_ACTION;
    }

    @Test
    public void testUnsupportedBytecodeVersionTerminatesApplicationByDefault()
    throws Exception {
        testUnsupportedBytecodeVersionTermination(new InstrumentConfig(), true);
    }

    @Test
    public void testUnsupportedBytecodeVersionCanContinueWithoutTermination()
    throws Exception {
        InstrumentConfig config = new InstrumentConfig();
        config.setHaltOnUnsupportedBytecode(false);
        testUnsupportedBytecodeVersionTermination(config, false);
    }

    private void testUnsupportedBytecodeVersionTermination(
            InstrumentConfig config,
            boolean expectTermination)
    throws Exception {
        final boolean[] terminated = { false };
        ClassTransformer.shutdownAction = new Runnable() {
            public void run() {
                terminated[0] = true;
            }
        };
        ClassTransformer transformer =
            new ClassTransformer(new Logger(null), new File("."), config);
        byte[] classBuffer = TransformClassLoader.getClassBytes(SynchronizedField.class);
        setClassFileMajorVersion(classBuffer,
                                 findUnsupportedClassFileMajorVersion(classBuffer));

        assertNull(transformer.transform(getClass().getClassLoader(),
                                         TEST_CLASS,
                                         null,
                                         null,
                                         classBuffer));
        assertTrue(terminated[0] == expectTermination);
    }

    private static int extractClassFileMajorVersion(byte[] classBuffer) {
        return ((classBuffer[6] & 0xff) << 8) | (classBuffer[7] & 0xff);
    }

    private static void setClassFileMajorVersion(byte[] classBuffer, int version) {
        classBuffer[6] = (byte) (version >>> 8);
        classBuffer[7] = (byte) version;
    }

    private static int findUnsupportedClassFileMajorVersion(byte[] classBuffer) {
        int version = extractClassFileMajorVersion(classBuffer);
        while (version < 65535) {
            version++;
            byte[] probe = classBuffer.clone();
            setClassFileMajorVersion(probe, version);
            if (isUnsupportedClassFileMajorVersion(probe, version)) {
                return version;
            }
        }
        throw new AssertionError("Could not find unsupported class file major version");
    }

    private static boolean isUnsupportedClassFileMajorVersion(byte[] classBuffer,
                                                              int version) {
        try {
            ClassReader reader = new ClassReader(classBuffer);
            ClassWriter writer = new ClassWriter(0);
            reader.accept(writer, 0);
            return false;
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            return message != null
                && message.contains("Unsupported class file major version "
                                    + version);
        }
    }

    @Test
    public void testOtherTransformFailuresReturnNull() throws Exception {
        ClassTransformer transformer =
            new ClassTransformer(new Logger(null), new File("."), new InstrumentConfig());
        byte[] classBuffer = TransformClassLoader.getClassBytes(SynchronizedField.class);
        classBuffer[classBuffer.length - 1] = (byte) ~classBuffer[classBuffer.length - 1];

        assertNull(transformer.transform(getClass().getClassLoader(),
                                       TEST_CLASS,
                                       null,
                                       null,
                                       classBuffer));
    }
}
